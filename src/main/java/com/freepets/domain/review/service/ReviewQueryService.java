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

    // 등급 집계가 의미를 가지려면 최소 이만큼의 review_pets 표본이 필요하다고 보는 기준선 (제안값)
    private static final long MIN_SAMPLE_SIZE = 5;
    private static final int TOP_TAGS_LIMIT = 5;

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
        if (count == 0) {
            return new ReviewResponseDTO.Grade(0, "집계 전", 0, 0, MIN_SAMPLE_SIZE);
        }

        double weightedScoreSum = eligibleReviews.stream()
                .mapToDouble(review -> ReviewConverter.toScore100(review) * review.getReviewPets().size())
                .sum();
        double score = weightedScoreSum / count;
        int level = gradeLevel(score);

        return new ReviewResponseDTO.Grade(
                level,
                gradeLabel(level),
                roundToOneDecimal(score),
                count,
                Math.max(0, MIN_SAMPLE_SIZE - count)
        );
    }

    private int gradeLevel(double score) {
        if (score >= 90) {
            return 5;
        }
        if (score >= 80) {
            return 4;
        }
        if (score >= 70) {
            return 3;
        }
        if (score >= 60) {
            return 2;
        }
        return 1;
    }

    private String gradeLabel(int level) {
        return switch (level) {
            case 5 -> "동반 최고";
            case 4 -> "동반 우수";
            case 3 -> "동반 양호";
            case 2 -> "동반 보통";
            default -> "동반 주의";
        };
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
