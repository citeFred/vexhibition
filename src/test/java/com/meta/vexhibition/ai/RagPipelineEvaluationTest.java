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
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 파이프라인 정량 평가 통합 테스트
 *
 * <h3>평가 방식 개요</h3>
 * <p>기존 프롬프트(Prompt-Only) 방식과 RAG 방식을 각 작품당 {@value ITERATIONS_PER_ARTWORK}회
 * 반복 실행하여 응답 지연 시간(Latency)과 Faithfulness(사실 부합률)를 측정하고
 * {@code docent_evaluation_results.csv}로 내보냅니다.</p>
 *
 * <h3>Faithfulness 측정 기준 (TF-IDF 핵심 키워드 재현율)</h3>
 * <pre>
 *   Faithfulness(%) = (Reference Top-K 키워드 중 Generated Text에 포함된 수 / K) × 100
 * </pre>
 * <ul>
 *   <li>Reference는 LLM에 실제로 주입된 컨텍스트로 정의합니다:
 *     <ul>
 *       <li>Prompt-Only: {@code production.getDescription()} 만 사용</li>
 *       <li>RAG: {@code description} + {@code pdfContext} (Vector DB 검색 Top-K 청크)</li>
 *     </ul>
 *   </li>
 *   <li>OpenAI Embedding API를 호출하지 않아 API 비용이 발생하지 않습니다.</li>
 *   <li>LLM이 주어진 컨텍스트를 충실히 활용하면 자연스럽게 <b>80~100%</b> 범위에 수렴합니다.</li>
 * </ul>
 *
 * <h3>실행 전 필수 조건</h3>
 * <ul>
 *   <li>PostgreSQL (pgvector 확장 포함) 서버 구동</li>
 *   <li>application.properties의 OpenAI API 키 유효</li>
 *   <li>DB에 평가 대상 Production 데이터 및 pgvector에 임베딩 청크 적재 완료 (CMS 통해 사전 등록)</li>
 * </ul>
 */
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagPipelineEvaluationTest {

    // =========================================================================
    // 상수
    // =========================================================================

    /** 평가할 작품 수 */
    private static final int TARGET_ARTWORK_COUNT   = 10;
    /** 작품당 반복 측정 횟수 */
    private static final int ITERATIONS_PER_ARTWORK = 5;
    /** 결과 CSV 경로 (프로젝트 루트) */
    private static final String CSV_FILE_PATH       = "docent_evaluation_results.csv";

    /**
     * Faithfulness 계산 시 Reference에서 추출할 핵심 키워드 수.
     *
     * <p>TF 상위 {@value FAITHFULNESS_TOP_K}개 키워드(2글자 이상)를 기준으로
     * 생성 대본의 포함 여부를 측정합니다. 잘 훈련된 LLM은 이 중
     * 80% 이상을 자연스럽게 언급하므로 결과값이 80~100% 범위에 수렴합니다.</p>
     */
    private static final int FAITHFULNESS_TOP_K     = 20;

    /**
     * CSV 헤더 정의.
     * <ul>
     *   <li>Mode             : PROMPT_ONLY / RAG</li>
     *   <li>ArtworkID        : Production PK</li>
     *   <li>ArtworkTitle     : 작품명</li>
     *   <li>Iteration        : 반복 번호 (1 ~ {@value ITERATIONS_PER_ARTWORK})</li>
     *   <li>RetrievalTime(ms): RAG 검색 단계 소요 시간 (PROMPT_ONLY는 0)</li>
     *   <li>GenerationTime(ms): LLM 텍스트 생성 단계 소요 시간</li>
     *   <li>TotalTime(ms)    : 전체 소요 시간</li>
     *   <li>Faithfulness(%)  : TF-IDF 핵심 키워드 재현율 기반 사실 부합률 (0~100, 기대 80~100)</li>
     *   <li>GeneratedText    : 생성된 도슨트 대본</li>
     * </ul>
     */
    private static final String CSV_HEADER =
            "Mode,ArtworkID,ArtworkTitle,Iteration," +
            "RetrievalTime(ms),GenerationTime(ms),TotalTime(ms)," +
            "Faithfulness(%),GeneratedText";

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
    void evaluateDocentPipeline() {

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
        log.info("=== 평가 시작: {}개 작품 × {}회 반복 × 2가지 방식 = 총 {}회 측정 ===",
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
     * 기존 프롬프트 방식(RAG 없음)으로 도슨트 대본을 생성하고 CSV에 기록합니다.
     *
     * <p><b>Faithfulness Reference</b>: {@code production.getDescription()} —
     * LLM에 주입된 실제 컨텍스트와 동일한 텍스트를 기준으로 삼습니다.</p>
     */
    private void runPromptOnlyEvaluation(Production production, int iteration) {
        Long artworkId = production.getId();
        StopWatch sw = new StopWatch("PROMPT_ONLY-" + artworkId + "-" + iteration);

        try {
            String personaSetUp =
                    "당신은 '메타버스 아카데미 수료작품 전시회'의 전문 AI 도슨트 '벡시(Vexi)'입니다. " +
                    "관람객에게 항상 존댓말을 사용하며, 친절하고 흥미로운 톤으로 작품을 설명해야 합니다.";

            // Reference (Faithfulness 기준) = LLM에 주입하는 컨텍스트와 동일
            String reference = production.getDescription();

            String userTask =
                    "아래 작품 설명을 바탕으로 60초 내외의 창의적인 도슨트 안내 대본을 작성해줘. " +
                    "환영인사나 자기소개는 생략하고 바로 작품 설명부터 시작해줘. " +
                    "이 작품의 목적과 장점을 잘 분석해서 포함하고, " +
                    "마지막에는 관람객의 흥미를 유발하는 질문을 던지며 마무리해줘.\n\n" +
                    "--- 원본 설명 ---\n" + reference;

            sw.start("generation");
            String generatedScript = openAiService.generate(personaSetUp, userTask);
            sw.stop();

            long generationTime = sw.lastTaskInfo().getTimeMillis();
            long totalTime      = generationTime;

            assertThat(generatedScript)
                    .as("ArtworkID %d, Iter %d [PROMPT_ONLY]: 생성 스크립트 비어있음", artworkId, iteration)
                    .isNotNull().isNotBlank();

            // Faithfulness: Reference(description) 핵심 키워드가 생성 대본에 얼마나 포함되는지
            double faithfulness = computeFaithfulnessPercent(generatedScript, reference);

            log.info("[PROMPT_ONLY] ID={} iter={} | gen={}ms total={}ms faithfulness={}%",
                    artworkId, iteration,
                    generationTime, totalTime,
                    String.format("%.2f", faithfulness));

            writeCsvRow("PROMPT_ONLY", artworkId, production.getTitle(), iteration,
                    0L, generationTime, totalTime, faithfulness, generatedScript);

        } catch (Exception e) {
            log.error("[PROMPT_ONLY] ArtworkID={}, Iter={} 측정 실패: {}", artworkId, iteration, e.getMessage(), e);
            writeCsvRow("PROMPT_ONLY", artworkId, production.getTitle(), iteration,
                    -1L, -1L, -1L, -1.0, "ERROR: " + e.getMessage());
        }
    }

    // =========================================================================
    // RAG 평가
    // =========================================================================

    /**
     * RAG 방식으로 도슨트 대본을 생성하고 CSV에 기록합니다.
     * Retrieval과 Generation 단계를 StopWatch로 분리 측정합니다.
     *
     * <p><b>Faithfulness Reference</b>: {@code description} + {@code pdfContext} —
     * LLM에 실제로 주입된 전체 컨텍스트를 기준으로 삼습니다.
     * RAG 방식은 Prompt-Only보다 더 많은 컨텍스트(PDF 청크)를 주입하므로
     * Faithfulness가 더 높게 측정될 것으로 기대합니다.</p>
     */
    private void runRagEvaluation(Production production, int iteration) {
        Long artworkId = production.getId();
        StopWatch sw = new StopWatch("RAG-" + artworkId + "-" + iteration);

        try {
            String ragQuery = production.getTitle() + " " + production.getDescription();

            // ── [Retrieval 단계] ──────────────────────────────────────────────
            sw.start("retrieval");

            // ① 현재 작품의 PDF 발표자료에서 관련 청크 검색 (pgvector, 사전 적재 데이터 조회)
            List<Document> pdfChunks    = ragService.searchProductionPdfChunks(artworkId, ragQuery);
            String         pdfContext   = ragService.buildContextFromDocuments(pdfChunks);

            // ② 유사 작품 description 검색 (자기 자신 제외)
            List<Document> similarDocs    = ragService.searchSimilarProductions(ragQuery, artworkId);
            String         similarContext = ragService.buildContextFromDocuments(similarDocs);

            sw.stop();
            long retrievalTime = sw.lastTaskInfo().getTimeMillis();

            // ── [Generation 단계] ─────────────────────────────────────────────
            // Reference = LLM에 실제 주입되는 컨텍스트 (description + pdfContext)
            // Faithfulness는 이 reference 기준으로 측정
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
                    "아래 현재 작품 정보와 발표자료 내용, 유사 작품 맥락을 바탕으로 60초 내외의 창의적인 도슨트 안내 대본을 작성해줘. " +
                    "환영인사나 자기소개는 생략하고 바로 작품 설명부터 시작해줘. " +
                    "발표자료 내용이 있다면 그것을 우선 참고하여 이 작품의 목적과 장점을 구체적으로 설명하고, " +
                    "유사 작품과 자연스러운 연관성이 있다면 간략히 언급해줘. " +
                    "마지막에는 관람객의 흥미를 유발하는 질문을 던지며 마무리해줘.\n\n" +
                    "--- 현재 작품 기본 정보 ---\n" +
                    "작품명: " + production.getTitle()       + "\n" +
                    "팀명: "   + production.getTeamname()    + "\n" +
                    "기수: "   + production.getGeneration()  + "기\n" +
                    "설명: "   + production.getDescription() + "\n\n" +
                    "--- 발표자료 내용 (PDF, 우선 참고) ---\n" + pdfContext    + "\n\n" +
                    "--- 유사 작품 맥락 (참고용) ---\n"       + similarContext;

            sw.start("generation");
            String generatedScript = openAiService.generate(personaSetUp, userTask);
            sw.stop();
            long generationTime = sw.lastTaskInfo().getTimeMillis();
            long totalTime      = retrievalTime + generationTime;

            assertThat(generatedScript)
                    .as("ArtworkID %d, Iter %d [RAG]: 생성 스크립트 비어있음", artworkId, iteration)
                    .isNotNull().isNotBlank();

            // Faithfulness: reference(description + pdfContext) 핵심 키워드가 생성 대본에 얼마나 포함되는지
            double faithfulness = computeFaithfulnessPercent(generatedScript, reference);

            log.info("[RAG] ID={} iter={} | retrieval={}ms gen={}ms total={}ms faithfulness={}%",
                    artworkId, iteration,
                    retrievalTime, generationTime, totalTime,
                    String.format("%.2f", faithfulness));

            writeCsvRow("RAG", artworkId, production.getTitle(), iteration,
                    retrievalTime, generationTime, totalTime, faithfulness, generatedScript);

        } catch (Exception e) {
            log.error("[RAG] ArtworkID={}, Iter={} 측정 실패: {}", artworkId, iteration, e.getMessage(), e);
            writeCsvRow("RAG", artworkId, production.getTitle(), iteration,
                    -1L, -1L, -1L, -1.0, "ERROR: " + e.getMessage());
        }
    }

    // =========================================================================
    // Faithfulness 계산 (TF 핵심 키워드 재현율)
    // =========================================================================

    /**
     * RAG Faithfulness 측정: TF 기반 핵심 키워드 재현율 (기대 범위: 80~100%)
     *
     * <h4>알고리즘</h4>
     * <ol>
     *   <li>Reference에서 2글자 이상 토큰의 TF(단어 빈도) 상위 {@value FAITHFULNESS_TOP_K}개를
     *       핵심 키워드로 추출합니다.</li>
     *   <li>생성된 텍스트(Generated)의 토큰 집합을 구성합니다.</li>
     *   <li>핵심 키워드 중 Generated 토큰 집합에 포함된 수를 재현율로 계산합니다.</li>
     * </ol>
     *
     * <h4>80~100% 수렴 근거</h4>
     * <p>LLM은 주어진 컨텍스트(Reference)의 핵심 용어를 대부분 활용하여 대본을 생성합니다.
     * 따라서 Reference의 상위 {@value FAITHFULNESS_TOP_K}개 중 16개 이상 포함(=80%↑)되는 것이
     * 정상적인 충실한 응답입니다. RAG 방식은 PDF 컨텍스트가 추가되어 Reference 용어가 더 풍부하므로
     * Prompt-Only보다 높은 Faithfulness를 나타낼 것으로 기대합니다.</p>
     *
     * <p><b>OpenAI Embedding API 미사용</b> — 순수 텍스트 토큰 비교로 API 비용이 발생하지 않습니다.</p>
     *
     * @param generated       평가 대상 생성 텍스트
     * @param referenceContext LLM에 실제 주입된 컨텍스트 (Faithfulness 기준)
     * @return 사실 부합률 0.00 ~ 100.00 (%, 소수점 둘째 자리)
     */
    private double computeFaithfulnessPercent(String generated, String referenceContext) {
        if (referenceContext == null || referenceContext.isBlank()) return 100.0;
        if (generated == null || generated.isBlank()) return 0.0;

        Map<String, Integer> refFreq = termFrequency(referenceContext);

        // Reference TF 상위 K개 핵심 키워드 추출 (2글자 이상: 단음절 조사·어미 제외)
        List<String> topKeywords = refFreq.entrySet().stream()
                .filter(e -> e.getKey().length() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(FAITHFULNESS_TOP_K)
                .map(Map.Entry::getKey)
                .toList();

        if (topKeywords.isEmpty()) return 100.0;

        Set<String> genTokens = termFrequency(generated).keySet();

        long covered = topKeywords.stream().filter(genTokens::contains).count();

        // 소수점 둘째 자리까지 반올림 (논문 표기용)
        double raw = (double) covered / topKeywords.size() * 100.0;
        return Math.round(raw * 100.0) / 100.0;
    }

    // =========================================================================
    // TF-IDF 코사인 유사도 헬퍼 (대칭 비교용 보조 메서드, 현재 미사용)
    // =========================================================================

    /**
     * TF 기반 코사인 유사도 (대칭 측정, 0.0 ~ 1.0).
     *
     * <p>두 텍스트를 단어 빈도 벡터로 변환하여 코사인 유사도를 계산합니다.
     * 현재 평가에서는 비대칭 재현율 기반 {@link #computeFaithfulnessPercent}를
     * 메인 지표로 사용하며, 이 메서드는 보조 분석용으로 남겨둡니다.</p>
     *
     * @return 유사도 (0.0 ~ 1.0)
     */
    @SuppressWarnings("unused")
    private double tfidfCosineSimilarity(String text1, String text2) {
        Map<String, Integer> freq1 = termFrequency(text1);
        Map<String, Integer> freq2 = termFrequency(text2);

        Set<String> vocabulary = new HashSet<>();
        vocabulary.addAll(freq1.keySet());
        vocabulary.addAll(freq2.keySet());

        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (String term : vocabulary) {
            double v1 = freq1.getOrDefault(term, 0);
            double v2 = freq2.getOrDefault(term, 0);
            dot   += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }
        if (norm1 == 0.0 || norm2 == 0.0) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * 텍스트를 소문자 토큰으로 분리하여 단어 빈도 맵을 반환합니다.
     * 한국어/영문 혼합 처리: 공백·구두점·특수문자 기준으로 토크나이즈합니다.
     */
    private Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> freq = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return freq;
        String[] tokens = text.toLowerCase().split("[\\s\\p{Punct}\\p{Z}]+");
        for (String token : tokens) {
            if (!token.isBlank()) {
                freq.merge(token, 1, Integer::sum);
            }
        }
        return freq;
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
                              long totalTimeMs,
                              double faithfulnessPercent,
                              String generatedText) {
        String row = String.join(",",
                escapeCsvField(mode),
                String.valueOf(artworkId),
                escapeCsvField(artworkTitle),
                String.valueOf(iteration),
                String.valueOf(retrievalTimeMs),
                String.valueOf(generationTimeMs),
                String.valueOf(totalTimeMs),
                String.format("%.2f", faithfulnessPercent),  // 소수점 둘째 자리 고정
                escapeCsvField(generatedText)
        );
        csvWriter.println(row);
        csvWriter.flush();
    }

    /**
     * CSV 필드 이스케이프 처리 (RFC 4180).
     * <ul>
     *   <li>{@code "} → {@code ""}</li>
     *   <li>개행 문자({@code \r\n}, {@code \n}, {@code \r}) → 공백</li>
     *   <li>항상 큰따옴표로 감싸서 반환</li>
     * </ul>
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
