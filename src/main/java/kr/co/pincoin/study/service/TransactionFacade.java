package kr.co.pincoin.study.service;

import java.math.BigDecimal;
import kr.co.pincoin.study.model.MyTransaction;
import kr.co.pincoin.study.model.MyTransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionFacade {

    private final IdempotencyService idempotencyService;
    private final TransactionRecordService transactionRecordService;
    private final FundTransferService fundTransferService;
    private final NotificationService notificationService;

    public void transfer(String transactionId, Long fromAccountId, Long toAccountId,
        BigDecimal amount) {
        // 1. 멱등성 체크
        MyTransaction existingTx = idempotencyService.checkIdempotency(transactionId);
        if (existingTx != null) {
            if (existingTx.getStatus() == MyTransactionStatus.COMPLETED) {
                return;
            }
            throw new IllegalStateException("처리 중인 거래가 있습니다.");
        }

        // 2. 트랜잭션 생성
        MyTransaction transaction = transactionRecordService.createTransaction(
            transactionId, fromAccountId, toAccountId, amount);

        try {
            // 3. 송금 실행
            fundTransferService.transfer(fromAccountId, toAccountId, amount);

            // 4. 트랜잭션 완료 처리
            transactionRecordService.markAsCompleted(transaction);

            // 5. 알림 발송
            notificationService.sendTransferNotification(transactionId, amount);
        } catch (Exception e) {
            transactionRecordService.markAsFailed(transaction);
            throw e;
        }
    }
}
