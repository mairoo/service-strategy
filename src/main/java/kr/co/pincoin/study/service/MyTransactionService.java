package kr.co.pincoin.study.service;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyTransactionService {

    private final MyBalanceRepository myBalanceRepository;
    private final MyTransactionRepository myTransactionRepository;

    @Transactional
    public void transfer(String transactionId, Long fromAccountId, Long toAccountId,
        BigDecimal amount) {
        // 멱등성 체크는 별도 트랜잭션으로 처리
        MyTransaction existingTx = checkIdempotency(transactionId);
        if (existingTx != null) {
            if (existingTx.getStatus() == MyTransactionStatus.COMPLETED) {
                return; // 이미 처리된 거래
            }
            throw new IllegalStateException("처리 중인 거래가 있습니다.");
        }

        MyTransaction transaction = createTransaction(transactionId, fromAccountId, toAccountId,
            amount);

        try {
            executeTransfer(fromAccountId, toAccountId, amount);
            markTransactionComplete(transaction);
            sendNotification(transactionId, amount);
        } catch (Exception e) {
            markTransactionFailed(transaction);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected MyTransaction checkIdempotency(String transactionId) {
        return myTransactionRepository.findByTransactionId(transactionId).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected MyTransaction createTransaction(String transactionId, Long fromAccountId,
        Long toAccountId,
        BigDecimal amount) {
        MyTransaction myTransaction = new MyTransaction(transactionId, fromAccountId, toAccountId,
            amount);
        return myTransactionRepository.save(myTransaction);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    protected void executeTransfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        MyBalance fromAccount = myBalanceRepository.findByAccountId(fromAccountId)
            .orElseThrow(() -> new IllegalArgumentException("출금 계좌가 존재하지 않습니다."));

        MyBalance toAccount = myBalanceRepository.findByAccountId(toAccountId)
            .orElseThrow(() -> new IllegalArgumentException("입금 계좌가 존재하지 않습니다."));

        fromAccount.decrease(amount);
        toAccount.increase(amount);

        myBalanceRepository.save(fromAccount);
        myBalanceRepository.save(toAccount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markTransactionComplete(MyTransaction myTransaction) {
        myTransaction.markAsCompleted();
        myTransactionRepository.save(myTransaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void markTransactionFailed(MyTransaction myTransaction) {
        myTransaction.markAsFailed();
        myTransactionRepository.save(myTransaction);
    }

    private void sendNotification(String transactionId, BigDecimal amount) {
        CompletableFuture.runAsync(() -> sendEmailNotification(transactionId, amount));
    }

    private void sendEmailNotification(String transactionId, BigDecimal amount) {
        try {
            Thread.sleep(2000);
            log.debug("이메일 발송 완료. 거래ID: {}, 송금액: {}", transactionId, amount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("이메일 발송 중 인터럽트 발생", e);
        }
    }
}