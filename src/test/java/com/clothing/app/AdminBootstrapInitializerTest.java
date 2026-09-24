package com.clothing.app;

import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstrap_test;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "app.demo-data.enabled=false",
        "app.schema-migration.enabled=false",
        "app.bootstrap-admin.enabled=true",
        "app.bootstrap-admin.username=first_admin",
        "app.bootstrap-admin.password=TestOnly-Bootstrap-Password-2026!",
        "app.bootstrap-admin.employee-name=First Administrator"
})
class AdminBootstrapInitializerTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void provisionsAStrongEnabledAdministratorWithoutDemoData() {
        var account = userAccountRepository.findByUsername("first_admin").orElseThrow();
        assertTrue(account.getEnabled());
        assertTrue(passwordEncoder.matches("TestOnly-Bootstrap-Password-2026!", account.getPasswordHash()));
        assertTrue(userRoleRepository.findByUserAccount_UserId(account.getUserId()).stream()
                .anyMatch(role -> "ADMIN".equals(role.getAppRole().getRoleName())));
    }
}
