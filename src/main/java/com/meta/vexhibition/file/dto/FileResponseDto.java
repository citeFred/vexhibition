package com.meta.vexhibition.file.dto;

import com.meta.vexhibition.file.domain.File;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FileResponseDto {
    private Long id;
    private String originalFileName;
    private String storedFileName;
    private String path;
    private Integer displayOrder;

    public FileResponseDto(File file) {
        this.id = file.getId();
        this.originalFileName = file.getOriginalFileName();
        this.storedFileName = file.getStoredFileName();
        this.path = file.getPath();
        this.displayOrder = file.getDisplayOrder();
    }
}