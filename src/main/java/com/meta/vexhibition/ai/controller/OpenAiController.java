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

    @GetMapping("/ai/tts/projects/{projectId}/description-audio")
    public ResponseEntity<byte[]> getProjectDescriptionAudio(@PathVariable Long projectId) {
        byte[] audioData = openAIService.generateDescriptionAudio(projectId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("audio/mpeg"));
        headers.setContentLength(audioData.length);

        return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
    }

    @GetMapping("/ai/tts/projects/{projectId}/docent")
    public ResponseEntity<ApiResponseDto<AudioResponseDto>> getCreativeProjectDescriptionAudio(@PathVariable Long projectId) {
        AudioResponseDto audioDto = openAIService.generateCreativeDescriptionAudio(projectId);

        ApiResponseDto<AudioResponseDto> response = new ApiResponseDto<>(
                audioDto,
                "AI 도슨트 음성(Base64) 생성에 성공했습니다."
        );
        System.out.println("AI Audio응답 완료");

        return ResponseEntity.ok(response);
    }
}