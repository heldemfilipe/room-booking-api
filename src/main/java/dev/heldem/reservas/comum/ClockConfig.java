package dev.heldem.reservas.comum;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfig {

    // um relógio injetável deixa a data de criação previsível nos testes (relógio fixo)
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
