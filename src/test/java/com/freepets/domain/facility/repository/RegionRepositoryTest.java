package com.freepets.domain.facility.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.freepets.domain.facility.entity.Region;
import com.freepets.global.config.JpaAuditingConfig;

/**
 * {@code findBySidoCodeAndSigunguCode}가 {@code sigunguCode=null} 파라미터를 {@code is null}
 * 비교로 정확히 번역하는지 확인한다. 목으로는 검증할 수 없는 부분이라 H2에 넣고 돌린다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:region;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS freepets",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RegionRepositoryTest {

    @Autowired
    private RegionRepository regionRepository;

    @BeforeEach
    void setUp() {
        regionRepository.save(Region.builder()
                .sidoCode("32")
                .sido("강원특별자치도")
                .sigunguCode("32210")
                .sigungu("강릉시")
                .build());

        // 세종특별자치시처럼 하위 시군구가 없는 시도는 sigunguCode가 null이다.
        regionRepository.save(Region.builder()
                .sidoCode("36")
                .sido("세종특별자치시")
                .build());
    }

    @Test
    void 시도와_시군구_코드가_일치하면_조회된다() {
        Optional<Region> found = regionRepository.findBySidoCodeAndSigunguCode("32", "32210");

        assertThat(found).isPresent();
        assertThat(found.get().getSido()).isEqualTo("강원특별자치도");
        assertThat(found.get().getSigungu()).isEqualTo("강릉시");
    }

    @Test
    void 시군구_코드가_null이면_시군구가_없는_시도_행과_매칭된다() {
        Optional<Region> found = regionRepository.findBySidoCodeAndSigunguCode("36", null);

        assertThat(found).isPresent();
        assertThat(found.get().getSido()).isEqualTo("세종특별자치시");
    }

    @Test
    void 시군구_행만_있는_시도도_시도_존재_확인은_통과한다() {
        // #154 — 시도 단위 조회 검증이 이 메서드에 달려 있다. "시군구가 null인 행"을 찾는
        // 방식이었다면 강원처럼 시군구 행만 있는 시도는 전부 400이 된다.
        assertThat(regionRepository.existsBySidoCode("32")).isTrue();
    }

    @Test
    void 시군구가_없는_시도도_시도_존재_확인은_통과한다() {
        assertThat(regionRepository.existsBySidoCode("36")).isTrue();
    }

    @Test
    void 없는_시도_코드는_존재_확인에서_걸린다() {
        assertThat(regionRepository.existsBySidoCode("99")).isFalse();
    }

    @Test
    void 존재하지_않는_코드면_비어있다() {
        Optional<Region> found = regionRepository.findBySidoCodeAndSigunguCode("99", "99999");

        assertThat(found).isEmpty();
    }

    @Test
    void 시군구가_있는_시도에_null을_보내면_매칭되지_않는다() {
        Optional<Region> found = regionRepository.findBySidoCodeAndSigunguCode("32", null);

        assertThat(found).isEmpty();
    }
}
