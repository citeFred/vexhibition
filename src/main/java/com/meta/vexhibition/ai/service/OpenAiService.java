package com.meta.vexhibition.ai.service;

import com.meta.vexhibition.project.domain.Project;
import com.meta.vexhibition.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenAiService {
    // SpringAI-OpenAI 제공 모델 구현체
    private final OpenAiChatModel openAiChatModel;
    private final OpenAiEmbeddingModel openAiEmbeddingModel;
    private final OpenAiImageModel openAiImageModel;
    private final OpenAiAudioSpeechModel openAiAudioSpeechModel;
    private final OpenAiAudioTranscriptionModel openAiAudioTranscriptionModel;

    // 대화 기록을 포함하여 AI 응답을 생성하기 위한 인터페이스 직접 사용
    private final ChatModel chatModel;

    // Project 정보 활용을 위한 주입
    private final ProjectRepository projectRepository;

    @Value("${spring.ai.openai.chat.options.model}")
    private String aiModel;

    @Value("${spring.ai.openai.chat.options.temperature}")
    private Double aiTemperature;

    // [0] ChatModel의 히스토리(이전대화내용)을 사용할 수 있도록 커스텀
    public String generateWithHistory(List<Message> conversationHistory) {
        // 1. 시스템 메시지를 생성
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate("You are a helpful assistant who speaks Korean.");
        Message systemMessage = systemPromptTemplate.createMessage();

        // 2. 전달받은 대화 기록의 맨 앞에 시스템 메시지를 추가하여 최종 프롬프트를 구성
        List<Message> finalMessages = new ArrayList<>();
        finalMessages.add(systemMessage);
        finalMessages.addAll(conversationHistory);

        // 3. 프롬프트
        Prompt prompt = new Prompt(finalMessages);

        // 4. 요청과 응답
        ChatResponse chatResponse = chatModel.call(prompt);
        return chatResponse.getResult().getOutput().getText();
    }

    // [1] ChatModel 사용법 - 응답반환
    public String generate(String message, String agenticSystemSettings) {
        // Message
        SystemMessage systemMessage = new SystemMessage(agenticSystemSettings);
        UserMessage userMessage = new UserMessage(message);
        AssistantMessage assistantMessage = new AssistantMessage("");

        // 옵션
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(aiModel)
                .temperature(aiTemperature)
                .build();

        // 프롬프트
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage, assistantMessage));

        // 요청과 응답
        ChatResponse chatResponse = openAiChatModel.call(prompt);
        return chatResponse.getResult().getOutput().getText();
    }

    // [2] ChatModel 사용법 - Flux 객체의 스트림 응답반환
    public Flux<String> generateStream(String message) {
        // Message
        SystemMessage systemMessage = new SystemMessage("");
        UserMessage userMessage = new UserMessage("");
        AssistantMessage assistantMessage = new AssistantMessage("");

        // 옵션
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(aiModel)
                .temperature(aiTemperature)
                .build();

        // 프롬프트
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage, assistantMessage));

        // 요청과 응답
        return openAiChatModel.stream(prompt)
                .mapNotNull(chatResponse -> chatResponse.getResult().getOutput().getText());
    }

    // [3] Embedding 호출 메서드
    public List<float[]> generateEmbedding(List<String> texts, String model) {
        // 옵션
        EmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        // 프롬프트
        EmbeddingRequest prompt = new EmbeddingRequest(texts, embeddingOptions);

        EmbeddingResponse  embeddingResponse = openAiEmbeddingModel.call(prompt);
        return embeddingResponse.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
    }

    // [4] Image 모델 (DALL-E) 메서드
    public List<String> generateImages(String text, int count, int height, int width) {
        // 옵션
        OpenAiImageOptions imageOptions = OpenAiImageOptions.builder()
                .quality("hd")
                .N(count)
                .height(height)
                .width(width)
                .build();

        // 프롬프트
        ImagePrompt prompt = new ImagePrompt(text, imageOptions);

        // 요청 및 응답
        ImageResponse response = openAiImageModel.call(prompt);
        return response.getResults().stream()
                .map(image -> image.getOutput().getUrl())
                .toList();
    }

    // [5] Text To Speech(TTS) Audio 메서드
    public byte[] tts(String text) {
        // 옵션
        OpenAiAudioSpeechOptions speechOptions = OpenAiAudioSpeechOptions.builder()
                .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                .speed(1.0f)
                .model(OpenAiAudioApi.TtsModel.TTS_1.value)
                .build();

        // 프롬프트
        SpeechPrompt prompt = new SpeechPrompt(text, speechOptions);

        // 요청 및 응답
        SpeechResponse response = openAiAudioSpeechModel.call(prompt);
        return response.getResult().getOutput();
    }

    // Speech To Text(STT) Audio 메서드 2
    public String stt(Resource audioFile) {
        // 옵션
        OpenAiAudioApi.TranscriptResponseFormat responseFormat = OpenAiAudioApi.TranscriptResponseFormat.VTT;
        OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .language("ko") // 인식할 언어
                .prompt("Ask not this, but ask that") // 음성 인식 전 참고할 텍스트 프롬프트
                .temperature(0f)
                .model(OpenAiAudioApi.TtsModel.TTS_1.value)
                .responseFormat(responseFormat) // 결과 타입 지정 VTT 자막형식
                .build();

        // 프롬프트
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);

        // 요청 및 응답
        AudioTranscriptionResponse response = openAiAudioTranscriptionModel.call(prompt);
        return response.getResult().getOutput();
    }

    @Transactional(readOnly = true)
    public byte[] generateDescriptionAudio(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("ID에 해당하는 작품을 찾을 수 없습니다."));

        String description = project.getDescription();

        return this.tts(description);
    }

    @Transactional(readOnly = true)
    public byte[] generateCreativeDescriptionAudio(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("ID에 해당하는 작품을 찾을 수 없습니다."));

        String originalDescription = project.getDescription();

        String agenticSystemSettings = "당신은 '메타버스 아카데미 수료작품 전시회'의 전문 AI 도슨트 '벡시(Vexi)'입니다." +
                "여러 작품들 중에 현재 작품을 소개하는 상황이므로 환영인사는 제외"+
                "이 프로젝트 정보에 대한 것을 재구성해서 설명해야 할 대본을 작성하는 역할. 필수적인 구성은 다음과 같음." +
                "1. 환영인사, 본인 소개 등은 제외. 작품에 대한 설명을 바로 시작" +
                "2. 이 작품 내용을 분석하고, 어떤 목적을 가진 프로젝트인지 소개하는 대본으로 시작"+
                "3. 부가적인 설명은 프로젝트 분석에 따라 논리적으로 구성"+
                "4. 한국어 존댓말 사용"+
                "5. 친절하고 흥미로운 톤의 대본"+
                "6. 프로젝트의 장점을 간략히 추가"+
                "7. 60초 이내 대본";

        String creativeScript = this.generate(originalDescription, agenticSystemSettings);

        return this.tts(creativeScript);
    }
}