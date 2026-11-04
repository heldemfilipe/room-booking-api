package dev.heldem.reservas.cache;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import dev.heldem.reservas.sala.dto.SalaResponse;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cache da listagem de salas em Redis (Etapa 4). Só as salas: as reservas NÃO são cacheadas (veja o README).
 *
 * <ul>
 *   <li><b>TTL:</b> {@code app.cache.salas-ttl} (5 minutos). É o limite de quanto tempo uma alteração feita FORA da API
 *       (direto no banco) demora para aparecer.</li>
 *   <li><b>Valores em JSON tipado</b> ({@code List<SalaResponse>}): sem nome de classe dentro do JSON, então nada é
 *       instanciado a partir do que está no Redis além desse tipo, e o valor é legível no redis-cli.</li>
 *   <li><b>Transacional:</b> {@code transactionAware()} faz o {@code @CacheEvict} e o {@code @Cacheable} acontecerem
 *       DEPOIS do commit. Sem isso, o evict roda antes do commit, um leitor concorrente relê o valor antigo do banco e
 *       o devolve ao cache: o dado velho fica lá até o TTL.</li>
 *   <li><b>Writer com lock:</b> {@code @Cacheable(sync = true)} só evita o estouro de cache (várias requisições
 *       recalculando a mesma chave) se o writer travar de verdade. O padrão ({@code nonLocking}) não trava: medido, 50
 *       requisições juntas sobre o cache vazio fizeram de 5 a 26 consultas ao banco, com ou sem {@code sync}. O
 *       {@code lockingRedisCacheWriter} usa um lock no próprio Redis (vale entre instâncias da API): 1 consulta.</li>
 *   <li><b>Estatísticas ligadas:</b> o Micrometer expõe {@code cache.gets} (hit e miss), {@code cache.puts} etc.</li>
 *   <li><b>O cache é uma otimização, não uma dependência:</b> se o Redis cair, a API segue servindo do banco
 *       ({@link CacheTolerante}).</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String SALAS = "salas";

    private final MeterRegistry metricas;

    CacheConfig(MeterRegistry metricas) {
        this.metricas = metricas;
    }

    @Bean
    CacheManager cacheManager(RedisConnectionFactory conexao, JsonMapper json,
            @Value("${app.cache.salas-ttl}") Duration ttl) {
        JavaType listaDeSalas = json.getTypeFactory().constructCollectionType(java.util.List.class, SalaResponse.class);
        RedisCacheConfiguration salas = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("reservas:") // as chaves ficam como reservas:salas::SimpleKey []
                .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(json, listaDeSalas)));
        return RedisCacheManager.builder(RedisCacheWriter.lockingRedisCacheWriter(conexao))
                .cacheDefaults(salas)
                .withInitialCacheConfigurations(Map.of(SALAS, salas))
                .transactionAware()
                .enableStatistics()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheTolerante(metricas);
    }
}
