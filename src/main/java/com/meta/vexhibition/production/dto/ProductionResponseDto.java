package com.meta.vexhibition.production.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.meta.vexhibition.file.dto.FileResponseDto;
import com.meta.vexhibition.production.domain.Production;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors; // Collectors import 추가

@Getter
@NoArgsConstructor
public class ProductionResponseDto {
    private Long id;
    private String teamname;
    private int generation;
    private String title;
    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    private List<FileResponseDto> files;

    public ProductionResponseDto(Production production) {
        this.id = production.getId();
        this.teamname = production.getTeamname();
        this.title = production.getTitle();
        this.generation = production.getGeneration();
        this.description = production.getDescription();
        this.createAt = production.getCreatedAt();
        this.modifiedAt = production.getModifiedAt();

        if (production.getFiles() != null) {
            this.files = production.getFiles().stream()
                    .map(FileResponseDto::new)
                    .collect(Collectors.toList());
        } else {
            this.files = Collections.emptyList(); // 파일이 없는 경우 빈 리스트 할당
        }
    }
}