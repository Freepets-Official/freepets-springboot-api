package com.freepets.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private CourseAssemblyService courseAssemblyService;

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
    void 공개_여부를_담아_생성하면_isPublic이_그대로_저장된다() {
        User user = user(1L);
        Facility a = facility(1L, "A");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(facilityRepository.findAllById(List.of(1L))).thenReturn(List.of(a));
        when(courseRepository.save(org.mockito.ArgumentMatchers.any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseRequestDTO.SaveRequest request = request("강릉 코스", List.of(1L));
        request.setIsPublic(true);

        CourseResponseDTO.MyCourse result = courseCommandService.createCourse(1L, request);

        assertThat(result.isPublic()).isTrue();
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

    @Test
    void 경로_최적화는_저장_없이_재정렬된_stopIds만_반환한다() {
        Facility a = facility(1L, "A");
        Facility b = facility(2L, "B");
        when(facilityRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(a, b));
        when(courseAssemblyService.reorderForCustomEdit(List.of(a, b))).thenReturn(List.of(b, a));

        CourseResponseDTO.OrderResult result = courseCommandService.optimizeOrder(List.of(1L, 2L));

        assertThat(result.stopIds()).containsExactly(2L, 1L);
        org.mockito.Mockito.verifyNoInteractions(courseRepository);
    }

    @Test
    void 경로_최적화_시_존재하지_않는_시설이_있으면_FACILITY4001() {
        when(facilityRepository.findAllById(List.of(1L, 999L))).thenReturn(List.of(facility(1L, "A")));

        assertThatThrownBy(() -> courseCommandService.optimizeOrder(List.of(1L, 999L)))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 스톱_교체는_그_자리만_바꾸고_나머지_순서는_그대로_유지한다() {
        // 1·2·3·4·5(순서 0~4)에서 순서 3(네 번째, 시설 4)만 시설 6으로 바꾸면 1·2·3·6·5가 되어야 한다.
        Course course = ownedCourseWithStops(1L, 2L, 3L, 4L, 5L);
        Facility six = facility(6L, "F");
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(facilityRepository.findById(6L)).thenReturn(Optional.of(six));

        CourseResponseDTO.MyCourse result = courseCommandService.replaceStop(1L, 10L, 3, 6L);

        assertThat(result.stopIds()).containsExactly(1L, 2L, 3L, 6L, 5L);
    }

    @Test
    void 스톱_교체_시_범위를_벗어난_순서면_COURSE4043() {
        Course course = ownedCourseWithStops(1L, 2L, 3L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseCommandService.replaceStop(1L, 10L, 3, 6L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 스톱_교체_시_존재하지_않는_시설이면_FACILITY4001() {
        Course course = ownedCourseWithStops(1L, 2L, 3L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(facilityRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseCommandService.replaceStop(1L, 10L, 1, 999L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 스톱_교체_시_본인_코스가_아니면_COURSE4042() {
        Course course = ownedCourseWithStops(1L, 2L, 3L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseCommandService.replaceStop(2L, 10L, 1, 6L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 공유_코드가_없으면_새로_발급된다() {
        Course course = ownedCourseWithStops(1L, 2L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        CourseResponseDTO.ShareResult result = courseCommandService.shareCourse(1L, 10L);

        assertThat(result.courseId()).isEqualTo(10L);
        assertThat(result.shareCode()).matches("CRS-[A-Z0-9]{10}");
        assertThat(course.getShareCode()).isEqualTo(result.shareCode());
    }

    @Test
    void 이미_발급된_코스는_같은_코드를_그대로_반환한다() {
        Course course = ownedCourseWithStops(1L, 2L);
        ReflectionTestUtils.setField(course, "shareCode", "CRS-EXISTING01");
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        CourseResponseDTO.ShareResult result = courseCommandService.shareCourse(1L, 10L);

        assertThat(result.shareCode()).isEqualTo("CRS-EXISTING01");
    }

    @Test
    void 본인_코스가_아니면_공유_코드_발급시_COURSE4042() {
        Course course = ownedCourseWithStops(1L, 2L);
        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseCommandService.shareCourse(2L, 10L))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 공유_코드로_복사하면_스톱까지_그대로_담긴_새_코스가_생성된다() {
        Course original = ownedCourseWithStops(1L, 2L);
        ReflectionTestUtils.setField(original, "shareCode", "CRS-SHARE0001");
        User receiver = user(2L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(courseRepository.findByShareCode("CRS-SHARE0001")).thenReturn(Optional.of(original));
        when(courseRepository.save(org.mockito.ArgumentMatchers.any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseResponseDTO.MyCourse result = courseCommandService.copySharedCourse(2L, "CRS-SHARE0001");

        assertThat(result.name()).isEqualTo(original.getName());
        assertThat(result.stopIds()).containsExactly(1L, 2L);
        assertThat(result.isPublic()).isFalse();

        // MyCourse 응답엔 소유자 필드가 없어 위 검증만으론 저장된 엔티티의 소유자가 실제로
        // 받는 사람인지 확인이 안 된다 — 저장 직전 엔티티를 잡아 직접 확인한다.
        ArgumentCaptor<Course> savedCourse = ArgumentCaptor.forClass(Course.class);
        org.mockito.Mockito.verify(courseRepository).save(savedCourse.capture());
        assertThat(savedCourse.getValue().isOwnedBy(2L)).isTrue();
        assertThat(savedCourse.getValue()).isNotSameAs(original);
    }

    @Test
    void 원본_소유자가_아니어도_공유_코드만_있으면_복사에_성공한다() {
        // 원본은 user(1L) 소유, 복사 요청자는 user(2L) — 소유자 확인 없이도 성공해야 한다
        // (코드 자체가 공유 권한이므로).
        Course original = ownedCourseWithStops(1L);
        ReflectionTestUtils.setField(original, "shareCode", "CRS-SHARE0002");

        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(courseRepository.findByShareCode("CRS-SHARE0002")).thenReturn(Optional.of(original));
        when(courseRepository.save(org.mockito.ArgumentMatchers.any(Course.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CourseResponseDTO.MyCourse result = courseCommandService.copySharedCourse(2L, "CRS-SHARE0002");

        assertThat(result.stopIds()).containsExactly(1L);
    }

    @Test
    void 존재하지_않는_공유_코드로_복사하면_COURSE4044() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L)));
        when(courseRepository.findByShareCode("CRS-NOTFOUND1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseCommandService.copySharedCourse(2L, "CRS-NOTFOUND1"))
                .isInstanceOf(GeneralException.class);
    }

    private Course ownedCourseWithStops(Long... facilityIds) {
        Course course = Course.builder()
                .user(user(1L))
                .name("몽이 코스")
                .source(CourseSource.CUSTOM)
                .build();
        List<Facility> stops = List.of(facilityIds).stream()
                .map(id -> facility(id, "시설" + id))
                .toList();
        course.replaceStops(stops);
        ReflectionTestUtils.setField(course, "courseId", 10L);
        return course;
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
