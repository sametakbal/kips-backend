package com.akbal.kips.be.service.commerce;

import com.akbal.kips.be.service.staff.StaffAudit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CommerceServiceTest {
    @Mock
    JdbcClient jdbc;

    @Mock
    StaffAudit audit;

    @Test
    void refusesCheckoutWhenLocalPaymentIsDisabled() {
        var service = new CommerceService(jdbc, audit, false);

        assertStatus(service, "key", HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsBlankAndOversizedIdempotencyKeysBeforeTouchingTheDatabase() {
        var service = new CommerceService(jdbc, audit, true);

        assertStatus(service, " ", HttpStatus.BAD_REQUEST);
        assertStatus(service, "k".repeat(129), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(jdbc);
    }

    private void assertStatus(CommerceService service, String key, HttpStatus expected) {
        assertThatThrownBy(() -> service.checkout("alice", key, 1L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(expected));
    }
}
