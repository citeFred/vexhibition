package com.meta.vexhibition.production.controller;

import com.meta.vexhibition.production.dto.ProductionRequestDto;
import com.meta.vexhibition.production.dto.ProductionResponseDto;
import com.meta.vexhibition.production.dto.ProductionUpdateRequestDto;
import com.meta.vexhibition.production.service.ProductionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductionController {
    private final ProductionService productionService;

    @PostMapping("/exhibitions/{exhibitionId}/productions")
    public ResponseEntity<ProductionResponseDto> createProductionForExhibition(
            @PathVariable Long exhibitionId,
            @ModelAttribute ProductionRequestDto productionRequestDto,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        ProductionResponseDto createdProduction = productionService.createProduction(exhibitionId, productionRequestDto, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProduction);
    }

    @GetMapping("/exhibitions/{exhibitionId}/productions")
    public ResponseEntity<Page<ProductionResponseDto>> getProductionsByExhibitionId(
            @PathVariable Long exhibitionId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductionResponseDto> productionResponseDtoPage = productionService.getProductionsByExhibitionId(exhibitionId, pageable);
        return ResponseEntity.ok(productionResponseDtoPage);
    }

    @GetMapping("/productions")
    public ResponseEntity<Page<ProductionResponseDto>> getProductions(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductionResponseDto> productionResponseDtoPage = productionService.getProductions(pageable);
        return ResponseEntity.ok(productionResponseDtoPage);
    }

    @GetMapping("/exhibitions/{exhibitionId}/productions/{id}")
    public ResponseEntity<ProductionResponseDto> getProductionById(
            @PathVariable Long exhibitionId,
            @PathVariable Long id) {
        ProductionResponseDto productionResponseDto = productionService.getProductionById(exhibitionId, id);
        return ResponseEntity.ok(productionResponseDto);
    }

    @PutMapping("/exhibitions/{exhibitionId}/productions/{id}")
    public ResponseEntity<ProductionResponseDto> updateProduction(
            @PathVariable Long exhibitionId,
            @PathVariable Long id,
            @ModelAttribute ProductionUpdateRequestDto productionUpdateRequestDto,
            @RequestParam(value = "addFiles", required = false) List<MultipartFile> addFiles) {
        ProductionResponseDto updatedProduction = productionService.updateProduction(exhibitionId, id, productionUpdateRequestDto, addFiles);
        return ResponseEntity.ok(updatedProduction);
    }

    @DeleteMapping("/exhibitions/{exhibitionId}/productions/{id}")
    public ResponseEntity<Void> deleteProduction(
            @PathVariable Long exhibitionId,
            @PathVariable Long id) {
        productionService.deleteProduction(exhibitionId, id);
        return ResponseEntity.noContent().build();
    }
}