package vn.edu.hcmut.identity.converter;

import java.util.*;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * Custom converter to extract authorities (privileges and roles) from a JWT token.
 * It maps custom claims ("privilege" and "scope") into Spring Security's GrantedAuthority objects.
 */
public class CustomJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    // Standard Spring Security role prefix
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public @NonNull Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // "privilege" claim (e.g., "READ_CLASS WRITE_CLASS") -> Action-based authorities
        String privileges = jwt.getClaimAsString("privilege");

        if (StringUtils.hasText(privileges)) {
            // Split by one or more whitespace characters ("\\s+") to handle accidental double spaces
            Arrays.stream(privileges.split("\\s+"))
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        // "scope" claim (e.g., "TUTOR USER") -> Role-based authorities
        String scopes = jwt.getClaimAsString("scope");

        if (StringUtils.hasText(scopes)) {
            Arrays.stream(scopes.split("\\s+"))
                    // Prefix with "ROLE_" and convert to uppercase to strictly follow Spring Security conventions
                    .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                    .forEach(authorities::add);
        }

        return authorities;
    }
}
