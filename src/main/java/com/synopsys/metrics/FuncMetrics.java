package com.synopsys.metrics;

import com.synopsys.sipm.model.Parameter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.File;
import java.util.stream.Stream;

/**
 * Manages the code metrics for a single function. Each components of the XML metrics structure provided by Coverity is
 * saved as a String associated to a key. Those components stores: The name of the function the metrics relates to The
 * file where the function is defined Impact information (???) Coverage information And the code metrics themselves.
 */
public class FuncMetrics extends Measurable {

	protected Logger logger = LogManager.getLogger(FuncMetrics.class);

	public static String tagMETRICS = "metrics";
	public static String tagFILE = "file";
	public static String tagNAMES = "names";
	public static String tagCOVERAGE = "coverage";
	public static String tagIMPACT = "impact";

	public static String[] loadedTags = { tagMETRICS, tagFILE, tagNAMES, tagCOVERAGE, tagIMPACT };

	public FuncMetrics() {
		super("Function Metrics");
		for (String tag : loadedTags) {
			add(tag, "", Parameter.READ_WRITE);
		}
		add("fdir", null, Parameter.READ_WRITE);
		add("fname", null, Parameter.READ_WRITE);
		add("module", null, Parameter.READ_WRITE);
		add("class", null, Parameter.READ_WRITE);
	}

	//
	// ******************************************************************************************************************
	//

	public Stream<String> getAllSources() {
		return Stream.of(getPathname());
	}

	public String getSourcesLabel() {
		return getPathname();
	}

	//
	// ******************************************************************************************************************
	//

	public String getPathname() {
		return get("file");
	}

	public void setPathname(String value) {
		set("file", value);
	}

	public void autoset() {
		String file = getPathname();
		if (file != null) {
			int pos = file.lastIndexOf(File.separatorChar);
			if (pos == -1) {
				_logger.warn("Suspicious pathname");
			} else {
				String dir = file.substring(0, pos);
				String name = file.substring(pos + 1);
				set("fdir", dir);
				set("fname", name);
				set("module", dir);
				set("class", name);
			}
		}
	}

	//
	// ******************************************************************************************************************
	//

	public String getFunctionName() {
		return get("function");
	}

	public String getClassName() {
		return get("class");
	}

	public String getModuleName() {
		return get("module");
	}
	//
	// ******************************************************************************************************************
	//

	/**
	 * Read the function's metrics components from the Coverity XML syntax.
	 */
	public boolean read(XMLStreamReader xmlsr) {
		//
		// The expected XML structure for a function metrics is :
		//
		// <fnmetrics>
		// <file>...</file>
		// <names>...</names>
		// <metrics>...</metrics>
		// <coverage>...</coverage>
		// <impact>...</impact>
		// </fnmetrics>
		//
		boolean result = false;
		try {
			result = (xmlsr != null) && (xmlsr.hasNext());
			if (result) {
				result = false;
				int eventType = 0;
				String key = "";
				String value = "";
				while (!result && xmlsr.hasNext()) {
					eventType = xmlsr.next();
					switch (eventType) {
					case XMLEvent.START_ELEMENT: {
						if (!key.isEmpty()) {
							_logger.error("Embedded XML element is not expected");
						}
						String elementName = xmlsr.getName().getLocalPart();
						for (String tag : loadedTags) {
							if (tag.equals(elementName)) {
								key = tag;
							}
						}
						if (key.isEmpty()) {
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

						if (!value.isEmpty()) {
							set(key, value);
						}

						key = "";
						value = "";
					}
						break;

					case XMLEvent.CDATA:
					case XMLEvent.CHARACTERS:
						if (!key.isEmpty()) {
							String text = xmlsr.getText();
							value += text;
						}
						break;
					default:
						break;
					}
				}
			}
		} catch (XMLStreamException e2) {

		}

		autoset();
		return result;
	}

	/**
	 * Parse the content of the <metrics> tag to extract all metrics.
	 */
	public boolean parse() {
		boolean result = true;

		// ------------------------------------------------------------------------
		// Parse the metrics field from the XML file into a list of metrics
		// ------------------------------------------------------------------------

		// TODO Can we clear previous metrics values here ?

		String[] metricsList = get("metrics", "").split(";");

		if (metricsList.length == 0) {
			_logger.error("There's no 'metrics' element associated to function in file {}", getPathname());
		} else {
			for (String metric : metricsList) {
				String[] metricList = metric.split(":");
				if (metricList.length == 2) {
					if (metricList[0].equals("lc")) {
						setMetrics(tagLOC, Double.parseDouble(metricList[1]));
					} else if (metricList[0].equals("cc")) {
						setMetrics(tagCCM, Double.parseDouble(metricList[1]));
					} else {
						addMetrics(metricList[0], Double.parseDouble(metricList[1]));
					}
				} else {
					logger.error("Bad format for metrics : {}", metric);
					result = false;
				}
			}
		}

		// ------------------------------------------------------------------------
		// Parse the function and class names
		// ------------------------------------------------------------------------
		String strNames = get("names", "");
		if (strNames.isEmpty()) {
			_logger.warn("There's no 'names' element for {}",getPathname());
		} else {
			String[] fields = strNames.split(";");
			for (String field : fields) {
				if (field.startsWith("fn:")) {
					add("function", field.substring(3), Parameter.READ_WRITE);
				} else if (field.startsWith("mn:")) {
					add("module", field.substring(3), Parameter.READ_WRITE);
				} else if (field.startsWith("cn:")) {
					int end = field.indexOf("$");
					if (end == -1)
						end = field.length();
					add("class", field.substring(3, end), Parameter.READ_WRITE);
				}
			}
		}

		return result;
	}

}
