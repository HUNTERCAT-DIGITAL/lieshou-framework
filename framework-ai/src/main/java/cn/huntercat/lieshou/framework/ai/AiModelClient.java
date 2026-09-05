package cn.huntercat.lieshou.framework.ai;

import java.util.List;

/**
 * AI 模型调用客户端（framework 公共抽象 · 2026-09 arch 方案 A）.
 *
 * <p>OpenAI 兼容协议。实现可选：{@link OpenAiCompatibleClient}（真实端点 · 生产）/ {@link MockAiModelClient}（无端点/无
 * key 时降级演示）。装配与治理编排（密级路由 + 哈希 + 披露）由消费方服务负责（如 legal 的 AiGatewayService），本层只管模型接入。
 */
public interface AiModelClient {

  /** 对话消息。 */
  record ChatMessage(String role, String content) {}

  /** 模型回复。 */
  record ChatReply(String content, String model, String finishReason) {}

  /**
   * 完成一次对话。
   *
   * @param model 模型标识（如 deepseek-chat）
   * @param messages 消息历史（system/user/assistant）
   * @return 模型回复
   * @throws AiModelException 端点不可用/调用失败
   */
  ChatReply complete(String model, List<ChatMessage> messages) throws AiModelException;

  /** 调用失败（治理层捕获后降级/记录）。 */
  final class AiModelException extends RuntimeException {
    public AiModelException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
