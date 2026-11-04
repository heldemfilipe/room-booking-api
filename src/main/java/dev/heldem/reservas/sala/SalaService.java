package dev.heldem.reservas.sala;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.heldem.reservas.cache.CacheConfig;
import dev.heldem.reservas.comum.ConflitoException;
import dev.heldem.reservas.comum.Datas;
import dev.heldem.reservas.comum.RecursoNaoEncontradoException;
import dev.heldem.reservas.reserva.ReservaRepository;
import dev.heldem.reservas.sala.dto.SalaRequest;
import dev.heldem.reservas.sala.dto.SalaResponse;

@Service
public class SalaService {

    private final SalaRepository repository;
    private final ReservaRepository reservaRepository;
    private final Clock clock;

    public SalaService(SalaRepository repository, ReservaRepository reservaRepository, Clock clock) {
        this.repository = repository;
        this.reservaRepository = reservaRepository;
        this.clock = clock;
    }

    @CacheEvict(cacheNames = CacheConfig.SALAS, allEntries = true)
    @Transactional
    public Sala criar(SalaRequest request) {
        String nome = request.nome().strip();
        if (repository.existsByNomeIgnoreCase(nome)) {
            throw nomeJaExiste(nome);
        }
        try {
            // saveAndFlush: a violação do índice único precisa estourar aqui, dentro do try
            return repository.saveAndFlush(new Sala(nome, request.capacidade(), Datas.agora(clock)));
        } catch (DataIntegrityViolationException ex) {
            // duas requisições simultâneas passam pela consulta acima; só o banco barra a segunda
            throw nomeJaExiste(nome);
        }
    }

    /**
     * A listagem de salas vem do cache (Redis, TTL de 5 minutos). Guarda o DTO, e não a entidade JPA. As escritas abaixo
     * esvaziam o cache; uma alteração feita direto no banco só aparece quando o TTL vence. As reservas não são cacheadas.
     */
    @Cacheable(cacheNames = CacheConfig.SALAS, sync = true)
    @Transactional(readOnly = true)
    public List<SalaResponse> listar() {
        return repository.findAll(Sort.by("nome", "id")).stream().map(SalaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public Sala buscar(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala " + id + " não encontrada."));
    }

    /** A sala com a linha trancada até o fim da transação: usada por quem cria reserva (veja SalaRepository). */
    @Transactional
    public Sala buscarParaReservar(UUID id) {
        return repository.buscarParaReservar(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sala " + id + " não encontrada."));
    }

    @CacheEvict(cacheNames = CacheConfig.SALAS, allEntries = true)
    @Transactional
    public Sala atualizar(UUID id, SalaRequest request) {
        Sala sala = buscar(id);
        String nome = request.nome().strip();
        if (repository.existsByNomeIgnoreCaseAndIdNot(nome, id)) {
            throw nomeJaExiste(nome);
        }
        sala.atualizar(nome, request.capacidade());
        try {
            return repository.saveAndFlush(sala);
        } catch (DataIntegrityViolationException ex) {
            throw nomeJaExiste(nome);
        }
    }

    @CacheEvict(cacheNames = CacheConfig.SALAS, allEntries = true)
    @Transactional
    public void remover(UUID id) {
        Sala sala = buscar(id);
        // a chave estrangeira também barraria, mas com um erro de banco; assim a resposta é um 409 claro
        if (reservaRepository.existsBySalaId(id)) {
            throw new ConflitoException("A sala " + id + " tem reservas e não pode ser removida.");
        }
        repository.delete(sala);
    }

    private static ConflitoException nomeJaExiste(String nome) {
        return new ConflitoException("Já existe uma sala chamada '" + nome + "'.");
    }
}
