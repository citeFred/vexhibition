package com.meta.vexhibition.admin.controller;

import com.meta.vexhibition.ai.service.RagService;
import com.meta.vexhibition.document.domain.Document;
import com.meta.vexhibition.document.repository.DocumentRepository;
import com.meta.vexhibition.production.domain.Production;
import com.meta.vexhibition.production.repository.ProductionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/admin/api/rag")
@RequiredArgsConstructor
public class AdminRagController {

    private final RagService ragService;
    private final ProductionRepository productionRepository;
    private final DocumentRepository documentRepository;

    /**
     * 전체 작품 description을 pgvector에 일괄 인덱싱(Embedding).
     * 데이터 마이그레이션 후 최초 1회 실행 필요합니다.
     */
    @PostMapping("/index/descriptions")
    @Transactional
    public ResponseEntity<String> indexAllDescriptions() {
        List<Production> productions = productionRepository.findAll();
        productions.forEach(ragService::indexProduction);
        log.info("전체 작품 description 인덱싱 완료 - 총 {}개", productions.size());
        return ResponseEntity.ok("총 " + productions.size() + "개의 작품 description 인덱싱 완료");
    }

    /**
     * 특정 작품 description을 재인덱싱합니다.
     */
    @PostMapping("/index/productions/{id}/description")
    @Transactional
    public ResponseEntity<String> indexOneDescription(@PathVariable Long id) {
        Production production = productionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("작품을 찾을 수 없습니다. ID: " + id));
        ragService.indexProduction(production);
        return ResponseEntity.ok("작품 ID " + id + " description 인덱싱 완료");
    }

    /**
     * 특정 작품의 description 인덱스를 삭제합니다.
     */
    @DeleteMapping("/index/productions/{id}/description")
    public ResponseEntity<String> deleteDescriptionIndex(@PathVariable Long id) {
        ragService.deleteProductionIndex(id);
        return ResponseEntity.ok("작품 ID " + id + " description 인덱스 삭제 완료");
    }

    /**
     * 업로드된 PDF 문서 목록을 조회합니다. (인덱스 현황 확인용)
     */
    @GetMapping("/index/documents")
    public ResponseEntity<String> getDocumentIndexStatus() {
        List<Document> documents = documentRepository.findAll();
        long totalChunks = documents.stream().mapToLong(Document::getChunkCount).sum();
        String status = String.format("총 %d개 문서, %d개 청크가 인덱싱되어 있습니다.", documents.size(), totalChunks);
        return ResponseEntity.ok(status);
    }
}