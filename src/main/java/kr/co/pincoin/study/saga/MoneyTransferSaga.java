package kr.co.pincoin.study.saga;

import java.math.BigDecimal;
import kr.co.pincoin.study.command.AccountCommands.CreditAccountCommand;
import kr.co.pincoin.study.event.AccountEvents.MoneyCreditedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyDebitedEvent;
import kr.co.pincoin.study.event.TransferCompensatedEvent;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Saga
public class MoneyTransferSaga {

  private static final Logger logger = LoggerFactory.getLogger(MoneyTransferSaga.class);

  @Autowired
  private transient CommandGateway commandGateway;

  private String sourceAccountId;
  private String targetAccountId;
  private BigDecimal amount;
  private String transactionId;

  @StartSaga
  @SagaEventHandler(associationProperty = "transactionId")
  public void handle(MoneyDebitedEvent event) {
    this.sourceAccountId = event.getAccountId();
    this.targetAccountId = event.getTargetAccountId();
    this.amount = event.getAmount();
    this.transactionId = event.getTransactionId();

    commandGateway.send(new CreditAccountCommand(
        targetAccountId,
        sourceAccountId,
        amount,
        transactionId
    )).exceptionally(throwable -> {
      compensateDebit();
      return null;
    });
  }

  private void compensateDebit() {
    commandGateway.send(new CreditAccountCommand(
        sourceAccountId,
        targetAccountId,
        amount,
        transactionId + "-compensation"
    ));
  }

  @SagaEventHandler(associationProperty = "transactionId")
  public void handle(MoneyCreditedEvent event) {
    SagaLifecycle.end();
  }

  @EndSaga
  @SagaEventHandler(associationProperty = "transactionId")
  public void handle(TransferCompensatedEvent event) {
    logger.info("Transfer compensated for transaction: {}", transactionId);
  }
}