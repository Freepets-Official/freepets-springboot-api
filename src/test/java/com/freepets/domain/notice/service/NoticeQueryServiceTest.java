package com.freepets.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.notice.dto.NoticeResponseDTO;
import com.freepets.domain.notice.entity.Notice;
import com.freepets.domain.notice.repository.NoticeRepository;

@ExtendWith(MockitoExtension.class)
class NoticeQueryServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @InjectMocks
    private NoticeQueryService noticeQueryService;

    @Test
    void 고정_공지가_위로_최신순으로_내려온다() {
        Notice pinned = Notice.builder().title("점검 안내").body("내용1").pinned(true).build();
        Notice normal = Notice.builder().title("업데이트 소식").body("내용2").pinned(false).build();
        when(noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc())
                .thenReturn(List.of(pinned, normal));

        List<NoticeResponseDTO.NoticeItem> result = noticeQueryService.getNotices();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("점검 안내");
        assertThat(result.get(0).pinned()).isTrue();
        assertThat(result.get(1).title()).isEqualTo("업데이트 소식");
        assertThat(result.get(1).pinned()).isFalse();
    }

    @Test
    void 공지가_없으면_빈_목록을_반환한다() {
        when(noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc()).thenReturn(List.of());

        List<NoticeResponseDTO.NoticeItem> result = noticeQueryService.getNotices();

        assertThat(result).isEmpty();
    }

}
