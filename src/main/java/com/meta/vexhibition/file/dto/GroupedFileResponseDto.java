package com.meta.vexhibition.file.dto;

import com.meta.vexhibition.file.domain.File;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 정적 이미지(파일 1개)와 애니메이션 GIF(파일 N개)를 모두 표현하기 위한 DTO.
 */
@Getter
public class GroupedFileResponseDto {
    private final Long id; // 대표 ID (정적 파일의 ID 또는 GIF 첫 프레임의 ID)
    private final String originalFileName; // 업로드된 원본 파일명 (예: "my_animation.gif")
    private final String type; // 파일 종류: "STATIC" 또는 "ANIMATED"
    private final List<String> path; // 파일 경로. STATIC은 1개, ANIMATED는 N개의 URL을 가짐.
    private final Integer displayOrder;

    /**
     * 정적 이미지 파일을 위한 생성자
     * @param file File 엔티티
     */
    public GroupedFileResponseDto(File file) {
        this.id = file.getId();
        this.originalFileName = file.getOriginalFileName();
        this.type = "STATIC";
        this.path = List.of(file.getPath()); // 경로가 1개인 리스트
        this.displayOrder = file.getDisplayOrder();
    }

    /**
     * 애니메이션 GIF 프레임들을 위한 생성자
     * @param frames 동일한 GIF에서 분할된 File 엔티티 리스트
     */
    public GroupedFileResponseDto(List<File> frames) {
        File firstFrame = frames.get(0); // 첫 번째 프레임을 기준으로 정보 설정
        this.id = firstFrame.getId();
        this.originalFileName = restoreOriginalGifName(firstFrame.getOriginalFileName());
        this.type = "ANIMATED";
        this.path = frames.stream().map(File::getPath).collect(Collectors.toList()); // 모든 프레임의 경로 리스트
        this.displayOrder = firstFrame.getDisplayOrder();
    }

    // "_frame_0.png" 같은 접미사를 제거하고 원본 GIF 파일명을 복원하는 헬퍼 메소드
    private String restoreOriginalGifName(String frameName) {
        if (frameName != null && frameName.matches(".*_frame_\\d+\\.png")) {
            return frameName.substring(0, frameName.lastIndexOf("_frame_")) + ".gif";
        }
        return frameName;
    }
}
