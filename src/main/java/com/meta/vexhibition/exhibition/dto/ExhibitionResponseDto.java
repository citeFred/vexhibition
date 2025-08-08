package com.meta.vexhibition.exhibition.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.meta.vexhibition.exhibition.domain.Exhibition;
import com.meta.vexhibition.project.dto.ProjectResponseDto;
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
    private List<ProjectResponseDto> projects;

    public ExhibitionResponseDto(Exhibition exhibition) {
        this.id = exhibition.getId();
        this.title = exhibition.getTitle();
        this.createAt = exhibition.getCreatedAt();
        this.modifiedAt = exhibition.getModifiedAt();
        this.projects = exhibition.getProjects()
                .stream()
                .map(ProjectResponseDto::new)
                .collect(Collectors.toList()
                );
    }
}
