package com.homefit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Single Spring Boot application (Phase 1). Its functional modules — core, ingestion, api —
 * are Spring Modulith application modules verified by {@code ModularityTests}.
 * Run as {@code --spring.profiles.active=api} (default) or {@code =worker}.
 */
@Modulithic(systemName = "HomeFit")
@SpringBootApplication
@EnableScheduling
public class HomefitApplication {
    public static void main(String[] args) {
        SpringApplication.run(HomefitApplication.class, args);
    }
}
