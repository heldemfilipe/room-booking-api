package dev.heldem.reservas;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	// Um só PostgreSQL para a JVM de testes inteira, em vez de um por contexto do Spring: os contextos que diferem em
	// alguma propriedade subiam cada um o seu contêiner. O Ryuk (do Testcontainers) o derruba quando a JVM termina.
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	// Idem para o Redis do cache (mesma imagem do docker-compose.yml).
	private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.3-alpine"))
			.withExposedPorts(6379);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return REDIS;
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}

}
