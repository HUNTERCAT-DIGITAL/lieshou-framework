package cn.huntercat.lieshou.framework.rules;

import cn.huntercat.lieshou.framework.rules.domain.RuleDefinition;
import java.util.Map;

/**
 * 规则动作处理器（由消费方注册为 Spring Bean，按 action() 匹配）.
 *
 * <p>动作是领域相关的（发邮件/改状态/生成单据…），框架只提供引擎与定义，动作由消费方实现。
 */
public interface RuleActionHandler {

  /** 动作类型（与 RuleDefinition.action 对应） */
  String action();

  /** 执行动作；抛异常则引擎捕获并返回 {@link RuleExecution#failed} */
  RuleExecution execute(RuleDefinition rule, Map<String, Object> facts);
}
