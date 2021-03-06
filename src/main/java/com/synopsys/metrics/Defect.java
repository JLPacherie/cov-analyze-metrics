package com.synopsys.metrics;

import com.synopsys.sipm.model.Parameter;
import com.synopsys.sipm.model.ParameterSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.Stream;

/**
 * A Defect is generated by a Checker and refers to a Measurable Object (A function, a File, a Module...)
 *
 * @author pacherie
 */
public class Defect {

	protected Logger logger = LogManager.getLogger(Defect.class);

	protected Checker checker;
	protected Measurable measured;
	protected List<String> violations;

	public Defect(Checker checker, Measurable metrics) {
		this.checker = checker;
		this.measured = metrics;
	}

	public void setViolations(List<String> list) {
		this.violations = list;
	}

	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append(checker.toString());
		result.append(" triggered on ");
		result.append(measured.getName());
		result.append(" in ");
		result.append(measured.getSourcesLabel());
		result.append(" ( ");

		for (String m : violations) {
			result.append(m);
			result.append(" is ");
			result.append(measured.getMetric(m));
			result.append(" > ");
			result.append(checker.getThreshold(m));
		}
		result.append(" )");
		return result.toString();
	}

	public String Double2String(double d) {
		long l = (long) d;
		if (((double) l) == d) {
			return Long.toString(l);
		}
		return Double.toString(d);
	}

	/** Generates the JSON segment modeling this defect for the cov-import-result file. */
	public String getJson() {

		String result = checker.getJsonDefectTemplate();
		String eventTemplate = checker.getJsonDefectEventTemplate();

		try {
			//
			// Replace all references to "${key}" of a defined parameter with name key
			// by its value in the measured object.
			//
			result = measured.process(result);

			//
			// Look for each metrics referenced by the Checker triggering that defect
			// in the Measurable object and replace them by their value and/or threshold
			//
			ParameterSet set = new ParameterSet();
			// To replace occurrences of ${cc.threshold}, cc is the id of the metrics in Coverity data file
			checker.metrics().forEach(
					metrics -> set.add(metrics.name + ".threshold", Double.toString(metrics.value), Parameter.READ_WRITE));
			// To replace occurrences of ${ccm.threshold}, ccm is the user friendly name of the metric
			checker.metrics().forEach(
					metrics -> set.add(metrics.metric + ".threshold", Double.toString(metrics.value), Parameter.READ_WRITE));

			// To replace occurrences of ${cc} with value of the CCM in the measured object that triggers the defect
			checker.metrics().forEach(
					metrics -> set.add(metrics.name, Double.toString(measured.getMetric(metrics.metric)), Parameter.READ_WRITE));

			// To replace occurrences of ${ccm} with value of the CCM in the measured object that triggers the defect
			checker.metrics().forEach(//
					metrics -> set.add(metrics.metric, Double.toString(measured.getMetric(metrics.metric)),
							Parameter.READ_WRITE));

			// Perform substitutions
			result = set.process(result);

			if ((eventTemplate != null) && (!eventTemplate.isEmpty())) {
				StringBuilder eventBuilder = new StringBuilder();
				Stream.of(measured.getSourcesLabel().split(","))//
						.forEach(source -> {
							String strEvent = measured.process(eventTemplate);
							strEvent = set.process(strEvent);
							if (eventBuilder.length() != 0)
								eventBuilder.append(",\n");
							eventBuilder.append(strEvent);
						});
				result = result.replace("EVENTS",eventBuilder.toString());
			} else {
				result = result.replace("EVENTS","");
			}

		} catch (Exception e) {
			logger.error("Unable to process Defect template: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
		return result;
	}

}
