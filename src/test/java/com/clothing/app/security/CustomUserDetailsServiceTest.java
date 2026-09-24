package com.clothing.app.security;

import com.clothing.app.entity.AppRole;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.UserAccount;
import com.clothing.app.entity.UserRole;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    @Test
    void loadsEnabledAdministratorWithoutDependingOnARealDatabasePassword() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        Employee employee = new Employee();
        employee.setStatus("ACTIVE");
        UserAccount account = new UserAccount();
        account.setUserId(1L);
        account.setUsername("admin");
        account.setPasswordHash(encoder.encode("test-password"));
        account.setEnabled(true);
        account.setEmployee(employee);
        AppRole adminRole = new AppRole();
        adminRole.setRoleName("ADMIN");
        UserRole userRole = new UserRole();
        userRole.setAppRole(adminRole);

        when(accounts.findByUsername("admin")).thenReturn(Optional.of(account));
        when(roles.findByUserAccount_UserId(1L)).thenReturn(List.of(userRole));

        var user = new CustomUserDetailsService(accounts, roles).loadUserByUsername("ADMIN");

        assertEquals("admin", user.getUsername());
        assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void throwsDisabledExceptionWhenAccountDisabled() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);

        Employee employee = new Employee();
        employee.setStatus("ACTIVE");
        UserAccount account = new UserAccount();
        account.setUserId(2L);
        account.setUsername("disabled_user");
        account.setEnabled(false);
        account.setEmployee(employee);

        when(accounts.findByUsername("disabled_user")).thenReturn(Optional.of(account));

        var service = new CustomUserDetailsService(accounts, roles);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.authentication.DisabledException.class,
                () -> service.loadUserByUsername("disabled_user")
        );
    }

    @Test
    void throwsDisabledExceptionWhenEmployeeInactive() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);

        Employee employee = new Employee();
        employee.setStatus("INACTIVE");
        UserAccount account = new UserAccount();
        account.setUserId(3L);
        account.setUsername("inactive_emp");
        account.setEnabled(true);
        account.setEmployee(employee);

        when(accounts.findByUsername("inactive_emp")).thenReturn(Optional.of(account));

        var service = new CustomUserDetailsService(accounts, roles);
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.security.authentication.DisabledException.class,
                () -> service.loadUserByUsername("inactive_emp")
        );
    }
}
