package com.freepets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class FreepetsServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FreepetsServerApplication.class, args);
	}

}
