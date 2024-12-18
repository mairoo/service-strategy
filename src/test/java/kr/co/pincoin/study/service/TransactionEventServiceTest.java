package kr.co.pincoin.study.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kr.co.pincoin.study.model.MyBalance;
import kr.co.pincoin.study.repository.MyBalanceRepository;
import kr.co.pincoin.study.repository.MyTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class TransactionEventServiceTest {

    private static final Long ACCOUNT_1_ID = 1L;
    private static final Long ACCOUNT_2_ID = 2L;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("10.00");
    private static final int CONCURRENT_THREADS = 5;

    @Autowired
    private TransactionEventService transactionEventService;

    @Autowired
    private MyBalanceRepository myBalanceRepository;

    @Autowired
    private MyTransactionRepository myTransactionRepository;

    @BeforeEach
    @Transactional(isolation = Isolation.READ_COMMITTED)
    void setUp() {
        myBalanceRepository.deleteAll();
        myTransactionRepository.deleteAll();

        MyBalance balance1 = new MyBalance(ACCOUNT_1_ID, INITIAL_BALANCE);
        MyBalance balance2 = new MyBalance(ACCOUNT_2_ID, INITIAL_BALANCE);

        myBalanceRepository.saveAll(List.of(balance1, balance2));

        if (TestTransaction.isActive()) {
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    @Test
    @DisplayName("동시 송금 요청 시 낙관적 락 동작 테스트")
    void testConcurrentTransfers() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_THREADS);
        List<Exception> exceptions = new ArrayList<>();

        // 여러 스레드에서 동시에 송금 시도
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    String transactionId = UUID.randomUUID().toString();
                    transactionEventService.transfer(
                        transactionId,
                        ACCOUNT_1_ID,
                        ACCOUNT_2_ID,
                        TRANSFER_AMOUNT
                    );
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                        System.out.println("거래 실패: " + e.getMessage());
                    }
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "모든 스레드가 시간 내에 완료되지 않았습니다.");

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        verifyTransferResults(exceptions);
    }

    @Test
    @DisplayName("동일 거래ID로 동시 요청 시 멱등성 보장 테스트")
    void testIdempotencyWithConcurrentRequests() throws InterruptedException {
        String transactionId = UUID.randomUUID().toString();
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_THREADS);
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    transactionEventService.transfer(
                        transactionId,
                        ACCOUNT_1_ID,
                        ACCOUNT_2_ID,
                        TRANSFER_AMOUNT
                    );
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completionLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        verifyIdempotencyResults();
    }

    private void verifyTransferResults(List<Exception> exceptions) {
        MyBalance account1Final = myBalanceRepository.findByAccountId(ACCOUNT_1_ID).orElseThrow();
        MyBalance account2Final = myBalanceRepository.findByAccountId(ACCOUNT_2_ID).orElseThrow();

        // 예외 분석
        for (Exception e : exceptions) {
            assertTrue(isOptimisticLockException(e),
                "예상된 낙관적 락 예외가 발생해야 합니다: " + e.getClass().getName());
        }

        // 성공한 거래 수 계산
        BigDecimal account1Difference = INITIAL_BALANCE.subtract(account1Final.getAmount());
        int successfulTransfers = account1Difference.divide(TRANSFER_AMOUNT).intValue();

        System.out.println("성공한 거래 수: " + successfulTransfers);
        System.out.println("발생한 예외 수: " + exceptions.size());

        // 검증
        assertTrue(successfulTransfers > 0 && successfulTransfers < CONCURRENT_THREADS,
            String.format("일부 트랜잭션만 성공해야 합니다. (성공: %d, 전체: %d)",
                successfulTransfers, CONCURRENT_THREADS));

        assertEquals(
            INITIAL_BALANCE.subtract(
                TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(successfulTransfers))),
            account1Final.getAmount(),
            "출금 계좌 잔액이 일치해야 합니다."
        );

        assertEquals(
            INITIAL_BALANCE.add(TRANSFER_AMOUNT.multiply(BigDecimal.valueOf(successfulTransfers))),
            account2Final.getAmount(),
            "입금 계좌 잔액이 일치해야 합니다."
        );
    }

    private void verifyIdempotencyResults() {
        MyBalance account1Final = myBalanceRepository.findByAccountId(ACCOUNT_1_ID).orElseThrow();
        MyBalance account2Final = myBalanceRepository.findByAccountId(ACCOUNT_2_ID).orElseThrow();

        assertEquals(
            INITIAL_BALANCE.subtract(TRANSFER_AMOUNT),
            account1Final.getAmount(),
            "출금 계좌에서 정확히 한 번만 차감되어야 합니다."
        );

        assertEquals(
            INITIAL_BALANCE.add(TRANSFER_AMOUNT),
            account2Final.getAmount(),
            "입금 계좌에 정확히 한 번만 입금되어야 합니다."
        );
    }

    private boolean isOptimisticLockException(Exception e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}