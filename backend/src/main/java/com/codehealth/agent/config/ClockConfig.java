package com.codehealth.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Injectable clock. Production code never calls Instant.now() for decisions, so tests stay deterministic
 * (the same mistake this agent fixes in other people's code).
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
