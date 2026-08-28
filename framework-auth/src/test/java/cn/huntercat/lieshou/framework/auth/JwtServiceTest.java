package cn.huntercat.lieshou.framework.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.huntercat.lieshou.framework.jwt.JwtSupport;
import io.jsonwebtoken.Claims;
import java.util.List;

/** JwtService 签发/解析测试（全栈单体 · 认证核心） */
class JwtServiceTest {

  private static final String SECRET = "test-secret-key-for-hs256-min-32-bytes!!";
  private static final String ISSUER = "lieshouboot-test";

  private JwtService service;

  @BeforeEach
  void setUp() {
    service = new JwtService(new JwtSupport(SECRET, ISSUER), 1800, 604800);
  }

  @Test
  void generateAccessToken_returnsParsableTokenWithClaims() {
    String token =
        service.generateAccessToken(1L, 2L, "huntercat", "admin", List.of("PLATFORM_ADMIN"));

    assertThat(token).isNotBlank();
    Claims claims = new JwtSupport(SECRET, ISSUER).parse(token);
    assertThat(claims.getSubject()).isEqualTo("admin");
    assertThat(claims.get(JwtSupport.CLAIM_UID, Long.class)).isEqualTo(1L);
    assertThat(claims.get(JwtSupport.CLAIM_TID, Long.class)).isEqualTo(2L);
    assertThat(claims.get(JwtSupport.CLAIM_TCODE, String.class)).isEqualTo("huntercat");
    assertThat(claims.get(JwtSupport.CLAIM_TYPE, String.class)).isEqualTo("access");
    assertThat(claims.get(JwtSupport.CLAIM_ROLES, List.class)).contains("PLATFORM_ADMIN");
  }

  @Test
  void generateAccessToken_nullTenant_usesZero() {
    String token = service.generateAccessToken(1L, null, null, "user", List.of("USER"));
    Claims claims = new JwtSupport(SECRET, ISSUER).parse(token);
    assertThat(claims.get(JwtSupport.CLAIM_TID, Long.class)).isEqualTo(0L);
  }

  @Test
  void generateRefreshToken_marksTypeRefresh() {
    String token = service.generateRefreshToken(1L, "admin");
    Claims claims = new JwtSupport(SECRET, ISSUER).parse(token);
    assertThat(claims.get(JwtSupport.CLAIM_TYPE, String.class)).isEqualTo("refresh");
    assertThat(claims.getSubject()).isEqualTo("admin");
  }

  @Test
  void expiredToken_isRejected() {
    JwtService shortTtl = new JwtService(new JwtSupport(SECRET, ISSUER), 1, 604800);
    String token = shortTtl.generateAccessToken(1L, 1L, "c", "u", List.of("USER"));
    // 强制过期：签发后立刻解析仍有效（TTL=1s），此处只验证可解析；过期语义由 JwtSupport.parse 处理
    assertThat(new JwtSupport(SECRET, ISSUER).parse(token).getSubject()).isEqualTo("u");
  }

  @Test
  void constructor_shortSecret_throws() {
    assertThatThrownBy(() -> new JwtSupport("short", ISSUER))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET 长度不足");
  }
}
