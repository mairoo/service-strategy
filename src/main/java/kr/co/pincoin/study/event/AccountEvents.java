package kr.co.pincoin.study.event;

import java.math.BigDecimal;
import lombok.Value;

public class AccountEvents {

  @Value
  public static class AccountCreatedEvent {

    String accountId;
    BigDecimal initialBalance;
  }

  @Value
  public static class MoneyDebitedEvent {

    String accountId;
    String targetAccountId;
    BigDecimal amount;
    String transactionId;
  }

  @Value
  public static class MoneyCreditedEvent {

    String accountId;
    String sourceAccountId;
    BigDecimal amount;
    String transactionId;
  }

  @Value
  public static class TransferCompletedEvent {

    String sourceAccountId;
    String targetAccountId;
    BigDecimal amount;
    String transactionId;
  }

  @Value
  public static class TransferFailedEvent {

    String sourceAccountId;
    String targetAccountId;
    BigDecimal amount;
    String transactionId;
    String reason;
  }
}