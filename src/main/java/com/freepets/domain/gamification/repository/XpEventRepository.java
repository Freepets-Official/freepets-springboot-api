package com.freepets.domain.gamification.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.gamification.entity.XpEvent;
import com.freepets.domain.gamification.entity.XpSourceType;

public interface XpEventRepository extends JpaRepository<XpEvent, Long> {

    // 하루 지급 상한 판단(GamificationService) — 오늘 자정 이후 같은 유형으로 몇 번 지급받았는지.
    long countByUser_IdAndSourceTypeAndCreatedAtGreaterThanEqual(
            Long userId,
            XpSourceType sourceType,
            LocalDateTime since
    );

    // 평생 1회 지급 판단(코스 공개 등) — 이 (user, type, sourceId) 조합으로 이미 지급한 적이 있는지.
    boolean existsByUser_IdAndSourceTypeAndSourceId(
            Long userId,
            XpSourceType sourceType,
            Long sourceId
    );

    // 구성요소 조합 단위 평생 1회 판단(코스 공개 등, GamificationService.findGrantedSourceIds
    // 자리를 대체) — 이 (user, type, componentSignature) 조합으로 이미 지급한 적이 있는지.
    // sourceId(코스 id)와 달리 componentSignature는 구성요소(스톱 시설)가 그대로면 코스를
    // 새로 만들어도 값이 안 바뀌어서, courseId 우회로 재지급받는 것을 막는다.
    boolean existsByUser_IdAndSourceTypeAndComponentSignature(
            Long userId,
            XpSourceType sourceType,
            String componentSignature
    );

    // 당일 구성요소 중복 판단(코스 공개의 스톱 단위 하루 중복 방지) — 오늘 이 sourceType으로
    // 지급된 componentSignature 전체. 호출부(CourseCommandService)가 각 서명을 다시 개별
    // componentId로 쪼개 합집합을 구한다. Course를 다시 조회하지 않아도 되도록 지급 시점에
    // 굳힌 값만 쓴다 — 이후 코스가 수정·삭제돼도 이 값은 영향받지 않는다.
    @Query("""
            SELECT xpEvent.componentSignature
            FROM XpEvent xpEvent
            WHERE xpEvent.user.id = :userId
            AND xpEvent.sourceType = :sourceType
            AND xpEvent.createdAt >= :since
            AND xpEvent.componentSignature IS NOT NULL
            """)
    List<String> findComponentSignaturesGrantedSince(
            @Param("userId") Long userId,
            @Param("sourceType") XpSourceType sourceType,
            @Param("since") LocalDateTime since
    );

    // BadgeEvaluationService의 개수 기반 배지 조건(예: 리뷰 10개) 판정용 — sourceType 하나만
    // 필요한 상황(XpEvent가 막 하나 생긴 직후 그 타입만 재평가)에서 쓴다.
    long countByUser_IdAndSourceType(
            Long userId,
            XpSourceType sourceType
    );

    /**
     * GamificationQueryService의 progress[] 조회용 — sourceType별 누적 개수가 한 번에 다
     * 필요할 때(패밀리 6개를 전부 보여줘야 하는 화면 조회) countByUser_IdAndSourceType을
     * 소스타입 수만큼 반복 호출하는 대신 그룹 쿼리 하나로 가져온다. 한 번도 지급받은 적 없는
     * sourceType은 결과에 행 자체가 없다 — 호출부가 0으로 채워야 한다.
     */
    @Query("""
            SELECT xpEvent.sourceType AS sourceType, COUNT(xpEvent) AS count
            FROM XpEvent xpEvent
            WHERE xpEvent.user.id = :userId
            GROUP BY xpEvent.sourceType
            """)
    List<SourceTypeCount> countGroupedByUser_Id(@Param("userId") Long userId);

    /**
     * GamificationQueryService의 오늘의 퀘스트 조회용({@code GET /me/gamification/quests}) —
     * 오늘(자정 이후) sourceType별 지급 횟수와 XP 합계를 한 번에 가져온다. countGroupedByUser_Id와
     * 같은 이유로 그룹 쿼리 하나로 합친다 — sourceType 수만큼 반복 호출하지 않는다. 오늘 한 번도
     * 지급받지 않은 sourceType은 결과에 행 자체가 없다 — 호출부가 0으로 채워야 한다.
     */
    @Query("""
            SELECT xpEvent.sourceType AS sourceType, COUNT(xpEvent) AS count, SUM(xpEvent.amount) AS totalAmount
            FROM XpEvent xpEvent
            WHERE xpEvent.user.id = :userId
            AND xpEvent.createdAt >= :since
            GROUP BY xpEvent.sourceType
            """)
    List<SourceTypeDailyStats> countAndSumGroupedByUser_IdSince(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since
    );

    interface SourceTypeCount {
        XpSourceType getSourceType();

        long getCount();
    }

    interface SourceTypeDailyStats {
        XpSourceType getSourceType();

        long getCount();

        long getTotalAmount();
    }

}
