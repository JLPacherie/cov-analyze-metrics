package com.synopsys.metrics;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

import static java.util.stream.Collectors.joining;

public class Main {

  protected static Logger logger = LogManager.getLogger(Main.class);
  protected Config config;

  public boolean init(String[] args) {
    logger.info("Creating a new configuration from the CLI options.");
    config = new Config(args);

    if (config.isValid()) {

      logger.info("configuration is valid, we can proceed further.");
      logger.info("There are " + config.enabledCheckers.size() + " checkers to process.");

    } else {

      System.out.println(config.getStandardBanner());
      System.out.println("Execution failed, check log file and command line usage with --help.");
      System.exit(-1);
    }

    return true;
  }


  public Iterator<FuncMetrics> getFunctionMetricIter(String filename) {

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

      final String stripPath  = config.getStripPath();

      return new Iterator<FuncMetrics>() {


        @Override
        /** Move to the next fnmetrics tag or fails */
        public boolean hasNext() {
          boolean result = false;
          try {

            result = (xmlsr != null) && xmlsr.hasNext();
            if (result) {
              result = false;
              while (!result && xmlsr.hasNext()) {
                int eventType = xmlsr.next();
                result = eventType == XMLEvent.START_ELEMENT &&
                        "fnmetric".equals(xmlsr.getName().getLocalPart());
              }
            }
          } catch (XMLStreamException e) {
            e.printStackTrace();
            result = false;
          }
          return result;
        }

        @Override
        /** Read the fnmetric record at current position. */
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
      }

              ;

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (XMLStreamException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Returns a new stream for converting each XML segment for the metrics of a function into a Hash map.
   */
  public Stream<FuncMetrics> getParsedStream(String filename) {

    Iterator<FuncMetrics> iter = getFunctionMetricIter(filename);

    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(iter, 0),
            true
    ).filter(fm -> fm.parse());

  }

  public static void main(String[] args) {

    logger.info("");
    logger.info("**************************************");
    logger.info("** Starting new execution from here **");
    logger.info("**************************************");
    logger.info("");

    if ((args == null) || args.length == 0) {
      logger.error("Unable to execute without command line arguments.");
      return;
    }


    logger.info("Command line arguments:");
    for (String opt : args) {
      logger.info("'" + opt + "'");
    }
    logger.info("");

    Main main = new Main();

    main.init(args);

    Config config = main.config;

    // ----------------------------------------------------------------------------------------------------------------
    // Collecting defect results.
    // ----------------------------------------------------------------------------------------------------------------
    List<Defect> defects = main.getParsedStream(config.getFunctionsFileName())
            .parallel()
            .filter(fnMetrics -> config.filter(fnMetrics))
            .flatMap(fnmetrics -> config.check(fnmetrics))
            .collect(Collectors.toList());

    // ----------------------------------------------------------------------------------------------------------------
    // Counts results by checker
    // ----------------------------------------------------------------------------------------------------------------
    {
      Map<String, Integer> countBy = new HashMap<>();
      for (Defect defect : defects) {
        String key = defect.checker.getName();
        countBy.compute(key, (k, v) -> (v == null) ? new Integer(1) : v + 1);
      }

      System.out.println("Defects count by checker");

      countBy.entrySet().stream()
              .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
              .forEach(e -> System.out.printf("\t%8d %s\n", e.getValue(), e.getKey()));

    }

    // ----------------------------------------------------------------------------------------------------------------
    // Counts results by file
    // ----------------------------------------------------------------------------------------------------------------
    {
      Map<String, Integer> countBy = new HashMap<>();
      for (Defect defect : defects) {
        String path = defect.funcMetrics.getPathname();
        countBy.compute(path, (k, v) -> (v == null) ? new Integer(1) : v + 1);
      }

      System.out.println("Defects count by file");
      countBy.entrySet().stream()
              .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
              .forEach(e -> System.out.printf("\t%8d %s\n", e.getValue(), e.getKey()));
    }

    logger.info("Found " + defects.size() + " defects.");

    // ----------------------------------------------------------------------------------------------------------------
    // Build the JSON file for cov-import-results
    // ----------------------------------------------------------------------------------------------------------------
    {
      try {

        FileOutputStream os = new FileOutputStream(config.getReportFile());
        Writer writer = new OutputStreamWriter(os);


        writer.write("\n" +
                "{\n" +
                "\t\"header\" : {\n" +
                "\t\t\"version\" : 1,\n" +
                "\t\t\"format\" : \"cov-import-results input\" \n" +
                "\t},\n" +
                "\t\n" +
                "\t\"issues\": [\n");


        boolean first = true;
        for (int iDefect = 0; iDefect < defects.size(); iDefect++) {
          String json = defects.get(iDefect).getJson();
          if (json != null) {
            if (!first) writer.write(",\n");
            writer.write("\t\t");
            writer.write(json);
            first = false;
          }
          iDefect++;

        }

        // End of JSON segments for defects and beginning of the array for source files.
        writer.write("\n\t],\n" +
                "\t\"sources\": [\n");

        String json = defects.stream()
                .map(defect -> defect.funcMetrics.getPathname())
                .distinct()
                .map(path -> "\t\t{ \"file\": \"" + path + "\", \"encoding\": \"ASCII\" }")
                .collect(joining(",\n"));

        writer.write(json);

        writer.write("\n\t]\n}\n");

        writer.close();

      } catch (IOException e) {
        e.printStackTrace();
      }

    }
  }
}
