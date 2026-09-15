package com.freepets.infra.s3;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    // 문서 확장자별로 허용하는 Content-Type. 확장자와 형식을 따로 확인하면 "등록증.pdf + image/png"처럼
    // 어긋난 조합이 통과해, S3가 엉뚱한 형식으로 내려줘 관리자 화면에서 파일이 깨진다.
    private static final Map<String, String> DOCUMENT_CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf"
    );

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public String upload(MultipartFile image) {
        String extension = extractExtension(image);
        validateExtension(extension, ALLOWED_EXTENSIONS, ErrorStatus.IMAGE4003);

        return putObject(image, extension, image.getContentType());
    }

    /**
     * 증빙 문서(사업자등록증 등)를 올리고 공개 URL을 돌려준다. 사진과 달리 PDF를 받는다.
     *
     * <p><b>민감 문서다.</b> 사업자등록증에는 대표자 생년월일 같은 개인정보가 찍혀 있는데, 지금은 사진과 같은
     * 퍼블릭 버킷에 올라가 URL을 알면 누구나 열 수 있다. 파일명이 UUID라 추측은 어렵지만, 그래서 이 URL은
     * 앱의 일반 응답에 내리지 않고 관리자 응답에만 내린다는 전제로 쓴다.
     *
     * <p>비공개 버킷으로 옮기려면 URL 대신 키를 저장하도록 기존 값을 마이그레이션하고, 관리자에게는 짧게
     * 유효한 서명 URL(presigned URL)을 내려줘야 한다.
     *
     * <p>Content-Type이 확장자에 맞는 형식인지도 확인한다. 클라이언트가 보내는 값이라 위조할 수 있지만,
     * 확장자만 pdf로 바꾼 파일이나 형식 정보가 빠진 요청은 걸러낸다.
     */
    public String uploadDocument(MultipartFile document) {
        String extension = extractExtension(document);
        validateExtension(extension, DOCUMENT_CONTENT_TYPES.keySet(), ErrorStatus.IMAGE4004);

        String expectedContentType = DOCUMENT_CONTENT_TYPES.get(extension);
        if (!expectedContentType.equals(normalizeContentType(document.getContentType()))) {
            throw new GeneralException(ErrorStatus.IMAGE4004);
        }

        // 클라이언트가 보낸 원래 값 대신 확장자에 맞는 형식으로 저장해, S3가 항상 깔끔한 형식으로 내려주게 한다.
        return putObject(document, extension, expectedContentType);
    }

    /**
     * 비교용으로 Content-Type을 정리한다. 파일 파트에 헤더가 없으면 {@code null}이라 빈 문자열로 바꾸고,
     * {@code "application/PDF; name=a.pdf"}처럼 대소문자나 파라미터가 붙어 와도 형식만 남긴다.
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }

        int parameterIndex = contentType.indexOf(';');
        String mediaType = parameterIndex == -1 ? contentType : contentType.substring(0, parameterIndex);
        return mediaType.trim().toLowerCase();
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

    /**
     * 버킷 루트에 {@code {uuid}.{확장자}}로 올리고 공개 URL을 돌려준다. 폴더를 붙이지 않는다 —
     * {@link #extractKey}가 URL의 마지막 {@code /} 뒤만 키로 보기 때문에 폴더가 있으면 삭제가 엉뚱한 키를 지운다.
     */
    private String putObject(
            MultipartFile file,
            String extension,
            String contentType
    ) {
        String key = UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | RuntimeException e) {
            log.error("S3 파일 업로드에 실패했습니다. key={}", key, e);
            throw new GeneralException(ErrorStatus.IMAGE5001);
        }

        return buildImageUrl(key);
    }

    private String extractExtension(MultipartFile file) {
        if (file.isEmpty() || Objects.isNull(file.getOriginalFilename())) {
            throw new GeneralException(ErrorStatus.IMAGE4001);
        }

        String filename = file.getOriginalFilename();
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1) {
            throw new GeneralException(ErrorStatus.IMAGE4002);
        }

        return filename.substring(lastDotIndex + 1).toLowerCase();
    }

    private void validateExtension(
            String extension,
            Collection<String> allowedExtensions,
            ErrorStatus unsupportedErrorStatus
    ) {
        if (!allowedExtensions.contains(extension)) {
            throw new GeneralException(unsupportedErrorStatus);
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
