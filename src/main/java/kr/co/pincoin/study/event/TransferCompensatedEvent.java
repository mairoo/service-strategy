package kr.co.pincoin.study.event;

import lombok.Value;

@Value
public class TransferCompensatedEvent {

  String transactionId;
  String accountId;
  String targetAccountId;
}

