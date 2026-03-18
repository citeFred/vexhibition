package com.meta.vexhibition.ai.service;

import com.meta.vexhibition.production.domain.Production;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;

    private static final int DEFAULT_TOP_K = 3;
    private static final double SIMILARITY_THRESHOLD = 0.75;

    // 논리적 ID 문자열 → 결정적 UUID (PgVectorStore는 UUID 타입만 허용)
    private String toUUID(String logicalId) {
        return UUID.nameUUIDFromBytes(logicalId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String productionDocId(Long productionId) {
        return toUUID("production-" + productionId);
    }

    private String pdfChunkId(Long documentId, int chunkIndex) {
        return toUUID("pdf-" + documentId + "-chunk-" + chunkIndex);
    }

    // =========================================================================
    // Production Description 인덱싱 (기존)
    // =========================================================================

    /**
     * Production 기본 정보를 벡터 스토어에 인덱싱합니다. (upsert)
     */
    public void indexProduction(Production production) {
        String docId = productionDocId(production.getId());
        vectorStore.delete(List.of(docId));

        Document document = new Document(
                docId,
                buildProductionContent(production),
                Map.of(
                        "productionId", String.valueOf(production.getId()),
                        "source", "description",
                        "title", production.getTitle(),
                        "teamname", production.getTeamname(),
                        "generation", String.valueOf(production.getGeneration()),
                        "exhibitionId", String.valueOf(production.getExhibition().getId())
                )
        );

        vectorStore.add(List.of(document));
        log.info("작품 description 인덱싱 완료 - ID: {}, 제목: {}", production.getId(), production.getTitle());
    }

    /**
     * Production description 인덱스를 삭제합니다.
     */
    public void deleteProductionIndex(Long productionId) {
        vectorStore.delete(List.of(productionDocId(productionId)));
        log.info("작품 description 인덱스 삭제 완료 - ID: {}", productionId);
    }

    /**
     * 현재 작품과 유사한 작품들을 description 기반으로 검색합니다. (자기 자신 제외)
     */
    public List<Document> searchSimilarProductions(String query, Long excludeProductionId) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(DEFAULT_TOP_K + 1)
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .filterExpression("source == 'description'")
                        .build()
        );

        return results.stream()
                .filter(doc -> !doc.getId().equals(productionDocId(excludeProductionId)))
                .limit(DEFAULT_TOP_K)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // PDF 문서 청크 인덱싱 (신규)
    // =========================================================================

    /**
     * PDF 파일을 파싱 → 청크 분할 → 임베딩하여 벡터 스토어에 저장합니다.
     * 청크 ID 형식: pdf-{documentId}-chunk-{index}
     *
     * @return 저장된 청크 수 (ProductionDocument.chunkCount에 저장용)
     */
    public int indexPdfDocument(MultipartFile pdfFile, Long productionId, Long documentId) {
        // PDFBox 직접 사용 - PagePdfDocumentReader의 CharacterFactory 버그 우회
        // (빈 텍스트 요소에 charAt(0) 호출 시 StringIndexOutOfBoundsException 발생)
        List<Document> pages = parsePdfWithPdfBox(pdfFile);

        if (pages.isEmpty()) {
            log.warn("PDF에서 텍스트를 추출하지 못했습니다. documentId: {}", documentId);
            return 0;
        }

        // 토큰 단위로 청크 분할 (기본: 800토큰, 150토큰 overlap)
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> rawChunks = splitter.apply(pages);

        // 청크마다 결정적 ID + 메타데이터 부여
        List<Document> enrichedChunks = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            String chunkId = pdfChunkId(documentId, i);
            Document chunk = new Document(
                    chunkId,
                    rawChunks.get(i).getText(),
                    Map.of(
                            "productionId", String.valueOf(productionId),
                            "documentId", String.valueOf(documentId),
                            "source", "pdf",
                            "fileName", pdfFile.getOriginalFilename() != null ? pdfFile.getOriginalFilename() : "",
                            "chunkIndex", String.valueOf(i)
                    )
            );
            enrichedChunks.add(chunk);
        }

        vectorStore.add(enrichedChunks);
        log.info("PDF 인덱싱 완료 - documentId: {}, 총 {}개 청크", documentId, enrichedChunks.size());

        return enrichedChunks.size();
    }

    /**
     * 저장된 chunkCount를 이용해 PDF 청크를 모두 삭제합니다.
     */
    public void deletePdfChunks(Long documentId, int chunkCount) {
        if (chunkCount <= 0) return;

        List<String> chunkIds = IntStream.range(0, chunkCount)
                .mapToObj(i -> pdfChunkId(documentId, i))
                .collect(Collectors.toList());

        vectorStore.delete(chunkIds);
        log.info("PDF 청크 삭제 완료 - documentId: {}, {}개 청크", documentId, chunkCount);
    }

    /**
     * 특정 Production의 PDF 청크에서 관련 내용을 검색합니다. (발표자료 세부 내용)
     */
    public List<Document> searchProductionPdfChunks(Long productionId, String query) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)
                        .filterExpression("productionId == '" + productionId + "' && source == 'pdf'")
                        .build()
        );
    }

    /**
     * 특정 Production의 PDF 청크 전체를 가져옵니다. (비교 검증용 ground truth 구성)
     * similarityThreshold=0.0 으로 유사도 필터 없이 메타데이터 필터만 적용합니다.
     */
    public List<Document> getAllProductionPdfChunks(Long productionId, String query) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(200)
                        .similarityThreshold(0.0)
                        .filterExpression("productionId == '" + productionId + "' && source == 'pdf'")
                        .build()
        );
    }

    // =========================================================================
    // 공통 유틸
    // =========================================================================

    /**
     * Document 리스트를 프롬프트용 문자열로 변환합니다.
     */
    public String buildContextFromDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            return "없음";
        }
        return documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String buildProductionContent(Production production) {
        return String.format(
                "팀명: %s | %d기 | 작품명: %s\n%s",
                production.getTeamname(),
                production.getGeneration(),
                production.getTitle(),
                production.getDescription()
        );
    }

    /**
     * PDFBox로 PDF를 페이지 단위 파싱합니다.
     * PagePdfDocumentReader 대신 사용 — 한국어/특수 폰트의 빈 텍스트 요소에도 안전합니다.
     */
    private List<Document> parsePdfWithPdfBox(MultipartFile pdfFile) {
        try (PDDocument pdDoc = Loader.loadPDF(pdfFile.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<Document> pages = new ArrayList<>();
            int totalPages = pdDoc.getNumberOfPages();
            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdDoc);
                if (text != null && !text.isBlank()) {
                    pages.add(new Document(text));
                }
            }
            if (pages.isEmpty()) {
                log.warn("PDF 텍스트 추출 결과 없음 (스캔 이미지 PDF이거나 텍스트 레이어 없음): {}", pdfFile.getOriginalFilename());
            }
            return pages;
        } catch (IOException e) {
            throw new RuntimeException("PDF 파싱에 실패했습니다: " + pdfFile.getOriginalFilename(), e);
        }
    }

}
