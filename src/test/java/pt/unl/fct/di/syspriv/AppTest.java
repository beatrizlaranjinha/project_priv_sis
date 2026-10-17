package pt.unl.fct.di.syspriv;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.unl.fct.di.syspriv.entities.Employee;
import pt.unl.fct.di.syspriv.entities.Patient;
import pt.unl.fct.di.syspriv.xacml.XACMLPDP;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
/**
 * Unit test for simple App.
 */
public class AppTest {

    /**
     * Rigorous Test :-)
     */

    private static XACMLPDP pdp;

    @BeforeAll
    public static void setUp() throws Exception {
        pdp = new XACMLPDP();
    }

    @Test
    public void shouldAnswerWithTrue() {
        assertTrue(true);
    }
}
