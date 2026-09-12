package com.freepets.domain.user.dto;

import java.util.List;

import com.freepets.domain.user.entity.Profile;

public class UserResponseDTO {

    private UserResponseDTO() {}

    public static class SignUpResult {}

    public static class PushTokenResult {}

    public static class WithdrawResult {}

    public record LoginResult(
            String accessToken,
            String refreshToken
    ) {}

    /**
     * @param profiles         고를 수 있는 프로필. {@code CONSUMER}는 항상, {@code OWNER}는 소유 매장이 있을 때만
     * @param ownedFacilityIds 소유한 시설 ID. 사업자 대시보드 진입용이며 소유 기록이 생긴 순서다
     */
    public record AccountResult(
            String nickname,
            String avatarUri,
            List<Profile> profiles,
            List<Long> ownedFacilityIds
    ) {}
}
