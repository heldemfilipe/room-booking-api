package dev.heldem.reservas.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * O cache é uma otimização: se o Redis falhar, a requisição NÃO falha, segue para o banco (fail-open). Por padrão o Spring
 * propaga o erro do cache, e uma queda do Redis derrubaria a listagem de salas, que funciona perfeitamente sem ele.
 *
 * <p>Cada falha é registrada (log em WARN e o contador {@code reservas.cache.erros}, por operação), para a queda não
 * passar despercebida. Um custo: se o {@code evict} falhar, o valor antigo pode ficar no Redis até o TTL.
 */
class CacheTolerante implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(CacheTolerante.class);

    private final MeterRegistry metricas;

    CacheTolerante(MeterRegistry metricas) {
        this.metricas = metricas;
    }

    @Override
    public void handleCacheGetError(RuntimeException erro, Cache cache, Object chave) {
        registrar("get", cache, erro);
    }

    @Override
    public void handleCachePutError(RuntimeException erro, Cache cache, Object chave, Object valor) {
        registrar("put", cache, erro);
    }

    @Override
    public void handleCacheEvictError(RuntimeException erro, Cache cache, Object chave) {
        registrar("evict", cache, erro);
    }

    @Override
    public void handleCacheClearError(RuntimeException erro, Cache cache) {
        registrar("clear", cache, erro);
    }

    private void registrar(String operacao, Cache cache, RuntimeException erro) {
        metricas.counter("reservas.cache.erros", "cache", cache.getName(), "operacao", operacao).increment();
        log.warn("Cache '{}' indisponível ({}): {}. Seguindo pelo banco.", cache.getName(), operacao,
                erro.getClass().getSimpleName());
    }
}
