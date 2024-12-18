package kr.co.pincoin.study.service;

import kr.co.pincoin.study.event.TransactionEvent.FundsTransferredEvent;
import kr.co.pincoin.study.event.TransactionEvent.TransactionCompletedEvent;
import kr.co.pincoin.study.event.TransactionEvent.TransactionCreatedEvent;
import kr.co.pincoin.study.event.TransactionEvent.TransactionFailedEvent;
import kr.co.pincoin.study.model.MyTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventListener {

    private final TransactionRecordService transactionRecordService;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        log.info("Transaction created: {}", event.getTransactionId());
        MyTransaction transaction = transactionRecordService.createTransaction(
            event.getTransactionId(),
            event.getFromAccountId(),
            event.getToAccountId(),
            event.getAmount()
        );
        log.info("Transaction record created: {}", transaction.getTransactionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFundsTransferred(FundsTransferredEvent event) {
        log.info("Funds transferred for transaction: {}", event.getTransactionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Transaction completed: {}", event.getTransactionId());
        notificationService.sendTransferNotification(event.getTransactionId(), event.getAmount());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleTransactionFailed(TransactionFailedEvent event) {
        log.error("Transaction failed: {} - Reason: {}", event.getTransactionId(),
            event.getReason());
    }
}