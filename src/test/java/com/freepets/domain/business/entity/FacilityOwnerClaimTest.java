package com.freepets.domain.business.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.facility.entity.FacilitySource;
import com.freepets.domain.facility.entity.PetAllowed;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;

class FacilityOwnerClaimTest {

    private static final long ADMIN_USER_ID = 99L;

    private FacilityOwnerClaim createClaim() {
        User user = User.builder()
                .email("owner@test.com")
                .passwordHash("encodedPassword")
                .nickname("사장님")
                .provider(Provider.LOCAL)
                .build();
        Facility facility = Facility.builder()
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

        return FacilityOwnerClaim.builder()
                .user(user)
                .facility(facility)
                .maskedBusinessNumber("123-45-*****")
                .verifiedAt(LocalDateTime.of(2026, 9, 12, 10, 0))
                .build();
    }

    @Test
    void approve_상태를_APPROVED로_바꾸고_심사_시각과_심사자를_남긴다() {
        FacilityOwnerClaim claim = createClaim();

        claim.approve(ADMIN_USER_ID);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getReviewedAt()).isNotNull();
        assertThat(claim.getReviewedByUserId()).isEqualTo(ADMIN_USER_ID);
        // 승인에는 반려·해제 사유를 쓰지 않는다.
        assertThat(claim.getReviewReason()).isNull();
    }

    @Test
    void reject_상태를_REJECTED로_바꾸고_사유와_심사자를_남긴다() {
        FacilityOwnerClaim claim = createClaim();

        claim.reject("등록증 상호가 매장명과 다릅니다.", ADMIN_USER_ID);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getReviewReason()).isEqualTo("등록증 상호가 매장명과 다릅니다.");
        assertThat(claim.getReviewedAt()).isNotNull();
        assertThat(claim.getReviewedByUserId()).isEqualTo(ADMIN_USER_ID);
    }

    @Test
    void revoke_상태를_REVOKED로_바꾸고_사유와_심사자를_남긴다() {
        FacilityOwnerClaim claim = createClaim();
        ReflectionTestUtils.setField(claim, "status", ClaimStatus.APPROVED);

        claim.revoke("이의 제기로 소유권을 회수합니다.", ADMIN_USER_ID);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REVOKED);
        assertThat(claim.getReviewReason()).isEqualTo("이의 제기로 소유권을 회수합니다.");
        assertThat(claim.getReviewedAt()).isNotNull();
        assertThat(claim.getReviewedByUserId()).isEqualTo(ADMIN_USER_ID);
    }
}
