package com.freepets.domain.petsatisfaction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import tools.jackson.databind.ObjectMapper;

/**
 * FacilityItem.isRecorded는 record 접근자명(isRecorded())과 실제로 내려가야 하는 JSON
 * 키(recorded)가 다르므로, 서비스 단위 테스트만으로는 필드명이 계약대로 나가는지 검증할
 * 수 없다. 프로젝트에서 실제로 쓰는 ObjectMapper로 직렬화한 결과 자체를 확인한다.
 */
@JsonTest
class PetSatisfactionResponseDTOTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void FacilityItem은_isRecorded가_아니라_recorded로_직렬화된다() throws Exception {
        PetSatisfactionResponseDTO.FacilityItem item =
                new PetSatisfactionResponseDTO.FacilityItem(1L, "몽이", 9.8f, true);

        String json = objectMapper.writeValueAsString(item);

        assertThat(json).contains("\"recorded\":true");
        assertThat(json).doesNotContain("isRecorded");
    }
}
