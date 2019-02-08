package com.synopsys.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.util.HashMap;
import java.util.Map;

public class FuncMetrics extends HashMap<String, String> {

  protected Logger logger = LogManager.getLogger(FuncMetrics.class);
  protected Map<String, Double> metrics = new HashMap<>();

  public String getPathname() {
    return get("file");
  }

  public void setPathname(String value) {
    put("file",value);
  }
  public String getFunctionName() {
    return get("functionName");
  }

  public String getClassName() {
    return get("className");
  }

  public boolean read(XMLStreamReader xmlsr) {
    boolean result = false;
    try {
      result = (xmlsr != null) && (xmlsr.hasNext());
      if (result) {
        result = false;
        int eventType = 0;
        String key = "";
        while (!result && xmlsr.hasNext()) {
          eventType = xmlsr.next();
          switch (eventType) {
            case XMLEvent.START_ELEMENT: {
              String elementName = xmlsr.getName().getLocalPart();
              if ("fnmetric".equals(elementName)) {
              } else if ("file".equals(elementName)) {
                key = "file";
              } else if ("names".equals(elementName)) {
                key = "names";
              } else if ("metrics".equals(elementName)) {
                key = "metrics";
              } else if ("coverage".equals(elementName)) {
                key = "coverage";
              } else if ("impact".equals(elementName)) {
                key = "impact";
              } else {
                logger.warn("Found unexpected node " + elementName);
              }
            }
            break;
            case XMLEvent.END_ELEMENT: {
              String elementName = xmlsr.getName().getLocalPart();
              //
              // Store the data for processing in a dedicated object
              //
              if ("fnmetric".equals(elementName)) {
                result = true;
              }
              key = "";
            }
            break;
            case XMLEvent.CHARACTERS:
              if (!key.isEmpty()) {
                String text = xmlsr.getText();
                if (!xmlsr.isWhiteSpace()) {
                  put(key, text);
                }
              }
              break;
            default:
              break;
          }
        }
      }
    } catch (XMLStreamException e2) {

    }

    return result;
  }

  public boolean parse() {
    boolean result = true;

    // ------------------------------------------------------------------------
    //  Parse the metrics field from the XML file into a list of metrics
    // ------------------------------------------------------------------------
    metrics.clear();
    String[] metricsList = get("metrics").split(";");
    for (String metric : metricsList) {
      String[] metricList = metric.split(":");
      if (metricList.length == 2) {
        metrics.put(metricList[0], Double.parseDouble(metricList[1]));
      } else {
        logger.error("Bad format for metrics : " + metric);
        result = false;
      }
    }

    // ------------------------------------------------------------------------
    //  Parse the function and class names
    // ------------------------------------------------------------------------
    String[] names = get("names").split(";");
    if (names.length > 0) {

      String flagedFn = names[0];
      if (flagedFn.startsWith("fn:")) {
        put("functionName", flagedFn.substring(3));
      } else {
        logger.error("Unable to extract function name from '" + get("names") + "'");
      }
    } else {
      logger.error("No names defined ? " + get("names"));
    }

    if (names.length == 2) {
      String flagedCn = names[1];
      if (flagedCn.startsWith("cn:")) {
        put("className", flagedCn.substring(3));
      } else {
        logger.error("Unable to extract class name from '" + get("names") + "'");
      }
    } else {
      put("className", "");
    }


    return result;
  }

  public double getMetric(String name) {
    if (metrics.containsKey(name)) {
      return metrics.get(name);
    } else {
      logger.error("Unable to find metric " + name + " in " + toString());
      return -1;
    }
  }

}
