package com.synopsys.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * This class is used to save the configuration for an analysis. Such a configuration can be
 * read from the command line options or from a JSON file.
 */
public class Config {

  protected Logger logger = LogManager.getLogger(Config.class);

  // ------------------------------------------------------------------------------------------------------------------
  // Configuration parameters.
  // ------------------------------------------------------------------------------------------------------------------

  protected String idir = null;
  protected String cfgDirPath = null;
  protected String name = "";
  protected String description = "";
  protected String outputTag = "";
  protected String stripPath = "";

  private Options options = null;

  /** The list of filters defined at the configuration level to include or exclude some function prefix for checking. */
  private List<Function<FuncMetrics,Boolean>> exclusionFilters = new ArrayList<>();

  /** The list of enabled chechets by the current configuration. */
  public List<Checker> enabledCheckers = new ArrayList<Checker>();

  /** The list of all available checkers (and not yet enabled). */
  public List<Checker> availableCheckers = new ArrayList<Checker>();

  // ------------------------------------------------------------------------------------------------------------------
  // Constructors.
  // ------------------------------------------------------------------------------------------------------------------

  public Config() {


  }

  /**
   * Default constructor for an empty configuration.
   */
  public Config(String[] args) {
    boolean status = cliLoad(args);
    if (!status) {
      logger.error("Initialization of configuration failed from command line");
    }
  }

  /**
   * Constructor reading configuration from a JSON file.
   */
  public Config(String jsonFileName) {
    boolean status = jsonLoad(jsonFileName);
    if (!status) {
      logger.error("Initialization of configuration failed from file " + jsonFileName);
    }
  }


  protected Options getOptions() {
    if (options == null) {

      // TODO Move the definition of options string in static constant string.
      // to make sure this is compliant in defining CLI, reading CLI and parsing JSON.

      options = new Options();

      options.addOption("h", "help", false, "display help message");
      options.addOption("v", "verbose", false, "Run verbosely");


      options.addOption(Option.builder()
              .required(false)
              .longOpt("exclude-pathname-regex")
              .numberOfArgs(1)
              .desc("Exclude all functions defined in file with pathanme matching a regex")
              .build());

      options.addOption(Option.builder("all")
              .required(false)
              .longOpt("all")
              .desc("Enable all known checkers")
              .build());

      options.addOption(Option.builder("c")
              .required(false)
              .longOpt("config-file")
              .numberOfArgs(1)
              .desc("Specify the JSON configuration file")
              .build());

      options.addOption(Option.builder("dir")
              .required(false)
              .longOpt("dir")
              .numberOfArgs(1)
              .desc("Specify the Coverity intermediate directory")
              .build());

      options.addOption(Option.builder("D")
              .longOpt("overwrite")
              .numberOfArgs(2)
              .valueSeparator('=')
              .desc("Overwrite value for given JSON property")
              .build());

      options.addOption(Option.builder("co")
              .longOpt("checker-option")
              .numberOfArgs(1)
              .desc("Overwrite checker metric threshold CHECKER:METRIC:THRESHOLD")
              .build());

      // TODO Implement support for defining output tag from the command line.
      options.addOption(Option.builder("ot")
              .longOpt("output-tag")
              .numberOfArgs(1)
              .desc("Change the output directory (idir/output{tag}) for the analysis")
              .build());


    }
    return options;
  }

  public String getStandardBanner() {

    String result = "\n";
    result += "*******************************\n";
    result += "** Coverity Metrics Analysis **\n";
    result += "*******************************\n";
    result += "\n";
    return result;
  }

