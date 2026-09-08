package com.freepets;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FreepetsServerApplication {

	// BaseEntity.createdAt/updatedAt이 LocalDateTime이라 타임존 개념이 없다 — JVM 기본
	// 타임존에 따라 LocalDateTime.now()가 무슨 시각을 담는지가 갈린다. 운영 환경은 우연히
	// UTC로 떠 있어서 값 자체는 맞았지만(그래서 API 응답도 실제로 UTC였다), 로컬 개발 환경은
	// OS 기본 타임존(KST 등)을 그대로 따라가 서버·API를 재실행할 때마다 값이 달라질 수 있었다.
	// 스프링 컨텍스트가 뜨기 전(정적 블록)에 명시적으로 고정해 모든 환경에서 항상 UTC로
	// 일치시킨다 — JacksonConfig가 이 값을 UTC로 간주하고 응답에 "Z"를 붙인다.
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(FreepetsServerApplication.class, args);
	}

}
