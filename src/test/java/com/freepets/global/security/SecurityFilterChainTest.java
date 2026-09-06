package com.freepets.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.freepets.domain.auth.controller.AuthController;
import com.freepets.domain.auth.dto.AuthResponseDTO;
import com.freepets.domain.auth.service.AuthCommandService;
import com.freepets.domain.user.controller.UserController;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.service.UserCommandService;
import com.freepets.domain.user.service.UserQueryService;
import com.freepets.global.config.SecurityConfig;
import com.freepets.global.config.JwtConfig;
import com.freepets.global.security.jwt.JwtProvider;

@WebMvcTest(controllers = {UserController.class, AuthController.class, SecurityTestPingController.class})
@Import({SecurityConfig.class, JwtConfig.class, JwtProvider.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private AuthCommandService authCommandService;

    @Test
    void 토큰없이_보호된_경로_요청시_401과_COMMON401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/security-test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    @Test
    void 잘못된_토큰으로_보호된_경로_요청시_401과_TOKEN4001을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/security-test/ping")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("TOKEN4001"));
    }

    @Test
    void 유효한_토큰으로_보호된_경로_요청시_200을_반환한다() throws Exception {
        String token = jwtProvider.createAccessToken(1L);

        mockMvc.perform(get("/api/v1/security-test/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("pong:1"));
    }

    @Test
    void signup_로그인_경로는_토큰없이도_통과한다() throws Exception {
        when(userCommandService.signUp(any())).thenReturn(new UserResponseDTO.SignUpResult());

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\",\"password\":\"password1\",\"nickname\":\"tester\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 소셜_로그인_경로는_토큰없이도_통과한다() throws Exception {
        // 아직 우리 토큰이 없는 상태로 들어오는 경로라 인증을 요구하면 로그인 자체가 불가능하다.
        when(authCommandService.socialLogin(any(), any()))
                .thenReturn(new AuthResponseDTO.SocialLoginResult("access", "refresh", true));

        mockMvc.perform(post("/api/v1/auth/social/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerToken\":\"provider-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.isNewUser").value(true));
    }
}
