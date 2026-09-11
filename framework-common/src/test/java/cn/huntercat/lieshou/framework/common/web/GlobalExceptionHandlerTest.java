package cn.huntercat.lieshou.framework.common.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler} 兜底分支契约：Spring MVC 自带语义异常（实现 {@code ErrorResponse}）的 4xx 状态码 不得被统一成
 * 500，否则前端会把「无权限 / 路径不存在」误判为服务端故障。
 */
class GlobalExceptionHandlerTest {

  @SuppressWarnings("unchecked")
  private static MockMvc mvc(Object controller) {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler(mock(ObjectProvider.class)))
        .build();
  }

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
}
