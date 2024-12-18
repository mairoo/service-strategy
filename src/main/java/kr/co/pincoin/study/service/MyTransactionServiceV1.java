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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyTransactionServiceV1 {

    private final MyBalanceRepository myBalanceRepository;
    private final MyTransactionRepository myTransactionRepository;

    @Transactional
    public void transfer(String transactionId, Long fromAccountId, Long toAccountId,
        BigDecimal amount) {
        // 멱등성 체크
        MyTransaction existingTx = myTransactionRepository.findByTransactionId(transactionId)
            .orElse(null);

        if (existingTx != null) {
            if (existingTx.getStatus() == MyTransactionStatus.COMPLETED) {
                return;
            }
            throw new IllegalStateException("처리 중인 거래가 있습니다.");
        }

        // 거래 생성
        MyTransaction transaction = new MyTransaction(transactionId, fromAccountId, toAccountId,
            amount);
        myTransactionRepository.save(transaction);

        try {
            // 잔액 변경
            MyBalance fromAccount = myBalanceRepository.findByAccountId(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("출금 계좌가 존재하지 않습니다."));
            MyBalance toAccount = myBalanceRepository.findByAccountId(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("입금 계좌가 존재하지 않습니다."));

            fromAccount.decrease(amount);
            toAccount.increase(amount);

            // 거래 완료 표시
            transaction.markAsCompleted();

            // 비동기 알림
            CompletableFuture.runAsync(() -> sendEmailNotification(transactionId, amount));

        } catch (Exception e) {
            transaction.markAsFailed();
            throw e;
        }
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