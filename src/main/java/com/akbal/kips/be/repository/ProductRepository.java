package com.akbal.kips.be.repository;

import com.akbal.kips.be.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}