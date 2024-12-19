package kr.co.pincoin.study.aggregate;

import java.math.BigDecimal;
import kr.co.pincoin.study.command.AccountCommands.CreateAccountCommand;
import kr.co.pincoin.study.command.AccountCommands.CreditAccountCommand;
import kr.co.pincoin.study.command.AccountCommands.DebitAccountCommand;
import kr.co.pincoin.study.event.AccountEvents.AccountCreatedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyCreditedEvent;
import kr.co.pincoin.study.event.AccountEvents.MoneyDebitedEvent;
import lombok.NoArgsConstructor;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

/**
 * 계좌 애그리게잇(Aggregate)
 * <p>
 * DDD의 Aggregate Root 패턴을 구현한 도메인 객체로, 계좌와 관련된 모든 불변성을 보장합니다. Axon Framework의 이벤트 소싱을 통해 상태를
 * 관리합니다.
 * <p>
 * 상태 관리 방식: 1. 모든 상태 변경은 이벤트로 저장됨 (Axon Server에 영구 저장) 2. 현재 상태는 저장된 이벤트들을 순차적으로 적용하여 재구성 3.
 * balance 필드는 일시적인 메모리 상태이며, 이벤트 재생을 통해 언제든 복원 가능
 */
@Aggregate
@NoArgsConstructor
public class AccountAggregate {

  /**
   * 애그리게잇 식별자 Axon이 이벤트를 특정 애그리게잇 인스턴스와 연결하는 데 사용
   */
  @AggregateIdentifier
  private String accountId;

  /**
   * 계좌 잔액 이 상태는 일시적이며 이벤트 소싱을 통해 재구성됨 Axon Server에 직접 저장되지 않고, 이벤트들의 결과로 계산됨
   */
  private BigDecimal balance;

  /**
   * 새로운 계좌를 생성하는 커맨드 핸들러
   * <p>
   * 동작 방식: 1. 커맨드 유효성 검증 2. AccountCreatedEvent 발행 3. 이벤트는 Axon Server에 저장되고 상태 변경의 근거가 됨
   *
   * @param command 계좌 생성 명령
   * @throws IllegalArgumentException 초기 잔액이 음수인 경우
   */
  @CommandHandler
  public AccountAggregate(CreateAccountCommand command) {
    if (command.getInitialBalance().compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("초기 잔액은 0보다 작을 수 없습니다");
    }

    AggregateLifecycle.apply(new AccountCreatedEvent(
        command.getAccountId(),
        command.getInitialBalance()
    ));
  }

  /**
   * 계좌에서 출금하는 커맨드 핸들러
   * <p>
   * 동시성 처리: - Axon이 애그리게잇 단위로 동시성을 보장하므로 별도의 락이 필요 없음 - 동일 계좌에 대한 모든 트랜잭션은 순차적으로 처리됨
   *
   * @param command 출금 명령
   * @throws IllegalArgumentException 출금액이 0 이하인 경우
   * @throws IllegalStateException    잔액이 부족한 경우
   */
  @CommandHandler
  public void handle(DebitAccountCommand command) {
    if (command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("출금액은 반드시 양수여야 합니다");
    }

    if (balance.compareTo(command.getAmount()) < 0) {
      throw new IllegalStateException("잔액이 부족합니다");
    }

    // ❌ 이렇게 직접 상태를 변경하지 않습니다
    // this.balance = this.balance.subtract(amount);

    // ✅ 대신 이벤트를 아래와 같이 apply하여 상태를 변경합니다
    AggregateLifecycle.apply(new MoneyDebitedEvent(
        accountId,
        command.getTargetAccountId(),
        command.getAmount(),
        command.getTransactionId()
    ));
  }

  /**
   * 계좌에 입금하는 커맨드 핸들러
   *
   * @param command 입금 명령
   * @throws IllegalArgumentException 입금액이 0 이하인 경우
   */
  @CommandHandler
  public void handle(CreditAccountCommand command) {
    if (command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("입금액은 반드시 양수여야 합니다");
    }

    AggregateLifecycle.apply(new MoneyCreditedEvent(
        accountId,
        command.getSourceAccountId(),
        command.getAmount(),
        command.getTransactionId()
    ));
  }

  // 도메인 상태 업데이트 (@EventSourcingHandler)
  // - 순수하게 도메인 상태만 관리
  // - 모든 상태 변경은 이벤트의 결과
  // - 외부 시스템과의 의존성 없음

  /**
   * 계좌 생성 이벤트 처리
   * <p>
   * 이벤트 소싱 동작 방식: 1. Axon이 애그리게잇 로드시 저장된 모든 이벤트를 시간 순서대로 로드 2. 각 이벤트에 대해 해당하는 EventSourcingHandler
   * 메서드 호출 3. 이벤트들을 순차적으로 적용하여 현재 상태 재구성
   *
   * @param event 계좌 생성 이벤트
   */
  @EventSourcingHandler
  public void on(AccountCreatedEvent event) {
    this.accountId = event.getAccountId();
    this.balance = event.getInitialBalance();
  }

  /**
   * 출금 이벤트 처리
   *
   * @param event 출금 이벤트
   */
  @EventSourcingHandler
  public void on(MoneyDebitedEvent event) {
    this.balance = this.balance.subtract(event.getAmount());
  }

  /**
   * 입금 이벤트 처리
   *
   * @param event 입금 이벤트
   */
  @EventSourcingHandler
  public void on(MoneyCreditedEvent event) {
    this.balance = this.balance.add(event.getAmount());
  }
}