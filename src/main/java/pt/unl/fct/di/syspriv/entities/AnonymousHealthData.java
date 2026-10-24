package pt.unl.fct.di.syspriv.entities;

import pt.unl.fct.di.syspriv.anonymity.Anonymizable;
import pt.unl.fct.di.syspriv.storage.Hibernate;

import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Contains only quasi-identifiers and the sensitive measurement, never patient identifiers. */
public class AnonymousHealthData implements Anonymizable {
    private static final Pattern ZIP_CODE = Pattern.compile("\\b(\\d{5})\\b");
    private final Map<String, Object> quasiIdentifiers = new LinkedHashMap<>();
    private float glucose;

    public AnonymousHealthData(HealthData healthData) {
        this(healthData, Hibernate.getInstance().get(Patient.class, healthData.getPatientId()));
    }

    public AnonymousHealthData(HealthData healthData, Patient patient) {
        if (patient == null || patient.getBirthdate() == null || healthData.getDiagnosisDate() == null) {
            throw new IllegalArgumentException("Health data requires a patient with birthdate and postal code");
        }
        // Unknown/previously anonymized addresses have no usable postcode.
        Matcher matcher = ZIP_CODE.matcher(patient.getAddress() == null ? "" : patient.getAddress());
        int postalCode = matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        quasiIdentifiers.put("birthYear", year(patient.getBirthdate()));
        quasiIdentifiers.put("postalCode", postalCode);
        quasiIdentifiers.put("diagnosisYear", year(healthData.getDiagnosisDate()));
        this.glucose = healthData.getGlucose();
    }

    // Constructor matching the provided template. The record ID is deliberately not exported.
    public AnonymousHealthData(Long id, float patientBirthYear, float patientPostalCode,
                               float glucose, Date diagnosisDate) {
        quasiIdentifiers.put("birthYear", (int) patientBirthYear);
        quasiIdentifiers.put("postalCode", (int) patientPostalCode);
        quasiIdentifiers.put("diagnosisYear", year(diagnosisDate));
        this.glucose = glucose;
    }

    private static int year(Date date) {
        return date.toInstant().atZone(ZoneOffset.UTC).getYear();
    }

    @Override
    public Map<String, Object> getQuasiIdentifiers() { return quasiIdentifiers; }

    @Override
    public Object getSensitiveValue() { return glucose; }

    public Object getPatientBirthYear() { return quasiIdentifiers.get("birthYear"); }
    public Object getPatientPostalCode() { return quasiIdentifiers.get("postalCode"); }
    public Object getDiagnosisDate() { return quasiIdentifiers.get("diagnosisYear"); }
    public float getGlucose() { return glucose; }
    public void setGlucose(float glucose) { this.glucose = glucose; }

    @Override
    public String toString() {
        return "AnonymousHealthData{quasiIdentifiers=" + quasiIdentifiers + ", glucose=" + glucose + "}";
    }
}
