package com.codehealth.agent;

import com.codehealth.agent.config.CodeHealthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CodeHealthProperties.class)
public class CodeHealthAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeHealthAgentApplication.class, args);
    }
}
