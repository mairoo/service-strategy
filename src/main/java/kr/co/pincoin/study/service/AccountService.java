package kr.co.pincoin.study.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.co.pincoin.study.command.AccountCommands.CreateAccountCommand;
import kr.co.pincoin.study.command.AccountCommands.CreditAccountCommand;
import kr.co.pincoin.study.command.AccountCommands.DebitAccountCommand;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountService {

  private final CommandGateway commandGateway;

  public CompletableFuture<String> createAccount(BigDecimal initialBalance) {
    String accountId = UUID.randomUUID().toString();
    return commandGateway.send(new CreateAccountCommand(accountId, initialBalance));
  }

  public CompletableFuture<Void> transfer(String fromAccountId, String toAccountId,
      BigDecimal amount) {
    String transactionId = UUID.randomUUID().toString();

    // 출금 처리
    return commandGateway.send(new DebitAccountCommand(
            fromAccountId, toAccountId, amount, transactionId))
        .thenCompose(r ->
            // 입금 처리
            commandGateway.send(new CreditAccountCommand(
                toAccountId, fromAccountId, amount, transactionId)));
  }
}