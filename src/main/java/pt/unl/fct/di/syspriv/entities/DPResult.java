package pt.unl.fct.di.syspriv.entities;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class DPResult {
    
    public enum ResultType {
        COUNT("COUNT"),
        SUM("SUM"),
        AVERAGE("AVERAGE"),
        HISTOGRAM("HISTOGRAM");

        private final String code;

        ResultType(String code) { this.code = code; }

        public String getCode() { return code; }
    }

    public enum ResultCode {
        SUCCESS(0),
        FAIL(-1);

        private final int code;

        ResultCode(int code) { this.code = code; }
        public int getCode() { return code; }
    };

    private ResultType resultType;
    private ResultCode resultCode;

    private float resultValue;
    private float querySensitivity;
    private Float countSensitivity;
    private double privacyBudget;
    private Map<String, Float> histogramResultValue;
    private Float clippingUpper;
    private Float clippingLower;

    public DPResult() {}

    public DPResult(float result, float sensitivity, float clippingLower, float clippingUpper) {
        this.resultValue = result;
        this.querySensitivity = sensitivity;
        this.clippingLower = clippingLower;
        this.clippingUpper = clippingUpper;
        this.resultCode = ResultCode.SUCCESS;
    }

    // Getters and Setters
    /**
     * Get resultType.
     *
     * @return resultType as ResultType.
     */
    public ResultType getResultType()
    {
        return resultType;
    }
    
    /**
     * Set resultType.
     *
     * @param resultType the value to set.
     */
    public void setResultType(ResultType resultType)
    {
        this.resultType = resultType;
    }
    
    /**
     * Get resultCode.
     *
     * @return resultCode as ResultCode.
     */
    public ResultCode getResultCode()
    {
        return resultCode;
    }
    
    /**
     * Set resultCode.
     *
     * @param resultCode the value to set.
     */
    public void setResultCode(ResultCode resultCode)
    {
        this.resultCode = resultCode;
    }
    
    /**
     * Get resultValue.
     *
     * @return resultValue as float.
     */
    public float getResultValue()
    {
        return resultValue;
    }
    
    /**
     * Set resultValue.
     *
     * @param resultValue the value to set.
     */
    public void setResultValue(float resultValue)
    {
        this.resultValue = resultValue;
    }
    
    /**
     * Get querySensitivity.
     *
     * @return querySensitivity as float.
     */
    public float getQuerySensitivity()
    {
        return querySensitivity;
    }
    
    /**
     * Set querySensitivity.
     *
     * @param querySensitivity the value to set.
     */
    public void setQuerySensitivity(float querySensitivity)
    {
        this.querySensitivity = querySensitivity;
    }

    public Float getCountSensitivity()
    {
        return countSensitivity;
    }

    public void setCountSensitivity(Float countSensitivity)
    {
        this.countSensitivity = countSensitivity;
    }
    
    /**
     * Get privacyBudget.
     *
     * @return privacyBudget as double.
     */
    public double getPrivacyBudget()
    {
        return privacyBudget;
    }
    
    /**
     * Set privacyBudget.
     *
     * @param privacyBudget the value to set.
     */
    public void setPrivacyBudget(double privacyBudget)
    {
        this.privacyBudget = privacyBudget;
    }

    public String getResultString()
    {
        if (resultCode == ResultCode.FAIL) return "-1";
        return Float.toString(resultValue);
    }

    public void setHistogramResultValue(Map<String, Float> histogramResultValue)
    {
        this.histogramResultValue = histogramResultValue == null
                ? null
                : new LinkedHashMap<>(histogramResultValue);
    }

    public Map<String, Float> getHistogramResultValue()
    {
        return histogramResultValue;
    }

    public String getHistogramResultString()
    {
        if (resultCode == ResultCode.FAIL) {
            return "-1";
        }
        if (histogramResultValue == null || histogramResultValue.isEmpty()) {
            return "{}";
        }
        return histogramResultValue.entrySet().stream()
                .map(entry -> entry.getKey() + ", Count: " + entry.getValue())
                .collect(Collectors.joining(System.lineSeparator()));
    }
    
    /**
     * Get clippingUpper.
     *
     * @return clippingUpper as Float.
     */
    public Float getClippingUpper()
    {
        return clippingUpper;
    }
    
    /**
     * Set clippingUpper.
     *
     * @param clippingUpper the value to set.
     */
    public void setClippingUpper(Float clippingUpper)
    {
        this.clippingUpper = clippingUpper;
    }
    
    /**
     * Get clippingLower.
     *
     * @return clippingLower as Float.
     */
    public Float getClippingLower()
    {
        return clippingLower;
    }
    
    /**
     * Set clippingLower.
     *
     * @param clippingLower the value to set.
     */
    public void setClippingLower(Float clippingLower)
    {
        this.clippingLower = clippingLower;
    }
 
    
    // For debugging and logging purposes.
    @Override
    public String toString() {
        return "DPResult{" +
                "type=" + (resultType == null ? "NULL" : resultType.getCode()) +
                ", code='" + (resultCode == null ? "NULL" : resultCode.getCode()) +
                ", privacyBudget='" + Double.toString(privacyBudget) + '\'' +
                ", sensitivity='" + Float.toString(querySensitivity) +'\'' +
                ", countSensitivity='" + (countSensitivity == null ? "NULL" : countSensitivity) + '\'' +
                ", result=" +  (resultType != ResultType.HISTOGRAM ? getResultString() : getHistogramResultString()) +
                ", clippingUpper=" + (clippingUpper != null ? getClippingUpper().toString() : "NULL") +
                ", clippingLower=" + (clippingLower != null ? getClippingLower().toString() : "NULL") +
                '}';
    }    
}
