package com.freepets.domain.stamp.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.stamp.converter.StampConverter;
import com.freepets.domain.stamp.dto.StampResponseDTO;
import com.freepets.domain.stamp.entity.Stamp;
import com.freepets.domain.stamp.repository.StampRepository;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.BusinessZone;

import lombok.RequiredArgsConstructor;

// GET /api/v1/me/stamps — 내 도장첩 + 요약. 게이미피케이션 progress[]의 STAMP·REGION 패밀리
// 진행도(GamificationQueryService.countByFamily)도 이 서비스의 집계 메서드를 그대로 가져다
// 쓴다 — "도장 수"·"지역 수" 계산이 두 곳에 따로 있으면 도장첩 요약과 배지 진행도가 어긋날 수 있다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StampQueryService {

    private final UserRepository userRepository;
    private final StampRepository stampRepository;

    public StampResponseDTO.MyStamps getMyStamps(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new GeneralException(ErrorStatus.MEMBER4005);
        }

        List<Stamp> stamps = stampRepository.findAllByUser_IdOrderByStampedAtDesc(userId);
        StampResponseDTO.Summary summary = new StampResponseDTO.Summary(
                stamps.size(),
                getDistinctRegionCount(userId),
                stampRepository.countByUser_IdAndVerifiedOnSiteTrue(userId),
                stampRepository.countByUser_IdAndStampedAtGreaterThanEqual(userId, startOfMonthInBusinessZone())
        );

        return StampConverter.toMyStamps(stamps, summary);
    }

    public long getTotalStampCount(Long userId) {
        return stampRepository.countByUser_Id(userId);
    }

    public long getDistinctRegionCount(Long userId) {
        return stampRepository.findDistinctRegionsByUser_Id(userId).size();
    }

    /**
     * "이번 달(KST)" 경계 — {@code GamificationService.startOfTodayInBusinessZone}과 같은 이유로
     * KST 기준이어야 하지만, 그건 "오늘"만 계산해서 그대로 재사용할 수 없다. createdAt과 같은
     * 좌표(naive UTC LocalDateTime)로 맞추기 위해 KST 자정을 구한 뒤 UTC로 환산한다.
     */
    private static LocalDateTime startOfMonthInBusinessZone() {
        return LocalDate.now(BusinessZone.ZONE)
                .withDayOfMonth(1)
                .atStartOfDay(BusinessZone.ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

}
