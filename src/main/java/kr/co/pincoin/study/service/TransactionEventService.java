package kr.co.pincoin.study.service;

import java.math.BigDecimal;
import kr.co.pincoin.study.model.MyTransaction;
import kr.co.pincoin.study.model.MyTransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionEventService {

    private final IdempotencyService idempotencyService;
    private final FundTransferService fundTransferService;
    private final TransactionEventPublisher eventPublisher;

    @Transactional
    public void transfer(String transactionId, Long fromAccountId, Long toAccountId,
        BigDecimal amount) {
        try {
            // 멱등성 체크
            MyTransaction existingTx = idempotencyService.checkIdempotency(transactionId);
            if (existingTx != null) {
                if (existingTx.getStatus() == MyTransactionStatus.COMPLETED) {
                    return;
                }
                throw new IllegalStateException("처리 중인 거래가 있습니다.");
            }

            // 트랜잭션 생성 이벤트 발행
            eventPublisher.publishTransactionCreated(transactionId, fromAccountId, toAccountId,
                amount);

            // 송금 실행
            fundTransferService.transfer(fromAccountId, toAccountId, amount);
            eventPublisher.publishFundsTransferred(transactionId, fromAccountId, toAccountId,
                amount);

            // 트랜잭션 완료 이벤트 발행
            eventPublisher.publishTransactionCompleted(transactionId, amount);

        } catch (Exception e) {
            eventPublisher.publishTransactionFailed(transactionId, e.getMessage());
            throw e;
        }
    }
}
