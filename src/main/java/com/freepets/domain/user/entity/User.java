package com.freepets.domain.user.entity;

import java.time.LocalDateTime;
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

    /**
     * 탈퇴한 유저는 이 값을 null로 비운다({@link #withdraw}). 같은 이메일로 즉시 재가입할 수
     * 있어야 해서(재가입 시 유니크 제약과 부딪히면 안 됨) NOT NULL을 걸 수 없다.
     *
     * <p>ddl-auto=update가 NOT NULL을 자동으로 풀어주지 않으므로
     * 기존 DB에는 {@code db/pending-manual-migrations.sql}의 ALTER를 직접 실행해야 한다.
     */
    @Column(unique = true, length = 255)
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

    // 탈퇴 시점. null이면 활성 계정이다. Pet·Review처럼 소프트 삭제 — 탈퇴해도 이 유저가 쓴
    // 리뷰·공개 코스·거부 제보 등 남에게도 보이는 콘텐츠 행 자체는 지우지 않는다(작성자 표시는
    // withdraw()가 닉네임을 "탈퇴한 계정"으로 바꿔서 처리한다).
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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

    // 탈퇴한 유저의 닉네임은 이 값으로 덮어쓴다 — 실명·별명 등 개인을 특정할 수 있는 표시명이라
    // 남에게 보이는 콘텐츠(리뷰 등)에 그대로 남기지 않는다.
    private static final String WITHDRAWN_NICKNAME = "탈퇴한 계정";

    /**
     * 회원 탈퇴. 인증에 쓰이는 값(이메일·소셜 식별자·비밀번호)과 개인을 식별할 수 있는 값
     * (닉네임·아바타)을 비워, 같은 이메일·소셜 계정으로 즉시 재가입할 수 있게 하면서도 더는
     * 로그인할 수 없게 한다.
     *
     * <p>닉네임은 {@link #WITHDRAWN_NICKNAME}으로 바뀐다 — 이미 남에게 보이는 리뷰·공개 코스
     * 등에서 작성자 이름으로 쓰이고 있어(예: {@code ReviewConverter}), 탈퇴 후에는 그 표시도
     * "탈퇴한 계정"으로 나가야 한다.
     *
     * <p>소유한 반려동물도 함께 소프트 삭제한다 — 탈퇴한 계정의 반려동물 프로필은 더 쓸 일이
     * 없다. 아바타 이미지 파일(S3) 자체를 지우는 것은 호출부(UserCommandService)의 책임이다 —
     * 엔티티는 외부 I/O를 하지 않는다.
     */
    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
        this.email = null;
        this.providerId = null;
        this.passwordHash = null;
        this.avatarUri = null;
        this.nickname = WITHDRAWN_NICKNAME;
        // 이미 소프트 삭제된 펫까지 다시 delete()를 부르면 원래 삭제 시각이 지금 시각으로
        // 덮어써진다 — 아직 활성인 펫만 대상으로 한다.
        this.pets.stream()
                .filter(pet -> !pet.isDeleted())
                .forEach(Pet::delete);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

}
