package dev.heldem.reservas.seguranca;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * O Keycloak põe os papéis do realm no claim {@code realm_access.roles} (por exemplo {@code ["USER"]}). O Spring
 * Security, por padrão, só lê o claim {@code scope}; sem este conversor, nenhuma regra por papel funcionaria.
 * Cada papel vira a autoridade {@code ROLE_<papel>}, que é o que {@code hasRole("ADMIN")} procura.
 */
public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ACESSO_DO_REALM = "realm_access";
    private static final String PAPEIS = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // um token sem o claim (ou com formato inesperado) simplesmente não tem papel nenhum: nunca vira ADMIN por engano
        if (!(jwt.getClaim(ACESSO_DO_REALM) instanceof Map<?, ?> acesso)
                || !(acesso.get(PAPEIS) instanceof List<?> papeis)) {
            return List.of();
        }
        return papeis.stream()
                .filter(String.class::isInstance)
                .map(papel -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + papel))
                .toList();
    }
}
