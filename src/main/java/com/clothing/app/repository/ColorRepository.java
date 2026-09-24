package com.clothing.app.repository;

import com.clothing.app.entity.Color;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ColorRepository extends JpaRepository<Color, Long> {
    Optional<Color> findByColorName(String colorName);
}
