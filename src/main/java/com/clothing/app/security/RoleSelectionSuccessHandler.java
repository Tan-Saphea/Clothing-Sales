package com.clothing.app.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.io.IOException;
import java.util.Locale;
import java.time.LocalDateTime;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.service.OracleSessionContextService;
import org.springframework.transaction.annotation.Transactional;

public class RoleSelectionSuccessHandler implements AuthenticationSuccessHandler {

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    private final UserAccountRepository userAccountRepository;
    private final OracleSessionContextService oracleSessionContextService;

    public RoleSelectionSuccessHandler(UserAccountRepository userAccountRepository,
                                       OracleSessionContextService oracleSessionContextService) {
        this.userAccountRepository = userAccountRepository;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String requestedRole = request.getParameter("role");
        if (requestedRole == null || requestedRole.isBlank()) {
            rejectLogin(request, response);
            return;
        }

        String expectedAuthority = "ROLE_" + requestedRole.trim().toUpperCase(Locale.ROOT);
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(expectedAuthority::equals);

        if (allowed) {
            oracleSessionContextService.applyCurrentUser();
            userAccountRepository.findByUsername(authentication.getName().trim().toLowerCase(Locale.ROOT))
                    .ifPresent(account -> {
                        account.setLastLogin(LocalDateTime.now());
                        userAccountRepository.save(account);
                    });
            UsernamePasswordAuthenticationToken selectedAuthentication =
                    new UsernamePasswordAuthenticationToken(
                            authentication.getPrincipal(),
                            authentication.getCredentials(),
                            java.util.List.of(new SimpleGrantedAuthority(expectedAuthority)));
            selectedAuthentication.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(selectedAuthentication);
            securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
            request.getSession(true).setAttribute("selectedRole", expectedAuthority);
            response.sendRedirect("ROLE_CASHIER".equals(expectedAuthority) ? "/sales" : "/dashboard");
            return;
        }

        rejectLogin(request, response);
    }

    private void rejectLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect("/login?roleError");
    }
}
