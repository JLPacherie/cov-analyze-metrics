package com.synopsys.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Checker {

	protected Logger logger = LogManager.getLogger(Checker.class);

	private String name = null;
	private String description = null;

	protected List<Metrics> _allMetrics = new ArrayList<>();

	private String jsonDefectTemplate = null;
	protected String jsonDefectTemplateFilename = null;

	private String jsonDefectEventTemplate = null;
	protected String jsonDefectEventTemplateFilename = null;

	public Checker() {

	}

	/**
	 * Load the checker definition from the given file.
	 */
	public Checker(File file) {
		if (!load(file.getAbsolutePath())) {
			logger.error("Unable to load a checker from file {}.", file.getAbsolutePath());
		}
	}

	/**
	 * Loaf the checker definition form the given input stream.
	 */
	public Checker(InputStream is) {
		if (!load(is)) {
			logger.error("Unable to load a checker from  input stream");
		}
	}

	//
	// ******************************************************************************************************************
	//

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	//
	// ******************************************************************************************************************
	//

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	//
	// ******************************************************************************************************************
	//

	/** If there's a metric identified by the given name in this checker then return it. */
	public Metrics getMetric(String name) {
		return _allMetrics.stream().filter(m -> m.name.equals(name) || m.metric.equals(name)).findFirst().orElse(null);
	}

	public boolean hasMetric(String name) {
		return getMetric(name) != null;
	}

	public double getThreshold(String metricName) {

		Metrics metric = _allMetrics.stream().filter(m -> m.name.equals(metricName) || m.metric.equals(metricName))
				.findFirst().orElse(null);

		if (metric != null) {
			return metric.value;
		}
		return -1;
	}

	public void setThreshold(String metricName, double threshold) {

		Metrics metric = _allMetrics.stream().filter(m -> m.name.equals(metricName) || m.metric.equals(metricName))
				.findFirst().orElse(null);

		if (metric != null) {
			metric.value = threshold;
		} else {
			logger.error("Unknown metric name '{}'", metricName);
		}
	}

	//
	// ******************************************************************************************************************
	//

	public Stream<Metrics> metrics() {
		return _allMetrics.stream();
	}

	//
	// ******************************************************************************************************************
	//

	public boolean isValid() {
		boolean result = true;

		if ((getName() == null) || !getName().startsWith("METRICS.")) {
			result = false;
			logger.error("Invalid checker name (prefix is not METRICS.) '{}'", getName());
		}

		if (_allMetrics.isEmpty()) {
			logger.error("There's no metrics to check for in this checker ??");
			result = false;
		}

		return result;
	}

	//
	// ******************************************************************************************************************
	//

	// TODO Implement file path regex matching filter for the Checker

	/**
	 * Returns true if the current checker is applicable to the given function metrics.
	 */
	public boolean canCheck(Measurable measured) {
		if (!isValid()) {
			logger.debug("Can't check '{}' because invalid config.", this);
		}

		return isValid() && measured != null;
	}

	/**
	 * Applies the checker on the metrics from the given function. Returns a Defect if all the thresholds are passed over.
	 */
	public Defect check(Measurable measured) {

		if (!canCheck(measured)) {
			throw new IllegalArgumentException("Can't check measurable: " + measured);
		}

		Defect result = null;

		List<String> violatingMetrics = _allMetrics.stream()
				.filter(metric -> metric.scope.isEmpty() || (measured.getName().matches(metric.scope))) //
				// For each metrics this checker puts a threshold, if the value in the measured object is above
				// the threshold then produce the name of that metrics otherwise null
				.map(metric -> {
					if (measured.isMetrics(metric.metric)) {
						return measured.getMetric(metric.metric) > metric.value ? metric.metric : null;
					} else {
						logger.warn("For checker {} Requesting unknown metrics '{}' on {}", getName(), metric.metric, measured);
					}
					return null;
				})//
				//
				// Filter out null values for metrics under the threshold.
				.filter(Objects::nonNull)
				// Collect all metrics above their threshold in the checker into a list
				.collect(Collectors.toList());

		// If the number of metrics in the measured object above their threshold in the checkers
		// is equal or above the total number of checked metrics in that checker then we have a defect
		if (violatingMetrics.size() >= _allMetrics.size()) {
			result = new Defect(this, measured);
			result.setViolations(violatingMetrics);
		}

		return result;
	}

	//
	// ******************************************************************************************************************
	//

	public String getJsonDefectTemplateFilename() {
		return jsonDefectTemplateFilename;
	}

	public String getJsonDefectTemplate() {
		return jsonDefectTemplate;
	}

	public void setJsonDefectTemplate(String value) {
		jsonDefectTemplate = value;
	}

	//
	// ******************************************************************************************************************
	//

	public String getJsonDefectEventTemplateFilename() {
		return jsonDefectEventTemplateFilename;
	}

	public String getJsonDefectEventTemplate() {
		return jsonDefectEventTemplate;
	}

	public void setJsonDefectEventTemplate(String value) {
		jsonDefectEventTemplate = value;
	}

	//
	// ******************************************************************************************************************
	//

	public boolean load(JsonNode root) {
		boolean result = (root != null);
		if (result) {
			setName(root.get("name").asText(""));
			setDescription(root.get("description").asText(""));
			JsonNode metrics = root.get("thresholds");
			if (metrics.isArray()) {
				for (JsonNode node : metrics) {
					Metrics metric = new Metrics();
					metric.scope = node.get("scope").asText("");
					metric.name = node.get("name").asText("");
					metric.metric = node.get("metrics").asText("");
					metric.value = node.get("threshold").asDouble(0);
					_allMetrics.add(metric);
				}
			}

			{
				JsonNode defectTemplate = root.get("defect-template");

				if (defectTemplate != null) {
					jsonDefectTemplateFilename = defectTemplate.asText("");
				} else {
					logger.error("No template JSON defined for defects generated by this checker.");
					result = false;
				}
			}

			{
				JsonNode defectTemplate = root.get("defect-event-template");

				if (defectTemplate != null) {
					jsonDefectEventTemplateFilename = defectTemplate.asText("");
				} else {
					logger.warn("No event template JSON defined for defects generated by this checker.");
				}
			}
		}
		return result;
	}

	//
	// ******************************************************************************************************************
	//

	public boolean load(InputStream is) {
		JsonNode node = null;
		if (is != null) {
			try {
				node = Utils.getObjectMapper().readTree(is);
			} catch (IOException io) {
				logger.error("Unable parse JSON node from input stream.");
			}

		}
		return load(node);
	}

	public boolean load(String filename) {
		boolean result = (filename != null);
		if (result) {
			File file = new File(filename);
			if (file.exists()) {
				result = load(Utils.getJsonNodeFromFile(filename));
			} else {
				result = false;
				logger.error("Checker file not found at: '{}'", filename);
			}
		}
		return result;
	}

	public boolean save(String filename) {
		boolean result = (filename != null);
		if (result) {
			result = false;
			logger.error("Unimplemented method.");
		}
		return result;
	}

	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append(getName());
		result.append(" (");
		for (Metrics m : _allMetrics) {
			result.append(m.name);
			result.append("=");
			result.append(m.value);
			result.append(" ");
		}
		result.append(")");
		return result.toString();
	}
}
