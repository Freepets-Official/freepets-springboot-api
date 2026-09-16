package com.freepets.domain.notice.dto;

import java.time.LocalDateTime;

public class NoticeResponseDTO {

    private NoticeResponseDTO() {}

    // GET /api/v1/notices 응답 항목. 필드명이 그대로 프론트 요구 스펙(id·title·body·createdAt·pinned)이다.
    public record NoticeItem(
            Long id,
            String title,
            String body,
            LocalDateTime createdAt,
            boolean pinned
    ) {}

}
