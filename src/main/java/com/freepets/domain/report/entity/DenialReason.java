package com.freepets.domain.report.entity;

// 문 앞에서 거부당한 이유. 예전엔 이 이름으로 "제보 자체가 반려된 사유"(DUPLICATE 등)를
// 담았는데 코드 어디서도 안 쓰이던 죽은 값이었다 — enum 이름(DenialReason = 거부 사유) 자체엔
// 오히려 이쪽 의미가 더 맞아서, F4(거부 실시간 경고) 구현과 함께 이 값들로 교체했다.
public enum DenialReason {
    WEIGHT("체중 초과"),
    BREED("견종 제한"),
    INDOOR("실내 불가"),
    POLICY_CHANGED("정책이 바뀜"),
    CROWDED("혼잡·자리 없음"),
    OTHER("그 밖의 이유");

    private final String label;

    DenialReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
