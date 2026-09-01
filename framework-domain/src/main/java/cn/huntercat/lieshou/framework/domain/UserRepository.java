package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

  /** 租户内按 username 查（多租户后登录/鉴权必须带 tenant 维度 · ADR-0022） */
  Optional<User> findByTenantIdAndUsername(Long tenantId, String username);

  /** 全局按 username 查（仅平台管理/兼容场景使用） */
  Optional<User> findByUsername(String username);

  /** 按用户名查所有租户的用户（多租户登录前 · tenant-options） */
  List<User> findAllByUsername(String username);

  /** 按手机号查（租户内唯一 · 2026-08 方案 B 调整：手机号按租户隔离） */
  Optional<User> findByPhone(String phone);

  /** 全量按手机号查（authViewByPhone 跨租户首个 · 2026-09） */
  List<User> findAllByPhone(String phone);

  /** 租户内按手机号查（ADR-0023 手机号租户内唯一 · 2026-08） */
  Optional<User> findByTenantIdAndPhone(Long tenantId, String phone);

  /** 按邮箱查（全局唯一） */
  Optional<User> findByEmail(String email);

  /** 按租户列出用户（租户内 CRUD 强制过滤 · ADR-0022） */
  List<User> findByTenantId(Long tenantId);

  /** 判断租户内 username 是否已存在 */
  boolean existsByTenantIdAndUsername(Long tenantId, String username);

  /** 统计租户下用户数（租户删除保护用） */
  long countByTenantId(Long tenantId);
}
