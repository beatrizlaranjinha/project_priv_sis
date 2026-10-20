package pt.unl.fct.di.syspriv;

import com.fasterxml.jackson.databind.ObjectMapper;
import pt.unl.fct.di.syspriv.entities.*;
import pt.unl.fct.di.syspriv.storage.Hibernate;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

public class DataSafe {

    private static final Logger logger = LoggerFactory.getLogger(DataSafe.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Hibernate hibernate = Hibernate.getInstance();
    private final XACMLPDP pdp;
    private Object loggedInPerson;

    public DataSafe() throws Exception {
        this.pdp = new XACMLPDP();
        logger.info("DataSafe instance initialized.");
    }

    public static void main(String[] args) {
        try {
            DataSafe app = new DataSafe();
            app.run();

        } catch (Exception e) {
            logger.error("Failed to initialize application: " + e.getMessage());
            e.printStackTrace();
        }
    }



    public void run() {
        Scanner scanner = new Scanner(System.in);

        if (!login(scanner)) {
            logger.error("Login failed. Exiting...");
            return;
        }

        displayWelcome();

        boolean running = true;
        while (running) {
            switch (promptGDPROptions(scanner)) {
                case 1 -> requestErasure();
                case 2 -> exportMyData(scanner);
                case 3 -> accessRecord(scanner);
                case 5 -> {
                    running = false;
                    System.out.println("Exiting...");
                }
                default -> System.out.println("Invalid option. Please try again.");
            }
        }

        scanner.close();
    }

    private boolean login(Scanner scanner) {
        System.out.println("Are you an employee or patient?");
        System.out.println("1. Employee");
        System.out.println("2. Patient");
        System.out.print("Select an option: ");

        String choice = scanner.nextLine();

        System.out.print("Enter your name to login: ");
        String loginName = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                List<Employee> employees = hibernate.jpql("FROM Employee", Employee.class);
                for (Employee e : employees) {
                    if (e.getName().equalsIgnoreCase(loginName)) {
                        loggedInPerson = e;
                        return true;
                    }
                }
                System.out.println("Employee not found.");
                return false;

            case "2":
                List<Patient> patients = hibernate.jpql(
                        "FROM Patient p LEFT JOIN FETCH p.primaryDoctor", Patient.class);
                for (Patient p : patients) {
                    if (p.getName().equalsIgnoreCase(loginName)) {
                        loggedInPerson = p;
                        return true;
                    }
                }
                System.out.println("Patient not found.");
                return false;

            default:
                System.out.println("Invalid option.");
                return false;
        }
    }

    private void displayWelcome() {
        if (loggedInPerson instanceof Employee emp) {
            System.out.println("Welcome, " + emp.getName() + " (" + emp.getRole() + ")!");
        } else if (loggedInPerson instanceof Patient pat) {
            System.out.println("Welcome, " + pat.getName() + "!");
        } else {
            System.out.println("Welcome!");
        }
    }

    private String promptContext(Scanner scanner) {
        System.out.println("\n=== Please select a context ===");
        System.out.println("1. Billing and Payments");
        System.out.println("2. Scheduling");
        System.out.println("3. Check-in");
        System.out.println("4. Hiring");
        System.out.println("5. Appointment");
        System.out.println("6. Emergency");
        System.out.print("Select an option: ");

        String choice = scanner.nextLine();
        return switch (choice) {
            case "1" -> "BillingAndPayment"; //como esta no ci_policy
            case "2" -> "Scheduling";
            case "3" -> "Check-in";
            case "4" -> "Hiring";
            case "5" -> "Appointment";
            case "6" -> "Emergency";
            default -> null;
        };
    }

