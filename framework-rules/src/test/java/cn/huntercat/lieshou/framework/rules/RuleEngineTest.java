package cn.huntercat.lieshou.framework.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.rules.domain.RuleDefinition;
import cn.huntercat.lieshou.framework.rules.domain.RuleDefinitionRepository;
import java.util.List;
import java.util.Map;

/** 规则引擎核心测试（SpEL 条件匹配 + 优先级 + 动作分发） */
@ExtendWith(MockitoExtension.class)
class RuleEngineTest {

  @Mock private RuleDefinitionRepository repo;
  @Mock private RuleActionHandler mailHandler;
  @Mock private RuleActionHandler statusHandler;

  private RuleEngine engine;

  @BeforeEach
  void setUp() {
    when(mailHandler.action()).thenReturn("SEND_MAIL");
    when(statusHandler.action()).thenReturn("CHANGE_STATUS");
    engine = new RuleEngine(repo, List.of(mailHandler, statusHandler));
  }

  private RuleDefinition rule(
      String code, String domain, String condition, String action, int priority) {
    RuleDefinition r = new RuleDefinition(null, code, code, domain, condition, action);
    r.setPriority(priority);
    return r;
  }

  @Test
  void evaluate_blankCondition_isAlwaysTrue() {
    assertThat(engine.evaluate(null, Map.of())).isTrue();
    assertThat(engine.evaluate("  ", Map.of())).isTrue();
  }

  @Test
  void evaluate_spelCondition_matchesFacts() {
    assertThat(
            engine.evaluate("#status == 'DUE' and #days > 30", Map.of("status", "DUE", "days", 45)))
        .isTrue();
    assertThat(
            engine.evaluate("#status == 'DUE' and #days > 30", Map.of("status", "DUE", "days", 10)))
        .isFalse();
  }

  @Test
  void evaluate_invalidExpression_returnsFalse() {
    assertThat(engine.evaluate("#status ==' DUE", Map.of("status", "DUE"))).isFalse();
  }

  @Test
  void match_mergesGlobalAndTenantRules_withTenantOverride() {
    RuleDefinition global = rule("r1", "dues", "#status == 'DUE'", "SEND_MAIL", 1);
    RuleDefinition tenantOverride = rule("r1", "dues", "#status == 'PAID'", "CHANGE_STATUS", 2);
    when(repo.findByTenantIdIsNullAndDomainAndEnabledTrueOrderByPriorityDesc("dues"))
        .thenReturn(List.of(global));
    when(repo.findByTenantIdAndDomainAndEnabledTrueOrderByPriorityDesc(9L, "dues"))
        .thenReturn(List.of(tenantOverride));

    List<RuleDefinition> matched = engine.match(9L, "dues", Map.of("status", "PAID"));

    assertThat(matched).hasSize(1);
    assertThat(matched.get(0).getAction()).isEqualTo("CHANGE_STATUS");
  }

  @Test
  void fire_dispatchesToHandlerAndSkipsUnknown() {
    when(repo.findByTenantIdIsNullAndDomainAndEnabledTrueOrderByPriorityDesc("dues"))
        .thenReturn(
            List.of(
                rule("r1", "dues", "#status == 'DUE'", "SEND_MAIL", 2),
                rule("r2", "dues", null, "UNKNOWN_ACTION", 1)));
    when(repo.findByTenantIdAndDomainAndEnabledTrueOrderByPriorityDesc(eq(1L), eq("dues")))
        .thenReturn(List.of());
    when(mailHandler.execute(any(), anyMap()))
        .thenReturn(RuleExecution.ok("r1", "SEND_MAIL", "sent"));

    List<RuleExecution> results = engine.fire(1L, "dues", Map.of("status", "DUE"));

    assertThat(results).hasSize(2);
    assertThat(results.get(0).ruleCode()).isEqualTo("r1");
    assertThat(results.get(0).executed()).isTrue();
    assertThat(results.get(1).ruleCode()).isEqualTo("r2");
    assertThat(results.get(1).executed()).isFalse();
  }

  @Test
  void fire_handlerThrows_returnsFailed() {
    when(repo.findByTenantIdIsNullAndDomainAndEnabledTrueOrderByPriorityDesc("dues"))
        .thenReturn(List.of(rule("r1", "dues", null, "SEND_MAIL", 1)));
    when(repo.findByTenantIdAndDomainAndEnabledTrueOrderByPriorityDesc(eq(1L), eq("dues")))
        .thenReturn(List.of());
    when(mailHandler.execute(any(), anyMap())).thenThrow(new RuntimeException("mail down"));

    List<RuleExecution> results = engine.fire(1L, "dues", Map.of());

    assertThat(results).hasSize(1);
    assertThat(results.get(0).executed()).isFalse();
    assertThat(results.get(0).message()).contains("mail down");
  }
}
