package io.camunda.demo.process_order;

import io.camunda.client.api.response.ActivatedJob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 负责读写流程业务记录。 */
@Repository
public class WorkflowRecordRepository {
    private final JdbcTemplate jdbcTemplate;

    public WorkflowRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String recordType, ActivatedJob job, String variablesJson) {
        Object correlationKeyValue = job.getVariablesAsMap().get("correlationKey");
        String correlationKey;
        if (correlationKeyValue == null) {
            correlationKey = null;
        } else {
            correlationKey = correlationKeyValue.toString();
        }

        jdbcTemplate.update(
                """
                INSERT INTO workflow_record (
                    record_type,
                    process_instance_key,
                    job_key,
                    correlation_key,
                    variables_json,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                recordType,
                job.getProcessInstanceKey(),
                job.getKey(),
                correlationKey,
                variablesJson,
                Instant.now());
    }

    public List<WorkflowRecord> findByProcessInstanceKey(long processInstanceKey) {
        return jdbcTemplate.query(
                """
                SELECT id, record_type, process_instance_key, job_key,
                       correlation_key, variables_json, created_at
                FROM workflow_record
                WHERE process_instance_key = ?
                ORDER BY id
                """,
                this::mapRecord,
                processInstanceKey);
    }

    public List<WorkflowRecord> findByCorrelationKey(String correlationKey) {
        return jdbcTemplate.query(
                """
                SELECT id, record_type, process_instance_key, job_key,
                       correlation_key, variables_json, created_at
                FROM workflow_record
                WHERE correlation_key = ?
                ORDER BY id
                """,
                this::mapRecord,
                correlationKey);
    }

    private WorkflowRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkflowRecord(
                resultSet.getLong("id"),
                resultSet.getString("record_type"),
                resultSet.getLong("process_instance_key"),
                resultSet.getLong("job_key"),
                resultSet.getString("correlation_key"),
                resultSet.getString("variables_json"),
                resultSet.getObject("created_at", Instant.class));
    }
}
