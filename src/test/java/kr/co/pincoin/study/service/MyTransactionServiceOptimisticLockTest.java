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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class MyTransactionServiceOptimisticLockTest {

    private static final Long ACCOUNT_1_ID = 1L;
    private static final Long ACCOUNT_2_ID = 2L;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("10.00");
    private static final int CONCURRENT_THREADS = 5;
    @Autowired
    private MyTransactionServiceV2 myTransactionService;
    @Autowired
    private MyBalanceRepository myBalanceRepository;

    @BeforeEach
    @Transactional(isolation = Isolation.READ_COMMITTED)
    void setUp() {
        myBalanceRepository.deleteAll();

        MyBalance balance1 = new MyBalance(ACCOUNT_1_ID, INITIAL_BALANCE);
        MyBalance balance2 = new MyBalance(ACCOUNT_2_ID, INITIAL_BALANCE);

        myBalanceRepository.saveAll(List.of(balance1, balance2));

        if (TestTransaction.isActive()) {
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    @Test
    void testConcurrentTransfers() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_THREADS);
        List<Exception> exceptions = new ArrayList<>();

        // 여러 스레드에서 동시에 송금 시도
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    String transactionId = UUID.randomUUID().toString();
                    myTransactionService.transfer(
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

        // 모든 스레드를 동시에 시작
        startLatch.countDown();

        // 모든 스레드가 완료될 때까지 대기 (최대 10초)
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "모든 스레드가 시간 내에 완료되지 않았습니다.");

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // 결과 검증
        MyBalance account1Final = myBalanceRepository.findByAccountId(ACCOUNT_1_ID).orElseThrow();
        MyBalance account2Final = myBalanceRepository.findByAccountId(ACCOUNT_2_ID).orElseThrow();

        // 예외 분석 및 출력
        for (Exception e : exceptions) {
            System.out.println("발생한 예외: " + e.getClass().getName());
            if (e.getCause() != null) {
                System.out.println("예외 원인: " + e.getCause().getClass().getName());
            }
        }

        // 낙관적 락 예외가 발생했는지 확인
        boolean hasOptimisticLockException = exceptions.stream()
            .anyMatch(e -> {
                Throwable current = e;
                while (current != null) {
                    if (current instanceof ObjectOptimisticLockingFailureException) {
                        return true;
                    }
                    current = current.getCause();
                }
                return false;
            });

        assertTrue(hasOptimisticLockException, "낙관적 락 예외가 발생해야 합니다.");

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
            account1Final.getAmount(),
            INITIAL_BALANCE.subtract(TRANSFER_AMOUNT.multiply(new BigDecimal(successfulTransfers))),
            "출금 계좌 잔액이 일치해야 합니다."
        );

        assertEquals(
            account2Final.getAmount(),
            INITIAL_BALANCE.add(TRANSFER_AMOUNT.multiply(new BigDecimal(successfulTransfers))),
            "입금 계좌 잔액이 일치해야 합니다."
        );
    }

    @Test
    @Transactional(isolation = Isolation.READ_COMMITTED)
    void testIdempotency() throws InterruptedException {
        String transactionId = UUID.randomUUID().toString();
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_THREADS);
        List<Exception> exceptions = new ArrayList<>();

        // 동일한 transactionId로 여러 번 송금 시도
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    myTransactionService.transfer(
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

        // 결과 검증
        MyBalance account1Final = myBalanceRepository.findByAccountId(ACCOUNT_1_ID).orElseThrow();
        MyBalance account2Final = myBalanceRepository.findByAccountId(ACCOUNT_2_ID).orElseThrow();

        // 정확히 한 번만 처리되었는지 확인
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
}