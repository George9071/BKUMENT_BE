package vn.edu.hcmut.lms.utils;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import vn.edu.hcmut.lms.exception.AppException;
import vn.edu.hcmut.lms.exception.ErrorCode;

@Component
public final class SecurityUtils {
    public String getProfileId() {
        return getJwt().getClaimAsString("profile_id");
    }

    public String getSafeProfileId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getPrincipal().equals("anonymousUser")) {
            return null;
        }

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("profile_id");
        }

        return null;
    }

    public String getAccountId() {
        return getJwt().getSubject();
    }

    private Jwt getJwt() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        if (auth.getPrincipal() instanceof Jwt jwt) return jwt;
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
