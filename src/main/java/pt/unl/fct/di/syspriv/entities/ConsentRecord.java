package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "consent_records")
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_subject_id")
    private Long employeeSubjectId;

    @Column(name = "patient_subject_id")
    private Long patientSubjectId;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType; // 'EMPLOYEE' or 'PATIENT'

    @Column(nullable = false, length = 50)
    private String purpose; // ex: 'marketing', 'research'

    @Column(nullable = false)
    private Boolean granted;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "policy_version", length = 20)
    private String policyVersion;

    @Column(length = 50)
    private String source; // ex: 'web', 'paper', 'phone'

    public ConsentRecord() {}

    public ConsentRecord(Long patientSubjectId, String subjectType, String purpose, Boolean granted, LocalDateTime grantedAt, String policyVersion, String source) {
        this.patientSubjectId = patientSubjectId;
        this.subjectType = subjectType;
        this.purpose = purpose;
        this.granted = granted;
        this.grantedAt = grantedAt;
        this.policyVersion = policyVersion;
        this.source = source;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEmployeeSubjectId() { return employeeSubjectId; }
    public void setEmployeeSubjectId(Long employeeSubjectId) { this.employeeSubjectId = employeeSubjectId; }
    public Long getPatientSubjectId() { return patientSubjectId; }
    public void setPatientSubjectId(Long patientSubjectId) { this.patientSubjectId = patientSubjectId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Boolean getGranted() { return granted; }
    public void setGranted(Boolean granted) { this.granted = granted; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(LocalDateTime withdrawnAt) { this.withdrawnAt = withdrawnAt; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}