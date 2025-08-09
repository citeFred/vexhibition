package com.meta.vexhibition.ai.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AudioResponseDto {

    // Base64로 인코딩된 오디오 데이터 문자열을 담을 필드
    private final String stream;

}