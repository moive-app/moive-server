package com.moive.MoiveBE.global.s3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class S3ImageServiceTest {

    @Autowired
    private S3ImageService s3ImageService;

    @Test
    void 프로필_이미지를_S3에_업로드한다() {
        MockMultipartFile file = new MockMultipartFile(
                "profileImage",
                "profile.jpg",
                "image/jpeg",
                "test-image".getBytes()
        );

        String imageUrl = s3ImageService.uploadProfileImage(file);

        assertThat(imageUrl).startsWith("https://");
        assertThat(imageUrl).contains("/profiles/");
    }
}