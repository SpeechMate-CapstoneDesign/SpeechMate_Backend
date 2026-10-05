package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.speech.controller.dto.Silence;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse.Results;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse.Utterance;
import com.example.speechmate_backend.speech.controller.dto.TranscriptionResponse.Word;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.domain.converter.VerbalAnalysisConverter;
import com.example.speechmate_backend.speech.service.SpeechAnalysisResultService;
import com.example.speechmate_backend.common.exception.ReturnZeroException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** STT 결과 → 대본·어절/음절 수·침묵 구간·간투어 집계. 외부 호출 없는 순수 계산. */
@ExtendWith(MockitoExtension.class)
class VerbalAnalyzeTest {

    @Mock ObjectMapper objectMapper;
    @Mock ChatClient.Builder chatClientBuilder;
    @Mock VerbalAnalysisConverter converter;
    @InjectMocks SpeechAnalysisResultService service;

    @Captor ArgumentCaptor<List<Silence>> silences;
    @Captor ArgumentCaptor<Map<String, List<Integer>>> fillers;

    private static Word w(int startAt, int duration, String text) { return new Word(startAt, duration, text); }

    @Test
    @DisplayName("대본은 발화 msg를 공백으로 잇고, 어절 수는 단어 수, 음절 수는 글자 수 합")
    void content_word_and_syllable_counts() throws Exception {
        Speech speech = new Speech();
        TranscriptionResponse tr = new TranscriptionResponse("id", "completed", new Results(List.of(
                new Utterance(0, 1000, 0, "s", List.of(w(0, 300, "안녕하세요"), w(400, 300, "여러분")), "안녕하세요 여러분"),
                new Utterance(2000, 500, 0, "s", List.of(w(2000, 500, "반갑습니다")), "반갑습니다")
        ), true));

        String content = service.verbalanalyze(speech, tr);

        assertThat(content).isEqualTo("안녕하세요 여러분 반갑습니다");
        assertThat(speech.getContent()).isEqualTo(content);
        verify(converter).saveAnalysis(same(speech), /* 어절 */ anyLong(), /* 음절 */ anyLong(), anyList(), anyMap());
        ArgumentCaptor<Long> words = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> syllables = ArgumentCaptor.forClass(Long.class);
        verify(converter).saveAnalysis(same(speech), words.capture(), syllables.capture(), anyList(), anyMap());
        assertThat(words.getValue()).isEqualTo(3);
        assertThat(syllables.getValue()).isEqualTo(5 + 3 + 5);
    }

    @Test
    @DisplayName("같은 발화 안에서 단어 사이 공백이 1초 이상이면 침묵 구간, 미만이면 아님")
    void silence_threshold_is_one_second() throws Exception {
        TranscriptionResponse tr = new TranscriptionResponse("id", "completed", new Results(List.of(
                new Utterance(0, 5000, 0, "s", List.of(
                        w(0, 500, "첫"),        // 끝 500
                        w(1499, 100, "둘"),     // 공백 999ms → 침묵 아님
                        w(2600, 100, "셋")      // 공백 1001ms → 침묵
                ), "첫 둘 셋")
        ), true));

        service.verbalanalyze(new Speech(), tr);

        verify(converter).saveAnalysis(org.mockito.ArgumentMatchers.any(), anyLong(), anyLong(), silences.capture(), anyMap());
        assertThat(silences.getValue()).hasSize(1);
        Silence s = silences.getValue().get(0);
        assertThat(s.getDuration()).isEqualTo(1001);
        assertThat(s.getStartTime()).isEqualTo(1599);
        assertThat(s.getEndTime()).isEqualTo(2600);
        assertThat(s.getWordBefore()).isEqualTo("둘");
        assertThat(s.getWordAfter()).isEqualTo("셋");
    }

    @Test
    @DisplayName("간투어는 문장부호를 떼고 판정하며 등장 시각을 모은다. 일반 단어는 집계 안 됨")
    void fillers_are_counted_with_punctuation_stripped() throws Exception {
        TranscriptionResponse tr = new TranscriptionResponse("id", "completed", new Results(List.of(
                new Utterance(0, 5000, 0, "s", List.of(
                        w(0, 100, "음,"), w(200, 100, "그"), w(400, 100, "발표를"), w(600, 100, "음")
                ), "음, 그 발표를 음")
        ), true));

        service.verbalanalyze(new Speech(), tr);

        verify(converter).saveAnalysis(org.mockito.ArgumentMatchers.any(), anyLong(), anyLong(), anyList(), fillers.capture());
        assertThat(fillers.getValue())
                .containsEntry("음", List.of(0, 600))
                .containsEntry("그", List.of(200))
                .doesNotContainKey("발표를");
    }

    @Test
    @DisplayName("분석 결과 저장 중 직렬화가 실패하면 STT 파이프라인 예외(ReturnZeroException)로 나간다")
    void serialization_failure_becomes_domain_exception() throws Exception {
        TranscriptionResponse tr = new TranscriptionResponse("id", "completed", new Results(List.of(
                new Utterance(0, 100, 0, "s", List.of(w(0, 100, "말")), "말")), true));
        doThrow(new JsonProcessingException("x") {}).when(converter)
                .saveAnalysis(org.mockito.ArgumentMatchers.any(), anyLong(), anyLong(), anyList(), anyMap());

        assertThatThrownBy(() -> service.verbalanalyze(new Speech(), tr)).isInstanceOf(ReturnZeroException.class);
    }
}
