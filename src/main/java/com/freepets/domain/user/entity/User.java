package com.freepets.domain.user.entity;

import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.pet.entity.Pet;
import com.freepets.global.entity.BaseEntity;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * 소셜 가입자는 비밀번호가 없어 null이다. LOCAL 가입자만 채워진다.
     *
     * <p>ddl-auto=update가 NOT NULL을 자동으로 풀어주지 않으므로
     * 기존 DB에는 {@code db/pending-manual-migrations.sql}의 ALTER를 직접 실행해야 한다.
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /**
     * 소셜 제공자가 발급한 고유 식별자. 카카오 회원번호, 네이버 {@code response.id},
     * 구글·애플 {@code sub}가 여기 들어간다. LOCAL 가입자는 null이다.
     *
     * <p>제공자별 형식이 달라(카카오는 숫자) 문자열로 통일한다.
     * 이메일은 바뀌거나 아예 없을 수 있어 식별자로 쓰지 않고, 이 값으로만 사용자를 찾는다.
     */
    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(columnDefinition = "TEXT")
    private String avatarUri;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pet> pets = new ArrayList<>();

    // 게이미피케이션 누적치. gamification 도메인이 소유한 개념이지만, 여러 도메인(판별·리뷰·제보 등)이
    // 경험치를 주는 근거가 User 한 명이라 계정과 함께 다니는 값으로 여기 둔다 — 레벨 산정 공식 자체는
    // gamification.service.LevelCurve에 있고, User는 계산된 값을 들고만 있을 뿐 그 공식을 모른다
    // (엔티티가 상위 도메인 서비스를 참조하면 계층 규칙을 어기게 되므로, 새 레벨은 항상 호출부가
    // 계산해서 넘겨준다). 라이브 유저가 이미 있어 Course.isPublic과 같은 이유로 @ColumnDefault 필요.
    @ColumnDefault("0")
    @Column(name = "total_xp", nullable = false)
    private long totalXp;

    @ColumnDefault("1")
    @Column(nullable = false)
    private int level;

    @ColumnDefault("true")
    @Column(name = "level_up_notification_enabled", nullable = false)
    private boolean levelUpNotificationEnabled;

    @Builder
    private User(
            String email,
            String passwordHash,
            String nickname,
            Provider provider,
            String providerId,
            String avatarUri
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
        this.avatarUri = avatarUri;
        this.totalXp = 0;
        this.level = 1;
        this.levelUpNotificationEnabled = true;
    }

    public void update(
            String nickname,
            String avatarUri
    ) {
        this.nickname = nickname;
        this.avatarUri = avatarUri;
    }

    /**
     * 누적 경험치와 레벨을 반영한다. 둘 다 호출부(GamificationService)가 {@code LevelCurve}로
     * 미리 계산해서 넘긴다 — User는 계정 도메인 소속이라 gamification 도메인의 계산 공식을
     * 몰라야 하고, 이 메서드는 그 결과를 그대로 대입만 한다(같은 덧셈을 여기서 다시 하면
     * 호출부의 계산과 이 메서드의 계산이 갈라질 여지가 생긴다).
     *
     * @return 이번 지급으로 레벨이 올랐는지
     */
    public boolean gainXp(
            long newTotalXp,
            int newLevel
    ) {
        this.totalXp = newTotalXp;
        boolean isLeveledUp = newLevel > this.level;
        this.level = newLevel;
        return isLeveledUp;
    }

    public void toggleLevelUpNotification(boolean enabled) {
        this.levelUpNotificationEnabled = enabled;
    }

}
