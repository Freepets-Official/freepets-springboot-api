package com.freepets.domain.petcheck.entity;

public enum PetCheckResult {
    ALLOWED,
    CONDITIONAL,
    DENIED;

    // 그룹 판별의 overall은 아이들 result 중 심각도가 가장 높은 것을 취한다 (하나라도 DENIED면 DENIED).
    public static PetCheckResult mostSevere(
            PetCheckResult a,
            PetCheckResult b
    ) {
        return a.severity() >= b.severity() ? a : b;
    }

    private int severity() {
        return switch (this) {
            case ALLOWED -> 0;
            case CONDITIONAL -> 1;
            case DENIED -> 2;
        };
    }
}
