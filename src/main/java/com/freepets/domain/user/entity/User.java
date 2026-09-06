package com.freepets.domain.user.entity;

import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.pet.entity.Pet;
import com.freepets.global.entity.BaseEntity;

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
    }

    public void update(
            String nickname,
            String avatarUri
    ) {
        this.nickname = nickname;
        this.avatarUri = avatarUri;
    }

}
