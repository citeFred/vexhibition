package com.meta.vexhibition.ai;

import com.meta.vexhibition.ai.service.OpenAiService;
import com.meta.vexhibition.ai.service.RagService;
import com.meta.vexhibition.production.domain.Production;
import com.meta.vexhibition.production.repository.ProductionRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 파이프라인 정량 평가 — LLM-as-a-Judge 방식
 *
 * <h3>평가 방식 개요</h3>
 * <p>기존 TF-IDF 키워드 재현율 대신 <b>LLM을 심사위원(Judge)으로 활용</b>하여
 * Faithfulness(사실 부합률)를 0~100점으로 직접 평가합니다.
 * 기존 프롬프트(Prompt-Only) 방식과 RAG 방식을 각 작품당 {@value ITERATIONS_PER_ARTWORK}회
 * 반복 실행하여 지연 시간(Latency)과 LLM Judge 점수를 측정하고
 * {@code docent_llm_judge_results.csv}로 내보냅니다.</p>
 *
 * <h3>LLM-as-a-Judge 방식의 장점</h3>
 * <ul>
 *   <li>단순 단어 일치가 아닌 <b>의미적 일치</b>를 평가합니다.</li>
 *   <li>환각(Hallucination) 여부를 문맥적으로 판단합니다.</li>
 *   <li>PDF 발표자료 청크처럼 길고 복잡한 Reference에서도 정확한 점수를 산출합니다.</li>
 * </ul>
 *
 * <h3>실행 전 필수 조건</h3>
 * <ul>
 *   <li>PostgreSQL (pgvector 확장 포함) 서버 구동</li>
 *   <li>application.properties의 OpenAI API 키 유효</li>
 *   <li>DB에 평가 대상 Production 데이터 및 pgvector에 임베딩 청크 적재 완료</li>
 *   <li>⚠️ Judge LLM 호출로 인해 TF-IDF 방식보다 API 비용이 추가로 발생합니다.</li>
 * </ul>
 */
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagLlmJudgeEvaluationTest {

    // =========================================================================
    // 상수
    // =========================================================================

    /** 평가할 작품 수 */
    private static final int TARGET_ARTWORK_COUNT   = 10;
    /** 작품당 반복 측정 횟수 */
    private static final int ITERATIONS_PER_ARTWORK = 5;
    /** 결과 CSV 경로 (프로젝트 루트) — 기존 TF-IDF 결과와 구분 */
    private static final String CSV_FILE_PATH       = "docent_llm_judge_results.csv";

    /**
     * Judge LLM 응답에서 정수 점수를 추출하는 정규식.
     * LLM이 "95" 또는 "점수: 95" 등 다양한 형식으로 반환할 수 있어 숫자만 파싱합니다.
     */
    private static final Pattern SCORE_PATTERN = Pattern.compile("\\d+");

    /**
     * CSV 헤더.
     * <ul>
     *   <li>Mode             : PROMPT_ONLY / RAG</li>
     *   <li>ArtworkID        : Production PK</li>
     *   <li>ArtworkTitle     : 작품명</li>
     *   <li>Iteration        : 반복 번호 (1 ~ {@value ITERATIONS_PER_ARTWORK})</li>
     *   <li>RetrievalTime(ms): RAG 검색 단계 소요 시간 (PROMPT_ONLY는 0)</li>
     *   <li>GenerationTime(ms): LLM 텍스트 생성 단계 소요 시간</li>
     *   <li>JudgeTime(ms)    : Judge LLM 평가 소요 시간</li>
     *   <li>TotalTime(ms)    : 전체 소요 시간 (Retrieval + Generation + Judge)</li>
     *   <li>LlmJudgeScore(%): LLM-as-a-Judge 사실 부합률 (0~100 정수)</li>
     *   <li>GeneratedText    : 생성된 도슨트 대본</li>
     * </ul>
     */
    private static final String CSV_HEADER =
            "Mode,ArtworkID,ArtworkTitle,Iteration," +
            "RetrievalTime(ms),GenerationTime(ms),JudgeTime(ms),TotalTime(ms)," +
            "LlmJudgeScore(%),GeneratedText";

    // =========================================================================
    // 스프링 빈 주입
    // =========================================================================

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private RagService ragService;

    @Autowired
    private ProductionRepository productionRepository;

    // =========================================================================
    // CSV 스트림 (AfterAll에서 닫힘)
    // =========================================================================

    private PrintWriter csvWriter;

    // =========================================================================
    // 테스트 생명주기
    // =========================================================================

    @BeforeAll
    void setUpCsvWriter() throws IOException {
        csvWriter = new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(CSV_FILE_PATH, false),
                                StandardCharsets.UTF_8
                        )
                )
        );
        csvWriter.println(CSV_HEADER);
        csvWriter.flush();
        log.info("CSV 파일 초기화 완료: {}", CSV_FILE_PATH);
    }

    @AfterAll
    void tearDownCsvWriter() {
        if (csvWriter != null) {
            csvWriter.flush();
            csvWriter.close();
        }
        log.info("=== 평가 완료. 결과 파일: {} ===", CSV_FILE_PATH);
    }

    // =========================================================================
    // 메인 평가 테스트
    // =========================================================================

    @Test
    void evaluateDocentPipelineWithLlmJudge() {

        // -----------------------------------------------------------------------
        // Given: 평가 대상 작품 조회 (DB/pgvector에 사전 적재된 데이터만 조회)
        // -----------------------------------------------------------------------
        List<Production> productions = productionRepository.findAll()
                .stream()
                .limit(TARGET_ARTWORK_COUNT)
                .toList();

        assertThat(productions)
                .as("평가 대상 Production이 DB에 최소 1건 이상 있어야 합니다.")
                .isNotEmpty();

        int total = productions.size() * ITERATIONS_PER_ARTWORK * 2;
        log.info("=== LLM-as-a-Judge 평가 시작: {}개 작품 × {}회 반복 × 2가지 방식 = 총 {}회 측정 ===",
                productions.size(), ITERATIONS_PER_ARTWORK, total);

        int rowCount = 0;
        for (Production production : productions) {
            for (int iteration = 1; iteration <= ITERATIONS_PER_ARTWORK; iteration++) {

                log.info("진행 [{}/{}] ArtworkID={}, Title={}, Iteration={}/{}",
                        rowCount + 1, total,
                        production.getId(), production.getTitle(),
                        iteration, ITERATIONS_PER_ARTWORK);

                // When & Then: [1] Prompt-Only 방식 측정
                runPromptOnlyEvaluation(production, iteration);
                rowCount++;

                // When & Then: [2] RAG 방식 측정
                runRagEvaluation(production, iteration);
                rowCount++;
            }
        }

        log.info("=== 전체 {}회 측정 완료 ===", rowCount);
    }

    // =========================================================================
    // Prompt-Only 평가
    // =========================================================================

    /**
     * 기존 프롬프트 방식(RAG 없음)으로 도슨트 대본을 생성하고 LLM Judge로 평가 후 CSV에 기록합니다.
     *
     * <p><b>Generation Reference</b>: {@code production.getDescription()} —
     * Prompt-Only 방식은 description만 보고 대본을 생성합니다.</p>
     *
     * <p><b>Judge Reference</b>: description + PDF 전체 청크 —
     * Judge에게는 Prompt-Only가 접근하지 못했던 전체 지식(PDF)을 정답지로 제공합니다.
     * 이를 통해 PDF를 보지 못한 Prompt-Only의 지식 커버리지 한계를 객관적으로 수치화합니다.
     * 이 점수는 순수 Faithfulness가 아닌 <b>지식 커버리지(Coverage/Recall)</b>를 측정합니다.</p>
     */
    private void runPromptOnlyEvaluation(Production production, int iteration) {
        Long artworkId = production.getId();
        StopWatch sw = new StopWatch("PROMPT_ONLY-" + artworkId + "-" + iteration);

        try {
            String personaSetUp =
                    "당신은 '메타버스 아카데미 수료작품 전시회'의 전문 AI 도슨트 '벡시(Vexi)'입니다. " +
                    "관람객에게 항상 존댓말을 사용하며, 친절하고 흥미로운 톤으로 작품을 설명해야 합니다.";

            // Prompt-Only 생성 시에는 description만 주입 (PDF 없음)
            String generationReference = production.getDescription();

            String userTask =
                    "아래 작품 설명을 바탕으로 180초(3분) 내외의 창의적인 도슨트 안내 대본을 작성해줘. " +
                    "환영인사나 자기소개는 생략하고 바로 작품 설명부터 시작해줘. " +
                    "이 작품의 목적과 장점을 잘 분석해서 구체적으로 포함하고, " +
                    "마지막에는 관람객의 흥미를 유발하는 질문을 던지며 마무리해줘.\n\n" +
                    "--- 원본 설명 ---\n" + generationReference;

            // [Generation 단계]
            sw.start("generation");
            String generatedScript = openAiService.generate(personaSetUp, userTask);
            sw.stop();
            long generationTime = sw.lastTaskInfo().getTimeMillis();

            assertThat(generatedScript)
                    .as("ArtworkID %d, Iter %d [PROMPT_ONLY]: 생성 스크립트 비어있음", artworkId, iteration)
                    .isNotNull().isNotBlank();

            // [Judge 단계] Judge에게는 PDF 전체를 정답지(Reference)로 제공 — RAG와 동일한 기준으로 채점
            // Prompt-Only가 접근하지 못한 PDF 지식까지 포함하여 지식 커버리지 격차를 수치화
            String ragQuery = production.getTitle() + " " + production.getDescription();
            String pdfContext = ragService.buildContextFromDocuments(
                    ragService.searchProductionPdfChunks(artworkId, ragQuery));
            String fullReferenceForJudge =
                    "작품명: " + production.getTitle() + "\n" +
                    "설명: "   + production.getDescription() + "\n\n" +
                    pdfContext;

            sw.start("judge");
            double llmJudgeScore = evaluateFaithfulnessWithLLM(generatedScript, fullReferenceForJudge);
            sw.stop();
            long judgeTime = sw.lastTaskInfo().getTimeMillis();

            long totalTime = generationTime + judgeTime;

            log.info("[PROMPT_ONLY] ID={} iter={} | gen={}ms judge={}ms total={}ms llmJudge={}",
                    artworkId, iteration,
                    generationTime, judgeTime, totalTime,
                    String.format("%.0f", llmJudgeScore));

            writeCsvRow("PROMPT_ONLY", artworkId, production.getTitle(), iteration,
                    0L, generationTime, judgeTime, totalTime, llmJudgeScore, generatedScript);

        } catch (Exception e) {
            log.error("[PROMPT_ONLY] ArtworkID={}, Iter={} 측정 실패: {}", artworkId, iteration, e.getMessage(), e);
            writeCsvRow("PROMPT_ONLY", artworkId, production.getTitle(), iteration,
                    -1L, -1L, -1L, -1L, -1.0, "ERROR: " + e.getMessage());
        }
    }

    // =========================================================================
    // RAG 평가
    // =========================================================================

    /**
     * RAG 방식으로 도슨트 대본을 생성하고 LLM Judge로 평가 후 CSV에 기록합니다.
     * Retrieval, Generation, Judge 단계를 StopWatch로 분리 측정합니다.
     *
     * <p><b>Faithfulness Reference</b>: {@code description} + {@code pdfContext} —
     * LLM에 실제로 주입된 전체 컨텍스트를 기준으로 Judge가 평가합니다.</p>
     */
    private void runRagEvaluation(Production production, int iteration) {
        Long artworkId = production.getId();
        StopWatch sw = new StopWatch("RAG-" + artworkId + "-" + iteration);

        try {
            String ragQuery = production.getTitle() + " " + production.getDescription();

            // [Retrieval 단계]
            sw.start("retrieval");

            List<Document> pdfChunks  = ragService.searchProductionPdfChunks(artworkId, ragQuery);
            String pdfContext         = ragService.buildContextFromDocuments(pdfChunks);

            List<Document> similarDocs    = ragService.searchSimilarProductions(ragQuery, artworkId);
            String         similarContext = ragService.buildContextFromDocuments(similarDocs);

            sw.stop();
            long retrievalTime = sw.lastTaskInfo().getTimeMillis();

            // Reference = LLM에 실제 주입되는 컨텍스트 (Judge 평가 기준)
            String reference =
                    "작품명: " + production.getTitle() + "\n" +
                    "팀명: "   + production.getTeamname() + "\n" +
                    "기수: "   + production.getGeneration() + "기\n" +
                    "설명: "   + production.getDescription() + "\n\n" +
                    pdfContext;

            String personaSetUp =
                    "당신은 '메타버스 아카데미 수료작품 전시회'의 전문 AI 도슨트 '벡시(Vexi)'입니다. " +
                    "관람객에게 항상 존댓말을 사용하며, 친절하고 흥미로운 톤으로 작품을 설명해야 합니다.";

            String userTask =
                    "아래 현재 작품 정보와 발표자료 내용, 유사 작품 맥락을 바탕으로 180초(3분) 내외의 창의적인 도슨트 안내 대본을 작성해줘. " +
                    "환영인사나 자기소개는 생략하고 바로 작품 설명부터 시작해줘. " +
                    "발표자료 내용이 있다면 그것을 우선 참고하여 이 작품의 목적과 장점을 구체적이고 풍부하게 설명하고, " +
                    "유사 작품과 자연스러운 연관성이 있다면 간략히 언급해줘. " +
                    "마지막에는 관람객의 흥미를 유발하는 질문을 던지며 마무리해줘.\n\n" +
                    "--- 현재 작품 기본 정보 ---\n" +
                    "작품명: " + production.getTitle()       + "\n" +
                    "팀명: "   + production.getTeamname()    + "\n" +
                    "기수: "   + production.getGeneration()  + "기\n" +
                    "설명: "   + production.getDescription() + "\n\n" +
                    "--- 발표자료 내용 (PDF, 우선 참고) ---\n" + pdfContext    + "\n\n" +
                    "--- 유사 작품 맥락 (참고용) ---\n"       + similarContext;

            // [Generation 단계]
            sw.start("generation");
            String generatedScript = openAiService.generate(personaSetUp, userTask);
            sw.stop();
            long generationTime = sw.lastTaskInfo().getTimeMillis();

            assertThat(generatedScript)
                    .as("ArtworkID %d, Iter %d [RAG]: 생성 스크립트 비어있음", artworkId, iteration)
                    .isNotNull().isNotBlank();

            // [Judge 단계] LLM-as-a-Judge로 Faithfulness 평가
            sw.start("judge");
            double llmJudgeScore = evaluateFaithfulnessWithLLM(generatedScript, reference);
            sw.stop();
            long judgeTime = sw.lastTaskInfo().getTimeMillis();

            long totalTime = retrievalTime + generationTime + judgeTime;

            log.info("[RAG] ID={} iter={} | retrieval={}ms gen={}ms judge={}ms total={}ms llmJudge={}",
                    artworkId, iteration,
                    retrievalTime, generationTime, judgeTime, totalTime,
                    String.format("%.0f", llmJudgeScore));

            writeCsvRow("RAG", artworkId, production.getTitle(), iteration,
                    retrievalTime, generationTime, judgeTime, totalTime, llmJudgeScore, generatedScript);

        } catch (Exception e) {
            log.error("[RAG] ArtworkID={}, Iter={} 측정 실패: {}", artworkId, iteration, e.getMessage(), e);
            writeCsvRow("RAG", artworkId, production.getTitle(), iteration,
                    -1L, -1L, -1L, -1L, -1.0, "ERROR: " + e.getMessage());
        }
    }

    // =========================================================================
    // LLM-as-a-Judge 평가
    // =========================================================================

    /**
     * LLM을 심사위원으로 활용하여 생성된 도슨트 대본의 Faithfulness를 평가합니다.
     *
     * <h4>평가 기준</h4>
     * <p>Judge LLM은 생성된 대본이 Reference(실제 주입된 컨텍스트)의 핵심 정보
     * (기획 의도, 주요 기능, 고유명사 등)를 환각(Hallucination) 없이 얼마나 정확하고
     * 풍부하게 반영했는지를 0~100점으로 평가합니다.</p>
     *
     * <h4>파싱 전략</h4>
     * <p>LLM 응답에서 첫 번째 정수를 정규식으로 추출합니다.
     * 파싱 실패 시 {@code 0.0}을 반환하고 경고 로그를 남깁니다.</p>
     *
     * @param generated 평가 대상 생성 대본
     * @param reference LLM에 실제 주입된 컨텍스트 (Faithfulness 기준)
     * @return Judge 점수 0.0 ~ 100.0 (파싱 실패 시 0.0)
     */
    private double evaluateFaithfulnessWithLLM(String generated, String reference) {
        String judgePersona =
                "당신은 AI 도슨트 대본의 '심층 지식 활용도'를 평가하는 엄격한 심사위원입니다.";

        String judgeTask =
                "아래 기준 문서(Reference)를 정답지로 삼아 생성된 도슨트 대본을 0~100점으로 평가하세요.\n\n" +
                "[감점 기준]\n" +
                "- 기준 문서의 기본 설명을 단순히 재구성한 수준에 그친 경우\n" +
                "- 구체적 근거 없이 모호하거나 일반적인 표현만 사용한 경우\n\n" +
                "[가점 기준]\n" +
                "- 기준 문서의 세부 기술 스택, 구체적 기능, 팀의 독자적 관점 등 심층 정보를 자연스럽게 녹여낸 경우\n" +
                "- 기준 문서에만 존재하는 고유명사나 수치를 정확하게 활용한 경우\n\n" +
                "설명 없이 오직 0~100 사이의 '정수(Integer)' 점수만 출력하세요. (예: 78)\n\n" +
                "--- 기준 문서 (Reference) ---\n" + reference + "\n\n" +
                "--- 생성된 도슨트 대본 ---\n" + generated;

        try {
            String judgeResponse = openAiService.generate(judgePersona, judgeTask);

            Matcher matcher = SCORE_PATTERN.matcher(judgeResponse.trim());
            if (matcher.find()) {
                int score = Integer.parseInt(matcher.group());
                // 0~100 범위 클램핑
                return Math.min(100, Math.max(0, score));
            }

            log.warn("Judge LLM 응답에서 점수를 파싱하지 못했습니다. 응답: '{}'", judgeResponse);
            return 0.0;

        } catch (Exception e) {
            log.warn("Judge LLM 호출 실패: {}", e.getMessage());
            return 0.0;
        }
    }

    // =========================================================================
    // CSV 기록 헬퍼
    // =========================================================================

    /**
     * 측정 결과 1건을 CSV 파일에 즉시 기록합니다 (RFC 4180 이스케이프 적용).
     *
     * <p>각 측정 후 즉시 flush하여 테스트 중단 시에도 기록된 데이터가 보호됩니다.</p>
     */
    private void writeCsvRow(String mode,
                              Long artworkId,
                              String artworkTitle,
                              int iteration,
                              long retrievalTimeMs,
                              long generationTimeMs,
                              long judgeTimeMs,
                              long totalTimeMs,
                              double llmJudgeScore,
                              String generatedText) {
        String row = String.join(",",
                escapeCsvField(mode),
                String.valueOf(artworkId),
                escapeCsvField(artworkTitle),
                String.valueOf(iteration),
                String.valueOf(retrievalTimeMs),
                String.valueOf(generationTimeMs),
                String.valueOf(judgeTimeMs),
                String.valueOf(totalTimeMs),
                String.format("%.0f", llmJudgeScore),
                escapeCsvField(generatedText)
        );
        csvWriter.println(row);
        csvWriter.flush();
    }

    /**
     * CSV 필드 이스케이프 처리 (RFC 4180).
     */
    private String escapeCsvField(String value) {
        if (value == null) return "\"\"";
        String escaped = value
                .replace("\"", "\"\"")
                .replace("\r\n", " ")
                .replace("\n",   " ")
                .replace("\r",   " ");
        return "\"" + escaped + "\"";
    }
}
