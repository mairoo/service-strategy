package kr.co.pincoin.study.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transactionId; // 멱등성을 위한 식별자
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    private MyTransactionStatus status;
    private LocalDateTime createdAt;

    public MyTransaction(String transactionId, Long fromAccountId, Long toAccountId,
        BigDecimal amount) {
        this.transactionId = transactionId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.status = MyTransactionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markAsCompleted() {
        this.status = MyTransactionStatus.COMPLETED;
    }

    public void markAsFailed() {
        this.status = MyTransactionStatus.FAILED;
    }
}