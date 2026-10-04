package pt.unl.fct.di.syspriv.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "birthdate", nullable = false)
    private Date birthdate;

    @Column(length = 50)
    private String insurance;

    @Column(length = 200)
    private String address;

    @ManyToOne
    @JoinColumn(name = "primaryDoctor_id")
    private Employee primaryDoctor;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Appointment> appointments = new ArrayList<>();

    public Patient() {}

    public Patient(String name, Date birthdate, String insurance, String address, Employee primaryDoctorId) {
        this.name = name;
        this.birthdate = birthdate;
        this.insurance = insurance;
        this.address = address;
        this.primaryDoctor = primaryDoctor;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getStringId() { return Long.toString(id); }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Date getBirthdate() { return birthdate; }
    public void setBirthdate(Date birthdate) { this.birthdate = birthdate; }
    public String getInsurance() { return insurance; }
    public void setInsurance(String insurance) { this.insurance = insurance; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Employee getPrimaryDoctor() { return primaryDoctor; }
    public void setPrimaryDoctor(Employee primaryDoctor) { this.primaryDoctor = primaryDoctor; }
    public List<Appointment> getAppointments() { return appointments; }
    public void setAppointments(List<Appointment> appointments) { this.appointments = appointments; }

    // For debugging and logging purposes.
    @Override
    public String toString() {
        return "Patient{" +
                "id=" + Long.toString(id) +
                ", name='" + name + '\'' +
                ", birthdate=" + birthdate +
                ", insurance='" + insurance + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}
