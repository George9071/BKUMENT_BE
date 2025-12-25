package vn.edu.hcmut.identity.converter;

import java.util.*;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class CustomJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public @NonNull Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. "privilege" claim -> action authorities
        Object privilegeClaim = jwt.getClaim("privilege");
        if (privilegeClaim instanceof String privilege) {
            Arrays.stream(privilege.split(" ")) // Split by spaces
                    .map(String::trim) // Remove extra whitespace
                    .filter(s -> !s.isEmpty()) // Ignore empty strings
                    .map(SimpleGrantedAuthority::new) // Convert to GrantedAuthority
                    .forEach(authorities::add); // Add to the authority set
        }

        // 2. "scope" claim → role-based authorities ("ADMIN" → [ROLE_ADMIN])
        Object scopeClaim = jwt.getClaim("scope");
        if (scopeClaim instanceof String scope) {
            Arrays.stream(scope.split(" "))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(role -> "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        return authorities;
    }
}
