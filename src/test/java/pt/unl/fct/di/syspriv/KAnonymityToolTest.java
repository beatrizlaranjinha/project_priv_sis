package pt.unl.fct.di.syspriv;

import org.junit.jupiter.api.Test;
import pt.unl.fct.di.syspriv.entities.AnonymousHealthData;
import pt.unl.fct.di.syspriv.entities.HealthData;
import pt.unl.fct.di.syspriv.entities.Patient;
import pt.unl.fct.di.syspriv.kanonymity.KAnonymityTool;
import pt.unl.fct.di.syspriv.storage.Hibernate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KAnonymityToolTest {
    private static final int K = 5;
    private static final float T = 0.2f;

    @Test
    void checkerAcceptsKAnonymousData() {
        List<AnonymousHealthData> records = new ArrayList<>();
        for (int i = 0; i < K; i++) {
            records.add(new AnonymousHealthData((long) i, 1980, 60614, 100 + i, new Date(0)));
        }
        assertTrue(new KAnonymityTool<AnonymousHealthData>(K, T).isKAnonymous(records));
    }

    @Test
    void mondrianMakesDatabaseKAnonymous() {
        KAnonymityTool<AnonymousHealthData> tool = new KAnonymityTool<>(K, T);
        List<AnonymousHealthData> anonymized = tool.runMondrain(loadHealthData());
        assertTrue(tool.isKAnonymous(anonymized));
    }

    @Test
    void tCloseMondrianMaintainsKAndT() {
        List<AnonymousHealthData> records = loadHealthData();
        KAnonymityTool<AnonymousHealthData> tool = new KAnonymityTool<>(K, T);
        List<AnonymousHealthData> anonymized = tool.runTCloseMondrain(records);
        assertTrue(tool.isKAnonymous(anonymized));
        assertTrue(tool.isTClose(anonymized, records));
    }

    private List<AnonymousHealthData> loadHealthData() {
        List<AnonymousHealthData> result = new ArrayList<>();
        for (HealthData health : Hibernate.getInstance().jpql("FROM HealthData", HealthData.class)) {
            Patient patient = Hibernate.getInstance().get(Patient.class, health.getPatientId());
            result.add(new AnonymousHealthData(health, patient));
        }
        return result;
    }
}
