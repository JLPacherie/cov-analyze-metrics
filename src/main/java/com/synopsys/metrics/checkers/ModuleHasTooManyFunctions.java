package com.synopsys.metrics.checkers;

import java.util.ArrayList;
import java.util.List;

import com.synopsys.metrics.Checker;
import com.synopsys.metrics.CompositeMetrics;
import com.synopsys.metrics.Defect;
import com.synopsys.metrics.Measurable;
import com.synopsys.metrics.Metrics;

public class ModuleHasTooManyFunctions extends Checker {

	Metrics funcCountThreshold;

	public ModuleHasTooManyFunctions() {
		setName("METRICS.MODULE_HAS_TOO_MANY_FUNCTIONS");
		setDescription("Detect modules with too many functions or methods.");
		jsonDefectTemplateFilename = "METRICS.MODULE_HAS_TOO_MANY_FUNCTIONS.txt"; 
		setJsonDefectTemplate("");

		funcCountThreshold = new Metrics();
		funcCountThreshold.scope = "Module Metrics";
		funcCountThreshold.metric = "func_count";
		funcCountThreshold.name = "func_count";
		funcCountThreshold.value = 50;

		_allMetrics.add(funcCountThreshold);
	}

	/**
	 * Applies the checker on the metrics from the given function. Returns a Defect if all the thresholds are passed over.
	 */
	public Defect check(Measurable measured) {

		assert measured != null : "There's no function metrics provided for checking.";
		assert filter(measured) : "The function metrics should be filtered before invoking check()";

		Defect result = null;

		double min_loc = 3;
		double min_ccm = 1;

		if (measured instanceof CompositeMetrics) {
			CompositeMetrics cm = (CompositeMetrics) measured;
			long count = cm.stream()//
					.filter(m -> m.getMetric(Measurable.tagLOC) > min_loc)//
					.filter(m -> m.getMetric(Measurable.tagCCM) > min_ccm)//
					.count();
			
			if (count > funcCountThreshold.value) {
				result = new Defect(this, measured);
				List<String> violatingMetrics = new ArrayList<String>();
				violatingMetrics.add(funcCountThreshold.metric);
				result.setViolations(violatingMetrics);
			}
		}
		return result;
	}
}
