package com.freepets.domain.review.entity;

import java.util.ArrayList;
import java.util.List;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.user.entity.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class Review {

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

    @Column(name = "space_score")
    private Float spaceScore;

    @Column(name = "staff_kindness")
    private Float staffKindness;

    @Column(name = "amenities_score")
    private Float amenitiesScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "facilities_merit", nullable = false)
    private FacilitiesMerit facilitiesMerit;

    @Column(columnDefinition = "TEXT")
    private String review;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewPet> reviewPets = new ArrayList<>();

    @Builder
    private Review(
            Facility facility,
            User user,
            Float spaceScore,
            Float staffKindness,
            Float amenitiesScore,
            FacilitiesMerit facilitiesMerit,
            String review
    ) {
        this.facility = facility;
        this.user = user;
        this.spaceScore = spaceScore;
        this.staffKindness = staffKindness;
        this.amenitiesScore = amenitiesScore;
        this.facilitiesMerit = facilitiesMerit;
        this.review = review;
    }

}
