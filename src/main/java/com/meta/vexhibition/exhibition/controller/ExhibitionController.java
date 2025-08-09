package com.meta.vexhibition.exhibition.controller;

import com.meta.vexhibition.common.ApiResponseDto;
import com.meta.vexhibition.exhibition.dto.ExhibitionRequestDto;
import com.meta.vexhibition.exhibition.dto.ExhibitionResponseDto;
import com.meta.vexhibition.exhibition.service.ExhibitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExhibitionController {
    private final ExhibitionService exhibitionService;

    /**
     * For UnrealEngine Request&Response
     * @param id
     * @return Project DATA list
     */
    @GetMapping("/exhibitions/{id}/public")
    public ResponseEntity<ApiResponseDto<?>> getPublicExhibitionById(@PathVariable Long id) {
        try {
            ExhibitionResponseDto exhibitionData = exhibitionService.getExhibitionById(id);

            ApiResponseDto<ExhibitionResponseDto> successResponse = new ApiResponseDto<>(
                    exhibitionData,
                    "전시회 정보 조회에 성공했습니다"
            );

            return ResponseEntity.ok(successResponse);

        } catch (IllegalArgumentException ex) {

            ApiResponseDto<?> errorResponse = new ApiResponseDto<>(
                    HttpStatus.NOT_FOUND,
                    ex.getMessage()
            );

            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }
    }


    @PostMapping("/exhibitions")
    public ResponseEntity<ExhibitionResponseDto> createExhibition(@RequestBody ExhibitionRequestDto exhibitionRequestDto) {
        ExhibitionResponseDto exhibitionResponseDto = exhibitionService.createExhibition(exhibitionRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(exhibitionResponseDto);
    }

    @GetMapping("/exhibitions")
    public ResponseEntity<List<ExhibitionResponseDto>> getExhibitions() {
        List<ExhibitionResponseDto> exhibitionResponseDtoList = exhibitionService.getExhibitions();
        return ResponseEntity.ok(exhibitionResponseDtoList);
    }

    @GetMapping("/exhibitions/{id}")
    public ResponseEntity<ExhibitionResponseDto> getExhibitionById(@PathVariable Long id) {
        ExhibitionResponseDto exhibitionResponseDto = exhibitionService.getExhibitionById(id);
        return ResponseEntity.ok(exhibitionResponseDto);
    }

    @PutMapping("/exhibitions/{id}")
    public ResponseEntity<ExhibitionResponseDto> updateExhibition(@PathVariable Long id, @RequestBody ExhibitionRequestDto exhibitionRequestDto) {
        ExhibitionResponseDto updatedExhibition = exhibitionService.updateExhibition(id, exhibitionRequestDto);
        return ResponseEntity.ok(updatedExhibition);
    }

    @DeleteMapping("/exhibitions/{id}")
    public ResponseEntity<Void> deleteExhibition(@PathVariable Long id) {
        exhibitionService.deleteExhibition(id);
        return ResponseEntity.noContent().build();
    }
}