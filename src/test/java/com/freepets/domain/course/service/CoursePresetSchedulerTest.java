package com.freepets.domain.course.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// @ConditionalOnProperty/@Scheduled는 스프링 컨텍스트 관련 설정이라 여기서 검증하지 않는다
// (FacilitySyncSchedulerTest와 같은 이유). recalculateAll() 호출을 그대로 위임하는지만 본다.
@ExtendWith(MockitoExtension.class)
class CoursePresetSchedulerTest {

    @Mock
    private CoursePresetService coursePresetService;

    @InjectMocks
    private CoursePresetScheduler coursePresetScheduler;

    @Test
    void 재계산을_위임한다() {
        coursePresetScheduler.recalculateAll();

        verify(coursePresetService).recalculateAll();
    }

}
