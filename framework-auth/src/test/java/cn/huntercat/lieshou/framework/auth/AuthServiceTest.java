package cn.huntercat.lieshou.framework.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.LoginRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.LoginWithCodeRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.RegisterRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.TokenResponse;
import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;
import cn.huntercat.lieshou.framework.common.dto.UserAuthView;
import java.util.List;

/** AuthService 登录链路测试（认证核心：密码校验 / 账户状态 / token 签发） */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private JwtService jwt;
  @Mock private UserAuthPort userClient;
  @Mock private PasswordEncoder passwordEncoder;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(jwt, userClient, passwordEncoder, "huntercat");
  }

  private UserAuthView user(String status) {
    return new UserAuthView(
        1L,
        1L,
        "huntercat",
        "猎手猫",
        "generic",
        "admin",
        "管理员",
        "{bcrypt}hash",
        List.of("PLATFORM_ADMIN"),
        status);
  }

  @Test
  void login_validCredentials_returnsTokenAndMarksLogin() {
    when(userClient.findByTenantAndUsername("huntercat", "admin")).thenReturn(user("ACTIVE"));
    when(passwordEncoder.matches("admin123", "{bcrypt}hash")).thenReturn(true);
    when(jwt.generateAccessToken(1L, 1L, "huntercat", "admin", List.of("PLATFORM_ADMIN")))
        .thenReturn("access-token");
    when(jwt.generateRefreshToken(1L, "admin")).thenReturn("refresh-token");

    var resp = authService.login(new LoginRequest("huntercat", "admin", "admin123"));

    assertThat(resp.accessToken()).isEqualTo("access-token");
    assertThat(resp.refreshToken()).isEqualTo("refresh-token");
    verify(userClient).markLastLogin(1L);
  }

  @Test
  void login_wrongPassword_throwsBadCredentials() {
    when(userClient.findByTenantAndUsername("huntercat", "admin")).thenReturn(user("ACTIVE"));
    when(passwordEncoder.matches("wrong", "{bcrypt}hash")).thenReturn(false);

    assertThatThrownBy(() -> authService.login(new LoginRequest("huntercat", "admin", "wrong")))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("INVALID_CREDENTIALS");
  }

  @Test
  void login_userNotFound_throwsUsernameNotFound() {
    when(userClient.findByTenantAndUsername("huntercat", "ghost")).thenReturn(null);

    assertThatThrownBy(() -> authService.login(new LoginRequest("huntercat", "ghost", "x")))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageContaining("USER_NOT_FOUND");
  }

  @Test
  void login_disabledAccount_throwsBadCredentials() {
    when(userClient.findByTenantAndUsername("huntercat", "admin")).thenReturn(user("DISABLED"));
    when(passwordEncoder.matches("admin123", "{bcrypt}hash")).thenReturn(true);

    assertThatThrownBy(() -> authService.login(new LoginRequest("huntercat", "admin", "admin123")))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("ACCOUNT_DISABLED");
  }

  @Test
  void login_defaultTenantCode_whenBlank() {
    when(userClient.findByTenantAndUsername("huntercat", "admin")).thenReturn(user("ACTIVE"));
    when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
    when(jwt.generateAccessToken(eq(1L), eq(1L), anyString(), eq("admin"), any())).thenReturn("t");
    when(jwt.generateRefreshToken(eq(1L), eq("admin"))).thenReturn("r");

    var resp = authService.login(new LoginRequest("  ", "admin", "admin123"));

    assertThat(resp.accessToken()).isEqualTo("t");
  }

  /** 开放注册：code 为空时跳过验证码校验,直接创建用户并签发 token（2026-08） */
  @Test
  void register_codeBlank_skipsVerification() {
    java.util.Map<String, Object> created = new java.util.HashMap<>();
    created.put("id", 1);
    created.put("tenantId", 1);
    created.put("tenantCode", "huntercat");
    created.put("tenantName", "猎手猫");
    created.put("tenantEdition", "GENERIC");
    when(userClient.createUser(any())).thenReturn(created);
    when(jwt.generateAccessToken(anyLong(), anyLong(), anyString(), anyString(), anyList()))
        .thenReturn("at");
    when(jwt.generateRefreshToken(anyLong(), anyString())).thenReturn("rt");
    when(jwt.getAccessTtlSeconds()).thenReturn(1800L);

    RegisterRequest req =
        new RegisterRequest(
            "huntercat", "u_new", "新用户", "pw123456", "SMS", "13800000000", null, null);
    TokenResponse resp = authService.register(req);

    assertThat(resp.username()).isEqualTo("u_new");
    assertThat(resp.tenantCode()).isEqualTo("huntercat");
    // 关键：code 为空 → 不校验验证码
    verify(userClient, never()).verifyVerificationCode(any());
    verify(userClient).createUser(any());
  }

  /** 兼容：code 非空仍走验证码校验（admin-web 验证码注册流程） */
  @Test
  void register_codePresent_stillVerifies() {
    java.util.Map<String, Object> created = new java.util.HashMap<>();
    created.put("id", 1);
    created.put("tenantId", 1);
    created.put("tenantCode", "huntercat");
    created.put("tenantName", "猎手猫");
    created.put("tenantEdition", "GENERIC");
    when(userClient.createUser(any())).thenReturn(created);
    when(jwt.generateAccessToken(anyLong(), anyLong(), anyString(), anyString(), anyList()))
        .thenReturn("at");
    when(jwt.generateRefreshToken(anyLong(), anyString())).thenReturn("rt");
    when(jwt.getAccessTtlSeconds()).thenReturn(1800L);

    RegisterRequest req =
        new RegisterRequest(
            "huntercat", "u_code", "验证码用户", "pw123456", "SMS", "13900000000", "123456", null);
    authService.register(req);

    // code 非空 → 验证码校验必须被调用
    verify(userClient).verifyVerificationCode(any());
  }

  // ============================================================
  // 依赖故障 ≠ 业务否定：端口抛异常 = user-service 不可达 → 503，不误报业务错误
  // ============================================================

  /** 登录：user-service 网络故障 → SERVICE_UNAVAILABLE，而非 USER_NOT_FOUND */
  @Test
  void login_upstreamDown_throwsServiceUnavailable() {
    when(userClient.findByTenantAndUsername("huntercat", "admin"))
        .thenThrow(new RuntimeException("connect timeout"));

    assertThatThrownBy(() -> authService.login(new LoginRequest("huntercat", "admin", "admin123")))
        .isInstanceOf(BaseException.class)
        .satisfies(
            e ->
                assertThat(((BaseException) e).errorCode())
                    .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.name()));
  }

  /** 验证码登录：查用户时依赖故障 → 503，不吞成 USER_NOT_FOUND */
  @Test
  void loginWithCode_upstreamDown_throwsServiceUnavailable() {
    when(userClient.findByTenantAndPhone("huntercat", "13800000000")).thenThrow(new RuntimeException("connect timeout"));

    assertThatThrownBy(
            () ->
                authService.loginWithCode(
                    new LoginWithCodeRequest("huntercat", "SMS", "13800000000", "123456")))
        .isInstanceOf(BaseException.class)
        .satisfies(
            e ->
                assertThat(((BaseException) e).errorCode())
                    .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.name()));
  }

  /** 注册：createUser 依赖故障 → 503，不吞成 REGISTER_FAILED */
  @Test
  void register_upstreamDown_throwsServiceUnavailable() {
    when(userClient.createUser(any())).thenThrow(new RuntimeException("connect timeout"));

    RegisterRequest req =
        new RegisterRequest(
            "huntercat", "u_new", "新用户", "pw123456", "SMS", "13800000000", null, null);
    assertThatThrownBy(() -> authService.register(req))
        .isInstanceOf(BaseException.class)
        .satisfies(
            e ->
                assertThat(((BaseException) e).errorCode())
                    .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.name()));
  }

  /** 租户选项：依赖故障降级空列表（前端回退默认租户），不抛错 */
  @Test
  void tenantOptions_upstreamDown_returnsEmptyList() {
    when(userClient.tenantOptions("admin")).thenThrow(new RuntimeException("connect timeout"));

    assertThat(authService.tenantOptions("admin")).isEmpty();
  }

  /** 验证码登录：业务否定（BaseException INVALID_CODE）→ 透传错误码，不是 503 */
  @Test
  void loginWithCode_businessRejection_passesThroughErrorCode() {
    doThrow(new BaseException("INVALID_CODE", HttpStatus.BAD_REQUEST, "验证码错误"))
        .when(userClient)
        .verifyVerificationCode(any());

    assertThatThrownBy(
            () ->
                authService.loginWithCode(
                    new LoginWithCodeRequest("huntercat", "SMS", "13800000000", "123456")))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("INVALID_CODE");
  }
}
