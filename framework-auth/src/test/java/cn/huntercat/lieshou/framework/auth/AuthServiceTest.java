package cn.huntercat.lieshou.framework.auth.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.auth.AuthService;
import cn.huntercat.lieshou.framework.auth.JwtService;
import cn.huntercat.lieshou.framework.auth.UserAuthPort;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.LoginRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.RegisterRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.TokenResponse;
import cn.huntercat.lieshou.framework.common.dto.UserAuthView;
import java.util.List;

/** AuthService 登录链路测试（认证核心：密码校验 / 账户状态 / token 签发） */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private JwtService jwt;
  @Mock private UserAuthPort userClient;
  @Mock private PasswordEncoder passwordEncoder;

  @InjectMocks private AuthService authService;

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
}
