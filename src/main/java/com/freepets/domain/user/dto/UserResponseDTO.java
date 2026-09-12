package com.freepets.domain.user.dto;

import java.util.List;

import com.freepets.domain.user.entity.Profile;

public class UserResponseDTO {

    private UserResponseDTO() {}

    public static class SignUpResult {}

    public static class PushTokenResult {}

    public static class WithdrawResult {}

    /**
     * @param userId 앱이 로그인 직후부터 사용자별 로컬 저장소를 가르는 데 쓴다. 문자열인 이유는
     *               {@code AuthResponseDTO.TokenRefreshResult}와 같다
     */
    public record LoginResult(
            String userId,
            String accessToken,
            String refreshToken
    ) {}

    /**
     * @param userId           앱이 사용자별 로컬 저장소를 가르는 데 쓴다. 이 API는 로그인 직후에도
     *                         호출되므로 재발급·재로그인 없이 항상 주인을 확인할 수 있다.
     *                         문자열인 이유는 {@code AuthResponseDTO.TokenRefreshResult}와 같다
     * @param profiles         고를 수 있는 프로필. {@code CONSUMER}는 항상, {@code OWNER}는 소유 매장이 있을 때만
     * @param ownedFacilityIds 소유한 시설 ID. 사업자 대시보드 진입용이며 소유 기록이 생긴 순서다
     */
    public record AccountResult(
            String userId,
            String nickname,
            String avatarUri,
            List<Profile> profiles,
            List<Long> ownedFacilityIds
    ) {}
}
