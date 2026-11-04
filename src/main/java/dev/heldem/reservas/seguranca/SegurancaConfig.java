package dev.heldem.reservas.seguranca;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * API stateless protegida por JWT (resource server). Regras:
 * <ul>
 *   <li>saúde ({@code /actuator/health}) é pública, para o orquestrador;</li>
 *   <li>ler salas: qualquer autenticado; escrever salas: só ADMIN;</li>
 *   <li>reservas: qualquer autenticado cria e lista; cancelar exige ser o dono (regra no service, que compara o dono
 *       com o {@code sub} do token);</li>
 *   <li>todo o resto é negado: o que não foi liberado de propósito não é servido.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
class SegurancaConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) {
        var respostas = new RespostasDeSeguranca();
        http
                // CSRF desligado SÓ porque a API é stateless e autentica por token no cabeçalho Authorization: não há
                // cookie de sessão que um site malicioso possa fazer o navegador enviar sozinho. Com cookie, não seria seguro.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/salas", "/salas/**").authenticated()
                        .requestMatchers("/salas", "/salas/**").hasRole("ADMIN")
                        .requestMatchers("/reservas", "/reservas/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeJwt()))
                        .authenticationEntryPoint(respostas)
                        .accessDeniedHandler(respostas))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(respostas)
                        .accessDeniedHandler(respostas));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter conversorDeJwt() {
        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter());
        return conversor;
    }

    /** CORS explícito: só as origens configuradas, só os métodos e cabeçalhos que a API usa, sem credenciais. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origens) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origens.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        var origem = new UrlBasedCorsConfigurationSource();
        origem.registerCorsConfiguration("/**", cors);
        return origem;
    }
}
