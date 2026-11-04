package dev.heldem.reservas.diagnostico;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.heldem.reservas.comum.EntradaInvalidaException;

/**
 * Endpoint de teste para o experimento de observabilidade (Etapa 5): injeta lentidão NO BANCO com {@code pg_sleep}, para
 * seguir o problema do dashboard (latência p95 sobe, alerta dispara) até o span da query no trace.
 *
 * <p><b>Desligado por padrão.</b> Só existe com {@code app.diagnostico.habilitado=true}, e mesmo assim só o ADMIN acessa
 * (SegurancaConfig). Um endpoint que dorme à vontade seria uma arma de negação de serviço em produção.
 */
@RestController
@RequestMapping("/diagnostico")
@ConditionalOnProperty(name = "app.diagnostico.habilitado", havingValue = "true")
class DiagnosticoController {

    private static final double MAXIMO_EM_SEGUNDOS = 10;

    private final JdbcTemplate jdbc;

    DiagnosticoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/lento")
    Map<String, Object> lento(@RequestParam(defaultValue = "1") double segundos) {
        if (segundos < 0 || segundos > MAXIMO_EM_SEGUNDOS) {
            throw new EntradaInvalidaException("segundos deve estar entre 0 e " + (int) MAXIMO_EM_SEGUNDOS + ".");
        }
        jdbc.queryForObject("select pg_sleep(?) is null", Boolean.class, segundos);
        return Map.of("dormiuSegundos", segundos);
    }
}
