package com.akbal.kips.be.repository;

import com.akbal.kips.be.domain.product.MarketplaceChannel;
import com.akbal.kips.be.domain.product.MarketplaceListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, Long> {
    List<MarketplaceListing> findAllByProductIdOrderById(Long productId);

    Optional<MarketplaceListing> findByProductIdAndChannelAndAccountKeyAndMarketKey(
            Long productId, MarketplaceChannel channel, String accountKey, String marketKey);
}
