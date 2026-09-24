package com.clothing.app.service;

import com.clothing.app.entity.AppRole;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.UserAccount;
import com.clothing.app.entity.UserRole;
import com.clothing.app.repository.EmployeeRepository;
import com.clothing.app.repository.PurchaseRepository;
import com.clothing.app.repository.SaleRepository;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffAccessRulesTest {

    @Test
    void administratorEmployeeCannotBeDeletedOrDeactivated() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        Employee employee = employee(1L, "ACTIVE");
        UserAccount account = account(10L, employee);
        when(employees.findById(1L)).thenReturn(Optional.of(employee));
        when(accounts.findByEmployee_EmployeeId(1L)).thenReturn(Optional.of(account));
        when(roles.findByUserAccount_UserId(10L)).thenReturn(List.of(role("ADMIN")));
        StaffService service = new StaffService(employees, mock(SaleRepository.class),
                mock(PurchaseRepository.class), accounts, roles, mock(OracleSessionContextService.class));

        assertThrows(IllegalArgumentException.class, () -> service.delete(1L));
        verify(employees, never()).delete(employee);
    }

    @Test
    void deactivatingCashierStaffAlsoDisablesLoginAccount() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserRoleRepository roles = mock(UserRoleRepository.class);
        SaleRepository sales = mock(SaleRepository.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        Employee employee = employee(2L, "ACTIVE");
        UserAccount account = account(20L, employee);
        when(employees.findById(2L)).thenReturn(Optional.of(employee));
        when(accounts.findByEmployee_EmployeeId(2L)).thenReturn(Optional.of(account));
        when(accounts.existsByEmployee_EmployeeId(2L)).thenReturn(true);
        when(roles.findByUserAccount_UserId(20L)).thenReturn(List.of(role("CASHIER")));
        when(sales.findByEmployee_EmployeeId(2L)).thenReturn(List.of());
        when(purchases.findByEmployee_EmployeeId(2L)).thenReturn(List.of());
        StaffService service = new StaffService(employees, sales, purchases, accounts, roles,
                mock(OracleSessionContextService.class));

        assertEquals("DEACTIVATED", service.delete(2L).get("action"));
        assertEquals("INACTIVE", employee.getStatus());
        assertFalse(account.getEnabled());
        verify(accounts).save(account);
    }

    private Employee employee(Long id, String status) {
        Employee employee = new Employee();
        employee.setEmployeeId(id);
        employee.setEmployeeName("Staff " + id);
        employee.setStatus(status);
        return employee;
    }

    private UserAccount account(Long id, Employee employee) {
        UserAccount account = new UserAccount();
        account.setUserId(id);
        account.setEmployee(employee);
        account.setEnabled(true);
        return account;
    }

    private UserRole role(String name) {
        AppRole appRole = new AppRole();
        appRole.setRoleName(name);
        UserRole userRole = new UserRole();
        userRole.setAppRole(appRole);
        return userRole;
    }
}
