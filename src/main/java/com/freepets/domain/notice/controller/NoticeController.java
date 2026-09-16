package com.freepets.domain.notice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepets.domain.notice.dto.NoticeResponseDTO;
import com.freepets.domain.notice.service.NoticeQueryService;
import com.freepets.global.apiPayload.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeQueryService noticeQueryService;

    @GetMapping
    public ApiResponse<List<NoticeResponseDTO.NoticeItem>> getNotices() {
        return ApiResponse.onSuccess(
                noticeQueryService.getNotices()
        );
    }

}
