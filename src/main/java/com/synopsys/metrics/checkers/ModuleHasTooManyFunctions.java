package com.synopsys.metrics.checkers;

import com.synopsys.metrics.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleHasTooManyFunctions extends Checker {

	Metrics funcCountThreshold;

	public ModuleHasTooManyFunctions() {
		setName("METRICS.MODULE_HAS_TOO_MANY_FUNCTIONS");
		setDescription("Detect modules with too many functions or methods.");
		jsonDefectTemplateFilename = "METRICS.MODULE_HAS_TOO_MANY_FUNCTIONS.txt";
		jsonDefectEventTemplateFilename = "METRICS.MODULE_HAS_TOO_MANY_FUNCTIONS.events.txt";
	
		setJsonDefectTemplate("");
		setJsonDefectEventTemplate("");
		
		funcCountThreshold = new Metrics();
		funcCountThreshold.scope = "Module Metrics";
		funcCountThreshold.metric = "func_count";
		funcCountThreshold.name = "func_count";
		funcCountThreshold.value = 50;

		_allMetrics.add(funcCountThreshold);
	}

	@Override
	public boolean canCheck(Measurable measured) {
		return super.canCheck(measured) && (measured instanceof ModuleMetrics);
	}

	/**
	 * Applies the checker on the metrics from the given function. Returns a Defect if all the thresholds are passed over.
	 */
	public Defect check(Measurable measured) {

		if (!canCheck(measured)) {
			throw new IllegalArgumentException("Can't check measurable: " + measured);
		}

		Defect result = null;

		double min_loc = 3;
		double min_ccm = 1;

		if (measured instanceof ModuleMetrics) {
			ModuleMetrics cm = (ModuleMetrics) measured;
			long count = cm.stream()//
					.filter(m -> m.getMetric(Measurable.tagLOC) > min_loc)//
					.filter(m -> m.getMetric(Measurable.tagCCM) > min_ccm)//
					.count();

			if (count > funcCountThreshold.value) {
				result = new Defect(this, measured);
				List<String> violatingMetrics = new ArrayList<>();
				violatingMetrics.add(funcCountThreshold.metric);
				result.setViolations(violatingMetrics);
			}
		}
		return result;
	}
}
