package com.freepets.domain.review.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewPet> reviewPets = new ArrayList<>();

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

    public boolean isOwnedBy(Long userId) {
        return user != null && user.getId().equals(userId);
    }

}
