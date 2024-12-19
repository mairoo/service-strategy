package kr.co.pincoin.study.aggregate;

import java.math.BigDecimal;
import kr.co.pincoin.study.command.AccountCommands.CreateAccountCommand;
import kr.co.pincoin.study.command.AccountCommands.DebitAccountCommand;
import kr.co.pincoin.study.event.AccountEvents.AccountCreatedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyDebitedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountAggregateTest {

  private FixtureConfiguration<AccountAggregate> fixture;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(AccountAggregate.class);
  }

  @Test
  void createAccount() {
    String accountId = "test-account";
    BigDecimal initialBalance = BigDecimal.valueOf(1000);

    fixture.givenNoPriorActivity()
        .when(new CreateAccountCommand(accountId, initialBalance))
        .expectEvents(new AccountCreatedEvent(accountId, initialBalance));
  }

  @Test
  void transferMoney_withSufficientBalance() {
    String sourceAccountId = "source-account";
    String targetAccountId = "target-account";
    String transactionId = "tx-1";
    BigDecimal initialBalance = BigDecimal.valueOf(1000);
    BigDecimal transferAmount = BigDecimal.valueOf(500);

    fixture.given(new AccountCreatedEvent(sourceAccountId, initialBalance))
        .when(new DebitAccountCommand(sourceAccountId, targetAccountId, transferAmount,
            transactionId))
        .expectEvents(
            new MoneyDebitedEvent(sourceAccountId, targetAccountId, transferAmount, transactionId));
  }

  @Test
  void transferMoney_withInsufficientBalance() {
    String sourceAccountId = "source-account";
    String targetAccountId = "target-account";
    String transactionId = "tx-1";
    BigDecimal initialBalance = BigDecimal.valueOf(100);
    BigDecimal transferAmount = BigDecimal.valueOf(500);

    fixture.given(new AccountCreatedEvent(sourceAccountId, initialBalance))
        .when(new DebitAccountCommand(sourceAccountId, targetAccountId, transferAmount,
            transactionId))
        .expectException(IllegalStateException.class);
  }

  @Test
  void transferMoney_withNegativeAmount() {
    String sourceAccountId = "source-account";
    String targetAccountId = "target-account";
    String transactionId = "tx-1";
    BigDecimal initialBalance = BigDecimal.valueOf(1000);
    BigDecimal transferAmount = BigDecimal.valueOf(-500);

    fixture.given(new AccountCreatedEvent(sourceAccountId, initialBalance))
        .when(new DebitAccountCommand(sourceAccountId, targetAccountId, transferAmount,
            transactionId))
        .expectException(IllegalArgumentException.class);
  }
}