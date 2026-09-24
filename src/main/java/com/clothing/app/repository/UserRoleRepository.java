package com.clothing.app.repository;

import com.clothing.app.entity.UserRole;
import com.clothing.app.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findByUserAccount_UserId(Long userId);
    List<UserRole> findByAppRole_RoleId(Long roleId);
}
