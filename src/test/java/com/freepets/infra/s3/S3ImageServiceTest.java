package com.freepets.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageServiceTest {

    private static final String BUCKET = "freepets-bucket";
    private static final String REGION = "ap-northeast-2";
    private static final String URL_PREFIX = "https://freepets-bucket.s3.ap-northeast-2.amazonaws.com/";

    @Mock
    private S3Client s3Client;

    private S3ImageService s3ImageService;

    @BeforeEach
    void setUp() {
        s3ImageService = new S3ImageService(
                s3Client,
                new S3Properties(BUCKET, REGION, "access-key", "secret-key")
        );
    }

    private MockMultipartFile file(
            String filename,
            String contentType
    ) {
        return new MockMultipartFile("file", filename, contentType, new byte[] {1, 2, 3});
    }

    private PutObjectRequest capturePutObjectRequest() {
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        return requestCaptor.getValue();
    }

    @Test
    void uploadDocument_PDF를_버킷_루트에_올리고_공개_URL을_돌려준다() {
        String url = s3ImageService.uploadDocument(file("사업자등록증.pdf", "application/pdf"));

        PutObjectRequest request = capturePutObjectRequest();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.contentType()).isEqualTo("application/pdf");
        // 폴더가 붙으면 delete가 URL의 마지막 "/" 뒤만 키로 봐서 엉뚱한 키를 지운다.
        assertThat(request.key()).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(url).isEqualTo(URL_PREFIX + request.key());
    }

    @Test
    void uploadDocument_jpg와_png_사진도_받는다() {
        s3ImageService.uploadDocument(file("등록증.JPG", "image/jpeg"));
        s3ImageService.uploadDocument(file("등록증.png", "image/png"));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(2)).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getAllValues())
                .extracting(PutObjectRequest::key)
                .allSatisfy(key -> assertThat(key).matches("[0-9a-f-]{36}\\.(jpg|png)"));
    }

    @Test
    void uploadDocument_지원하지_않는_확장자면_IMAGE4004() {
        assertDocumentRejected(file("등록증.gif", "image/gif"));
    }

    @Test
    void uploadDocument_허용되지_않은_Content_Type이면_IMAGE4004() {
        // 확장자만 pdf로 바꾼 파일은 올리지 않는다.
        assertDocumentRejected(file("등록증.pdf", "text/html"));
    }

    @Test
    void uploadDocument_확장자와_Content_Type이_서로_맞지_않으면_IMAGE4004() {
        // 둘 다 허용 목록에 있어도 짝이 맞지 않으면 S3가 엉뚱한 형식으로 내려줘 파일이 깨진다.
        assertDocumentRejected(file("등록증.pdf", "image/png"));
        assertDocumentRejected(file("등록증.png", "application/pdf"));
    }

    @Test
    void uploadDocument_Content_Type이_없으면_500이_아니라_IMAGE4004() {
        // 파일 파트에 Content-Type 헤더가 없으면 getContentType()이 null이다.
        assertDocumentRejected(file("등록증.pdf", null));
    }

    @Test
    void uploadDocument_Content_Type에_대소문자나_파라미터가_붙어도_받고_정리된_형식으로_저장한다() {
        s3ImageService.uploadDocument(file("등록증.pdf", "Application/PDF; name=certificate.pdf"));

        PutObjectRequest request = capturePutObjectRequest();
        assertThat(request.key()).endsWith(".pdf");
        assertThat(request.contentType()).isEqualTo("application/pdf");
    }

    private void assertDocumentRejected(MockMultipartFile document) {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> s3ImageService.uploadDocument(document)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE4004);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void uploadDocument_빈_파일이면_IMAGE4001() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "등록증.pdf", "application/pdf", new byte[0]);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> s3ImageService.uploadDocument(emptyFile)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE4001);
    }

    @Test
    void uploadDocument_확장자가_없으면_IMAGE4002() {
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> s3ImageService.uploadDocument(file("등록증", "application/pdf"))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE4002);
    }

    @Test
    void uploadDocument_S3_업로드에_실패하면_IMAGE5001() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connection reset"));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> s3ImageService.uploadDocument(file("등록증.pdf", "application/pdf"))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE5001);
    }

    @Test
    void upload_사진_업로드는_여전히_gif를_받는다() {
        String url = s3ImageService.upload(file("몽이.gif", "image/gif"));

        PutObjectRequest request = capturePutObjectRequest();
        assertThat(request.key()).matches("[0-9a-f-]{36}\\.gif");
        assertThat(url).isEqualTo(URL_PREFIX + request.key());
    }

    @Test
    void upload_사진_업로드는_PDF를_받지_않는다() {
        // 문서 업로드를 추가하면서 사진 업로드 경로로 PDF가 들어오게 되면 안 된다.
        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> s3ImageService.upload(file("몽이.pdf", "application/pdf"))
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorStatus.IMAGE4003);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
