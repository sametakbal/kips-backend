package com.akbal.kips.be.repository;

import com.akbal.kips.be.domain.product.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findAllByOrderByIdAsc();
}
