package com.meta.vexhibition.project.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.meta.vexhibition.file.dto.FileResponseDto;
import com.meta.vexhibition.project.domain.Project;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors; // Collectors import 추가

@Getter
@NoArgsConstructor
public class ProjectResponseDto {
    private Long id;
    private String title;
    private String content;
    private String exhibitionTitle;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    private List<FileResponseDto> files;

    public ProjectResponseDto(Project project) {
        this.id = project.getId();
        this.title = project.getTitle();
        this.content = project.getContent();
        this.createAt = project.getCreatedAt();
        this.modifiedAt = project.getModifiedAt();

        if (project.getExhibition() != null) {
            this.exhibitionTitle = project.getExhibition().getTitle();
        }

        if (project.getFiles() != null) {
            this.files = project.getFiles().stream()
                    .map(FileResponseDto::new)
                    .collect(Collectors.toList());
        } else {
            this.files = Collections.emptyList(); // 파일이 없는 경우 빈 리스트 할당
        }
    }
}