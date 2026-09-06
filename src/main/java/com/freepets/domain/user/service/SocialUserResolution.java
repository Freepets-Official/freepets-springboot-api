package com.freepets.domain.user.service;

import com.freepets.domain.user.entity.User;

/**
 * 소셜 로그인 조회 결과.
 *
 * @param isNewUser 이번 요청으로 계정이 새로 만들어졌는지. 앱이 프로필 등록 화면으로 보낼지
 *                  판단하는 근거이므로, 기존 로그인과 신규 가입을 호출자가 구분할 수 있어야 한다
 */
public record SocialUserResolution(
        User user,
        boolean isNewUser
) {
}
