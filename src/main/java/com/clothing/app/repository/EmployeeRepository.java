package com.clothing.app.repository;

import com.clothing.app.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByEmail(String email);
    List<Employee> findByStatus(String status);
    List<Employee> findByPositionContainingIgnoreCase(String position);
}
