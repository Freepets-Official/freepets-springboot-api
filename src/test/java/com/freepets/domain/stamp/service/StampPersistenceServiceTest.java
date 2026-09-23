package com.freepets.domain.stamp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.gamification.service.GamificationService;
import com.freepets.domain.stamp.dto.StampResponseDTO;
import com.freepets.domain.stamp.entity.RegionCompletion;
import com.freepets.domain.stamp.entity.Stamp;
import com.freepets.domain.stamp.repository.StampRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

/**
 * 도장 저장·승격·지역 완성 판정(DB 쓰기)만 다룬다 — 사진 업로드·좌표 재판정은
 * StampCommandServiceTest 참고.
 */
@ExtendWith(MockitoExtension.class)
class StampPersistenceServiceTest {

    @Mock
    private StampRepository stampRepository;

    @Mock
    private RegionCompletionService regionCompletionService;

    @Mock
    private GamificationService gamificationService;

    private StampPersistenceService stampPersistenceService;

    private void setUpService() {
        stampPersistenceService = new StampPersistenceService(stampRepository, regionCompletionService, gamificationService);
    }

    private User user() {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private Facility facility(
            long facilityId,
            String sidoCode,
            String sigunguCode
    ) {
        Facility facility = Facility.builder()
                .name("헤이도그 애견카페")
                .sido("강원특별자치도")
                .sigungu("강릉시")
                .sidoCode(sidoCode)
                .sigunguCode(sigunguCode)
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

    @Test
    void 처음_찍는_시설이면서_처음_찍는_지역이면_isNew와_isRegionCompleted가_모두_true다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L, "51", "150");
        when(stampRepository.findByUser_IdAndFacility_FacilityId(1L, 7L)).thenReturn(Optional.empty());
        when(stampRepository.existsByUser_IdAndSidoCodeAndSigunguCode(1L, "51", "150")).thenReturn(false);
        when(stampRepository.save(any(Stamp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RegionCompletion regionCompletion = RegionCompletion.builder()
                .user(user).sidoCode("51").sigunguCode("150")
                .completionOrder(1L).completedAt(LocalDateTime.now())
                .build();
        when(regionCompletionService.completeRegion(user, "51", "150")).thenReturn(regionCompletion);

        StampResponseDTO.StampResult result =
                stampPersistenceService.persist(user, facility, List.of(), null, false, LocalDateTime.now());

        assertThat(result.isNew()).isTrue();
        assertThat(result.isRegionCompleted()).isTrue();
        assertThat(result.completionOrder()).isEqualTo(1L);
        assertThat(result.facilityId()).isEqualTo(7L);
        verify(regionCompletionService).completeRegion(user, "51", "150");
        verify(gamificationService).evaluateStampBadge(eq(user), any(Long.class));
        verify(gamificationService).evaluateRegionBadge(eq(user), any(Long.class));
    }

    @Test
    void 이미_같은_지역에_도장이_있으면_새_시설이어도_isRegionCompleted는_false다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L, "51", "150");
        when(stampRepository.findByUser_IdAndFacility_FacilityId(1L, 7L)).thenReturn(Optional.empty());
        when(stampRepository.existsByUser_IdAndSidoCodeAndSigunguCode(1L, "51", "150")).thenReturn(true);
        when(stampRepository.save(any(Stamp.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StampResponseDTO.StampResult result =
                stampPersistenceService.persist(user, facility, List.of(), null, false, LocalDateTime.now());

        assertThat(result.isNew()).isTrue();
        assertThat(result.isRegionCompleted()).isFalse();
        assertThat(result.completionOrder()).isNull();
        verify(regionCompletionService, never()).completeRegion(any(), any(), any());
    }

    @Test
    void 이미_찍은_시설에_다시_요청하면_저장하지_않고_isNew는_false다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L, "51", "150");
        Stamp existing = Stamp.builder()
                .user(user).facility(facility).facilityName("헤이도그 애견카페")
                .sido("강원특별자치도").sigungu("강릉시").sidoCode("51").sigunguCode("150")
                .isVerifiedOnSite(true).stampedAt(LocalDateTime.now())
                .build();
        when(stampRepository.findByUser_IdAndFacility_FacilityId(1L, 7L)).thenReturn(Optional.of(existing));

        StampResponseDTO.StampResult result =
                stampPersistenceService.persist(user, facility, List.of(), null, false, LocalDateTime.now());

        assertThat(result.isNew()).isFalse();
        assertThat(result.isRegionCompleted()).isFalse();
        verify(stampRepository, never()).save(any());
        verify(regionCompletionService, never()).completeRegion(any(), any(), any());
    }

    @Test
    void 원거리로_찍혀있던_도장은_isVerifiedOnSite_true로_들어오면_승격된다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L, "51", "150");
        Stamp existing = Stamp.builder()
                .user(user).facility(facility).facilityName("헤이도그 애견카페")
                .sido("강원특별자치도").sigungu("강릉시").sidoCode("51").sigunguCode("150")
                .isVerifiedOnSite(false).stampedAt(LocalDateTime.now())
                .build();
        when(stampRepository.findByUser_IdAndFacility_FacilityId(1L, 7L)).thenReturn(Optional.of(existing));

        StampResponseDTO.StampResult result =
                stampPersistenceService.persist(user, facility, List.of(), null, true, LocalDateTime.now());

        assertThat(result.isVerifiedOnSite()).isTrue();
    }

    @Test
    void 이미_현장확인된_도장은_false가_들어와도_강등되지_않는다() {
        setUpService();
        User user = user();
        Facility facility = facility(7L, "51", "150");
        Stamp existing = Stamp.builder()
                .user(user).facility(facility).facilityName("헤이도그 애견카페")
                .sido("강원특별자치도").sigungu("강릉시").sidoCode("51").sigunguCode("150")
                .isVerifiedOnSite(true).stampedAt(LocalDateTime.now())
                .build();
        when(stampRepository.findByUser_IdAndFacility_FacilityId(1L, 7L)).thenReturn(Optional.of(existing));

        StampResponseDTO.StampResult result =
                stampPersistenceService.persist(user, facility, List.of(), null, false, LocalDateTime.now());

        assertThat(result.isVerifiedOnSite()).isTrue();
    }

}
