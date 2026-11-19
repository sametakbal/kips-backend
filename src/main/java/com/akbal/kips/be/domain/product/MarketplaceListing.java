package com.akbal.kips.be.domain.product;

import com.akbal.kips.be.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "marketplace_listing")
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceListing extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private MarketplaceChannel channel;

    @Column(nullable = false, length = 64, updatable = false)
    private String accountKey;

    @Column(nullable = false, length = 64, updatable = false)
    private String marketKey;

    @Column(nullable = false)
    private String channelSku;

    private String channelBarcode;
    private String externalCatalogId;
    private String externalListingId;
    private String categoryReference;
    private String brandReference;
    private String productType;
    private String groupReference;
    private BigDecimal salePrice;
    private BigDecimal listPrice;
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> channelAttributes;
}
