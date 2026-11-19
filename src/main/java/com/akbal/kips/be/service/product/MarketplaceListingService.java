package com.akbal.kips.be.service.product;

import com.akbal.kips.be.domain.product.MarketplaceChannel;
import com.akbal.kips.be.domain.product.MarketplaceListing;
import com.akbal.kips.be.dto.product.request.MarketplaceListingRequest;
import com.akbal.kips.be.dto.product.response.MarketplaceListingResponse;
import com.akbal.kips.be.repository.MarketplaceListingRepository;
import com.akbal.kips.be.repository.ProductRepository;
import com.akbal.kips.be.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
public class MarketplaceListingService {
    private final MarketplaceListingRepository listings;
    private final ProductRepository products;
    private final JsonMapper json;

    @Transactional(readOnly = true)
    public List<MarketplaceListingResponse> list(long productId) {
        if (!products.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return listings.findAllByProductIdOrderById(productId).stream().map(this::view).toList();
    }

    @Transactional
    public MarketplaceListingResponse upsert(long productId, MarketplaceChannel channel, String account,
            String market, MarketplaceListingRequest request) {
        validateOffer(request);
        var channelSku = normalizeChannelSku(channel, request.channelSku());
        var accountKey = CatalogIdentifiers.normalizedCode(account, "account key");
        var marketKey = CatalogIdentifiers.normalizedCode(market, "market key");
        var product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        var listing = listings.findByProductIdAndChannelAndAccountKeyAndMarketKey(productId, channel, accountKey, marketKey)
                .orElseGet(() -> {
                    var created = new MarketplaceListing();
                    created.setProduct(product);
                    created.setChannel(channel);
                    created.setAccountKey(accountKey);
                    created.setMarketKey(marketKey);
                    return created;
                });

        listing.setChannelSku(channelSku);
        listing.setChannelBarcode(request.channelBarcode());
        listing.setExternalCatalogId(request.externalCatalogId());
        listing.setExternalListingId(request.externalListingId());
        listing.setCategoryReference(request.categoryReference());
        listing.setBrandReference(request.brandReference());
        listing.setProductType(request.productType());
        listing.setGroupReference(request.groupReference());
        listing.setSalePrice(request.salePrice());
        listing.setListPrice(request.listPrice());
        listing.setCurrency(request.currency());
        listing.setChannelAttributes(request.channelAttributes() == null ? Map.of() : request.channelAttributes());
        return view(listings.save(listing));
    }

    @Transactional
    public void delete(long productId, MarketplaceChannel channel, String account, String market) {
        var accountKey = CatalogIdentifiers.normalizedCode(account, "account key");
        var marketKey = CatalogIdentifiers.normalizedCode(market, "market key");
        products.findByIdForUpdate(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        listings.findByProductIdAndChannelAndAccountKeyAndMarketKey(productId, channel, accountKey, marketKey)
                .ifPresent(listings::delete);
    }

    private void validateOffer(MarketplaceListingRequest request) {
        if ((request.salePrice() == null) != (request.currency() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sale price and currency must be provided together");
        }
        if (request.currency() != null) {
            int fractionDigits = currencyFractionDigits(request.currency());
            if (request.salePrice().stripTrailingZeros().scale() > fractionDigits
                    || (request.listPrice() != null && request.listPrice().stripTrailingZeros().scale() > fractionDigits)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price precision exceeds the currency minor unit");
            }
        }
        if (request.listPrice() != null && (request.salePrice() == null
                || request.listPrice().compareTo(request.salePrice()) < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "List price must be at least the sale price");
        }
        if (request.channelAttributes() != null && json.writeValueAsBytes(request.channelAttributes()).length > 65_536) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel attributes exceed 64 KiB");
        }
    }

    private int currencyFractionDigits(String code) {
        final int fractionDigits;
        try {
            fractionDigits = Currency.getInstance(code).getDefaultFractionDigits();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid currency");
        }
        if (fractionDigits < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency must have a defined minor unit");
        }
        return fractionDigits;
    }

    private String normalizeChannelSku(MarketplaceChannel channel, String value) {
        var sku = value.strip();
        if (channel == MarketplaceChannel.HEPSIBURADA) {
            sku = sku.toUpperCase(Locale.ROOT);
            if (sku.length() > 255 || sku.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hepsiburada SKU must have at most 255 characters and no spaces");
            }
        }
        return sku;
    }

    private BigDecimal viewPrice(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        var trimmed = amount.stripTrailingZeros();
        return trimmed.scale() < 0 ? trimmed.setScale(0) : trimmed;
    }

    private MarketplaceListingResponse view(MarketplaceListing listing) {
        return new MarketplaceListingResponse(listing.getId(), listing.getProduct().getId(), listing.getChannel(),
                listing.getAccountKey(), listing.getMarketKey(), listing.getChannelSku(), listing.getChannelBarcode(),
                listing.getExternalCatalogId(), listing.getExternalListingId(), listing.getCategoryReference(),
                listing.getBrandReference(), listing.getProductType(), listing.getGroupReference(),
                viewPrice(listing.getSalePrice()), viewPrice(listing.getListPrice()), listing.getCurrency(),
                listing.getChannelAttributes());
    }
}
