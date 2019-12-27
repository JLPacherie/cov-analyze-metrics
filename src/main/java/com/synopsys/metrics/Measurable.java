package com.synopsys.metrics;

import com.synopsys.sipm.model.Parameter;
import com.synopsys.sipm.model.ParameterSet;

import java.util.stream.Stream;

/**
 * Base class for a measurable object having some metrics attached to. This is the base class for a function, a class /
 * a module, a component.
 */
public abstract class Measurable extends ParameterSet {

	public static final String tagNAME = "name";

	public static final String tagLOC = "loc";
	public static final String tagCCM = "ccm";

	public static final String tagMETRICS_PREFIX = "metrics.";

	public Measurable(String name) {
		add(tagNAME, name, Parameter.READ_WRITE);
		addMetrics(tagLOC, 0.0);
		addMetrics(tagCCM, 0.0);
	}

	public String getName() {
		return get(tagNAME, "");
	}

	public void setName(String value) {
		set(tagNAME, value);
	}

	public abstract Stream<String> getAllSources();

	public String getSourcesLabel() {
		StringBuilder result = new StringBuilder();
		getAllSources().forEach(src -> {
			if (result.length() == 0) {
				result.append(src);
			} else {
				result.append("," + src);
			}
		});
		return result.toString();
	}

	public boolean isMetrics(String metricName) {
		return hasName(tagMETRICS_PREFIX + metricName);
	}

	/**
	 * Returns a metric's value.
	 */
	public double getMetric(String metricsName) {
		double result = 0.0;
		if (isMetrics(metricsName)) {
			String metricValue = get(metricsName, "0.0");
			try {
				result = Double.parseDouble(metricValue);
			} catch (NumberFormatException e) {
				_logger.error("For metric named {}, unable to convert value {} as a double", metricsName, metricValue);
				result = 0.0;
			}
		} else {
			_logger.warn("Requesting metrics with unknown name '{}' on '{}'", metricsName,this);
		}
		return result;
	}

	public void setMetrics(String name, double value) {
		set(tagMETRICS_PREFIX + name, Double.toString(value));
	}

	public void addMetrics(String name, double value) {
		add(tagMETRICS_PREFIX + name, Double.toString(value), Parameter.READ_WRITE);
	}
	
	@Override
	public String toString( ) {
		return getName() + super.toString();
	}
}
