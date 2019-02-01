package com.synopsys.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class Defect {

    protected Logger logger = LogManager.getLogger(Config.class);

    protected Checker checker;
    protected FuncMetrics funcMetrics;
    protected List<String> violations;

    public Defect(Checker checker, FuncMetrics metrics) {
        this.checker = checker;
        this.funcMetrics = metrics;
    }

    public void setViolations(List<String> list) {
        this.violations = list;
    }

    public String toString() {
        String result = checker.toString() + " triggered on " + funcMetrics.get("names") + " in " + funcMetrics.get("file") + " ( ";
        for (String m : violations) {
            result += m + " is " + funcMetrics.getMetric(m) + " > " + checker.getThreshold(m);
        }
        result += " )";
        return result;
    }

    public String Double2String(double d) {
        long l = (long) d;
        if ((double) l == (double) d) {
            return Long.toString(l);
        }
        return Double.toString(d);
    }

    public String getJson() {
        String result = checker.getJsonDefectTemplate();

        try {
            //  TODO Strip filenaes ?

            result = result.replace("${fileName}", funcMetrics.getPathname());

            result = result.replace("${funcName}", funcMetrics.getFunctionName());

            result = result.replace("${className}", funcMetrics.getClassName());

            for (String key : funcMetrics.keySet()) {
                result = result.replace("${" + key + "}", funcMetrics.get(key));
            }

            for (String metric : funcMetrics.metrics.keySet()) {
                Metrics m = checker.getMetric(metric);
                if (m != null) {

                    result = result.replace("${" + m.name + ".threshold}",
                            Double2String(checker.getThreshold(metric)));

                    result = result.replace("${" + m.metric + ".threshold}",
                            Double2String(checker.getThreshold(metric)));

                    result = result.replace("${" + m.name + "}",
                            Double2String(funcMetrics.getMetric(m.metric)));

                    result = result.replace("${" + m.metric + "}",
                            Double2String(funcMetrics.getMetric(m.metric)));
                }
                result = result.replace("${" + metric + "}",
                        Double2String(funcMetrics.metrics.get(metric)));


            }
        } catch (Exception e) {
            logger.error("Unable to process Defect template.");

        }
        return result;
    }

}
