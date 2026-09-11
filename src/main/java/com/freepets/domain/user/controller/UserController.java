package com.freepets.domain.user.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.service.UserCommandService;
import com.freepets.domain.user.service.UserQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    @PostMapping("/signup")
    public ApiResponse<UserResponseDTO.SignUpResult> signUp(
            @Valid @RequestBody UserRequestDTO.SignUpRequest request
    ) {
        return ApiResponse.onSuccess(
                userCommandService.signUp(request)
        );
    }

    @PostMapping("/login")
    public ApiResponse<UserResponseDTO.LoginResult> login(
            @Valid @RequestBody UserRequestDTO.LoginRequest request
    ) {
        return ApiResponse.onSuccess(
                userQueryService.login(request)
        );
    }

    @GetMapping("/account")
    public ApiResponse<UserResponseDTO.AccountResult> getAccount(
            @AuthenticationPrincipal Long userId
    ) {
        return ApiResponse.onSuccess(
                userQueryService.getAccount(userId)
        );
    }

    @PatchMapping(value = "/account", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponseDTO.AccountResult> updateAccount(
            @AuthenticationPrincipal Long userId,
            @Valid @ModelAttribute UserRequestDTO.UpdateAccountRequest request
    ) {
        return ApiResponse.onSuccess(
                userCommandService.updateAccount(userId, request)
        );
    }

    /**
     * DELETE에 바디를 싣는 요청이라 {@code required = false}로 둔다 — 소셜 계정은 비밀번호가
     * 없어 바디 자체를 안 보낼 수 있는데, 일부 HTTP 클라이언트·프록시는 DELETE 요청에 바디를
     * 안 붙이거나 지워버린다. 기본값으로 요구하면 그런 요청이 서비스 로직(비밀번호 없어도
     * 되는 소셜 계정 처리)에 닿기 전에 "필수 바디 없음" 400으로 먼저 막혀버린다.
     */
    @DeleteMapping("/account")
    public ApiResponse<UserResponseDTO.WithdrawResult> withdraw(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) UserRequestDTO.WithdrawRequest request
    ) {
        return ApiResponse.onSuccess(
                userCommandService.withdraw(userId, request != null ? request : new UserRequestDTO.WithdrawRequest())
        );
    }

    @PostMapping("/push-tokens")
    public ApiResponse<UserResponseDTO.PushTokenResult> registerPushToken(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserRequestDTO.RegisterPushTokenRequest request
    ) {
        return ApiResponse.onSuccess(
                userCommandService.registerPushToken(userId, request)
        );
    }

    @DeleteMapping("/push-tokens")
    public ApiResponse<UserResponseDTO.PushTokenResult> unregisterPushToken(
            @AuthenticationPrincipal Long userId,
            @RequestParam String token
    ) {
        return ApiResponse.onSuccess(
                userCommandService.unregisterPushToken(userId, token)
        );
    }
}
