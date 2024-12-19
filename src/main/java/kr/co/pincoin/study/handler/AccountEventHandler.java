package kr.co.pincoin.study.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.co.pincoin.study.command.AccountCommands.CreditAccountCommand;
import kr.co.pincoin.study.event.AccountEvents.AccountCreatedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyCreditedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyDebitedEvent;
import kr.co.pincoin.study.event.AccountEvents.TransferCompletedEvent;
import kr.co.pincoin.study.event.AccountEvents.TransferFailedEvent;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountEventHandler {

  private final CommandGateway commandGateway;

  // 처리된 트랜잭션 ID를 추적하기 위한 맵 (실제로는 DB를 사용해야 함)
  private final Map<String, Boolean> processedTransactions = new ConcurrentHashMap<>();

  @EventHandler
  public void on(AccountCreatedEvent event) {
    System.out.println("Account created: " + event.getAccountId() +
        " with balance: " + event.getInitialBalance());
  }

  @EventHandler
  public void on(MoneyDebitedEvent event) {
    // 출금이 성공하면 입금 커맨드를 발행
    if (!processedTransactions.containsKey(event.getTransactionId())) {
      commandGateway.send(new CreditAccountCommand(
          event.getTargetAccountId(),
          event.getAccountId(),
          event.getAmount(),
          event.getTransactionId()
      ));
      processedTransactions.put(event.getTransactionId(), true);
    }

    System.out.println("Money debited from account: " + event.getAccountId() +
        " amount: " + event.getAmount());
  }

  @EventHandler
  public void on(MoneyCreditedEvent event) {
    System.out.println("Money credited to account: " + event.getAccountId() +
        " amount: " + event.getAmount());

    // 입금이 완료되면 전체 송금 완료 이벤트를 발행할 수 있음
    System.out.println("Transfer completed for transaction: " + event.getTransactionId());
  }

  @EventHandler
  public void on(TransferCompletedEvent event) {
    System.out.println("Transfer completed from " + event.getSourceAccountId() +
        " to " + event.getTargetAccountId() +
        " amount: " + event.getAmount());
  }

  @EventHandler
  public void on(TransferFailedEvent event) {
    System.out.println("Transfer failed from " + event.getSourceAccountId() +
        " to " + event.getTargetAccountId() +
        " amount: " + event.getAmount() +
        " reason: " + event.getReason());

    // 실패한 트랜잭션 ID 제거
    processedTransactions.remove(event.getTransactionId());
  }
}