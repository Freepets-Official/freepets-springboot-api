package com.freepets.global.util;

import java.time.ZoneId;

/**
 * 서버 프로세스는 항상 UTC로 고정돼 있다(FreepetsServerApplication의 static 블록) — "오늘"·
 * "이번 주" 같은 날짜 경계는 서버 타임존이 아니라 실제 사용자가 있는 KST 기준이어야 한다.
 *
 * <p>이 상수를 쓰기 전엔 GamificationService·OwnerFacilityQueryService·FacilityGradeSnapshotService·
 * PetConverter가 각자 같은 값을 독립적으로 선언하고 있었다 — 타임존 정책이 바뀌면(예: DST 관련
 * 설정) 네 곳을 따로 찾아 고쳐야 했다.
 */
public final class BusinessZone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private BusinessZone() {}

}
