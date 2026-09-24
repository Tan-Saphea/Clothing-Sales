package com.clothing.app.security;

import com.clothing.app.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.service.OracleSessionContextService;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.UserAccount;

import java.util.Optional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = PermissionProbeController.class)
@Import(SecurityConfig.class)
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private OracleSessionContextService oracleSessionContextService;

    @BeforeEach
    void activeAccounts() {
        when(userAccountRepository.findByUsername("admin")).thenReturn(Optional.of(account("admin", true)));
        when(userAccountRepository.findByUsername("cashier")).thenReturn(Optional.of(account("cashier", true)));
        when(userAccountRepository.findByUsername("user")).thenReturn(Optional.of(account("user", true)));
    }

    @Test
    void anonymousBrowserRequestIsSentToLogin() throws Exception {
        mockMvc.perform(get("/sales"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void anonymousApiRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/sales"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cashierCanUsePointOfSaleAndCustomerFunctions() throws Exception {
        mockMvc.perform(get("/sales").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/customers").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/variants").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sales").with(user("cashier").roles("CASHIER")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void cashierCannotReachAdministrativeOrInventoryControls() throws Exception {
        mockMvc.perform(get("/dashboard").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/user-control").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products").with(user("cashier").roles("CASHIER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/variants").with(user("cashier").roles("CASHIER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/user-control/cashiers").with(user("cashier").roles("CASHIER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/customers/1").with(user("cashier").roles("CASHIER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanReachAdministrationAndInventoryControls() throws Exception {
        mockMvc.perform(get("/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/user-control").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/products").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/user-control/cashiers").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void everyWriteRequestRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/sales").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsupportedRoleCannotUseBusinessPages() throws Exception {
        mockMvc.perform(get("/sales").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountLosesExistingSessionAccessImmediately() throws Exception {
        when(userAccountRepository.findByUsername("cashier")).thenReturn(Optional.of(account("cashier", false)));

        mockMvc.perform(get("/sales").with(user("cashier").roles("CASHIER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?disabled"));
    }

    @Test
    void eachLoginRoleRoutesToItsAuthorizedWorkspace() throws Exception {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(customUserDetailsService.loadUserByUsername("admin")).thenReturn(
                User.withUsername("admin").password(encoder.encode("Admin@123")).roles("ADMIN").build());
        when(customUserDetailsService.loadUserByUsername("cashier")).thenAnswer(invocation ->
                User.withUsername("cashier").password(encoder.encode("Cashier@123")).roles("CASHIER").build());

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "admin").param("password", "Admin@123").param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "cashier").param("password", "Cashier@123").param("role", "CASHIER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sales"));
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "cashier").param("password", "Cashier@123").param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?roleError"));
    }

    private UserAccount account(String username, boolean enabled) {
        Employee employee = new Employee();
        employee.setStatus("ACTIVE");
        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setEnabled(enabled);
        account.setEmployee(employee);
        return account;
    }
}
