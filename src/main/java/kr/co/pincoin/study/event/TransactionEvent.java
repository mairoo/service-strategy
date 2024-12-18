package kr.co.pincoin.study.event;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class TransactionEvent {

    @Getter
    @RequiredArgsConstructor
    public static class TransactionCreatedEvent {

        private final String transactionId;
        private final Long fromAccountId;
        private final Long toAccountId;
        private final BigDecimal amount;
    }

    @Getter
    @RequiredArgsConstructor
    public static class FundsTransferredEvent {

        private final String transactionId;
        private final Long fromAccountId;
        private final Long toAccountId;
        private final BigDecimal amount;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TransactionCompletedEvent {

        private final String transactionId;
        private final BigDecimal amount;
    }

    @Getter
    @RequiredArgsConstructor
    public static class TransactionFailedEvent {

        private final String transactionId;
        private final String reason;
    }
}
