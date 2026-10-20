package io.camunda.demo.process_order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import org.springframework.stereotype.Service;

/** 将 Camunda 任务数据保存为数据库记录。 */
@Service
public class WorkflowRecordService {
    private final WorkflowRecordRepository repository;
    private final ObjectMapper objectMapper;

    public WorkflowRecordService(WorkflowRecordRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    public void save(String recordType, ActivatedJob job) {
        try {
            String variablesJson = objectMapper.writeValueAsString(job.getVariablesAsMap());
            repository.save(recordType, job, variablesJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize process variables", exception);
        }
    }
}
