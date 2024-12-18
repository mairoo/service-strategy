package kr.co.pincoin.study.service;

import java.math.BigDecimal;
import kr.co.pincoin.study.model.MyTransaction;
import kr.co.pincoin.study.repository.MyTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionRecordService {

    private final MyTransactionRepository transactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MyTransaction createTransaction(String transactionId, Long fromAccountId,
        Long toAccountId, BigDecimal amount) {
        MyTransaction transaction = new MyTransaction(transactionId, fromAccountId, toAccountId,
            amount);
        return transactionRepository.save(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsCompleted(MyTransaction transaction) {
        transaction.markAsCompleted();
        transactionRepository.save(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(MyTransaction transaction) {
        transaction.markAsFailed();
        transactionRepository.save(transaction);
    }
}