  public String getHelpBanner() {
    HelpFormatter formatter = new HelpFormatter();
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    formatter.printHelp(printWriter, 80, "cov-analysis-metrics [options] where ", "", getOptions(), 2, 1, "");
    return getStandardBanner() + "\n" + stringWriter.toString();
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * After being loaded, a configuration should be initialized.
   */
  public boolean init() {
    boolean result = true;

    logger.info("Initializing configuration " + getName());

    logger.info("Clearing list of " + enabledCheckers.size() + " enabled checkers.");
    enabledCheckers.clear();

    logger.info("Clearing list of " + availableCheckers.size() + " available checkers.");
    availableCheckers.clear();

    logger.info("Loading  list of known checkers from " + getConfigDir());

    if (getConfigDir() != null) {
      File cfgDir = new File(getConfigDir());
      if (cfgDir.isDirectory()) {
        WildcardFileFilter filter = new WildcardFileFilter("METRICS.*.json");
        Iterator<File> allCheckerFiles = FileUtils.iterateFiles(cfgDir, filter, DirectoryFileFilter.INSTANCE);
        while (allCheckerFiles.hasNext()) {
          File checkerFile = allCheckerFiles.next();
          Checker checker = new Checker(checkerFile);
          if (checker.isValid()) {
            availableCheckers.add(checker);
          } else {
            logger.error("Unable to load checker from file " + checkerFile.getAbsolutePath());
            result = false;
          }
        }
      } else {
        logger.error("No configuration directory found at " + cfgDirPath);
        result = false;
      }
    } else {
      logger.error("Unable to initialize configuration without a config directory specified.");
      result = false;
    }
    return result;
  }

  //
  // ******************************************************************************************************************
  //


  public void setIDIR(String value) {
    idir = value;
  }

  public String getIDIR() {
    return idir;
  }

  public String getOutputDir() {
    String result = getIDIR() + "/output" + getOutputTag();
    return result;
  }

  public String getFunctionsFileName() {
    return getOutputDir() + "/FUNCTION.metrics.xml.gz";
  }

  //
  // ******************************************************************************************************************
  //

  public String getConfigDir() {
    return cfgDirPath;
  }

  public void setConfigDir(String cfgDirPath) {
    this.cfgDirPath = cfgDirPath;
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

  public String getOutputTag() {
    return outputTag;
  }

  public void setOutputTag(String outputTag) {
    this.outputTag = outputTag;
  }

  //
  // ******************************************************************************************************************
  //

  public String getStripPath() {
    return stripPath;
  }

  public void setStripPath(String stripPath) {
    this.stripPath = stripPath;
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * Move the specified checker to the list of enabled checkers.
   */
  public Checker enableChecker(String name) {

    assert (name != null) && (!name.isEmpty()) : "Invalid checker name to enable: " + name;
    assert getAvailableChecker(name) != null : "No checker available with name: " + name;
    assert getEnabledChecker(name) == null : "Checker already enabled: " + name;

    Checker checker = getAvailableChecker(name);
    if ((checker != null) && (checker.isValid())) {
      enabledCheckers.add(checker);
      availableCheckers.remove(checker);
      logger.info("Adding checker " + checker);
    } else {
      logger.error("Invalid checker submitted to configuration " + checker);
      checker = null;
    }

    assert (checker == null) || getAvailableChecker(name) == null : "Enabled checker still available with name: " + name;
    assert (checker == null) || getEnabledChecker(name) != null : "Enabled checker not actually enabled: " + name;

    return checker;
  }

  public Checker getAvailableChecker(String name) {
    Checker result = null;
    if (name != null)
      result = availableCheckers.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
    return result;
  }

  public Checker getEnabledChecker(String name) {
    Checker result = null;
    if (name != null)
      result = enabledCheckers.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
    return result;
  }

  //
  // ******************************************************************************************************************
  //

  public boolean addFileFilter(String regex, boolean excluded) {
    boolean result = regex != null;
    if (result) {
      try {
        Pattern pattern = Pattern.compile(regex);

        // A filter returns TRUE if the fnmetrics is excluded

        Function<FuncMetrics,Boolean> filter = (fnmetrics) -> {
          if (fnmetrics != null) {
            String pathname = fnmetrics.getPathname();
            Matcher matcher = pattern.matcher(pathname);
            return (excluded) ? (matcher.matches()) : ! matcher.matches();
          }
          return false;
        };

        exclusionFilters.add( filter );

        logger.info("Adding filter, all functions defined in files matching " + regex + " are ignored");
      } catch (PatternSyntaxException e) {
        logger.error("Failed to add filter, regex pattern invalid " + regex + ":" + e.getMessage());
        result = false;
      }
    }
    return result;
  }


  public boolean filter(FuncMetrics fnMetrics) {
    boolean result = ! exclusionFilters.stream().anyMatch(filter -> filter.apply(fnMetrics));
    if (!result) {
      logger.debug("Functions metrics from " + fnMetrics.getPathname() + " are filtered out.");
    }
    return result;
  }

  public Stream<Defect> check(FuncMetrics fnMetrics) {
    return enabledCheckers.stream()
            .filter(checker -> checker.filter(fnMetrics))
            .map(checker -> checker.check(fnMetrics))
            .filter(defect -> defect != null);
  }

  //
  // ******************************************************************************************************************
  //

  public boolean isValid() {
    boolean result = true;

    // ----------------------------------------
    // Validate the Intermediate Directory
    // ----------------------------------------

    if (idir == null) {
      result = false;
      logger.error("No Coverity analysis intermediate directory defined.");
    } else {
      File dir = new File(idir);
      if (!dir.isDirectory()) {
        result = false;
        logger.error("Missing intermediate directory at '" + idir + "'");
      } else if (!dir.canWrite()) {
        result = false;
        logger.error("Write access permissions denied for intermediate directory at " + idir);
      }
    }

    {
      String targetPath = getOutputDir();

      File outputDir = new File(targetPath);
      if (!outputDir.isDirectory()) {
        logger.error("Coverity output directory not found at '" + targetPath + "'");
        result = false;
      } else if (!outputDir.canWrite()) {
        logger.error("Write access permission denied on Coverity output directory at '" + targetPath + "'");
        result = false;
      }
    }


    {
      File functions = new File(getFunctionsFileName());
      if (!functions.exists()) {

      } else if (!functions.canRead()) {

      }

    }
    // ----------------------------------------
    // Validate the Enabled Checker list.
    // ----------------------------------------
    if (enabledCheckers.size() + availableCheckers.size() == 0) {
      result = false;
      logger.error("There's no checker defined or enabled in this configuration ?");
    }

    for (
            Checker checker : enabledCheckers) {
      if (!checker.isValid()) {
        result = false;
        logger.error("Invalid enabled checker detected with " + checker.toString());
      }
    }

    // ----------------------------------------
    // Validate the Available Checker list.
    // ----------------------------------------
    for (
            Checker checker : availableCheckers) {
      if (!checker.isValid()) {
        result = false;
        logger.error("Invalid available checker definition detected with " + checker.toString());
      }
    }

    return result;
  }

  //
  // ******************************************************************************************************************
  //

  public boolean cliLoad(String[] args) {
    boolean result = (args != null) && (args.length > 0);
    if (result) {
      // First, check for json configuration.
      CommandLineParser parser = new DefaultParser();
      try {
        // parse the command line arguments
        CommandLine line = parser.parse(getOptions(), args);

        if (line.hasOption("help")) {
          System.err.println(getHelpBanner());
          return true;
        }

        if (line.hasOption("config-file")) {
          String fileName = line.getOptionValue("config-file");
          result = jsonLoad(fileName);
          if (result) {

            // ----------------------------------------------------------------
            // Overwriting some JSON parameters
            // ----------------------------------------------------------------
            {
              Properties props = line.getOptionProperties("overwrite");
              for (String name : props.stringPropertyNames()) {
                if ("dir".equals(name)) {
                  setIDIR(props.getProperty(name));
                } else if ("config".equals(name)) {
                  setConfigDir(props.getProperty(name));
                } else if ("output".equals(name)) {
                  setOutputTag(props.getProperty(name));
                } else if ("name".equals(name)) {
                  setName(props.getProperty(name));
                } else if ("description".equals(name)) {
                  setDescription(props.getProperty(name));
                }
              }
            }

            // ----------------------------------------------------------------
            // Enabling all checkers.
            // ----------------------------------------------------------------
            {
              if (line.hasOption("all")) {
                while  (availableCheckers.size() > 0) {
                  Checker checker = availableCheckers.get(0);
                  enableChecker(checker.getName());
                }
              }
            }

            // ----------------------------------------------------------------
            // Adding a file pathname exclusion filter
            // ----------------------------------------------------------------
            {
              if (line.hasOption("exclude-pathname-regex")) {
                String regex = line.getOptionValue("exclude-pathname-regex");
                if (addFileFilter(regex,true)) {
                  logger.info("File exclusion filter added for " + regex);
                } else {
                  logger.error("Unable to process options --exclude-pathname-regex");
                }
              }
            }
            // Overwriting Checkers options
            {
              String[] values = line.getOptionValues("checker-option");
              if (values != null) {
                for (String value : values) {
                  String[] list = value.split(":");
                  if (list.length == 3) {
                    String checkerName = list[0];
                    String metricName = list[1];
                    try {
                      Double threshold = Double.parseDouble(list[2]);
                      Checker checker = getEnabledChecker(checkerName);
                      if (checker != null) {
                        if (checker.hasMetric(metricName)) {
                          checker.setThreshold(metricName, threshold);
                          logger.info("Threshold for metric " + metricName + " in checker " + checkerName + " changed to " + threshold);
                        } else {
                          logger.error("Unknown metric name " + metricName + " for checker " + checkerName);
                          result = false;
                        }
                      }
                    } catch (NumberFormatException e) {
                      logger.error("Unable to parse metric threshold for checker " + checkerName + " and metric " + metricName);
                      result = false;
                    }
                  }
                }
              }
            }


          } else {
            logger.error("Unable to read configuration file at " + fileName);
            result = false;
          }
        } else {
          logger.error("A configuration file should be defined with option --config-file");
          result = false;
        }

        if (line.hasOption("dir")) {
          String value = line.getOptionValue("dir");
          setIDIR(value);
        }

      } catch (ParseException exp) {
        // oops, something went wrong
        logger.error("Parsing failed.  Reason: " + exp.getMessage());
        result = false;
      }
    } else {
      logger.error("No command line provided.");
      System.err.println(getStandardBanner());
      System.err.println("Use --help for usage information.");
      result = true;
      return true; // Escape
    }
    assert !result || isValid() : "Successfully loaded configuration from CLI is still invalid ?";
    return result;
  }

  //
  // ******************************************************************************************************************
  //

  public boolean jsonLoad(String jsonFileName) {
    boolean result = (jsonFileName != null);
    if (result) {
      File file = new File(jsonFileName);
      result = file.exists();
      if (result) {
        JsonNode root = Utils.getJsonNodeFromFile(jsonFileName);
        if (root != null) {
          logger.info("Start loading configuration from " + jsonFileName);

          // Load config name
          Utils.getFieldAsText(root, "name", "", s -> setName(s));

          // Load config description
          Utils.getFieldAsText(root, "description", "", s -> setDescription(s));

          // Load config dir (with all defined checkers)
          Utils.getFieldAsText(root, "config", "./config/", s -> setConfigDir(s));

          // Load Coverity's intermediate directory location
          Utils.getFieldAsText(root, "dir", "", s -> setIDIR(s));

          // Load Coverity's output dir in intermediate directory location
          Utils.getFieldAsText(root, "output", "", s -> setOutputTag(s));

          Utils.getFieldAsText(root, "strip-path", "", s -> setStripPath(s));

          Utils.getFieldAsStrArray(root,"excluded-files",null, filter -> addFileFilter(filter,true));

          // Before loading the checker configurations we must load the list of available checkers from config dir.
          if (init()) {

            JsonNode checkersNode = root.get("checkers");
            if (checkersNode.isArray()) {
              for (JsonNode node : checkersNode) {
                String checkerName = Utils.getFieldAsText(node, "name", "", null);
                logger.info("Attempting to enable checker " + checkerName);
                Checker checker = enableChecker(checkerName);
                if (checker != null) {
                  JsonNode thresholdsNode = node.get("thresholds");
                  if ((thresholdsNode != null) && (thresholdsNode.isArray())) {

                    for (JsonNode thresholdNode : thresholdsNode) {
                      String metricName = Utils.getFieldAsText(thresholdNode, "metric", null, null);
                      if (metricName != null) {
                        // TODO Check that there's no metric that can possibly generates a -1 value in Coverity.
                        double value = Utils.getFieldAsDouble(thresholdNode, "value", -1, null);
                        if (value != -1) {
                          logger.info("Changing default threshold for " + metricName + " to " + value);
                          checker.setThreshold(metricName, value);
                        } else {
                          result = false;
                          logger.error("Invalid value for metric " + metricName + " = " + value);
                        }
                      } else {
                        result = false;
                        logger.error("Undefined metric name.");
                      }
                    }
                  } else {
                    logger.warn("Missing thresholds, using default ones for checker " + checkerName);
                  }
                } else {
                  logger.error("Unknown checker " + checkerName);
                  result = false;
                }
                if (result) {
                  logger.info("Checker successfully enabled and configured: " + checker);
                }
              }
            } else {
              logger.error("Bad syntax, missing list of enabled checkers in tag checkers");
              result = false;
            }
          } else {
            logger.error("Unable to load checker configuration.");
            result = false;
          }
        }
      } else {
        // The given filename doesn't point to a file
        logger.error("Missing file at " + jsonFileName);
        result = false;
      }
    } else {
      // No filename is given
      logger.error("No file provided");
      result = false;
    }

    assert !result || isValid() : "Loaded configuration is not valid ?";
    return result;
  }

  public boolean save(String jsonFileName) {
    boolean result = (jsonFileName != null);
    if (result) {
      File file = new File(jsonFileName);
      result = file.exists();
      if (result) {
        // Warning on overwriting a configuration file
      } else {
        // All fine we can save.
        result = false;
        logger.error("Method not yet implemented.");
      }
    } else {
      // No filename is given
    }
    return result;
  }
}
