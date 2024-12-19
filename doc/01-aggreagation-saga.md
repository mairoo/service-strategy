# AggregationLifecycle, SagaLifecycle

| 구분         | AggregateLifecycle                                          | SagaLifecycle                                                             |
|------------|-------------------------------------------------------------|---------------------------------------------------------------------------|
| **목적**     | - 단일 Aggregate 내부 상태 관리<br>- 도메인 일관성 보장                     | - 장기 실행 비즈니스 프로세스 관리<br>- 여러 Aggregate 간 조정                               |
| **주요 메서드** | - apply()<br>- markDeleted()<br>- createNew()<br>- isLive() | - associateWith()<br>- removeAssociationWith()<br>- end()<br>- isActive() |
| **트랜잭션**   | 단일 트랜잭션 내 동기 처리                                             | 여러 트랜잭션에 걸친 비동기 처리                                                        |
| **상태 관리**  | - 즉각적인 상태 변경<br>- 강한 일관성 보장                                 | - 점진적인 상태 변경<br>- 최종 일관성 보장                                               |
| **복구 방식**  | 트랜잭션 롤백                                                     | 보상 트랜잭션                                                                   |
| **이벤트 처리** | 동기적 이벤트 처리                                                  | 비동기적 이벤트 처리                                                               |
| **사용 사례**  | - 단일 도메인 객체 관리<br>- 계좌 이체<br>- 주문 생성                        | - 복잡한 비즈니스 프로세스<br>- 주문-결제-배송<br>- 여러 서비스 간 조정                            |
| **범위**     | 단일 Aggregate 내부                                             | 여러 Aggregate/서비스 걸쳐                                                       |
| **특징**     | - 이벤트 소싱 지원<br>- Aggregate 루트만 사용 가능                        | - 보상 트랜잭션 지원<br>- 서비스 간 조율 가능                                             |

# 계좌이체 사례

## 트랜잭션 기법

| 단계 | 작업 내용       | 확인 사항             | 실패 시 동작 |
|----|-------------|-------------------|---------|
| 1  | A 계좌 잔액 확인  | 잔액 >= 500원        | 거래 거절   |
| 2  | B 계좌 유효성 확인 | 계좌 상태 정상 여부       | 거래 거절   |
| 3  | 거래 중복 확인    | 트랜잭션 ID 중복 체크     | 거래 거절   |
| 4  | A 계좌 잔액 차감  | -500원             | 트랜잭션 롤백 |
| 5  | B 계좌 잔액 증가  | +500원             | 트랜잭션 롤백 |
| 6  | 거래 내역 기록    | 거래 완료 시각, 금액 등 기록 | 트랜잭션 롤백 |

여기서 중요한 점은:

1. **4-6단계가 하나의 원자적 트랜잭션**으로 처리됨
2. 어느 한 단계라도 실패하면 전체 롤백
3. 동시성 제어를 위해 잠금(Lock) 메커니즘 필요

## Event Sourcing + AggregateLifecycle (계좌 이체)

| 순서 | 이벤트                         | 계좌 A Aggregate 상태 | 계좌 B Aggregate 상태 | 이벤트 저장소   |
|----|-----------------------------|-------------------|-------------------|-----------|
| 1  | TransferRequestedEvent      | 검증 시작             | 변화 없음             | Event1 저장 |
| 2  | AccountABalanceCheckedEvent | 잔액 확인 완료          | 변화 없음             | Event2 저장 |
| 3  | AccountADebitedEvent        | 잔액 -500 반영        | 변화 없음             | Event3 저장 |
| 4  | AccountBCreditedEvent       | 잔액 갱신 유지          | 잔액 +500 반영        | Event4 저장 |
| 5  | TransferCompletedEvent      | 최종 상태 유지          | 최종 상태 유지          | Event5 저장 |

- 모든 상태 변화가 이벤트로 기록됨
- 이벤트를 재생하여 현재 상태 복원 가능
- 각 Aggregate의 상태는 이벤트의 결과물

이벤트 소싱은 "무슨 일이 있었는지"를 기록하는 반면, Saga는 "어떻게 조율할 것인지"에 중점을 둡니다.

## Saga 패턴 (계좌 이체)

| 단계 | 커맨드/이벤트               | 성공 시                     | 실패 시 보상 트랜잭션                                       |
|----|-----------------------|--------------------------|----------------------------------------------------|
| 1  | CheckBalanceCommand   | BalanceVerifiedEvent 발행  | 거래 종료                                              |
| 2  | DebitAccountCommand   | AccountDebitedEvent 발행   | -                                                  |
| 3  | CreditAccountCommand  | AccountCreditedEvent 발행  | DebitRollbackCommand 실행                            |
| 4  | RecordTransferCommand | TransferRecordedEvent 발행 | CreditRollbackCommand 실행 후 DebitRollbackCommand 실행 |

