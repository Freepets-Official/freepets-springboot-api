package com.freepets.global.util;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freepets.global.apiPayload.code.status.ErrorStatus;
import com.freepets.global.apiPayload.exception.GeneralException;

// facilities.requirements, pet_check_verdicts.conditions, pet_checks.checklist/tips는 전부
// JSON 컬럼에 문자열 배열을 담는다(db/schema.sql). 엔티티 필드는 다른 JSON 컬럼들(PetCheck의
// 기존 conditions/checklist)과 같은 방식으로 순수 String으로 두고, 이 유틸로 서비스 레이어에서
// List<String> <-> JSON 문자열을 변환한다.
public class JsonListUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonListUtil() {}

    public static String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new GeneralException(ErrorStatus.COMMON500);
        }
    }

    public static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new GeneralException(ErrorStatus.COMMON500);
        }
    }
}
