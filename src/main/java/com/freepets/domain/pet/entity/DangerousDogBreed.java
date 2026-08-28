package com.freepets.domain.pet.entity;

import java.util.List;

/**
 * 동물보호법 시행규칙 제1조의2가 정하는 맹견 품종. {@code Pet.species}(자유 텍스트)에 이 중 하나의
 * 키워드가 포함되면 맹견으로 판단한다 — 법 원문이 "및 그 잡종의 개"도 포함하므로 완전일치가 아니라
 * 부분일치로 본다(예: "로트와일러 믹스"도 매칭). 한글 표기와 영문 표기를 둘 다 등록해서, 영문으로
 * 입력된 품종명(예: "Rottweiler")도 잡는다 — 대소문자는 구분하지 않는다.
 */
public enum DangerousDogBreed {

    TOSA(List.of("도사견", "tosa")),
    AMERICAN_PIT_BULL_TERRIER(List.of("핏불", "pit bull", "pitbull")),
    AMERICAN_STAFFORDSHIRE_TERRIER(List.of("아메리칸 스태퍼드셔", "american staffordshire")),
    STAFFORDSHIRE_BULL_TERRIER(List.of("스태퍼드셔 불", "staffordshire bull")),
    ROTTWEILER(List.of("로트와일러", "rottweiler"));

    private final List<String> keywords;

    DangerousDogBreed(List<String> keywords) {
        this.keywords = keywords;
    }

    public static boolean matches(String species) {
        if (species == null || species.isBlank()) {
            return false;
        }

        String normalized = species.toLowerCase();
        for (DangerousDogBreed breed : values()) {
            for (String keyword : breed.keywords) {
                if (normalized.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
}
