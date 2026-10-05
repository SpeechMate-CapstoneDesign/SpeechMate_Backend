package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.SpeechPagingResponseDto;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.controller.SortType;
import com.example.speechmate_backend.speech.controller.dto.SpeechFeedDto;
import com.example.speechmate_backend.speech.controller.dto.SpeechPagingFeedDto;
import com.example.speechmate_backend.speech.repository.SpeechCustomRepository;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.service.SpeechService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 커서 페이징: limit+1개를 조회해 와서 limit개로 자르고 hasNext·커서를 계산하는 공통 로직. */
@ExtendWith(MockitoExtension.class)
class SpeechPagingTest {

    @Mock SpeechRepository speechRepository;
    @Mock S3UploadPresignedUrlService s3UploadPresignedUrlService;
    @Mock SpeechCustomRepository speechCustomRepository;
    @InjectMocks SpeechService speechService;

    private static Speech speech(long id, boolean analyzed) {
        Speech s = new Speech();
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "createdAt", LocalDateTime.of(2026, 10, 5, 0, 0).plusMinutes(id));
        if (analyzed) s.setAnalysisResult(AnalysisResult.builder().summary("s" + id).build());
        return s;
    }

    @Test
    @DisplayName("limit+1개가 오면 limit개만 돌려주고 hasNext=true, 커서는 돌려준 마지막 항목")
    void has_next_when_one_extra_row() {
        // 최신순이라 id 내림차순으로 온다. limit 5에 6개
        when(speechRepository.findAllSpeechesWithAnalysis(eq(1L), any(), any(Pageable.class)))
                .thenReturn(List.of(speech(10, true), speech(9, false), speech(8, true), speech(7, false), speech(6, true), speech(5, false)));

        SpeechPagingResponseDto page = speechService.getAllSpeeches(1L, null, 5);

        assertThat(page.speeches()).extracting("speechId").containsExactly(10L, 9L, 8L, 7L, 6L);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.cursordto().id()).isEqualTo(6L);
        assertThat(page.cursordto().dateTime()).isEqualTo(LocalDateTime.of(2026, 10, 5, 0, 6));
    }

    @Test
    @DisplayName("limit 이하로 오면 전부 돌려주고 hasNext=false, 커서 없음")
    void no_next_when_rows_fit() {
        when(speechRepository.findAllSpeechesWithAnalysis(eq(1L), any(), any(Pageable.class)))
                .thenReturn(List.of(speech(3, true), speech(2, false)));

        SpeechPagingResponseDto page = speechService.getAllSpeeches(1L, null, 5);

        assertThat(page.speeches()).hasSize(2);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.cursordto()).isNull();
    }

    @Test
    @DisplayName("분석 안 된 스피치는 isAnalyzed=false에 빈 목록, 분석된 건 요약이 채워진다")
    void maps_analysis_null_safely() {
        when(speechRepository.findAllSpeechesWithAnalysis(eq(1L), any(), any(Pageable.class)))
                .thenReturn(List.of(speech(2, true), speech(1, false)));

        var rows = speechService.getAllSpeeches(1L, null, 5).speeches();

        assertThat(rows.get(0).isAnalyzed()).isTrue();
        assertThat(rows.get(0).summary()).isEqualTo("s2");
        assertThat(rows.get(1).isAnalyzed()).isFalse();
        assertThat(rows.get(1).summary()).isNull();
        assertThat(rows.get(1).improvementPoints()).isEmpty();
    }

    @Test
    @DisplayName("분석된 것만 조회(getAnalyzedSpeeches)도 같은 자르기 규칙을 쓴다")
    void analyzed_query_uses_same_slicing() {
        when(speechRepository.findAnalyzedSpeeches(eq(1L), any(), any(Pageable.class)))
                .thenReturn(List.of(speech(4, true), speech(2, true)));

        SpeechPagingResponseDto page = speechService.getAnalyzedSpeeches(1L, null, 1);

        assertThat(page.speeches()).extracting("speechId").containsExactly(4L);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.cursordto().id()).isEqualTo(4L);
    }

    private static SpeechFeedDto feed(long id) {
        return new SpeechFeedDto(id, "t" + id, LocalDateTime.of(2026, 10, 5, 0, 0).plusMinutes(id), 60L, "AUDIO", "key" + id, null, null, null);
    }

    @Test
    @DisplayName("피드도 같은 자르기 규칙을 쓰고, 파일 키는 공개 URL로 바꿔 돌려준다")
    void feed_slices_and_resolves_urls() {
        when(speechCustomRepository.findMyFeed(1L, null, 3, SortType.LATEST)).thenReturn(List.of(feed(9), feed(8), feed(7)));
        when(s3UploadPresignedUrlService.getPublicS3Url(any())).thenAnswer(inv -> "https://cdn/" + inv.getArgument(0));

        SpeechPagingFeedDto page = speechService.getMySpeecheFeed(1L, null, 2, SortType.LATEST);

        assertThat(page.speeches()).extracting(SpeechFeedDto::id).containsExactly(9L, 8L);
        assertThat(page.speeches().get(0).fileUrl()).isEqualTo("https://cdn/key9");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.cursordto().id()).isEqualTo(8L);
    }
}
