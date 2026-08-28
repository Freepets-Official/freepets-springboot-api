package com.freepets.domain.pet.entity;

/**
 * 동물보호법 시행규칙 제1조의2가 정하는 맹견 품종. {@code Pet.species}(자유 텍스트)에 이 중 하나의
 * 키워드가 포함되면 맹견으로 판단한다 — 법 원문이 "및 그 잡종의 개"도 포함하므로 완전일치가 아니라
 * 부분일치로 본다(예: "로트와일러 믹스"도 매칭).
 */
public enum DangerousDogBreed {

    TOSA("도사견"),
    AMERICAN_PIT_BULL_TERRIER("핏불"),
    AMERICAN_STAFFORDSHIRE_TERRIER("아메리칸 스태퍼드셔"),
    STAFFORDSHIRE_BULL_TERRIER("스태퍼드셔 불"),
    ROTTWEILER("로트와일러");

    private final String keyword;

    DangerousDogBreed(String keyword) {
        this.keyword = keyword;
    }

    public static boolean matches(String species) {
        if (species == null || species.isBlank()) {
            return false;
        }

        for (DangerousDogBreed breed : values()) {
            if (species.contains(breed.keyword)) {
                return true;
            }
        }
        return false;
    }
}
