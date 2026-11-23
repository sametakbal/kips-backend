package com.akbal.kips.be.service.user;

import com.akbal.kips.be.domain.user.User;
import com.akbal.kips.be.dto.user.UserDtos.ChangePasswordRequest;
import com.akbal.kips.be.dto.user.UserDtos.UpdateProfileRequest;
import com.akbal.kips.be.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {
    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    UserProfileService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, passwordEncoder);
        user = User.builder().username("alice").email("alice@example.test").password("stored-hash")
                .roles(new HashSet<>()).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    }

    @Test
    void changesThePasswordWhenTheCurrentOneMatches() {
        when(passwordEncoder.matches("current-password", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.changePassword("alice", new ChangePasswordRequest("current-password", "new-password"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void keepsThePasswordWhenTheCurrentOneIsWrong() {
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword("alice", new ChangePasswordRequest("wrong", "new-password")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(user.getPassword()).isEqualTo("stored-hash");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void rejectsPasswordsLongerThanBcryptCanHandleInBytes() {
        when(passwordEncoder.matches("current-password", "stored-hash")).thenReturn(true);
        var multiByte = "ü".repeat(37);

        assertThatThrownBy(() -> service.changePassword("alice", new ChangePasswordRequest("current-password", multiByte)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(user.getPassword()).isEqualTo("stored-hash");
    }

    @Test
    void rejectsAnEmailUsedByAnotherAccount() {
        when(userRepository.existsByEmail("taken@example.test")).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile("alice", new UpdateProfileRequest("taken@example.test")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(user.getEmail()).isEqualTo("alice@example.test");
    }

    @Test
    void allowsRecasingTheOwnEmailWithoutAConflictCheck() {
        var updated = service.updateProfile("alice", new UpdateProfileRequest("Alice@Example.test"));

        assertThat(updated.email()).isEqualTo("Alice@Example.test");
        verify(userRepository, never()).existsByEmail(anyString());
    }
}
