package com.akbal.kips.be.service.product;

import com.akbal.kips.be.domain.product.ProductFamily;
import com.akbal.kips.be.repository.ProductFamilyRepository;
import com.akbal.kips.be.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProductFamilyService {
    private final ProductFamilyRepository repository;

    public record Family(Long id, String code, String name) {
    }

    @Transactional(readOnly = true)
    public Family get(long id) {
        return view(find(id));
    }

    @Transactional(readOnly = true)
    public Page<Family> list(Pageable pageable) {
        if (pageable.getSort().stream().anyMatch(order ->
                !java.util.Set.of("id", "code", "name").contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported family sort field");
        }
        return repository.findAll(pageable).map(this::view);
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public Family create(String code, String name) {
        var family = new ProductFamily();
        family.setCode(CatalogIdentifiers.normalizedCode(code, "family code"));
        family.setName(name);
        return view(repository.save(family));
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public Family rename(long id, String name) {
        var family = find(id);
        family.setName(name);
        return view(family);
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + Permissions.CATALOG_MANAGE + "')")
    public void delete(long id) {
        repository.delete(find(id));
    }

    private ProductFamily find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product family not found"));
    }

    private Family view(ProductFamily family) {
        return new Family(family.getId(), family.getCode(), family.getName());
    }
}
