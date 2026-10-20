package io.camunda.demo.process_order;

import java.time.Instant;

/** 数据库中的流程业务记录。 */
public record WorkflowRecord(
        long id,
        String recordType,
        long processInstanceKey,
        long jobKey,
        String correlationKey,
        String variablesJson,
        Instant createdAt) {
}
