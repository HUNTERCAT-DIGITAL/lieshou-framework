package cn.huntercat.lieshou.framework.auth.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Auth 三件套 DTO（登录 / 刷新 / 令牌响应）. */
public final class AuthDtos {

  private AuthDtos() {}

  @Schema(description = "Login request body")
  public record LoginRequest(
      @Schema(description = "Tenant code (default default)", example = "default")
          String tenantCode,
      @Schema(description = "Username", example = "futurewl") @NotBlank String username,
      @Schema(
              description = "Plaintext password (over HTTPS only)",
              example = "correct horse battery")
          @NotBlank
          String password) {}

  @Schema(description = "Refresh token request body")
  public record RefreshRequest(
      @Schema(description = "Refresh token from /login response") @NotBlank String refreshToken) {}

  @Schema(description = "Token response (Phase 5: access + refresh + meta)")
  public record TokenResponse(
      @Schema(description = "JWT access token (Bearer)") String accessToken,
      @Schema(description = "JWT refresh token (long-lived)") String refreshToken,
      @Schema(description = "Access token TTL in seconds", example = "1800") long expiresIn,
      @Schema(description = "Token type", example = "Bearer") String tokenType,
      @Schema(description = "User id (uid claim)") Long userId,
      @Schema(description = "Username (sub claim)") String username,
      @Schema(description = "Tenant code (tcode claim)", example = "default") String tenantCode,
      @Schema(description = "Tenant display name", example = "南昌猎手猫数字科技有限公司") String tenantName,
      @Schema(
              description =
                  "Tenant edition: GENERIC | LAYER | LEGALMIND | ZHIYE | JMZZ (ADR-0035/0036)",
              example = "GENERIC")
          String tenantEdition,
      @Schema(
              description = "Available tenants for this username (多租户登录前选租户 · 8f0d60e)",
              example = "[]")
          java.util.List<java.util.Map<String, Object>> availableTenants) {}

  @Schema(description = "Error response body")
  public record ErrorResponse(
      @Schema(description = "Error code") String error,
      @Schema(description = "Human-readable message") String message) {}

  // ============================================================
  // Phase 8 · 认证体系扩展（ADR-0023）：验证码登录 / 注册 / 重置密码
  // ============================================================

  @Schema(description = "Send one-time code request")
  public record SendCodeRequest(
      @Schema(description = "Channel: SMS | EMAIL", example = "SMS") @NotBlank String channel,
      @Schema(description = "Phone or email", example = "13800000000") @NotBlank String target,
      @Schema(description = "Purpose: LOGIN | REGISTER | RESET_PASSWORD", example = "LOGIN")
          @NotBlank
          String purpose) {}

  @Schema(description = "Code login request (SMS/EMAIL verification code)")
  public record LoginWithCodeRequest(
      @Schema(description = "Tenant code", example = "default") String tenantCode,
      @Schema(description = "Channel: SMS | EMAIL", example = "SMS") @NotBlank String channel,
      @Schema(description = "Phone or email", example = "13800000000") @NotBlank String target,
      @Schema(description = "6-digit code", example = "123456") @NotBlank String code) {}

  @Schema(description = "Self/invited registration request")
  public record RegisterRequest(
      @Schema(description = "Tenant code (ignored when inviteCode present)", example = "default")
          String tenantCode,
      @Schema(description = "Login username") @NotBlank String username,
      @Schema(description = "Display name") @NotBlank String displayName,
      @Schema(description = "Password") @NotBlank String password,
      @Schema(description = "Channel: SMS | EMAIL") @NotBlank String channel,
      @Schema(description = "Phone or email") @NotBlank String target,
      @Schema(
              description =
                  "6-digit code (optional; blank skips verification for open registration)")
          String code,
      @Schema(
              description =
                  "Invite code (optional; auto-joins tenant with invite role · ADR-0023 P2)",
              example = "AB12CD34")
          String inviteCode) {}

  /** 切换租户请求（先登录后选租户） */
  public record SwitchTenantRequest(@NotBlank String refreshToken, @NotBlank String tenantCode) {}

  @Schema(description = "Reset password request (via code)")
  public record ResetPasswordRequest(
      @Schema(description = "Channel: SMS | EMAIL", example = "SMS") @NotBlank String channel,
      @Schema(description = "Phone or email", example = "13800000000") @NotBlank String target,
      @Schema(description = "6-digit code", example = "123456") @NotBlank String code,
      @Schema(description = "New password") @NotBlank String newPassword) {}
}
