package com.freepets.infra.s3;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@RequiredArgsConstructor
@Component
public class S3ImageService {

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif");

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public String upload(MultipartFile image) {
        if (image.isEmpty() || Objects.isNull(image.getOriginalFilename())) {
            throw new GeneralException(ErrorStatus.IMAGE4001);
        }

        String extension = extractExtension(image.getOriginalFilename());
        validateExtension(extension);

        String key = UUID.randomUUID() + "." + extension;
        putObject(image, key);

        return buildImageUrl(key);
    }

    public void delete(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        try {
            String key = extractKey(imageUrl);
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .build());
        } catch (Exception e) {
            // 이미지 삭제 실패로 본 작업(반려동물 수정 등)이 실패해서는 안 되므로 로그만 남긴다.
            log.warn("S3 이미지 삭제에 실패했습니다. imageUrl={}", imageUrl, e);
        }
    }

    private void putObject(
            MultipartFile image,
            String key
    ) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .contentType(image.getContentType())
                    .contentLength(image.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(image.getInputStream(), image.getSize()));
        } catch (IOException | RuntimeException e) {
            log.error("S3 이미지 업로드에 실패했습니다. key={}", key, e);
            throw new GeneralException(ErrorStatus.IMAGE5001);
        }
    }

    private String extractExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1) {
            throw new GeneralException(ErrorStatus.IMAGE4002);
        }

        return filename.substring(lastDotIndex + 1).toLowerCase();
    }

    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new GeneralException(ErrorStatus.IMAGE4003);
        }
    }

    private String buildImageUrl(String key) {
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(
                s3Properties.bucket(),
                s3Properties.region(),
                key
        );
    }

    private String extractKey(String imageUrl) {
        return imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
    }
}
