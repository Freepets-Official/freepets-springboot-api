package com.freepets.domain.pet.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import tools.jackson.databind.ObjectMapper;

/**
 * RegistrationCard의 @JsonInclude(NON_NULL) 계약을 실제 프로젝트 ObjectMapper로 확인한다.
 * age·xpToNextLevel처럼 "null이면 키 자체가 없다"고 주석·문서에 적어둔 필드는, 서비스 단위
 * 테스트(POJO 비교)만으로는 실제 직렬화 결과까지 보장하지 못한다 —
 * PetSatisfactionResponseDTOTest와 같은 이유로 여기서 직접 확인한다.
 */
@JsonTest
class PetResponseDTOTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void age와_xpToNextLevel이_null이면_키_자체가_빠진다() throws Exception {
        PetResponseDTO.RegistrationCard card = new PetResponseDTO.RegistrationCard(
                1L, "몽이", null, "말티즈", null, null,
                LocalDateTime.of(2026, 1, 1, 0, 0), 70, 241500L, null, List.of()
        );

        String json = objectMapper.writeValueAsString(card);

        assertThat(json).doesNotContain("\"age\"");
        assertThat(json).doesNotContain("\"xpToNextLevel\"");
    }

    @Test
    void age와_xpToNextLevel이_있으면_그대로_직렬화된다() throws Exception {
        PetResponseDTO.RegistrationCard card = new PetResponseDTO.RegistrationCard(
                1L, "몽이", null, "말티즈", null, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0), 2, 150L, 50L, List.of()
        );

        String json = objectMapper.writeValueAsString(card);

        assertThat(json).contains("\"age\":3");
        assertThat(json).contains("\"xpToNextLevel\":50");
    }
}
