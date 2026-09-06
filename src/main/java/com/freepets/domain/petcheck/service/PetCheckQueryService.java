package com.freepets.domain.petcheck.service;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.petcheck.converter.PetCheckConverter;
import com.freepets.domain.petcheck.dto.PetCheckResponseDTO;
import com.freepets.domain.petcheck.entity.PetCheck;
import com.freepets.domain.petcheck.entity.PetCheckVerdict;
import com.freepets.domain.petcheck.repository.PetCheckRepository;
import com.freepets.domain.petcheck.repository.PetCheckVerdictRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PetCheckQueryService {

    // FacilityRequestDTO의 페이지 크기 상한(100)과 맞춘다 — 상한이 없으면 limit을 크게 보내는
    // 요청 하나로 DB가 한 번에 큰 결과 집합을 만들게 할 수 있다.
    private static final int MAX_LIMIT = 100;

    private final PetCheckRepository petCheckRepository;
    private final PetCheckVerdictRepository petCheckVerdictRepository;

    // GET /api/v1/pet-checks — 내 판별 이력(최신순). facilityId로 필터 가능.
    // offset은 limit의 배수로 들어온다고 가정(프론트 "더보기" 방식) — Pageable의 page 개념으로 환산.
    public PetCheckResponseDTO.CheckHistoryList getMyChecks(
            Long userId,
            Long facilityId,
            int limit,
            int offset
    ) {
        if (limit <= 0) {
            throw new GeneralException(ErrorStatus.COMMON400, Map.of("limit", "1 이상이어야 합니다."));
        }
        if (limit > MAX_LIMIT) {
            throw new GeneralException(ErrorStatus.COMMON400, Map.of("limit", MAX_LIMIT + " 이하여야 합니다."));
        }
        if (offset < 0) {
            throw new GeneralException(ErrorStatus.COMMON400, Map.of("offset", "0 이상이어야 합니다."));
        }
        // offset이 limit의 배수라는 가정(위 주석)이 깨지면 PageRequest.of가 잘못된 페이지로
        // 조용히 내림 계산해버려서 호출자가 알아챌 방법이 없다 — 가정이 어긋났다는 걸 명시적으로 알린다.
        if (offset % limit != 0) {
            throw new GeneralException(ErrorStatus.COMMON400, Map.of("offset", "limit의 배수여야 합니다."));
        }

        PageRequest pageRequest = PageRequest.of(offset / limit, limit);

        Page<PetCheck> page = (facilityId != null)
                ? petCheckRepository.findAllByUser_IdAndFacility_FacilityIdOrderByCreatedAtDesc(userId, facilityId, pageRequest)
                : petCheckRepository.findAllByUser_IdOrderByCreatedAtDesc(userId, pageRequest);

        return new PetCheckResponseDTO.CheckHistoryList(
                page.getContent().stream().map(PetCheckConverter::toHistoryItem).toList(),
                page.getTotalElements()
        );
    }

    // GET /verify/{code} — 동반 출입증 QR이 가리키는 공개 페이지가 조회한다. 인증 없음.
    public PetCheckResponseDTO.VerifyPage getVerifyPage(String verifyCode) {
        PetCheckVerdict verdict = petCheckVerdictRepository.findByVerifyCode(verifyCode)
                .orElseThrow(() -> new GeneralException(ErrorStatus.PETCHECK4001));

        return PetCheckConverter.toVerifyPage(verdict);
    }
}
