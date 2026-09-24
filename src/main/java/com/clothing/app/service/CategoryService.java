package com.clothing.app.service;

import com.clothing.app.entity.Category;
import com.clothing.app.repository.CategoryRepository;
import com.clothing.app.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final OracleSessionContextService oracleSessionContextService;

    public CategoryService(CategoryRepository categoryRepository,
                           ProductRepository productRepository,
                           OracleSessionContextService oracleSessionContextService) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.oracleSessionContextService = oracleSessionContextService;
    }

    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    public Optional<Category> findById(Long id) {
        return categoryRepository.findById(id);
    }

    public Optional<Category> findByName(String categoryName) {
        return categoryRepository.findByCategoryName(categoryName);
    }

    @Transactional
    public Category save(Category category) {
        oracleSessionContextService.applyCurrentUser();
        if (category.getCategoryName() == null || category.getCategoryName().isBlank()) {
            throw new IllegalArgumentException("Category name is required");
        }
        String name = category.getCategoryName().trim();
        if (name.length() > 100) {
            throw new IllegalArgumentException("Category name must be 100 characters or fewer");
        }
        if (category.getDescription() != null && category.getDescription().length() > 255) {
            throw new IllegalArgumentException("Category description must be 255 characters or fewer");
        }
        categoryRepository.findByCategoryNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getCategoryId().equals(category.getCategoryId())) {
                throw new IllegalArgumentException("Category name already exists");
            }
        });
        category.setCategoryName(name);
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteById(Long categoryId) {
        oracleSessionContextService.applyCurrentUser();
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
        if (!productRepository.findByCategory_CategoryId(categoryId).isEmpty()) {
            throw new IllegalArgumentException("Cannot delete category '" + category.getCategoryName() + "' because products are assigned to it. Reassign or delete the products first.");
        }
        categoryRepository.delete(category);
    }
}
