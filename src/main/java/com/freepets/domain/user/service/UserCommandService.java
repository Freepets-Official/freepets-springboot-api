package com.freepets.domain.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.user.converter.UserConverter;
import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponseDTO.SignUpResult signUp(UserRequestDTO.SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new GeneralException(ErrorStatus.MEMBER4001);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = UserConverter.toUser(
                request,
                encodedPassword
        );
        User savedUser = userRepository.save(user);

        return UserConverter.toSignUpResult(savedUser);
    }
}
