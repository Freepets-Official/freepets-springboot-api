package com.freepets.infra.nts;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 국세청 진위확인 실호출 탐사. 실제 외부 API를 부르므로 {@code test} 태스크에서는 돌지 않는다.
 *
 * <p>값을 주지 않으면 <b>존재하지 않는 번호</b>로 호출한다. 인증키가 살아 있는지, 요청 형식이
 * 받아들여지는지, 응답을 우리 파싱이 읽어내는지를 실제 사업자 정보 없이 확인하기 위해서다.
 * 이 경우 기대 결과는 {@code valid=false}이고 사유가 "확인할 수 없습니다"로 온다.
 *
 * <p>실제로 통과하는 경로(사업 상태까지 오는 응답)를 보려면 실제 값을 넘긴다.
 *
 * <pre>
 * ./gradlew ntsProbe
 * ./gradlew ntsProbe -Dnts.probe.business-number=1234567890 \
 *     -Dnts.probe.representative-name=홍길동 -Dnts.probe.opening-date=20200315
 * </pre>
 *
 * <p>서비스키는 환경변수 {@code NTS_SERVICE_KEY}가 우선이고, 없으면 {@code application.yml}의
 * {@code nts.service-key}를 읽는다. yml은 gitignore 대상이라 키가 저장소에 올라가지 않는다.
 */
@EnabledIfSystemProperty(
        named = "nts.probe",
        matches = "true",
        disabledReason = "실제 국세청 API를 호출하므로 ./gradlew ntsProbe 로만 실행한다"
)
class NtsProbeTest {

    private static final String SERVICE_KEY_PROPERTY = "nts.service-key";
    private static final String SERVICE_KEY_ENVIRONMENT = "NTS_SERVICE_KEY";

    /** 국세청에 없는 번호. 값을 안 넘겼을 때 쓰는 기본값이다. */
    private static final String ABSENT_BUSINESS_NUMBER = "0000000000";

    private static String optional(
            String propertyName,
            String defaultValue
    ) {
        String value = System.getProperty(propertyName);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String serviceKey() {
        String fromEnvironment = System.getenv(SERVICE_KEY_ENVIRONMENT);
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }

        String fromYaml = readServiceKeyFromApplicationYaml();
        if (fromYaml != null && !fromYaml.isBlank()) {
            return fromYaml;
        }

        throw new IllegalStateException(
                "인증키를 찾을 수 없습니다. application.yml에 아래를 추가하거나 환경변수 "
                        + SERVICE_KEY_ENVIRONMENT + "를 설정하세요.\n\n"
                        + "nts:\n"
                        + "  service-key: 발급받은키\n\n"
                        + "공공데이터포털 '국세청_사업자등록정보 진위확인 및 상태조회 서비스' 인증키여야 합니다."
        );
    }

    private static String readServiceKeyFromApplicationYaml() {
        try {
            ClassPathResource resource = new ClassPathResource("application.yml");
            if (!resource.exists()) {
                return null;
            }

            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load("application", resource);

            return sources.stream()
                    .map(source -> source.getProperty(SERVICE_KEY_PROPERTY))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    @Test
    void 진위확인_실호출() {
        String businessNumber = optional("nts.probe.business-number", ABSENT_BUSINESS_NUMBER);
        String representativeName = optional("nts.probe.representative-name", "홍길동");
        String openingDate = optional("nts.probe.opening-date", "20200101");

        boolean isSmokeTest = ABSENT_BUSINESS_NUMBER.equals(businessNumber);
        System.out.println(isSmokeTest
                ? "[모드] 없는 번호로 연결·인증·파싱만 확인한다 (valid=false가 정상)"
                : "[모드] 실제 사업자 정보로 확인한다");

        NtsClient client = new NtsClient(new NtsApiCaller(), serviceKey());
        NtsValidationResult result = client.validate(businessNumber, representativeName, openingDate);

        System.out.println("valid        = " + result.valid());
        System.out.println("validMessage = " + result.validMessage());
        System.out.println("statusCode   = " + result.statusCode());
        System.out.println("statusLabel  = " + result.statusLabel());
    }
}
