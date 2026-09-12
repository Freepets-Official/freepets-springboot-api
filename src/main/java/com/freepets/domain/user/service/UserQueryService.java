package com.freepets.domain.user.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.business.repository.FacilityOwnerClaimRepository;
import com.freepets.domain.user.converter.UserConverter;
import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.dto.UserResponseDTO;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.security.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;
    private final FacilityOwnerClaimRepository facilityOwnerClaimRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public UserResponseDTO.LoginResult login(UserRequestDTO.LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));

        // 소셜 가입자는 passwordHash가 null이다. matches()에 null을 넘기면 구현체에 따라
        // NPE가 나므로, 비밀번호 로그인 대상이 아님을 먼저 걸러낸다.
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new GeneralException(ErrorStatus.MEMBER4006);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        return UserConverter.toLoginResult(accessToken, refreshToken);
    }

    /**
     * 프로필 목록은 저장된 값이 아니라 소유 기록에서 매번 파생한다. 매장 등록 직후 이 API를 다시
     * 부르면 재로그인 없이 {@code OWNER}가 보인다.
     */
    public UserResponseDTO.AccountResult getAccount(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        List<Long> ownedFacilityIds = facilityOwnerClaimRepository.findFacilityIdsByUserId(userId);

        return UserConverter.toAccountResult(user, ownedFacilityIds);
    }

    /**
     * 토큰의 주인이 아직 쓸 수 있는 계정인지 확인한다. 탈퇴한 계정은 행이 남아있어도
     * 없는 것으로 본다.
     */
    public boolean isActiveUser(Long userId) {
        return userRepository.existsByIdAndDeletedAtIsNull(userId);
    }
}
