package com.freepets.domain.review.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.freepets.domain.facility.entity.Facility;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "rating_space", nullable = false)
    private Integer ratingSpace;

    @Column(name = "rating_staff", nullable = false)
    private Integer ratingStaff;

    @Column(name = "rating_amenity", nullable = false)
    private Integer ratingAmenity;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "show_pet_info", nullable = false)
    private boolean isShowPetInfo;

    @Column(name = "visited_at", nullable = false)
    private LocalDate visitedAt;

    // Pet과 동일하게 소프트 삭제로 둔다 — 하드 삭제로 지우면 신고 이력(reports)까지
    // cascade로 함께 사라져서 운영·감사 기록이 날아간다. Review.delete() 참고.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 앱 화면이 한 리뷰에 반려동물을 여러 마리 함께 선택할 수 있게 돼 있어 다대다로 둔다.
    // 등급·태그 집계는 review_pets 개수가 아니라 리뷰 1건당 1로 계산한다(ReviewQueryService 참고).
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewPet> reviewPets = new ArrayList<>();

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewTag> tags = new ArrayList<>();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewReport> reports = new ArrayList<>();

    @Builder
    private Review(
            Facility facility,
            User user,
            Integer ratingSpace,
            Integer ratingStaff,
            Integer ratingAmenity,
            String content,
            boolean isShowPetInfo,
            LocalDate visitedAt
    ) {
        this.facility = facility;
        this.user = user;
        this.ratingSpace = ratingSpace;
        this.ratingStaff = ratingStaff;
        this.ratingAmenity = ratingAmenity;
        this.content = content;
        this.isShowPetInfo = isShowPetInfo;
        this.visitedAt = visitedAt;
    }

    public void update(
            Integer ratingSpace,
            Integer ratingStaff,
            Integer ratingAmenity,
            String content,
            boolean isShowPetInfo,
            LocalDate visitedAt
    ) {
        this.ratingSpace = ratingSpace;
        this.ratingStaff = ratingStaff;
        this.ratingAmenity = ratingAmenity;
        this.content = content;
        this.isShowPetInfo = isShowPetInfo;
        this.visitedAt = visitedAt;
    }

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOwnedBy(Long userId) {
        return user != null && user.getId().equals(userId);
    }

}
