package com.meta.vexhibition.production.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.meta.vexhibition.file.domain.File;
import com.meta.vexhibition.file.dto.GroupedFileResponseDto;
import com.meta.vexhibition.production.domain.Production;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor
public class ProductionResponseDto {
    private Long id;
    private String teamname;
    private int generation;
    private String title;
    private String description;
    private String exhibitionTitle;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime modifiedAt;

    private List<GroupedFileResponseDto> files; // DTO 타입 변경

    public ProductionResponseDto(Production production) {
        this.id = production.getId();
        this.teamname = production.getTeamname();
        this.generation = production.getGeneration();
        this.title = production.getTitle();
        this.description = production.getDescription();
        this.createAt = production.getCreatedAt();
        this.modifiedAt = production.getModifiedAt();

        if (production.getExhibition() != null) {
            this.exhibitionTitle = production.getExhibition().getTitle();
        }

        // --- 파일 그룹화 로직 ---
        if (production.getFiles() != null && !production.getFiles().isEmpty()) {
            List<File> sortedFiles = production.getFiles().stream()
                    .sorted(Comparator.comparing(File::getDisplayOrder))
                    .collect(Collectors.toList());

            List<GroupedFileResponseDto> resultList = new ArrayList<>();
            Map<String, List<File>> groupedByBaseName = new LinkedHashMap<>();

            // GIF 프레임들을 베이스 이름으로 그룹화
            for (File file : sortedFiles) {
                String originalName = file.getOriginalFileName();
                if (originalName != null && originalName.matches(".*_frame_\\d+\\.png")) {
                    String baseName = originalName.substring(0, originalName.lastIndexOf("_frame_"));
                    groupedByBaseName.computeIfAbsent(baseName, k -> new ArrayList<>()).add(file);
                } else {
                    // 정적 이미지는 바로 결과 리스트에 추가
                    resultList.add(new GroupedFileResponseDto(file));
                }
            }

            // 그룹화된 GIF 프레임들을 결과 리스트에 추가
            for (List<File> gifFrames : groupedByBaseName.values()) {
                resultList.add(new GroupedFileResponseDto(gifFrames));
            }

            // 정적 이미지와 GIF 그룹이 올바른 순서로 정렬되도록 최종 소팅
            resultList.sort(Comparator.comparing(GroupedFileResponseDto::getDisplayOrder));

            this.files = resultList;
        } else {
            this.files = List.of();
        }
    }
}