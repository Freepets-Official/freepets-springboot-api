package com.freepets.domain.pet.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.pet.dto.PetResponseDTO;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.global.util.BusinessZone;

class PetConverterTest {

    // PetConverter가 나이를 KST(BusinessZone.ZONE) 기준으로 계산하므로, 테스트의 "오늘" 기준도
    // 똑같이 KST여야 한다. LocalDate.now()(JVM 기본 타임존)를 쓰면 테스트 실행 시각이 자정
    // 전후(UTC/KST 날짜가 갈리는 구간)일 때만 드물게 하루 차이로 실패한다 — 실제로 이 테스트가
    // 그렇게 한 번 깨져서 알게 됐다.
    private static LocalDate today() {
        return LocalDate.now(BusinessZone.ZONE);
    }

    private Pet pet(LocalDate birthDate) {
        return Pet.builder()
                .name("몽이")
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(new BigDecimal("3.0"))
                .breedSize(BreedSize.SMALL)
                .birthDate(birthDate)
                .build();
    }

    @Test
    void toRegistrationCard는_생년월일로_만_나이를_계산한다() {
        // 오늘로부터 정확히 3년 전 생일 — 생일이 지난 상태라 만 3세여야 한다.
        Pet pet = pet(today().minusYears(3));

        PetResponseDTO.RegistrationCard card = PetConverter.toRegistrationCard(pet, List.of());

        assertThat(card.age()).isEqualTo(3);
    }

    @Test
    void toRegistrationCard는_생일이_아직_안_지났으면_한_살_적게_계산한다() {
        // 3년 전 오늘보다 하루 늦게 태어난 것으로 설정 — 올해 생일이 아직 안 지나서 만 2세여야 한다.
        Pet pet = pet(today().minusYears(3).plusDays(1));

        PetResponseDTO.RegistrationCard card = PetConverter.toRegistrationCard(pet, List.of());

        assertThat(card.age()).isEqualTo(2);
    }

    @Test
    void toRegistrationCard는_생년월일이_없으면_나이도_null이다() {
        Pet pet = pet(null);

        PetResponseDTO.RegistrationCard card = PetConverter.toRegistrationCard(pet, List.of());

        assertThat(card.age()).isNull();
    }

    @Test
    void toRegistrationCard는_생성일을_발급일로_쓴다() {
        Pet pet = pet(null);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        ReflectionTestUtils.setField(pet, "createdAt", createdAt);

        PetResponseDTO.RegistrationCard card = PetConverter.toRegistrationCard(pet, List.of());

        assertThat(card.issuedAt()).isEqualTo(createdAt);
    }
}
