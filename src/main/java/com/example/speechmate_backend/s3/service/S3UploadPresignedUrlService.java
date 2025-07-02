package com.example.speechmate_backend.s3.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.example.speechmate_backend.s3.MediaFileExtension;
import com.example.speechmate_backend.s3.controller.dto.VoiceRecordDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3UploadPresignedUrlService {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String region;

    private static final long PRESIGNED_URL_EXPIRATION_MILLIS = 1000 * 60 * 10;

    public VoiceRecordDto generatePreSignedUrlForSpeech(Long userId, Long speechId, MediaFileExtension mediaFileExtension) {
        String fileExtension = mediaFileExtension.getUploadExtension();
        String fileName = getSpeechFileName(userId, speechId, fileExtension);

        URL url = amazonS3.generatePresignedUrl(
                getGeneratePreSignedUrlRequest(bucket, fileName, fileExtension)
        );
        return VoiceRecordDto.of(url.toString(), fileName);
    }

    public String getPublicS3Url(String fileKey) {
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + fileKey;
    }

    public S3Object getObject(String key) {
        return amazonS3.getObject(bucket, key);
    }


    private GeneratePresignedUrlRequest getGeneratePreSignedUrlRequest(
            String bucket, String fileName, String fileExtension) {
        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(bucket, fileName)
                        .withMethod(HttpMethod.PUT)
                        .withKey(fileName)
                        .withContentType(getMimeTypeFromExtension(fileExtension))
                        .withExpiration(getPreSignedUrlExpiration());

//        generatePresignedUrlRequest.addRequestParameter(                        //Bucket Owner Enforced 일때는 불필요
//                Headers.S3_CANNED_ACL, CannedAccessControlList.PublicRead.toString());   //파일은 공개 상태로
        return generatePresignedUrlRequest;
    }


    private String getSpeechFileName(Long userId, Long speechId, String fileExtension) {
        return "user/"
                + userId.toString()
                + "/speech/"
                + speechId
                + "/"
                + UUID.randomUUID()
                +"."
                +fileExtension;
    }

    private String getMimeTypeFromExtension(String extension) {
        return switch (extension.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "wav", "wave" -> "audio/wave";
            case "webm" -> "audio/webm";
            case "m4a", "mp4" -> "audio/mp4";
            default -> "application/octet-stream";
        };
    }


    private Date getPreSignedUrlExpiration() {
        return new Date(System.currentTimeMillis() + PRESIGNED_URL_EXPIRATION_MILLIS);
    }


}
