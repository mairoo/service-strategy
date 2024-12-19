package kr.co.pincoin.study.aggregate;

import java.math.BigDecimal;
import kr.co.pincoin.study.command.AccountCommands.CreateAccountCommand;
import kr.co.pincoin.study.command.AccountCommands.CreditAccountCommand;
import kr.co.pincoin.study.command.AccountCommands.DebitAccountCommand;
import kr.co.pincoin.study.event.AccountEvents.AccountCreatedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyCreditedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyDebitedEvent;
import lombok.NoArgsConstructor;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

@Aggregate
@NoArgsConstructor
public class AccountAggregate {

  @AggregateIdentifier
  private String accountId;
  private BigDecimal balance;

  @CommandHandler
  public AccountAggregate(CreateAccountCommand command) {
    if (command.getInitialBalance().compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Initial balance cannot be negative");
    }

    AggregateLifecycle.apply(new AccountCreatedEvent(
        command.getAccountId(),
        command.getInitialBalance()
    ));
  }

  @CommandHandler
  public void handle(DebitAccountCommand command) {
    if (command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Transfer amount must be positive");
    }

    if (balance.compareTo(command.getAmount()) < 0) {
      throw new IllegalStateException("Insufficient balance");
    }

    AggregateLifecycle.apply(new MoneyDebitedEvent(
        accountId,
        command.getTargetAccountId(),
        command.getAmount(),
        command.getTransactionId()
    ));
  }

  @CommandHandler
  public void handle(CreditAccountCommand command) {
    if (command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Transfer amount must be positive");
    }

    AggregateLifecycle.apply(new MoneyCreditedEvent(
        accountId,
        command.getSourceAccountId(),
        command.getAmount(),
        command.getTransactionId()
    ));
  }

  @EventSourcingHandler
  public void on(AccountCreatedEvent event) {
    this.accountId = event.getAccountId();
    this.balance = event.getInitialBalance();
  }

  @EventSourcingHandler
  public void on(MoneyDebitedEvent event) {
    this.balance = this.balance.subtract(event.getAmount());

  }

  @EventSourcingHandler
  public void on(MoneyCreditedEvent event) {
    this.balance = this.balance.add(event.getAmount());
  }
}