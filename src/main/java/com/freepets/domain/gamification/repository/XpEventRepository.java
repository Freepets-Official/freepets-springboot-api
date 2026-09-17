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

    interface SourceTypeCount {
        XpSourceType getSourceType();

        long getCount();
    }

}
