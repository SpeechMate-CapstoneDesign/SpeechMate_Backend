package com.example.speechmate_backend.speech;

import com.example.speechmate_backend.common.exception.SpeechNotFoundException;
import com.example.speechmate_backend.common.exception.UserNotMatchException;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.example.speechmate_backend.speech.controller.dto.SpeechMetadataRequestDto;
import com.example.speechmate_backend.speech.domain.AnalysisResult;
import com.example.speechmate_backend.speech.domain.Speech;
import com.example.speechmate_backend.speech.repository.SpeechRepository;
import com.example.speechmate_backend.speech.service.SpeechService;
import com.example.speechmate_backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** speechId만으로 남의 스피치를 읽거나 지우는 IDOR 차단, 그리고 Long 참조 비교(!=) 버그 회귀 방지. 스프링 컨텍스트 없이 돈다. */
@ExtendWith(MockitoExtension.class)
class SpeechOwnershipTest {

    @Mock SpeechRepository speechRepository;
    @Mock S3UploadPresignedUrlService s3UploadPresignedUrlService;
    @InjectMocks SpeechService speechService;

    // 캐시 범위(-128~127) 밖의 id. 예전 코드의 `getId() != userId`는 여기서 본인 요청도 거부했다
    private static final Long OWNER_ID = 1000L;

    private Speech speechOwnedBy(Long userId) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", userId);
        Speech speech = new Speech();
        speech.setUser(user);
        speech.setFileUrl("key.mp3");
        when(speechRepository.findById(1L)).thenReturn(Optional.of(speech));
        return speech;
    }

    @Test
    @DisplayName("본인 스피치 삭제: id가 Long 캐시 범위 밖(1000)이어도 통과하고 S3 오브젝트와 행을 지운다")
    void owner_can_delete_even_when_id_is_outside_long_cache() {
        Speech speech = speechOwnedBy(OWNER_ID);

        speechService.deleteSpeechById(1L, Long.valueOf(1000)); // 일부러 다른 Long 인스턴스

        verify(s3UploadPresignedUrlService).deleteObject("key.mp3");
        verify(speechRepository).delete(speech);
    }

    @Test
    @DisplayName("남의 스피치 삭제는 403(UserNotMatch)이고 아무것도 지우지 않는다")
    void other_user_cannot_delete() {
        speechOwnedBy(OWNER_ID);

        assertThatThrownBy(() -> speechService.deleteSpeechById(1L, 2L))
                .isInstanceOf(UserNotMatchException.class);
        verify(speechRepository, never()).delete(any());
        verify(s3UploadPresignedUrlService, never()).deleteObject(any());
    }

    @Test
    @DisplayName("남의 스피치 대본 조회는 403(UserNotMatch)")
    void other_user_cannot_read_content() {
        speechOwnedBy(OWNER_ID);

        assertThatThrownBy(() -> speechService.getSpeechContentById(1L, 2L))
                .isInstanceOf(UserNotMatchException.class);
    }

    @Test
    @DisplayName("speechId를 받는 모든 서비스 진입점이 타인 요청을 403으로 막는다")
    void every_entry_point_rejects_other_user() {
        speechOwnedBy(OWNER_ID);
        Long other = 2L;

        List<ThrowingCallable> calls = List.of(
                () -> speechService.analyze(1L, other),
                () -> speechService.rtzrStt(1L, other),
                () -> speechService.addMetadataToSpeech(1L, null, other),
                () -> speechService.getSpeechById(1L, other),
                () -> speechService.getSpeechConfigById(1L, other),
                () -> speechService.getSpeechContentById(1L, other),
                () -> speechService.getSpeechContentAnalysisById(1L, other),
                () -> speechService.getSpeechVerbalAnalysisById(1L, other),
                () -> speechService.requestNonVerbalAnalysis(1L, other),
                () -> speechService.deleteSpeechById(1L, other)
        );
        for (ThrowingCallable call : calls) {
            assertThatThrownBy(call).isInstanceOf(UserNotMatchException.class);
        }
        verify(speechRepository, never()).save(any());
        verify(speechRepository, never()).delete(any());
    }

    @Test
    @DisplayName("본인 요청은 조회·분석·메타데이터 수정 전부 통과한다")
    void owner_passes_every_read_and_update_path() {
        Speech speech = speechOwnedBy(OWNER_ID);
        ReflectionTestUtils.setField(speech, "createdAt", LocalDateTime.of(2026, 10, 5, 0, 0));
        speech.setContent("대본");
        speech.setAnalysisResult(AnalysisResult.builder().summary("요약").build());

        assertThat(speechService.analyze(1L, OWNER_ID).summary()).isEqualTo("요약"); // 이미 분석됨 → 저장된 결과 반환
        assertThat(speechService.getSpeechById(1L, OWNER_ID).sttContent()).isEqualTo("대본");
        assertThat(speechService.getSpeechContentById(1L, OWNER_ID).content()).isEqualTo("대본");
        assertThat(speechService.getSpeechConfigById(1L, OWNER_ID).createdAt()).startsWith("2026-10-05");
        assertThat(speechService.getSpeechContentAnalysisById(1L, OWNER_ID).summary()).isEqualTo("요약");
        assertThat(speechService.getSpeechVerbalAnalysisById(1L, OWNER_ID)).isNull(); // 언어 분석 전이면 null

        speechService.addMetadataToSpeech(1L, new SpeechMetadataRequestDto("제목", null, null, null), OWNER_ID);
        assertThat(speech.getTitle()).isEqualTo("제목");
    }

    @Test
    @DisplayName("없는 speechId는 소유자 검사 전에 404(SpeechNotFound)")
    void missing_speech_is_404() {
        when(speechRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> speechService.getSpeechById(99L, OWNER_ID))
                .isInstanceOf(SpeechNotFoundException.class);
    }
}
