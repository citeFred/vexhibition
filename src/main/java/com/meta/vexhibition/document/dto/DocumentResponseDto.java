package com.meta.vexhibition.document.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.meta.vexhibition.document.domain.Document;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class DocumentResponseDto {

    private final Long id;
    private final String originalFileName;
    private final String url;
    private final int chunkCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime createdAt;

    public DocumentResponseDto(Document document) {
        this.id = document.getId();
        this.originalFileName = document.getFile().getOriginalFileName();
        this.url = document.getFile().getPath();
        this.chunkCount = document.getChunkCount();
        this.createdAt = document.getCreatedAt();
    }
}
