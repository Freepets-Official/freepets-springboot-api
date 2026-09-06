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
    }
}
