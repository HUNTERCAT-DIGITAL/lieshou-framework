package cn.huntercat.lieshou.framework.rules;

/**
 * 规则执行结果（单规则一次 fire 的结果）.
 *
 * @param ruleCode 规则编码
 * @param action 动作类型
 * @param executed 是否实际执行（false = 无处理器被跳过）
 * @param message 结果/错误信息
 */
public record RuleExecution(String ruleCode, String action, boolean executed, String message) {

  public static RuleExecution ok(String ruleCode, String action, String message) {
    return new RuleExecution(ruleCode, action, true, message);
  }

  public static RuleExecution skipped(String ruleCode, String action) {
    return new RuleExecution(ruleCode, action, false, "无匹配的动作处理器");
  }

  public static RuleExecution failed(String ruleCode, String action, String error) {
    return new RuleExecution(ruleCode, action, false, error);
  }
}
