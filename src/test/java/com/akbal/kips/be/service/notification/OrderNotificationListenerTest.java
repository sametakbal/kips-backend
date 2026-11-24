package com.akbal.kips.be.service.notification;

import com.akbal.kips.be.service.notification.OrderNotificationListener.OrderRecipient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNotificationListenerTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    JdbcClient jdbc;

    @Mock
    NotificationService notifications;

    private void orderLookupReturns(Optional<OrderRecipient> result) {
        when(jdbc.sql(any(String.class)).param(any(String.class), any()).query(OrderRecipient.class).optional())
                .thenReturn(result);
    }

    @Test
    void queuesANotificationForTheOrderOwner() {
        orderLookupReturns(Optional.of(new OrderRecipient(7L, "alice@example.test", "SHIPPED",
                new BigDecimal("19.90"), "TRY")));

        new OrderNotificationListener(jdbc, notifications).onOrderEvent(new OrderEvent(OrderEvent.Type.SHIPPED, 42L));

        var captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).send(captor.capture());
        var sent = captor.getValue();
        assertThat(sent.userId()).isEqualTo(7L);
        assertThat(sent.recipient()).isEqualTo("alice@example.test");
        assertThat(sent.template()).isEqualTo("ORDER_SHIPPED");
        assertThat(sent.payload()).containsEntry("orderId", 42L).containsEntry("status", "SHIPPED")
                .containsEntry("currency", "TRY");
    }

    @Test
    void swallowsDeliveryFailuresSoTheOrderFlowIsUnaffected() {
        orderLookupReturns(Optional.of(new OrderRecipient(7L, "alice@example.test", "PAID",
                BigDecimal.ONE, "TRY")));
        doThrow(new IllegalStateException("smtp down")).when(notifications).send(any());

        assertThatNoException().isThrownBy(() -> new OrderNotificationListener(jdbc, notifications)
                .onOrderEvent(new OrderEvent(OrderEvent.Type.CREATED, 1L)));
    }

    @Test
    void swallowsLookupFailures() {
        when(jdbc.sql(any(String.class))).thenThrow(new IllegalStateException("db down"));

        assertThatNoException().isThrownBy(() -> new OrderNotificationListener(jdbc, notifications)
                .onOrderEvent(new OrderEvent(OrderEvent.Type.CANCELLED, 1L)));
        verifyNoInteractions(notifications);
    }

    @Test
    void skipsMissingOrders() {
        orderLookupReturns(Optional.empty());

        new OrderNotificationListener(jdbc, notifications).onOrderEvent(new OrderEvent(OrderEvent.Type.CREATED, 1L));

        verifyNoInteractions(notifications);
    }
}
