package com.freepets.global.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 거부 제보 알림 발송(DenialReportNotificationService)을 API 응답과 분리해서 처리하는 데 쓴다.
@Configuration
@EnableAsync
public class AsyncConfig {

    // Spring 기본(SimpleAsyncTaskExecutor)은 풀링을 안 하고 호출마다 스레드를 새로 만든다 —
    // 알림 발송 전용 풀을 따로 둬서 @Async("notificationExecutor")로 명시적으로 구분해 쓴다.
    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }
}
