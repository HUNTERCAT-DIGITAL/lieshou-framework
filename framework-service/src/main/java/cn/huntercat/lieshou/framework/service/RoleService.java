package cn.huntercat.lieshou.framework.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;
import cn.huntercat.lieshou.framework.domain.Role;
import cn.huntercat.lieshou.framework.domain.RoleRepository;
import java.util.List;

/**
 * 角色管理业务（RBAC · ADR-0024，三套产品线共用）.
 *
 * <p>从两端 Controller（user-service / lieshou-boot backend）内联收敛：写操作保护 system 角色、 code 唯一性；错误抛 {@link
 * BaseException}（错误码契约：ROLE_CODE_TAKEN / SYSTEM_ROLE_READONLY / NOT_FOUND），由 GlobalExceptionHandler
 * 统一转 {error, message} + 状态码。
 */
@Service
public class RoleService {

  private final RoleRepository repo;

  public RoleService(RoleRepository repo) {
    this.repo = repo;
  }

  /** 角色列表（按 scope + id 排序）。 */
  @Transactional(readOnly = true)
  public List<Role> list() {
    return repo.findByOrderByScopeAscIdAsc();
  }

  /** 创建自定义角色；code 唯一。 */
  @Transactional
  public Role create(String code, String name, Role.Scope scope, String description) {
    if (repo.findByCode(code).isPresent()) {
      throw new BaseException("ROLE_CODE_TAKEN", HttpStatus.BAD_REQUEST, "角色编码已存在");
    }
    Role.Scope resolvedScope = scope == null ? Role.Scope.TENANT : scope;
    return repo.save(new Role(code, name, resolvedScope, description, false));
  }

  /** 更新角色（name/description/scope；code 不可变；system 角色只读）。 */
  @Transactional
  public Role update(Long id, String name, String scope, String description) {
    Role role =
        repo.findById(id).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "角色不存在"));
    if (role.isSystem()) {
      throw new BaseException("SYSTEM_ROLE_READONLY", HttpStatus.BAD_REQUEST, "系统角色不可修改");
    }
    if (name != null && !name.isBlank()) {
      role.setName(name);
    }
    if (description != null) {
      role.setDescription(description);
    }
    if (scope != null && !scope.isBlank()) {
      role.setScope(Role.Scope.valueOf(scope));
    }
    return repo.save(role);
  }

  /** 删除自定义角色；system 角色不可删。 */
  @Transactional
  public void delete(Long id) {
    Role role =
        repo.findById(id).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "角色不存在"));
    if (role.isSystem()) {
      throw new BaseException("SYSTEM_ROLE_READONLY", HttpStatus.BAD_REQUEST, "系统角色不可删除");
    }
    repo.delete(role);
  }
}
