package com.meta.vexhibition.exhibition.service;

import com.meta.vexhibition.ai.service.RagService;
import com.meta.vexhibition.document.service.DocumentService;
import com.meta.vexhibition.exhibition.domain.Exhibition;
import com.meta.vexhibition.exhibition.dto.ExhibitionRequestDto;
import com.meta.vexhibition.exhibition.dto.ExhibitionResponseDto;
import com.meta.vexhibition.exhibition.repository.ExhibitionRepository;
import com.meta.vexhibition.file.service.FileService;
import com.meta.vexhibition.production.domain.Production;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor

public class ExhibitionService {
    private final ExhibitionRepository exhibitionRepository;
    private final FileService fileService;
    private final DocumentService documentService;
    private final RagService ragService;

    @Transactional
    public ExhibitionResponseDto createExhibition(ExhibitionRequestDto exhibitionRequestDto) {
        Exhibition exhibition = new Exhibition(
                exhibitionRequestDto.getTitle()
        );
        Exhibition savedExhibition = exhibitionRepository.save(exhibition);
        ExhibitionResponseDto exhibitionResponseDto = new ExhibitionResponseDto(savedExhibition);
        return exhibitionResponseDto;
    }

    @Transactional(readOnly = true)
    public List<ExhibitionResponseDto> getExhibitions() {
        List<ExhibitionResponseDto> exhibitionResponseDtoList = exhibitionRepository.findAll()
                .stream()
                .map(ExhibitionResponseDto::new)
                .toList();
        return exhibitionResponseDtoList;
    }

    @Transactional(readOnly = true)
    public ExhibitionResponseDto getExhibitionById(Long id) {
        Exhibition exhibition = findExhibition(id);
        return new ExhibitionResponseDto(exhibition);
    }

    @Transactional
    public ExhibitionResponseDto updateExhibition(Long id, ExhibitionRequestDto exhibitionRequestDto) {
        Exhibition exhibition = findExhibition(id);
        exhibition.update(
                exhibitionRequestDto.getTitle()
        );
        return new ExhibitionResponseDto(exhibition);
    }

    @Transactional
    public void deleteExhibition(Long exhibitionId) {
        Exhibition exhibition = exhibitionRepository.findById(exhibitionId)
                .orElseThrow(() -> new IllegalArgumentException("ID에 해당하는 전시회를 찾을 수 없습니다."));

        if (exhibition.getProductions() != null && !exhibition.getProductions().isEmpty()) {
            for (Production production : exhibition.getProductions()) {
                if (production.getFiles() != null && !production.getFiles().isEmpty()) {
                    production.getFiles().forEach(file -> fileService.deleteFile(file.getStoredFileName()));
                }
                documentService.deleteAllByProductionId(production.getId()); // PDF 문서 + pgvector 청크 삭제
                ragService.deleteProductionIndex(production.getId());         // description 벡터 인덱스 삭제
            }
        }
        exhibitionRepository.delete(exhibition);
    }

    private Exhibition findExhibition(Long id) {
        return exhibitionRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("해당 게시판은 존재하지 않습니다.")
        );
    }
}
