package com.freepets.domain.pet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.facility.entity.FacilityCategory;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Gender;
import com.freepets.domain.pet.entity.Kind;

public class PetResponseDTO {

    private PetResponseDTO() {}

    public record PetDetail(
            Long petId,
            String name,
            Kind kind,
            String species,
            Gender gender,
            LocalDate birthDate,
            BigDecimal weight,
            BreedSize breedSize,
            String profile,
            LocalDate vaccinationDate,
            LocalDate nextVaccinationDate,

            // record의 접근자도 isVaccinated()라 Jackson이 vaccinated로 줄이는 것을 막는다
            @JsonProperty("isVaccinated")
            boolean isVaccinated,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    // GET /api/v1/pets/{petId}/card — "반려동물 등록증" 카드 전용 응답. PetDetail(수정 화면용)과
    // 분리한 이유는 이 응답이 게이미피케이션(레벨)·만족도(최애 장소) 등 다른 도메인 데이터까지
    // 합쳐서 내려주기 때문 — PetDetail을 그대로 쓰면 수정 화면 호출마다 안 쓰는 조회가 같이 돈다.
    // xpToNextLevel은 GamificationResponseDTO.MyStatus와 같은 규칙 — 최대 레벨 도달 시, age도
    // birthDate가 없을 때 키 자체가 없다(null 체크가 아니라 필드 존재 여부로 확인) —
    // @JsonInclude(NON_NULL)이 그걸 보장한다.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegistrationCard(
            Long petId,
            String name,
            Gender gender,
            String species,
            LocalDate birthDate,

            // birthDate로부터 계산한 만 나이(생일이 아직 안 지났으면 그만큼 하나 적게). birthDate가
            // 없으면(선택 입력이라 안 보낸 경우) 같이 null이라 응답에서 키 자체가 빠진다.
            Integer age,
            LocalDateTime issuedAt,
            int level,
            long totalXp,
            Long xpToNextLevel,
            List<FavoriteFacility> favoriteFacilities
    ) {}

    // RegistrationCard 전용 "최애 장소" 항목. petsatisfaction 도메인의
    // PetSatisfactionResponseDTO.TopFacility와 필드 구성은 같지만, pet 도메인의 응답 계약을
    // 다른 도메인의 DTO 타입에 묶어두지 않으려고 따로 둔다 — PetConverter가 변환한다.
    public record FavoriteFacility(
            Long facilityId,
            String facilityName,
            FacilityCategory category,
            float score
    ) {}

    public record PetList(
            List<PetDetail> pets
    ) {}

    public record CreateResult(
            Long petId
    ) {}

    public record DeleteResult(
            Long petId
    ) {}
}
