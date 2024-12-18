package kr.co.pincoin.study.service;


import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    public void sendTransferNotification(String transactionId, BigDecimal amount) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                log.debug("이메일 발송 완료. 거래ID: {}, 송금액: {}", transactionId, amount);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("이메일 발송 중 인터럽트 발생", e);
            }
        });
    }
}