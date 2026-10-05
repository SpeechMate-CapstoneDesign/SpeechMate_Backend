package com.example.speechmate_backend.speech.returnzero;

import com.amazonaws.services.s3.model.S3Object;
import com.example.speechmate_backend.common.exception.FileTooLargeException;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReturnZeroClientTest {

    @TempDir Path workDir;

    @Test
    @DisplayName("25MB를 넘는 오디오는 외부 API를 부르기 전에 FileTooLarge(400)로 끝나고, 작업 디렉터리의 임시파일은 모두 지워진다")
    void rejects_large_file_and_cleans_temp_files() throws Exception {
        S3UploadPresignedUrlService s3 = mock(S3UploadPresignedUrlService.class);
        S3Object big = new S3Object();
        big.setObjectContent(new ByteArrayInputStream(new byte[26 * 1024 * 1024]));
        when(s3.getObject("big.mp3")).thenReturn(big);

        ReturnZeroClient client = new ReturnZeroClient(WebClient.builder(), mock(ReturnZeroTokenManager.class), new ObjectMapper(), s3);
        ReflectionTestUtils.setField(client, "workDir", workDir.resolve("work"));
        client.init();

        assertThatThrownBy(() -> client.rtzrSttFromS3("big.mp3")).isSameAs(FileTooLargeException.EXCEPTION);
        assertThat(workDir.resolve("work")).isEmptyDirectory();
    }
}
