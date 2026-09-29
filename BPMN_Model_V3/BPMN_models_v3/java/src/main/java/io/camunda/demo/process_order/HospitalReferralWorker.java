package io.camunda.demo.process_order;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 医院转诊流程中自动节点对应的任务处理器。 */
@Component
public class HospitalReferralWorker {
    private static final Logger LOG = LoggerFactory.getLogger(HospitalReferralWorker.class);
    private static final String MSG_REFERRAL = "Referral";
    private static final String MSG_MISSING_INFORMATION_REQUEST = "Missing Information Request";
    private static final String MSG_MISSING_INFORMATION_RESPONSE = "Missing Information Response";
    private static final String MSG_REFERRAL_DECISION_PATIENT = "Referral Decision for Patient";
    private static final String MSG_REFERRAL_DECISION_REFERRER = "Referral Decision for Referring Organisation";
    private static final String MSG_APPOINTMENT_DETAILS = "Appointment Details";
    private static final String MSG_APPOINTMENT_CHANGE = "Appointment Change";
    private static final String MSG_APPOINTMENT_CANCELLATION = "Appointment Cancellation";
    private static final String MSG_CONSULTATION_ATTENDANCE = "Consultation Attendance";
    private static final String MSG_TREATMENT_PROPOSAL = "Treatment Proposal";
    private static final String MSG_CONSENT_DECISION = "Consent Decision";
    private static final String MSG_PAYMENT_REQUEST = "Payment Request";
    private static final String MSG_PAYMENT_INFORMATION = "Payment Information";
    private static final String MSG_PAYMENT_RESULT = "Payment Result";
    private static final String MSG_FUNDING_REQUEST = "Funding Approval Request";
    private static final String MSG_FUNDING_RESULT = "Funding Approval Result";
    private static final String MSG_TREATMENT_ARRANGEMENT = "Treatment Arrangement";
    private static final String MSG_TREATMENT_ATTENDANCE = "Treatment Attendance";
    private static final String MSG_CLINIC_LETTER_PATIENT = "Clinic Letter - Patient";
    private static final String MSG_CLINIC_LETTER_REFERRER = "Clinic Letter";
    /** 消息开始事件按消息名启动流程，不使用现有流程实例的关联键。 */
    private static final Set<String> MESSAGE_START_EVENT_NAMES = Set.of(
            MSG_REFERRAL,
            MSG_MISSING_INFORMATION_REQUEST,
            MSG_APPOINTMENT_DETAILS,
            MSG_APPOINTMENT_CHANGE,
            MSG_APPOINTMENT_CANCELLATION,
            MSG_CONSULTATION_ATTENDANCE,
            MSG_TREATMENT_PROPOSAL,
            MSG_PAYMENT_REQUEST,
            MSG_PAYMENT_INFORMATION,
            MSG_FUNDING_REQUEST,
            MSG_TREATMENT_ARRANGEMENT,
            MSG_TREATMENT_ATTENDANCE,
            MSG_CLINIC_LETTER_PATIENT,
            MSG_CLINIC_LETTER_REFERRER,
            MSG_REFERRAL_DECISION_PATIENT,
            MSG_REFERRAL_DECISION_REFERRER);

    private final CamundaClient client;

    public HospitalReferralWorker(CamundaClient client) {
        this.client = client;
    }

    @JobWorker(type = "Prepare_and_Submit_Referral")
    public Map<String, Object> sendReferral(ActivatedJob job) {
        validateReferralVariables(job.getVariablesAsMap());
        return publishMessageAndMark(
                job,
                MSG_REFERRAL,
                "referralSent");
    }


    static void validateReferralVariables(Map<?, ?> variables) {
        Object patientNameValue = variables.get("patientName");
        String patientName;
        if (patientNameValue == null) {
            patientName = "";
        } else {
            patientName = patientNameValue.toString().trim();
        }
        if (patientName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Patient name patientName is required before submitting the referral");
        }
    }

    @JobWorker(type = "Create_Referral_Record")
    public Map<String, Object> createReferralRecord(ActivatedJob job) {
        return mark(
                job,
                "referralRecordCreated",
                true);
    }

    @JobWorker(type = "Store_Treatment_Results")
    public Map<String, Object> storeTreatmentResults(ActivatedJob job) {
        return mark(
                job,
                "treatmentResultsStored",
                true);
    }

    @JobWorker(type = "Record_Clinical_Decision")
    public Map<String, Object> recordClinicalDecision(ActivatedJob job) {
        return mark(
                job,
                "clinicalDecisionRecorded",
                true);
    }

