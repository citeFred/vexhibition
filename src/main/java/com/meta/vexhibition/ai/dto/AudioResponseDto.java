package com.meta.vexhibition.ai.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AudioResponseDto {
    // AI가 생성한 도슨트 대본 텍스트
    private final String script;

    // 스크립트 vs Reference(description + PDF) 코사인 유사도 (0.0 ~ 1.0)
    private final double similarityWithReference;

    // Base64로 인코딩된 오디오 데이터
    private final String stream;
}