- 각 단계가 독립적인 트랜잭션
- 실패 시 보상 트랜잭션으로 롤백
- 최종적 일관성(Eventually Consistent) 보장

# 보상 트랜잭션

## 이벤트 소싱에서 보상트랜잭션

AccountBCreditedCommand 명령을 보냈으나 실패하여 이벤트가 발생하지 않은 경우 실패 상황을 처리하기 위한 새로운 이벤트가 필요하다.

| 순서 | 이벤트                         | 계좌 A Aggregate 상태 | 계좌 B Aggregate 상태 | 이벤트 저장소   |
|----|-----------------------------|-------------------|-------------------|-----------|
| 1  | TransferRequestedEvent      | 검증 시작             | 변화 없음             | Event1 저장 |
| 2  | AccountABalanceCheckedEvent | 잔액 확인 완료          | 변화 없음             | Event2 저장 |
| 3  | AccountADebitedEvent        | 잔액 -500 반영        | 변화 없음             | Event3 저장 |
| 4  | AccountBCreditFailedEvent   | 보상 트랜잭션 시작        | 변화 없음             | Event4 저장 |
| 5  | AccountADebitReversedEvent  | 잔액 +500 복원        | 변화 없음             | Event5 저장 |
| 6  | TransferFailedEvent         | 최종 실패 상태          | 변화 없음             | Event6 저장 |

1. 실패를 감지하면 새로운 보상 이벤트들을 발행해야 함
2. 원래 상태로 돌리는 보상 이벤트(AccountADebitReversedEvent)도 이벤트 저장소에 기록됨
3. 모든 실패와 복구 과정이 이벤트로 추적 가능
4. 최종적으로 TransferFailedEvent로 전체 거래의 실패를 명확히 기록

이런 방식으로 이벤트 소싱에서는 롤백 대신 보상 이벤트를 통해 일관성 유지

## Saga 패턴에서 보상트랜잭션

# 결론

AggregateLifecycle:

```
Account A Aggregate              Account B Aggregate
├─ accountId                     ├─ accountId  
├─ balance                       ├─ balance
├─ status                        ├─ status
└─ transferHistory               └─ transferHistory

각 Aggregate가 자신의 상태를 이벤트로 관리
```

- 도메인 객체(Account)가 주인공
- 각 Aggregate가 자신의 일관성 책임
- 트랜잭션 경계가 명확함

Saga Pattern:

```
TransferSaga (오케스트레이션)
├─ sagaId
├─ transferAmount
├─ sourceAccountId    ────┐
├─ targetAccountId    ────┼─── Account 도메인 객체들은 
├─ status                 │    각각 자신의 바운디드 컨텍스트에 존재
└─ compensationStatus     │
                          │
Account Domain ───────────┘
├─ Account A
└─ Account B
```

- 비즈니스 프로세스(Transfer)가 주인공
- 도메인 객체들은 서비스 뒤에 숨어있음
- 각 단계가 독립적 트랜잭션

Axon Framework의 AggregateLifecycle과 SagaLifecycle을 표로 비교 분석하겠습니다:

| 구분         | AggregateLifecycle                                            | SagaLifecycle                                                                |
|------------|---------------------------------------------------------------|------------------------------------------------------------------------------|
| **목적**     | - 단일 Aggregate 내부 상태 관리<br/>- 도메인 일관성 보장                      | - 장기 실행 비즈니스 프로세스 관리<br/>- 여러 Aggregate 간 조정                                 |
| **주요 메서드** | - apply()<br/>- markDeleted()<br>- createNew()<br/>- isLive() | - associateWith()<br/>- removeAssociationWith()<br/>- end()<br/>- isActive() |
| **트랜잭션**   | 단일 트랜잭션 내 동기 처리                                               | 여러 트랜잭션에 걸친 비동기 처리                                                           |
| **상태 관리**  | - 즉각적인 상태 변경<br>- 강한 일관성 보장                                   | - 점진적인 상태 변경<br>- 최종 일관성 보장                                                  |
| **복구 방식**  | 트랜잭션 롤백                                                       | 보상 트랜잭션                                                                      |
| **이벤트 처리** | 동기적 이벤트 처리                                                    | 비동기적 이벤트 처리                                                                  |
| **사용 사례**  | - 단일 도메인 객체 관리<br>- 계좌 이체<br/>- 주문 생성                         | - 복잡한 비즈니스 프로세스<br>- 주문-결제-배송<br/>- 여러 서비스 간 조정                              |
| **범위**     | 단일 Aggregate 내부                                               | 여러 Aggregate/서비스 걸쳐                                                          |
| **특징**     | - 이벤트 소싱 지원<br/>- Aggregate 루트만 사용 가능                         | - 보상 트랜잭션 지원<br/>- 서비스 간 조율 가능                                               |
