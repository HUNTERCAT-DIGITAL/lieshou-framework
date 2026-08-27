package cn.huntercat.lieshou.framework.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/** JwtSupport 单测（HS256 签发/解析/验证闭环 · Bottom-Up 抽象）. */
class JwtSupportTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 字节

  private final JwtSupport jwt = new JwtSupport(SECRET, "lieshoucloud");

  private String sign(Long uid, String username, List<String> roles, long ttlSeconds) {
    return Jwts.builder()
        .issuer(jwt.issuer())
        .subject(username)
        .claims(
            Map.of(
                JwtSupport.CLAIM_UID,
                uid,
                JwtSupport.CLAIM_ROLES,
                roles,
                JwtSupport.CLAIM_TYPE,
                "access"))
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(Instant.now().plusSeconds(ttlSeconds)))
        .signWith(jwt.signingKey(), Jwts.SIG.HS256)
        .compact();
  }

  @Test
  void parse_roundTrip_returnsClaims() {
    String token = sign(42L, "alice", List.of("TENANT_ADMIN"), 3600);

    Claims claims = jwt.parse(token);
    assertThat(claims.getSubject()).isEqualTo("alice");
    assertThat(claims.get(JwtSupport.CLAIM_UID, Long.class)).isEqualTo(42L);
    assertThat(claims.get(JwtSupport.CLAIM_ROLES, List.class)).containsExactly("TENANT_ADMIN");
    assertThat(claims.getIssuer()).isEqualTo("lieshoucloud");
  }

  @Test
  void validate_acceptsValidToken() {
    assertThat(jwt.validate(sign(1L, "bob", List.of(), 3600))).isTrue();
  }

  @Test
  void validate_rejectsExpiredToken() {
    String expired = sign(1L, "bob", List.of(), -10);
    assertThat(jwt.validate(expired)).isFalse();
  }

  @Test
  void validate_rejectsTamperedToken() {
    String token = sign(1L, "bob", List.of(), 3600);
    String tampered = token.substring(0, token.length() - 2) + "xx";
    assertThat(jwt.validate(tampered)).isFalse();
  }

  @Test
  void validate_rejectsWrongIssuer() {
    JwtSupport other = new JwtSupport(SECRET, "evil-issuer");
    String token =
        Jwts.builder()
            .issuer("evil-issuer")
            .subject("x")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(other.signingKey(), Jwts.SIG.HS256)
            .compact();
    assertThat(jwt.validate(token)).isFalse();
  }

  @Test
  void shortSecret_throws() {
    assertThatThrownBy(() -> new JwtSupport("short", "lieshoucloud"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET 长度不足");
  }
}
