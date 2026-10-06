package pt.unl.fct.di.syspriv.xacml;

import pt.unl.fct.di.syspriv.util.ConfigLoader;
import pt.unl.fct.di.syspriv.entities.*;

import org.ow2.authzforce.core.pdp.api.*;
import org.ow2.authzforce.core.pdp.api.value.*;
import org.ow2.authzforce.core.pdp.impl.BasePdpEngine;
import org.ow2.authzforce.core.pdp.impl.PdpEngineConfiguration;
import org.ow2.authzforce.xacml.identifiers.XacmlStatusCode;
import org.ow2.authzforce.core.pdp.api.value.Bags;
import org.ow2.authzforce.core.pdp.api.value.Datatype;
import org.ow2.authzforce.core.pdp.api.value.StandardDatatypes;
import org.ow2.authzforce.core.pdp.api.value.StringValue;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.DecisionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.logging.log4j.Level;


import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.apache.commons.io.IOUtils;

public class XACMLPDP {

    // Standard XACML category URNs
    private static final String SUBJECT_CATEGORY = "urn:oasis:names:tc:xacml:1.0:subject-category:access-subject";
    private static final String RESOURCE_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:resource";
    private static final String ACTION_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:action";
    private static final String ENV_CATEGORY = "urn:oasis:names:tc:xacml:3.0:attribute-category:environment";

    private final BasePdpEngine pdp;
    private Logger logger;

    public XACMLPDP() throws Exception {
        PdpEngineConfiguration config = PdpEngineConfiguration.getInstance(XACMLPDP.class.getClassLoader().getResource("pdp.xml").getPath());
        this.pdp = new BasePdpEngine(config);
        this.logger = LoggerFactory.getLogger(BasePdpEngine.class);
    }

    private String loadPolicyXml(String policyFile) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(policyFile);
        if (is != null) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        if (Files.exists(Paths.get(policyFile))) {
            return new String(Files.readAllBytes(Paths.get(policyFile)), StandardCharsets.UTF_8);
        }
        
        throw new IllegalStateException("Policy file not found: " + policyFile);
    }

    public boolean checkAccess(Object requester, Object resource, String context) throws Exception {
        Map<String, String> subjectAttrs = new HashMap<>();
        Map<String, String> resourceAttrs = new HashMap<>();
        Map<String, String> actionAttrs = new HashMap<>();
        Map<String, String> envAttrs = new HashMap<>();

        // TODO
        // Identificar quem está a pedir o acesso
        if (requester instanceof Patient) {
            Patient patient = (Patient) requester;

            subjectAttrs.put("id", patient.getStringId());
            subjectAttrs.put("role", "Patient");
        }

        else if (requester instanceof Employee) {
            Employee employee = (Employee) requester;

            subjectAttrs.put("id", employee.getStringId());
            subjectAttrs.put("role", employee.getRole());
        }

        // Se o recurso a que queremos aceder são dados de um Employee
        if (resource instanceof Employee) {

            Employee employee = (Employee) resource;

            // ID do employee cujos dados estão a ser acedidos
            resourceAttrs.put("id", employee.getStringId());

            // Tipo de recurso usado no ci_policy.xml
            resourceAttrs.put("resource-type", "EmployeeData");
        }

        else if (resource instanceof Patient) {

            Patient patient = (Patient) resource;

            // ID do paciente
            resourceAttrs.put("id", patient.getStringId());

            //  aceder a dados de paciente
            resourceAttrs.put("resource-type", "PatientData");

            //  ID do médico responsável pelo paciente
            if (patient.getPrimaryDoctor() != null) {
                resourceAttrs.put(
                        "doctor-id", //medico associoado ao paciente
                        patient.getPrimaryDoctor().getStringId()
                );
            }
        }
        else if (resource instanceof Appointment) {

            Appointment appointment = (Appointment) resource;

            // Tipo de recurso
            resourceAttrs.put("resource-type", "AppointmentData");

            // A appointment pertence a um Patient
            if (appointment.getPatient() != null) {

                // O "id" do recurso será o ID do paciente
                resourceAttrs.put(
                        "id",
                        appointment.getPatient().getStringId()
                );

                // Médico responsável por esse paciente
                if (appointment.getPatient().getPrimaryDoctor() != null) {
                    resourceAttrs.put(
                            "doctor-id",
                            appointment.getPatient()
                                    .getPrimaryDoctor()
                                    .getStringId()
                    );
                }
            }

            // Employee associado à appointment
            if (appointment.getEmployee() != null) {
                resourceAttrs.put(
                        "employee-id",
                        appointment.getEmployee().getStringId()
                );
            }
        }

        else if (resource instanceof Payment) {

            Payment payment = (Payment) resource;

            // Tipo de recurso
            resourceAttrs.put("resource-type", "PaymentData");

            // O pagamento pertence a um Patient
            if (payment.getPatient() != null) {
                resourceAttrs.put(
                        "id",
                        payment.getPatient().getStringId()
                );
            }
        }

        // Guarda o contexto do acesso para a policy XACML
        envAttrs.put("context", context);
        
        actionAttrs.put("action-id", "read");

        //temp
        System.out.println("SUBJECT = " + subjectAttrs);
        System.out.println("RESOURCE = " + resourceAttrs);
        System.out.println("ENVIRONMENT = " + envAttrs);
        System.out.println("ACTION = " + actionAttrs);

        DecisionRequestBuilder<?> requestBuilder = pdp.newRequestBuilder(-1, -1);

        addAttributes(requestBuilder, SUBJECT_CATEGORY, subjectAttrs);
        addAttributes(requestBuilder, RESOURCE_CATEGORY, resourceAttrs);
        addAttributes(requestBuilder, ACTION_CATEGORY, actionAttrs);
        addAttributes(requestBuilder, ENV_CATEGORY, envAttrs);

        var request = requestBuilder.build(true);
        logger.debug(request.toString());
        DecisionResult result = pdp.evaluate(request);

        //temp
        System.out.println("DECISION = " + result.getDecision());
        System.out.println("FULL RESULT = " + result);
        
        logger.debug(result.toString());
        return result.getDecision() == DecisionType.PERMIT;
    }

    private void addAttributes(DecisionRequestBuilder<?> requestBuilder, String category, Map<String, String> attrs) {
        for (var entry : attrs.entrySet()) {
            AttributeFqn fqn = AttributeFqns.newInstance(category, Optional.empty(), entry.getKey());
            AttributeBag<?> bag = Bags.singletonAttributeBag(
                StandardDatatypes.STRING,
                new StringValue(entry.getValue())
            );
            requestBuilder.putNamedAttributeIfAbsent(fqn, bag);
        }
    }
}
