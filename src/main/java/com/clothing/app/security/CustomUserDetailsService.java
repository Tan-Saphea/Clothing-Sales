package com.clothing.app.security;

import com.clothing.app.entity.UserAccount;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.UserRoleRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final LoginAttemptService loginAttemptService;

    public CustomUserDetailsService(UserAccountRepository userAccountRepository,
                                   UserRoleRepository userRoleRepository) {
        this(userAccountRepository, userRoleRepository, new LoginAttemptService());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CustomUserDetailsService(UserAccountRepository userAccountRepository,
                                   UserRoleRepository userRoleRepository,
                                   LoginAttemptService loginAttemptService) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

        if (loginAttemptService != null && loginAttemptService.isBlocked(normalizedUsername)) {
            throw new org.springframework.security.authentication.LockedException(
                    "Account is temporarily locked due to multiple failed login attempts. Please try again later.");
        }

        UserAccount userAccount = userAccountRepository.findByUsername(normalizedUsername)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalizedUsername));

        if (userAccount.getEnabled() == null || !userAccount.getEnabled()) {
            throw new org.springframework.security.authentication.DisabledException("User account is disabled: " + normalizedUsername);
        }
        if (userAccount.getEmployee() == null
                || !"ACTIVE".equalsIgnoreCase(userAccount.getEmployee().getStatus())) {
            throw new org.springframework.security.authentication.DisabledException("Employee account is inactive: " + normalizedUsername);
        }

        List<GrantedAuthority> authorities = userRoleRepository.findByUserAccount_UserId(userAccount.getUserId())
            .stream()
            .map(userRole -> userRole.getAppRole())
            .filter(Objects::nonNull)
            .map(role -> role.getRoleName() == null ? "" : role.getRoleName().trim().toUpperCase(Locale.ROOT))
            .filter(roleName -> roleName.equals("ADMIN") || roleName.equals("CASHIER"))
            .distinct()
            .map(roleName -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + roleName))
            .collect(java.util.stream.Collectors.toList());

        if (authorities.isEmpty()) {
            throw new UsernameNotFoundException("User has no supported role: " + normalizedUsername);
        }

        return new User(
            userAccount.getUsername(),
            userAccount.getPasswordHash(),
            authorities
        );
    }
}
