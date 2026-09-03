package com.freepets.domain.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freepets.domain.facility.entity.Region;
import com.freepets.domain.facility.repository.RegionRepository;
import com.freepets.infra.tourapi.TourApiClient;
import com.freepets.infra.tourapi.TourApiResponseParser;
import com.freepets.infra.tourapi.dto.LdongCodeItem;

@ExtendWith(MockitoExtension.class)
class RegionSyncServiceTest {

    private static final String RESPONSE_BODY = "{}";

    @Mock
    private TourApiClient tourApiClient;

    @Mock
    private TourApiResponseParser tourApiResponseParser;

    @Mock
    private RegionRepository regionRepository;

    @InjectMocks
    private RegionSyncService regionSyncService;

    @BeforeEach
    void 관광공사가_한_페이지를_내려준다() {
        when(tourApiClient.ldongCode(any(), eq(true), anyInt(), anyInt())).thenReturn(RESPONSE_BODY);
        when(tourApiResponseParser.parseTotalCount(anyString())).thenReturn(1);
    }

    private void givenResponse(List<LdongCodeItem> items) {
        when(tourApiResponseParser.parseItems(anyString(), eq(LdongCodeItem.class))).thenReturn(items);
    }

    private Region createRegion(
            String sidoCode,
            String sido,
            String sigunguCode,
            String sigungu
    ) {
        return Region.builder()
                .sidoCode(sidoCode)
                .sido(sido)
                .sigunguCode(sigunguCode)
                .sigungu(sigungu)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Region> captureSaved() {
        ArgumentCaptor<List<Region>> captor = ArgumentCaptor.forClass(List.class);
        verify(regionRepository).saveAll(captor.capture());

        return captor.getValue();
    }

    @Test
    @DisplayName("저장되지 않은 지역을 새로 넣는다")
    void 저장되지_않은_지역을_새로_넣는다() {
        givenResponse(List.of(new LdongCodeItem("32", "강원특별자치도", "010", "강릉시")));
        when(regionRepository.findAll()).thenReturn(List.of());

        regionSyncService.syncRegions();

        assertThat(captureSaved())
                .extracting(Region::getSigungu)
                .containsExactly("강릉시");
    }

    @Test
    @DisplayName("이름이 바뀌었으면 행을 새로 만들지 않고 고친다")
    void 이름이_바뀌었으면_행을_새로_만들지_않고_고친다() {
        Region stored = createRegion("32", "강원도", "010", "강릉시");

        givenResponse(List.of(new LdongCodeItem("32", "강원특별자치도", "010", "강릉시")));
        when(regionRepository.findAll()).thenReturn(List.of(stored));

        regionSyncService.syncRegions();

        // 코드가 같으므로 같은 행의 이름만 바뀌어야 한다. 새로 넣으면 칩이 두 개가 된다.
        assertThat(stored.getSido()).isEqualTo("강원특별자치도");
        assertThat(captureSaved()).isEmpty();
    }

    @Test
    @DisplayName("응답에 없는 기존 지역을 지우지 않는다")
    void 응답에_없는_기존_지역을_지우지_않는다() {
        Region stored = createRegion("32", "강원특별자치도", "010", "강릉시");

        givenResponse(List.of(new LdongCodeItem("11", "서울특별시", "680", "강남구")));
        when(regionRepository.findAll()).thenReturn(List.of(stored));

        regionSyncService.syncRegions();

        // 응답이 일부만 내려온 상황에서 지우면 멀쩡한 지역이 통째로 사라진다.
        verify(regionRepository, never()).delete(any(Region.class));
        verify(regionRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("같은 지역이 응답에 두 번 담겨 와도 한 번만 넣는다")
    void 같은_지역이_응답에_두_번_담겨_와도_한_번만_넣는다() {
        givenResponse(List.of(
                new LdongCodeItem("32", "강원특별자치도", "010", "강릉시"),
                new LdongCodeItem("32", "강원특별자치도", "010", "강릉시")
        ));
        when(regionRepository.findAll()).thenReturn(List.of());

        regionSyncService.syncRegions();

        assertThat(captureSaved()).hasSize(1);
    }

    @Test
    @DisplayName("시군구가 없는 시도도 저장한다")
    void 시군구가_없는_시도도_저장한다() {
        givenResponse(List.of(new LdongCodeItem("36", "세종특별자치시", null, null)));
        when(regionRepository.findAll()).thenReturn(List.of());

        regionSyncService.syncRegions();

        assertThat(captureSaved())
                .singleElement()
                .satisfies(region -> {
                    assertThat(region.getSido()).isEqualTo("세종특별자치시");
                    assertThat(region.hasSigungu()).isFalse();
                });
    }

}
