package com.freepets.domain.stamp.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.user.entity.User;
import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여권 도장 — 사용자가 방문 인증한 시설 1건. (user, facility) 조합에 유니크 제약을 걸어 같은
 * 시설에 중복으로 찍히지 않게 한다({@code freepets-docs/docs/14-도장-서버-저장.md} "같은 시설은
 * 다시 세지 않는다").
 *
 * <p>{@code facilityName}·{@code sido}·{@code sigungu}·{@code sidoCode}·{@code sigunguCode}는
 * 찍는 시점의 {@link Facility} 값을 스냅샷한다 — 도장첩을 그릴 때마다 시설을 다시 조회하지 않아도
 * 되고, 나중에 시설 정보가 바뀌거나(관광공사 재동기화) 시설이 비활성화돼도 이미 찍은 도장의 표시가
 * 흔들리지 않는다.
 */
@Getter
@Entity
@Table(
        name = "stamps",
        uniqueConstraints = @UniqueConstraint(name = "uk_stamps_user_facility", columnNames = {"user_id", "facility_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stamp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stamp_id")
    private Long stampId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(name = "facility_name", nullable = false, length = 200)
    private String facilityName;

    @Column(length = 30)
    private String sido;

    @Column(length = 30)
    private String sigungu;

    @Column(name = "sido_code", length = 10)
    private String sidoCode;

    @Column(name = "sigungu_code", length = 10)
    private String sigunguCode;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    @Column(name = "verified_on_site", nullable = false)
    private boolean isVerifiedOnSite;

    /**
     * "찍은 시각". {@link BaseEntity#getCreatedAt()}은 Spring Data JPA Auditing이 INSERT
     * 시점에 항상 "지금"으로 덮어써서(값을 미리 넣어도 무시된다) 기기 이전 마이그레이션(과거
     * 날짜로 저장)에 쓸 수 없다 — 그래서 도메인이 실제로 보여줄 시각은 이 필드로 따로 둔다
     * ({@code Review.visitedAt}이 같은 이유로 분리돼 있다).
     */
    @Column(name = "stamped_at", nullable = false)
    private LocalDateTime stampedAt;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "stamp", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StampPet> stampPets = new ArrayList<>();

    @Builder
    private Stamp(
            User user,
            Facility facility,
            String facilityName,
            String sido,
            String sigungu,
            String sidoCode,
            String sigunguCode,
            String photoUrl,
            boolean isVerifiedOnSite,
            LocalDateTime stampedAt
    ) {
        this.user = user;
        this.facility = facility;
        this.facilityName = facilityName;
        this.sido = sido;
        this.sigungu = sigungu;
        this.sidoCode = sidoCode;
        this.sigunguCode = sigunguCode;
        this.photoUrl = photoUrl;
        this.isVerifiedOnSite = isVerifiedOnSite;
        this.stampedAt = stampedAt;
    }

    public void replacePets(List<Pet> pets) {
        this.stampPets.clear();
        for (Pet pet : pets) {
            this.stampPets.add(StampPet.builder().stamp(this).pet(pet).build());
        }
    }

    /**
     * 원거리({@code false})로 찍었던 도장을 현장 확인({@code true})으로 승격한다. 반대 방향(참
     * → 거짓)은 없다 — 이미 확인된 현장 기록을 원거리로 되돌릴 이유가 없다.
     */
    public void promoteToOnSite() {
        this.isVerifiedOnSite = true;
    }

}
