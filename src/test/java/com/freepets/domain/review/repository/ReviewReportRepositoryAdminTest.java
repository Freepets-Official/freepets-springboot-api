package com.freepets.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * 관리자 신고 목록 쿼리 검증. {@code group by}와 직접 적은 count 쿼리는 목으로 잡을 수 없어 실제 DB(H2)에
 * 넣고 돌린다({@link ReviewRepositoryAggregateTest}와 같은 설정).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:reviewreportadmin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ReviewReportRepositoryAdminTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 20, 10, 0);

    @Autowired
    private ReviewReportRepository reviewReportRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private EntityManager entityManager;

    private Facility facility;
    private int userSequence;

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
    }

    private User saveUser() {
        userSequence++;
        User user = User.builder()
                .email("user" + userSequence + "@test.com")
                .passwordHash("encodedPassword")
                .nickname("유저" + userSequence)
                .provider(Provider.LOCAL)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Review saveReview() {
        Review review = Review.builder()
                .facility(facility)
                .user(saveUser())
                .ratingSpace(5)
                .ratingStaff(5)
                .ratingAmenity(5)
                .content("좋았어요")
                .isShowPetInfo(true)
                .visitedAt(LocalDate.of(2026, 8, 1))
                .build();
        entityManager.persist(review);
        return review;
    }

    // 감사 설정이 persist 시점에 createdAt을 현재 시각으로 채우고, IDENTITY라 그 자리에서 insert까지 된다.
    // createdAt은 updatable=false라 필드를 바꿔도 반영되지 않으므로 정렬을 검증할 수 있게 벌크 update로 덮어쓴다.
    private void saveReport(
            Review review,
            ReviewReportReason reason,
            ReviewReportStatus status,
            LocalDateTime reportedAt
    ) {
        ReviewReport report = ReviewReport.builder()
                .review(review)
                .user(saveUser())
                .reason(reason)
                .status(status)
                .build();
        entityManager.persist(report);
        entityManager.flush();
        entityManager.createQuery("UPDATE ReviewReport report SET report.createdAt = :reportedAt WHERE report.reportId = :reportId")
                .setParameter("reportedAt", reportedAt)
                .setParameter("reportId", report.getReportId())
                .executeUpdate();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("신고를 리뷰 단위로 묶고 첫 신고가 오래된 순으로 정렬한다")
    void 신고를_리뷰_단위로_묶고_첫_신고가_오래된_순으로_정렬한다() {
        Review recentReview = saveReview();
        Review oldReview = saveReview();
        saveReport(recentReview, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME.plusHours(2));
        saveReport(oldReview, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME);
        saveReport(oldReview, ReviewReportReason.ABUSE, ReviewReportStatus.PENDING, BASE_TIME.plusHours(5));
        flushAndClear();

        Page<ReportedReviewSummary> page =
                reviewReportRepository.findReportedReviewSummaries(ReviewReportStatus.PENDING, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(ReportedReviewSummary::reviewId, ReportedReviewSummary::reportCount,
                        ReportedReviewSummary::firstReportedAt)
                .containsExactly(
                        tuple(oldReview.getReviewId(), 2L, BASE_TIME),
                        tuple(recentReview.getReviewId(), 1L, BASE_TIME.plusHours(2))
                );
    }

    @Test
    @DisplayName("다른 상태의 신고와 삭제된 리뷰의 신고는 목록에서 빠진다")
    void 다른_상태의_신고와_삭제된_리뷰의_신고는_목록에서_빠진다() {
        Review acceptedReview = saveReview();
        saveReport(acceptedReview, ReviewReportReason.SPAM, ReviewReportStatus.ACCEPTED, BASE_TIME);

        Review deletedReview = saveReview();
        saveReport(deletedReview, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME);
        deletedReview.delete();

        Review pendingReview = saveReview();
        saveReport(pendingReview, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME);
        flushAndClear();

        Page<ReportedReviewSummary> page =
                reviewReportRepository.findReportedReviewSummaries(ReviewReportStatus.PENDING, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent())
                .extracting(ReportedReviewSummary::reviewId)
                .containsExactly(pendingReview.getReviewId());
    }

    @Test
    @DisplayName("전체 건수는 신고 수가 아니라 리뷰 수다")
    void 전체_건수는_신고_수가_아니라_리뷰_수다() {
        Review first = saveReview();
        Review second = saveReview();
        Review third = saveReview();
        saveReport(first, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME);
        saveReport(first, ReviewReportReason.ABUSE, ReviewReportStatus.PENDING, BASE_TIME.plusMinutes(1));
        saveReport(second, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME.plusMinutes(2));
        saveReport(third, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME.plusMinutes(3));
        flushAndClear();

        Page<ReportedReviewSummary> page =
                reviewReportRepository.findReportedReviewSummaries(ReviewReportStatus.PENDING, PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("사유별 건수는 주어진 상태의 신고만 센다")
    void 사유별_건수는_주어진_상태의_신고만_센다() {
        Review review = saveReview();
        saveReport(review, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME);
        saveReport(review, ReviewReportReason.SPAM, ReviewReportStatus.PENDING, BASE_TIME);
        saveReport(review, ReviewReportReason.ABUSE, ReviewReportStatus.PENDING, BASE_TIME);
        saveReport(review, ReviewReportReason.PRIVACY, ReviewReportStatus.REJECTED, BASE_TIME);
        flushAndClear();

        List<ReviewReasonCount> counts =
                reviewReportRepository.countReasonsByReviewIdIn(List.of(review.getReviewId()), ReviewReportStatus.PENDING);

        assertThat(counts)
                .extracting(ReviewReasonCount::reason, ReviewReasonCount::count)
                .containsExactlyInAnyOrder(
                        tuple(ReviewReportReason.SPAM, 2L),
                        tuple(ReviewReportReason.ABUSE, 1L)
                );
    }

    @Test
    @DisplayName("리뷰를 시설과 작성자와 함께 가져온다")
    void 리뷰를_시설과_작성자와_함께_가져온다() {
        Review review = saveReview();
        flushAndClear();

        List<Review> reviews = reviewRepository.findAllWithFacilityAndUserByReviewIdIn(List.of(review.getReviewId()));

        assertThat(reviews).hasSize(1);
        // 영속성 컨텍스트를 비운 뒤라 지연 로딩이었다면 초기화되지 않은 프록시가 나온다.
        assertThat(Hibernate.isInitialized(reviews.get(0).getFacility())).isTrue();
        assertThat(Hibernate.isInitialized(reviews.get(0).getUser())).isTrue();
    }
}
