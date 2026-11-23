package com.akbal.kips.be.service.user;

import com.akbal.kips.be.domain.user.Address;
import com.akbal.kips.be.domain.user.User;
import com.akbal.kips.be.dto.user.AddressDtos.AddressRequest;
import com.akbal.kips.be.repository.AddressRepository;
import com.akbal.kips.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
    private static final long USER_ID = 7L;

    @Mock
    AddressRepository repository;

    @Mock
    UserRepository userRepository;

    AddressService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new AddressService(repository, userRepository);
        user = new User();
        user.setId(USER_ID);
        lenient().when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        lenient().when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        lenient().when(repository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void makesTheFirstAddressDefaultEvenWhenNotRequested() {
        when(repository.countByUserId(USER_ID)).thenReturn(0L);

        var created = service.create("alice", request(false));

        assertThat(created.defaultAddress()).isTrue();
        assertThat(created.country()).isEqualTo("TR");
        verify(repository).clearDefaults(USER_ID);
    }

    @Test
    void keepsTheCurrentDefaultWhenALaterAddressIsNotRequestedAsDefault() {
        when(repository.countByUserId(USER_ID)).thenReturn(2L);

        var created = service.create("alice", request(false));

        assertThat(created.defaultAddress()).isFalse();
        verify(repository, never()).clearDefaults(anyLong());
    }

    @Test
    void clearsPreviousDefaultsBeforeSavingANewDefault() {
        when(repository.countByUserId(USER_ID)).thenReturn(2L);

        var created = service.create("alice", request(true));

        assertThat(created.defaultAddress()).isTrue();
        InOrder order = inOrder(repository);
        order.verify(repository).clearDefaults(USER_ID);
        order.verify(repository).save(any(Address.class));
    }

    @Test
    void rejectsAddressesBeyondTheLimit() {
        when(repository.countByUserId(USER_ID)).thenReturn(20L);

        assertThatThrownBy(() -> service.create("alice", request(true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(repository, never()).save(any());
    }

    @Test
    void promotesAnExistingAddressToDefaultOnUpdate() {
        when(repository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(address(10L, false)));

        var updated = service.update("alice", 10L, request(true));

        assertThat(updated.defaultAddress()).isTrue();
        verify(repository).clearDefaults(USER_ID);
    }

    @Test
    void doesNotDemoteTheDefaultAddressOnUpdate() {
        when(repository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(address(10L, true)));

        var updated = service.update("alice", 10L, request(false));

        assertThat(updated.defaultAddress()).isTrue();
        verify(repository, never()).clearDefaults(anyLong());
    }

    @Test
    void setDefaultIsANoOpForTheCurrentDefault() {
        when(repository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(address(10L, true)));

        assertThat(service.setDefault("alice", 10L).defaultAddress()).isTrue();
        verify(repository, never()).clearDefaults(anyLong());
    }

    @Test
    void setDefaultSwitchesTheDefaultAddress() {
        when(repository.findByIdAndUserId(11L, USER_ID)).thenReturn(Optional.of(address(11L, false)));

        assertThat(service.setDefault("alice", 11L).defaultAddress()).isTrue();
        verify(repository).clearDefaults(USER_ID);
    }

    @Test
    void hidesAddressesOwnedBySomeoneElse() {
        when(repository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("alice", 99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(repository, never()).delete(any());
    }

    @Test
    void rejectsUnknownUsers() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list("ghost"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private Address address(long id, boolean isDefault) {
        var address = new Address();
        address.setId(id);
        address.setUser(user);
        address.setDefaultAddress(isDefault);
        return address;
    }

    private AddressRequest request(boolean makeDefault) {
        return new AddressRequest("Home", "Alice", "+90 555 000 0000", "1 Test Street", null, "Istanbul",
                null, "34000", "tr", makeDefault);
    }
}
