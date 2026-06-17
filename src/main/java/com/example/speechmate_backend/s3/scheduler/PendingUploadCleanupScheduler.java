package com.example.speechmate_backend.s3.scheduler;

import com.example.speechmate_backend.s3.domain.PendingUpload;
import com.example.speechmate_backend.s3.repository.PendingUploadRepository;
import com.example.speechmate_backend.s3.service.S3UploadPresignedUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingUploadCleanupScheduler {

    private final PendingUploadRepository pendingUploadRepository;
    private final S3UploadPresignedUrlService s3UploadPresignedUrlService;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanUpStalePendingUploads() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<PendingUpload> stale = pendingUploadRepository.findByCreatedAtBefore(cutoff);

        if (stale.isEmpty()) return;

        stale.forEach(p -> {
            try {
                s3UploadPresignedUrlService.deleteObject(p.getS3Key());
            } catch (Exception e) {
                log.warn("S3 오브젝트 삭제 실패 (key={}): {}", p.getS3Key(), e.getMessage());
            }
        });

        pendingUploadRepository.deleteAll(stale);
        log.info("만료된 PendingUpload {}건 정리 완료", stale.size());
    }
}
