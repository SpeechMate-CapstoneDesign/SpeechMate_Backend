package com.example.speechmate_backend.speech.service;

import com.example.speechmate_backend.common.exception.AiAnalysisException;
import com.example.speechmate_backend.speech.controller.dto.GptResponse;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.domain.VerbalAnalysisResult;
import com.example.speechmate_backend.speech.domain.converter.VerbalAnalysisConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** GPT 응답(JSON) → AnalysisResult 매핑과 반복 단어 저장. GPT 호출 자체는 mock. */
@ExtendWith(MockitoExtension.class)
class AnalyzeTextTest {

    @Mock ObjectMapper objectMapper;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) ChatClient.Builder chatClientBuilder;
    @Mock VerbalAnalysisConverter converter;
    @InjectMocks SpeechAnalysisResultService service;

    @Test
    @DisplayName("응답 JSON을 파싱해 AnalysisResult를 만들고, 언어 분석 결과가 있으면 반복 단어를 거기에 저장한다")
    void maps_gpt_json_and_saves_repeated_words() throws Exception {
        when(chatClientBuilder.build().prompt(any(Prompt.class)).call().content()).thenReturn("{json}");
        GptResponse gpt = new GptResponse("요약", "a,b", List.of("개선1"), List.of("질문1"), "피드백", Map.of("그", 3));
        when(objectMapper.readValue("{json}", GptResponse.class)).thenReturn(gpt);
        Speech speech = new Speech();
        VerbalAnalysisResult verbal = new VerbalAnalysisResult();
        speech.setVerbalAnalysisResult(verbal);

        AnalysisResult result = service.analyzeText(speech, "발표 대본");

        assertThat(result.getSummary()).isEqualTo("요약");
        assertThat(result.getImprovementPoints()).containsExactly("개선1");
        assertThat(result.getExpectedQuestions()).containsExactly("질문1");
        verify(converter).saveRepeatedWords(eq(verbal), eq(Map.of("그", 3)));
    }

    @Test
    @DisplayName("GPT가 형식을 어긴 응답을 주면 AiAnalysisException")
    void malformed_response_becomes_domain_exception() throws Exception {
        when(chatClientBuilder.build().prompt(any(Prompt.class)).call().content()).thenReturn("not json");
        when(objectMapper.readValue("not json", GptResponse.class)).thenThrow(new RuntimeException("parse"));

        assertThatThrownBy(() -> service.analyzeText(new Speech(), "대본")).isInstanceOf(AiAnalysisException.class);
        verifyNoInteractions(converter);
    }
}
