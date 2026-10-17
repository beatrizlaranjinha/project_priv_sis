package pt.unl.fct.di.syspriv.xacml;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.DecisionType;
import org.ow2.authzforce.core.pdp.api.AttributeFqn;
import org.ow2.authzforce.core.pdp.api.AttributeFqns;
import org.ow2.authzforce.core.pdp.api.DecisionRequestBuilder;
import org.ow2.authzforce.core.pdp.api.DecisionResult;
import org.ow2.authzforce.core.pdp.api.value.AttributeBag;
import org.ow2.authzforce.core.pdp.api.value.Bags;
import org.ow2.authzforce.core.pdp.api.value.StandardDatatypes;
import org.ow2.authzforce.core.pdp.api.value.StringValue;
import org.ow2.authzforce.core.pdp.impl.BasePdpEngine;
import org.ow2.authzforce.core.pdp.impl.PdpEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.unl.fct.di.syspriv.entities.Appointment;
import pt.unl.fct.di.syspriv.entities.ConsentRecord;
import pt.unl.fct.di.syspriv.entities.Employee;
import pt.unl.fct.di.syspriv.entities.GDPRRequest;
import pt.unl.fct.di.syspriv.entities.Patient;
import pt.unl.fct.di.syspriv.entities.Payment;
import pt.unl.fct.di.syspriv.storage.Hibernate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class XACMLPDP {
    private static final String SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject";
    private static final String RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource";
    private static final String ACTION_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action";
    private static final String ENV_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:environment";

    private final BasePdpEngine pdp;
    private final Logger logger = LoggerFactory.getLogger(BasePdpEngine.class);

    public XACMLPDP() throws Exception {
        PdpEngineConfiguration config = PdpEngineConfiguration.getInstance(
                XACMLPDP.class.getClassLoader().getResource("pdp.xml").getPath());
        this.pdp = new BasePdpEngine(config);
    }
    //foi necessário mudar o método checkAccess para receber o lawfulBasis, pois é necessário para a avaliação da política de acesso.
    public boolean checkAccess(Object requester, Object resource, String context) throws Exception {
        return checkAccess(requester, resource, context, null);
    }

    public boolean checkAccess(Object requester, Object resource, String context, String lawfulBasis) throws Exception {
        Map<String, String> subjectAttrs = new HashMap<>();
        Map<String, String> resourceAttrs = new HashMap<>();
        Map<String, String> actionAttrs = new HashMap<>();
        Map<String, String> envAttrs = new HashMap<>();

        if (requester instanceof Patient patient) {
            subjectAttrs.put("id", patient.getStringId());
            subjectAttrs.put("role", "Patient");
        } else if (requester instanceof Employee employee) {
            subjectAttrs.put("id", employee.getStringId());
            subjectAttrs.put("role", employee.getRole());
        }

        Object dataSubject = resource;
        if (resource instanceof Employee employee) {
            resourceAttrs.put("id", employee.getStringId());
            resourceAttrs.put("resource-type", "EmployeeData");
        } else if (resource instanceof Patient patient) {
            resourceAttrs.put("id", patient.getStringId());
            resourceAttrs.put("resource-type", "PatientData");
            if (patient.getPrimaryDoctor() != null) {
                resourceAttrs.put("doctor-id", patient.getPrimaryDoctor().getStringId());
            }
        } else if (resource instanceof Appointment appointment) {
            resourceAttrs.put("resource-type", "AppointmentData");
            if (appointment.getPatient() != null) {
                dataSubject = appointment.getPatient();
                resourceAttrs.put("id", appointment.getPatient().getStringId());
                if (appointment.getPatient().getPrimaryDoctor() != null) {
                    resourceAttrs.put("doctor-id", appointment.getPatient().getPrimaryDoctor().getStringId());
                }
            }
            if (appointment.getEmployee() != null) {
                resourceAttrs.put("employee-id", appointment.getEmployee().getStringId());
            }
        } else if (resource instanceof Payment payment) {
            resourceAttrs.put("resource-type", "PaymentData");
            if (payment.getPatient() != null) {
                dataSubject = payment.getPatient();
                resourceAttrs.put("id", payment.getPatient().getStringId());
            }
        }

        addGdprAttributes(resourceAttrs, dataSubject, lawfulBasis);
        actionAttrs.put("action-id", "read");
        envAttrs.put("context", context == null ? "" : context);
        return evaluateAccessRequest(subjectAttrs, resourceAttrs, actionAttrs, envAttrs);
    }

    public boolean evaluateAccessRequest(
            Map<String, String> subjectAttrs,
            Map<String, String> resourceAttrs,
            Map<String, String> actionAttrs,
            Map<String, String> envAttrs) throws Exception {
        DecisionRequestBuilder<?> requestBuilder = pdp.newRequestBuilder(-1, -1);
        addAttributes(requestBuilder, SUBJECT_CATEGORY, subjectAttrs);
        addAttributes(requestBuilder, RESOURCE_CATEGORY, resourceAttrs);
        addAttributes(requestBuilder, ACTION_CATEGORY, actionAttrs);
        addAttributes(requestBuilder, ENV_CATEGORY, envAttrs);

        var request = requestBuilder.build(true);
        logger.debug(request.toString());
        DecisionResult result = pdp.evaluate(request);
        logger.debug(result.toString());
        return result.getDecision() == DecisionType.PERMIT;
    }

    private void addGdprAttributes(
            Map<String, String> resourceAttrs, Object dataSubject, String lawfulBasis) {
        resourceAttrs.put("lawful-basis",
                lawfulBasis == null || lawfulBasis.isBlank()
                        ? "missing"
                        : lawfulBasis.trim().toLowerCase(Locale.ROOT));

        resourceAttrs.put("data-category", dataSubject instanceof Patient ? "health-data" : "employee-data");

        List<ConsentRecord> consentRecords = consentRecordsFor(dataSubject);
        ConsentRecord marketingConsent = latestConsent(consentRecords, "marketing");
        ConsentRecord researchConsent = latestConsent(consentRecords, "research");
        resourceAttrs.put("consent-marketing", Boolean.toString(isActive(marketingConsent)));
        resourceAttrs.put("consent-withdrawn", Boolean.toString(isWithdrawn(marketingConsent)));
        resourceAttrs.put("consent-research", Boolean.toString(isActive(researchConsent)));

        List<GDPRRequest> requests = requestsFor(dataSubject);
        resourceAttrs.put("erasure-requested", Boolean.toString(requests.stream()
                .anyMatch(request -> "ERASURE".equalsIgnoreCase(request.getRequestType()))));

        LocalDateTime retentionUntil = retentionUntil(dataSubject);
        resourceAttrs.put("retention-expired", Boolean.toString(
                retentionUntil != null && retentionUntil.isBefore(LocalDateTime.now())));
    }

    private List<ConsentRecord> consentRecordsFor(Object subject) {
        if (subject instanceof Patient patient && patient.getId() != null) {
            return Hibernate.getInstance().jpql(
                    "FROM ConsentRecord WHERE patientSubjectId = " + patient.getId(), ConsentRecord.class);
        }
        if (subject instanceof Employee employee && employee.getId() != null) {
            return Hibernate.getInstance().jpql(
                    "FROM ConsentRecord WHERE employeeSubjectId = " + employee.getId(), ConsentRecord.class);
        }
        return List.of();
    }

    // Load dos GDPR requests para um subject
    private List<GDPRRequest> requestsFor(Object subject) {
        if (subject instanceof Patient patient && patient.getId() != null) {
            return Hibernate.getInstance().jpql(
                    "FROM GDPRRequest WHERE patientSubjectId = " + patient.getId(), GDPRRequest.class);
        }
        if (subject instanceof Employee employee && employee.getId() != null) {
            return Hibernate.getInstance().jpql(
                    "FROM GDPRRequest WHERE employeeSubjectId = " + employee.getId(), GDPRRequest.class);
        }
        return List.of();
    }

    // Seleciona o consent mais novo. Isto porque o ID é incremental, então o mais novo terá o maior ID. O timestamp de é usado para determinar a data do consentimento, e se houver empate, o ID é usado como critério de desempate.
    private ConsentRecord latestConsent(List<ConsentRecord> records, String purpose) {
        return records.stream()
                .filter(record -> purpose.equalsIgnoreCase(record.getPurpose()))
                .max(Comparator.comparing(
                                ConsentRecord::getGrantedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ConsentRecord::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }


    private boolean isActive(ConsentRecord record) {
        return record != null && Boolean.TRUE.equals(record.getGranted()) && record.getWithdrawnAt() == null;
    }

    // É withdraw quando o record mais novo tem um timestamp de withdraw ou não é um grant. Se não houver record, retorna false;
    private boolean isWithdrawn(ConsentRecord record) {
        return record != null
                && (record.getWithdrawnAt() != null || !Boolean.TRUE.equals(record.getGranted()));
    }

    //retorna o deadline da retention.
    private LocalDateTime retentionUntil(Object subject) {
        if (subject instanceof Patient patient) {
            return patient.getRetentionUntil();
        }
        if (subject instanceof Employee employee) {
            return employee.getRetentionUntil();
        }
        return null;
    }

    private void addAttributes(DecisionRequestBuilder<?> requestBuilder, String category, Map<String, String> attrs) {
        for (var entry : attrs.entrySet()) {
            AttributeFqn fqn = AttributeFqns.newInstance(category, Optional.empty(), entry.getKey());
            AttributeBag<?> bag = Bags.singletonAttributeBag(
                    StandardDatatypes.STRING,
                    new StringValue(entry.getValue()));
            requestBuilder.putNamedAttributeIfAbsent(fqn, bag);
        }
    }
}
