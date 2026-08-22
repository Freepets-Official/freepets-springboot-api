package com.freepets.infra.tourapi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.freepets.infra.tourapi.dto.LdongCodeItem;

/**
 * 법정동 코드를 지역 이름으로 옮기는 표.
 *
 * <p>시설 응답에는 지역이 코드로만 담겨 오지만, 발자국 랭킹의 시도·시군구 칩에는 이름이 필요하다.
 * 지역명은 거의 변하지 않으므로 조인 대신 시설 행에 비정규화해 저장한다.
 */
public class RegionNameTable {

    private final Map<String, String> sidoNames = new HashMap<>();
    private final Map<String, String> sigunguNames = new HashMap<>();

    public RegionNameTable(List<LdongCodeItem> items) {
        for (LdongCodeItem item : items) {
            if (item.sidoCode() == null) {
                continue;
            }
            if (item.sidoName() != null) {
                sidoNames.putIfAbsent(item.sidoCode(), item.sidoName());
            }
            if (item.sigunguCode() != null && item.sigunguName() != null) {
                sigunguNames.putIfAbsent(keyOf(item.sidoCode(), item.sigunguCode()), item.sigunguName());
            }
        }
    }

    public String sidoNameOf(String sidoCode) {
        return sidoCode == null ? null : sidoNames.get(sidoCode);
    }

    public String sigunguNameOf(
            String sidoCode,
            String sigunguCode
    ) {
        if (sidoCode == null || sigunguCode == null) {
            return null;
        }
        return sigunguNames.get(keyOf(sidoCode, sigunguCode));
    }

    public int size() {
        return sigunguNames.size();
    }

    private String keyOf(
            String sidoCode,
            String sigunguCode
    ) {
        return sidoCode + "-" + sigunguCode;
    }

}
