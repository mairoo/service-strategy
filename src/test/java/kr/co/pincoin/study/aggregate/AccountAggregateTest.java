package kr.co.pincoin.study.aggregate;

import java.math.BigDecimal;
import kr.co.pincoin.study.command.AccountCommands.CreateAccountCommand;
import kr.co.pincoin.study.command.AccountCommands.DebitAccountCommand;
import kr.co.pincoin.study.event.AccountEvents.AccountCreatedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyDebitedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("계좌 애그리게잇 테스트")
class AccountAggregateTest {

  private FixtureConfiguration<AccountAggregate> fixture;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(AccountAggregate.class);
  }

  @Test
  @DisplayName("신규 계좌 생성시 초기 잔액이 정상적으로 설정되어야 한다")
  void createAccount() {
    String accountId = "test-account";
    BigDecimal initialBalance = BigDecimal.valueOf(1000);

    fixture.givenNoPriorActivity()
        .when(new CreateAccountCommand(accountId, initialBalance))
        .expectEvents(new AccountCreatedEvent(accountId, initialBalance));
  }

  @Test
  @DisplayName("잔액이 충분할 경우 송금이 정상적으로 처리되어야 한다")
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
  @DisplayName("잔액이 부족할 경우 송금이 실패해야 한다")
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
  @DisplayName("음수 금액 송금 시도시 예외가 발생해야 한다")
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