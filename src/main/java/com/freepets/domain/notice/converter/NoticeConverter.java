package com.freepets.domain.notice.converter;

import com.freepets.domain.notice.dto.NoticeResponseDTO;
import com.freepets.domain.notice.entity.Notice;

public class NoticeConverter {

    private NoticeConverter() {}

    public static NoticeResponseDTO.NoticeItem toNoticeItem(Notice notice) {
        return new NoticeResponseDTO.NoticeItem(
                notice.getNoticeId(),
                notice.getTitle(),
                notice.getBody(),
                notice.getCreatedAt(),
                notice.isPinned()
        );
    }

}
