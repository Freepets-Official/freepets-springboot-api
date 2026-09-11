package com.freepets.domain.gamification.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.Badge;
import com.freepets.domain.gamification.entity.PawAnimal;
import com.freepets.domain.gamification.entity.PawColor;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.service.LevelCurve;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

class GamificationConverterTest {

    private User user(
            long totalXp,
            int level
    ) {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "totalXp", totalXp);
        ReflectionTestUtils.setField(user, "level", level);
        return user;
    }

    @Test
    void 레벨_2에서_다음_레벨까지_남은_XP를_계산한다() {
        // 레벨 3에 필요한 누적 XP는 300 — 지금 150이면 150이 남아야 한다.
        User user = user(150, 2);

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of());

        assertThat(status.xpToNextLevel()).isEqualTo(LevelCurve.xpToReachLevel(3) - 150);
        // 레벨 1=개·빨강, 레벨 2=개·주황(레벨마다 한 칸씩 색이 바뀐다).
        assertThat(status.tierAnimal()).isEqualTo(PawAnimal.DOG);
        assertThat(status.tierColor()).isEqualTo(PawColor.ORANGE);
        assertThat(status.tierLabel()).isEqualTo("개 발바닥 · 주황");
    }

    @Test
    void 최대_레벨이면_xpToNextLevel이_null이다() {
        User user = user(LevelCurve.xpToReachLevel(LevelCurve.MAX_LEVEL), LevelCurve.MAX_LEVEL);

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of());

        assertThat(status.xpToNextLevel()).isNull();
        // 레벨 70 = 10번째 동물(도마뱀)의 7번째 색(보라) — 10종×7색이 정확히 70에서 끝난다.
        assertThat(status.tierAnimal()).isEqualTo(PawAnimal.LIZARD);
        assertThat(status.tierColor()).isEqualTo(PawColor.VIOLET);
        assertThat(status.tierLabel()).isEqualTo("도마뱀 발바닥 · 보라");
    }

    @Test
    void 보유한_배지_목록이_그대로_담긴다() {
        User user = user(0, 1);
        UserBadge userBadge = UserBadge.builder().user(user).badge(Badge.FIRST_REVIEW).build();

        GamificationResponseDTO.MyStatus status = GamificationConverter.toMyStatus(user, List.of(userBadge));

        assertThat(status.badges()).hasSize(1);
        assertThat(status.badges().get(0).code()).isEqualTo("FIRST_REVIEW");
        assertThat(status.badges().get(0).label()).isEqualTo(Badge.FIRST_REVIEW.getLabel());
    }
}
