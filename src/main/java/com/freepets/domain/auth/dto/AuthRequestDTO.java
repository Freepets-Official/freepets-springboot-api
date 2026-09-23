package com.freepets.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class AuthRequestDTO {

    private AuthRequestDTO() {}

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SocialLoginRequest {

        /**
         * 앱이 소셜 SDK로 로그인해 받은 토큰.
         * 카카오·네이버는 액세스 토큰, 구글·애플은 id_token을 넣는다.
         */
        @NotBlank(message = "소셜 로그인 토큰은 필수입니다.")
        private String providerToken;

        /**
         * 사용자 이름. 애플 최초 로그인에서만 필요하다.
         *
         * <p>애플은 이름을 id_token에 담지 않고 최초 인가 응답에서 클라이언트에게 한 번만
         * 전달하므로, 그때 앱이 받아서 같이 보내줘야 서버가 저장할 수 있다.
         * 두 번째 로그인부터는 이미 저장돼 있어 보내지 않아도 된다.
         */
        private String name;

        /**
         * 애플이 발급한 인가 코드. 애플 로그인에서만 쓴다.
         *
         * <p>서버가 이 코드를 애플 refresh token으로 바꿔 보관해두었다가, 탈퇴할 때 애플에
         * 토큰 폐기를 요청한다(App Store 심사 지침 5.1.1(v)). id_token은 신원을 증명할 뿐
         * 폐기할 수 있는 값이 아니라서 이 코드가 따로 필요하다.
         *
         * <p>{@code name}과 달리 <b>애플 로그인을 할 때마다 매번</b> 보내야 한다 — 5분 만에
         * 만료되는 1회용 값이라 서버가 재사용할 수 없다.
         *
         * <p>없어도 로그인은 정상 처리된다. 그래서 필수로 걸지 않는다 — 이 값을 보내지 않는
         * 구버전 앱도 그대로 동작해야 하고, 애초에 다른 제공자는 보내지 않는 값이다.
         */
        private String authorizationCode;
    }
}
