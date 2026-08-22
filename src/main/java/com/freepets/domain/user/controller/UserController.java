package com.freepets.domain.user.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
