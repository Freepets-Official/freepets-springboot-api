package com.freepets.domain.stamp.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.gamification.service.GamificationService;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.stamp.converter.StampConverter;
import com.freepets.domain.stamp.dto.StampResponseDTO;
import com.freepets.domain.stamp.entity.RegionCompletion;
import com.freepets.domain.stamp.entity.Stamp;
import com.freepets.domain.stamp.repository.StampRepository;
import com.freepets.domain.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * {@code StampCommandService}에서 DB 쓰기만 떼어낸 부분. S3 사진 업로드(외부 네트워크 I/O)는
 * 이 서비스를 부르기 전에 이미 끝나 있어야 한다 — 관광공사 호출을 트랜잭션 밖으로 뺀
 * {@code FacilityListAssembler}와 같은 이유(같은 클래스 안에서 부르면 self-invocation이라
 * 프록시를 안 타 트랜잭션 애너테이션만으로는 경계가 생기지 않는다)로 별도 빈으로 분리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StampPersistenceService {

    private final StampRepository stampRepository;
    private final RegionCompletionService regionCompletionService;
    private final GamificationService gamificationService;

    public StampResponseDTO.StampResult persist(
            User user,
            Facility facility,
            List<Pet> pets,
            String photoUrl,
            boolean isVerifiedOnSite,
            LocalDateTime stampedAt
    ) {
        Optional<Stamp> existingStamp =
                stampRepository.findByUser_IdAndFacility_FacilityId(user.getId(), facility.getFacilityId());
        boolean isNew = existingStamp.isEmpty();

        // 저장 "전"에 확인해야 한다 — 저장 후에 확인하면 방금 넣은 도장 자신이 걸려 항상
        // true가 나온다.
        boolean isRegionCompleted = isNew
                && !stampRepository.existsByUser_IdAndSidoCodeAndSigunguCode(user.getId(), facility.getSidoCode(), facility.getSigunguCode());

        Stamp stamp = isNew
                ? createNewStamp(user, facility, pets, photoUrl, isVerifiedOnSite, stampedAt)
                : promoteIfNeeded(existingStamp.get(), isVerifiedOnSite);

        RegionCompletion regionCompletion = isRegionCompleted
                ? regionCompletionService.completeRegion(user, facility.getSidoCode(), facility.getSigunguCode())
                : null;

        gamificationService.evaluateStampBadge(user, stampRepository.countByUser_Id(user.getId()));
        gamificationService.evaluateRegionBadge(user, stampRepository.findDistinctRegionsByUser_Id(user.getId()).size());

        return StampConverter.toStampResult(stamp, isNew, isRegionCompleted, regionCompletion);
    }

    private Stamp createNewStamp(
            User user,
            Facility facility,
            List<Pet> pets,
            String photoUrl,
            boolean isVerifiedOnSite,
            LocalDateTime stampedAt
    ) {
        Stamp stamp = stampRepository.save(Stamp.builder()
                .user(user)
                .facility(facility)
                .facilityName(facility.getName())
                .sido(facility.getSido())
                .sigungu(facility.getSigungu())
                .sidoCode(facility.getSidoCode())
                .sigunguCode(facility.getSigunguCode())
                .photoUrl(photoUrl)
                .isVerifiedOnSite(isVerifiedOnSite)
                .stampedAt(stampedAt)
                .build());
        stamp.replacePets(pets);
        return stamp;
    }

    private Stamp promoteIfNeeded(
            Stamp stamp,
            boolean isVerifiedOnSite
    ) {
        if (isVerifiedOnSite && !stamp.isVerifiedOnSite()) {
            stamp.promoteToOnSite();
        }
        return stamp;
    }

}
