package cn.huntercat.lieshou.framework.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cn.huntercat.lieshou.framework.jwt.JwtSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JWT 签发 + 验证服务（HS256 · Bottom-Up 抽象）.
 *
 * <p>解析 / 验证 / 密钥校验已下沉 {@link JwtSupport}（jwt-support 模块，与 gateway 共享 单一事实源）；本服务仅保留签发（access /
 * refresh）与 TTL 配置。
 *
 * @see .ai/decisions/0017-spring-security-jwt.md
 */
@Service
public class JwtService {

  private final JwtSupport jwt;
  private final long accessTtlSeconds;
  private final long refreshTtlSeconds;

  public JwtService(
      JwtSupport jwt,
      @Value("${app.jwt.access-ttl-seconds:1800}") long accessTtlSeconds,
      @Value("${app.jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds) {
    this.jwt = jwt;
    this.accessTtlSeconds = accessTtlSeconds;
    this.refreshTtlSeconds = refreshTtlSeconds;
  }

  /** 生成 access token (typ=access, 含租户 tid/tcode · ADR-0022). */
  public String generateAccessToken(
      Long userId, Long tenantId, String tenantCode, String username, List<String> roles) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(jwt.issuer())
        .subject(username)
        .claims(
            Map.of(
                JwtSupport.CLAIM_UID,
                userId,
                JwtSupport.CLAIM_TID,
                tenantId == null ? 0L : tenantId,
                JwtSupport.CLAIM_TCODE,
                tenantCode == null ? "" : tenantCode,
                JwtSupport.CLAIM_ROLES,
                roles,
                JwtSupport.CLAIM_TYPE,
                "access"))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
        .signWith(jwt.signingKey(), Jwts.SIG.HS256)
        .compact();
  }

  /** 生成 refresh token (typ=refresh, 仅含 uid + username, 不带 roles). */
  public String generateRefreshToken(Long userId, String username) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(jwt.issuer())
        .subject(username)
        .claims(Map.of(JwtSupport.CLAIM_UID, userId, JwtSupport.CLAIM_TYPE, "refresh"))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(refreshTtlSeconds)))
        .signWith(jwt.signingKey(), Jwts.SIG.HS256)
        .compact();
  }

  /** 解析 token; 失败抛 JwtException. */
  public Claims parse(String token) {
    return jwt.parse(token);
  }

  /** 仅验证 token 是否合法 + 未过期. */
  public boolean validate(String token) {
    return jwt.validate(token);
  }

  public long getAccessTtlSeconds() {
    return accessTtlSeconds;
  }

  public long getRefreshTtlSeconds() {
    return refreshTtlSeconds;
  }
}
