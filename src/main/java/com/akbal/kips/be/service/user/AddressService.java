package com.akbal.kips.be.service.user;

import com.akbal.kips.be.domain.user.Address;
import com.akbal.kips.be.dto.user.AddressDtos.AddressRequest;
import com.akbal.kips.be.dto.user.AddressDtos.AddressResponse;
import com.akbal.kips.be.repository.AddressRepository;
import com.akbal.kips.be.repository.UserRepository;
import com.akbal.kips.be.security.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permissions.SHOP_OWN + "') and #username == authentication.name")
public class AddressService {
    private static final int MAX_ADDRESSES_PER_USER = 20;

    private final AddressRepository repository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(String username) {
        return repository.findAllByUserIdOrderByIdDesc(resolveUserId(username)).stream().map(this::view).toList();
    }

    @Transactional
    public AddressResponse create(String username, AddressRequest request) {
        long userId = resolveUserId(username);
        long existing = repository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Address limit reached");
        }
        boolean makeDefault = request.defaultAddress() || existing == 0;
        if (makeDefault) {
            repository.clearDefaults(userId);
        }
        var address = new Address();
        address.setUser(userRepository.getReferenceById(userId));
        apply(address, request);
        address.setDefaultAddress(makeDefault);
        return view(repository.save(address));
    }

    @Transactional
    public AddressResponse update(String username, long id, AddressRequest request) {
        var address = findOwn(username, id);
        apply(address, request);
        if (request.defaultAddress() && !address.isDefaultAddress()) {
            repository.clearDefaults(address.getUser().getId());
            address.setDefaultAddress(true);
        }
        return view(address);
    }

    @Transactional
    public void delete(String username, long id) {
        repository.delete(findOwn(username, id));
    }

    @Transactional
    public AddressResponse setDefault(String username, long id) {
        var address = findOwn(username, id);
        if (!address.isDefaultAddress()) {
            repository.clearDefaults(address.getUser().getId());
            address.setDefaultAddress(true);
        }
        return view(address);
    }

    private Address findOwn(String username, long id) {
        return repository.findByIdAndUserId(id, resolveUserId(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private long resolveUserId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
                .getId();
    }

    private void apply(Address address, AddressRequest request) {
        address.setLabel(request.label());
        address.setRecipientName(request.recipientName());
        address.setPhone(request.phone());
        address.setLine1(request.line1());
        address.setLine2(request.line2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country().toUpperCase(Locale.ROOT));
    }

    private AddressResponse view(Address address) {
        return new AddressResponse(address.getId(), address.getLabel(), address.getRecipientName(),
                address.getPhone(), address.getLine1(), address.getLine2(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getCountry(), address.isDefaultAddress());
    }
}
