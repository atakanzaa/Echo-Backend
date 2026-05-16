package com.echo.event;

import com.echo.service.EmailTemplateService;
import com.echo.service.ResendEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseConfirmationEmailListener {

    private final ResendEmailService resendEmailService;
    private final EmailTemplateService emailTemplateService;

    @Async("journalProcessingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseConfirmed(PurchaseConfirmedEvent event) {
        try {
            resendEmailService.send(
                    event.email(),
                    emailTemplateService.purchaseConfirmationSubject(event.language()),
                    emailTemplateService.purchaseConfirmationHtml(
                            event.productId(),
                            event.language(),
                            event.activatedAt()
                    ),
                    emailTemplateService.purchaseConfirmationText(
                            event.productId(),
                            event.language(),
                            event.activatedAt()
                    )
            );
        } catch (Exception e) {
            log.error("Purchase confirmation email failed: userId={}, productId={}",
                    event.userId(), event.productId(), e);
        }
    }
}
