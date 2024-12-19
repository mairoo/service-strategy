package kr.co.pincoin.study.scratch;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.axonframework.eventhandling.EventBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AxonServerConnectionTest {

  @Autowired
  private EventBus eventBus;

  @Test
  void testAxonServerConnection() {
    assertNotNull(eventBus, "EventBus 주입 받음");

    System.out.println("Successfully connected to Axon Server!");

    // axon 서버 로그 확인 가능
    // Application connected: study, clientId = 6675@seojonghwaui-Macmini.local, clientStreamId = 6675@seojonghwaui-Macmini.local.aa9c130f-8bea-4fbb-aec2-bfb7ddb631ad, context = default
    // Application disconnected: study, clientId = 6675@seojonghwaui-Macmini.local.aa9c130f-8bea-4fbb-aec2-bfb7ddb631ad, context = default: Platform connection completed by client
  }
}