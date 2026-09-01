package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class CourseQueryServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseQueryService courseQueryService;

    @Test
    void 내_코스_목록을_스톱_순서대로_반환한다() {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Facility a = facility(1L, "A");
        Facility b = facility(2L, "B");
        Course course = Course.builder()
                .user(user)
                .name("몽이 코스")
                .source(CourseSource.CUSTOM)
                .build();
        course.replaceStops(List.of(a, b));
        ReflectionTestUtils.setField(course, "courseId", 10L);

        when(courseRepository.findAllByUser_Id(1L)).thenReturn(List.of(course));

        List<CourseResponseDTO.MyCourse> result = courseQueryService.getMyCourses(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stopIds()).containsExactly(1L, 2L);
    }

    private Facility facility(
            Long facilityId,
            String name
    ) {
        Facility facility = Facility.builder()
                .name(name)
                .category(FacilityCategory.CAFE)
                .lat(new BigDecimal("37.0"))
                .lng(new BigDecimal("128.0"))
                .build();
        ReflectionTestUtils.setField(facility, "facilityId", facilityId);
        return facility;
    }

}
