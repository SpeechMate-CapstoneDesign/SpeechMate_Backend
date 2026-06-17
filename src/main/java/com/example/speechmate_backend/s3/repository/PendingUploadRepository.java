package com.example.speechmate_backend.s3.repository;

import com.example.speechmate_backend.s3.domain.PendingUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PendingUploadRepository extends JpaRepository<PendingUpload, Long> {

    Optional<PendingUpload> findByS3KeyAndUserId(String s3Key, Long userId);

    List<PendingUpload> findByCreatedAtBefore(LocalDateTime cutoff);
}