package com.mallota;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MallotaBackendApplication {

    public static void main(String[] args) {
        // 1. .env 파일의 환경변수를 읽어서 시스템 프로퍼티로 자동 등록
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        } catch (Exception ignored) {
            // .env 파일이 없어도 서버가 정상 구동되도록 예외 무시
        }

        // 2. Spring Boot 애플리케이션 실행
        SpringApplication.run(MallotaBackendApplication.class, args);
    }

}