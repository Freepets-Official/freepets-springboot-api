package com.freepets.domain.petcheck.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.freepets.domain.petcheck.entity.PetCheckVerdict;

public interface PetCheckVerdictRepository extends JpaRepository<PetCheckVerdict, Long> {

    // GET /verify/{code} — 아이 1마리분 판별 결과 조회. verify_code는 unique라 최대 1건이다.
    // petCheck·facility·pet이 전부 지연 로딩이라 JOIN FETCH 없이 쓰면 렌더링 한 번에 최대
    // 4번(자신+petCheck+facility+pet) 왕복한다 — 인증 없는 공개 페이지라 스캔이 몰리면 그대로
    // DB 부하가 된다. pet은 삭제돼 없을 수 있어 LEFT JOIN FETCH를 쓴다.
    @Query("""
            SELECT v FROM PetCheckVerdict v
            JOIN FETCH v.petCheck pc
            JOIN FETCH pc.facility
            LEFT JOIN FETCH v.pet
            WHERE v.verifyCode = :verifyCode
            """)
    Optional<PetCheckVerdict> findByVerifyCode(@Param("verifyCode") String verifyCode);
}
