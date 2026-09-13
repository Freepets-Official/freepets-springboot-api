package com.freepets.domain.user.event;

/**
 * 회원이 탈퇴했을 때 발행된다 — {@code UserCommandService.withdraw}가 성공적으로 마친 시점.
 *
 * <p>user 도메인은 탈퇴 사실만 알리고, 이걸로 뭘 할지는 모른다(애플 토큰 폐기 등). 탈퇴 뒤에
 * 따라붙는 정리 작업이 늘어나도 발행부는 그대로 두고 구독자만 추가하면 된다. 구독자는
 * {@code com.freepets.domain.user.service.AppleTokenRevocationListener} 참고.
 *
 * <p>담는 값이 {@code userId}뿐인 이유 — {@code User.withdraw()}가 이메일·providerId를 비우기
 * 때문에 다른 값은 이 시점에 이미 신뢰할 수 없다. 필요한 정보는 구독자가 자기 테이블에서
 * 찾는다.
 */
public record UserWithdrawnEvent(
        Long userId
) {}
