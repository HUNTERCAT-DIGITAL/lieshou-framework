package cn.huntercat.lieshou.framework.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容模型客户端（V32 · 真实模型端点接入）.
 *
 * <p>POST {@code {endpoint}/chat/completions}，Bearer apiKey。响应解析 choices[0].message.content。
 * 端点不可用/超时/非 2xx → {@link AiModelException}（治理层记录并降级）。
 */
public final class OpenAiCompatibleClient implements AiModelClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

  private final String endpoint;
  private final String apiKey;
  private final int timeoutMillis;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  private final ObjectMapper mapper = new ObjectMapper();

  public OpenAiCompatibleClient(String endpoint, String apiKey, int timeoutMillis) {
    this.endpoint =
        endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    this.apiKey = apiKey;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public ChatReply complete(String model, List<ChatMessage> messages) throws AiModelException {
    try {
      Map<String, Object> body =
          Map.of(
              "model",
              model,
              "messages",
              messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList(),
              "temperature",
              0.2,
              "max_tokens",
              2048);
      HttpRequest req =
          HttpRequest.newBuilder(URI.create(endpoint + "/chat/completions"))
              .timeout(Duration.ofMillis(timeoutMillis))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + apiKey)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      mapper.writeValueAsString(body), StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> resp =
          http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() / 100 != 2) {
        throw new AiModelException(
            "模型端点返回 " + resp.statusCode() + "：" + truncate(resp.body(), 200), null);
      }
      Map<?, ?> json = mapper.readValue(resp.body(), Map.class);
      List<?> choices = (List<?>) json.get("choices");
      if (choices == null || choices.isEmpty()) {
        throw new AiModelException("模型端点无 choices 响应", null);
      }
      Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
      String content = message == null ? "" : String.valueOf(message.get("content"));
      Object finishRaw = ((Map<?, ?>) choices.get(0)).get("finish_reason");
      String finish = finishRaw == null ? "stop" : String.valueOf(finishRaw);
      return new ChatReply(content == null ? "" : content, model, finish);
    } catch (AiModelException e) {
      throw e;
    } catch (Exception e) {
      throw new AiModelException("模型调用失败：" + e.getMessage(), e);
    }
  }

  private String truncate(String s, int n) {
    return s == null || s.length() <= n ? s : s.substring(0, n);
  }
}
