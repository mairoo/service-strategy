package kr.co.pincoin.study.service;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import kr.co.pincoin.study.model.MyBalance;
import kr.co.pincoin.study.model.MyTransaction;
import kr.co.pincoin.study.model.MyTransactionStatus;
import kr.co.pincoin.study.repository.MyBalanceRepository;
import kr.co.pincoin.study.repository.MyTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class MyTransactionService {

    private final MyBalanceRepository myBalanceRepository;

    private final MyTransactionRepository myTransactionRepository;

    public void transfer(String transactionId, Long fromAccountId, Long toAccountId,
        BigDecimal amount) {
        MyTransaction myTransaction = checkIdempotency(transactionId);
        if (myTransaction != null) {
            return;
        }

        myTransaction = createTransaction(transactionId, fromAccountId, toAccountId, amount);

        try {
            processWithdrawal(fromAccountId, amount);
            processDeposit(toAccountId, amount);

            markTransactionComplete(myTransaction);
            sendNotification(transactionId, amount);

        } catch (Exception e) {
            markTransactionFailed(myTransaction);
            throw e;
        }
    }

    private MyTransaction checkIdempotency(String transactionId) {
        MyTransaction existingTx = myTransactionRepository.findByTransactionId(transactionId)
            .orElse(null);

        if (existingTx != null) {
            if (existingTx.getStatus() == MyTransactionStatus.COMPLETED) {
                return existingTx; // 이미 처리된 거래
            }
            throw new IllegalStateException("처리 중인 거래가 있습니다.");
        }
        return null;
    }

    private MyTransaction createTransaction(String transactionId, Long fromAccountId,
        Long toAccountId,
        BigDecimal amount) {
        MyTransaction myTransaction = new MyTransaction(transactionId, fromAccountId, toAccountId,
            amount);
        return myTransactionRepository.save(myTransaction);
    }

    private void processWithdrawal(Long accountId, BigDecimal amount) {
        MyBalance myBalance = myBalanceRepository.findByAccountIdWithLock(accountId)
            .orElseThrow(() -> new IllegalArgumentException("출금 계좌가 존재하지 않습니다."));

        myBalance.decrease(amount);
    }

    private void processDeposit(Long accountId, BigDecimal amount) {
        MyBalance myBalance = myBalanceRepository.findByAccountIdWithLock(accountId)
            .orElseThrow(() -> new IllegalArgumentException("입금 계좌가 존재하지 않습니다."));

        myBalance.increase(amount);
    }

    private void markTransactionComplete(MyTransaction myTransaction) {
        myTransaction.markAsCompleted();
        myTransactionRepository.save(myTransaction);
    }

    private void markTransactionFailed(MyTransaction myTransaction) {
        myTransaction.markAsFailed();
        myTransactionRepository.save(myTransaction);
    }

    private void sendNotification(String transactionId, BigDecimal amount) {
        CompletableFuture.runAsync(() -> sendEmailNotification(transactionId, amount));
    }

    private void sendEmailNotification(String transactionId, BigDecimal amount) {
        try {
            Thread.sleep(2000); // 2초 지연
            log.debug("이메일 발송 완료. 거래ID: {}, 송금액: {}", transactionId, amount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("이메일 발송 중 인터럽트 발생", e);
        }
    }
}
