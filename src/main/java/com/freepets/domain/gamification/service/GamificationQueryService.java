package com.freepets.domain.gamification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.converter.GamificationConverter;
import com.freepets.domain.gamification.dto.GamificationResponseDTO;
import com.freepets.domain.gamification.entity.UserBadge;
import com.freepets.domain.gamification.repository.UserBadgeRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

// GET /api/v1/me/gamification — 레벨·XP·티어·배지 요약 조회.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GamificationQueryService {

    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;

    public GamificationResponseDTO.MyStatus getMyStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        List<UserBadge> badges = userBadgeRepository.findAllByUser_Id(userId);

        return GamificationConverter.toMyStatus(user, badges);
    }
}
