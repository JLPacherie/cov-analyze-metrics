package com.synopsys.metrics;

import java.util.List;

public class Defect {

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
    String result = checker.toString() + " triggered on " + funcMetrics.get("names") + " in " + funcMetrics.get("file") +" ( ";
    for (String m : violations) {
      result += m + " is " + funcMetrics.getMetric(m) + " > " + checker.getThreshold(m);
    }
    result += " )";
    return result;
  }

  public String Double2String(double d) {
    long l = (long) d;
    if (l == d) {
      return Long.toString(l);
    }
    return Double.toString(d);
  }

  public String getJson() {
    String result = checker.getJsonDefectTemplate();

    //  TODO Strip filenaes ?
    result = result.replaceAll("\\$\\{fileName\\}",funcMetrics.getPathname());

    result = result.replaceAll("\\$\\{funcName\\}",funcMetrics.getFunctionName());

    result = result.replaceAll("\\$\\{className\\}",funcMetrics.getClassName());

    for (String key : funcMetrics.keySet()) {
      result = result.replaceAll( "\\$\\{" + key + "\\}",funcMetrics.get(key));
    }

    for (String metric : funcMetrics.metrics.keySet()) {
      Metrics m = checker.getMetric(metric);
      if (m !=null) {

        result = result.replaceAll("\\$\\{" + m.name + ".threshold\\}",
                Double2String(checker.getThreshold(metric)));

        result = result.replaceAll("\\$\\{" + m.metric + ".threshold\\}",
                Double2String(checker.getThreshold(metric)));

        result = result.replaceAll("\\$\\{" + m.name + "\\}",
                Double2String(funcMetrics.getMetric(m.metric)));

        result = result.replaceAll("\\$\\{" + m.metric + "\\}",
                Double2String(funcMetrics.getMetric(m.metric)));

        result = result.replaceAll("\\$\\{" + m.name + "\\}",
                Double2String(funcMetrics.getMetric(m.metric)));
      }

    }

    return result;
  }

}
