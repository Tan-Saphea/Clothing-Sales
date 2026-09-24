package com.clothing.app.service;

import com.clothing.app.entity.Employee;
import com.clothing.app.repository.EmployeeRepository;
import com.clothing.app.repository.PurchaseRepository;
import com.clothing.app.repository.SaleRepository;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.UserRoleRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StaffService {

    private final EmployeeRepository employeeRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final OracleSessionContextService oracleSessionContextService;

    public StaffService(EmployeeRepository employeeRepository, SaleRepository saleRepository,
                        PurchaseRepository purchaseRepository, UserAccountRepository userAccountRepository,
                        UserRoleRepository userRoleRepository,
                        OracleSessionContextService oracleSessionContextService) {
        this.employeeRepository = employeeRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    public List<Employee> findAll() {
        return employeeRepository.findAll();
    }

    public Employee findById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found: " + employeeId));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Employee save(Employee employee) {
        oracleSessionContextService.applyCurrentUser();
        if (employee.getEmployeeName() == null || employee.getEmployeeName().isBlank()) {
            throw new IllegalArgumentException("Employee name is required");
        }
        employee.setEmployeeName(employee.getEmployeeName().trim());
        validateLength(employee.getEmployeeName(), 120, "Employee name");
        validateLength(employee.getGender(), 10, "Gender");
        validateLength(employee.getPhone(), 20, "Phone");
        validateLength(employee.getEmail(), 150, "Email");
        validateLength(employee.getPosition(), 100, "Position");
        validateLength(employee.getAddress(), 255, "Address");
        employee.setStatus(employee.getStatus() == null || employee.getStatus().isBlank()
                ? "ACTIVE" : employee.getStatus().trim().toUpperCase());
        if (!employee.getStatus().equals("ACTIVE") && !employee.getStatus().equals("INACTIVE")) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        if ("INACTIVE".equals(employee.getStatus()) && isAdministratorEmployee(employee.getEmployeeId())) {
            throw new IllegalArgumentException("The employee linked to an administrator account cannot be deactivated");
        }
        if (employee.getHireDate() == null) {
            employee.setHireDate(LocalDate.now());
        }
        if (employee.getEmail() != null && !employee.getEmail().isBlank()) {
            employeeRepository.findByEmail(employee.getEmail().trim()).ifPresent(existing -> {
                if (!existing.getEmployeeId().equals(employee.getEmployeeId())) {
                    throw new IllegalArgumentException("Employee email already exists");
                }
            });
            employee.setEmail(employee.getEmail().trim());
        }
        Employee saved = employeeRepository.save(employee);
        userAccountRepository.findByEmployee_EmployeeId(saved.getEmployeeId()).ifPresent(account -> {
            account.setEnabled("ACTIVE".equals(saved.getStatus()));
            userAccountRepository.save(account);
        });
        return saved;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> delete(Long employeeId) {
        oracleSessionContextService.applyCurrentUser();
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found: " + employeeId));
        if (isAdministratorEmployee(employeeId)) {
            throw new IllegalArgumentException("The employee linked to an administrator account cannot be deleted or deactivated");
        }
        if (!saleRepository.findByEmployee_EmployeeId(employeeId).isEmpty()
                || !purchaseRepository.findByEmployee_EmployeeId(employeeId).isEmpty()
                || userAccountRepository.existsByEmployee_EmployeeId(employeeId)) {
            employee.setStatus("INACTIVE");
            employeeRepository.save(employee);
            userAccountRepository.findByEmployee_EmployeeId(employeeId).ifPresent(account -> {
                account.setEnabled(false);
                userAccountRepository.save(account);
            });
            return Map.of("action", "DEACTIVATED", "message", "Staff member has linked records and was deactivated", "id", employeeId);
        }
        employeeRepository.delete(employee);
        return Map.of("action", "DELETED", "message", "Staff member deleted", "id", employeeId);
    }

    private boolean isAdministratorEmployee(Long employeeId) {
        if (employeeId == null) {
            return false;
        }
        return userAccountRepository.findByEmployee_EmployeeId(employeeId)
                .map(account -> userRoleRepository.findByUserAccount_UserId(account.getUserId()).stream()
                        .anyMatch(userRole -> userRole.getAppRole() != null
                                && "ADMIN".equalsIgnoreCase(userRole.getAppRole().getRoleName())))
                .orElse(false);
    }

    private void validateLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be " + max + " characters or fewer");
        }
    }
}
