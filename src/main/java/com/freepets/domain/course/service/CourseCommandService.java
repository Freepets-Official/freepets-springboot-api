package com.freepets.domain.course.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.course.converter.CourseConverter;
import com.freepets.domain.course.dto.CourseRequestDTO;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.entity.CourseStop;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.service.GamificationService;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

// POST/PUT/DELETE /api/v1/courses — 내 코스(CUSTOM) 저장/수정/삭제.
@Service
@RequiredArgsConstructor
@Transactional
public class CourseCommandService {

    private final CourseRepository courseRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final CourseAssemblyService courseAssemblyService;
    private final GamificationService gamificationService;
    private final PetCheckRepository petCheckRepository;
    private final ReviewRepository reviewRepository;

    // 코스가 처음 공개(isPublic=true)로 전환된 시점에 지급하는 경험치 — 스톱이 많을수록(그만큼
    // 판별·리뷰를 더 많이 실제로 남겨야 하므로) 조금씩 더 준다. courseId 자체는 매번 새로
    // 발급되는 값이라(같은 시설 구성이라도 코스를 새로 만들면 새 courseId) 이것만으로는 "평생
    // 1회"를 못 지킨다 — grantCoursePublishedXpIfEarned가 스톱 구성(시설 집합) 기준으로 한 번 더
    // 걸러낸다.
    private static final int COURSE_PUBLISHED_BASE_XP = 20;
    private static final int COURSE_PUBLISHED_XP_PER_STOP = 5;

    // 내가 공유한 코스를 다른 사람이 코드로 복사해갈 때마다 "원 소유자"에게 지급하는 경험치.
    private static final int COURSE_SHARED_COPY_XP = 15;

    public CourseResponseDTO.MyCourse createCourse(
            Long userId,
            CourseRequestDTO.SaveRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        List<Facility> stops = findFacilitiesInOrder(request.getStopIds());
        validateStopsPetAllowed(stops);

        if (request.isPublic()) {
            validateStopsEligibleForPublish(userId, stops);
        }

        Course course = Course.builder()
                .user(user)
                .name(request.getName())
                .description(request.getDescription())
                .source(CourseSource.CUSTOM)
                .isPublic(request.isPublic())
                .build();
        course.replaceStops(stops);

        Course saved = courseRepository.save(course);

        if (saved.isPublic()) {
            grantCoursePublishedXpIfEarned(userId, saved.getCourseId(), stops);
        }

        return CourseConverter.toMyCourse(saved);
    }

    public CourseResponseDTO.MyCourse updateCourse(
            Long userId,
            Long courseId,
            CourseRequestDTO.SaveRequest request
    ) {
        Course course = findOwnedCourse(userId, courseId);
        List<Facility> stops = findFacilitiesInOrder(request.getStopIds());
        validateStopsPetAllowed(stops);
        boolean isPublicBeforeUpdate = course.isPublic();

        if (request.isPublic()) {
            validateStopsEligibleForPublish(userId, stops);
        }

        course.update(request.getName(), request.getDescription(), stops);
        course.updateVisibility(request.isPublic());

        if (!isPublicBeforeUpdate && course.isPublic()) {
            grantCoursePublishedXpIfEarned(userId, course.getCourseId(), stops);
        }

        return CourseConverter.toMyCourse(course);
    }

    /**
     * PATCH /api/v1/courses/{courseId}/name — 이름만 바꾼다. 남이 만든 코스는(본인 소유가
     * 아니면) findOwnedCourse가 COURSE4042로 막는다 — 자기 코스만 바꿀 수 있다.
     */
    public CourseResponseDTO.MyCourse updateName(
            Long userId,
            Long courseId,
            String name
    ) {
        Course course = findOwnedCourse(userId, courseId);
        course.rename(name);

        return CourseConverter.toMyCourse(course);
    }

    /**
     * PATCH /api/v1/courses/{courseId}/visibility — 공개 여부만 바꾼다. updateCourse(PUT)는
     * name·stopIds 전체를 요구해서, 공개만 켜고 싶은 요청도 코스를 통째로 다시 보내야 했다 —
     * 그 부담 때문에 실제로는 토글이 거의 안 될 위험이 있어 가벼운 전용 경로를 따로 둔다.
     * 공개 전환 시 스톱 발행 요건 검증·XP 지급은 updateCourse와 동일하게 적용한다.
     */
    public CourseResponseDTO.MyCourse updateVisibility(
            Long userId,
            Long courseId,
            boolean isPublic
    ) {
        Course course = findOwnedCourse(userId, courseId);
        boolean isPublicBeforeUpdate = course.isPublic();
        List<Facility> stops = course.getStops().stream()
                .sorted(Comparator.comparingInt(CourseStop::getStopOrder))
                .map(CourseStop::getFacility)
                .toList();

        if (isPublic) {
            validateStopsEligibleForPublish(userId, stops);
        }

        course.updateVisibility(isPublic);

        if (!isPublicBeforeUpdate && course.isPublic()) {
            grantCoursePublishedXpIfEarned(userId, course.getCourseId(), stops);
        }

        return CourseConverter.toMyCourse(course);
    }

