package com.clothing.app.config;

import com.clothing.app.entity.AppRole;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.UserAccount;
import com.clothing.app.entity.UserRole;
import com.clothing.app.entity.UserRoleId;
import com.clothing.app.repository.AppRoleRepository;
import com.clothing.app.repository.EmployeeRepository;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.UserRoleRepository;
import com.clothing.app.service.OracleSessionContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Component
@Order(2)
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "enabled", havingValue = "true")
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);

    private final AppRoleRepository appRoleRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OracleSessionContextService oracleSessionContextService;

    @Value("${app.bootstrap-admin.username:}")
    private String configuredUsername;

    @Value("${app.bootstrap-admin.password:}")
    private String configuredPassword;

    @Value("${app.bootstrap-admin.employee-name:System Administrator}")
    private String configuredEmployeeName;

    public AdminBootstrapInitializer(AppRoleRepository appRoleRepository,
                                     EmployeeRepository employeeRepository,
                                     UserAccountRepository userAccountRepository,
                                     UserRoleRepository userRoleRepository,
                                     PasswordEncoder passwordEncoder,
                                     OracleSessionContextService oracleSessionContextService) {
        this.appRoleRepository = appRoleRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = configuredUsername == null ? "" : configuredUsername.trim().toLowerCase(Locale.ROOT);
        String employeeName = configuredEmployeeName == null ? "" : configuredEmployeeName.trim();
        validate(username, configuredPassword, employeeName);
        var existingAccount = userAccountRepository.findByUsername(username);
        if (existingAccount.isPresent()) {
            boolean isAdministrator = userRoleRepository.findByUserAccount_UserId(
                            existingAccount.get().getUserId()).stream()
                    .anyMatch(role -> role.getAppRole() != null
                            && "ADMIN".equalsIgnoreCase(role.getAppRole().getRoleName()));
            if (!isAdministrator) {
                throw new IllegalStateException("Bootstrap username already belongs to a non-administrator account");
            }
            log.info("Bootstrap administrator '{}' already exists; no credentials were changed.", username);
            return;
        }

        oracleSessionContextService.applySystemUser("SYSTEM_BOOTSTRAP");
        AppRole adminRole = appRoleRepository.findByRoleName("ADMIN").orElseGet(() -> {
            AppRole role = new AppRole();
            role.setRoleName("ADMIN");
            role.setDescription("Administrator with full system permissions");
            return appRoleRepository.save(role);
        });

        Employee employee = new Employee();
        employee.setEmployeeName(employeeName);
        employee.setPosition("Administrator");
        employee.setHireDate(LocalDate.now());
        employee.setStatus("ACTIVE");
        Employee savedEmployee = employeeRepository.save(employee);

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(configuredPassword));
        account.setEnabled(true);
        account.setEmployee(savedEmployee);
        UserAccount savedAccount = userAccountRepository.save(account);

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(savedAccount.getUserId(), adminRole.getRoleId()));
        userRole.setUserAccount(savedAccount);
        userRole.setAppRole(adminRole);
        userRoleRepository.save(userRole);
        log.info("Bootstrap administrator '{}' created. Disable BOOTSTRAP_ADMIN_ENABLED after first startup.", username);
    }

    private void validate(String username, String password, String employeeName) {
        if (username.isBlank() || username.length() > 50) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME is required and must be 50 characters or fewer");
        }
        if (employeeName.isBlank() || employeeName.length() > 120) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_NAME is required and must be 120 characters or fewer");
        }
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches(".*[a-z].*")
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be 12-72 bytes with upper-case, lower-case, number, and symbol");
        }
    }
}
