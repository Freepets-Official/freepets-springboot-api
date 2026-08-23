package com.freepets.domain.review.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.review.converter.ReviewConverter;
import com.freepets.domain.review.dto.ReviewResponseDTO;
import com.freepets.domain.review.entity.Review;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.entity.ReviewTag;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.ReviewReportRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private static final int TOP_TAGS_LIMIT = 5;

    // 등급 기준표(발자국). 점수·리뷰 수 둘 다 만족해야 그 등급이 되며, 높은 등급부터 확인한다.
    private static final List<GradeTier> GRADE_TIERS = List.of(
            new GradeTier(5, "최고 등급", 94, 150),
            new GradeTier(4, "동반 우수", 88, 90),
            new GradeTier(3, "동반 추천", 80, 50),
            new GradeTier(2, "동반 편안", 70, 25),
            new GradeTier(1, "동반 가능", 60, 10)
    );

    // 어떤 등급도 못 받았을 때 "리뷰 수집 중 (n/10)"의 분모 — 최하위 등급(레벨 1)의 필요 리뷰 수와 같다.
    private static final long MIN_REVIEW_COUNT_FOR_ANY_GRADE = 10;

    private record GradeTier(
            int level,
            String label,
            int minScore,
            long minCount
    ) {}

    private final FacilityRepository facilityRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;

    public ReviewResponseDTO.ReviewListResult getReviews(
            Long facilityId,
            Long userId
    ) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new GeneralException(ErrorStatus.FACILITY4041);
        }

        List<Review> reviews = reviewRepository.findAllByFacilityFacilityId(facilityId);

        Set<Long> excludedReviewIds = reviewReportRepository
                .findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.APPROVED, facilityId)
                .stream()
                .map(reviewReport -> reviewReport.getReview().getReviewId())
                .collect(Collectors.toSet());

        List<Long> reviewIds = reviews.stream()
                .map(Review::getReviewId)
                .toList();
        Set<Long> reportedByMeReviewIds = reviewReportRepository
                .findAllByUserIdAndReviewReviewIdIn(userId, reviewIds)
                .stream()
                .map(reviewReport -> reviewReport.getReview().getReviewId())
                .collect(Collectors.toSet());

        List<Review> eligibleReviews = reviews.stream()
                .filter(review -> !excludedReviewIds.contains(review.getReviewId()))
                .toList();

        ReviewResponseDTO.Grade grade = calculateGrade(eligibleReviews);
        ReviewResponseDTO.CategoryAverages categoryAverages = calculateCategoryAverages(eligibleReviews);
        List<ReviewResponseDTO.TagCount> topTags = calculateTopTags(eligibleReviews);

        List<ReviewResponseDTO.ReviewDetail> reviewDetails = reviews.stream()
                .map(review -> ReviewConverter.toReviewDetail(review, reportedByMeReviewIds.contains(review.getReviewId())))
                .toList();

        return new ReviewResponseDTO.ReviewListResult(grade, categoryAverages, topTags, reviewDetails);
    }

    private long weightedCount(List<Review> reviews) {
        return reviews.stream()
                .mapToLong(review -> review.getReviewPets().size())
                .sum();
    }

    private ReviewResponseDTO.Grade calculateGrade(List<Review> eligibleReviews) {
        long count = weightedCount(eligibleReviews);
        double score = count == 0 ? 0 : weightedScore(eligibleReviews, count);
        long needMore = Math.max(0, MIN_REVIEW_COUNT_FOR_ANY_GRADE - count);

        GradeTier tier = GRADE_TIERS.stream()
                .filter(candidate -> score >= candidate.minScore() && count >= candidate.minCount())
                .findFirst()
                .orElse(null);

        if (tier == null) {
            return new ReviewResponseDTO.Grade(0, "리뷰 수집 중 (%d/10)".formatted(count), roundToOneDecimal(score), count, needMore);
        }

        return new ReviewResponseDTO.Grade(tier.level(), tier.label(), roundToOneDecimal(score), count, needMore);
    }

    private double weightedScore(
            List<Review> reviews,
            long count
    ) {
        double weightedScoreSum = reviews.stream()
                .mapToDouble(review -> ReviewConverter.toScore100(review) * review.getReviewPets().size())
                .sum();
        return weightedScoreSum / count;
    }

    private ReviewResponseDTO.CategoryAverages calculateCategoryAverages(List<Review> eligibleReviews) {
        long count = weightedCount(eligibleReviews);
        if (count == 0) {
            return new ReviewResponseDTO.CategoryAverages(0, 0, 0);
        }

        double space = weightedAverage(eligibleReviews, Review::getRatingSpace, count);
        double staff = weightedAverage(eligibleReviews, Review::getRatingStaff, count);
        double amenity = weightedAverage(eligibleReviews, Review::getRatingAmenity, count);

        return new ReviewResponseDTO.CategoryAverages(space, staff, amenity);
    }

    private double weightedAverage(
            List<Review> reviews,
            Function<Review, Integer> ratingExtractor,
            long count
    ) {
        double sum = reviews.stream()
                .mapToDouble(review -> ratingExtractor.apply(review) * review.getReviewPets().size())
                .sum();
        return roundToOneDecimal(sum / count);
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private List<ReviewResponseDTO.TagCount> calculateTopTags(List<Review> eligibleReviews) {
        Map<Tag, Long> tagCounts = new EnumMap<>(Tag.class);
        for (Review review : eligibleReviews) {
            long weight = review.getReviewPets().size();
            for (ReviewTag reviewTag : review.getTags()) {
                tagCounts.merge(reviewTag.getTag(), weight, Long::sum);
            }
        }

        return tagCounts.entrySet().stream()
                .sorted(Map.Entry.<Tag, Long>comparingByValue().reversed())
                .limit(TOP_TAGS_LIMIT)
                .map(entry -> new ReviewResponseDTO.TagCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
