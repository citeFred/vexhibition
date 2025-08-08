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
    private String filePath;
    private Integer displayOrder;

    public FileResponseDto(File file) {
        this.id = file.getId();
        this.originalFileName = file.getOriginalFileName();
        this.storedFileName = file.getStoredFileName();
        this.filePath = file.getFilePath();
        this.displayOrder = file.getDisplayOrder();
    }
}