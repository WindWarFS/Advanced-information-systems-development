package io.camunda.demo.process_order;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = HospitalReferralApplication.class)
@CamundaSpringProcessTest
class HospitalReferralProcessTest {
    private static final String BPMN_RESOURCE =
            "Hospital_Patient_Referral_Treatment_Management_Complete_v3.bpmn";

    @Autowired
    private CamundaClient client;

    @Autowired
    private WorkflowRecordRepository workflowRecordRepository;

    @Test
    void submittingReferralAutomaticallyStartsHospitalProcess() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath(BPMN_RESOURCE)
                .send()
                .join();

        ProcessInstanceEvent referringProcess = client.newCreateInstanceCommand()
                .bpmnProcessId("Process_Referring")
                .latestVersion()
                .variables(Map.of(
                        "correlationKey", "referral-test-001",
                        "patientName", "测试患者"))
                .send()
                .join();

        CamundaAssert.assertThat(referringProcess)
                .hasCompletedElements("Prepare_and_Submit_Referral")
                .isCompleted();

        CamundaAssert.assertThat(ProcessInstanceSelectors.byProcessId("Process_Hospital"))
                .hasCompletedElements(
                        "Hospital_Referral_Process_Started",
                        "Create_Referral_Record")
                .hasActiveElements("Check_Referral_Documents")
                .hasVariable("correlationKey", "referral-test-001")
                .hasNoActiveIncidents();

        List<WorkflowRecord> records = workflowRecordRepository.findByCorrelationKey(
                "referral-test-001");
        Assertions.assertEquals(1, records.size());
        Assertions.assertEquals("REFERRAL_CREATED", records.getFirst().recordType());
        Assertions.assertEquals("referral-test-001", records.getFirst().correlationKey());
    }
}
