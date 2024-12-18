package kr.co.pincoin.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import kr.co.pincoin.study.model.MyBalance;
import kr.co.pincoin.study.model.MyTransaction;
import kr.co.pincoin.study.model.MyTransactionStatus;
import kr.co.pincoin.study.repository.MyBalanceRepository;
import kr.co.pincoin.study.repository.MyTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MyTransactionServiceTest {

    @Autowired
    private MyTransactionService myTransactionService;

    @Autowired
    private MyBalanceRepository myBalanceRepository;

    @Autowired
    private MyTransactionRepository myTransactionRepository;

    private MyBalance fromBalance;
    private MyBalance toBalance;

    @BeforeEach
    void setUp() {
        // accountId와 amount만 설정하여 새로운 MyBalance 생성
        fromBalance = new MyBalance(1L, new BigDecimal("1000.00"));
        toBalance = new MyBalance(2L, new BigDecimal("0.00"));

        // 저장 시 ID는 자동 생성됨
        fromBalance = myBalanceRepository.saveAndFlush(fromBalance);
        toBalance = myBalanceRepository.saveAndFlush(toBalance);
    }

    @Test
    @DisplayName("정상적인 송금 처리")
    void transferSuccess() {
        // given
        String transactionId = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("500.00");

        // when
        myTransactionService.transfer(transactionId, fromBalance.getAccountId(),
            toBalance.getAccountId(), amount);

        // then
        MyBalance updatedFromMyBalance = myBalanceRepository.findById(fromBalance.getId())
            .orElseThrow();
        MyBalance updatedToMyBalance = myBalanceRepository.findById(toBalance.getId())
            .orElseThrow();
        MyTransaction myTransaction = myTransactionRepository.findByTransactionId(transactionId)
            .orElseThrow();

        assertThat(updatedFromMyBalance.getAmount()).isEqualTo(new BigDecimal("500.00"));
        assertThat(updatedToMyBalance.getAmount()).isEqualTo(new BigDecimal("500.00"));
        assertThat(myTransaction.getStatus()).isEqualTo(MyTransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("잔액 부족으로 송금 실패")
    void transferFailDueToInsufficientBalance() {
        // given
        String transactionId = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("1500.00");

        // when & then
        assertThatThrownBy(() ->
            myTransactionService.transfer(transactionId, fromBalance.getAccountId(),
                toBalance.getAccountId(), amount)
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("잔액이 부족합니다.");

        MyTransaction myTransaction = myTransactionRepository.findByTransactionId(transactionId)
            .orElseThrow();
        assertThat(myTransaction.getStatus()).isEqualTo(MyTransactionStatus.FAILED);
    }

    @Test
    @DisplayName("멱등성 체크 - 동일 거래 재시도 시 무시")
    void idempotencyCheck() {
        // given
        String transactionId = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("100.00");

        // when
        myTransactionService.transfer(transactionId, fromBalance.getAccountId(),
            toBalance.getAccountId(), amount);
        myTransactionService.transfer(transactionId, fromBalance.getAccountId(),
            toBalance.getAccountId(), amount);

        // then
        MyBalance updatedFromMyBalance = myBalanceRepository.findById(fromBalance.getId())
            .orElseThrow();
        MyBalance updatedToMyBalance = myBalanceRepository.findById(toBalance.getId())
            .orElseThrow();

        assertThat(updatedFromMyBalance.getAmount()).isEqualTo(new BigDecimal("900.00"));
        assertThat(updatedToMyBalance.getAmount()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("존재하지 않는 출금 계좌")
    void nonExistentFromAccount() {
        // given
        String transactionId = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("100.00");
        Long nonExistentAccountId = 999L;

        // when & then
        assertThatThrownBy(() ->
            myTransactionService.transfer(transactionId, nonExistentAccountId,
                toBalance.getAccountId(), amount)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("출금 계좌가 존재하지 않습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 입금 계좌")
    void nonExistentToAccount() {
        // given
        String transactionId = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("100.00");
        Long nonExistentAccountId = 999L;

        // when & then
        assertThatThrownBy(() ->
            myTransactionService.transfer(transactionId, fromBalance.getAccountId(),
                nonExistentAccountId, amount)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("입금 계좌가 존재하지 않습니다.");
    }
}