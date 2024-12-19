# 다양한 서비스 로직 구현 전략

목표:

- 잔액의 무결성 보장
- 거래의 멱등성 보장
- 비동기 통보 이메일 발송(학습 목적 코드이므로 실제로는 2초 지연 후 로그 출력)

## 서비스 로직의 진화 과정

1. 트랜잭션 스크립트 패턴
2. 퍼사드 패턴
3. 스프링 이벤트 패턴
4. axon 이벤트 소싱 패턴

## 타당성 분석 검토

### 트랜잭션 스크립트 패턴 → 퍼사드 패턴으로의 확장

- 확장 사유:
    - 비즈니스 로직이 복잡해지면서 단순 트랜잭션 스크립트로는 관리가 어려워짐
    - 여러 서비스 레이어의 조합이 필요한 복잡한 유스케이스 증가
    - 서비스 레이어에 대한 단일 진입점 필요성

### 퍼사드 패턴 → 스프링 이벤트로의 확장

- 확장 사유:
    - 강한 결합도를 가진 서비스 간 의존성을 낮출 필요성
    - 부가적인 기능(알림, 로깅 등)을 메인 비즈니스 로직과 분리
    - 비동기 처리가 필요한 케이스 증가

### 스프링 이벤트 → Axon 이벤트 소싱으로의 확장

- 확장 사유:
    - 도메인 이벤트의 히스토리 추적 필요성
    - 마이크로서비스 아키텍처로의 전환 고려
    - 복잡한 분산 트랜잭션 처리 필요성

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
   ├── TransactionPhase.AFTER_COMMIT
   │     └── 성공 시 후처리(알림 발송 등)
   └── TransactionPhase.AFTER_ROLLBACK
         └── 실패 시 후처리(보상 트랜잭션 등)
```

### 트랜잭션의 원자성과 일관성 보장

```
@REQUIRES_NEW, @REQUIRED
- 트랜잭션 경계를 명시적으로 분리/참여
- 커밋/롤백의 단위를 명확하게 제어
- 예: 멱등성 체크는 독립적으로(@REQUIRES_NEW), 이체는 함께(@REQUIRED)

TransactionPhase.AFTER_COMMIT, AFTER_ROLLBACK, BEFORE_COMMIT
- 트랜잭션 생명주기의 특정 시점에 로직 실행
- 트랜잭션 상태에 따른 후처리 보장
- 예: 이체 성공 시 알림(AFTER_COMMIT), 실패 시 보상(AFTER_ROLLBACK)
```

### 스프링 트랜잭션 처리 전략과 스프링 이벤트 처리 전략의 공통된 지향점

```
데이터 무결성 보장
- @REQUIRES_NEW: 독립적인 트랜잭션으로 분리하여 보장
- AFTER_COMMIT: 트랜잭션이 완전히 커밋된 후 후속 처리

실패 시나리오 대응
- @REQUIRED: 부모 트랜잭션과 함께 롤백
- AFTER_ROLLBACK: 실패 시 보상 트랜잭션 실행

작업의 순서 보장
- @REQUIRES_NEW + @REQUIRED: 명시적인 실행 순서
- BEFORE_COMMIT, AFTER_COMMIT: 암묵적인 실행 순서
```

### 사용 패턴의 유사성

```
멱등성 처리:
@REQUIRES_NEW를 통한 체크:
  checkIdempotency(@REQUIRES_NEW)
  -> executeTransfer(@REQUIRED)

이벤트를 통한 체크:
  @TransactionalEventListener(phase = BEFORE_COMMIT)
  handleIdempotencyCheck()
  -> executeTransfer()
  -> AFTER_COMMIT/AFTER_ROLLBACK

상태 변경:
@REQUIRES_NEW를 통한 처리:
  executeTransfer(@REQUIRED)
  -> markAsCompleted(@REQUIRES_NEW)

이벤트를 통한 처리:
  executeTransfer()
  -> AFTER_COMMIT: markAsCompleted()
  -> AFTER_ROLLBACK: markAsFailed()
```

### 보상 이벤트 처리 방법

#### 다른 이벤트 발행

```java

@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
public void handleTransactionFailed(TransactionFailedEvent event) {
  // 새로운 보상 이벤트 발행
  eventPublisher.publishEvent(new CompensationEvent(event.getTransactionId()));
}
```

#### 보상 트랜잭션 서비스 직접 실행

```java

@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handleTransactionFailed(TransactionFailedEvent event) {
  // 실패한 트랜잭션에 대한 보상 처리
  compensationService.process(event.getTransactionId());
}
```

### 비동기 보상 처리

```java

@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
public void handleTransactionFailed(TransactionFailedEvent event) {
  // 비동기로 보상 처리 큐에 메시지 전송
  compensationQueueService.sendCompensationMessage(event.getTransactionId());
}
```

### 상태 업데이트 (현재 예시 프로젝트 방식)

```java

@TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handleTransactionFailed(TransactionFailedEvent event) {
  // 트랜잭션 상태를 FAILED로 업데이트
  transactionRecordService.markAsFailed(event.getTransactionId());
}
```

## axon 이벤트 소싱
```
java -jar axonserver.jar
```
- 8024: HTTP API 및 웹 대시보드: http://localhost:8024
- 8124: gRPC 포트 (실제 애플리케이션 연동용)

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
