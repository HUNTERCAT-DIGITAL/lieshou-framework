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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.auth.AuthService;
import cn.huntercat.lieshou.framework.auth.JwtService;
import cn.huntercat.lieshou.framework.auth.UserAuthPort;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.LoginRequest;
import cn.huntercat.lieshou.framework.auth.dto.UserAuthView;
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
}
