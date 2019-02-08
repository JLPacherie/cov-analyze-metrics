package com.synopsys.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Checker {

    protected Logger logger = LogManager.getLogger(Checker.class);


    private String name = null;
    private String description = null;
    private List<Metrics> allMetrics = new ArrayList<Metrics>();
    private String jsonDefectTemplate = null;

    public Checker() {

    }

    public Checker(File file) {
        if (!load(file.getAbsolutePath())) {
            logger.error("Unable to load a checker from file " + file.getAbsolutePath());
        }
    }

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

    public Metrics getMetric(String name) {
        return allMetrics.stream()
                .filter(m -> m.name.equals(name) || m.metric.equals(name))
                .findFirst()
                .orElse(null);
    }

    public boolean hasMetric(String name) {
        return getMetric(name) != null;
    }

    public double getThreshold(String metricName) {

        Metrics metric = allMetrics.stream()
                .filter(m -> m.name.equals(metricName) || m.metric.equals(metricName))
                .findFirst()
                .orElse(null);

        if (metric != null) {
            return metric.value;
        }
        return -1;
    }


    public void setThreshold(String metricName, double threshold) {

        Metrics metric = allMetrics.stream()
                .filter(m -> m.name.equals(metricName) || m.metric.equals(metricName))
                .findFirst()
                .orElse(null);

        if (metric != null) {
            metric.value = threshold;
        } else {
            logger.error("Unknown metric name. " + metricName);
        }
    }

    //
    // ******************************************************************************************************************
    //

    public Stream<Metrics> metrics() {
        return allMetrics.stream();
    }

    //
    // ******************************************************************************************************************
    //

    public boolean isValid() {
        boolean result = true;

        if ((getName() == null) || !getName().startsWith("METRICS.")) {
            result = false;
            logger.error("Invalid checker name : " + getName());
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
    public boolean filter(FuncMetrics funcMetrics) {
        return funcMetrics != null;
    }

    /**
     * Applies the checker on the metrics from the given function. Returns a Defect if all the thresholds are passed over.
     */
    public Defect check(FuncMetrics funcMetrics) {

        assert allMetrics.size() > 0 : "There's no metrics to check for in this checker ??";
        assert funcMetrics != null : "There's no function metrics provided for checking.";
        assert filter(funcMetrics) : "The function metrics should be filtered before invoking check()";

        Defect result = null;

        List<String> violatingMetrics = allMetrics.stream()
                .map(metric -> funcMetrics.getMetric(metric.metric) > metric.value ? metric.metric : null)
                .filter(m -> m != null)
                .collect(Collectors.toList());

        if (violatingMetrics.size() >= allMetrics.size()) {
            result = new Defect(this, funcMetrics);
            result.setViolations(violatingMetrics);
        }
        return result;
    }

    public String getJsonDefectTemplate() {
        return jsonDefectTemplate;
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
                    metric.name = node.get("name").asText("");
                    metric.metric = node.get("metrics").asText("");
                    metric.value = node.get("threshold").asDouble(0);
                    allMetrics.add(metric);
                }
            }

            JsonNode defectTemplate = root.get("defect-template");
            if (defectTemplate != null) {
                jsonDefectTemplate = Utils.getJsonElement(defectTemplate, null);
            } else {
                logger.error("No template JSON defined for defects generated by this checker.");
                result = false;
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
                logger.error("Checker file not found at :" + filename);
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
        String result = getName() + " (";
        for (Metrics m : allMetrics) {
            result += m.name + "=" + m.value + " ";
        }
        result += ")";
        return result;
    }
}
