
package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "health_data")
public class HealthData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patientId")
    private Long patientId;

    @Column(name = "glucose")
    private int glucose;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "diagnosisDate")
    private Date diagnosisDate;

    public HealthData() {}

    public HealthData(Long patientId, int glucose, Date diagnosisDate) {
        this.patientId = patientId;
        this.glucose = glucose;
        this.diagnosisDate = diagnosisDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public int getGlucose() { return glucose; }
    public void setGlucose(int glucose) { this.glucose = glucose; }

    public Date getDiagnosisDate() { return diagnosisDate; }
    public void setDiagnosisDate(Date diagnosisDate) { this.diagnosisDate = diagnosisDate; }
}
