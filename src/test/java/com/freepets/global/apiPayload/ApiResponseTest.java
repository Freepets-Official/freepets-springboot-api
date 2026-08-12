package com.freepets.global.apiPayload;

import tools.jackson.databind.ObjectMapper;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void onSuccess는_isSuccess_code_message_result를_포함한다() throws Exception {
        ApiResponse<String> response = ApiResponse.onSuccess("data");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).isEqualTo("{\"isSuccess\":true,\"code\":\"COMMON200\",\"message\":\"요청에 성공했습니다.\",\"result\":\"data\"}");
    }

    @Test
    void onFailure는_result가_없으면_result_키를_생략한다() throws Exception {
        ApiResponse<Object> response = ApiResponse.onFailure(ErrorStatus.COMMON400);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).isEqualTo("{\"isSuccess\":false,\"code\":\"COMMON400\",\"message\":\"잘못된 요청입니다.\"}");
    }
}