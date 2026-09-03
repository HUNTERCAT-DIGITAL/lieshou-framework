package cn.huntercat.lieshou.framework.rules.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 规则引擎 DTO（创建/更新）. */
public final class RuleDtos {

  private RuleDtos() {}

  @Schema(description = "创建规则")
  public record CreateRule(
      @Schema(description = "规则编码", example = "member.dues.overdue") String code,
      @Schema(description = "规则名称", example = "会费逾期催缴") String name,
      @Schema(description = "规则领域", example = "dues") String domain,
      String description,
      @Schema(description = "SpEL 条件", example = "#status == 'DUE' and #daysOverdue > 30")
          String condition,
      @Schema(description = "动作类型", example = "SEND_MAIL") String action,
      @Schema(description = "动作参数 JSON") String actionParams,
      Integer priority,
      Boolean enabled) {}

  @Schema(description = "更新规则")
  public record UpdateRule(
      String name,
      String description,
      String condition,
      String action,
      String actionParams,
      Integer priority,
      Boolean enabled) {}
}
