package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dateTime", nullable = false)
    private LocalDate dateTime;
    
    @JoinColumn
    @ManyToOne
    private Patient patient;

    @Column(nullable = false, length = 100)
    private float value;

    @JoinColumn(name="appointment_id")
    @OneToOne
    private Appointment appointment;

    @Column(length = 20)
    private String cardNumber;

    public Payment() {}

    public Payment(LocalDate dateTime, Patient patient, float value, Appointment appointment,
                    String cardNumber) {
        this.dateTime = dateTime;
        this.patient = patient;
        this.value = value;
        this.appointment = appointment;
        this.cardNumber = cardNumber;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getStringId() { return Long.toString(id); }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDateTime() { return dateTime; }
    public void setDateTime(LocalDate dateTime) { this.dateTime = dateTime; }
    public float getValue() { return value; }
    public void setValue(float value) { this.value = value; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    // For debugging and logging purposes.
    @Override
    public String toString() {
        return "Payment{" +
                "id=" + Long.toString(id) +
                ", dateTime='" + dateTime.toString() + '\'' +
                ", patient=" + patient.toString() +
                ", value='" + Float.toString(value) + '\'' +
                ", appointment='" + appointment.toString() + '\'' +
                ", cardNumber='" + cardNumber + '\'' +
                '}';
    }
}
