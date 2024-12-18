# 다양한 서비스 로직 구현 전략
목표:
- 잔액의 무결성 보장
- 거래의 멱등성 보장
- 비동기 통보 이메일 발송(학습 목적 코드이므로 실제로는 2초 지연 후 로그 출력)

서비스 로직의 진화 과정

1. 트랜잭션 스크립트 패턴
2. 퍼사드 패턴
3. 스프링 이벤트 패턴
4. axon 이벤트 소싱 패턴

## 트랜잭션 스크립트 패턴
### transfer 메소드 흐름

```
transfer (@Transactional)
    └── checkIdempotency (@Transactional(REQUIRES_NEW))
    └── createTransaction (@Transactional(REQUIRES_NEW))
    └── executeTransfer (@Transactional(REQUIRED))
    └── markTransactionComplete (@Transactional(REQUIRES_NEW))
```

## 퍼사드 패턴
```
TransactionFacade
   ├── IdempotencyService (@REQUIRES_NEW)
   |    └── checkIdempotency()
   |
   ├── TransactionRecordService (@REQUIRES_NEW)
   |    ├── createTransaction()
   |    ├── markAsCompleted()
   |    └── markAsFailed()
   |
   ├── FundTransferService (@REQUIRED)
   |    └── transfer()
   |
   └── NotificationService (No Transaction)
        └── sendTransferNotification()
```

## 스프링 이벤트
```
@TransactionalEventListener
- TransactionCreatedEvent
- TransferCompletedEvent
- TransferFailedEvent
```
## axon 이벤트 소싱
```
Commands:
- CreateTransactionCommand
- ExecuteTransferCommand
- CompleteTransactionCommand

Events:
- TransactionCreatedEvent
- TransferExecutedEvent
- TransactionCompletedEvent
```

# 결론

서비스 로직의 발전 진화 과정을 `@Transactional`, `@Transactional(propagation = Propagation.REQUIRED)`,
`@Transactional(propagation = Propagation.REQUIRES_NEW)` 애노테이션 함수 단위로 표현할 수 없다면 퍼사드, 스프링 이벤트, axon
이벤트 소싱은 모두 사상누각이 된다.

이벤트 기반 아키텍처로 발전하기 위해서는 트랜잭션 경계를 명확히 나누고 REQUIRES_NEW와 REQUIRED를 적용하는 것이 출발점이 된다.

## 트랜잭션 독립성 보장

- REQUIRES_NEW로 분리된 트랜잭션은 실패해도 다른 트랜잭션에 영향을 주지 않음
- 각 이벤트가 독립적으로 처리될 수 있는 기반이 됨

## 이벤트 발행 시점 제어

- 각 트랜잭션이 커밋되는 시점에 이벤트 발행 가능
- REQUIRES_NEW로 분리된 트랜잭션은 즉시 커밋되므로 이벤트도 즉시 발행 가능

## Saga 패턴 적용 용이

- 각 트랜잭션 단계가 분리되어 있어 보상 트랜잭션 구현이 쉬움
- 장애 발생 시 특정 지점부터 재시도 가능

## 이벤트 소싱을 위한 준비

- 각 단계가 독립적인 커맨드와 이벤트로 변환 가능
- 트랜잭션 경계가 곧 애그리거트 경계가 될 수 있음
