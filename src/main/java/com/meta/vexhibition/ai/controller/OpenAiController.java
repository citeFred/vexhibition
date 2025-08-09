package com.meta.vexhibition.ai.controller;

import com.meta.vexhibition.ai.service.OpenAiService;
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
    public ResponseEntity<byte[]> generateCreativeDescriptionAudio(@PathVariable Long projectId) {
        byte[] audioData = openAIService.generateCreativeDescriptionAudio(projectId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("audio/mpeg"));
        headers.setContentLength(audioData.length);

        return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
    }
}