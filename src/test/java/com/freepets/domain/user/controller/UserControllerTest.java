package com.freepets.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.service.UserCommandService;
import com.freepets.domain.user.service.UserQueryService;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserQueryService userQueryService;

    @Test
    void signUp_성공하면_200과_빈_result를_반환한다() throws Exception {
        when(userCommandService.signUp(any())).thenReturn(new UserResponseDTO.SignUpResult());

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"password1\",\"nickname\":\"tester\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"isSuccess\":true,\"code\":\"COMMON200\",\"message\":\"요청에 성공했습니다.\",\"result\":{}}",
                        true
                ));
    }

    @Test
    void signUp_이메일이_중복되면_409를_반환한다() throws Exception {
        when(userCommandService.signUp(any())).thenThrow(new GeneralException(ErrorStatus.MEMBER4001));

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"password1\",\"nickname\":\"tester\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER4001"));
    }

    @Test
    void signUp_필드가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\",\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.email").exists())
                .andExpect(jsonPath("$.result.password").exists())
                .andExpect(jsonPath("$.result.nickname").exists());

        verifyNoInteractions(userCommandService);
    }

    @Test
    void signUp_이메일_형식이_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"password1\",\"nickname\":\"tester\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.email").exists());

        verify(userCommandService, never()).signUp(any());
    }

    @Test
    void login_성공하면_200과_토큰을_반환한다() throws Exception {
        when(userQueryService.login(any()))
                .thenReturn(new UserResponseDTO.LoginResult("access-token", "refresh-token"));

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.accessToken").value("access-token"))
                .andExpect(jsonPath("$.result.refreshToken").value("refresh-token"));
    }

    @Test
    void login_존재하지_않는_이메일이면_404를_반환한다() throws Exception {
        when(userQueryService.login(any())).thenThrow(new GeneralException(ErrorStatus.MEMBER4005));

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nope@test.com\",\"password\":\"password1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER4005"));
    }

    @Test
    void login_비밀번호가_일치하지_않으면_401을_반환한다() throws Exception {
        when(userQueryService.login(any())).thenThrow(new GeneralException(ErrorStatus.MEMBER4006));

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER4006"));
    }

    @Test
    void login_필드가_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.email").exists())
                .andExpect(jsonPath("$.result.password").exists());

        verifyNoInteractions(userQueryService);
    }
}
