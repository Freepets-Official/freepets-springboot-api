package com.freepets.domain.facility.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class FacilityTest {

    private Facility createFacility() {
        return Facility.builder()
                .contentId("12345")
                .name("카페 파도살롱")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.PENDING)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
    }

    /** 관광공사에서 다시 내려받은 값. 동반 조건이 우리가 확정한 것과 다르게 온 상황이다. */
    private Facility fetchedFacility() {
        return Facility.builder()
                .contentId("12345")
                .name("카페 파도살롱(관광공사 갱신 이름)")
                .category(FacilityCategory.CAFE)
                .address("강원 강릉시 창해로 17")
                .lat(new BigDecimal("37.8000000"))
                .lng(new BigDecimal("128.9000000"))
                .petAllowed(PetAllowed.DENIED)
                .maxWeight(new BigDecimal("5.00"))
                .maxWeightInclusive(false)
                .source(FacilitySource.TOUR_API)
                .isActive(true)
                .petTourListed(true)
                .build();
    }

    private void confirm(Facility facility) {
        facility.confirmByOwner(
                PetAllowed.ALLOWED,
                new BigDecimal("10.00"),
                true,
                List.of(Requirement.LEASH),
                "리드줄 착용 시 실내 동반 가능"
        );
    }

    @Test
    void confirmByOwner_조건과_확정_시각을_반영한다() {
        Facility facility = createFacility();

        confirm(facility);

        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(facility.getMaxWeight()).isEqualByComparingTo("10.00");
        assertThat(facility.getMaxWeightInclusive()).isTrue();
        assertThat(facility.getPetConditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        assertThat(facility.getConfirmedAt()).isNotNull();
        assertThat(facility.getCheckLists()).hasSize(1);
        assertThat(facility.getCheckLists().get(0).getType()).isEqualTo(Requirement.LEASH);
    }

    @Test
    void confirmByOwner_최대_체중이_없으면_경계_종류도_비운다() {
        Facility facility = createFacility();

        facility.confirmByOwner(PetAllowed.ALLOWED, null, true, List.of(), "동반 가능");

        assertThat(facility.getMaxWeight()).isNull();
        // 상한이 없는데 "이하"만 남으면 판별이 읽을 때 의미가 없다.
        assertThat(facility.getMaxWeightInclusive()).isNull();
    }

    @Test
    void updateFromTourApi_확정된_시설은_동반_조건을_덮어쓰지_않는다() {
        // 이 보호가 없으면 다음 동기화가 사장님이 직접 확정한 값을 지운다.
        Facility facility = createFacility();
        confirm(facility);

        facility.updateFromTourApi(fetchedFacility());

        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(facility.getMaxWeight()).isEqualByComparingTo("10.00");
        assertThat(facility.getMaxWeightInclusive()).isTrue();
        assertThat(facility.getPetConditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        // 조건이 아닌 기본 정보는 그대로 갱신된다.
        assertThat(facility.getName()).isEqualTo("카페 파도살롱(관광공사 갱신 이름)");
    }

    @Test
    void updateFromTourApi_확정한_적이_없으면_동반_조건도_갱신한다() {
        Facility facility = createFacility();

        facility.updateFromTourApi(fetchedFacility());

        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.DENIED);
        assertThat(facility.getMaxWeight()).isEqualByComparingTo("5.00");
        assertThat(facility.getMaxWeightInclusive()).isFalse();
    }

    @Test
    void confirmByOwner_같은_값으로_다시_확정하면_확정_시각을_유지한다() throws InterruptedException {
        // 같은 값으로 PUT만 반복해도 거부 제보로 인한 신뢰도 하향이 계속 풀리면 안 된다.
        Facility facility = createFacility();
        confirm(facility);
        LocalDateTime firstConfirmedAt = facility.getConfirmedAt();
        Thread.sleep(5);

        confirm(facility);

        assertThat(facility.getConfirmedAt()).isEqualTo(firstConfirmedAt);
    }

    @Test
    void confirmByOwner_판별값이_바뀌면_확정_시각을_갱신한다() throws InterruptedException {
        Facility facility = createFacility();
        confirm(facility);
        LocalDateTime firstConfirmedAt = facility.getConfirmedAt();
        Thread.sleep(5);

        facility.confirmByOwner(
                PetAllowed.ALLOWED,
                new BigDecimal("12.00"),
                true,
                List.of(Requirement.LEASH),
                "리드줄 착용 시 실내 동반 가능"
        );

        assertThat(facility.getConfirmedAt()).isAfter(firstConfirmedAt);
    }

    @Test
    void confirmByOwner_안내문만_바뀌면_확정_시각을_유지한다() throws InterruptedException {
        // conditionRaw는 화면 안내문일 뿐 판별에 쓰이지 않는다 — 값은 저장하되 기준선은 유지한다.
        Facility facility = createFacility();
        confirm(facility);
        LocalDateTime firstConfirmedAt = facility.getConfirmedAt();
        Thread.sleep(5);

        facility.confirmByOwner(
                PetAllowed.ALLOWED,
                new BigDecimal("10.00"),
                true,
                List.of(Requirement.LEASH),
                "안내문만 바꿔봄"
        );

        assertThat(facility.getConfirmedAt()).isEqualTo(firstConfirmedAt);
        assertThat(facility.getPetConditionRaw()).isEqualTo("안내문만 바꿔봄");
    }

    @Test
    void confirmByOwner_필수_준비물_순서만_바뀌면_확정_시각을_유지한다() throws InterruptedException {
        Facility facility = createFacility();
        facility.confirmByOwner(
                PetAllowed.ALLOWED,
                new BigDecimal("10.00"),
                true,
                List.of(Requirement.LEASH, Requirement.CAGE),
                "동반 가능"
        );
        LocalDateTime firstConfirmedAt = facility.getConfirmedAt();
        Thread.sleep(5);

        facility.confirmByOwner(
                PetAllowed.ALLOWED,
                new BigDecimal("10.00"),
                true,
                List.of(Requirement.CAGE, Requirement.LEASH),
                "동반 가능"
        );

        assertThat(facility.getConfirmedAt()).isEqualTo(firstConfirmedAt);
        assertThat(facility.getCheckLists()).hasSize(2);
    }

    @Test
    void confirmByOwner_요청에_중복된_준비물이_있어도_체크리스트가_중복되지_않는다() {
        Facility facility = createFacility();

        facility.confirmByOwner(
                PetAllowed.ALLOWED,
                new BigDecimal("10.00"),
                true,
                List.of(Requirement.LEASH, Requirement.LEASH),
                "동반 가능"
        );

        assertThat(facility.getCheckLists()).hasSize(1);
    }

    @Test
    void confirmByOwner_최대_체중_스케일만_다르면_확정_시각을_유지한다() throws InterruptedException {
        Facility facility = createFacility();
        confirm(facility);
        LocalDateTime firstConfirmedAt = facility.getConfirmedAt();
        Thread.sleep(5);

        facility.confirmByOwner(
                PetAllowed.ALLOWED,
                new BigDecimal("10.0"),
                true,
                List.of(Requirement.LEASH),
                "리드줄 착용 시 실내 동반 가능"
        );

        assertThat(facility.getConfirmedAt()).isEqualTo(firstConfirmedAt);
    }

    @Test
    void releaseOwnerConfirmation_확정_시각만_비우고_조건은_그대로_둔다() {
        // 다음 관광공사 동기화가 confirmedAt이 없는 시설의 조건을 원래대로 되돌린다 — 여기서 조건까지
        // 미리 지우면 그 사이(동기화 전까지) 시설에 아무 조건도 없는 상태가 된다.
        Facility facility = createFacility();
        confirm(facility);

        facility.releaseOwnerConfirmation();

        assertThat(facility.getConfirmedAt()).isNull();
        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
        assertThat(facility.getMaxWeight()).isEqualByComparingTo("10.00");
        assertThat(facility.getPetConditionRaw()).isEqualTo("리드줄 착용 시 실내 동반 가능");
        assertThat(facility.getCheckLists()).hasSize(1);
    }

    @Test
    void updateProfile_소개글과_편의시설_태그를_반영한다() {
        Facility facility = createFacility();

        facility.updateProfile(
                "대형견도 환영해요. 야외 테라스와 급수대, 펫 메뉴가 준비돼 있어요.",
                List.of(FacilityAmenity.WATER_BOWL, FacilityAmenity.OUTDOOR_TERRACE)
        );

        assertThat(facility.getIntroduction())
                .isEqualTo("대형견도 환영해요. 야외 테라스와 급수대, 펫 메뉴가 준비돼 있어요.");
        assertThat(facility.getAmenityTags())
                .containsExactly(FacilityAmenity.WATER_BOWL, FacilityAmenity.OUTDOOR_TERRACE);
    }

    @Test
    void updateProfile_빈_태그_목록으로_저장하면_전부_해제된다() {
        Facility facility = createFacility();
        facility.updateProfile("소개글", List.of(FacilityAmenity.PARKING));

        facility.updateProfile("소개글", List.of());

        assertThat(facility.getAmenityTags()).isEmpty();
    }

    @Test
    void updateProfile_요청에_중복된_태그가_있어도_저장되는_태그는_중복되지_않는다() {
        Facility facility = createFacility();

        facility.updateProfile(
                "소개글",
                List.of(FacilityAmenity.PARKING, FacilityAmenity.PARKING, FacilityAmenity.WATER_BOWL)
        );

        assertThat(facility.getAmenityTags())
                .containsExactly(FacilityAmenity.PARKING, FacilityAmenity.WATER_BOWL);
    }

    @Test
    void updateProfile_태그_목록에_null이_섞여도_예외없이_걸러진다() {
        Facility facility = createFacility();

        facility.updateProfile("소개글", Arrays.asList(FacilityAmenity.PARKING, null));

        assertThat(facility.getAmenityTags()).containsExactly(FacilityAmenity.PARKING);
    }

    @Test
    void updateProfile_판별값과_확정_시각은_건드리지_않는다() {
        Facility facility = createFacility();
        confirm(facility);
        LocalDateTime confirmedAt = facility.getConfirmedAt();

        facility.updateProfile("소개글", List.of(FacilityAmenity.PARKING));

        assertThat(facility.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(facility.getPetAllowed()).isEqualTo(PetAllowed.ALLOWED);
    }
}
