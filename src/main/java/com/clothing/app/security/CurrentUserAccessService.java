package com.clothing.app.security;

import com.clothing.app.entity.Employee;
import com.clothing.app.entity.UserAccount;
import com.clothing.app.repository.UserAccountRepository;
import com.clothing.app.repository.SaleRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CurrentUserAccessService {

    private final UserAccountRepository userAccountRepository;
    private final SaleRepository saleRepository;

    public CurrentUserAccessService(UserAccountRepository userAccountRepository, SaleRepository saleRepository) {
        this.userAccountRepository = userAccountRepository;
        this.saleRepository = saleRepository;
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    public Long resolveSaleEmployeeId(Authentication authentication, Long requestedEmployeeId) {
        if (isAdmin(authentication)) {
            if (requestedEmployeeId == null) {
                throw new IllegalArgumentException("Employee is required");
            }
            return requestedEmployeeId;
        }

        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authenticated cashier account is required");
        }

        return resolveCurrentEmployeeId(authentication);
    }

    public Long resolveCurrentEmployeeId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authenticated cashier account is required");
        }
        UserAccount account = userAccountRepository.findByUsername(authentication.getName().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AccessDeniedException("User account is not available"));
        Employee employee = account.getEmployee();
        if (employee == null || employee.getEmployeeId() == null
                || !"ACTIVE".equalsIgnoreCase(employee.getStatus())) {
            throw new AccessDeniedException("Cashier is not linked to an active employee");
        }
        return employee.getEmployeeId();
    }

    public void assertCanAccessSale(Authentication authentication, Long saleId) {
        if (isAdmin(authentication)) {
            return;
        }
        Long employeeId = resolveCurrentEmployeeId(authentication);
        var sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        if (sale.getEmployee() == null || !employeeId.equals(sale.getEmployee().getEmployeeId())) {
            throw new AccessDeniedException("You can only access sales assigned to your cashier account");
        }
    }

    public void assertCanCancelSale(Authentication authentication, Long saleId) {
        if (isAdmin(authentication)) {
            return;
        }
        Long employeeId = resolveCurrentEmployeeId(authentication);
        var sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        if (sale.getEmployee() == null || !employeeId.equals(sale.getEmployee().getEmployeeId())) {
            throw new AccessDeniedException("You can only access sales assigned to your cashier account");
        }
        if ("COMPLETED".equalsIgnoreCase(sale.getStatus())) {
            throw new AccessDeniedException("An administrator must approve cancellation of a completed sale");
        }
    }
}