    /**
     * POST /api/v1/courses/optimize-order — 저장하지 않고 스톱 순서만 최근접 이웃 방식으로
     * 다듬어 미리 보여준다. AI 코스를 fork했거나 직접 검색해서 스톱을 추가/삭제한 뒤, 동선을
     * 정리하고 싶을 때 쓴다(그대로 저장하려면 이 결과를 다시 POST/PUT에 넣어야 한다).
     */
    public CourseResponseDTO.OrderResult optimizeOrder(List<Long> stopIds) {
        List<Facility> stops = findFacilitiesInOrder(stopIds);
        List<Facility> reordered = courseAssemblyService.reorderForCustomEdit(stops);

        List<Long> reorderedIds = reordered.stream().map(Facility::getFacilityId).toList();
        return new CourseResponseDTO.OrderResult(reorderedIds);
    }

    /**
     * PUT /api/v1/courses/{courseId}/stops/{stopOrder} — 그 자리(0부터 시작하는 순서)의
     * 스톱만 다른 시설로 교체한다. 나머지 스톱과 순서는 그대로 — "1·2·3·4·5에서 4번만
     * 6번으로" 같은 한 곳 스왑을 위해 매번 stopIds 전체를 다시 구성해 보낼 필요가 없게 한다.
     * 스톱을 추가하거나 빼서 개수 자체가 바뀌는 편집은 여전히 updateCourse(전체 교체)를 쓴다.
     */
    public CourseResponseDTO.MyCourse replaceStop(
            Long userId,
            Long courseId,
            int stopOrder,
            Long newFacilityId
    ) {
        Course course = findOwnedCourse(userId, courseId);
        List<Facility> facilitiesInOrder = course.getStops().stream()
                .sorted(Comparator.comparingInt(CourseStop::getStopOrder))
                .map(CourseStop::getFacility)
                .collect(Collectors.toCollection(ArrayList::new));

        if (stopOrder < 0 || stopOrder >= facilitiesInOrder.size()) {
            throw new GeneralException(ErrorStatus.COURSE4043);
        }

        Facility newFacility = facilityRepository.findById(newFacilityId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));
        validateStopsPetAllowed(List.of(newFacility));

        facilitiesInOrder.set(stopOrder, newFacility);

        // 이 코스가 이미 공개 상태라면, updateCourse(전체 교체)와 똑같이 스왑 후 스톱 전체가
        // 다시 발행 요건(판별+리뷰)을 만족하는지 확인한다 — 안 그러면 검증된 코스를 공개해둔
        // 뒤 이 엔드포인트로 한 스톱만 검증되지 않은 시설로 몰래 바꿔치기할 수 있다.
        if (course.isPublic()) {
            validateStopsEligibleForPublish(userId, facilitiesInOrder);
        }

        course.replaceStops(facilitiesInOrder);

