package com.meta.vexhibition.document.service;

import com.meta.vexhibition.ai.service.RagService;
import com.meta.vexhibition.document.domain.Document;
import com.meta.vexhibition.document.dto.DocumentResponseDto;
import com.meta.vexhibition.document.repository.DocumentRepository;
import com.meta.vexhibition.file.domain.File;
import com.meta.vexhibition.file.repository.FileRepository;
import com.meta.vexhibition.file.service.FileService;
import com.meta.vexhibition.production.domain.Production;
import com.meta.vexhibition.production.repository.ProductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ProductionRepository productionRepository;
    private final FileService fileService;
    private final FileRepository fileRepository;
    private final RagService ragService;

    /**
     * PDF 업로드 → S3 저장(FileService) → File 엔티티 생성 → Document 저장 → pgvector 인덱싱
     */
    public DocumentResponseDto uploadAndIndex(Long exhibitionId, Long productionId, MultipartFile pdfFile) {
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        if (!"application/pdf".equals(pdfFile.getContentType())) {
            throw new IllegalArgumentException("PDF 파일만 업로드할 수 있습니다.");
        }

        Production production = getValidProduction(exhibitionId, productionId);

        // S3 업로드 + File 엔티티 생성 (FileService 재사용, displayOrder = null)
        File file = fileService.uploadDocumentFile(production, pdfFile);

        // Document 저장 (chunkCount는 인덱싱 후 업데이트)
        Document document = new Document(production, file);
        Document saved = documentRepository.save(document);

        // PDF 파싱 → 청크 분할 → pgvector 인덱싱
        int chunkCount = ragService.indexPdfDocument(pdfFile, productionId, saved.getId());
        saved.setChunkCount(chunkCount);

        return new DocumentResponseDto(saved);
    }

    /**
     * 특정 Production의 문서 목록 조회
     */
    @Transactional(readOnly = true)
    public List<DocumentResponseDto> getDocuments(Long exhibitionId, Long productionId) {
        getValidProduction(exhibitionId, productionId);
        return documentRepository
                .findByProductionIdAndProductionExhibitionId(productionId, exhibitionId)
                .stream()
                .map(DocumentResponseDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 문서 삭제: pgvector 청크 → S3 → File DB → Document DB 순으로 삭제
     */
    public void deleteDocument(Long exhibitionId, Long productionId, Long documentId) {
        getValidProduction(exhibitionId, productionId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다. ID: " + documentId));

        if (!document.getProduction().getId().equals(productionId)) {
            throw new IllegalArgumentException("해당 작품의 문서가 아닙니다.");
        }

        deleteDocumentInternal(document);
    }

    /**
     * Production 삭제 시 연관된 모든 문서를 정리합니다.
     * ProductionService, ExhibitionService에서 호출됩니다.
     */
    public void deleteAllByProductionId(Long productionId) {
        List<Document> documents = documentRepository.findByProductionId(productionId);
        documents.forEach(this::deleteDocumentInternal);
    }

    // pgvector 청크 삭제 → S3 삭제 → File DB 삭제 → Document DB 삭제
    private void deleteDocumentInternal(Document document) {
        ragService.deletePdfChunks(document.getId(), document.getChunkCount());
        fileService.deleteFile(document.getFile().getStoredFileName()); // S3 삭제
        fileRepository.delete(document.getFile());                      // File DB 삭제
        documentRepository.delete(document);                            // Document DB 삭제
    }

    private Production getValidProduction(Long exhibitionId, Long productionId) {
        return productionRepository.findByIdAndExhibitionId(productionId, exhibitionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 전시회(ID: " + exhibitionId + ")에서 작품(ID: " + productionId + ")을 찾을 수 없습니다."));
    }
}
