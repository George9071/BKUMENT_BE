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

    public String getPrimaryAdminRole() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        boolean isAdmin = false;
        boolean isModerator = false;

        for (var authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ADMIN".equals(role)) isAdmin = true;
            else if ("ROLE_MODERATOR".equals(role) || "MODERATOR".equals(role)) isModerator = true;
        }

        if (isAdmin) return "ADMIN";
        if (isModerator) return "MODERATOR";

        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
