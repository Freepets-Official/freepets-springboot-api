package com.freepets.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReport;
import com.freepets.domain.review.entity.ReviewReportReason;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

import com.freepets.global.config.JpaAuditingConfig;

import jakarta.persistence.EntityManager;

/**
 * 리뷰 집계 쿼리 검증.
 *
 * <p>목으로는 절대 잡을 수 없는 부분이라 실제 DB에 넣고 돌린다. 운영 DB가 아니라 H2를 쓰며,
 * 스키마 이름은 운영과 맞춰 만들어둔다.
 */
@DataJpaTest
// 내장 DB로 갈아끼우는 기본 동작을 끈다. 그래야 아래에서 지정한 H2 URL의 INIT 절이 살아
// 운영과 같은 freepets 스키마가 만들어진다.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// BaseEntity의 createdAt/updatedAt은 not null이라 감사 설정이 없으면 저장 자체가 안 된다.
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aggregate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ReviewRepositoryAggregateTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private EntityManager entityManager;

    private Facility facility;
    private User user;

    @BeforeEach
    void setUp() {
        facility = Facility.builder()
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.ALLOWED)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
        entityManager.persist(facility);

        user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("몽이아빠")
                .provider(Provider.LOCAL)
                .build();
        entityManager.persist(user);
    }

    private Review saveReview(
            int ratingSpace,
            int ratingStaff,
            int ratingAmenity
    ) {
        Review review = Review.builder()
                .facility(facility)
                .user(user)
                .ratingSpace(ratingSpace)
                .ratingStaff(ratingStaff)
                .ratingAmenity(ratingAmenity)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.of(2026, 8, 1))
                .build();
        entityManager.persist(review);
        return review;
    }

    private void accept(Review review) {
        ReviewReport report = ReviewReport.builder()
                .review(review)
                .user(user)
                .reason(ReviewReportReason.SPAM)
                .status(ReviewReportStatus.ACCEPTED)
                .build();
        entityManager.persist(report);
    }

    private Optional<FacilityReviewAggregate> aggregate() {
        entityManager.flush();
        entityManager.clear();
        return reviewRepository.aggregateByFacilityId(
                facility.getFacilityId(), ReviewReportStatus.ACCEPTED);
    }

    @Test
    @DisplayName("적격 리뷰가 없으면 빈 결과를 반환한다")
    void 적격_리뷰가_없으면_빈_결과를_반환한다() {
        assertThat(aggregate()).isEmpty();
    }

    @Test
    @DisplayName("점수는 Review.toScore100의 평균과 같다")
    void 점수는_Review_toScore100의_평균과_같다() {
        List<Review> reviews = List.of(
                saveReview(4, 5, 3),
                saveReview(5, 5, 5),
                saveReview(1, 2, 3)
        );
        double expected = reviews.stream()
                .mapToInt(Review::toScore100)
                .average()
                .orElseThrow();

        FacilityReviewAggregate result = aggregate().orElseThrow();

        assertThat(result.reviewCount()).isEqualTo(3);
        assertThat(result.averageScore()).isEqualTo(expected);
        assertThat(result.averageSpace()).isEqualTo(10.0 / 3);
        assertThat(result.averageStaff()).isEqualTo(12.0 / 3);
        assertThat(result.averageAmenity()).isEqualTo(11.0 / 3);
    }

    @Test
    @DisplayName("승인된 신고가 달린 리뷰는 집계에서 빠진다")
    void 승인된_신고가_달린_리뷰는_집계에서_빠진다() {
        saveReview(5, 5, 5);
        accept(saveReview(1, 1, 1));

        FacilityReviewAggregate result = aggregate().orElseThrow();

        assertThat(result.reviewCount()).isEqualTo(1);
        assertThat(result.averageSpace()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("접수만 된 신고는 집계에 그대로 남는다")
    void 접수만_된_신고는_집계에_그대로_남는다() {
        Review review = saveReview(5, 5, 5);
        ReviewReport pending = ReviewReport.builder()
                .review(review)
                .user(user)
                .reason(ReviewReportReason.FALSE_INFO)
                .status(ReviewReportStatus.PENDING)
                .build();
        entityManager.persist(pending);

        assertThat(aggregate().orElseThrow().reviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("한 리뷰에 승인된 신고가 여러 건이어도 리뷰는 1건으로 센다")
    void 한_리뷰에_승인된_신고가_여러_건이어도_리뷰는_1건으로_센다() {
        saveReview(5, 5, 5);
        Review reported = saveReview(1, 1, 1);
        accept(reported);
        accept(reported);

        // 조인이었다면 신고 수만큼 행이 늘어 count가 부풀려진다.
        assertThat(aggregate().orElseThrow().reviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 리뷰는 집계에서 빠진다")
    void 삭제된_리뷰는_집계에서_빠진다() {
        saveReview(5, 5, 5);
        saveReview(1, 1, 1).delete();

        FacilityReviewAggregate result = aggregate().orElseThrow();

        assertThat(result.reviewCount()).isEqualTo(1);
        assertThat(result.averageSpace()).isEqualTo(5.0);
    }

}
