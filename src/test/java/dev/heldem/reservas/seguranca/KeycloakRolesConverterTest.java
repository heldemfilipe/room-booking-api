package dev.heldem.reservas.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRolesConverterTest {

    private final KeycloakRolesConverter conversor = new KeycloakRolesConverter();

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("ana");
    }

    private List<String> autoridades(Jwt jwt) {
        return conversor.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void cadaPapelDoRealmViraRolePrefixado() {
        var jwt = token().claim("realm_access", Map.of("roles", List.of("USER", "ADMIN"))).build();

        assertThat(autoridades(jwt)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void semOClaimRealmAccessNaoHaPapelNenhum() {
        assertThat(autoridades(token().build())).isEmpty();
    }

    @Test
    void claimComFormatoInesperadoNuncaViraPapel() {
        assertThat(autoridades(token().claim("realm_access", "ADMIN").build())).isEmpty();
        assertThat(autoridades(token().claim("realm_access", Map.of("roles", "ADMIN")).build())).isEmpty();
        assertThat(autoridades(token().claim("realm_access", Map.of("outro", List.of("ADMIN"))).build())).isEmpty();
    }

    @Test
    void itensQueNaoSaoTextoSaoIgnorados() {
        var jwt = token().claim("realm_access", Map.of("roles", List.of("USER", 42, true))).build();

        assertThat(autoridades(jwt)).containsExactly("ROLE_USER");
    }

    @Test
    void papeisDeClienteEOScopeNaoViramAutoridade() {
        // só realm_access conta; resource_access (papéis por cliente) e scope ficam de fora
        var jwt = token()
                .claim("resource_access", Map.of("reservas-cli", Map.of("roles", List.of("ADMIN"))))
                .claim("scope", "ADMIN")
                .build();

        assertThat(autoridades(jwt)).isEmpty();
    }
}
