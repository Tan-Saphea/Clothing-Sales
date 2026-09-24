package com.clothing.app.config;

import com.clothing.app.security.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.clothing.app.security.AccountStatusFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.clothing.app.security.RoleSelectionSuccessHandler;
import org.springframework.http.HttpMethod;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.service.OracleSessionContextService;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService) {
        this.customUserDetailsService = customUserDetailsService;
    }

    @Bean
    public RoleSelectionSuccessHandler roleSelectionSuccessHandler(
            UserAccountRepository userAccountRepository,
            OracleSessionContextService oracleSessionContextService) {
        return new RoleSelectionSuccessHandler(userAccountRepository, oracleSessionContextService);
    }

    @Bean
    public AccountStatusFilter accountStatusFilter(UserAccountRepository userAccountRepository) {
        return new AccountStatusFilter(userAccountRepository);
    }

    @Bean
    public FilterRegistrationBean<AccountStatusFilter> accountStatusFilterRegistration(
            AccountStatusFilter accountStatusFilter) {
        FilterRegistrationBean<AccountStatusFilter> registration = new FilterRegistrationBean<>(accountStatusFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RoleSelectionSuccessHandler roleSelectionSuccessHandler,
                                                   AccountStatusFilter accountStatusFilter) throws Exception {
        http
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                    "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                    "img-src 'self' data: blob: https://res.cloudinary.com https://images.unsplash.com; " +
                    "font-src 'self' https://cdn.jsdelivr.net; " +
                    "frame-ancestors 'none'; " +
                    "form-action 'self';"
                ))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                    "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"
                ))
            )
            .sessionManagement(session -> session
                .sessionFixation(sf -> sf.changeSessionId())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/favicon.ico", "/favicon.png", "/error", "/css/**", "/js/**", "/images/**", "/uploads/**", "/webjars/**").permitAll()
                .requestMatchers("/sales/**", "/customers/**").hasAnyRole("ADMIN", "CASHIER")
                .requestMatchers(HttpMethod.GET, "/api/sales/**", "/api/customers/**", "/api/variants/**").hasAnyRole("ADMIN", "CASHIER")
                .requestMatchers(HttpMethod.DELETE, "/api/customers/**").hasRole("ADMIN")
                .requestMatchers("/api/sales/**", "/api/customers/**").hasAnyRole("ADMIN", "CASHIER")
                .requestMatchers(
                    "/dashboard/**", "/categories/**", "/products/**", "/stock/**",
                    "/suppliers/**", "/purchases/**", "/reports/**", "/user-control/**", "/staff/**",
                    "/audit-log/**",
                    "/api/categories/**", "/api/products/**", "/api/variants/**", "/api/stock/**",
                    "/api/suppliers/**", "/api/purchases/**", "/api/reports/**",
                    "/api/user-control/**", "/api/staff/**", "/api/admin/**",
                    "/api/upload-image", "/api/sync-images-to-cloudinary", "/api/test-db"
                ).hasRole("ADMIN")
                .anyRequest().denyAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(roleSelectionSuccessHandler)
                .failureHandler((request, response, exception) -> {
                    if (exception instanceof org.springframework.security.authentication.LockedException) {
                        response.sendRedirect(request.getContextPath() + "/login?locked");
                    } else if (exception instanceof org.springframework.security.authentication.DisabledException) {
                        response.sendRedirect(request.getContextPath() + "/login?disabled");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login?error");
                    }
                })
                .permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login");
                    }
                })
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .addFilterAfter(accountStatusFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
