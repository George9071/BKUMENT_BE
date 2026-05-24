package vn.edu.hcmut.social.utils;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import vn.edu.hcmut.social.exception.AppException;
import vn.edu.hcmut.social.exception.ErrorCode;

@Component
public final class SecurityUtils {
    public String getProfileId() {
        Jwt jwt = getJwt();
        String profileId = jwt.getClaimAsString("profile_id");
        if (profileId == null || profileId.isBlank()) {
            throw new AppException(ErrorCode.INVALID_TOKEN_CLAIMS);
        }
        return profileId;
    }

    public String getAccountId() {
        return getJwt().getSubject();
    }

    public void requireAdminOrModerator() {
        Authentication authentication = getAuthentication();

        boolean authorized = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN")
                        || authority.equals("ADMIN")
                        || authority.equals("SCOPE_ADMIN")
                        || authority.equals("ROLE_MODERATOR")
                        || authority.equals("MODERATOR")
                        || authority.equals("SCOPE_MODERATOR"));

        if (!authorized) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private Jwt getJwt() {
        Authentication authentication = getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    private Authentication getAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return authentication;
    }
}
