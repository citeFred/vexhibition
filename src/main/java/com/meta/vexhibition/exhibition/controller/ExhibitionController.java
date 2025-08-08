package com.meta.vexhibition.exhibition.controller;

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