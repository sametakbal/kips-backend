package com.akbal.kips.be.repository;

import com.akbal.kips.be.domain.product.ProductFamily;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFamilyRepository extends JpaRepository<ProductFamily, Long> {
}
