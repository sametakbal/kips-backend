package com.akbal.kips.be.domain.product;

import com.akbal.kips.be.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_family")
@Getter
@Setter
@NoArgsConstructor
public class ProductFamily extends BaseEntity {
    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;
}
