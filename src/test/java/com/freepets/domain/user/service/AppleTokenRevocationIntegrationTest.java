package com.freepets.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.freepets.domain.user.dto.UserRequestDTO;
import com.freepets.domain.user.entity.AppleRefreshToken;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.AppleRefreshTokenRepository;
import com.freepets.domain.user.repository.UserRepository;

/**
 * 탈퇴 → 커밋 이후 폐기 리스너로 이어지는 배선이 실제로 동작하는지 확인한다.
 *
 * <p>유닛 테스트로는 잡히지 않는 두 가지를 본다.
 * <ul>
 *   <li>{@code AFTER_COMMIT} 리스너 안에서 한 DB 쓰기가 실제로 커밋되는가 — 이 구간은
 *       바깥 트랜잭션이 이미 끝난 뒤라, 전파 설정을 잘못 잡으면 <b>예외 없이 조용히 버려진다</b></li>
 *   <li>애플 설정이 없을 때 {@code ObjectProvider}가 빈 없음을 제대로 돌려주는가</li>
 * </ul>
 *
 * <p>{@code oauth.*}에 더미 값만 넣는 이유는 {@code FreepetsServerApplicationTests}와 같다.
 * 애플 폐기용 설정은 일부러 비워둔다 — 그 상태에서도 탈퇴가 끝까지 가야 한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "oauth.google.client-ids=dummy-google-client-id",
        "oauth.apple.client-ids=dummy-apple-bundle-id",
        "oauth.kakao.app-id=0"
})
class AppleTokenRevocationIntegrationTest {

    @Autowired
    private UserCommandService userCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppleRefreshTokenRepository appleRefreshTokenRepository;

    @Test
    void 탈퇴하면_커밋_이후_리스너가_폐기_실패를_기록한다() {
        User user = userRepository.save(User.builder()
                .email("apple-user@test.com")
                .nickname("애플회원")
                .provider(Provider.APPLE)
                .providerId("apple-sub-1")
                .build());
        appleRefreshTokenRepository.save(AppleRefreshToken.builder()
                .user(user)
                .encryptedRefreshToken("encrypted-token")
                .build());

        userCommandService.withdraw(user.getId(), new UserRequestDTO.WithdrawRequest());

        // 애플 설정이 없어 폐기는 실패하지만, 그 사실이 행에 남아 재시도 대상이 되어야 한다.
        // 여기가 null이면 리스너의 쓰기가 조용히 버려진 것이다.
        AppleRefreshToken stored = appleRefreshTokenRepository.findByUser_Id(user.getId()).orElseThrow();
        assertThat(stored.getRevokeRequestedAt()).isNotNull();
        assertThat(stored.getRevokeAttemptCount()).isEqualTo(1);
    }
}
