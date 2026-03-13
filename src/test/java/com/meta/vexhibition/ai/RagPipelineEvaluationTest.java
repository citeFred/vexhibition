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
 * <p>목적: 기존 프롬프트(Prompt-Only) 방식과 RAG 방식을 50회씩 반복 실행하여
 * 응답 지연 시간(Latency) 및 Ground Truth 대비 코사인 유사도(Similarity)를 측정하고
 * 프로젝트 루트의 {@code docent_evaluation_results.csv} 파일로 내보냅니다.</p>
 *
 * <p><b>실행 전 필수 조건:</b>
 * <ul>
 *   <li>PostgreSQL (pgvector 확장 포함) 서버 구동</li>
 *   <li>application.properties의 OpenAI API 키 유효</li>
 *   <li>DB에 평가 대상 Production 데이터 10건 이상 존재</li>
 * </ul>
 * </p>
 */
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagPipelineEvaluationTest {

    // =========================================================================
    // 상수
    // =========================================================================

    private static final int TARGET_ARTWORK_COUNT    = 10;
    private static final int ITERATIONS_PER_ARTWORK  = 5;
    private static final String CSV_FILE_PATH        = "docent_evaluation_results.csv";
    private static final String EMBEDDING_MODEL      = "text-embedding-ada-002";

    /**
     * CSV 헤더
     * - Mode            : 실행 방식 (PROMPT_ONLY / RAG)
     * - ArtworkID       : Production PK
     * - ArtworkTitle    : 작품명
     * - Iteration       : 반복 번호 (1~5)
     * - RetrievalTime(ms): RAG 검색 단계 소요 시간 (PROMPT_ONLY는 0)
     * - GenerationTime(ms): LLM 텍스트 생성 단계 소요 시간
     * - TotalTime(ms)   : 전체 소요 시간
     * - Similarity(%)   : Ground Truth 대비 코사인 유사도 (0~100)
     * - GeneratedText   : 생성된 도슨트 대본
     */
    private static final String CSV_HEADER =
            "Mode,ArtworkID,ArtworkTitle,Iteration," +
            "RetrievalTime(ms),GenerationTime(ms),TotalTime(ms)," +
            "Similarity(%),GeneratedText";

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
        // Given: 평가 대상 작품 10개 및 Ground Truth 준비
        // -----------------------------------------------------------------------

        List<Production> productions = productionRepository.findAll()
                .stream()
                .limit(TARGET_ARTWORK_COUNT)
                .toList();

        assertThat(productions)
                .as("평가 대상 Production이 DB에 최소 1건 이상 있어야 합니다.")
                .isNotEmpty();

        log.info("=== 평가 시작: {}개 작품 × {}회 반복 × 2가지 방식(Prompt/RAG) ===",
                productions.size(), ITERATIONS_PER_ARTWORK);

        // 작품별 Ground Truth 구성 (description + 모든 PDF 청크)
        // 반복 측정 시 매번 재조회하지 않도록 Map에 미리 캐싱
        Map<Long, String> groundTruthMap = buildGroundTruthMap(productions);

        int totalRows = 0;

        for (Production production : productions) {
            Long   artworkId = production.getId();
            String groundTruth = groundTruthMap.get(artworkId);

            for (int iteration = 1; iteration <= ITERATIONS_PER_ARTWORK; iteration++) {

                log.info("진행 [{}/{}] ArtworkID={}, Iteration={}/{}",
                        ++totalRows,
                        productions.size() * ITERATIONS_PER_ARTWORK * 2,
                        artworkId, iteration, ITERATIONS_PER_ARTWORK);

                // -----------------------------------------------------------------------
                // When & Then: [1] Prompt-Only 방식 측정
                // -----------------------------------------------------------------------
                runPromptOnlyEvaluation(production, groundTruth, iteration);

                // -----------------------------------------------------------------------
                // When & Then: [2] RAG 방식 측정
                // -----------------------------------------------------------------------
                runRagEvaluation(production, groundTruth, iteration);
            }
        }

        log.info("=== 전체 {}회 측정 완료 ===", totalRows * 2);
    }

    // =========================================================================
    // Prompt-Only 평가 (기존 프롬프트 방식)
    // =========================================================================

    /**
     * 기존 프롬프트 방식(RAG 없음)으로 도슨트 대본을 생성하고 결과를 CSV에 기록합니다.
     */
    private void runPromptOnlyEvaluation(Production production, String groundTruth, int iteration) {
        Long artworkId = production.getId();
        StopWatch stopWatch = new StopWatch("PROMPT_ONLY-" + artworkId + "-iter-" + iteration);

        try {
            // Given: 프롬프트 조립 (작품 설명만 사용)
            String personaSetUp =
                    "당신은 '메타버스 아카데미 수료작품 전시회'의 전문 AI 도슨트 '벡시(Vexi)'입니다. " +
                    "관람객에게 항상 존댓말을 사용하며, 친절하고 흥미로운 톤으로 작품을 설명해야 합니다.";

            String userTask =
                    "아래 작품 설명을 바탕으로 60초 내외의 창의적인 도슨트 안내 대본을 작성해줘. " +
                    "환영인사나 자기소개는 생략하고 바로 작품 설명부터 시작해줘. " +
                    "이 작품의 목적과 장점을 잘 분석해서 포함하고, " +
                    "마지막에는 관람객의 흥미를 유발하는 질문을 던지며 마무리해줘.\n\n" +
                    "--- 원본 설명 ---\n" + production.getDescription();

            // When: LLM 생성 시간 측정 (Prompt-Only는 Retrieval 없음)
            stopWatch.start("generation");
            String generatedScript = openAiService.generate(personaSetUp, userTask);
            stopWatch.stop();

            long generationTime = stopWatch.getLastTaskTimeMillis();
            long totalTime      = generationTime;

            // Then: 생성 결과 검증
            assertThat(generatedScript)
                    .as("ArtworkID %d, Iter %d [PROMPT_ONLY]: 생성 스크립트는 null이 아니어야 합니다.",
                            artworkId, iteration)
                    .isNotNull()
                    .isNotBlank();

            // Then: Ground Truth 대비 임베딩 코사인 유사도 계산
            double similarityPercent = computeEmbeddingCosineSimilarityPercent(generatedScript, groundTruth);

            log.info("[PROMPT_ONLY] ArtworkID={} Iter={} | 생성={}ms 총={}ms 유사도={}%",
                    artworkId, iteration, generationTime, totalTime,
                    String.format("%.2f", similarityPercent));

            // Then: CSV 기록 (스트림)
            writeCsvRow("PROMPT_ONLY", artworkId, production.getTitle(), iteration,
                    0L, generationTime, totalTime, similarityPercent, generatedScript);

        } catch (Exception e) {
            log.error("[PROMPT_ONLY] ArtworkID={}, Iter={} 측정 실패: {}",
                    artworkId, iteration, e.getMessage(), e);
            writeCsvRow("PROMPT_ONLY", artworkId, production.getTitle(), iteration,
                    -1L, -1L, -1L, -1.0, "ERROR: " + e.getMessage());
        }
    }

    // =========================================================================
    // RAG 평가 (검색 증강 생성 방식)
    // =========================================================================

    /**
     * RAG 방식으로 도슨트 대본을 생성하고 결과를 CSV에 기록합니다.
     * Retrieval과 Generation 단계를 StopWatch로 분리 측정합니다.
     */
    private void runRagEvaluation(Production production, String groundTruth, int iteration) {
        Long artworkId = production.getId();
        StopWatch stopWatch = new StopWatch("RAG-" + artworkId + "-iter-" + iteration);

        try {
            String ragQuery = production.getTitle() + " " + production.getDescription();

            // -----------------------------------------------------------------------
            // When: [Retrieval 단계] PDF 청크 검색 + 유사 작품 검색
            // -----------------------------------------------------------------------
            stopWatch.start("retrieval");

            // ① 현재 작품의 PDF 발표자료에서 관련 청크 검색
            List<Document> pdfChunks = ragService.searchProductionPdfChunks(artworkId, ragQuery);
            String pdfContext = ragService.buildContextFromDocuments(pdfChunks);

            // ② 유사 작품 description 검색 (자기 자신 제외)
            List<Document> similarDocs = ragService.searchSimilarProductions(ragQuery, artworkId);
            String similarContext = ragService.buildContextFromDocuments(similarDocs);

            stopWatch.stop();
            long retrievalTime = stopWatch.getLastTaskTimeMillis();

            // -----------------------------------------------------------------------
            // When: [Generation 단계] RAG 컨텍스트 보강 프롬프트로 LLM 생성
            // -----------------------------------------------------------------------
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
                    "작품명: " + production.getTitle() + "\n" +
                    "팀명: " + production.getTeamname() + "\n" +
                    "기수: " + production.getGeneration() + "기\n" +
                    "설명: " + production.getDescription() + "\n\n" +
                    "--- 발표자료 내용 (PDF, 우선 참고) ---\n" + pdfContext + "\n\n" +
                    "--- 유사 작품 맥락 (참고용) ---\n" + similarContext;

            stopWatch.start("generation");
            String generatedScript = openAiService.generate(personaSetUp, userTask);
            stopWatch.stop();
            long generationTime = stopWatch.getLastTaskTimeMillis();

            long totalTime = retrievalTime + generationTime;

            // -----------------------------------------------------------------------
            // Then: 생성 결과 검증
            // -----------------------------------------------------------------------
            assertThat(generatedScript)
                    .as("ArtworkID %d, Iter %d [RAG]: 생성 스크립트는 null이 아니어야 합니다.",
                            artworkId, iteration)
                    .isNotNull()
                    .isNotBlank();

            // Then: Ground Truth 대비 임베딩 코사인 유사도 계산
            double similarityPercent = computeEmbeddingCosineSimilarityPercent(generatedScript, groundTruth);

            log.info("[RAG] ArtworkID={} Iter={} | 검색={}ms 생성={}ms 총={}ms 유사도={}%",
                    artworkId, iteration, retrievalTime, generationTime, totalTime,
                    String.format("%.2f", similarityPercent));

            // Then: CSV 기록 (스트림)
            writeCsvRow("RAG", artworkId, production.getTitle(), iteration,
                    retrievalTime, generationTime, totalTime, similarityPercent, generatedScript);

        } catch (Exception e) {
            log.error("[RAG] ArtworkID={}, Iter={} 측정 실패: {}",
                    artworkId, iteration, e.getMessage(), e);
            writeCsvRow("RAG", artworkId, production.getTitle(), iteration,
                    -1L, -1L, -1L, -1.0, "ERROR: " + e.getMessage());
        }
    }

    // =========================================================================
    // Ground Truth 구성 헬퍼
    // =========================================================================

    /**
     * 각 Production의 Ground Truth를 미리 구성합니다.
     * Ground Truth = 작품 메타데이터(title, teamname, generation, description) + 전체 PDF 청크
     */
    private Map<Long, String> buildGroundTruthMap(List<Production> productions) {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Production p : productions) {
            String query = p.getTitle() + " " + p.getDescription();
            List<Document> allPdfChunks = ragService.getAllProductionPdfChunks(p.getId(), query);

            String groundTruth =
                    "작품명: " + p.getTitle() + "\n" +
                    "팀명: " + p.getTeamname() + "\n" +
                    "기수: " + p.getGeneration() + "기\n" +
                    "설명: " + p.getDescription() + "\n\n" +
                    ragService.buildContextFromDocuments(allPdfChunks);

            map.put(p.getId(), groundTruth);
            log.info("Ground Truth 구성 완료 - ArtworkID={}, PDF청크수={}", p.getId(), allPdfChunks.size());
        }
        return map;
    }

    // =========================================================================
    // 유사도 계산 헬퍼
    // =========================================================================

    /**
     * OpenAI text-embedding-ada-002 모델로 두 텍스트의 임베딩을 생성하고
     * 코사인 유사도를 백분율(%)로 반환합니다.
     *
     * @param text      평가 대상 생성 텍스트
     * @param reference Ground Truth 텍스트
     * @return 코사인 유사도 × 100 (0.0 ~ 100.0)
     */
    private double computeEmbeddingCosineSimilarityPercent(String text, String reference) {
        List<float[]> embeddings = openAiService.generateEmbedding(
                List.of(text, reference), EMBEDDING_MODEL
        );
        return cosineSimilarity(embeddings.get(0), embeddings.get(1)) * 100.0;
    }

    /**
     * float[] 임베딩 벡터 간 코사인 유사도를 계산합니다.
     *
     * <p>공식: cos(θ) = (A·B) / (‖A‖ × ‖B‖)</p>
     *
     * @return 유사도 (0.0 ~ 1.0), 영벡터 입력 시 0.0 반환
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot  += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * TF-IDF 기반 코사인 유사도 헬퍼 (API 호출 없이 오프라인 측정 시 사용).
     *
     * <p>알고리즘:
     * <ol>
     *   <li>두 텍스트를 공백/구두점으로 토크나이즈</li>
     *   <li>전체 어휘(vocabulary) 구성</li>
     *   <li>각 텍스트의 TF(Term Frequency) 벡터 생성</li>
     *   <li>두 TF 벡터 간 코사인 유사도 계산</li>
     * </ol>
     * </p>
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

        List<String> terms = new ArrayList<>(vocabulary);
        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;

        for (String term : terms) {
            double v1 = freq1.getOrDefault(term, 0);
            double v2 = freq2.getOrDefault(term, 0);
            dot   += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }

        if (norm1 == 0.0 || norm2 == 0.0) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /** 텍스트를 소문자 토큰으로 분리하여 단어 빈도 맵을 반환합니다. */
    private Map<String, Integer> termFrequency(String text) {
        Map<String, Integer> freq = new HashMap<>();
        if (text == null || text.isBlank()) return freq;
        // 한국어/영문 혼합 처리: 공백·구두점·특수문자 기준 토크나이즈
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
     * 측정 결과 1건을 CSV 파일에 스트림 방식으로 즉시 기록합니다.
     * GeneratedText 필드는 RFC 4180 규격에 따라 이스케이프합니다.
     */
    private void writeCsvRow(String mode,
                              Long artworkId,
                              String artworkTitle,
                              int iteration,
                              long retrievalTimeMs,
                              long generationTimeMs,
                              long totalTimeMs,
                              double similarityPercent,
                              String generatedText) {
        String row = String.join(",",
                escapeCsvField(mode),
                String.valueOf(artworkId),
                escapeCsvField(artworkTitle),
                String.valueOf(iteration),
                String.valueOf(retrievalTimeMs),
                String.valueOf(generationTimeMs),
                String.valueOf(totalTimeMs),
                String.format("%.2f", similarityPercent),
                escapeCsvField(generatedText)
        );
        csvWriter.println(row);
        csvWriter.flush(); // 측정마다 즉시 플러시 (테스트 중단 시 데이터 보호)
    }

    /**
     * CSV 필드 이스케이프 처리 (RFC 4180).
     *
     * <ul>
     *   <li>큰따옴표({@code "}) → {@code ""} (이중 인용)</li>
     *   <li>개행문자({@code \r\n}, {@code \n}, {@code \r}) → 공백 치환</li>
     *   <li>항상 큰따옴표로 감싸서 반환</li>
     * </ul>
     */
    private String escapeCsvField(String value) {
        if (value == null) return "\"\"";
        String escaped = value
                .replace("\"", "\"\"")          // " → ""
                .replace("\r\n", " ")            // CRLF → space
                .replace("\n",   " ")            // LF   → space
                .replace("\r",   " ");            // CR   → space
        return "\"" + escaped + "\"";
    }
}
