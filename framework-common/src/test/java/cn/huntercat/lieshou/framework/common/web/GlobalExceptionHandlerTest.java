package cn.huntercat.lieshou.framework.common.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler} 兜底分支契约：
 *
 * <ul>
 *   <li>Spring MVC 自带语义异常（实现 {@code ErrorResponse}）的 4xx 状态码 <b>不得</b> 被统一成 500， 否则前端会把「无权限 /
 *       路径不存在」误判为服务端故障；
 *   <li>异步断连/超时（SSE）静默处理，<b>不得</b> 写成 500 JSON（text/event-stream 响应无 JSON 转换器 → {@code
 *       HttpMessageNotWritableException} 二次异常，浏览器 EventSource 记一条 500）。
 * </ul>
 */
class GlobalExceptionHandlerTest {

  @SuppressWarnings("unchecked")
  private static MockMvc mvc(Object controller) {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler(mock(ObjectProvider.class)))
        .build();
  }

  // ===== 兜底分支保留 Spring 自带 4xx =====

  @Test
  void 兜底_handler不吞掉Spring自带的状态码() throws Exception {
    MockMvc mvc = mvc(new SemanticErrorController());

    mvc.perform(get("/boom/forbidden"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));

    mvc.perform(get("/boom/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void 兜底_handler对普通异常仍返回500() throws Exception {
    mvc(new SemanticErrorController())
        .perform(get("/boom/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
  }

  /** 按路径抛指定异常，用来驱动兜底分支。 */
  @RestController
  static class SemanticErrorController {

    @GetMapping("/boom/forbidden")
    String forbidden() {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作其他租户数据");
    }

    @GetMapping("/boom/missing")
    String missing() throws NoResourceFoundException {
      throw new NoResourceFoundException(HttpMethod.GET, "/boom/missing");
    }

    @GetMapping("/boom/unexpected")
    String unexpected() {
      throw new RuntimeException("boom");
    }
  }

  // ===== 异步断连/超时静默（SSE） =====

  /** 探针控制器：模拟 SSE 端点内的异步断连/超时，以及普通未知异常。 */
  @RestController
  static class ProbeController {
    @GetMapping(value = "/probe/disconnect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    String disconnect() throws AsyncRequestNotUsableException {
      throw new AsyncRequestNotUsableException("client gone");
    }

    @GetMapping(value = "/probe/timeout", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    String timeout() {
      throw new AsyncRequestTimeoutException();
    }

    @GetMapping("/probe/boom")
    String boom() {
      throw new RuntimeException("boom");
    }
  }

  @Test
  void asyncDisconnect_isSwallowed_no500AndNoBody() throws Exception {
    mvc(new ProbeController())
        .perform(get("/probe/disconnect"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void asyncTimeout_isSwallowed_no500AndNoBody() throws Exception {
    mvc(new ProbeController())
        .perform(get("/probe/timeout"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void unexpectedException_stillMapsTo500Json() throws Exception {
    mvc(new ProbeController())
        .perform(get("/probe/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
  }
}
