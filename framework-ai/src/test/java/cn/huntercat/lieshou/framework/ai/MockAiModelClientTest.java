package cn.huntercat.lieshou.framework.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/** Mock 模型客户端（无端点/无 key 降级演示 · framework 公共层）。 */
@DisplayName("MockAiModelClient（演示降级通道）")
class MockAiModelClientTest {

  @Test
  @DisplayName("无端点时返回演示回复并声明演示模式（finishReason=stop · 内容带最后一条用户消息）")
  void mockReturnsDemoReply() {
    MockAiModelClient client = new MockAiModelClient();
    AiModelClient.ChatReply reply =
        client.complete(
            "LM-Legal 2.5",
            List.of(
                new AiModelClient.ChatMessage("system", "你是案件秘书"),
                new AiModelClient.ChatMessage("user", "本案仲裁时效是否已过？")));

    assertThat(reply.model()).isEqualTo("LM-Legal 2.5");
    assertThat(reply.finishReason()).isEqualTo("stop");
    assertThat(reply.content()).contains("演示模式");
    assertThat(reply.content()).contains("本案仲裁时效是否已过？");
  }
}
