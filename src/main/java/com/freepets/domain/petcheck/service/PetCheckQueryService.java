package com.freepets.domain.petcheck.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.petcheck.converter.PetCheckConverter;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.repository.PetCheckRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetCheckQueryService {

    private final PetCheckRepository petCheckRepository;

    // GET /api/v1/pet-checks — 내 판별 이력(최신순). facilityId로 필터 가능.
    // offset은 limit의 배수로 들어온다고 가정(프론트 "더보기" 방식) — Pageable의 page 개념으로 환산.
    public PetCheckResponseDTO.CheckHistoryList getMyChecks(
            Long userId,
            Long facilityId,
            int limit,
            int offset
    ) {
        PageRequest pageRequest = PageRequest.of(offset / limit, limit);

        Page<PetCheck> page = (facilityId != null)
                ? petCheckRepository.findAllByUser_IdAndFacility_FacilityIdOrderByCreatedAtDesc(userId, facilityId, pageRequest)
                : petCheckRepository.findAllByUser_IdOrderByCreatedAtDesc(userId, pageRequest);

        return new PetCheckResponseDTO.CheckHistoryList(
                page.getContent().stream().map(PetCheckConverter::toHistoryItem).toList(),
                page.getTotalElements()
        );
    }
}
