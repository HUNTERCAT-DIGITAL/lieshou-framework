package cn.huntercat.lieshou.framework.ai;

import java.util.List;

/**
 * 模拟模型客户端（V32 · 无端点/无 key 降级）.
 *
 * <p>返回固定演示回复，并声明"演示模式"（前端据此标注非真实 AI 输出）。
 */
public final class MockAiModelClient implements AiModelClient {

  @Override
  public ChatReply complete(String model, List<ChatMessage> messages) {
    return new ChatReply(
        "【演示模式 · 未配置真实模型端点】您的问题已进入治理网关（密级路由/哈希/披露）流程。"
            + "生产接入：配置 LEGAL_AI_ENDPOINT + LEGAL_AI_API_KEY 后由真实模型作答。"
            + "\n\n收到请求（"
            + (messages.isEmpty() ? "空" : messages.get(messages.size() - 1).content())
            + "）",
        model,
        "stop");
  }
}
