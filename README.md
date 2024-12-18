# 다양한 서비스 로직 구현 전략
목표:
- 잔액의 무결성 보장
- 거래의 멱등성 보장
- 비동기 통보 이메일 발송(학습 목적 코드이므로 실제로는 2초 지연 후 로그 출력)

## 트랜잭션 스크립트 패턴 (private 메소드 호출)
### transfer 메소드 흐름

```
transfer (@Transactional)
    └── checkIdempotency (@Transactional(REQUIRES_NEW))
    └── createTransaction (@Transactional(REQUIRES_NEW))
    └── executeTransfer (@Transactional(REQUIRED))
    └── markTransactionComplete (@Transactional(REQUIRES_NEW))
```

각 단계별 트랜잭션 동작:

checkIdempotency (REQUIRES_NEW)

부모 트랜잭션과 완전히 독립된 새로운 트랜잭션 시작
멱등성 체크가 완료되면 즉시 커밋
실패해도 부모 트랜잭션에 영향 없음


createTransaction (REQUIRES_NEW)

다시 새로운 트랜잭션으로 거래 기록 생성
거래 생성이 완료되면 즉시 커밋
이후 단계가 실패해도 거래 기록은 보존됨


executeTransfer (REQUIRED)

부모 트랜잭션에 참여 (같은 트랜잭션 컨텍스트 공유)
잔액 변경 작업 수행
낙관적 락 충돌이 여기서 발생
실패하면 부모 트랜잭션과 함께 롤백


markTransactionComplete (REQUIRES_NEW)

또 다른 독립된 트랜잭션으로 거래 상태 업데이트
상태 업데이트가 완료되면 즉시 커밋
만약 이전 단계에서 실패했다면 실행되지 않음