package com.freepets.domain.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.freepets.domain.user.converter.UserConverter;
import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.entity.UserDeviceToken;
import com.freepets.domain.user.repository.UserDeviceTokenRepository;
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
    private final UserDeviceTokenRepository userDeviceTokenRepository;

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

    /**
     * 소셜 로그인 사용자를 찾거나, 없으면 새로 가입시킨다.
     *
     * <p>조회 키는 {@code (provider, providerId)}뿐이다. 이메일은 바뀌거나 아예 없을 수 있어
     * 식별에 쓰지 않는다.
     *
     * <p>소셜 토큰 검증(외부 HTTP 호출)은 이 메서드 바깥에서 이미 끝난 상태로 들어온다.
     * 트랜잭션 안에서 외부 API를 기다리면 커넥션을 그만큼 붙잡게 되기 때문이다.
     *
     * @throws GeneralException 같은 이메일이 다른 가입 경로로 이미 존재하는 경우
     *                          ({@code OAUTH4003}). 자동 계정 연결은 이메일 신뢰 문제가 얽혀
     *                          있어 하지 않고, 원래 방식으로 로그인하도록 알린다
     */
    public SocialUserResolution findOrRegisterSocialUser(
            Provider provider,
            String providerId,
            String email,
            String name
    ) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .map(user -> new SocialUserResolution(user, false))
                .orElseGet(() -> new SocialUserResolution(
                        registerSocialUser(provider, providerId, email, name),
                        true
                ));
    }

    private User registerSocialUser(
            Provider provider,
            String providerId,
            String email,
            String name
    ) {
        if (email != null && userRepository.existsByEmail(email)) {
            throw new GeneralException(ErrorStatus.OAUTH4003);
        }

        User user = UserConverter.toSocialUser(provider, providerId, email, name);
        return userRepository.save(user);
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

    // 등록할 때마다 이 토큰을 가진 기존 행을 지우고 새로 저장한다 — 기기 재설치·기기 변경·다른
    // 계정으로 로그인 시 같은 토큰이 예전 소유자에게 남아 잘못 발송되는 걸 막는다.
    public UserResponseDTO.PushTokenResult registerPushToken(
            Long userId,
            UserRequestDTO.RegisterPushTokenRequest request
    ) {
        User user = findUser(userId);

        userDeviceTokenRepository.deleteByToken(request.getToken());
        userDeviceTokenRepository.save(
                UserDeviceToken.builder()
                        .user(user)
                        .token(request.getToken())
                        .platform(request.getPlatform())
                        .build()
        );

        return UserConverter.toPushTokenResult();
    }

    // 로그아웃·앱 삭제 시 프론트가 호출 — 안 부르면 그 토큰으로 계속 발송을 시도하다 FCM 응답을
    // 보고서야(무효 토큰 정리, DenialReportNotificationService) 뒤늦게 지워진다. 존재하지 않거나
    // 이미 다른 계정으로 갈아탄 토큰(재등록으로 소유자가 바뀐 경우)이면 조용히 넘어간다 — 로그아웃
    // 흐름에서 이걸로 에러를 낼 이유가 없다.
    public UserResponseDTO.PushTokenResult unregisterPushToken(
            Long userId,
            String token
    ) {
        userDeviceTokenRepository.findByToken(token)
                .filter(deviceToken -> deviceToken.getUser().getId().equals(userId))
                .ifPresent(userDeviceTokenRepository::delete);

        return UserConverter.toPushTokenResult();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
    }

    private boolean isNewAvatarPresent(MultipartFile avatar) {
        return avatar != null && !avatar.isEmpty();
    }
}
