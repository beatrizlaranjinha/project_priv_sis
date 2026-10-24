package pt.unl.fct.di.syspriv.anonymity;

import java.util.Map;

public interface Anonymizable {
    Map<String, Object> getQuasiIdentifiers();

    Object getSensitiveValue();
}