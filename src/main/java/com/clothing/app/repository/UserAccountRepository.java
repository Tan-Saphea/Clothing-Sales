package com.clothing.app.repository;

import com.clothing.app.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @Query("SELECT u FROM UserAccount u LEFT JOIN FETCH u.employee WHERE LOWER(u.username) = LOWER(:username)")
    Optional<UserAccount> findByUsername(@Param("username") String username);

    @Query("SELECT (COUNT(u) > 0) FROM UserAccount u LEFT JOIN u.employee e " +
           "WHERE LOWER(u.username) = LOWER(:username) " +
           "AND u.enabled = true " +
           "AND (e IS NULL OR UPPER(e.status) = 'ACTIVE')")
    boolean isUserActive(@Param("username") String username);

    Optional<UserAccount> findByEmployee_EmployeeId(Long employeeId);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM UserAccount u LEFT JOIN FETCH u.employee ORDER BY u.userId")
    List<UserAccount> findAllWithEmployee();

    boolean existsByEmployee_EmployeeId(Long employeeId);
}
