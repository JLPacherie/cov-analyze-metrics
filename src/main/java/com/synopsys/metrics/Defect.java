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

  public String getJson() {
    return checker.getJsonDefectTemplate();
  }

}
