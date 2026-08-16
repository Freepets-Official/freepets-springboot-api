package com.freepets.domain.user.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.service.UserCommandService;
import com.freepets.global.apiPayload.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserCommandService userCommandService;

    @PostMapping("/signup")
    public ApiResponse<UserResponseDTO.SignUpResult> signUp(
            @Valid @RequestBody UserRequestDTO.SignUpRequest request
    ) {
        return ApiResponse.onSuccess(
                userCommandService.signUp(request)
        );
    }
}
