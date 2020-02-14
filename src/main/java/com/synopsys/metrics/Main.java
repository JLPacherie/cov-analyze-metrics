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

import com.hazelcast.internal.metrics.metricsets.FileMetricSet;
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
		_logger.debug("Collecting all function metrics from {}", inputMetricFileName);
		List<FuncMetrics> funcMeasures = new ArrayList<>();
		main.getParsedStream(inputMetricFileName).forEach(m -> funcMeasures.add(m));
		_logger.debug("Parsing input file metrics found {} functions with metrics.", funcMeasures.size());

		// ----------------------------------------------------------------------------------------------------------------
		// Add to the collection of Measurable objects the aggregated function metrics for a same file
		// ----------------------------------------------------------------------------------------------------------------
		HashMap<String, CompositeMetrics> fileMetrics = new HashMap<>();
		HashMap<String, CompositeMetrics> moduleMetrics = new HashMap<>();
		{
			_logger.debug("Aggregating function metrics by files and modules");
			Map<String, List<FuncMetrics>> fileMeasures = new HashMap<>();
			Map<String, List<FuncMetrics>> moduleFuncMetrics = new HashMap<>();

			for (FuncMetrics fMetrics : funcMeasures) {

				String fileLabel = fMetrics.getPathname();
				List<FuncMetrics> byFileList = fileMeasures.get(fileLabel);
				if (byFileList == null) {
					byFileList = new ArrayList<>();
					fileMeasures.put(fileLabel, byFileList);
				}
				byFileList.add(fMetrics);

				String className = fMetrics.getClassName();
				String moduleLabel = fMetrics.getModuleName();

				List<FuncMetrics> byModuleList = moduleFuncMetrics.get(moduleLabel);
				if (byModuleList == null) {
					byModuleList = new ArrayList<>();
					moduleFuncMetrics.put(moduleLabel, byModuleList);
				}
				byModuleList.add(fMetrics);

			}

			//
			// Remove common prefixes on file names
			//
			if (!funcMeasures.isEmpty()) {

				funcMeasures.sort((m1, m2) -> m1.getSourcesLabel().compareTo(m2.getSourcesLabel()));

				HashMap<String, Integer> prefixes = new HashMap<>();
				String prefix = funcMeasures.get(0).getSourcesLabel();
				int iFile = 1;
				int nbMatches = 0;
				while ((!prefix.isEmpty()) && (iFile < funcMeasures.size())) {

					String fileName = funcMeasures.get(iFile).getSourcesLabel();
					String pathName = fileName.substring(0, fileName.lastIndexOf('/'));
					//_logger.debug("File {} has module {}", funcMeasures.get(iFile).getPathname(),
					//		funcMeasures.get(iFile).getModuleName());

					prefixes.compute(pathName, (k, v) -> (v == null) ? Integer.valueOf(1) : v + 1);

					iFile++;
				}

				prefixes.entrySet().forEach(item -> {
					_logger.debug("Prefix {} has {} macthes", item.getKey(), item.getValue());
				});

				if (!prefixes.isEmpty()) {

				}
			}
			_logger.debug("Found metrics for {} different files.", fileMeasures.size());
			_logger.debug("Found metrics for {} different modules.", moduleFuncMetrics.size());

			//
			// For each file listed in the sources of the function metrics, we collect the metrics for
			// each of the functions into a single 'file' composite metric.
			//
			for (Map.Entry<String, List<FuncMetrics>> entry : fileMeasures.entrySet()) {
				String fileLabel = entry.getKey();
				FileMetrics metrics = new FileMetrics("File Metrics");
				for (Measurable m : entry.getValue()) {
					metrics.add(m);
				}
				metrics.add("file", fileLabel, Parameter.READ_WRITE);
				fileMetrics.put(fileLabel, metrics);
				_logger.debug("Registered {} functions in file {}.", entry.getValue().size(), fileLabel);
			}

			//
			//
			//
			for (Map.Entry<String, List<FuncMetrics>> entry : moduleFuncMetrics.entrySet()) {
				String moduleLabel = entry.getKey();
				ModuleMetrics metrics = new ModuleMetrics("Module Metrics");
				for (Measurable m : entry.getValue()) {
					metrics.add(m);
				}
				metrics.add("file", metrics.getSourcesLabel(), Parameter.READ_WRITE);
				metrics.add("module", moduleLabel, Parameter.READ_WRITE);
				moduleMetrics.put(moduleLabel, metrics);
				_logger.debug("Registered {} functions in module {}", entry.getValue().size(), moduleLabel);
			}

		}
		// ----------------------------------------------------------------------------------------------------------------
		// Collecting defect results.
		// ----------------------------------------------------------------------------------------------------------------
		Stream<Measurable> allMeasures = Stream.concat(funcMeasures.stream(), //
				Stream.concat(fileMetrics.values().stream(), moduleMetrics.values().stream()));

		List<Defect> defects = allMeasures.parallel() //
				.filter(measurable -> config.filter(measurable)) // Checker.check implementation to implement filtering ?
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
				if (defect.measured instanceof FuncMetrics) {
					src = "[FUNCTION]  " + src;
				} else if (defect.measured instanceof ModuleMetrics) {
					src = "[MODULE]    " + src;
				} else if (defect.measured instanceof FileMetrics) {
					src = "[FILE]      " + src;
				}
				countBy.compute(src, (k, v) -> (v == null) ? Integer.valueOf(1) : v + 1);
			}

			System.out.println("Defects count by file");
			countBy.entrySet().stream().sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
					.forEach(e -> System.out.printf("\t%8d %s\n", e.getValue(), e.getKey()));
		}

		_logger.info("Found {} defects.", defects.size());

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
				}

				// End of JSON segments for defects and beginning of the array for source files.
				writer.write("\n\t],\n" + "\t\"sources\": [\n");

				
				String json = defects.stream()//
						.map(defect -> defect.measured.getSourcesLabel())//
						.flatMap(strList -> Stream.of(strList.split(",")))
						.sorted() //
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
