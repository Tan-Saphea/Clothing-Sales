package com.clothing.app.security;

import com.clothing.app.config.SecurityConfig;
import com.clothing.app.exception.GlobalExceptionHandler;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.service.OracleSessionContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clothing.app.controller.AuthController;

@WebMvcTest(controllers = {PermissionProbeController.class, AuthController.class})
@Import(SecurityConfig.class)
class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private OracleSessionContextService oracleSessionContextService;

    @Test
    void responseIncludesStrictSecurityHeaders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(self), microphone=(), geolocation=(), payment=()"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
    }

    @Test
    void loginAttemptServiceBlocksAfterMaxFailedAttempts() {
        LoginAttemptService service = new LoginAttemptService(3, 15);
        String username = "attacker_target";

        assertFalse(service.isBlocked(username));

        service.loginFailed(username);
        service.loginFailed(username);
        assertFalse(service.isBlocked(username));

        service.loginFailed(username);
        assertTrue(service.isBlocked(username));
        assertTrue(service.getRemainingLockoutSeconds(username) > 0);

        service.loginSucceeded(username);
        assertFalse(service.isBlocked(username));
    }

    @Test
    void customUserDetailsServiceThrowsLockedExceptionWhenBlocked() {
        LoginAttemptService loginAttemptService = new LoginAttemptService(2, 15);
        loginAttemptService.loginFailed("cashier");
        loginAttemptService.loginFailed("cashier");

        CustomUserDetailsService detailsService = new CustomUserDetailsService(
                userAccountRepository, null, loginAttemptService);

        assertThrows(LockedException.class, () -> detailsService.loadUserByUsername("cashier"));
    }

    @Test
    void globalExceptionHandlerSanitizesInternalOracleErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // 1. Custom business error (ORA-20xxx) is exposed to the user
        DataIntegrityViolationException businessEx = new DataIntegrityViolationException(
                "ORA-20101: Purchase quantity must be greater than zero.\nORA-06512: at line 4");
        ResponseEntity<Map<String, Object>> businessResp = handler.handleDataAccessException(businessEx);
        assertEquals(400, businessResp.getStatusCode().value());
        assertEquals("Purchase quantity must be greater than zero.", businessResp.getBody().get("message"));

        // 2. Internal schema/system error (ORA-00942) is NOT leaked to the user
        DataIntegrityViolationException internalEx = new DataIntegrityViolationException(
                "ORA-00942: table or view does not exist\nORA-06512: at line 12");
        ResponseEntity<Map<String, Object>> internalResp = handler.handleDataAccessException(internalEx);
        assertEquals(400, internalResp.getStatusCode().value());
        assertEquals("Database operation failed. Please verify parameters and try again.", internalResp.getBody().get("message"));

        // 3. Generic unhandled exception does not disclose details
        ResponseEntity<Map<String, Object>> genericResp = handler.handleGenericException(new RuntimeException("NullPointerException at Class.java:99"));
        assertEquals(500, genericResp.getStatusCode().value());
        assertEquals("An unexpected error occurred. Please contact system support.", genericResp.getBody().get("message"));
    }

    @Test
    void loginPageRendersAppropriateAlertMessages() throws Exception {
        mockMvc.perform(get("/login").param("locked", "true"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute(
                        "errorMessage", "Account is temporarily locked due to multiple failed login attempts. Please try again later."));

        mockMvc.perform(get("/login").param("disabled", "true"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute(
                        "errorMessage", "Your account or employee profile has been deactivated. Please contact an administrator."));

        mockMvc.perform(get("/login").param("roleError", "true"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute(
                        "errorMessage", "You do not have permission to access the selected role workspace."));

        mockMvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute(
                        "errorMessage", "Invalid username or password."));
    }
}
