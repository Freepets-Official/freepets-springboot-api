package com.freepets.domain.notice.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.freepets.domain.notice.converter.NoticeConverter;
import com.freepets.domain.notice.dto.NoticeResponseDTO;
import com.freepets.domain.notice.repository.NoticeRepository;

import lombok.RequiredArgsConstructor;

// GET /api/v1/notices — 고정 공지가 위로, 나머지는 최신순으로 전체를 내려준다.
// 1.0 하드코딩 4건 수준의 규모라 페이지네이션은 아직 두지 않았다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeQueryService {

    private final NoticeRepository noticeRepository;

    public List<NoticeResponseDTO.NoticeItem> getNotices() {
        return noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc().stream()
                .map(NoticeConverter::toNoticeItem)
                .toList();
    }

}
