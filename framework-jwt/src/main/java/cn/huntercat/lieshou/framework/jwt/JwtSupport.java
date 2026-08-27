package cn.huntercat.lieshou.framework.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

/**
 * JWT 共享能力（HS256 · 单一事实源）.
 *
 * <p>自 auth-service / gateway 两处同源实现下沉（Bottom-Up）：secret 长度校验、签名密钥、 parse / validate
 * 逻辑收敛于此；签发（generate）留在 auth-service（组合本类，复用 {@link #signingKey()} 与 {@link #issuer()}）。
 *
 * <p><b>无任何 Web 依赖</b>：auth（servlet）与 gateway（webflux）均可引用，规避 common 模块 servlet starter 与 gateway
 * 的冲突。
 *
 * <p>secret 必须 ≥ 32 字节（256 bit，HS256 算法要求），auth-service 与 gateway 共享 同源环境变量 {@code
 * app.jwt.secret}（ADR-0017）。
 */
public final class JwtSupport {

  public static final String CLAIM_UID = "uid";
  public static final String CLAIM_TID = "tid";
  public static final String CLAIM_TCODE = "tcode";
  public static final String CLAIM_ROLES = "roles";
  public static final String CLAIM_TYPE = "typ";
  public static final String DEFAULT_ISSUER = "lieshoucloud";

  private final String issuer;
  private final SecretKey signingKey;

  public JwtSupport(String secret, String issuer) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException(
          "JWT_SECRET 长度不足（HS256 至少 32 字节）；当前长度=" + (secret == null ? 0 : secret.length()));
    }
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer == null || issuer.isBlank() ? DEFAULT_ISSUER : issuer;
  }

  /** 解析 token；失败抛 JwtException / IllegalArgumentException. */
  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .requireIssuer(issuer)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /** 仅验证 token 是否合法 + 未过期. */
  public boolean validate(String token) {
    try {
      parse(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }

  /** 签名密钥（auth-service 签发用） */
  public SecretKey signingKey() {
    return signingKey;
  }

  public String issuer() {
    return issuer;
  }
}
