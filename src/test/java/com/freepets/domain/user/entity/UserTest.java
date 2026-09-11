package com.freepets.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;

class UserTest {

    private User user() {
        return User.builder()
                .email("test@test.com")
                .passwordHash("encodedPassword")
                .nickname("tester")
                .provider(Provider.LOCAL)
                .build();
    }

    private Pet pet(User owner) {
        return Pet.builder()
                .user(owner)
                .name("몽이")
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(new BigDecimal("3.2"))
                .breedSize(BreedSize.SMALL)
                .isVaccinated(true)
                .build();
    }

    @Test
    void 탈퇴하면_인증_정보와_닉네임이_비워지고_소유한_펫도_소프트_삭제된다() {
        User user = user();
        Pet activePet = pet(user);
        ReflectionTestUtils.setField(user, "pets", java.util.List.of(activePet));

        user.withdraw();

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProviderId()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getAvatarUri()).isNull();
        assertThat(user.getNickname()).isEqualTo("탈퇴한 계정");
        assertThat(activePet.isDeleted()).isTrue();
    }

    @Test
    void 탈퇴해도_이미_삭제된_펫의_삭제_시각은_덮어쓰지_않는다() {
        User user = user();
        Pet alreadyDeletedPet = pet(user);
        LocalDateTime originalDeletedAt = LocalDateTime.now().minusDays(10);
        ReflectionTestUtils.setField(alreadyDeletedPet, "deletedAt", originalDeletedAt);
        ReflectionTestUtils.setField(user, "pets", java.util.List.of(alreadyDeletedPet));

        user.withdraw();

        // withdraw()가 이미 삭제된 펫까지 다시 delete()를 부르면 원래 삭제 시각(10일 전)이
        // 지금 시각으로 덮어써진다 — 그러면 안 된다.
        assertThat(alreadyDeletedPet.getDeletedAt()).isEqualTo(originalDeletedAt);
    }
}
