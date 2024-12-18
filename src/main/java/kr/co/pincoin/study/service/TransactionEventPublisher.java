package kr.co.pincoin.study.service;

import java.math.BigDecimal;
import kr.co.pincoin.study.event.TransactionEvent.FundsTransferredEvent;
import kr.co.pincoin.study.event.TransactionEvent.TransactionCompletedEvent;
import kr.co.pincoin.study.event.TransactionEvent.TransactionCreatedEvent;
import kr.co.pincoin.study.event.TransactionEvent.TransactionFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publishTransactionCreated(String transactionId, Long fromAccountId,
        Long toAccountId, BigDecimal amount) {
        eventPublisher.publishEvent(
            new TransactionCreatedEvent(transactionId, fromAccountId, toAccountId, amount));
    }

    public void publishFundsTransferred(String transactionId, Long fromAccountId, Long toAccountId,
        BigDecimal amount) {
        eventPublisher.publishEvent(
            new FundsTransferredEvent(transactionId, fromAccountId, toAccountId, amount));
    }

    public void publishTransactionCompleted(String transactionId, BigDecimal amount) {
        eventPublisher.publishEvent(new TransactionCompletedEvent(transactionId, amount));
    }

    public void publishTransactionFailed(String transactionId, String reason) {
        eventPublisher.publishEvent(new TransactionFailedEvent(transactionId, reason));
    }
}