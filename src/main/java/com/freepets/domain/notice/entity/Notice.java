package com.freepets.domain.notice.entity;

import org.hibernate.annotations.ColumnDefault;

import com.freepets.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공지사항. 1.0 앱엔 하드코딩된 4건이 있었는데, 출시 전 날짜가 찍혀 지어낸 이력처럼 보여서
// SHOW_NOTICES = false로 꺼둔 상태였다 — 이 API가 뜨면 프론트는 상수 한 줄만 바꿔 켠다.
// 작성/수정 API는 없다 — 하드코딩 4건을 대체하는 규모라 운영자가 DB에 직접 넣는 걸 전제로 한다.
@Getter
@Entity
@Table(name = "notices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long noticeId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // 목록 맨 위 고정 여부 — GET /api/v1/notices 정렬 기준(NoticeRepository 참고).
    @ColumnDefault("false")
    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned;

    @Builder
    private Notice(
            String title,
            String body,
            boolean isPinned
    ) {
        this.title = title;
        this.body = body;
        this.isPinned = isPinned;
    }

}
