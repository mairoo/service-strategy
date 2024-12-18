package kr.co.pincoin.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kr.co.pincoin.study.model.MyBalance;
import kr.co.pincoin.study.model.MyTransaction;
import kr.co.pincoin.study.model.MyTransactionStatus;
import kr.co.pincoin.study.repository.MyBalanceRepository;
import kr.co.pincoin.study.repository.MyTransactionRepository;
import org.junit.jupiter.api.AfterEach;
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

    private ExecutorService executorService;
    private CountDownLatch latch;

    @BeforeEach
    void setUp() {
        // accountId와 amount만 설정하여 새로운 MyBalance 생성
        fromBalance = new MyBalance(1L, new BigDecimal("1000.00"));
        toBalance = new MyBalance(2L, new BigDecimal("0.00"));

        // 저장 시 ID는 자동 생성됨
        fromBalance = myBalanceRepository.saveAndFlush(fromBalance);
        toBalance = myBalanceRepository.saveAndFlush(toBalance);

        // 확인을 위한 출력
        System.out.println("Initial fromBalance - id: " + fromBalance.getId() +
            ", accountId: " + fromBalance.getAccountId() +
            ", amount: " + fromBalance.getAmount());

        executorService = Executors.newFixedThreadPool(2);
        latch = new CountDownLatch(2);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
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

    @Test
    @DisplayName("동시에 같은 계좌에서 출금 시도시 낙관적 락으로 인한 실패 확인")
    void concurrentTransferWithOptimisticLock() throws InterruptedException {
        // given
        String transactionId1 = UUID.randomUUID().toString();
        String transactionId2 = UUID.randomUUID().toString();
        BigDecimal amount = new BigDecimal("700.00");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        // when
        Future<?> future1 = executorService.submit(() -> {
            try {
                startLatch.await();
                System.out.println("Transaction 1 starting...");
                myTransactionService.transfer(transactionId1, fromBalance.getAccountId(),
                    toBalance.getAccountId(), amount);
                System.out.println("Transaction 1 completed successfully");
                successCount.incrementAndGet();
            } catch (Exception e) {
                System.out.println("Transaction 1 failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                failureCount.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        Future<?> future2 = executorService.submit(() -> {
            try {
                startLatch.await();
                System.out.println("Transaction 2 starting...");
                myTransactionService.transfer(transactionId2, fromBalance.getAccountId(),
                    toBalance.getAccountId(), amount);
                System.out.println("Transaction 2 completed successfully");
                successCount.incrementAndGet();
            } catch (Exception e) {
                System.out.println("Transaction 2 failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                failureCount.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        System.out.println("Both transactions started");

        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        System.out.println("Test completion status: " + completed +
            ", successCount: " + successCount.get() +
            ", failureCount: " + failureCount.get());

        // 최종 상태 출력
        MyBalance finalFromBalance = myBalanceRepository.findById(fromBalance.getId()).orElseThrow();
        System.out.println("Final fromBalance: amount=" + finalFromBalance.getAmount() +
            ", version=" + finalFromBalance.getVersion());

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        assertThat(finalFromBalance.getAmount()).isEqualTo(new BigDecimal("300.00"));
        assertThat(myBalanceRepository.findById(toBalance.getId()).orElseThrow().getAmount())
            .isEqualTo(new BigDecimal("700.00"));
    }
}