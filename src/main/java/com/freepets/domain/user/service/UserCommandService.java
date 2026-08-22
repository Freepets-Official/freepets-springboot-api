package com.freepets.domain.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.freepets.domain.user.converter.UserConverter;
import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.infra.s3.S3ImageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final S3ImageService s3ImageService;

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

    public UserResponseDTO.AccountResult updateAccount(
            Long userId,
            UserRequestDTO.UpdateAccountRequest request
    ) {
        User user = findUser(userId);

        String previousAvatarUri = user.getAvatarUri();
        String avatarUri = isNewAvatarPresent(request.getAvatar())
                ? s3ImageService.upload(request.getAvatar())
                : previousAvatarUri;

        user.update(request.getNickname(), avatarUri);

        if (isNewAvatarPresent(request.getAvatar()) && previousAvatarUri != null) {
            s3ImageService.delete(previousAvatarUri);
        }

        return UserConverter.toAccountResult(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
    }

    private boolean isNewAvatarPresent(MultipartFile avatar) {
        return avatar != null && !avatar.isEmpty();
    }
}
