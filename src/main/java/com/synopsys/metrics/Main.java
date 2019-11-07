package com.synopsys.metrics;

import static java.util.stream.Collectors.joining;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.synopsys.sipm.model.Parameter;

public class Main {

	protected static Logger _logger = LogManager.getLogger(Main.class);
	protected Config config;

	//
	// ******************************************************************************************************************
	//

	/** Initialize the application for the options on the CLI. */
	public boolean init(String[] args) {
		_logger.info("Creating a new configuration from the CLI options.");
		config = new Config(args);

		if (config.isValid()) {

			_logger.info("configuration is valid, we can proceed further.");
			_logger.info("There are " + config.enabledCheckers.size() + " checkers to process.");

		} else {

			System.out.println(config.getStandardBanner());
			System.out.println("Execution failed, check log file and command line usage with --help.");
			System.exit(-1);
		}

		return true;
	}

	//
	// ******************************************************************************************************************
	//

	/** Extract from given file the XML segment for Function metrics, returns unparsed FuncMetrics. */
	public Iterator<FuncMetrics> getFunctionMetricIter(String filename) {
		FuncMetricsIter result = null;
		try {
			result = new FuncMetricsIter(filename);
		} catch (Exception e) {
			// TODO: handle exception
			if (result != null) {
				try {
					result.close();
				} catch (IOException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}
			}
		}
		return result;
		/*
		XMLInputFactory xmlif = XMLInputFactory.newInstance();
		if (xmlif.isPropertySupported("javax.xml.stream.isReplacingEntityReferences")) {
			xmlif.setProperty("javax.xml.stream.isReplacingEntityReferences", Boolean.TRUE);
		}
		
		try {
		
			InputStream xmlRootPrefix = IOUtils.toInputStream("<root>", Charset.forName("UTF8"));
			InputStream fileStream = new FileInputStream(config.getFunctionsFileName());
			InputStream gzipStream = new GZIPInputStream(fileStream);
			InputStream xmlRootSuffix = IOUtils.toInputStream("</root>", Charset.forName("UTF8"));
		
			InputStream xmlInput = new SequenceInputStream(xmlRootPrefix, new SequenceInputStream(gzipStream, xmlRootSuffix));
		
			final XMLStreamReader xmlsr = xmlif.createXMLStreamReader(xmlInput);
		
			final String stripPath = config.getStripPath();
		
			return new Iterator<FuncMetrics>() {
		
				@Override
				public boolean hasNext() {
					boolean result = false;
					try {
		
						result = (xmlsr != null) && xmlsr.hasNext();
						if (result) {
							result = false;
							while (!result && xmlsr.hasNext()) {
								int eventType = xmlsr.next();
								result = eventType == XMLEvent.START_ELEMENT && "fnmetric".equals(xmlsr.getName().getLocalPart());
							}
						}
					} catch (XMLStreamException e) {
						e.printStackTrace();
						result = false;
					}
					return result;
				}
		
				@Override
				public FuncMetrics next() {
					FuncMetrics result = new FuncMetrics();
					boolean loaded = result.read(xmlsr);
					while (!loaded && hasNext()) {
						loaded = result.read(xmlsr);
					}
		
					if ((loaded) && (!stripPath.isEmpty()) && result.getPathname().startsWith(stripPath)) {
						result.setPathname(result.getPathname().substring(stripPath.length()));
					}
					return loaded ? result : null;
				}
			};
		
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (XMLStreamException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
		*/
	}

	//
	// ******************************************************************************************************************
	//

