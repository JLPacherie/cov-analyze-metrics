package com.synopsys.metrics;

import com.synopsys.sipm.model.Parameter;
import com.synopsys.sipm.model.ParameterSet;

import java.util.stream.Stream;

/**
 * Base class for a measurable object having some metrics attached to. This is the base class
 * for a function, a class / a module, a component.
 */
public abstract class Measurable extends ParameterSet {

    public static String tagNAME = "name";
    public static String tagLOC = "loc";
    public static String tagCCM = "ccm";
    
    
    public Measurable(String name) {
    	add(tagNAME,name,Parameter.READ_WRITE);
    	add(tagLOC,"0.0",Parameter.READ_WRITE);
    	add(tagCCM,"0.0",Parameter.READ_WRITE);
    	
    }
    
    public String getName() {
        return get(tagNAME, "");
    }

    public void setName(String value) {
    	set(tagNAME,value);
    }
    
    
    public abstract Stream<String> getAllSources();

    public String getSourcesLabel() {
        StringBuffer result = new StringBuffer();
        getAllSources().forEach(src -> {
            if (result.length() == 0) {
                result.append(src);
            } else {
                result.append("," + src);
            }
        });
        return result.toString();
    }

    /**
     * Returns a metric's value.
     */
    public double getMetric(String metricsName) {
        return Double.parseDouble(get(metricsName,"0.0"));
    }

}
