package com.freepets.infra.oauth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.freepets.domain.user.entity.Provider;

/**
 * provider로 {@link OAuthClient}를 찾는다.
 *
 * <p>{@code OAuthConfig}가 등록한 클라이언트 목록을 받아 색인해 둔다.
 * 지원하지 않는 provider는 여기서 걸러지지 않고 {@code null}로 알려, HTTP 상태 결정은
 * 도메인 계층({@code AuthCommandService})이 하도록 남겨둔다.
 */
public class OAuthClientRegistry {

    private final Map<Provider, OAuthClient> clientsByProvider;

    public OAuthClientRegistry(List<OAuthClient> clients) {
        Map<Provider, OAuthClient> indexed = new EnumMap<>(Provider.class);
        for (OAuthClient client : clients) {
            OAuthClient previous = indexed.put(client.getProvider(), client);
            if (previous != null) {
                throw new IllegalStateException(
                        client.getProvider() + " 소셜 클라이언트가 중복 등록됐습니다."
                );
            }
        }
        this.clientsByProvider = Map.copyOf(indexed);
    }

    /**
     * @return 해당 provider를 지원하지 않으면 null
     */
    public OAuthClient find(Provider provider) {
        return clientsByProvider.get(provider);
    }
}
