package com.freepets.domain.review.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
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

    // size를 안 보내면 10건씩 내려준다.
    private static final int DEFAULT_PAGE_SIZE = 10;

    // 한 번에 너무 많이 긁어가지 못하게 상한을 둔다.
    private static final int MAX_PAGE_SIZE = 50;

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
            Long userId,
            int page,
            int size
    ) {
        if (!facilityRepository.existsById(facilityId)) {
            throw new GeneralException(ErrorStatus.FACILITY4041);
        }

        int safePage = Math.max(page, 0);
        // 0 이하로 오면 기본값으로, 상한을 넘으면 MAX_PAGE_SIZE로 잘라낸다.
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        List<Review> reviews = reviewRepository
                .findAllByFacilityFacilityIdAndDeletedAtIsNullOrderByCreatedAtDescReviewIdDesc(facilityId);

        Set<Long> excludedReviewIds = reviewReportRepository
                .findAllByStatusAndReviewFacilityFacilityId(ReviewReportStatus.ACCEPTED, facilityId)
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

        List<Review> pageContent = paginate(reviews, safePage, safeSize);
        List<ReviewResponseDTO.ReviewDetail> reviewDetails = pageContent.stream()
                .map(review -> ReviewConverter.toReviewDetail(review, reportedByMeReviewIds.contains(review.getReviewId())))
                .toList();

        boolean hasNext = (long) (safePage + 1) * safeSize < reviews.size();
        ReviewResponseDTO.PageInfo pageInfo = new ReviewResponseDTO.PageInfo(safePage, safeSize, reviews.size(), hasNext);

        return new ReviewResponseDTO.ReviewListResult(grade, categoryAverages, topTags, reviewDetails, pageInfo);
    }

    // 등급 집계는 시설 전체 리뷰가 필요해서 페이지네이션 없이 다 불러온 뒤, 화면에 내려줄
    // 목록만 여기서 요청받은 size 단위로 잘라낸다. page가 범위를 벗어나면 빈 목록을 반환한다.
    private List<Review> paginate(
            List<Review> reviews,
            int page,
            int size
    ) {
        // page가 아주 크면 page * size가 int 범위를 넘길 수 있어 long으로 계산한다.
        int fromIndex = (int) Math.min((long) page * size, reviews.size());
        int toIndex = (int) Math.min((long) fromIndex + size, reviews.size());
        return reviews.subList(fromIndex, toIndex);
    }

    private ReviewResponseDTO.Grade calculateGrade(List<Review> eligibleReviews) {
        long count = eligibleReviews.size();
        double score = count == 0 ? 0 : average(eligibleReviews, Review::toScore100);
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

    private ReviewResponseDTO.CategoryAverages calculateCategoryAverages(List<Review> eligibleReviews) {
        if (eligibleReviews.isEmpty()) {
            return new ReviewResponseDTO.CategoryAverages(0, 0, 0);
        }

        double space = roundToOneDecimal(average(eligibleReviews, Review::getRatingSpace));
        double staff = roundToOneDecimal(average(eligibleReviews, Review::getRatingStaff));
        double amenity = roundToOneDecimal(average(eligibleReviews, Review::getRatingAmenity));

        return new ReviewResponseDTO.CategoryAverages(space, staff, amenity);
    }

    private double average(
            List<Review> reviews,
            ToIntFunction<Review> valueExtractor
    ) {
        return reviews.stream()
                .mapToInt(valueExtractor)
                .average()
                .orElse(0);
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    // topTags/categoryAverages/grade는 review_pets(반려동물 수)가 아니라 리뷰 1건당 1로 집계한다.
    // 한 리뷰에 반려동물을 여러 마리 포함해도 통계에는 1건으로만 반영된다.
    private List<ReviewResponseDTO.TagCount> calculateTopTags(List<Review> eligibleReviews) {
        Map<Tag, Long> tagCounts = new EnumMap<>(Tag.class);
        for (Review review : eligibleReviews) {
            Set<Tag> tagsInReview = review.getTags().stream()
                    .map(ReviewTag::getTag)
                    .collect(Collectors.toSet());
            for (Tag tag : tagsInReview) {
                tagCounts.merge(tag, 1L, Long::sum);
            }
        }

        return tagCounts.entrySet().stream()
                .sorted(Map.Entry.<Tag, Long>comparingByValue().reversed())
                .limit(TOP_TAGS_LIMIT)
                .map(entry -> new ReviewResponseDTO.TagCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
