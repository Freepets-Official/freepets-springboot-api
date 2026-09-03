package com.freepets.domain.course.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.facility.event.FacilityBecameIneligibleEvent;

@ExtendWith(MockitoExtension.class)
class CoursePresetCacheInvalidationListenerTest {

    @Mock
    private CoursePresetService coursePresetService;

    @InjectMocks
    private CoursePresetCacheInvalidationListener listener;

    @Test
    void 이벤트를_받으면_해당_시설을_포함한_프리셋_캐시_무효화를_위임한다() {
        listener.onFacilityBecameIneligible(new FacilityBecameIneligibleEvent(42L));

        verify(coursePresetService).invalidateCoursesContaining(42L);
    }

}
