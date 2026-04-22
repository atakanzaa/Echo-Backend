package com.echo.service;

import com.echo.domain.subscription.Subscription;
import com.echo.domain.user.User;
import com.echo.exception.SubscriptionOwnershipException;
import com.echo.repository.SubscriptionEventRepository;
import com.echo.repository.SubscriptionRepository;
import com.echo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceOwnershipTest {

    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionEventRepository subscriptionEventRepository;
    @Mock UserRepository userRepository;
    @Mock AppleStoreKitService appleStoreKitService;
    @Mock EntitlementService entitlementService;
    @Mock ApplicationEventPublisher eventPublisher;
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks SubscriptionService subscriptionService;

    @Test
    void verifyAndActivateRejectsTransactionOwnedByAnotherUser() {
        UUID attackerId = UUID.randomUUID();
        UUID legitOwnerId = UUID.randomUUID();

        User attacker = new User();
        attacker.setId(attackerId);
        attacker.setEmail("attacker@example.com");

        User legitOwner = new User();
        legitOwner.setId(legitOwnerId);

        Subscription existing = Subscription.builder()
                .id(UUID.randomUUID())
                .user(legitOwner)
                .originalTransactionId("OT-123")
                .productId("premium_monthly")
                .purchaseDate(OffsetDateTime.now().minusDays(1))
                .expiresDate(OffsetDateTime.now().plusDays(29))
                .build();

        AppleStoreKitService.AppleTransactionPayload payload =
                new AppleStoreKitService.AppleTransactionPayload(
                        "OT-123", "OT-123", "premium_monthly",
                        OffsetDateTime.now().minusDays(1),
                        OffsetDateTime.now().plusDays(29),
                        null, "Production", "com.echojournal.ios",
                        Map.of());

        given(appleStoreKitService.verifyAndDecodeTransaction("jwt")).willReturn(payload);
        given(userRepository.findById(attackerId)).willReturn(Optional.of(attacker));
        given(subscriptionRepository.findByOriginalTransactionId("OT-123"))
                .willReturn(Optional.of(existing));

        // The subscription must NOT be reassigned to the attacker.
        assertThatThrownBy(() -> subscriptionService.verifyAndActivate(attackerId, "jwt"))
                .isInstanceOf(SubscriptionOwnershipException.class);

        verify(subscriptionRepository, never()).save(existing);
        verify(entitlementService, never()).invalidateCache(attackerId);
    }
}
