package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {
    private static XACMLPDP pdp;

    @BeforeAll
    static void setUp() throws Exception {
        pdp = new XACMLPDP();
    }

    @Test
    void Marketing_permit_Granted_NotWithdrawn_1() throws Exception {
        assertTrue(decide("Patient", "marketing", Map.of(
                "consent-marketing", "true",
                "consent-withdrawn", "false")));
    }

    @Test
    void Marketing_denied_withdrawn_2() throws Exception {
        assertFalse(decide("Patient", "marketing", Map.of(
                "consent-marketing", "true",
                "consent-withdrawn", "true")));
    }

    @Test
    void Marketing_denied_NeverGranted_3() throws Exception {
        assertFalse(decide("Patient", "marketing", Map.of(
                "consent-marketing", "false",
                "consent-withdrawn", "false")));
    }

    @Test
    void Doctor_denied_ErasureWasRequested_4() throws Exception {
        assertFalse(decide("Doctor", "read", Map.of("erasure-requested", "true")));
    }

    @Test
    void DPO_access_5() throws Exception {
        assertTrue(decide("DPO", "read", Map.of("erasure-requested", "true")));
    }

    @Test
    void Doctor_retentionExpried_6() throws Exception {
        assertFalse(decide("Doctor", "read", Map.of("retention-expired", "true")));
    }

    @Test
    void Auditor_retentionExperied_7() throws Exception {
        assertTrue(decide("Auditor", "read", Map.of("retention-expired", "true")));
    }

    @Test
    void Healthdata_8() throws Exception {
        assertFalse(decide("Patient", "read", Map.of("consent-research", "false")));
        assertTrue(decide("Patient", "read", Map.of("consent-research", "true")));
    }

    //funcao chamada em todos os testes para decidir se o acesso é permitido ou não, com base no role, action e facts
    private boolean decide(String role, String action, Map<String, String> facts) throws Exception {
        Map<String, String> subjectAttrs = Map.of(
                "id", "42",
                "role", role);
        Map<String, String> resourceAttrs = new HashMap<>(Map.of(
                "id", "42",
                "resource-type", "PatientData",
                "data-category", "health-data",
                "lawful-basis", "contract",
                "consent-marketing", "false",
                "consent-withdrawn", "false",
                "consent-research", "true",
                "erasure-requested", "false",
                "retention-expired", "false"));
        resourceAttrs.putAll(facts);

        return pdp.evaluateAccessRequest(
                subjectAttrs,
                resourceAttrs,
                Map.of("action-id", action),
                Map.of("context", "Appointment"));
    }
}
