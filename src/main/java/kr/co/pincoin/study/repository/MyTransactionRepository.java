package kr.co.pincoin.study.repository;

import java.util.Optional;
import kr.co.pincoin.study.model.MyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MyTransactionRepository extends JpaRepository<MyTransaction, Long> {

    Optional<MyTransaction> findByTransactionId(String transactionId);
}
