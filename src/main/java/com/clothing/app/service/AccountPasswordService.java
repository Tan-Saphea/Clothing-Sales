package com.clothing.app.service;

import com.clothing.app.entity.UserAccount;
import com.clothing.app.repository.UserAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AccountPasswordService {
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final OracleSessionContextService oracleSessionContextService;

    public AccountPasswordService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
                                  OracleSessionContextService oracleSessionContextService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    @Transactional
    public void changeOwnPassword(Authentication authentication, String currentPassword, String newPassword) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new IllegalArgumentException("Current password is required");
        }
        validatePassword(newPassword);
        oracleSessionContextService.applyCurrentUser();
        UserAccount account = userAccountRepository.findByUsername(
                        authentication.getName().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("Account is not available"));
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(account);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*")
                || !password.matches(".*\\d.*") || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException(
                    "Password must be 12-72 bytes and include upper-case, lower-case, number, and symbol");
        }
    }
}
