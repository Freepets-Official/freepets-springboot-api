package com.freepets.domain.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.freepets.domain.gamification.entity.XpEvent;
import com.freepets.domain.gamification.entity.XpSourceType;
import com.freepets.domain.gamification.repository.XpEventRepository;
import com.freepets.domain.pet.entity.Pet;
import com.freepets.domain.pet.repository.PetRepository;
import com.freepets.domain.user.entity.Provider;
import com.freepets.domain.user.entity.User;
import com.freepets.domain.user.repository.UserRepository;
import com.freepets.global.apiPayload.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PetRepository petRepository;

    @Mock
    private XpEventRepository xpEventRepository;

    @Mock
    private PetXpEventWriter petXpEventWriter;

    @Mock
    private GamificationNotificationService gamificationNotificationService;

    @Mock
    private BadgeEvaluationService badgeEvaluationService;

    private GamificationService gamificationService;

    private User newUser() {
        User user = User.builder()
                .email("test@freepets.com")
                .passwordHash("hash")
                .nickname("테스터")
                .provider(Provider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private Pet newPet(Long petId) {
        Pet pet = Pet.builder().build();
        ReflectionTestUtils.setField(pet, "petId", petId);
        return pet;
    }

    private void setUpService() {
        gamificationService = new GamificationService(
                userRepository, petRepository, xpEventRepository, petXpEventWriter,
                gamificationNotificationService, badgeEvaluationService
        );
    }

    @Test
    void 정상_지급하면_XP가_증가하고_레벨이_갱신되며_레벨업_알림과_배지_재평가가_호출된다() {
        setUpService();
        User user = newUser(); // totalXp=0, level=1

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);

        // 레벨 2에 필요한 누적 XP는 100 — 150을 주면 레벨 2로 올라가야 한다.
        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 150);

        assertThat(user.getTotalXp()).isEqualTo(150);
        assertThat(user.getLevel()).isEqualTo(2);
        verify(xpEventRepository).save(any(XpEvent.class));
        verify(gamificationNotificationService).notifyLevelUp(1L, 2);
        verify(badgeEvaluationService).evaluateAfterXpEvent(user, XpSourceType.REVIEW);
    }

    @Test
    void 레벨업이_아니면_레벨업_알림을_보내지_않는다() {
        setUpService();
        User user = newUser();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);

        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 20);

        assertThat(user.getLevel()).isEqualTo(1);
        verify(gamificationNotificationService, never()).notifyLevelUp(any(), anyInt());
        verify(badgeEvaluationService).evaluateAfterXpEvent(user, XpSourceType.REVIEW);
    }

    @Test
    void 레벨업_알림이_꺼져있으면_레벨업해도_알림을_보내지_않는다() {
        setUpService();
        User user = newUser();
        user.toggleLevelUpNotification(false);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);

        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 150);

        assertThat(user.getLevel()).isEqualTo(2);
        verify(gamificationNotificationService, never()).notifyLevelUp(any(), anyInt());
    }

    @Test
    void 검사와_저장_사이_레이스로_유니크_제약_위반이_나면_조용히_스킵한다() {
        setUpService();
        User user = newUser();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);
        when(xpEventRepository.save(any(XpEvent.class)))
                .thenThrow(new DataIntegrityViolationException("uk_xp_events_user_source"));

        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 150);

        // uk_xp_events_user_source 위반은 동시에 들어온 같은 지급이 이미 반영됐다는 뜻이라,
        // 이번 호출에서는 유저 상태를 건드리지 않고 조용히 끝나야 한다.
        assertThat(user.getTotalXp()).isZero();
        assertThat(user.getLevel()).isEqualTo(1);
        verifyNoInteractions(gamificationNotificationService);
        verifyNoInteractions(badgeEvaluationService);
    }

    @Test
    void 하루_상한에_도달하면_지급하지_않는다() {
        setUpService();
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.PETCHECK, 555L))
                .thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(
                eq(1L), eq(XpSourceType.PETCHECK), any(LocalDateTime.class)
        )).thenReturn(10L); // PETCHECK 하루 상한(10) 도달

        gamificationService.grantXp(1L, XpSourceType.PETCHECK, 555L, 5);

        verifyNoInteractions(userRepository);
        verify(xpEventRepository, never()).save(any());
        verifyNoInteractions(gamificationNotificationService);
        verifyNoInteractions(badgeEvaluationService);
    }

    @Test
    void 리뷰도_하루_상한이_있다() {
        // 시설당 리뷰는 1개뿐이라 sourceId(reviewId)가 매번 달라 평생 1회 검사는 항상 통과하지만,
        // 서로 다른 시설을 여러 곳 판별받고 리뷰를 남기면 하루에도 여러 번 지급될 수 있어
        // 다른 도메인처럼 하루 상한(5)으로 막혀야 한다.
        setUpService();
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 777L))
                .thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(
                eq(1L), eq(XpSourceType.REVIEW), any(LocalDateTime.class)
        )).thenReturn(5L); // REVIEW 하루 상한(5) 도달

        gamificationService.grantXp(1L, XpSourceType.REVIEW, 777L, 20);

        verifyNoInteractions(userRepository);
        verify(xpEventRepository, never()).save(any());
    }

    @Test
    void 코스_공개도_하루_상한이_있다() {
        // sourceId(courseId)가 매번 달라 평생 1회 검사는 항상 통과하므로, 같은 시설을 재사용한
        // 트리비얼한 코스를 계속 새로 만들어 공개해도 하루 상한으로 막혀야 한다.
        setUpService();
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.COURSE_PUBLISHED, 999L))
                .thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(
                eq(1L), eq(XpSourceType.COURSE_PUBLISHED), any(LocalDateTime.class)
        )).thenReturn(5L); // COURSE_PUBLISHED 하루 상한(5) 도달

        gamificationService.grantXp(1L, XpSourceType.COURSE_PUBLISHED, 999L, 25);

        verifyNoInteractions(userRepository);
        verify(xpEventRepository, never()).save(any());
    }

    @Test
    void 이미_지급된_sourceId면_스킵한다() {
        setUpService();
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.COURSE_PUBLISHED, 10L))
                .thenReturn(true);

        gamificationService.grantXp(1L, XpSourceType.COURSE_PUBLISHED, 10L, 30);

        verifyNoInteractions(userRepository);
        verify(xpEventRepository, never()).save(any());
        verify(xpEventRepository, never())
                .countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(any(), any(), any());
    }

    @Test
    void 알림_설정을_토글한다() {
        setUpService();
        User user = newUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        boolean result = gamificationService.updateLevelUpNotification(1L, false);

        assertThat(result).isFalse();
        assertThat(user.isLevelUpNotificationEnabled()).isFalse();
    }

    @Test
    void 존재하지_않는_유저의_알림_설정을_토글하면_예외를_던진다() {
        setUpService();
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gamificationService.updateLevelUpNotification(1L, false))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void 구원자_배지_평가는_badgeEvaluationService에_그대로_위임한다() {
        setUpService();
        User author = newUser();

        gamificationService.evaluateHelpfulSaviorBadge(author, 10L);

        verify(badgeEvaluationService).evaluateHelpfulSaviorBadge(author, 10L);
    }

    @Test
    void petsToCredit이_있으면_각_반려동물에게_전액이_그대로_지급된다() {
        setUpService();
        User user = newUser();
        Pet petA = newPet(1L);
        Pet petB = newPet(2L);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.PETCHECK, 100L))
                .thenReturn(false);

        gamificationService.grantXp(1L, XpSourceType.PETCHECK, 100L, 5, List.of(petA, petB));

        // 나눠주지 않고 두 마리 모두 5XP씩 그대로 — 스톱/아이 수와 무관하게 전액.
        assertThat(petA.getTotalXp()).isEqualTo(5);
        assertThat(petB.getTotalXp()).isEqualTo(5);
        verify(petXpEventWriter).save(petA, XpSourceType.PETCHECK, 100L, 5, null);
        verify(petXpEventWriter).save(petB, XpSourceType.PETCHECK, 100L, 5, null);
    }

    @Test
    void petsToCredit이_비어있으면_반려동물_지급을_아예_건드리지_않는다() {
        setUpService();
        User user = newUser();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.REVIEW, 100L))
                .thenReturn(false);

        gamificationService.grantXp(1L, XpSourceType.REVIEW, 100L, 20, List.of());

        verifyNoInteractions(petXpEventWriter);
    }

    @Test
    void 한_반려동물이_이미_지급받은_적_있어도_나머지_반려동물은_정상_지급된다() {
        setUpService();
        User user = newUser();
        Pet alreadyCredited = newPet(1L);
        Pet fresh = newPet(2L);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.PETCHECK, 100L))
                .thenReturn(false);
        // NESTED 세이브포인트로 저장하는 PetXpEventWriter가 이 반려동물만 유니크 제약에 걸려
        // 실패했다고 가정한다 — 실제로는 세이브포인트 롤백 후 호출부(creditPets)로 예외가
        // 전파되는데, Mockito 목에서는 그 저장 시점 예외만 그대로 재현하면 된다.
        doThrow(new DataIntegrityViolationException("uk_pet_xp_events_pet_source"))
                .when(petXpEventWriter).save(eq(alreadyCredited), any(), any(), anyInt(), any());

        gamificationService.grantXp(1L, XpSourceType.PETCHECK, 100L, 5, List.of(alreadyCredited, fresh));

        // 첫 번째 반려동물이 유니크 제약에 걸려도 두 번째 반려동물은 그대로 지급된다.
        assertThat(alreadyCredited.getTotalXp()).isZero();
        assertThat(fresh.getTotalXp()).isEqualTo(5);
    }

    @Test
    void User_지급_자체가_하루_상한으로_스킵되면_반려동물도_전혀_지급되지_않는다() {
        setUpService();
        Pet pet = newPet(1L);

        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.PETCHECK, 555L))
                .thenReturn(false);
        when(xpEventRepository.countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(
                eq(1L), eq(XpSourceType.PETCHECK), any(LocalDateTime.class)
        )).thenReturn(10L); // PETCHECK 하루 상한(10) 도달

        gamificationService.grantXp(1L, XpSourceType.PETCHECK, 555L, 5, List.of(pet));

        assertThat(pet.getTotalXp()).isZero();
        verifyNoInteractions(petXpEventWriter);
    }

    @Test
    void 이미_지급된_componentSignature면_스킵한다() {
        // sourceId(courseId)는 매번 새로 발급되는 값이라 평생 1회 검사를 못 걸러내지만,
        // componentSignature(스톱 구성)로 이미 지급받은 적이 있으면 새 courseId로도 막혀야 한다.
        setUpService();
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.COURSE_PUBLISHED, 20L))
                .thenReturn(false);
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndComponentSignature(1L, XpSourceType.COURSE_PUBLISHED, "1,2"))
                .thenReturn(true);

        gamificationService.grantXp(1L, XpSourceType.COURSE_PUBLISHED, 20L, 30, "1,2");

        verifyNoInteractions(userRepository);
        verify(xpEventRepository, never()).save(any());
    }

    @Test
    void amountSupplier는_User_행_잠금을_잡은_뒤에만_호출된다() {
        // 코스 공개처럼 "오늘 이미 쓴 스톱을 뺀 나머지로 금액 계산"이 필요한 호출부를 위한
        // 오버로드 — 잠금(findByIdForUpdate) 전에는 절대 호출되면 안 된다. 잠금 밖에서
        // 계산하면 동시 요청이 같은 스톱을 똑같이 "아직 안 쓴 것"으로 보는 레이스가 생긴다.
        setUpService();
        User user = newUser();
        List<String> callOrder = new ArrayList<>();

        when(userRepository.findByIdForUpdate(1L)).thenAnswer(invocation -> {
            callOrder.add("lock");
            return Optional.of(user);
        });
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.COURSE_PUBLISHED, 30L))
                .thenReturn(false);

        IntSupplier amountSupplier = () -> {
            callOrder.add("amount");
            return 25;
        };

        gamificationService.grantXp(1L, XpSourceType.COURSE_PUBLISHED, 30L, amountSupplier, null, List.of());

        assertThat(callOrder).containsExactly("lock", "amount");
        assertThat(user.getTotalXp()).isEqualTo(25);
    }

    @Test
    void amountSupplier가_0_이하를_반환하면_지급_자체를_완전히_스킵한다() {
        // 코스 공개에서 "오늘 이미 쓴 스톱뿐"인 경우(새 스톱 0개) — 기본 지급조차 없이 스킵돼야
        // 한다. XpEvent도 안 남고 하루 상한 카운트도 안 늘어야 한다.
        setUpService();
        User user = newUser();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(xpEventRepository.existsByUser_IdAndSourceTypeAndSourceId(1L, XpSourceType.COURSE_PUBLISHED, 40L))
                .thenReturn(false);

        gamificationService.grantXp(1L, XpSourceType.COURSE_PUBLISHED, 40L, () -> 0, "1,2", List.of());

        assertThat(user.getTotalXp()).isZero();
        verify(xpEventRepository, never()).save(any());
        verifyNoInteractions(gamificationNotificationService);
        verifyNoInteractions(badgeEvaluationService);
    }

    @Test
    void allActivePetsOf는_삭제되지_않은_반려동물만_petId_오름차순으로_돌려준다() {
        setUpService();
        Pet pet = newPet(1L);
        when(petRepository.findAllByUserIdAndDeletedAtIsNullOrderByPetIdAsc(1L)).thenReturn(List.of(pet));

        List<Pet> result = gamificationService.allActivePetsOf(1L);

        assertThat(result).containsExactly(pet);
    }

    @Test
    void findAllComponentSignaturesGranted는_오늘로_기간을_제한하지_않는다() {
        // "오늘"로 좁히면 스톱 하나만 바꿔가며 하루 지나서 반복하는 코스 공개 파밍을 못 막는다
        // (CourseCommandService.resolveCoursePublishedXp 참고) — 이 조회는 기간 제한이 아예
        // 없어야 한다. 리포지토리 자체가 시간 필터를 갖고 있으니, 여기서는 그 필터에
        // LocalDateTime.MIN(사실상 무제한)이 넘어가는지만 확인한다.
        setUpService();

        gamificationService.findAllComponentSignaturesGranted(1L, XpSourceType.COURSE_PUBLISHED);

        verify(xpEventRepository).findComponentSignaturesGrantedSince(1L, XpSourceType.COURSE_PUBLISHED, LocalDateTime.MIN);
    }
}
