package dev.heldem.reservas;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Tokens JWT de verdade para os testes, sem Keycloak: assinados com uma chave RSA gerada na hora, com o mesmo formato do
 * Keycloak (o papel vem em {@code realm_access.roles}). O decoder de teste valida assinatura, validade e emissor, como o
 * de produção; só a origem das chaves é outra. Assim os testes de 401 e 403 passam por toda a cadeia de filtros e pelo
 * conversor de papéis, em vez de pular a autenticação com um atalho.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TokensDeTeste {

    /** Igual ao issuer-uri do application.properties: um token de outro emissor tem que ser recusado. */
    public static final String EMISSOR = "http://localhost:8180/realms/reservas";

    private static final KeyPair CHAVES = novaChave();
    private static final KeyPair CHAVES_DE_OUTRO_EMISSOR = novaChave();

    @Bean
    @Primary
    JwtDecoder jwtDecoderDeTeste() {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) CHAVES.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMISSOR));
        return decoder;
    }

    /** Token válido por 5 minutos, do emissor certo, com os papéis informados. */
    public static String token(String sub, String... papeis) {
        return gerar(CHAVES, EMISSOR, Instant.now().plus(Duration.ofMinutes(5)), sub, papeis);
    }

    /** Venceu há 10 minutos (além da tolerância de relógio de 60 s do Spring Security). */
    public static String tokenExpirado(String sub, String... papeis) {
        return gerar(CHAVES, EMISSOR, Instant.now().minus(Duration.ofMinutes(10)), sub, papeis);
    }

    public static String tokenDeOutroEmissor(String sub, String... papeis) {
        return gerar(CHAVES, "http://outro-emissor/realms/reservas", Instant.now().plus(Duration.ofMinutes(5)), sub,
                papeis);
    }

    /** Emissor certo, mas assinado por uma chave que a API não conhece. */
    public static String tokenAssinadoPorOutraChave(String sub, String... papeis) {
        return gerar(CHAVES_DE_OUTRO_EMISSOR, EMISSOR, Instant.now().plus(Duration.ofMinutes(5)), sub, papeis);
    }

    /** Token sem o claim realm_access, como o de um usuário sem nenhum papel. */
    public static String tokenSemPapeis(String sub) {
        return montar(CHAVES, new JWTClaimsSet.Builder().subject(sub).issuer(EMISSOR)
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(5)))).build());
    }

    private static String gerar(KeyPair chaves, String emissor, Instant expira, String sub, String... papeis) {
        var claims = new JWTClaimsSet.Builder()
                .subject(sub)
                .issuer(emissor)
                .issueTime(new Date())
                .expirationTime(Date.from(expira))
                .claim("realm_access", Map.of("roles", List.of(papeis)))
                .build();
        return montar(chaves, claims);
    }

    private static String montar(KeyPair chaves, JWTClaimsSet claims) {
        try {
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) chaves.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Não foi possível assinar o token de teste", ex);
        }
    }

    private static KeyPair novaChave() {
        try {
            var gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            return gerador.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
