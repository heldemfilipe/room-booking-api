package dev.heldem.reservas.sala;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.heldem.reservas.sala.dto.SalaRequest;
import dev.heldem.reservas.sala.dto.SalaResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/salas")
public class SalaController {

    private final SalaService service;

    public SalaController(SalaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SalaResponse> criar(@Valid @RequestBody SalaRequest request) {
        SalaResponse criada = SalaResponse.de(service.criar(request));
        URI local = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(local).body(criada);
    }

    @GetMapping
    public List<SalaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public SalaResponse buscar(@PathVariable UUID id) {
        return SalaResponse.de(service.buscar(id));
    }

    @PutMapping("/{id}")
    public SalaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody SalaRequest request) {
        return SalaResponse.de(service.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        service.remover(id);
        return ResponseEntity.noContent().build();
    }
}
