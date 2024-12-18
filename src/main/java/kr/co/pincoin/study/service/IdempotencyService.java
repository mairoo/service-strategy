package kr.co.pincoin.study.service;

import kr.co.pincoin.study.model.MyTransaction;
import kr.co.pincoin.study.repository.MyTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final MyTransactionRepository transactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MyTransaction checkIdempotency(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId).orElse(null);
    }
}