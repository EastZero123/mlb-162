package com.mlbgame;

import static org.assertj.core.api.Assertions.assertThat;

import com.mlbgame.auth.User;
import com.mlbgame.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 MySQL 8 에서 Flyway 마이그레이션 + JPA 스키마 검증(ddl-auto=validate)이 통과하는지 확인.
 * Docker 가 실행 중이 아니면 자동으로 건너뛴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @Autowired
    UserRepository userRepository;

    @Test
    void MySQL8에서_마이그레이션과_엔티티_매핑이_일치한다() {
        User saved = userRepository.save(new User("mysqluser", "{bcrypt}hash", "한글닉네임"));

        assertThat(userRepository.findByLoginId("mysqluser"))
                .get()
                .extracting(User::getNickname)
                .isEqualTo("한글닉네임");
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
