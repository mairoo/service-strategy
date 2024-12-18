package kr.co.pincoin.study.repository;

import java.util.Optional;
import kr.co.pincoin.study.model.MyBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MyBalanceRepository extends JpaRepository<MyBalance, Long> {

    @Query("SELECT b FROM MyBalance b WHERE b.accountId = :accountId")
    Optional<MyBalance> findByAccountId(@Param("accountId") Long accountId);
}
