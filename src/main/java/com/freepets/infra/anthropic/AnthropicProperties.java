package com.freepets.infra.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic(Claude) API 설정.
 *
 * <pre>
 * anthropic:
 *   api-key: 발급받은키
 * </pre>
 */
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String apiKey
) {
}
