package com.synopsys.metrics.checkers;

import com.synopsys.metrics.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ModuleHasTooManyFiles extends Checker {

	Metrics fileCountThreshold;

	public ModuleHasTooManyFiles() {
		setName("METRICS.MODULE_HAS_TOO_MANY_FILES");
		setDescription("Detect modules with too many files.");
		jsonDefectTemplateFilename = "METRICS.MODULE_HAS_TOO_MANY_FILES.txt"; 
		jsonDefectEventTemplateFilename = "METRICS.MODULE_HAS_TOO_MANY_FILES.events.txt"; 
		
		setJsonDefectTemplate("");
		setJsonDefectEventTemplate("");

		fileCountThreshold = new Metrics();
		fileCountThreshold.scope = "Module Metrics";
		fileCountThreshold.metric = "file_count";
		fileCountThreshold.name = "file_count";
		fileCountThreshold.value = 20;

		_allMetrics.add(fileCountThreshold);
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
					.flatMap(Measurable::getAllSources)//
					.flatMap(strList -> Stream.of(strList.split(",",200)))
					.sorted()//
					.distinct() //
					.count();
			
			if (count > fileCountThreshold.value) {
				result = new Defect(this, measured);
				List<String> violatingMetrics = new ArrayList<>();
				violatingMetrics.add(fileCountThreshold.metric);
				result.setViolations(violatingMetrics);
			}
		}
		return result;
	}
}
