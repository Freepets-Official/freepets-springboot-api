package com.freepets.domain.pet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.freepets.domain.pet.entity.BreedSize;
import com.freepets.domain.pet.entity.Gender;
import com.freepets.domain.pet.entity.Kind;
import com.freepets.domain.petsatisfaction.dto.PetSatisfactionResponseDTO;

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
    // xpToNextLevel은 GamificationResponseDTO.MyStatus와 같은 규칙 — 최대 레벨 도달 시 키 자체가
    // 없다(null 체크가 아니라 필드 존재 여부로 확인).
    public record RegistrationCard(
            Long petId,
            String name,
            Gender gender,
            String species,
            LocalDate birthDate,
            LocalDateTime issuedAt,
            int level,
            long totalXp,
            Long xpToNextLevel,
            List<PetSatisfactionResponseDTO.TopFacility> topFacilities
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