    @JobWorker(type = "Request_Missing_Information")
    public Map<String, Object> requestMissingInformation(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_MISSING_INFORMATION_REQUEST,
                "missingInformationRequested");
    }

    @JobWorker(type = "Send_Appointment_Details")
    public Map<String, Object> sendAppointmentDetails(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_APPOINTMENT_DETAILS,
                "appointmentDetailsSent");
    }

    @JobWorker(type = "Store_Treatment_Request")
    public Map<String, Object> storeTreatmentRequest(ActivatedJob job) {
        return mark(
                job,
                "treatmentRequestStored",
                true);
    }

    @JobWorker(type = "Process_Payment_Transaction_or_Funding_Approval")
    public Map<String, Object> processPaymentOrFunding(ActivatedJob job) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> variables = job.getVariablesAsMap();
        String requestType = String.valueOf(variables.getOrDefault("requestType", "payment"));
        result.put("financialStatus", "cleared");
        result.put("processedRequestType", requestType);
        return result;
    }

    @JobWorker(type = "Send_Treatment_Proposal")
    public Map<String, Object> sendTreatmentProposal(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_TREATMENT_PROPOSAL,
                "treatmentProposalSent");
    }

    @JobWorker(type = "Send_Funding_Approval_Request")
    public Map<String, Object> sendFundingApprovalRequest(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_FUNDING_REQUEST,
                "fundingApprovalRequestSent");
    }

    @JobWorker(type = "Information_Sent")
    public Map<String, Object> informationSent(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_MISSING_INFORMATION_RESPONSE,
                "informationSent");
    }

    @JobWorker(type = "Change_Requested")
    public Map<String, Object> changeRequested(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_APPOINTMENT_CHANGE,
                "changeRequested");
    }

    @JobWorker(type = "Payment_Information_Sent")
    public Map<String, Object> paymentInformationSent(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_PAYMENT_INFORMATION,
                "paymentInformationSent");
    }

    @JobWorker(type = "Appointment_Cancelled_Patient")
    public Map<String, Object> appointmentCancelledPatient(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_APPOINTMENT_CANCELLATION,
                "appointmentCancelledPatient");
    }

    @JobWorker(type = "Consultation_Attended")
    public Map<String, Object> consultationAttended(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_CONSULTATION_ATTENDANCE,
                "consultationAttended");
    }

    @JobWorker(type = "Consent_Granted")
    public Map<String, Object> consentGranted(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_CONSENT_DECISION,
                "consentGranted");
    }

    @JobWorker(type = "Treatment_Refused")
    public Map<String, Object> treatmentRefused(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_CONSENT_DECISION,
                "treatmentRefused");
    }

    @JobWorker(type = "Treatment_Attended")
    public Map<String, Object> treatmentAttended(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_TREATMENT_ATTENDANCE,
                "treatmentAttended");
    }

    @JobWorker(type = "Funding_Result_Sent")
    public Map<String, Object> fundingResultSent(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_FUNDING_RESULT,
                "fundingResultSent");
    }

    @JobWorker(type = "Payment_Result_Sent")
    public Map<String, Object> paymentResultSent(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_PAYMENT_RESULT,
                "paymentResultSent");
    }

    @JobWorker(type = "Send_Referral_Decision")
    public Map<String, Object> sendReferralDecision(ActivatedJob job) {
        return publishMessagesAndMark(
                job,
                "referralDecisionSent",
                MSG_REFERRAL_DECISION_PATIENT,
                MSG_REFERRAL_DECISION_REFERRER);
    }

    @JobWorker(type = "Send_Payment_Request")
    public Map<String, Object> sendPaymentRequest(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_PAYMENT_REQUEST,
                "paymentRequestSent");
    }

    @JobWorker(type = "Send_Treatment_Arrangement")
    public Map<String, Object> sendTreatmentArrangement(ActivatedJob job) {
        return publishMessageAndMark(
                job,
                MSG_TREATMENT_ARRANGEMENT,
                "treatmentArrangementSent");
    }

    @JobWorker(type = "Send_Clinic_Letter")
    public Map<String, Object> sendClinicLetter(ActivatedJob job) {
        return publishMessagesAndMark(
                job,
                "clinicLetterSent",
                MSG_CLINIC_LETTER_PATIENT,
                MSG_CLINIC_LETTER_REFERRER);
    }

    private Map<String, Object> mark(ActivatedJob job, String key, Object value) {
        LOG.info("Executing job {} with type {}", job.getKey(), job.getType());
        return Map.of(key, value);
    }

    private Map<String, Object> publishMessageAndMark(
            ActivatedJob job,
            String messageName,
            String resultKey) {
        return publishMessagesAndMark(
                job,
                resultKey,
                messageName);
    }

    private Map<String, Object> publishMessagesAndMark(
            ActivatedJob job,
            String resultKey,
            String... messageNames) {
        Map<String, Object> variables = new HashMap<>(job.getVariablesAsMap());
        String correlationKey = resolveCorrelationKey(job, variables);

        for (String messageName : messageNames) {
            if (MESSAGE_START_EVENT_NAMES.contains(messageName)) {
                client.newPublishMessageCommand()
                        .messageName(messageName)
                        .correlationKey("")
                        .variables(variables)
                        .send()
                        .join();
            } else {
                client.newPublishMessageCommand()
                        .messageName(messageName)
                        .correlationKey(correlationKey)
                        .variables(variables)
                        .send()
                        .join();
            }
        }

        LOG.info(
                "Process instance {} published messages {} using correlation key {}",
                job.getProcessInstanceKey(),
                String.join(", ", messageNames),
                correlationKey);
        Map<String, Object> result = new HashMap<>();
        result.put("correlationKey", correlationKey);
        result.put(resultKey, true);
        return result;
    }

    private String resolveCorrelationKey(ActivatedJob job, Map<String, Object> variables) {
        Object configuredKey = variables.get("correlationKey");
        String correlationKey;
        if (configuredKey == null) {
            correlationKey = "";
        } else {
            correlationKey = configuredKey.toString().trim();
        }
        if (correlationKey.isEmpty()) {
            correlationKey = "referral-" + job.getProcessInstanceKey();
            variables.put("correlationKey", correlationKey);
        }
        return correlationKey;
    }
}
