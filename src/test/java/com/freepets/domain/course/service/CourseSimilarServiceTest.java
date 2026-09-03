package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.CourseTheme;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.service.PetCheckJudgeService;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.domain.review.entity.ReviewReportStatus;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.FacilityPetProfile;
import com.freepets.domain.review.repository.FacilityTag;
import com.freepets.domain.review.repository.ReviewPetRepository;
import com.freepets.domain.review.repository.ReviewRepository;
import com.freepets.domain.review.repository.ReviewTagRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class CourseSimilarServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private PetSatisfactionRepository petSatisfactionRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewTagRepository reviewTagRepository;

    @Mock
    private ReviewPetRepository reviewPetRepository;

    @Mock
    private PetCheckJudgeService petCheckJudgeService;

    @Spy
    private CourseAssemblyService courseAssemblyService = new CourseAssemblyService();

    @InjectMocks
    private CourseSimilarService courseSimilarService;

    @Test
    void 좋아한_시설이_없으면_인기_코스로_대체된다() {
        // 취향 프로필(만족도 기록)이 아예 없는 신규 유저 — 카테고리/태그 매치가 불가능하므로
        // 개인화를 포기하고 리뷰 평점 기준 대체 추천으로 넘어가야 한다.
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility popularA = facility(10L, "인기카페", FacilityCategory.CAFE, true);
        Facility popularB = facility(11L, "인기관광지", FacilityCategory.TOUR, true);

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of());
        when(facilityRepository.findAllByIsActiveTrueAndPetAllowedNot(PetAllowed.DENIED))
                .thenReturn(List.of(popularA, popularB));
        when(petCheckJudgeService.judgeGroup(any(), any()))
                .thenReturn(new PetCheckJudgeService.GroupVerdict(PetCheckResult.ALLOWED, List.of()));
        when(reviewRepository.aggregateByFacilityIdIn(any(), eq(ReviewReportStatus.ACCEPTED)))
                .thenReturn(List.of());
        // reviewPetRepository.findKindAndBreedSizeByFacilityIdIn은 스텁하지 않는다 — 이 테스트는
        // 종/크기 매치를 검증하지 않고, 스텁 안 된 배치 조회는 Mockito 기본값(빈 리스트)으로 충분.

        CourseResponseDTO.SimilarCourseResult result = courseSimilarService.getSimilarCourse(1L, List.of(5L), null, null, null, null);

        assertThat(result.title()).isEqualTo("지금 인기 있는 곳");
        assertThat(result.isPersonalized()).isFalse();
        assertThat(result.stops()).hasSize(2);
    }

    @Test
    void 취향_프로필도_없고_대체_후보도_부족하면_COURSE4003() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of());
        when(facilityRepository.findAllByIsActiveTrueAndPetAllowedNot(PetAllowed.DENIED)).thenReturn(List.of());

        assertThatThrownBy(() -> courseSimilarService.getSimilarCourse(1L, List.of(5L), null, null, null, null))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 후보가_전부_실제_판별에서_막히면_COURSE4003() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility liked = facility(1L, "좋아한카페", FacilityCategory.CAFE, true);
        Facility candidate = facility(2L, "후보카페", FacilityCategory.CAFE, true);
        PetSatisfaction satisfaction = PetSatisfaction.builder().pet(몽이).facility(liked).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of(satisfaction));
        when(reviewTagRepository.findDistinctTagsByFacilityIdIn(Set.of(1L))).thenReturn(Set.of());
        when(facilityRepository.findAllByIsActiveTrueAndPetAllowedNotAndFacilityIdNotInAndCategoryIn(
                PetAllowed.DENIED, Set.of(1L), Set.of(FacilityCategory.CAFE)
        )).thenReturn(List.of(candidate));
        when(petCheckJudgeService.judgeGroup(any(), any()))
                .thenReturn(new PetCheckJudgeService.GroupVerdict(PetCheckResult.DENIED, List.of()));

        assertThatThrownBy(() -> courseSimilarService.getSimilarCourse(1L, List.of(5L), null, null, null, null))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 정상_케이스_matchedTags와_고정_타이틀이_채워진다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility liked = facility(1L, "좋아한카페", FacilityCategory.CAFE, true);
        Facility candidateA = facility(2L, "후보카페A", FacilityCategory.CAFE, true);
        Facility candidateB = facility(3L, "후보관광지", FacilityCategory.TOUR, true);
        PetSatisfaction satisfaction = PetSatisfaction.builder().pet(몽이).facility(liked).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of(satisfaction));
        when(reviewTagRepository.findDistinctTagsByFacilityIdIn(Set.of(1L))).thenReturn(Set.of(Tag.SPACIOUS));
        when(facilityRepository.findAllByIsActiveTrueAndPetAllowedNotAndFacilityIdNotInAndCategoryIn(
                PetAllowed.DENIED, Set.of(1L), Set.of(FacilityCategory.CAFE)
        )).thenReturn(List.of(candidateA));
        when(reviewTagRepository.findFacilityIdsByTagInExcluding(Set.of(Tag.SPACIOUS), Set.of(1L)))
                .thenReturn(List.of(3L));
        when(facilityRepository.findAllById(List.of(3L))).thenReturn(List.of(candidateB));
        when(petCheckJudgeService.judgeGroup(any(), any()))
                .thenReturn(new PetCheckJudgeService.GroupVerdict(PetCheckResult.ALLOWED, List.of()));
        when(reviewTagRepository.findTagsByFacilityIdIn(any()))
                .thenReturn(List.of(new FacilityTag(2L, Tag.SPACIOUS)));
        // reviewPetRepository.findKindAndBreedSizeByFacilityIdIn은 스텁하지 않는다 — 이 테스트는
        // 종/크기 매치를 검증하지 않고, 스텁 안 된 배치 조회는 Mockito 기본값(빈 리스트)으로 충분.

        CourseResponseDTO.SimilarCourseResult result = courseSimilarService.getSimilarCourse(1L, List.of(5L), null, null, null, null);

        assertThat(result.title()).isEqualTo("취향과 비슷한 새로운 곳");
        assertThat(result.stops()).hasSize(2);
        assertThat(result.stops().get(0).facilityId()).isEqualTo(2L); // 카테고리(3)+태그(0.1)=3.1점이 관광지(0점)보다 높음
        assertThat(result.stops().get(0).matchedTags()).containsExactly(Tag.SPACIOUS);
    }

    @Test
    void 테마_필터를_지정하면_카테고리가_다른_후보는_제외되고_최종_후보가_부족하면_COURSE4003() {
        // 필터 없으면 카페(카테고리 매치)·관광지(태그 매치) 둘 다 후보가 되지만, PET_CAFE
        // 테마(카테고리=CAFE)를 지정하면 관광지 후보가 제외되어 후보가 1곳으로 줄어야 한다.
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility liked = facility(1L, "좋아한카페", FacilityCategory.CAFE, true);
        Facility candidateCafe = facility(2L, "후보카페A", FacilityCategory.CAFE, true);
        Facility candidateTour = facility(3L, "후보관광지", FacilityCategory.TOUR, true);
        PetSatisfaction satisfaction = PetSatisfaction.builder().pet(몽이).facility(liked).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of(satisfaction));
        when(reviewTagRepository.findDistinctTagsByFacilityIdIn(Set.of(1L))).thenReturn(Set.of(Tag.SPACIOUS));
        when(facilityRepository.findAllByIsActiveTrueAndPetAllowedNotAndFacilityIdNotInAndCategoryIn(
                PetAllowed.DENIED, Set.of(1L), Set.of(FacilityCategory.CAFE)
        )).thenReturn(List.of(candidateCafe));
        when(reviewTagRepository.findFacilityIdsByTagInExcluding(Set.of(Tag.SPACIOUS), Set.of(1L)))
                .thenReturn(List.of(3L));
        when(facilityRepository.findAllById(List.of(3L))).thenReturn(List.of(candidateTour));
        // 테마 필터가 관광지 후보를 먼저 걸러내 judgeGroup은 남은 카페 후보에만 호출된다.
        when(petCheckJudgeService.judgeGroup(any(), any()))
                .thenReturn(new PetCheckJudgeService.GroupVerdict(PetCheckResult.ALLOWED, List.of()));

        assertThatThrownBy(() -> courseSimilarService
                .getSimilarCourse(1L, List.of(5L), null, null, null, Set.of(CourseTheme.PET_CAFE)))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 리뷰_동물_종이_같으면_가점을_받아_순위가_올라가고_다른_종이라고_제외되지는_않는다() {
        // sameKind/differentKind를 카테고리까지 같게 하면 조립(assemble)의 "카테고리당 1곳" 규칙
        // 때문에 후보가 1곳으로 줄어 최소 후보 수(2) 미달로 예외가 난다 — 카테고리를 다르게 해서
        // 종 보너스만으로 순위가 갈리는지를(카테고리 점수는 둘 다 3점으로 동일하게) 확인한다.
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이"); // Kind.DOG
        Facility likedCafe = facility(1L, "좋아한카페", FacilityCategory.CAFE, true);
        Facility likedTour = facility(2L, "좋아한관광지", FacilityCategory.TOUR, true);
        Facility sameKind = facility(3L, "같은종카페", FacilityCategory.CAFE, true);
        Facility differentKind = facility(4L, "다른종관광지", FacilityCategory.TOUR, true);
        PetSatisfaction satisfactionCafe = PetSatisfaction.builder().pet(몽이).facility(likedCafe).score(9.0f).build();
        PetSatisfaction satisfactionTour = PetSatisfaction.builder().pet(몽이).facility(likedTour).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionCafe, satisfactionTour));
        when(reviewTagRepository.findDistinctTagsByFacilityIdIn(Set.of(1L, 2L))).thenReturn(Set.of());
        when(facilityRepository.findAllByIsActiveTrueAndPetAllowedNotAndFacilityIdNotInAndCategoryIn(
                PetAllowed.DENIED, Set.of(1L, 2L), Set.of(FacilityCategory.CAFE, FacilityCategory.TOUR)
        )).thenReturn(List.of(sameKind, differentKind));
        when(reviewPetRepository.findKindAndBreedSizeByFacilityIdIn(any())).thenReturn(List.of(
                new FacilityPetProfile(3L, Kind.DOG, null),
                new FacilityPetProfile(4L, Kind.CAT, null)
        ));
        when(petCheckJudgeService.judgeGroup(any(), any()))
                .thenReturn(new PetCheckJudgeService.GroupVerdict(PetCheckResult.ALLOWED, List.of()));

        CourseResponseDTO.SimilarCourseResult result = courseSimilarService.getSimilarCourse(1L, List.of(5L), null, null, null, null);

        // 카테고리 점수는 둘 다 같아(3점) 종 보너스가 순위를 가른다 — 같은 종(DOG) 리뷰가 있는 쪽이 먼저.
        assertThat(result.stops()).extracting(CourseResponseDTO.SimilarStop::facilityId)
                .containsExactly(3L, 4L);
        assertThat(result.stops().get(0).matchedByKind()).isTrue();
        // 다른 종(CAT) 리뷰만 있다고 감점되어 제외되지는 않는다 — 후보로는 그대로 남는다.
        assertThat(result.stops().get(1).matchedByKind()).isFalse();
    }

    @Test
    void 크기_보너스는_종_보너스보다_작다() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이"); // Kind.DOG
        ReflectionTestUtils.setField(몽이, "breedSize", BreedSize.SMALL);
        Facility likedCafe = facility(1L, "좋아한카페", FacilityCategory.CAFE, true);
        Facility likedTour = facility(2L, "좋아한관광지", FacilityCategory.TOUR, true);
        Facility kindMatched = facility(3L, "종만같음", FacilityCategory.CAFE, true);
        Facility breedSizeMatched = facility(4L, "크기만같음", FacilityCategory.TOUR, true);
        PetSatisfaction satisfactionCafe = PetSatisfaction.builder().pet(몽이).facility(likedCafe).score(9.0f).build();
        PetSatisfaction satisfactionTour = PetSatisfaction.builder().pet(몽이).facility(likedTour).score(9.0f).build();

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionCafe, satisfactionTour));
        when(reviewTagRepository.findDistinctTagsByFacilityIdIn(Set.of(1L, 2L))).thenReturn(Set.of());
        when(facilityRepository.findAllByIsActiveTrueAndPetAllowedNotAndFacilityIdNotInAndCategoryIn(
                PetAllowed.DENIED, Set.of(1L, 2L), Set.of(FacilityCategory.CAFE, FacilityCategory.TOUR)
        )).thenReturn(List.of(kindMatched, breedSizeMatched));
        when(reviewPetRepository.findKindAndBreedSizeByFacilityIdIn(any())).thenReturn(List.of(
                new FacilityPetProfile(3L, Kind.DOG, BreedSize.LARGE), // 종만 일치
                new FacilityPetProfile(4L, Kind.CAT, BreedSize.SMALL)  // 크기만 일치
        ));
        when(petCheckJudgeService.judgeGroup(any(), any()))
                .thenReturn(new PetCheckJudgeService.GroupVerdict(PetCheckResult.ALLOWED, List.of()));

        CourseResponseDTO.SimilarCourseResult result = courseSimilarService.getSimilarCourse(1L, List.of(5L), null, null, null, null);

        // 종 보너스(0.5) > 크기 보너스(0.2) — 종만 일치한 쪽이 먼저 나온다.
        assertThat(result.stops()).extracting(CourseResponseDTO.SimilarStop::facilityId)
                .containsExactly(3L, 4L);
    }

    @Test
    void 후보가_많아도_판별_호출은_점수_상위_40개로_제한된다() {
        // similar가 느렸던 원인 — 실제 판별(judgeGroup)을 후보 전체에 대해 호출하고 있었다.
        // 후보를 45개 준비해도 judgeGroup은 상한(CANDIDATE_JUDGE_LIMIT=40)만큼만 호출돼야 한다.
        // 카테고리를 두 종류로 섞어 조립의 "카테고리당 1곳" 규칙에 걸려 결과 자체가 비어버리는
        // 것을 막는다(이 테스트의 관심사는 judgeGroup 호출 수지 조립 결과가 아니다).
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        Facility likedCafe = facility(1L, "좋아한카페", FacilityCategory.CAFE, true);
        Facility likedTour = facility(2L, "좋아한관광지", FacilityCategory.TOUR, true);
        PetSatisfaction satisfactionCafe = PetSatisfaction.builder().pet(몽이).facility(likedCafe).score(9.0f).build();
        PetSatisfaction satisfactionTour = PetSatisfaction.builder().pet(몽이).facility(likedTour).score(9.0f).build();

        List<Facility> manyCandidates = new ArrayList<>();
        IntStream.range(0, 45).forEach(index -> {
            FacilityCategory category = index % 2 == 0 ? FacilityCategory.CAFE : FacilityCategory.TOUR;
            manyCandidates.add(facility(100L + index, "후보" + index, category, true));
        });

        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L)))
                .thenReturn(List.of(satisfactionCafe, satisfactionTour));
        when(reviewTagRepository.findDistinctTagsByFacilityIdIn(Set.of(1L, 2L))).thenReturn(Set.of());
        when(facilityRepository.findAllByIsActiveTrueAndPetAllowedNotAndFacilityIdNotInAndCategoryIn(
                PetAllowed.DENIED, Set.of(1L, 2L), Set.of(FacilityCategory.CAFE, FacilityCategory.TOUR)
        )).thenReturn(manyCandidates);
        when(petCheckJudgeService.judgeGroup(any(), any()))
                .thenReturn(new PetCheckJudgeService.GroupVerdict(PetCheckResult.ALLOWED, List.of()));

        courseSimilarService.getSimilarCourse(1L, List.of(5L), null, null, null, null);

        verify(petCheckJudgeService, times(40)).judgeGroup(any(), any());
    }

    private User user(Long id) {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Pet pet(
            Long petId,
            User owner,
            String name
    ) {
        Pet pet = Pet.builder()
                .user(owner)
                .name(name)
                .kind(Kind.DOG)
                .species("말티즈")
                .weight(new BigDecimal("3.2"))
                .isVaccinated(true)
                .build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private Facility facility(
            Long facilityId,
            String name,
            FacilityCategory category,
            boolean isActive
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(category)
                .petAllowed(PetAllowed.ALLOWED)
                .isActive(isActive)
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("128.0"))
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
