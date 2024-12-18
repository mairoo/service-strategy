package kr.co.pincoin.study.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kr.co.pincoin.study.model.MyBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MyBalanceRepository extends JpaRepository<MyBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM MyBalance b WHERE b.accountId = :accountId")
    Optional<MyBalance> findByAccountIdWithLock(@Param("accountId") Long accountId);
}
