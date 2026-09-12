package com.freepets.infra.nts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 국세청 진위확인 응답 판정. 네트워크를 타지 않도록 전송부({@link NtsApiCaller})를 목으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class NtsClientTest {

    private static final String SERVICE_KEY = "decoded-service-key";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NtsApiCaller apiCaller;

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private NtsClient createClient() {
        return new NtsClient(apiCaller, SERVICE_KEY);
    }

    private void givenResponse(String json) {
        when(apiCaller.postJson(anyString(), anyString())).thenReturn(read(json));
    }

    private NtsValidationResult validate() {
        return createClient().validate("1234567890", "홍길동", "20200315");
    }

    @Test
    void 일치하면_사업_상태까지_함께_반환한다() {
        // 진위확인 응답에 status가 함께 오기 때문에 상태조회를 따로 부르지 않는다.
        givenResponse("""
                {
                  "status_code": "OK", "request_cnt": 1, "valid_cnt": 1,
                  "data": [
                    {
                      "b_no": "1234567890", "valid": "01", "valid_msg": "",
                      "status": { "b_stt": "계속사업자", "b_stt_cd": "01", "tax_type": "부가가치세 일반과세자" }
                    }
                  ]
                }
                """);

        NtsValidationResult result = validate();

        assertThat(result.valid()).isTrue();
        assertThat(result.statusCode()).isEqualTo("01");
        assertThat(result.statusLabel()).isEqualTo("계속사업자");
    }

    @Test
    void 폐업한_사업자도_일치하면_상태_코드를_그대로_돌려준다() {
        // 차단 여부는 서비스 계층이 정한다. 클라이언트는 국세청이 준 값을 그대로 전달만 한다.
        givenResponse("""
                {
                  "data": [
                    {
                      "b_no": "1234567890", "valid": "01", "valid_msg": "",
                      "status": { "b_stt": "폐업자", "b_stt_cd": "03" }
                    }
                  ]
                }
                """);

        NtsValidationResult result = validate();

        assertThat(result.valid()).isTrue();
        assertThat(result.statusCode()).isEqualTo("03");
        assertThat(result.statusLabel()).isEqualTo("폐업자");
    }

    @Test
    void 불일치면_status가_없어도_사유와_함께_반환한다() {
        givenResponse("""
                {
                  "data": [
                    { "b_no": "1234567890", "valid": "02", "valid_msg": "확인할 수 없습니다." }
                  ]
                }
                """);

        NtsValidationResult result = validate();

        assertThat(result.valid()).isFalse();
        assertThat(result.validMessage()).isEqualTo("확인할 수 없습니다.");
        assertThat(result.statusCode()).isEmpty();
    }

    @Test
    void 응답에_사업자_정보가_없으면_예외를_던진다() {
        givenResponse("{\"status_code\": \"BAD_JSON_REQUEST\", \"data\": []}");

        NtsClient client = createClient();

        assertThrows(
                NtsException.class,
                () -> client.validate("1234567890", "홍길동", "20200315")
        );
    }

    @Test
    void 요청_본문은_국세청_필드명으로_보내고_서비스키는_URL에_싣는다() {
        givenResponse("""
                {
                  "data": [
                    { "valid": "01", "valid_msg": "", "status": { "b_stt": "계속사업자", "b_stt_cd": "01" } }
                  ]
                }
                """);

        validate();

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiCaller).postJson(uriCaptor.capture(), bodyCaptor.capture());

        assertThat(uriCaptor.getValue())
                .startsWith("https://api.odcloud.kr/api/nts-businessman/v1/validate?serviceKey=")
                .contains(SERVICE_KEY);
        assertThat(bodyCaptor.getValue())
                .contains("\"b_no\":\"1234567890\"")
                .contains("\"start_dt\":\"20200315\"")
                .contains("\"p_nm\":\"홍길동\"");
    }

    @Test
    void 이미_인코딩된_서비스키는_다시_인코딩하지_않는다() {
        // 포털이 주는 Encoding 키를 한 번 더 인코딩하면 서비스키 오류가 난다.
        String encodedKey = "abc%2Bdef%3D%3D";
        givenResponse("""
                {
                  "data": [
                    { "valid": "01", "valid_msg": "", "status": { "b_stt": "계속사업자", "b_stt_cd": "01" } }
                  ]
                }
                """);

        new NtsClient(apiCaller, encodedKey).validate("1234567890", "홍길동", "20200315");

        verify(apiCaller).postJson(
                eq("https://api.odcloud.kr/api/nts-businessman/v1/validate?serviceKey=" + encodedKey
                        + "&returnType=JSON"),
                anyString()
        );
    }

    @Test
    void 서비스키가_비어_있으면_클라이언트를_만들_수_없다() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NtsClient(apiCaller, "  ")
        );
    }
}
