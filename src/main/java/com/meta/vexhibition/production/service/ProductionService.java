package com.meta.vexhibition.production.service;

import com.meta.vexhibition.ai.service.RagService;
import com.meta.vexhibition.document.service.DocumentService;
import com.meta.vexhibition.exhibition.domain.Exhibition;
import com.meta.vexhibition.exhibition.repository.ExhibitionRepository;
import com.meta.vexhibition.file.domain.File;
import com.meta.vexhibition.file.repository.FileRepository;
import com.meta.vexhibition.file.service.FileService;
import com.meta.vexhibition.production.domain.Production;
import com.meta.vexhibition.production.dto.ProductionRequestDto;
import com.meta.vexhibition.production.dto.ProductionResponseDto;
import com.meta.vexhibition.production.dto.ProductionUpdateRequestDto;
import com.meta.vexhibition.production.repository.ProductionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductionService {
    private final ProductionRepository productionRepository;
    private final ExhibitionRepository exhibitionRepository;
    private final FileService fileService;
    private final FileRepository fileRepository;
    private final RagService ragService;
    private final DocumentService documentService;

    public ProductionResponseDto createProduction(Long exhibitionId, ProductionRequestDto productionRequestDto, List<MultipartFile> files) {
        Exhibition exhibition = getValidExhibition(exhibitionId);

        Production production = new Production(
                productionRequestDto.getTeamname(),
                productionRequestDto.getTitle(),
                productionRequestDto.getGeneration(),
                productionRequestDto.getDescription(),
                exhibition
        );
        Production savedProduction = productionRepository.save(production);

        if (files != null && !files.isEmpty()) {
            int currentOrder = 0;
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    String contentType = file.getContentType();
                    if (Objects.equals(contentType, "image/gif")) {
                        // GIF → 프레임 분할 업로드
                        int frameCount = fileService.uploadGifAsFrames(savedProduction, file, currentOrder);
                        currentOrder += frameCount;
                    } else if (Objects.equals(contentType, "application/pdf")) {
                        // PDF → 페이지별 PNG 이미지 변환 업로드 (패널 전시용)
                        int pageCount = fileService.uploadPdfAsImages(savedProduction, file, currentOrder);
                        currentOrder += pageCount;
                    } else {
                        // 일반 이미지 (JPG, PNG 등) → 그대로 업로드
                        fileService.uploadFile(savedProduction, file, currentOrder);
                        currentOrder++;
                    }
                }
            }
        }

        ragService.indexProduction(savedProduction);

        return new ProductionResponseDto(savedProduction);
    }

    public ProductionResponseDto updateProduction(Long exhibitionId, Long productionId, ProductionUpdateRequestDto requestDto,
                                                  List<MultipartFile> addFiles) {
        Production production = getValidExhibitionAndProduction(exhibitionId, productionId);

        production.update(requestDto.getTitle(), requestDto.getDescription(), requestDto.getTeamname(), requestDto.getGeneration());

        List<Long> deleteFileIds = requestDto.getDeleteFileIds();
        if (deleteFileIds != null && !deleteFileIds.isEmpty()) {
            List<File> filesToDelete = fileRepository.findAllById(deleteFileIds);
            filesToDelete.forEach(file -> {
                fileService.deleteFile(file.getStoredFileName());
                production.getFiles().remove(file);
            });
        }

        if (addFiles != null && !addFiles.isEmpty()) {
            int maxOrder = production.getFiles().stream()
                    .filter(f -> f.getDisplayOrder() != null)
                    .mapToInt(File::getDisplayOrder)
                    .max()
                    .orElse(-1);

            int currentOrder = maxOrder + 1;

            for (MultipartFile file : addFiles) {
                if (file != null && !file.isEmpty()) {
                    String contentType = file.getContentType();
                    if (Objects.equals(contentType, "image/gif")) {
                        int frameCount = fileService.uploadGifAsFrames(production, file, currentOrder);
                        currentOrder += frameCount;
                    } else if (Objects.equals(contentType, "application/pdf")) {
                        int pageCount = fileService.uploadPdfAsImages(production, file, currentOrder);
                        currentOrder += pageCount;
                    } else {
                        fileService.uploadFile(production, file, currentOrder);
                        currentOrder++;
                    }
                }
            }
        }

        ragService.indexProduction(production);

        return new ProductionResponseDto(production);
    }

    @Transactional(readOnly = true)
    public Page<ProductionResponseDto> getProductions(Pageable pageable) {
        Page<Production> productionPage = productionRepository.findAll(pageable);
        return productionPage.map(ProductionResponseDto::new);
    }

    @Transactional(readOnly = true)
    public Page<ProductionResponseDto> getProductionsByExhibitionId(Long exhibitionId, Pageable pageable) {
        getValidExhibition(exhibitionId);
        Page<Production> productionPage = productionRepository.findByExhibitionId(exhibitionId, pageable);
        return productionPage.map(ProductionResponseDto::new);
    }

    @Transactional(readOnly = true)
    public ProductionResponseDto getProductionById(Long exhibitionId, Long productionId) {
        Production production = getValidExhibitionAndProduction(exhibitionId, productionId);
        return new ProductionResponseDto(production);
    }

    public void deleteProduction(Long exhibitionId, Long productionId) {
        Production production = getValidExhibitionAndProduction(exhibitionId, productionId);

        if (production.getFiles() != null && !production.getFiles().isEmpty()) {
            production.getFiles().forEach(file -> fileService.deleteFile(file.getStoredFileName()));
        }

        documentService.deleteAllByProductionId(production.getId()); // PDF 문서 + pgvector 청크 삭제
        ragService.deleteProductionIndex(production.getId());         // description 벡터 인덱스 삭제
        productionRepository.delete(production);
    }

    public Exhibition getValidExhibition(Long exhibitionId) {
        return exhibitionRepository.findById(exhibitionId).orElseThrow(() ->
                new IllegalArgumentException("해당 전시회를 찾을 수 없습니다. Exhibition ID: " + exhibitionId)
        );
    }

    public Production getValidExhibitionAndProduction(Long exhibitionId, Long productionId) {
        getValidExhibition(exhibitionId);

        return productionRepository.findByIdAndExhibitionId(productionId, exhibitionId).orElseThrow(() ->
                new IllegalArgumentException("해당 전시회(ID: " + exhibitionId + ")에서 작품(ID: " + productionId + ")을 찾을 수 없습니다.")
        );
    }
}
