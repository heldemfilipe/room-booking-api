package dev.heldem.reservas.reserva;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.heldem.reservas.comum.Pagina;
import dev.heldem.reservas.reserva.dto.CriarReservaRequest;
import dev.heldem.reservas.reserva.dto.ReservaResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/reservas")
public class ReservaController {

    private final ReservaService service;
    private final MetricasDeReservas metricas;

    public ReservaController(ReservaService service, MetricasDeReservas metricas) {
        this.service = service;
        this.metricas = metricas;
    }

    // O dono é o "sub" do token validado, nunca algo que o cliente escreve no corpo.
    // Idempotency-Key (opcional): repetir a mesma chave com o mesmo corpo devolve a MESMA resposta (201 e a mesma
    // reserva), com o cabeçalho Idempotency-Replayed: true, sem criar outra reserva.
    @PostMapping
    public ResponseEntity<ReservaResponse> criar(@AuthenticationPrincipal Jwt token,
            @RequestHeader(value = "Idempotency-Key", required = false) String chave,
            @Valid @RequestBody CriarReservaRequest request) {
        var resultado = service.criar(token.getSubject(), chave, request);
        if (!resultado.repetida()) {
            metricas.criada();
        }
        ReservaResponse criada = resultado.reserva();
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criada.id()).toUri();
        var resposta = ResponseEntity.created(local);
        if (resultado.repetida()) {
            resposta.header("Idempotency-Replayed", "true");
        }
        return resposta.body(criada);
    }

    // existe para o Location do POST apontar para algo que responde
    @GetMapping("/{id}")
    public ReservaResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    // Reservas que se sobrepõem a [de, ate). Datas em ISO-8601 com Z (um "+" no fuso precisa ser escapado na URL).
    @GetMapping
    public Pagina<ReservaResponse> listar(
            @RequestParam(required = false) UUID salaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.listar(salaId, de, ate, pagina, tamanho);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id, @AuthenticationPrincipal Jwt token) {
        service.cancelar(id, token.getSubject());
        return ResponseEntity.noContent().build();
    }
}
