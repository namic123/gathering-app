package com.moim.moimbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 애플리케이션 진입점.
 *
 * @EnableScheduling: @Scheduled 어노테이션이 붙은 메서드를 주기적으로 실행.
 * 이 어노테이션이 없으면 DeadlineScheduler가 동작하지 않음.
 * Spring이 내부적으로 TaskScheduler 스레드를 생성하여 관리.
 */
@SpringBootApplication
@EnableScheduling
public class MoimBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoimBackendApplication.class, args);
    }

}
