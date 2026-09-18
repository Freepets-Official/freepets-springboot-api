package com.freepets.domain.gamification.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.IntSupplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.gamification.entity.XpEvent;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 게이미피케이션 진입점. 기존 도메인 서비스(판별·리뷰·제보·만족도·코스)가 자기 저장 로직이 끝난
 * 직후 {@link #grantXp} 한 줄만 호출하면 되고, 나머지(하루 상한·평생 1회 중복 방지·레벨 계산·
 * 레벨업 알림·배지 재평가)는 전부 여기서 처리한다.
 *
 * <p>상한 초과나 중복 지급은 예외를 던지지 않고 조용히 스킵한다 — 이 메서드를 호출한 원래 액션
 * (리뷰 작성 등)은 경험치 지급 여부와 무관하게 그대로 성공해야 하므로, 이 리포의 다른
 * idempotent 패턴(CalendarMedLogCommandService의 유니크 충돌 시 조용히 성공 처리 등)과 같은 결이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GamificationService {

    // 서버 프로세스는 항상 UTC로 고정돼 있다(FreepetsServerApplication의 static 블록) —
    // "하루"의 경계는 서버 타임존이 아니라 실제 사용자가 있는 KST 기준이어야 한다.
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final XpEventRepository xpEventRepository;
    private final PetXpEventWriter petXpEventWriter;
    private final GamificationNotificationService gamificationNotificationService;
    private final BadgeEvaluationService badgeEvaluationService;

    public void grantXp(
            Long userId,
            XpSourceType sourceType,
            Long sourceId,
            int amount
    ) {
        grantXp(userId, sourceType, sourceId, () -> amount, null, List.of());
    }

    /**
     * componentSignature가 있는 지급(코스 공개 등) 전용 — sourceId(예: courseId)는 대상을 새로
     * 만들 때마다 값이 바뀌어서 "구성요소 조합(예: 스톱 시설 집합) 기준 평생 1회"를 sourceId
     * 검사만으로는 못 지킨다. 그래서 sourceId 중복 검사에 더해 componentSignature 중복 검사를
     * 하나 더 거친다 — 둘 중 하나라도 걸리면 지급하지 않는다. null이면 이 검사 자체를 건너뛰고
     * 기존 sourceId 기준 평생 1회만 적용한다(호출부 대부분이 여기 해당).
     */
    public void grantXp(
            Long userId,
            XpSourceType sourceType,
            Long sourceId,
            int amount,
            String componentSignature
    ) {
        grantXp(userId, sourceType, sourceId, () -> amount, componentSignature, List.of());
    }

    /**
     * 이 지급과 함께 반려동물에게도 개별 경험치를 나눠주고 싶은 호출부(판별·리뷰·제보·만족도)
     * 전용 — componentSignature는 안 쓰는 호출부가 대부분이라 null로 고정한다.
     */
    public void grantXp(
            Long userId,
            XpSourceType sourceType,
            Long sourceId,
            int amount,
            List<Pet> petsToCredit
    ) {
        grantXp(userId, sourceType, sourceId, () -> amount, null, petsToCredit);
    }

    /**
     * componentSignature·반려동물 팬아웃이 둘 다 필요한 호출부(코스 공유 복사 등, 지급액이
     * 미리 정해져 있는 경우) 전용.
     */
    public void grantXp(
            Long userId,
            XpSourceType sourceType,
            Long sourceId,
            int amount,
            String componentSignature,
            List<Pet> petsToCredit
    ) {
        grantXp(userId, sourceType, sourceId, () -> amount, componentSignature, petsToCredit);
    }

    /**
     * 지급액을 User 행 잠금을 잡은 "뒤"에 계산해야 하는 호출부(코스 공개 등) 전용.
     *
     * <p>"오늘 이미 크레딧된 구성요소를 뺀 나머지로 금액을 계산"하는 경우, 그 계산을 잠금 밖에서
     * 미리 해버리면 동시에 들어온 두 요청이 같은 구성요소를 똑같이 "아직 안 쓴 것"으로 보고
     * 중복 계산할 수 있다 — 그래서 이 오버로드는 금액을 즉시 값으로 받지 않고 {@link IntSupplier}로
     * 받아, 잠금을 잡고 사전 검사(하루 상한·평생 1회)를 통과한 뒤에야 호출한다. 0 이하를 반환하면
     * "받을 자격이 없다"는 뜻으로 보고 지급 자체를 스킵한다(XpEvent도 안 남기고, 하루 상한 카운트도
     * 건드리지 않는다).
     */
    public void grantXp(
            Long userId,
            XpSourceType sourceType,
            Long sourceId,
            IntSupplier amountSupplier,
            String componentSignature,
            List<Pet> petsToCredit
    ) {
        // 유저 행을 잠그기 전에 값싼 사전 검사부터 한다 — 이미 상한/중복으로 막힐 호출
        // (예: 판별을 하루에 10번 넘게 반복하는 흔한 케이스)이 매번 User row를 잠글 필요는
        // 없다. 그래도 이 사전 검사만으로는 두 요청이 동시에 통과해버릴 수 있어(아래 참고),
        // 잠금을 잡은 뒤 같은 검사를 한 번 더 한다.
        if (isAlreadyGrantedForSource(userId, sourceType, sourceId)
                || isAlreadyGrantedForComponentSignature(userId, sourceType, componentSignature)) {
            return;
        }
        if (isDailyCapReached(userId, sourceType)) {
            return;
        }

        // 여기서부터 유저 행을 잠가 같은 유저에 대한 동시 지급 요청을 직렬화한다 — 잠금 없이
        // totalXp를 읽고 갱신하면, 두 요청이 같은 값을 읽어 서로의 결과를 덮어쓸 수 있다
        // (lost update). 호출부가 이미 존재 확인을 마친 userId만 넘기므로 못 찾는 경우는
        // 방어적으로만 처리한다 — 지급 없이 조용히 리턴(이 메서드가 원래 액션의 성공 여부에
        // 영향을 주면 안 되므로 예외로 올리지 않는다).
        User user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return;
        }
        // 잠금을 잡기 전에 두 요청이 동시에 위 사전 검사를 통과했을 수 있으므로, 직렬화된
        // 상태에서 같은 검사를 다시 한다 — 여기서는 앞선 요청이 커밋한 결과가 보인다.
        if (isAlreadyGrantedForSource(userId, sourceType, sourceId)
                || isAlreadyGrantedForComponentSignature(userId, sourceType, componentSignature)
                || isDailyCapReached(userId, sourceType)) {
            return;
        }

        // 잠금(직렬화)이 걸린 뒤에야 실제 지급액을 계산한다 — amountSupplier가 "오늘 이미 크레딧된
        // 구성요소" 같은 걸 다시 조회하는 경우, 앞선 요청이 커밋한 결과가 이 시점엔 이미 보인다.
        int amount = amountSupplier.getAsInt();
        if (amount <= 0) {
            return;
        }

        try {
            xpEventRepository.save(
                    XpEvent.builder()
                            .user(user)
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .amount(amount)
                            .componentSignature(componentSignature)
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            // uk_xp_events_user_source·uk_xp_events_user_source_type_component_signature가
            // 막아준 마지막 방어선 — 두 검사 사이에도 남는 레이스를 여기서 잡는다. 원래 액션은
            // 그대로 성공해야 하므로 예외를 올리지 않는다.
            log.warn(
                    "이미 지급된 경험치라 스킵합니다 — userId={}, sourceType={}, sourceId={}, componentSignature={}",
                    userId, sourceType, sourceId, componentSignature
            );
            return;
        }

        long newTotalXp = user.getTotalXp() + amount;
        int newLevel = LevelCurve.levelForTotalXp(newTotalXp);
        boolean isLeveledUp = user.gainXp(newTotalXp, newLevel);

        if (isLeveledUp && user.isLevelUpNotificationEnabled()) {
            gamificationNotificationService.notifyLevelUp(userId, newLevel);
        }

        badgeEvaluationService.evaluateAfterXpEvent(user, sourceType);

        if (!petsToCredit.isEmpty()) {
            creditPets(
                    petsToCredit,
                    sourceType,
                    sourceId,
                    amount,
                    componentSignature
            );
        }
    }

    /**
     * 특정 반려동물과 연결되지 않는 행동(제보·코스 공개·코스 공유 복사 등)이 반려동물 개별
     * 경험치를 이 유저의 반려동물 전체에게 나눠주고 싶을 때 쓰는 대상 목록 — 여러 호출부가
     * 각자 PetRepository를 직접 물고 똑같은 조회를 반복하지 않도록 한 곳에 모아둔다. 삭제된
     * 반려동물은 제외한다.
     */
    public List<Pet> allActivePetsOf(Long userId) {
        return petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(userId);
    }

    /**
     * petsToCredit 각각에게 같은 금액을 그대로(나누지 않고) 지급한다. 반려동물 한 마리가
     * uk_pet_xp_events_pet_source(-_component_signature)에 걸려도 그 반려동물만 건너뛰고
     * 나머지는 계속 지급한다 — 그룹 판별처럼 여러 마리가 한 번에 걸리는 호출에서 한 마리의
     * 중복이 다른 마리의 정상 지급까지 막으면 안 된다. petXpEventWriter.save가 별도
     * 세이브포인트(NESTED)로 저장해서, 한 마리의 실패가 Postgres 트랜잭션 전체를 abort시켜
     * 나머지 반려동물이나 이 메서드를 호출한 User XP 커밋까지 막는 걸 방지한다(PetXpEventWriter
     * 참고). 배지 재평가·레벨업 푸시는 반려동물 단위로는 아직 없다(카드 표시용 레벨·진행바만
     * 필요한 범위라 의도적으로 뺐다).
     */
    private void creditPets(
            List<Pet> petsToCredit,
            XpSourceType sourceType,
            Long sourceId,
            int amount,
            String componentSignature
    ) {
        for (Pet pet : petsToCredit) {
            try {
                petXpEventWriter.save(
                        pet,
                        sourceType,
                        sourceId,
                        amount,
                        componentSignature
                );
            } catch (DataIntegrityViolationException e) {
                log.warn(
                        "이미 지급된 반려동물 경험치라 스킵합니다 — petId={}, sourceType={}, sourceId={}, componentSignature={}",
                        pet.getPetId(), sourceType, sourceId, componentSignature
                );
                continue;
            }

            long newPetTotalXp = pet.getTotalXp() + amount;
            int newPetLevel = LevelCurve.levelForTotalXp(newPetTotalXp);
            pet.gainXp(newPetTotalXp, newPetLevel);
        }
    }

    /**
     * "구원자" 배지 평가 — 리뷰 도메인(ReviewCommandService)이 "도움됐어요" 표시
     * 성공 뒤에 부른다. grantXp와 달리 XP 지급이나 하루 상한이 없다 — 남이 눌러주는 게
     * 트리거라 본인 행동 기반 상한 개념이 안 맞고, 기획 결정으로 이 배지는 XP도 안 준다.
     * 총합 계산은 리뷰 도메인이 소유한 개념이라 호출부가 이미 계산해서 넘긴다.
     */
    public void evaluateHelpfulSaviorBadge(
            User reviewAuthor,
            long totalHelpfulReceived
    ) {
        badgeEvaluationService.evaluateHelpfulSaviorBadge(reviewAuthor, totalHelpfulReceived);
    }

    /**
     * PATCH /api/v1/me/gamification/notification — 레벨업(및 배지) 알림 on/off. 다른 도메인
     * 처럼 "본인 것만" 걱정할 필요가 없다 — 대상이 항상 인증된 본인(userId)뿐이라 소유권 검증이
     * 따로 필요 없다.
     */
    public boolean updateLevelUpNotification(
            Long userId,
            boolean enabled
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER4005));
        user.toggleLevelUpNotification(enabled);
        return user.isLevelUpNotificationEnabled();
    }

    // 오늘(KST) 지급분의 componentSignature 전체 — 당일 단위 중복 판단용(코스 공개의 "오늘 이미
    // 쓴 스톱" 계산에 쓰인다). 평생 1회(componentSignature 완전 일치) 판정은 이 서비스 내부에서만
    // 쓰고 호출부에 따로 안 열어준다 — grantXp 자체가 잠금 전/후로 이미 그 판정을 하기 때문에,
    // 호출부가 grantXp 호출 전에 똑같은 판정을 미리 하면 잠금 밖에서 계산한 값이라 레이스에
    // 취약해진다(IntSupplier 오버로드의 문서 참고).
    public List<String> findComponentSignaturesGrantedToday(
            Long userId,
            XpSourceType sourceType
    ) {
        return xpEventRepository.findComponentSignaturesGrantedSince(userId, sourceType, startOfTodayInBusinessZone());
    }

    private boolean isAlreadyGrantedForSource(
            Long userId,
            XpSourceType sourceType,
            Long sourceId
    ) {
        return sourceId != null
                && xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
    }

    private boolean isAlreadyGrantedForComponentSignature(
            Long userId,
            XpSourceType sourceType,
            String componentSignature
    ) {
        return componentSignature != null
                && xpEventRepository.existsByUser_IdAndSourceTypeAndComponentSignature(userId, sourceType, componentSignature);
    }

    private boolean isDailyCapReached(
            Long userId,
            XpSourceType sourceType
    ) {
        long todayCount = xpEventRepository
                .countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(userId, sourceType, startOfTodayInBusinessZone());
        return todayCount >= sourceType.getDailyCap();
    }

    /**
     * "오늘 자정"을 KST 기준으로 계산해, {@code createdAt}(서버가 항상 UTC로 고정해 저장하는
     * naive LocalDateTime)과 같은 좌표로 맞춰 돌려준다. 서버 프로세스 타임존(UTC)을 그대로
     * 썼다면 하루 상한이 자정이 아니라 오전 9시(KST)에 풀렸을 것이다.
     *
     * <p>인스턴스 상태를 안 쓰는 순수 계산이라 static이다 — GamificationQueryService(오늘의
     * 퀘스트 조회)도 이 메소드를 그대로 쓴다. "오늘"의 기준(하루 상한 판단·퀘스트 표시)이
     * 두 클래스에 따로 있으면 한쪽만 고쳤을 때 지급 여부와 화면 표시가 어긋날 수 있어, 상한을
     * 실제로 적용하는 이 클래스가 기준을 소유하고 조회 쪽은 가져다 쓰기만 한다.
     */
    static LocalDateTime startOfTodayInBusinessZone() {
        return LocalDate.now(BUSINESS_ZONE)
                .atStartOfDay(BUSINESS_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
