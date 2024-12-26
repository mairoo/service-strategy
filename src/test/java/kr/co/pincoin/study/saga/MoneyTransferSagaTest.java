package kr.co.pincoin.study.saga;

import kr.co.pincoin.study.command.AccountCommands.CreditAccountCommand;
import kr.co.pincoin.study.event.AccountEvents.MoneyCreditedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyDebitedEvent;
import kr.co.pincoin.study.event.TransferCompensatedEvent;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

@DisplayName("송금 사가 테스트")
class MoneyTransferSagaTest {
  private SagaTestFixture<MoneyTransferSaga> fixture;

  @BeforeEach
  void setUp() {
    fixture = new SagaTestFixture<>(MoneyTransferSaga.class);
  }

  @Test
  @DisplayName("송금 시작 시 입금 커맨드가 발행되어야 한다")
  void whenMoneyDebitedThenCreditCommandShouldBeDispatched() {
    String sourceAccountId = "source-123";
    String targetAccountId = "target-456";
    String transactionId = "tx-789";
    BigDecimal amount = BigDecimal.valueOf(500);

    MoneyDebitedEvent debitedEvent = new MoneyDebitedEvent(
        sourceAccountId,
        targetAccountId,
        amount,
        transactionId
    );

    CreditAccountCommand expectedCommand = new CreditAccountCommand(
        targetAccountId,
        sourceAccountId,
        amount,
        transactionId
    );

    fixture.givenNoPriorActivity()
        .whenPublishingA(debitedEvent)
        .expectDispatchedCommands(expectedCommand);
  }

  @Test
  @DisplayName("입금 성공 시 사가가 종료되어야 한다")
  void whenMoneyCreditedThenSagaShouldEnd() {
    String sourceAccountId = "source-123";
    String targetAccountId = "target-456";
    String transactionId = "tx-789";
    BigDecimal amount = BigDecimal.valueOf(500);

    MoneyDebitedEvent debitedEvent = new MoneyDebitedEvent(
        sourceAccountId,
        targetAccountId,
        amount,
        transactionId
    );

    MoneyCreditedEvent creditedEvent = new MoneyCreditedEvent(
        targetAccountId,
        sourceAccountId,
        amount,
        transactionId
    );

    fixture.givenAggregate(transactionId)
        .published(debitedEvent)
        .whenPublishingA(creditedEvent)
        .expectActiveSagas(0);
  }

  @Test
  @DisplayName("입금 실패 시 보상 트랜잭션이 실행되어야 한다")
  void whenCreditFailsThenCompensationShouldBeTriggered() {
    String sourceAccountId = "source-123";
    String targetAccountId = "target-456";
    String transactionId = "tx-789";
    BigDecimal amount = BigDecimal.valueOf(500);

    MoneyDebitedEvent debitedEvent = new MoneyDebitedEvent(
        sourceAccountId,
        targetAccountId,
        amount,
        transactionId
    );

    CreditAccountCommand compensationCommand = new CreditAccountCommand(
        sourceAccountId,
        targetAccountId,
        amount,
        transactionId + "-compensation"
    );

    fixture.givenAggregate(transactionId)
        .published(debitedEvent)
        .whenPublishingA(new MoneyCreditedEvent(
            targetAccountId,
            sourceAccountId,
            amount,
            transactionId))
        .expectDispatchedCommands(compensationCommand);
  }

  @Test
  @DisplayName("보상 트랜잭션 완료 시 사가가 종료되어야 한다")
  void whenCompensationCompletedThenSagaShouldEnd() {
    String sourceAccountId = "source-123";
    String targetAccountId = "target-456";
    String transactionId = "tx-789";
    BigDecimal amount = BigDecimal.valueOf(500);

    MoneyDebitedEvent debitedEvent = new MoneyDebitedEvent(
        sourceAccountId,
        targetAccountId,
        amount,
        transactionId
    );

    TransferCompensatedEvent compensatedEvent = new TransferCompensatedEvent(
        transactionId,
        sourceAccountId,
        targetAccountId
    );

    fixture.givenAggregate(transactionId)
        .published(debitedEvent)
        .whenPublishingA(compensatedEvent)
        .expectActiveSagas(0);
  }
}