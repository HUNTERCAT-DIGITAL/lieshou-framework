package cn.huntercat.lieshou.framework.rules;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import cn.huntercat.lieshou.framework.rules.domain.RuleDefinition;
import cn.huntercat.lieshou.framework.rules.domain.RuleDefinitionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 轻量规则引擎（SpEL 条件匹配 + 动作处理器分发 · ADR-0035）.
 *
 * <p>规则来源 = 全局规则（tenant_id 为 null）+ 租户规则；按 priority 降序匹配。 条件用 SpEL 表达式，facts 以变量注入（ {@code #status
 * == 'DUE'}）。 动作由消费方注册的 {@link RuleActionHandler} 执行。
 */
@Service
public class RuleEngine {

  private final RuleDefinitionRepository repo;
  private final Map<String, RuleActionHandler> handlers;
  private final SpelExpressionParser parser = new SpelExpressionParser();

  public RuleEngine(RuleDefinitionRepository repo, List<RuleActionHandler> actionHandlers) {
    this.repo = repo;
    this.handlers =
        actionHandlers == null
            ? Map.of()
            : actionHandlers.stream()
                .collect(
                    Collectors.toMap(RuleActionHandler::action, Function.identity(), (a, b) -> a));
  }

  /** 匹配规则（只评估条件，不执行动作） */
  public List<RuleDefinition> match(Long tenantId, String domain, Map<String, Object> facts) {
    return rules(tenantId, domain).stream().filter(r -> evaluate(r.getCondition(), facts)).toList();
  }

  /** 匹配并执行动作（按优先级降序） */
  public List<RuleExecution> fire(Long tenantId, String domain, Map<String, Object> facts) {
    return match(tenantId, domain, facts).stream().map(r -> execute(r, facts)).toList();
  }

  /** 评估单条 SpEL 条件（空 = 恒真；表达式异常视为不匹配） */
  public boolean evaluate(String condition, Map<String, Object> facts) {
    if (condition == null || condition.isBlank()) return true;
    try {
      StandardEvaluationContext ctx = new StandardEvaluationContext();
      if (facts != null) facts.forEach(ctx::setVariable);
      Expression exp = parser.parseExpression(condition);
      return Boolean.TRUE.equals(exp.getValue(ctx, Boolean.class));
    } catch (Exception e) {
      return false;
    }
  }

  /** 判断给定规则是否匹配（供外部复用，不依赖持久化） */
  public boolean matches(RuleDefinition rule, Map<String, Object> facts) {
    return rule != null && rule.isEnabled() && evaluate(rule.getCondition(), facts);
  }

  private List<RuleDefinition> rules(Long tenantId, String domain) {
    List<RuleDefinition> global =
        repo.findByTenantIdIsNullAndDomainAndEnabledTrueOrderByPriorityDesc(domain);
    List<RuleDefinition> tenant =
        tenantId == null
            ? List.of()
            : repo.findByTenantIdAndDomainAndEnabledTrueOrderByPriorityDesc(tenantId, domain);
    // 租户规则可覆盖同 code 的全局规则
    Map<String, RuleDefinition> merged = new LinkedHashMap<>();
    global.forEach(r -> merged.put(r.getCode(), r));
    tenant.forEach(r -> merged.put(r.getCode(), r));
    return merged.values().stream()
        .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private RuleExecution execute(RuleDefinition rule, Map<String, Object> facts) {
    RuleActionHandler handler = handlers.get(rule.getAction());
    if (handler == null) return RuleExecution.skipped(rule.getCode(), rule.getAction());
    try {
      return handler.execute(rule, facts);
    } catch (Exception e) {
      return RuleExecution.failed(rule.getCode(), rule.getAction(), e.getMessage());
    }
  }
}
