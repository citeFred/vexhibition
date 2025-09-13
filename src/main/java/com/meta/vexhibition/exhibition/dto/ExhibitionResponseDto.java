package com.meta.vexhibition.exhibition.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.meta.vexhibition.exhibition.domain.Exhibition;
import com.meta.vexhibition.production.dto.ProductionResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class ExhibitionResponseDto {
    private Long id;
    private String title;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDateTime createAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDateTime modifiedAt;
    private List<ProductionResponseDto> productions;

    public ExhibitionResponseDto(Exhibition exhibition) {
        this.id = exhibition.getId();
        this.title = exhibition.getTitle();
        this.createAt = exhibition.getCreatedAt();
        this.modifiedAt = exhibition.getModifiedAt();
        this.productions = exhibition.getProductions()
                .stream()
                .map(ProductionResponseDto::new)
                .collect(Collectors.toList()
                );
    }
}
