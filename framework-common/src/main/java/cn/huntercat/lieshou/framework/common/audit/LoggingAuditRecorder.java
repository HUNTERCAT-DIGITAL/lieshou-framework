package cn.huntercat.lieshou.framework.common.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 默认审计写入：结构化日志（logger 名 AUDIT · L2-1 · Bottom-Up）. */
@Component
@ConditionalOnMissingBean(AuditRecorder.class)
public class LoggingAuditRecorder implements AuditRecorder {

  private static final Logger log = LoggerFactory.getLogger("AUDIT");

  @Override
  public void record(AuditEvent event) {
    log.info(
        "audit tenant={} user={} action={} resource={} resourceId={} outcome={} detail={} ip={} ua={}",
        event.tenantId(),
        event.userId(),
        event.action(),
        event.resource(),
        event.resourceId(),
        event.outcome(),
        event.detail(),
        event.clientIp(),
        event.userAgent());
  }
}
