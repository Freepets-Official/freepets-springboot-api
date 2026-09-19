package com.freepets.domain.pet.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PetTest {

    @Test
    void 맹견_품종의_개는_isDangerousBreed_true() {
        Pet pet = pet(Kind.DOG, "로트와일러");

        assertThat(pet.isDangerousBreed()).isTrue();
    }

    @Test
    void 맹견_품종이어도_개가_아니면_isDangerousBreed_false() {
        Pet pet = pet(Kind.CAT, "로트와일러");

        assertThat(pet.isDangerousBreed()).isFalse();
    }

    @Test
    void 맹견_품종이_아닌_개는_isDangerousBreed_false() {
        Pet pet = pet(Kind.DOG, "말티즈");

        assertThat(pet.isDangerousBreed()).isFalse();
    }

    @Test
    void gainXp는_레벨이_오르면_true를_반환하고_누적치를_반영한다() {
        Pet pet = pet(Kind.DOG, "말티즈"); // totalXp=0, level=1

        boolean isLeveledUp = pet.gainXp(150, 2);

        assertThat(pet.getTotalXp()).isEqualTo(150);
        assertThat(pet.getLevel()).isEqualTo(2);
        assertThat(isLeveledUp).isTrue();
    }

    @Test
    void gainXp는_레벨이_그대로면_false를_반환한다() {
        Pet pet = pet(Kind.DOG, "말티즈"); // totalXp=0, level=1

        boolean isLeveledUp = pet.gainXp(20, 1);

        assertThat(pet.getTotalXp()).isEqualTo(20);
        assertThat(pet.getLevel()).isEqualTo(1);
        assertThat(isLeveledUp).isFalse();
    }

    private Pet pet(Kind kind, String species) {
        return Pet.builder()
                .name("테스트")
                .kind(kind)
                .species(species)
                .weight(new BigDecimal("3.0"))
                .breedSize(BreedSize.SMALL)
                .isVaccinated(true)
                .build();
    }
}
