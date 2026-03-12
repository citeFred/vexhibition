package com.meta.vexhibition.ai.controller;

import com.meta.vexhibition.ai.dto.AudioResponseDto;
import com.meta.vexhibition.ai.service.OpenAiService;
import com.meta.vexhibition.common.ApiResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OpenAiController {
    private final OpenAiService openAIService;

//    @PostMapping("/chat")
//    public String chat(@RequestBody Map<String, String> body) {
//        return openAIService.generate(body.get("text"));
//    }

    @PostMapping("/chat/stream")
    public Flux<String> streamChat(@RequestBody Map<String, String> body) {
        return openAIService.generateStream(body.get("text"));
    }

    @PostMapping("/ai/tts-test")
    public ResponseEntity<byte[]> ttsTest(@RequestBody Map<String, String> body) {
        byte[] audioData = openAIService.tts(body.get("text"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("audio/mpeg"));
        headers.setContentLength(audioData.length);

        return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
    }

    @GetMapping("/ai/tts/productions/{productionId}/description-audio")
    public ResponseEntity<byte[]> getProductionDescriptionAudio(@PathVariable Long productionId) {
        byte[] audioData = openAIService.generateDescriptionAudio(productionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("audio/mpeg"));
        headers.setContentLength(audioData.length);

        return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
    }

    // 기존 방식 - 프롬프트 엔지니어링만 사용
    @GetMapping("/ai/tts/productions/{productionId}/docent")
    public ResponseEntity<ApiResponseDto<AudioResponseDto>> getCreativeProductionDescriptionAudio(@PathVariable Long productionId) {
        AudioResponseDto audioDto = openAIService.generateCreativeDescriptionAudio(productionId);

        ApiResponseDto<AudioResponseDto> response = new ApiResponseDto<>(
                audioDto,
                "AI 도슨트 음성(Base64) 생성에 성공했습니다."
        );
        System.out.println("AI Audio응답 완료");

        return ResponseEntity.ok(response);
    }

    // RAG 기반 방식 - 유사 작품 벡터 검색 + 프롬프트 보강
    @GetMapping("/ai/tts/productions/{productionId}/docent/rag")
    public ResponseEntity<ApiResponseDto<AudioResponseDto>> getCreativeProductionDescriptionAudioWithRag(@PathVariable Long productionId) {
        AudioResponseDto audioDto = openAIService.generateCreativeDescriptionAudioWithRag(productionId);

        ApiResponseDto<AudioResponseDto> response = new ApiResponseDto<>(
                audioDto,
                "RAG 기반 AI 도슨트 음성(Base64) 생성에 성공했습니다."
        );
        System.out.println("RAG AI Audio응답 완료");

        return ResponseEntity.ok(response);
    }
}