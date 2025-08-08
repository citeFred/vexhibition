package com.meta.vexhibition.exhibition.service;

import com.meta.vexhibition.exhibition.domain.Exhibition;
import com.meta.vexhibition.exhibition.dto.ExhibitionRequestDto;
import com.meta.vexhibition.exhibition.dto.ExhibitionResponseDto;
import com.meta.vexhibition.exhibition.repository.ExhibitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor

public class ExhibitionService {
    private final ExhibitionRepository exhibitionRepository;

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
        List<ExhibitionResponseDto> exhibitionResponseDtoList = exhibitionRepository.findAllByOrderByCreatedAtDesc()
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
    public void deleteExhibition(Long id) {
        Exhibition exhibition = findExhibition(id);
        exhibitionRepository.delete(exhibition);
    }

    private Exhibition findExhibition(Long id) {
        return exhibitionRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("해당 게시판은 존재하지 않습니다.")
        );
    }
}
