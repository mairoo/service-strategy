package kr.co.pincoin.study.command;

import java.math.BigDecimal;
import lombok.Value;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public class AccountCommands {

  @Value
  public static class CreateAccountCommand {

    @TargetAggregateIdentifier
    String accountId;
    BigDecimal initialBalance;
  }

  @Value
  public static class DebitAccountCommand {

    @TargetAggregateIdentifier
    String accountId;
    String targetAccountId;
    BigDecimal amount;
    String transactionId;
  }

  @Value
  public static class CreditAccountCommand {

    @TargetAggregateIdentifier
    String accountId;
    String sourceAccountId;
    BigDecimal amount;
    String transactionId;
  }
}