package io.camunda.demo.process_order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.client.annotation.JobWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class HospitalReferralModelTest {
    private static final String BPMN_RESOURCE =
            "Hospital_Patient_Referral_Treatment_Management_Complete_v3.bpmn";

    private static Document document;

    @BeforeAll
    static void loadModel() throws Exception {
        InputStream input = HospitalReferralModelTest.class.getClassLoader()
                .getResourceAsStream(BPMN_RESOURCE);
        assertNotNull(input, "The hospital BPMN file must be copied to the test classpath");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        document = factory.newDocumentBuilder().parse(input);
    }

    @Test
    void referralMessageStartsHospitalProcessDirectly() throws Exception {
        Element startEvent = elementById("Hospital_Referral_Process_Started");
        assertNotNull(startEvent, "The hospital process must retain an explicit start event");

        NodeList messageDefinitions = startEvent.getElementsByTagNameNS(
                "http://www.omg.org/spec/BPMN/20100524/MODEL", "messageEventDefinition");
        assertEquals(1, messageDefinitions.getLength(), "The hospital entry point must be a message start event");
        assertEquals("Msg_Referral", ((Element) messageDefinitions.item(0)).getAttribute("messageRef"));

        assertFalse(hasElement("Receive_Referral"), "The message start event already receives the referral and must not wait for a second message");

        Element messageFlow = elementById("M_T01_T02");
        assertEquals("Hospital_Referral_Process_Started", messageFlow.getAttribute("targetRef"));

        Element sequenceFlow = elementById("S_Hospital_Start_T03");
        assertEquals("Hospital_Referral_Process_Started", sequenceFlow.getAttribute("sourceRef"));
        assertEquals("Create_Referral_Record", sequenceFlow.getAttribute("targetRef"));
    }

    @Test
    void referringProcessCollectsReferralDetailsBeforeSending() throws Exception {
        Element task = elementById("Enter_Referral_Details");
        assertNotNull(task, "The referral process must include a user task for entering referral details");
        assertEquals("userTask", task.getLocalName());

        NodeList formDefinitions = task.getElementsByTagNameNS(
                "http://camunda.org/schema/zeebe/1.0", "formDefinition");
        assertEquals(1, formDefinitions.getLength());
        assertEquals("Enter_Referral_Details",
                ((Element) formDefinitions.item(0)).getAttribute("formId"));

        Element startToTask = elementById("S_Ref_Start_EnterDetails");
        assertEquals("Referral_Process_Started", startToTask.getAttribute("sourceRef"));
        assertEquals("Enter_Referral_Details", startToTask.getAttribute("targetRef"));

        Element taskToSend = elementById("S_Ref_EnterDetails_T01");
        assertEquals("Enter_Referral_Details", taskToSend.getAttribute("sourceRef"));
        assertEquals("Prepare_and_Submit_Referral", taskToSend.getAttribute("targetRef"));
        assertFalse(hasElement("S_Ref_Start_T01"), "The start event must not connect directly to the send task");
    }

    @Test
    void referralDetailsFormContainsOnlyTheRequestedFields() throws Exception {
        Path formPath = Path.of("..", "Enter_Referral_Details.form");
        assertTrue(Files.exists(formPath), "A form file must be created for the new user task");

        JsonNode form = new ObjectMapper().readTree(Files.readString(formPath));
        assertEquals("Enter_Referral_Details", form.get("id").asText());

        JsonNode components = form.get("components");
        Set<String> fieldKeys = java.util.stream.IntStream.range(0, components.size())
                .mapToObj(components::get)
                .filter(component -> component.has("key"))
                .map(component -> component.get("key").asText())
                .collect(Collectors.toSet());
        assertEquals(Set.of("patientName", "referralNotes"), fieldKeys);

        JsonNode patientName = java.util.stream.IntStream.range(0, components.size())
                .mapToObj(components::get)
                .filter(component -> component.has("key"))
                .filter(component -> "patientName".equals(component.get("key").asText()))
                .findFirst()
                .orElseThrow();
        assertTrue(patientName.path("validate").path("required").asBoolean());

        JsonNode referralNotes = java.util.stream.IntStream.range(0, components.size())
                .mapToObj(components::get)
                .filter(component -> component.has("key"))
                .filter(component -> "referralNotes".equals(component.get("key").asText()))
                .findFirst()
                .orElseThrow();
        assertFalse(referralNotes.path("validate").path("required").asBoolean());
    }

    @Test
    void referralSubmissionRequiresPatientName() {
        assertThrows(IllegalArgumentException.class,
                () -> HospitalReferralWorker.validateReferralVariables(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> HospitalReferralWorker.validateReferralVariables(Map.of("patientName", "  ")));

        HospitalReferralWorker.validateReferralVariables(Map.of("patientName", "Jane Doe"));
    }

    @Test
    void appointmentAvailabilityUsesRequestedSlotAndGatewayDecision() throws Exception {
        Element requestedSlot = elementById("Prepare_Appointment_Search");
        assertEquals("Requested Appointment Slot", requestedSlot.getAttribute("name"));
        assertFalse(hasElement("Record_Appointment_Availability"));
        assertFalse(hasElement("Review_Available_Appointment_Slots"));

        Element checkTask = elementById("Check_Appointment_Availability");
        assertNotNull(checkTask);
        Element checkToGateway = elementById("S_T12_G03");
        assertEquals("Check_Appointment_Availability", checkToGateway.getAttribute("sourceRef"));
        assertEquals("Suitable_Appointment_Available", checkToGateway.getAttribute("targetRef"));

        Element availableFlow = elementById("S_G03_T15");
        assertEquals("= suitableAppointmentAvailable = \"AVAILABLE\"",
                availableFlow.getElementsByTagNameNS(
                        "http://www.omg.org/spec/BPMN/20100524/MODEL", "conditionExpression")
                        .item(0).getTextContent().trim());
        Element unavailableFlow = elementById("S_G03_T11");
        assertEquals("= suitableAppointmentAvailable = \"NOT_AVAILABLE\"",
                unavailableFlow.getElementsByTagNameNS(
                        "http://www.omg.org/spec/BPMN/20100524/MODEL", "conditionExpression")
                        .item(0).getTextContent().trim());
        assertEquals("Prepare_Appointment_Search", unavailableFlow.getAttribute("targetRef"));
        assertFalse(hasElement("Wait_for_Appointment_Slots"));
        assertFalse(hasElement("Treatment_Paused"));
        assertFalse(hasElement("TimerEventDefinition_Timer_WaitSlots"));
        assertFalse(hasElement("TimerEventDefinition_Timer_TreatmentPause"));
        Element pauseFlow = elementById("S_G09_T36");
        assertEquals("Review_Treatment_Results", pauseFlow.getAttribute("targetRef"));
    }

    @Test
    void appointmentFormsExposeMatchingVariableKeys() throws Exception {
        assertFormKeys(Path.of("..", "Requested_Appointment_Slot.form"),
                "requestedAppointmentDate", "requestedAppointmentTime");
        assertFormKeys(Path.of("..", "Check_Appointment_Availability.form"),
                "suitableAppointmentAvailable");
    }

    @Test
    void bookInitialAppointmentFormOnlyRequiresPatientName() throws Exception {
        assertFormKeys(Path.of("..", "Book_Initial_Appointment.form"), "patientName");
    }

    @Test
    void notificationMessageIsOptional() throws Exception {
        Path formPath = Path.of("..", "Notify_Referral_Parties.form");
        assertTrue(Files.exists(formPath));
        JsonNode components = new ObjectMapper().readTree(Files.readString(formPath)).get("components");
        JsonNode message = java.util.stream.IntStream.range(0, components.size())
                .mapToObj(components::get)
                .filter(component -> "notificationMessage".equals(component.path("key").asText()))
                .findFirst()
                .orElseThrow();
        assertFalse(message.path("validate").path("required").asBoolean(false));
    }

    @Test
    void everyAutomatedTaskHasAJavaWorker() throws Exception {
        Set<String> taskTypes = taskDefinitionTypes();

        Class<?> workerClass = Class.forName("io.camunda.demo.process_order.HospitalReferralWorker");
        Set<String> workerTypes = Arrays.stream(workerClass.getDeclaredMethods())
                .map(method -> method.getAnnotation(JobWorker.class))
                .filter(annotation -> annotation != null)
                .map(JobWorker::type)
                .collect(Collectors.toSet());

        assertEquals(taskTypes, workerTypes,
                () -> "BPMN and Java Worker types do not match. Missing: "
                        + difference(taskTypes, workerTypes)
                        + ", extra: " + difference(workerTypes, taskTypes));
    }

    @Test
    void everyMessageUsesTheProcessCorrelationKey() throws Exception {
        NodeList messages = (NodeList) XPathFactory.newInstance().newXPath().evaluate(
                "//*[local-name()='message']", document, XPathConstants.NODESET);
        assertEquals(20, messages.getLength(), "Java message constants must be updated when the number of message definitions changes");

        for (int index = 0; index < messages.getLength(); index++) {
            Element message = (Element) messages.item(index);
            NodeList subscriptions = message.getElementsByTagNameNS(
                    "http://camunda.org/schema/zeebe/1.0", "subscription");
            assertEquals(1, subscriptions.getLength(), message.getAttribute("id") + " must define a message subscription");
            assertEquals("=correlationKey",
                    ((Element) subscriptions.item(0)).getAttribute("correlationKey"));
        }
    }

    @Test
    void javaMessageConstantsMatchEveryBpmnMessageName() throws Exception {
        NodeList messages = (NodeList) XPathFactory.newInstance().newXPath().evaluate(
                "//*[local-name()='message']", document, XPathConstants.NODESET);
        Set<String> bpmnMessageNames = java.util.stream.IntStream.range(0, messages.getLength())
                .mapToObj(messages::item)
                .map(node -> ((Element) node).getAttribute("name"))
                .collect(Collectors.toSet());

        Set<String> javaMessageNames = Arrays.stream(HospitalReferralWorker.class.getDeclaredFields())
                .filter(field -> field.getName().startsWith("MSG_"))
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .map(HospitalReferralModelTest::readStringConstant)
                .collect(Collectors.toSet());

        assertEquals(bpmnMessageNames, javaMessageNames,
                () -> "BPMN and Java message names do not match. Missing from Java: "
                        + difference(bpmnMessageNames, javaMessageNames)
                        + ", extra in Java: " + difference(javaMessageNames, bpmnMessageNames));
    }

    private static Set<String> taskDefinitionTypes() throws Exception {
        NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath().evaluate(
                "//*[local-name()='taskDefinition']", document, XPathConstants.NODESET);

        return java.util.stream.IntStream.range(0, nodes.getLength())
                .mapToObj(nodes::item)
                .map(node -> ((Element) node).getAttribute("type"))
                .filter(type -> !type.isBlank())
                .collect(Collectors.toSet());
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        return expected.stream().filter(type -> !actual.contains(type)).collect(Collectors.toSet());
    }

    private static String readStringConstant(Field field) {
        try {
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read message constant: " + field.getName(), exception);
        }
    }

    private static boolean hasElement(String id) throws Exception {
        return elementById(id) != null;
    }

    private static void assertFormKeys(Path formPath, String... expectedKeys) throws Exception {
        assertTrue(Files.exists(formPath), "The form file must exist: " + formPath);
        JsonNode components = new ObjectMapper().readTree(Files.readString(formPath)).get("components");
        Set<String> actualKeys = java.util.stream.IntStream.range(0, components.size())
                .mapToObj(components::get)
                .filter(component -> component.has("key"))
                .map(component -> component.get("key").asText())
                .collect(Collectors.toSet());
        assertEquals(Set.of(expectedKeys), actualKeys);
    }

    private static Element elementById(String id) throws Exception {
        return (Element) XPathFactory.newInstance().newXPath().evaluate(
                "//*[@id='" + id + "']", document, XPathConstants.NODE);
    }
}
