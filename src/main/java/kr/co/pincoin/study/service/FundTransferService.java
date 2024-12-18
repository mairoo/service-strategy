package kr.co.pincoin.study.service;

import java.math.BigDecimal;
import kr.co.pincoin.study.model.MyBalance;
import kr.co.pincoin.study.repository.MyBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FundTransferService {

    private final MyBalanceRepository balanceRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        MyBalance fromAccount = balanceRepository.findByAccountId(fromAccountId)
            .orElseThrow(() -> new IllegalArgumentException("출금 계좌가 존재하지 않습니다."));

        MyBalance toAccount = balanceRepository.findByAccountId(toAccountId)
            .orElseThrow(() -> new IllegalArgumentException("입금 계좌가 존재하지 않습니다."));

        fromAccount.decrease(amount);
        toAccount.increase(amount);

        balanceRepository.save(fromAccount);
        balanceRepository.save(toAccount);
    }
}
