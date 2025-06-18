package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.speech.controller.dto.AnalysisResultDto;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class SpeechAnalysisResultService {

    private final ObjectMapper objectMapper;
    private final ChatClient.Builder chatClientBuilder;
    AnalysisResult analyzeText(String text) {
        var outputParser = new BeanOutputConverter<>(AnalysisResultDto.class);

        // 프롬프트에 outputParser.getFormat()을 포함시켜 LLM이 JSON 형식을 따르도록 지시.
        String promptTemplateString = """
            당신은 세계 최고의 스피치 코치이자 텍스트 분석 전문가입니다.
            다음 발표 텍스트를 분석하고, 아래의 모든 항목에 대해 한국어로 구체적이고 전문적인 피드백을 제공해주세요.

            분석할 텍스트:
            ---
            {text}
            ---

            지시사항:
            - summary: 핵심 내용을 3~4개의 문장으로 간결하게 요약하세요.
            - keywords: 텍스트의 핵심 단어 5개를 쉼표로 구분된 하나의 문자열로 제공하세요.
            - improvementPoints: 개선점 3가지를 구체적으로 서술하세요.
            - logicalCoherenceScore: 1~10 사이의 정수로 평가하세요.
            - feedback: 발표의 부족한 부분에 대한 종합적인 피드백을 주세요.
            - 결과는 반드시 아래에 명시된 JSON 형식으로만 응답해야 합니다. 다른 설명은 절대 추가하지 마세요.
            - scoreExplanation: 위에서 준 점수에 대한 이유를 1~2문장으로 설명하세요.
            - expectedQuestions: 청중이 발표를 들은 후 할 수 있는 질문을 3가지 예측해서 작성하세요.
            - feedback: 발표 전체에 대한 종합적인 피드백을 작성하세요. 발표자의 강점과 약점을 균형 있게 다루세요.
            
            {format}
            """;

        // {format}  <-- 여기에 BeanOutputConverter가 생성하는 JSON 형식 지시사항이 삽입


        // PromptTemplate 생성 시 format 지시사항을 포함.
        PromptTemplate promptTemplate = new PromptTemplate(promptTemplateString);
        Prompt prompt = promptTemplate.create(Map.of(
                "text", text,
                "format", outputParser.getFormat()
        ));

        System.out.println(prompt.getContents());


        ChatClient chatClient = chatClientBuilder.build();

        // 1. AI를 호출하여 JSON 응답을 문자열로 받기.
        String jsonResponse = chatClient.prompt(prompt)
                .call()
                .content();
        System.out.println("AI 응답: " + jsonResponse);

        // 2. 받은 문자열을 ObjectMapper로 직접 파싱.
        try {
            AnalysisResultDto dto = objectMapper.readValue(jsonResponse, AnalysisResultDto.class);
            return AnalysisResult.from(dto);
        } catch (Exception e) {
            // 파싱 실패 시 예외 처리
            throw new RuntimeException("AI 응답 파싱 실패: " + jsonResponse, e);
        }

    }

}
