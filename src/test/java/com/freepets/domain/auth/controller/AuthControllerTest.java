package com.freepets.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.auth.dto.AuthResponseDTO;
import com.freepets.domain.auth.service.AuthCommandService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String REFRESH_TOKEN_HEADER = "RefreshToken";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthCommandService authCommandService;

    @Test
    void refresh_성공하면_200과_유저_아이디_및_두_토큰을_반환한다() throws Exception {
        when(authCommandService.refreshToken("refresh-token"))
                .thenReturn(new AuthResponseDTO.TokenRefreshResult("7", "new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(REFRESH_TOKEN_HEADER, "refresh-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.userId").value("7"))
                .andExpect(jsonPath("$.result.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.result.refreshToken").value("new-refresh-token"));
    }

    @Test
    void refresh_헤더가_없으면_400을_반환하고_서비스를_부르지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(authCommandService);
    }

    @Test
    void refresh_용도가_다른_토큰이면_401과_TOKEN4003을_반환한다() throws Exception {
        when(authCommandService.refreshToken(any()))
                .thenThrow(new GeneralException(ErrorStatus.TOKEN4003));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(REFRESH_TOKEN_HEADER, "access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("TOKEN4003"));
    }

    @Test
    void refresh_탈퇴한_계정이면_401과_MEMBER4007을_반환한다() throws Exception {
        when(authCommandService.refreshToken(any()))
                .thenThrow(new GeneralException(ErrorStatus.MEMBER4007));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(REFRESH_TOKEN_HEADER, "refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER4007"));
    }
}
