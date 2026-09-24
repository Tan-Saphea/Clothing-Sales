package com.clothing.app.security;

import com.clothing.app.entity.Employee;
import com.clothing.app.entity.UserAccount;
import com.clothing.app.entity.Sale;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserAccessServiceTest {

    @Test
    void administratorMayChooseActiveSalesEmployee() {
        CurrentUserAccessService service = new CurrentUserAccessService(mock(UserAccountRepository.class), mock(SaleRepository.class));
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertEquals(42L, service.resolveSaleEmployeeId(auth, 42L));
    }

    @Test
    void cashierSaleAlwaysUsesEmployeeLinkedToAccount() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        Employee employee = new Employee();
        employee.setEmployeeId(7L);
        employee.setStatus("ACTIVE");
        UserAccount account = new UserAccount();
        account.setEmployee(employee);
        when(accounts.findByUsername("cashier")).thenReturn(Optional.of(account));
        CurrentUserAccessService service = new CurrentUserAccessService(accounts, mock(SaleRepository.class));
        var auth = new UsernamePasswordAuthenticationToken(
                "cashier", "", List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));

        assertEquals(7L, service.resolveSaleEmployeeId(auth, 999L));
    }

    @Test
    void inactiveCashierEmployeeCannotCreateSale() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        Employee employee = new Employee();
        employee.setEmployeeId(7L);
        employee.setStatus("INACTIVE");
        UserAccount account = new UserAccount();
        account.setEmployee(employee);
        when(accounts.findByUsername("cashier")).thenReturn(Optional.of(account));
        CurrentUserAccessService service = new CurrentUserAccessService(accounts, mock(SaleRepository.class));
        var auth = new UsernamePasswordAuthenticationToken(
                "cashier", "", List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));

        assertThrows(AccessDeniedException.class,
                () -> service.resolveSaleEmployeeId(auth, 7L));
    }

    @Test
    void cashierCannotCancelACompletedSale() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        SaleRepository sales = mock(SaleRepository.class);
        Employee employee = new Employee();
        employee.setEmployeeId(7L);
        employee.setStatus("ACTIVE");
        UserAccount account = new UserAccount();
        account.setEmployee(employee);
        Sale sale = new Sale();
        sale.setEmployee(employee);
        sale.setStatus("COMPLETED");
        when(accounts.findByUsername("cashier")).thenReturn(Optional.of(account));
        when(sales.findById(15L)).thenReturn(Optional.of(sale));
        CurrentUserAccessService service = new CurrentUserAccessService(accounts, sales);
        var auth = new UsernamePasswordAuthenticationToken(
                "cashier", "", List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));

        assertThrows(AccessDeniedException.class, () -> service.assertCanCancelSale(auth, 15L));
    }
}
