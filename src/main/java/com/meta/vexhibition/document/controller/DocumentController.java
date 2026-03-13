package com.meta.vexhibition.document.controller;

import com.meta.vexhibition.document.dto.DocumentResponseDto;
import com.meta.vexhibition.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/exhibitions/{exhibitionId}/productions/{productionId}/documents")
    public ResponseEntity<List<DocumentResponseDto>> uploadDocuments(
            @PathVariable Long exhibitionId,
            @PathVariable Long productionId,
            @RequestParam("files") List<MultipartFile> files) {
        List<DocumentResponseDto> response = documentService.uploadAndIndexAll(exhibitionId, productionId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/exhibitions/{exhibitionId}/productions/{productionId}/documents")
    public ResponseEntity<List<DocumentResponseDto>> getDocuments(
            @PathVariable Long exhibitionId,
            @PathVariable Long productionId) {
        List<DocumentResponseDto> response = documentService.getDocuments(exhibitionId, productionId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/exhibitions/{exhibitionId}/productions/{productionId}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long exhibitionId,
            @PathVariable Long productionId,
            @PathVariable Long documentId) {
        documentService.deleteDocument(exhibitionId, productionId, documentId);
        return ResponseEntity.noContent().build();
    }
}
