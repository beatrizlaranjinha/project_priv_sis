package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gdpr_requests")
public class GDPRRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_subject_id")
    private Long employeeSubjectId; // Cam be NULL

    @Column(name = "patient_subject_id")
    private Long patientSubjectId; // Can be NULL

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType; // 'EMPLOYEE' or 'PATIENT'

    @Column(name = "request_type", nullable = false, length = 30)
    private String requestType; // 'ACCESS', 'ERASURE', 'PORTABILITY', 'RECTIFICATION'

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(length = 20)
    private String status = "PENDING";

    @Column(name = "handled_by")
    private Long handledBy;

    public GDPRRequest() {}

    public GDPRRequest(Long patientSubjectId, String subjectType, String requestType, LocalDateTime requestedAt, String status) {
        this.patientSubjectId = patientSubjectId;
        this.subjectType = subjectType;
        this.requestType = requestType;
        this.requestedAt = requestedAt;
        this.status = status;
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
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void status(String status) { this.status = status; }
    public void setStatus(String status) { this.status = status; }
    public Long getHandledBy() { return handledBy; }
    public void setHandledBy(Long handledBy) { this.handledBy = handledBy; }
}