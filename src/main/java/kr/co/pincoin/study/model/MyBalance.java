package kr.co.pincoin.study.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private BigDecimal amount;
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public MyBalance(Long accountId, BigDecimal amount) {
        this.accountId = accountId;
        this.amount = amount;
        this.updatedAt = LocalDateTime.now();
    }

    public void decrease(BigDecimal amount) {
        if (this.amount.compareTo(amount) < 0) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }
        this.amount = this.amount.subtract(amount);
        this.updatedAt = LocalDateTime.now();
    }

    public void increase(BigDecimal amount) {
        this.amount = this.amount.add(amount);
        this.updatedAt = LocalDateTime.now();
    }
}