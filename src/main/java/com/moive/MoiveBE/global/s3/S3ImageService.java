package com.moive.MoiveBE.global.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ImageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.cloudfront.domain}")
    private String cloudFrontDomain;

    public String uploadProfileImage(MultipartFile file) {
        String key = createProfileImageKey(file);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    )
            );
        } catch (IOException e) {
            throw new IllegalStateException("프로필 이미지 업로드에 실패했습니다.", e);
        }

        return "https://" + cloudFrontDomain + "/" + key;
    }

    private String createProfileImageKey(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = "";

        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(
                    originalFilename.lastIndexOf(".")
            );
        }

        return "profiles/" + UUID.randomUUID() + extension;
    }
}