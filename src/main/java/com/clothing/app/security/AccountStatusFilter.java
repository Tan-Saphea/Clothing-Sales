package com.clothing.app.security;

import com.clothing.app.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserAccountRepository userAccountRepository;

    public AccountStatusFilter(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/favicon.")
                || path.startsWith("/uploads/")
                || path.startsWith("/webjars/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            boolean active = userAccountRepository
                    .findByUsername(authentication.getName().trim().toLowerCase(Locale.ROOT))
                    .filter(account -> Boolean.TRUE.equals(account.getEnabled()))
                    .filter(account -> account.getEmployee() == null
                            || "ACTIVE".equalsIgnoreCase(account.getEmployee().getStatus()))
                    .isPresent();
            if (!active) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                if (request.getRequestURI().startsWith("/api/")) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account is disabled or inactive");
                } else {
                    response.sendRedirect(request.getContextPath() + "/login?disabled");
                }
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