        return CourseConverter.toMyCourse(course);
    }

    /**
     * POST /api/v1/courses/{courseId}/share — 공유 코드 발급. idempotent하게 이미 발급된
     * 코스면 새로 만들지 않고 기존 코드를 그대로 돌려준다 — 이미 공유해둔 코드가 재요청만으로
     * 조용히 무효화되면 안 된다.
     */
    public CourseResponseDTO.ShareResult shareCourse(
            Long userId,
            Long courseId
    ) {
        Course course = findOwnedCourse(userId, courseId);
        if (course.getShareCode() == null) {
            course.issueShareCode(CourseShareCodeGenerator.generate());
        }

        return CourseConverter.toShareResult(course);
    }

    /**
     * POST /api/v1/courses/shared/{shareCode}/copy — 공유 코드로 원본 코스를 복사해 내 코스로
     * 저장한다. 코드 자체가 공유 권한이라 원본의 소유자·isPublic 여부는 따지지 않는다(코드를
     * 모르면 어차피 못 옴 — VerifyCodeGenerator와 동일 논리). 이름·설명·스톱을 그대로 복제한
     * 완전히 새로운 CUSTOM 코스라, 이후 원본을 수정·삭제해도 이 사본엔 영향이 없다.
     */
    public CourseResponseDTO.MyCourse copySharedCourse(
            Long userId,
            String shareCode
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        Course original = courseRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new GeneralException(ErrorStatus.COURSE4044));

        List<Facility> stops = original.getStops().stream()
                .sorted(Comparator.comparingInt(CourseStop::getStopOrder))
                .map(CourseStop::getFacility)
                .toList();
        // 원본이 이 게이트가 생기기 전에 만들어졌거나, 저장 이후 시설의 petAllowed가 DENIED로
        // 바뀌었을 수 있다 — 복사도 결국 새 CUSTOM 코스를 만드는 경로라 createCourse/updateCourse와
        // 같은 검증을 거쳐야 한다.
        validateStopsPetAllowed(stops);

        Course copy = Course.builder()
                .user(user)
                .name(original.getName())
                .description(original.getDescription())
                .source(CourseSource.CUSTOM)
                .isPublic(false)
                .build();
        copy.replaceStops(stops);

        Course saved = courseRepository.save(copy);

        // 인기순 폴백(GET /courses/public)의 신호라 자기 복사여도 센다 — XP와 달리 파밍 방지
        // 대상이 아니다(경험치를 노리고 자기 코스를 반복 복사해도 "얼마나 담아갔는지"라는
        // 사실 자체는 바뀌지 않는다).
        original.incrementCopyCount();

        // 자기 코스를 자기 공유 코드로 복사하면 원 소유자 == 복사한 사람이라 실제 참여 없이도
        // 매번 새 courseId로 XP를 받아갈 수 있다(하루 상한만으로는 완전히 막지 못한다) —
        // 원 소유자 본인이 복사한 경우는 지급하지 않는다.
        if (!original.getUser().getId().equals(userId)) {
            gamificationService.grantXp(
                    original.getUser().getId(),
                    XpSourceType.COURSE_SHARED_COPY,
                    saved.getCourseId(),
                    COURSE_SHARED_COPY_XP
            );
        }

        return CourseConverter.toMyCourse(saved);
    }

    public CourseResponseDTO.DeleteResult deleteCourse(
            Long userId,
            Long courseId
    ) {
        Course course = findOwnedCourse(userId, courseId);
        courseRepository.delete(course);

        return new CourseResponseDTO.DeleteResult(courseId);
    }

    private Course findOwnedCourse(
            Long userId,
            Long courseId
    ) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.COURSE4041));

        if (!course.isOwnedBy(userId)) {
            throw new GeneralException(ErrorStatus.COURSE4042);
        }

        return course;
    }

    /**
     * 공개하려는 코스의 스톱 전부가 "실제로 다녀본 곳"이어야 한다 — 시설마다 이 유저의 판별
     * 기록과 리뷰가 둘 다 있어야 통과한다. 리뷰 작성 자체가 이미 판별 이력을 전제로 하지만
     * ({@link com.freepets.domain.review.service.ReviewCommandService#validateFacilityEligibility}),
     * 리뷰가 나중에 삭제될 수도 있어 판별 기록은 별도로 다시 확인한다.
     *
     * <p>트리비얼한 코스(방문한 적 없는 시설들로만 구성)를 마구 만들어 공개하는 것을 막는
     * 게이트다 — 공개 코스는 "둘러보기"에 노출되는 콘텐츠라 신뢰도를 담보해야 하고, 부수적으로
     * 코스 공개 경험치({@link XpSourceType#COURSE_PUBLISHED}) 악용도 막아준다.
     */
    private void validateStopsEligibleForPublish(
            Long userId,
            List<Facility> stops
    ) {
        boolean isAllStopsVerified = stops.stream().allMatch(facility ->
                petCheckRepository.existsByUserIdAndFacilityFacilityId(userId, facility.getFacilityId())
                        && reviewRepository.existsByFacilityFacilityIdAndUserIdAndDeletedAtIsNull(facility.getFacilityId(), userId)
        );

        if (!isAllStopsVerified) {
            throw new GeneralException(ErrorStatus.COURSE4045);
        }
    }

    private int coursePublishedXp(int newStopCount) {
        return COURSE_PUBLISHED_BASE_XP + COURSE_PUBLISHED_XP_PER_STOP * newStopCount;
    }

    /**
     * 코스 공개 XP를 스톱 구성 기준으로 다시 계산해 지급한다 — sourceId(courseId)만으로는 막지
     * 못하는 두 가지 파밍을 여기서 막는다. 판정 기준은 {@link Course}의 "현재" 스톱이 아니라
     * 지급 시점에 XpEvent에 함께 굳혀 저장한 componentSignature다 — 이후 그 코스가 수정되거나
     * 삭제돼도 이미 지급된 이력은 그대로 남는다(코스를 다시 조회할 필요도, N+1도 없다).
     *
     * <ol>
     *   <li>같은 시설 구성(순서 무관)으로 이미 XP를 받은 적이 있으면(과거 어느 날이든) 이번엔
     *       완전히 스킵한다 — 재정렬만 해서 새 코스로 다시 올리는 패턴을 막는다.
     *       componentSignature엔 DB 유니크 제약(uk_xp_events_user_source_type_component_signature)이
     *       걸려있어, 동시에 서로 다른 courseId로 같은 스톱 구성을 공개해도 GamificationService의
     *       grantXp가 DataIntegrityViolationException으로 한쪽을 마지막에 한 번 더 막는다.</li>
     *   <li>오늘 다른 코스로 이미 XP를 받은 스톱은 이번 코스에서 다시 세지 않는다 — 스톱
     *       하나짜리 차이만 두고 여러 코스를 만들어 하루 상한(5회)을 다 채우는 패턴을 막는다.
     *       겹치지 않는 새 스톱이 하나도 없으면(전부 오늘 이미 쓴 시설) 기본 지급(20XP)도 없이
     *       완전히 스킵한다.</li>
     * </ol>
     */
    private void grantCoursePublishedXpIfEarned(
            Long userId,
            Long courseId,
            List<Facility> stops
    ) {
        Set<Long> currentFacilityIds = stops.stream()
                .map(Facility::getFacilityId)
                .collect(Collectors.toSet());
        String componentSignature = componentSignatureOf(currentFacilityIds);

        if (gamificationService.existsGrantedForComponentSignature(userId, XpSourceType.COURSE_PUBLISHED, componentSignature)) {
            return;
        }

        Set<Long> alreadyCreditedTodayFacilityIds = gamificationService
                .findComponentSignaturesGrantedToday(userId, XpSourceType.COURSE_PUBLISHED).stream()
                .flatMap(CourseCommandService::facilityIdsFromComponentSignature)
                .collect(Collectors.toSet());

        long newStopCount = currentFacilityIds.stream()
                .filter(facilityId -> !alreadyCreditedTodayFacilityIds.contains(facilityId))
                .count();
        if (newStopCount == 0) {
            return;
        }

        gamificationService.grantXp(
                userId, XpSourceType.COURSE_PUBLISHED, courseId, coursePublishedXp((int) newStopCount), componentSignature
        );
    }

    // 정렬된 facilityId를 콤마로 이어붙인 정규화 표현 — 스톱 순서가 달라도 구성이 같으면 항상
    // 같은 문자열이 나와야, 이 값 하나로 "같은 시설 구성" 여부를 DB 유니크 제약까지 포함해
    // 그대로 판정할 수 있다.
    private static String componentSignatureOf(Set<Long> facilityIds) {
        return facilityIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private static Stream<Long> facilityIdsFromComponentSignature(String componentSignature) {
        if (componentSignature.isEmpty()) {
            return Stream.empty();
        }
        return Arrays.stream(componentSignature.split(",")).map(Long::valueOf);
    }

    private List<Facility> findFacilitiesInOrder(List<Long> stopIds) {
        Map<Long, Facility> byId = new LinkedHashMap<>();
        facilityRepository.findAllById(stopIds).forEach(facility -> byId.put(facility.getFacilityId(), facility));

        List<Facility> ordered = new ArrayList<>();
        for (Long facilityId : stopIds) {
            Facility facility = byId.get(facilityId);
            if (facility == null) {
                throw new GeneralException(ErrorStatus.FACILITY4001);
            }
            ordered.add(facility);
        }
        return ordered;
    }

    /**
     * 반려동물 동반이 애초에 불가능한(PetAllowed.DENIED) 시설은 코스에 담을 수 없다 —
     * preset/liked/similar 추천은 이미 후보 단계에서 DENIED를 걸러내지만(FacilityRepository의
     * findPresetCandidates 등), CUSTOM 코스는 직접 검색해서 담는 방식이라 이 게이트가 없으면
     * 프론트가 검색 필터를 깜빡했을 때 그대로 저장돼버린다. validateStopsEligibleForPublish(공개
     * 게이트, "방문한 적 있는지")와는 별개 검사다 — 이건 비공개 코스를 만들 때도 항상 적용된다.
     * PENDING(아직 파싱 안 됨)은 "불가로 확인된 것"이 아니므로 통과시킨다 — 다른 곳의 필터
     * (petAllowed <> DENIED)와 기준을 맞춘다.
     */
    private void validateStopsPetAllowed(List<Facility> stops) {
        boolean isAnyDenied = stops.stream().anyMatch(facility -> facility.getPetAllowed() == PetAllowed.DENIED);
        if (isAnyDenied) {
            throw new GeneralException(ErrorStatus.COURSE4046);
        }
    }

}
