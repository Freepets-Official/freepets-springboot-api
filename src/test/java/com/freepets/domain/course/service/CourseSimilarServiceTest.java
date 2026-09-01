package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.petcheck.entity.PetCheckResult;
import com.freepets.domain.petcheck.service.PetCheckJudgeService;
import com.freepets.domain.petsatisfaction.entity.PetSatisfaction;
import com.freepets.domain.petsatisfaction.repository.PetSatisfactionRepository;
import com.freepets.domain.review.entity.Tag;
import com.freepets.domain.review.repository.ReviewPetRepository;
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
    void 좋아한_시설이_없으면_COURSE4003() {
        User user = user(1L);
        Pet 몽이 = pet(5L, user, "몽이");
        when(petRepository.findAllByPetIdInAndDeletedAtIsNull(List.of(5L))).thenReturn(List.of(몽이));
        when(petSatisfactionRepository.findAllByPetPetIdIn(List.of(5L))).thenReturn(List.of());

        assertThatThrownBy(() -> courseSimilarService.getSimilarCourse(1L, List.of(5L)))
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

        assertThatThrownBy(() -> courseSimilarService.getSimilarCourse(1L, List.of(5L)))
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
        when(reviewTagRepository.findTagsByFacilityId(2L)).thenReturn(List.of(Tag.SPACIOUS));
        when(reviewTagRepository.findTagsByFacilityId(3L)).thenReturn(List.of());
        when(reviewPetRepository.findAllByReview_Facility_FacilityIdAndReview_DeletedAtIsNull(2L))
                .thenReturn(List.of());
        when(reviewPetRepository.findAllByReview_Facility_FacilityIdAndReview_DeletedAtIsNull(3L))
                .thenReturn(List.of());

        CourseResponseDTO.SimilarCourseResult result = courseSimilarService.getSimilarCourse(1L, List.of(5L));

        assertThat(result.title()).isEqualTo("취향과 비슷한 새로운 곳");
        assertThat(result.stops()).hasSize(2);
        assertThat(result.stops().get(0).facilityId()).isEqualTo(2L); // 카테고리(3)+태그(1)=4점이 관광지(3점)보다 높음
        assertThat(result.stops().get(0).matchedTags()).containsExactly(Tag.SPACIOUS);
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
