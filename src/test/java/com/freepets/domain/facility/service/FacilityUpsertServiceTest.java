package com.freepets.domain.facility.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.facility.event.FacilityBecameIneligibleEvent;
import com.freepets.domain.facility.repository.FacilityRepository;

@ExtendWith(MockitoExtension.class)
class FacilityUpsertServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FacilityUpsertService facilityUpsertService;

    @Test
    void 활성_시설이_비활성으로_바뀌면_캐시_무효화_이벤트를_발행한다() {
        Facility existing = facility("c-1", true, PetAllowed.ALLOWED);
        ReflectionTestUtils.setField(existing, "facilityId", 10L);
        Facility fetched = facility("c-1", false, PetAllowed.ALLOWED); // 관광공사가 비표출로 내림

        when(facilityRepository.findByContentIdIn(List.of("c-1"))).thenReturn(List.of(existing));

        facilityUpsertService.upsertAll(List.of(fetched), new FacilitySyncResult());

        verify(eventPublisher).publishEvent(eq(new FacilityBecameIneligibleEvent(10L)));
    }

    @Test
    void 동반가능이_동반불가로_바뀌면_캐시_무효화_이벤트를_발행한다() {
        Facility existing = facility("c-2", true, PetAllowed.ALLOWED);
        ReflectionTestUtils.setField(existing, "facilityId", 11L);
        Facility fetched = facility("c-2", true, PetAllowed.DENIED);

        when(facilityRepository.findByContentIdIn(List.of("c-2"))).thenReturn(List.of(existing));

        facilityUpsertService.upsertAll(List.of(fetched), new FacilitySyncResult());

        verify(eventPublisher).publishEvent(eq(new FacilityBecameIneligibleEvent(11L)));
    }

    @Test
    void 계속_동반가능_상태를_유지하면_이벤트를_발행하지_않는다() {
        Facility existing = facility("c-3", true, PetAllowed.ALLOWED);
        ReflectionTestUtils.setField(existing, "facilityId", 12L);
        Facility fetched = facility("c-3", true, PetAllowed.ALLOWED); // 이름 등 사소한 값만 갱신

        when(facilityRepository.findByContentIdIn(List.of("c-3"))).thenReturn(List.of(existing));

        facilityUpsertService.upsertAll(List.of(fetched), new FacilitySyncResult());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 동반불가에서_동반가능으로_개선되면_이벤트를_발행하지_않는다() {
        // 자격을 "잃었을" 때만 발행한다 — 나아지는 방향은 캐시를 지울 이유가 없다(오히려
        // 나이틀리 재계산이 새 후보로 반영해준다).
        Facility existing = facility("c-4", true, PetAllowed.DENIED);
        ReflectionTestUtils.setField(existing, "facilityId", 13L);
        Facility fetched = facility("c-4", true, PetAllowed.ALLOWED);

        when(facilityRepository.findByContentIdIn(List.of("c-4"))).thenReturn(List.of(existing));

        facilityUpsertService.upsertAll(List.of(fetched), new FacilitySyncResult());

        verify(eventPublisher, never()).publishEvent(any());
    }

    private Facility facility(
            String contentId,
            boolean isActive,
            PetAllowed petAllowed
    ) {
        return Facility.builder()
                .contentId(contentId)
                .name("테스트시설")
                .category(FacilityCategory.CAFE)
                .petAllowed(petAllowed)
                .isActive(isActive)
                .parserVersion(1)
                .build();
    }

}
