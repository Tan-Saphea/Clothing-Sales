package com.clothing.app.service;

import com.clothing.app.entity.AppRole;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.UserAccount;
import com.clothing.app.entity.UserRole;
import com.clothing.app.entity.UserRoleId;
import com.clothing.app.repository.AppRoleRepository;
import com.clothing.app.repository.EmployeeRepository;
import com.clothing.app.repository.PurchaseRepository;
import com.clothing.app.repository.SaleRepository;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminManagementService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final AppRoleRepository appRoleRepository;
    private final EmployeeRepository employeeRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final PasswordEncoder passwordEncoder;
    private final OracleSessionContextService oracleSessionContextService;

    public AdminManagementService(UserAccountRepository userAccountRepository,
                                  UserRoleRepository userRoleRepository,
                                  AppRoleRepository appRoleRepository,
                                  EmployeeRepository employeeRepository,
                                  SaleRepository saleRepository,
                                  PurchaseRepository purchaseRepository,
                                  PasswordEncoder passwordEncoder,
                                  OracleSessionContextService oracleSessionContextService) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.appRoleRepository = appRoleRepository;
        this.employeeRepository = employeeRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.passwordEncoder = passwordEncoder;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    public List<Map<String, Object>> listAccounts() {
        return userAccountRepository.findAllWithEmployee().stream().map(account -> {
            String role = userRoleRepository.findByUserAccount_UserId(account.getUserId()).stream()
                    .map(UserRole::getAppRole)
                    .filter(java.util.Objects::nonNull)
                    .map(AppRole::getRoleName)
                    .findFirst()
                    .orElse("USER");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userId", account.getUserId());
            result.put("username", account.getUsername());
            result.put("enabled", Boolean.TRUE.equals(account.getEnabled()));
            result.put("role", role);
            result.put("employeeId", account.getEmployee() != null ? account.getEmployee().getEmployeeId() : 0);
            result.put("employeeName", account.getEmployee() != null ? account.getEmployee().getEmployeeName() : "Unassigned");
            result.put("gender", account.getEmployee() != null ? account.getEmployee().getGender() : null);
            result.put("phone", account.getEmployee() != null ? account.getEmployee().getPhone() : null);
            result.put("email", account.getEmployee() != null ? account.getEmployee().getEmail() : null);
            result.put("address", account.getEmployee() != null ? account.getEmployee().getAddress() : null);
            result.put("createdAt", account.getCreatedAt() != null ? account.getCreatedAt().toString() : null);
            result.put("lastLogin", account.getLastLogin() != null ? account.getLastLogin().toString() : null);
            return result;
        }).toList();
    }

    @Transactional
    public Map<String, Object> createCashier(String username, String password, String employeeName,
                                              String gender, String phone, String email, String address) {
        oracleSessionContextService.applyCurrentUser();
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isBlank() || normalizedUsername.length() > 50) {
            throw new IllegalArgumentException("Username is required and must be 50 characters or fewer");
        }
        if (userAccountRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username already exists");
        }
        validatePassword(password);
        if (employeeName == null || employeeName.isBlank()) {
            throw new IllegalArgumentException("Employee name is required");
        }
        validateEmployeeFields(employeeName, gender, phone, email, address);
        if (email != null && !email.isBlank() && employeeRepository.findByEmail(email.trim()).isPresent()) {
            throw new IllegalArgumentException("Employee email already exists");
        }

        Employee employee = new Employee();
        employee.setEmployeeName(employeeName.trim());
        employee.setGender(blankToNull(gender));
        employee.setPhone(blankToNull(phone));
        employee.setEmail(blankToNull(email));
        employee.setPosition("Cashier");
        employee.setAddress(blankToNull(address));
        employee.setHireDate(LocalDate.now());
        employee.setStatus("ACTIVE");
        Employee savedEmployee = employeeRepository.save(employee);

        UserAccount account = new UserAccount();
        account.setUsername(normalizedUsername);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setEnabled(true);
        account.setEmployee(savedEmployee);
        UserAccount savedAccount = userAccountRepository.save(account);

        AppRole cashierRole = appRoleRepository.findByRoleName("CASHIER")
                .orElseThrow(() -> new IllegalStateException("CASHIER role is not configured"));
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(savedAccount.getUserId(), cashierRole.getRoleId()));
        userRole.setUserAccount(savedAccount);
        userRole.setAppRole(cashierRole);
        userRoleRepository.save(userRole);

        return Map.of("userId", savedAccount.getUserId(), "username", savedAccount.getUsername(),
                "employeeId", savedEmployee.getEmployeeId(), "role", "CASHIER");
    }

    @Transactional
    public Map<String, Object> createCashierForExistingEmployee(String username, String password, Long employeeId) {
        oracleSessionContextService.applyCurrentUser();
        String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (normalizedUsername.isBlank() || normalizedUsername.length() > 50) {
            throw new IllegalArgumentException("Username is required and must be 50 characters or fewer");
        }
        if (userAccountRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username already exists");
        }
        validatePassword(password);
        if (userAccountRepository.existsByEmployee_EmployeeId(employeeId)) {
            throw new IllegalArgumentException("This staff member already has a login account");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found: " + employeeId));
        if (!"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new IllegalArgumentException("Cannot create a login for an inactive staff member");
        }

        UserAccount account = new UserAccount();
        account.setUsername(normalizedUsername);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setEnabled(true);
        account.setEmployee(employee);
        UserAccount savedAccount = userAccountRepository.save(account);

        AppRole cashierRole = appRoleRepository.findByRoleName("CASHIER")
                .orElseThrow(() -> new IllegalStateException("CASHIER role is not configured"));
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(savedAccount.getUserId(), cashierRole.getRoleId()));
        userRole.setUserAccount(savedAccount);
        userRole.setAppRole(cashierRole);
        userRoleRepository.save(userRole);

        return Map.of("userId", savedAccount.getUserId(), "username", savedAccount.getUsername(),
                "employeeId", employee.getEmployeeId(), "employeeName", employee.getEmployeeName(), "role", "CASHIER");
    }

    @Transactional
    public Map<String, Object> updateCashierInfo(Long userId, String employeeName, String gender,
                                                  String phone, String email, String address) {
        oracleSessionContextService.applyCurrentUser();
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found: " + userId));
        if (!isCashierOnlyAccount(userId)) {
            throw new IllegalArgumentException("Only cashier accounts can be edited here");
        }
        if (employeeName == null || employeeName.isBlank()) {
            throw new IllegalArgumentException("Employee name is required");
        }
        validateEmployeeFields(employeeName, gender, phone, email, address);
        Employee employee = account.getEmployee();
        if (employee == null) {
            throw new IllegalArgumentException("This account has no linked employee profile");
        }
        String newEmail = blankToNull(email);
        if (newEmail != null && !newEmail.equalsIgnoreCase(employee.getEmail())) {
            employeeRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getEmployeeId().equals(employee.getEmployeeId())) {
                    throw new IllegalArgumentException("Employee email already exists");
                }
            });
        }
        employee.setEmployeeName(employeeName.trim());
        employee.setGender(blankToNull(gender));
        employee.setPhone(blankToNull(phone));
        employee.setEmail(newEmail);
        employee.setAddress(blankToNull(address));
        employeeRepository.save(employee);
        return Map.of("userId", userId, "employeeName", employee.getEmployeeName(),
                "message", "Cashier info updated successfully");
    }

    @Transactional
    public Map<String, Object> deleteCashier(Long userId) {
        oracleSessionContextService.applyCurrentUser();
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found: " + userId));
        if (!isCashierOnlyAccount(userId)) {
            throw new IllegalArgumentException("Only cashier accounts can be deleted here");
        }
        Employee employee = account.getEmployee();
        Long employeeId = employee != null ? employee.getEmployeeId() : null;

        boolean hasLinkedRecords = employeeId != null && (
                !saleRepository.findByEmployee_EmployeeId(employeeId).isEmpty() ||
                !purchaseRepository.findByEmployee_EmployeeId(employeeId).isEmpty()
        );

        if (hasLinkedRecords) {
            account.setEnabled(false);
            userAccountRepository.save(account);
            if (employee != null) {
                employee.setStatus("INACTIVE");
                employeeRepository.save(employee);
            }
            return Map.of("action", "DEACTIVATED",
                    "message", "Cashier has linked transaction records and was deactivated instead of deleted");
        }

        userRoleRepository.findByUserAccount_UserId(userId).forEach(userRoleRepository::delete);
        userAccountRepository.delete(account);
        if (employee != null) {
            employeeRepository.delete(employee);
        }
        return Map.of("action", "DELETED", "message", "Cashier account deleted successfully");
    }

    @Transactional
    public void setAccountEnabled(Long userId, boolean enabled) {
        oracleSessionContextService.applyCurrentUser();
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found: " + userId));
        if (!isCashierOnlyAccount(userId)) {
            throw new IllegalArgumentException("Only cashier accounts can be enabled or disabled here");
        }
        account.setEnabled(enabled);
        userAccountRepository.save(account);
        if (account.getEmployee() != null) {
            account.getEmployee().setStatus(enabled ? "ACTIVE" : "INACTIVE");
            employeeRepository.save(account.getEmployee());
        }
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        oracleSessionContextService.applyCurrentUser();
        validatePassword(newPassword);
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found: " + userId));
        if (!isCashierOnlyAccount(userId)) {
            throw new IllegalArgumentException("Password can only be reset for cashier accounts here");
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(account);
    }

    private boolean isCashierOnlyAccount(Long userId) {
        List<String> roles = userRoleRepository.findByUserAccount_UserId(userId).stream()
                .map(UserRole::getAppRole)
                .filter(java.util.Objects::nonNull)
                .map(AppRole::getRoleName)
                .toList();
        return roles.stream().anyMatch("CASHIER"::equalsIgnoreCase)
                && roles.stream().noneMatch("ADMIN"::equalsIgnoreCase);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches(".*[a-z].*")
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*\\d.*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException("Password must be 12-72 bytes and include upper-case, lower-case, number, and symbol");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateEmployeeFields(String employeeName, String gender, String phone, String email, String address) {
        validateLength(employeeName == null ? null : employeeName.trim(), 120, "Employee name");
        validateLength(blankToNull(gender), 10, "Gender");
        validateLength(blankToNull(phone), 20, "Phone");
        validateLength(blankToNull(email), 150, "Email");
        validateLength(blankToNull(address), 255, "Address");
    }

    private void validateLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be " + max + " characters or fewer");
        }
    }
}