	/**
	 * Returns a new stream for converting each XML segment for the metrics of a function into a Hash map.
	 */
	public Stream<FuncMetrics> getParsedStream(String filename) {

		Iterator<FuncMetrics> iter = getFunctionMetricIter(filename);

		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, 0), true).filter(fm -> fm.parse());

	}

	public static void main(String[] args) {

		_logger.info("");
		_logger.info("**************************************");
		_logger.info("** Starting new execution from here **");
		_logger.info("**************************************");
		_logger.info("");

		if ((args == null) || args.length == 0) {
			_logger.error("Unable to execute without command line arguments.");
			return;
		}

		_logger.info("Command line arguments:");
		for (String opt : args) {
			_logger.info("'" + opt + "'");
		}
		_logger.info("");

		Main main = new Main();

		main.init(args);

		Config config = main.config;

		String inputMetricFileName = config.getFunctionsFileName();

		// ----------------------------------------------------------------------------------------------------------------
		// Initialize the collection of Measurable objects with the Function Metrics extracted from Coverity metrics file
		// ----------------------------------------------------------------------------------------------------------------
		_logger.debug("Collecting all function metrics from " + inputMetricFileName);
		List<Measurable> measurableList = new ArrayList<Measurable>();
		main.getParsedStream(inputMetricFileName).forEach(m -> measurableList.add(m));
		_logger.debug("Parsing input file metrics found " + measurableList.size() + " functions with metrics.");

		// ----------------------------------------------------------------------------------------------------------------
		// Add to the collection of Measurable objects the aggregated function metrics for a same file
		// ----------------------------------------------------------------------------------------------------------------
		{
			_logger.debug("Aggregating function metrics by files and modules");
			Map<String, List<Measurable>> byFileMeasures = new HashMap<>();
			Map<String, List<Measurable>> byModuleMeasures = new HashMap<>();

			for (Measurable m : measurableList) {
				if (m instanceof FuncMetrics) {
					FuncMetrics fMetrics = (FuncMetrics) m;

					String fileLabel = fMetrics.getPathname();
					List<Measurable> byFileList = byFileMeasures.get(fileLabel);
					if (byFileList == null) {
						byFileList = new ArrayList<Measurable>();
						byFileMeasures.put(fileLabel, byFileList);
					}
					byFileList.add(fMetrics);

					String className = fMetrics.getClassName();
					String moduleLabel = fMetrics.getClassName();
					List<Measurable> byModuleList = byModuleMeasures.get(moduleLabel);
					if (byModuleList == null) {
						byModuleList = new ArrayList<Measurable>();
						byModuleMeasures.put(moduleLabel, byModuleList);
					}
					byModuleList.add(fMetrics);

				}
			}

			_logger.debug("Found metrics for " + byFileMeasures.size() + " different files");
			_logger.debug("Found metrics for " + byModuleMeasures.size() + " different modules");

			for (Map.Entry<String, List<Measurable>> entry : byFileMeasures.entrySet()) {
				String fileLabel = entry.getKey();
				_logger.debug("Adding a file metrics for " + fileLabel);
				CompositeMetrics metrics = new CompositeMetrics("File Metrics");
				for (Measurable m : entry.getValue()) {
					metrics.add(m);
				}
				metrics.add("file", fileLabel, Parameter.READ_WRITE);
				measurableList.add(metrics);
				_logger.debug("Registered " + entry.getValue().size() + " funtions in that file.");
			}

			for (Map.Entry<String, List<Measurable>> entry : byModuleMeasures.entrySet()) {
				String moduleLabel = entry.getKey();
				_logger.debug("Adding a module metrics for " + moduleLabel);
				CompositeMetrics metrics = new CompositeMetrics("Module Metrics");
				for (Measurable m : entry.getValue()) {
					metrics.add(m);
				}
				metrics.add("file", metrics.getSourcesLabel(), Parameter.READ_WRITE);
				metrics.add("module", moduleLabel, Parameter.READ_WRITE);
				measurableList.add(metrics);
				_logger.debug("Registered " + entry.getValue().size() + " functions in that module.");
			}

		}
		// ----------------------------------------------------------------------------------------------------------------
		// Collecting defect results.
		// ----------------------------------------------------------------------------------------------------------------
		List<Defect> defects = measurableList.parallelStream() //
				.filter(fnMetrics -> config.filter(fnMetrics))// TODO Checker.check implementation to implement filtering ?
				.flatMap(measurable -> config.check(measurable))// Each measured item may trigger multiple defects
				.collect(Collectors.toList());

		// ----------------------------------------------------------------------------------------------------------------
		// Counts results by checker
		// ----------------------------------------------------------------------------------------------------------------
		{
			Map<String, Integer> countBy = new HashMap<>();
			for (Defect defect : defects) {
				String key = defect.checker.getName();
				countBy.compute(key, (k, v) -> (v == null) ? Integer.valueOf(1) : v + 1);
			}

			System.out.println("Defects count " + defects.size());
			System.out.println("Defects count by checker");

			countBy.entrySet().stream()//
					.sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())) //
					.forEach(e -> System.out.printf("\t%8d %s\n", e.getValue(), e.getKey()));

		}

		// ----------------------------------------------------------------------------------------------------------------
		// Counts results by file
		// ----------------------------------------------------------------------------------------------------------------
		{
			Map<String, Integer> countBy = new HashMap<>();
			for (Defect defect : defects) {
				String src = defect.measured.getSourcesLabel();
				countBy.compute(src, (k, v) -> (v == null) ? Integer.valueOf(1) : v + 1);
			}

			System.out.println("Defects count by file");
			countBy.entrySet().stream().sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
					.forEach(e -> System.out.printf("\t%8d %s\n", e.getValue(), e.getKey()));
		}

		_logger.info("Found " + defects.size() + " defects.");

		// ----------------------------------------------------------------------------------------------------------------
		// Build the JSON file for cov-import-results
		// ----------------------------------------------------------------------------------------------------------------
		{
			try (FileOutputStream os = new FileOutputStream(config.getReportFile());
					Writer writer = new OutputStreamWriter(os, "UTF8");) {

				writer.write("\n" + "{\n" + "\t\"header\" : {\n" + "\t\t\"version\" : 1,\n"
						+ "\t\t\"format\" : \"cov-import-results input\" \n" + "\t},\n" + "\t\n" + "\t\"issues\": [\n");

				boolean first = true;
				for (int iDefect = 0; iDefect < defects.size(); iDefect++) {
					String json = defects.get(iDefect).getJson();
					if (json != null) {
						if (!first)
							writer.write(",\n");
						writer.write("\t\t");
						writer.write(json);
						first = false;
					} else {
						_logger.error("Unable to retreive JSON excerpt for defect");
					}
					iDefect++;

				}

				// End of JSON segments for defects and beginning of the array for source files.
				writer.write("\n\t],\n" + "\t\"sources\": [\n");

				String json = defects.stream()//
						.map(defect -> defect.measured.getSourcesLabel())//
						.distinct() //
						.map(path -> "\t\t{ \"file\": \"" + path + "\", \"encoding\": \"ASCII\" }")//
						.collect(joining(",\n"));

				writer.write(json);

				writer.write("\n\t]\n}\n");

			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}
}