    //Após o login, dá acesso às opções de GDPR, como pedido de apagamento, exportação de dados e acesso a um registo específico.
    private int promptGDPROptions(Scanner scanner) {
        System.out.println("\n=== Available GDPR operations ===");
        System.out.println("1. Request Erasure");
        System.out.println("2. Export my data");
        System.out.println("3. Access a record");
        System.out.println("5. Exit");
        System.out.print("Select an option: ");

        String input = scanner.nextLine();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int promptTableSelection(Scanner scanner) {
        System.out.println("\n=== Available tables to request data from ===");
        System.out.println("1. Employees");
        System.out.println("2. Patients");
        System.out.println("3. Appointments");
        System.out.println("4. Payments");
        System.out.println("5. Exit");
        System.out.print("Select an option: ");

        String input = scanner.nextLine();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Long promptId(Scanner scanner) {
        System.out.print("Please enter the ID of the row you'd like to access: ");
        String input = scanner.nextLine();
        try {
            return Long.valueOf(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    //Para inserir qual das 6 lawful basis queres.
    private String promptLawfulBasis(Scanner scanner) {
        System.out.println("Enter the lawful basis for this read:");
        System.out.println("consent, contract, legal-obligation, vital-interests, public-task, legitimate-interests");
        System.out.print("Lawful basis: ");
        return scanner.nextLine().trim();
    }

    //Para aceder a um record.
    private void accessRecord(Scanner scanner) {
        String context = promptContext(scanner);
        if (context == null) {
            System.out.println("Invalid context. Please try again.");
            return;
        }

        int tableChoice = promptTableSelection(scanner);
        if (tableChoice == 5) {
            return;
        }

        Long id = promptId(scanner);
        if (id == null) {
            System.out.println("Invalid ID. Please try again.");
            return;
        }
        handleDataAccess(context, tableChoice, id, promptLawfulBasis(scanner));
    }

    //funcao para exportar a data, onde o user escolhe o Lawfulbasis;
    private void exportMyData(Scanner scanner) {
        String lawfulBasis = promptLawfulBasis(scanner);
        GDPRRequest accessRequest = new GDPRRequest();
        accessRequest.setSubjectType(loggedInPerson instanceof Patient ? "PATIENT" : "EMPLOYEE");
        if (loggedInPerson instanceof Patient patient) {
            accessRequest.setPatientSubjectId(patient.getId());
        } else if (loggedInPerson instanceof Employee employee) {
            accessRequest.setEmployeeSubjectId(employee.getId());
        }
        accessRequest.setRequestType("ACCESS");
        accessRequest.setRequestedAt(LocalDateTime.now());
        accessRequest.setStatus("PENDING");

        try {
            hibernate.persist(accessRequest);

            Map<String, Object> export = new LinkedHashMap<>();
            export.put("subject", subjectSummary(loggedInPerson));
            //duas opcoes: Patient or Employee, e através do hibernate, faz-se o fetch dos dados relacionados com o subject, e depois exporta-se para JSON.
            if (loggedInPerson instanceof Patient patient) {
                export.put("patient", exportRecords(List.of(patient), "Appointment", lawfulBasis));
                export.put("appointments", exportRecords(
                        hibernate.jpql("FROM Appointment a LEFT JOIN FETCH a.patient p LEFT JOIN FETCH p.primaryDoctor "
                                + "LEFT JOIN FETCH a.employee WHERE a.patient.id = " + patient.getId(), Appointment.class),
                        "Appointment", lawfulBasis));
                export.put("payments", exportRecords(
                        hibernate.jpql("FROM Payment p LEFT JOIN FETCH p.patient LEFT JOIN FETCH p.appointment "
                                + "WHERE p.patient.id = " + patient.getId(), Payment.class),
                        "BillingAndPayment", lawfulBasis));
                export.put("consentRecords", exportRecords(
                        hibernate.jpql("FROM ConsentRecord WHERE patientSubjectId = " + patient.getId(), ConsentRecord.class),
                        "Appointment", lawfulBasis));
                export.put("gdprRequests", exportRecords(
                        hibernate.jpql("FROM GDPRRequest WHERE patientSubjectId = " + patient.getId(), GDPRRequest.class),
                        "Appointment", lawfulBasis));
            } else if (loggedInPerson instanceof Employee employee) {
                export.put("employee", exportRecords(List.of(employee), "Hiring", lawfulBasis));
                export.put("appointments", exportRecords(
                        hibernate.jpql("FROM Appointment a LEFT JOIN FETCH a.patient p LEFT JOIN FETCH p.primaryDoctor "
                                + "LEFT JOIN FETCH a.employee WHERE a.employee.id = " + employee.getId(), Appointment.class),
                        "Scheduling", lawfulBasis));
                export.put("payments", exportRecords(
                        hibernate.jpql("FROM Payment p LEFT JOIN FETCH p.patient "
                                + "LEFT JOIN FETCH p.appointment "
                                + "WHERE p.appointment.employee.id = " + employee.getId(), Payment.class),
                        "BillingAndPayment", lawfulBasis));
                export.put("consentRecords", exportRecords(
                        hibernate.jpql("FROM ConsentRecord WHERE employeeSubjectId = " + employee.getId(), ConsentRecord.class),
                        "Hiring", lawfulBasis));
                export.put("gdprRequests", exportRecords(
                        hibernate.jpql("FROM GDPRRequest WHERE employeeSubjectId = " + employee.getId(), GDPRRequest.class),
                        "Hiring", lawfulBasis));
            } else {
                throw new IllegalStateException("Cannot export data for an unsupported subject type.");
            }

            String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(export);
            System.out.println(json);
            accessRequest.setCompletedAt(LocalDateTime.now());
            accessRequest.setStatus("COMPLETED");
            hibernate.update(accessRequest);
        } catch (Exception e) {
            logger.error("Failed to export subject data.", e);
            if (accessRequest.getId() != null) {
                try {
                    accessRequest.setStatus("FAILED");
                    accessRequest.setCompletedAt(LocalDateTime.now());
                    hibernate.update(accessRequest);
                } catch (Exception logError) {
                    logger.error("Failed to update the GDPR access request.", logError);
                }
            }
            System.out.println("Data export failed. See the application log for details.");
        }
    }

    //funcao que exporta os records, verificando se o loggedInPerson tem acesso a cada record, e se não tiver, coloca um redacted true.
    private List<Map<String, Object>> exportRecords(List<?> records, String context, String lawfulBasis)
            throws Exception {
        List<Map<String, Object>> exported = new ArrayList<>();
        for (Object record : records) {
            Long id = recordId(record);
            if (pdp.checkAccessGDPR(loggedInPerson, record, context, lawfulBasis)) {
                exported.add(recordData(record));
            } else {
                Map<String, Object> redacted = new LinkedHashMap<>();
                redacted.put("id", id);
                redacted.put("redacted", true);
                exported.add(redacted);
            }
        }
        return exported;
    }

    //funcao que cria um resumo do subject, com o tipo (PATIENT ou EMPLOYEE) e o id.
    private Map<String, Object> subjectSummary(Object subject) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", subject instanceof Patient ? "PATIENT" : "EMPLOYEE");
        summary.put("id", subjectId(subject));
        return summary;
    }

    //funcao que cria um hashmap com os dados do record.
    private Map<String, Object> recordData(Object record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", recordId(record));
        if (record instanceof Patient patient) {
            data.put("name", patient.getName());
            data.put("birthdate", dateString(patient.getBirthdate()));
            data.put("insurance", patient.getInsurance());
            data.put("address", patient.getAddress());
            data.put("primaryDoctorId", patient.getPrimaryDoctor() == null ? null : patient.getPrimaryDoctor().getId());
            data.put("createdAt", stringValue(patient.getCreatedAt()));
            data.put("updatedAt", stringValue(patient.getUpdatedAt()));
            data.put("retentionUntil", stringValue(patient.getRetentionUntil()));
            data.put("erasureRequested", patient.isErasureRequested());
            data.put("erasureCompletedAt", stringValue(patient.getErasureCompletedAt()));
        } else if (record instanceof Employee employee) {
            data.put("name", employee.getName());
            data.put("role", employee.getRole());
            data.put("department", employee.getDepartment());
            data.put("isAdmin", employee.getIsAdmin());
            data.put("createdAt", stringValue(employee.getCreatedAt()));
            data.put("updatedAt", stringValue(employee.getUpdatedAt()));
            data.put("retentionUntil", stringValue(employee.getRetentionUntil()));
        } else if (record instanceof Appointment appointment) {
            data.put("dateTime", stringValue(appointment.getDateTime()));
            data.put("employeeId", appointment.getEmployee() == null ? null : appointment.getEmployee().getId());
            data.put("patientId", appointment.getPatient() == null ? null : appointment.getPatient().getId());
        } else if (record instanceof Payment payment) {
            data.put("dateTime", stringValue(payment.getDateTime()));
            data.put("patientId", payment.getPatient() == null ? null : payment.getPatient().getId());
            data.put("value", payment.getValue());
            data.put("appointmentId", payment.getAppointment() == null ? null : payment.getAppointment().getId());
            data.put("cardNumber", payment.getCardNumber());
            data.put("anonymized", payment.isAnonymized());
        } else if (record instanceof ConsentRecord consent) {
            data.put("patientSubjectId", consent.getPatientSubjectId());
            data.put("employeeSubjectId", consent.getEmployeeSubjectId());
            data.put("subjectType", consent.getSubjectType());
            data.put("purpose", consent.getPurpose());
            data.put("granted", consent.getGranted());
            data.put("grantedAt", stringValue(consent.getGrantedAt()));
            data.put("withdrawnAt", stringValue(consent.getWithdrawnAt()));
            data.put("policyVersion", consent.getPolicyVersion());
            data.put("source", consent.getSource());
        } else if (record instanceof GDPRRequest request) {
            data.put("patientSubjectId", request.getPatientSubjectId());
            data.put("employeeSubjectId", request.getEmployeeSubjectId());
            data.put("subjectType", request.getSubjectType());
            data.put("requestType", request.getRequestType());
            data.put("requestedAt", stringValue(request.getRequestedAt()));
            data.put("completedAt", stringValue(request.getCompletedAt()));
            data.put("status", request.getStatus());
            data.put("handledBy", request.getHandledBy());
        }
        return data;
    }

    private Long recordId(Object record) {
        if (record instanceof Patient patient) return patient.getId();
        if (record instanceof Employee employee) return employee.getId();
        if (record instanceof Appointment appointment) return appointment.getId();
        if (record instanceof Payment payment) return payment.getId();
        if (record instanceof ConsentRecord consent) return consent.getId();
        if (record instanceof GDPRRequest request) return request.getId();
        throw new IllegalArgumentException("Unsupported export record type: " + record.getClass().getName());
    }

    private Long subjectId(Object subject) {
        if (subject instanceof Patient patient) return patient.getId();
        if (subject instanceof Employee employee) return employee.getId();
        throw new IllegalArgumentException("Unsupported subject type: " + subject.getClass().getName());
    }

    private String dateString(Date date) {
        return date == null ? null : date.toInstant().toString();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    //funcao que dá handle ao requestErasure
    private void requestErasure() {
        try {
            ErasureResult result = hibernate.inTransaction(session -> {
                Object managedSubject;
                String token = "ANON_" + UUID.randomUUID(); //gera um token aleatório para substituir os dados do subject.
                GDPRRequest request = new GDPRRequest();
                request.setSubjectType(loggedInPerson instanceof Patient ? "PATIENT" : "EMPLOYEE");
                request.setRequestType("ERASURE");
                request.setRequestedAt(LocalDateTime.now());
                request.setStatus("PENDING");

                if (loggedInPerson instanceof Patient patient) {
                    managedSubject = session.find(Patient.class, patient.getId());
                    if (managedSubject == null) {
                        throw new IllegalStateException("The logged-in patient no longer exists.");
                    }
                    Patient managedPatient = (Patient) managedSubject;
                    request.setPatientSubjectId(managedPatient.getId());
                    managedPatient.setErasureRequested(true);
                    managedPatient.setName(token); //aqui são colocados então os tokens aleatórios no lugar do nome e endereço do paciente, para anonimizar os dados.
                    managedPatient.setAddress(token);

                    List<Payment> payments = session.createQuery(
                                    "FROM Payment p WHERE p.patient.id = :patientId", Payment.class)
                            .setParameter("patientId", managedPatient.getId())
                            .getResultList();
                    for (Payment payment : payments) {
                        payment.setCardNumber("ANON_" + UUID.randomUUID());
                        payment.setAnonymized(true);
                    }
                    managedPatient.setErasureCompletedAt(LocalDateTime.now());
                } else if (loggedInPerson instanceof Employee employee) {
                    managedSubject = session.find(Employee.class, employee.getId());
                    if (managedSubject == null) {
                        throw new IllegalStateException("The logged-in employee no longer exists.");
                    }
                    Employee managedEmployee = (Employee) managedSubject;
                    request.setEmployeeSubjectId(managedEmployee.getId());
                    managedEmployee.setName(token);
                } else {
                    throw new IllegalStateException("Cannot erase an unsupported subject type.");
                }

                request.setCompletedAt(LocalDateTime.now());
                request.setStatus("COMPLETED");
                session.persist(request);
                return new ErasureResult(token, request); //retorna o token e o request para ser usado fora da transação.
            });

            if (loggedInPerson instanceof Patient patient) {
                patient.setName(result.token());
                patient.setAddress(result.token());
                patient.setErasureRequested(true);
                patient.setErasureCompletedAt(result.request().getCompletedAt());
            } else if (loggedInPerson instanceof Employee employee) {
                employee.setName(result.token());
            }

            System.out.println("Erasure request completed. Request ID: " + result.request().getId());
            System.out.println("Your anonymized login name is: " + result.token());
        } catch (Exception e) {
            logger.error("Failed to process erasure request.", e);
            System.out.println("Erasure request failed. No changes were committed.");
        }
    }

    private record ErasureResult(String token, GDPRRequest request) {}

    private void handleDataAccess(String context, int tableChoice, Long id, String lawfulBasis) {
        Class<?> entityClass;
        String entityName;

        switch (tableChoice) {
            case 1:
                entityClass = Employee.class;
                entityName = "Employee";
                break;
            case 2:
                entityClass = Patient.class;
                entityName = "Patient";
                break;
            case 3:
                entityClass = Appointment.class;
                entityName = "Appointment";
                break;
            case 4:
                entityClass = Payment.class;
                entityName = "Payment";
                break;
            default:
                System.out.println("Invalid table selection.");
                return;
        }

        try {
            // Fetch the entity from the database
            logger.info("Fetching from" + entityName + "ID " + Long.toString(id));
            Object result = hibernate.get(entityClass, id);

            if (result == null) {
                logger.error("No " + entityName + " found with ID: " + id);
                return;
            }

            // Check access via XACML PDP
            boolean allowed = pdp.checkAccessGDPR(loggedInPerson, result, context, lawfulBasis);

            if (allowed) {
                System.out.println("Access Granted!");
            } else {
                System.out.println("Access Denied: You do not have permission to view "
                        + entityName + " data.");
            }

        } catch (Exception e) {
            logger.error("Error accessing data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

