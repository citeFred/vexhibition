package com.meta.vexhibition.production.service;

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

@Service
@RequiredArgsConstructor
public class ProductionService {
    private final ProductionRepository productionRepository;
    private final ExhibitionRepository exhibitionRepository;
    private final FileService fileService;
    private final FileRepository fileRepository;

    @Transactional
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
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file != null && !file.isEmpty()) {
                    fileService.uploadFile(savedProduction, file, i);
                }
            }
        }

        return new ProductionResponseDto(savedProduction);
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

    @Transactional
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
                    .mapToInt(File::getDisplayOrder)
                    .max()
                    .orElse(-1);

            for (int i = 0; i < addFiles.size(); i++) {
                MultipartFile file = addFiles.get(i);
                if (!file.isEmpty()) {
                    fileService.uploadFile(production, file, maxOrder + 1 + i);
                }
            }
        }

        return new ProductionResponseDto(production);
    }

    @Transactional
    public void deleteProduction(Long exhibitionId, Long productionId) {
        Production production = getValidExhibitionAndProduction(exhibitionId, productionId);

        if (production.getFiles() != null && !production.getFiles().isEmpty()) {
            production.getFiles().forEach(file -> {
                fileService.deleteFile(file.getStoredFileName());
            });
        }

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