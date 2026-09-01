package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.course.dto.CourseRequestDTO;
import com.freepets.domain.course.dto.CourseResponseDTO;
import com.freepets.domain.course.entity.Course;
import com.freepets.domain.course.entity.CourseSource;
import com.freepets.domain.course.repository.CourseRepository;
import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class CourseCommandServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CourseCommandService courseCommandService;

    @Test
    void 코스를_생성하면_스톱_순서가_요청_순서대로_저장된다() {
        User user = user(1L);
        Facility a = facility(1L, "A");
        Facility b = facility(2L, "B");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // findAllById가 순서를 안 지켜서 뒤섞여 와도(2, 1) 요청 순서(1, 2)로 다시 맞춰져야 한다.
        when(facilityRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(b, a));
        when(courseRepository.save(org.mockito.ArgumentMatchers.any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseResponseDTO.MyCourse result = courseCommandService.createCourse(1L, request("강릉 코스", List.of(1L, 2L)));

        assertThat(result.stopIds()).containsExactly(1L, 2L);
    }

    @Test
    void 존재하지_않는_시설을_담으면_FACILITY4001() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findAllById(List.of(1L, 999L))).thenReturn(List.of(facility(1L, "A")));

        assertThatThrownBy(() -> courseCommandService.createCourse(1L, request("강릉 코스", List.of(1L, 999L))))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 본인_코스가_아니면_수정시_COURSE4042() {
        Course course = Course.builder()
                .user(user(1L))
                .name("몽이 코스")
                .source(CourseSource.CUSTOM)
                .build();
        ReflectionTestUtils.setField(course, "courseId", 10L);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseCommandService.updateCourse(2L, 10L, request("변경", List.of(1L))))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 존재하지_않는_코스_삭제시_COURSE4041() {
        when(courseRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseCommandService.deleteCourse(1L, 10L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 본인_코스는_정상_삭제된다() {
        Course course = Course.builder()
                .user(user(1L))
                .name("몽이 코스")
                .source(CourseSource.CUSTOM)
                .build();
        ReflectionTestUtils.setField(course, "courseId", 10L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        CourseResponseDTO.DeleteResult result = courseCommandService.deleteCourse(1L, 10L);

        assertThat(result.courseId()).isEqualTo(10L);
    }

    private CourseRequestDTO.SaveRequest request(
            String name,
            List<Long> stopIds
    ) {
        CourseRequestDTO.SaveRequest request = new CourseRequestDTO.SaveRequest();
        request.setName(name);
        request.setStopIds(stopIds);
        return request;
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
