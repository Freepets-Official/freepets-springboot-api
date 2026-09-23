package com.freepets.domain.stamp.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.freepets.domain.facility.entity.Facility;
import com.freepets.domain.facility.repository.FacilityRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.stamp.dto.StampRequestDTO;
import com.freepets.domain.stamp.dto.StampResponseDTO;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;
import com.freepets.global.util.GeoUtils;
import com.freepets.infra.s3.S3ImageService;

import lombok.RequiredArgsConstructor;

/**
 * POST /api/v1/me/stamps — 여권 도장 찍기(freepets-docs PR #48, {@code docs/14-도장-서버-저장.md}).
 * 이 클래스는 트랜잭션을 걸지 않는다 — S3 사진 업로드(외부 네트워크 I/O)가 여기서 일어나는데,
 * 클래스 전체에 트랜잭션이 걸려 있으면 그 호출이 끝날 때까지 DB 커넥션을 쥐고 있게 된다
 * ({@code FacilityListAssembler}가 관광공사 호출을 트랜잭션 밖으로 뺀 것과 같은 이유). 실제
 * DB 쓰기(도장 저장/승격, 지역 완성, 배지 평가)는 전부 {@link StampPersistenceService}가
 * 맡는다.
 */
@Service
@RequiredArgsConstructor
public class StampCommandService {

    /**
     * 현장 확인(isVerifiedOnSite) 판정 반경. 좌표는 이 판정에만 쓰고 저장하지 않는다 — 저장하면
     * 개인위치정보 수집이 되어 위치정보법 적용 범위가 넓어진다(PR #48 권장사항). 앱이 로컬에서
     * 쓰던 구체적인 반경 값이 공유되면 그에 맞춰 조정한다.
     */
    private static final double ON_SITE_RADIUS_METERS = 200;

    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final PetRepository petRepository;
    private final S3ImageService s3ImageService;
    private final StampPersistenceService stampPersistenceService;

    public StampResponseDTO.StampResult createStamp(
            Long userId,
            StampRequestDTO.CreateRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.FACILITY4001));
        List<Pet> pets = findOwnedPets(userId, request.getPetIds());

        // createdAt이 왔다는 것 자체가 "기기 이전" 신호다 — 그 시점엔 위치를 다시 잴 수 없으므로
        // 좌표 재판정 대신 앱이 로컬에서 이미 판정해둔 isVerifiedOnSite를 그대로 믿는다.
        boolean isMigration = request.getCreatedAt() != null;
        boolean isVerifiedOnSite = isMigration
                ? request.isVerifiedOnSite()
                : resolveVerifiedOnSite(facility, request.getLat(), request.getLng());
        LocalDateTime stampedAt = isMigration ? request.getCreatedAt() : LocalDateTime.now();

        // 여기까지는 DB 읽기만 있고 트랜잭션을 걸지 않는다 — S3 업로드가 끝난 뒤에야 실제 저장
        // (StampPersistenceService, 별도 트랜잭션)을 시작한다.
        String photoUrl = uploadPhotoIfPresent(request.getPhoto());

        return stampPersistenceService.persist(user, facility, pets, photoUrl, isVerifiedOnSite, stampedAt);
    }

    private boolean resolveVerifiedOnSite(
            Facility facility,
            Double lat,
            Double lng
    ) {
        if (lat == null || lng == null || facility.getLat() == null || facility.getLng() == null) {
            return false;
        }

        double distanceMeters = GeoUtils.distanceMeters(facility.getLat().doubleValue(), facility.getLng().doubleValue(), lat, lng);
        return distanceMeters <= ON_SITE_RADIUS_METERS;
    }

    // CourseCheckService.findOwnedPets와 같은 패턴 — petIds 중복 제거 후 소유 검증.
    private List<Pet> findOwnedPets(
            Long userId,
            List<Long> petIds
    ) {
        if (petIds == null || petIds.isEmpty()) {
            return List.of();
        }

        List<Long> distinctPetIds = petIds.stream().distinct().toList();
        List<Pet> pets = petRepository.findAllByPetIdInAndDeletedAtIsNull(distinctPetIds);

        if (pets.size() != distinctPetIds.size()) {
            throw new GeneralException(ErrorStatus.PET4001);
        }

        boolean isAllOwnedByUser = pets.stream().allMatch(pet -> pet.isOwnedBy(userId));
        if (!isAllOwnedByUser) {
            throw new GeneralException(ErrorStatus.PET4002);
        }

        return pets;
    }

    // ReviewCommandService.isNewPhotoPresent/uploadPhotoIfPresent와 같은 패턴.
    private boolean isNewPhotoPresent(MultipartFile photo) {
        return photo != null && !photo.isEmpty();
    }

    private String uploadPhotoIfPresent(MultipartFile photo) {
        if (!isNewPhotoPresent(photo)) {
            return null;
        }
        return s3ImageService.upload(photo);
    }

}
