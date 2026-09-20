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
