package com.synopsys.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.synopsys.metrics.checkers.ModuleHasTooManyFiles;
import com.synopsys.metrics.checkers.ModuleHasTooManyFunctions;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * This class is used to save the configuration for an analysis. Such a configuration can be read from the command line
 * options or from a JSON file.
 */
public class Config {

  protected Logger _logger = LogManager.getLogger(Config.class);

  // ------------------------------------------------------------------------------------------------------------------
  // Configuration parameters.
  // ------------------------------------------------------------------------------------------------------------------

  protected String idir = null;
  protected String cfgDirPath = null;
  protected String name = "";
  protected String description = "";
  protected String outputTag = "";
  protected String stripPath = "";
  protected String reportFile = "cov-metrics-report.json"; /* Default name for the Coverity defects file. */
  protected String metricsFileName = null;

  private Options options = null;

  /**
   * The list of filters defined at the configuration level to include or exclude some function prefix for checking.
   */
  private List<Function<Measurable, Boolean>> exclusionFilters = new ArrayList<>();

  /**
   * The list of enabled checkers by the current configuration.
   */
  public List<Checker> enabledCheckers = new ArrayList<>();

  /**
   * The list of all available checkers (and not yet enabled).
   */
  public List<Checker> availableCheckers = new ArrayList<>();

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
      _logger.error("Initialization of configuration failed from command line");
    }
  }

  /**
   * Constructor reading configuration from a JSON file.
   */
  public Config(String jsonFileName) {
    boolean status = jsonLoad(jsonFileName);
    if (!status) {
      _logger.error("Initialization of configuration failed from file {}", jsonFileName);
    }
  }

  protected Options getOptions() {
    if (options == null) {

      // TODO Move the definition of options string in static constant string.
      // to make sure this is compliant in defining CLI, reading CLI and parsing JSON.

      options = new Options();

      options.addOption("h", "help", false, "display help message");
      options.addOption("v", "verbose", false, "Run verbosely");
      options.addOption("g", "debug", false, "Run in debug mode");

      options.addOption(Option.builder().required(false).longOpt("exclude-pathname-regex").numberOfArgs(1)
              .desc("Exclude all functions defined in file with pathname matching a regex").build());

      options.addOption(Option.builder().required(false).longOpt("strip-path").numberOfArgs(1)
              .desc("Remove prefix in the pathname of analyzed files.").build());

      options.addOption(Option.builder("cf").required(false).longOpt("config-file").numberOfArgs(1)
              .desc("Specify the JSON configuration file").build());

      options.addOption(Option.builder().required(false).longOpt("dir").numberOfArgs(1)
              .desc("Specify the Coverity intermediate directory").build());

      options.addOption(Option.builder().required(false).longOpt("metrics").numberOfArgs(1)
              .desc("Specify the Coverity intermediate directory").build());

      options.addOption(Option.builder("cd").required(false).longOpt("config-dir").numberOfArgs(1)
              .desc("Specify the main configuration directory").build());

      options.addOption(Option.builder().required(false).longOpt("output-tag").numberOfArgs(1)
              .desc("Specify the Coverity output in the intermediate directory").build());

      options.addOption(Option.builder("D").longOpt("overwrite").numberOfArgs(2).valueSeparator('=')
              .desc("Overwrite value for given JSON property").build());

      options.addOption(Option.builder().required(false).longOpt("all").desc("Enable all known checkers").build());

      options.addOption(Option.builder("co").longOpt("checker-option").numberOfArgs(1)
              .desc("Overwrite checker metric threshold CHECKER:METRIC:THRESHOLD").build());

      options.addOption(
              Option.builder("ec").longOpt("enable-checker").numberOfArgs(1).desc("Enable checker metric").build());

      options.addOption(Option.builder("o").longOpt("output").numberOfArgs(1)
              .desc("Change the outputfile for the JSON defect report").build());

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

  public String getResource(String path, String resource) {
    String result = null;
    URI uri = null;
    try {
      URL url = Config.class.getResource(path);
      uri = (url != null) ? url.toURI() : null;
    } catch (URISyntaxException e) {
      _logger.error("Unable to create URI from Jar {}", e.getMessage());
    }

    if (uri == null) {
      _logger.error("Unable to initialize configuration without a config directory specified.");
    } else {

      //
      // Load the resource from the executable JAR
      //
      if (uri.getScheme().contains("jar")) {

        _logger.debug("Searching for resource {} in Jar", resource);

        URL jar = Config.class.getProtectionDomain().getCodeSource().getLocation();
        Path jarFile = Paths.get(jar.toString().substring("file:".length()));

        try (FileSystem fs = FileSystems.newFileSystem(jarFile, null);
             DirectoryStream<Path> directoryStream = Files.newDirectoryStream(fs.getPath(path))) {

          HashMap<String, InputStream> allStreams = new HashMap<>();
          for (Path p : directoryStream) {
            InputStream is = Config.class.getResourceAsStream(p.toString());
            if (is != null) {
              allStreams.put(p.getFileName().toString(), is);
            } else {
              _logger.debug("Unable to create input stream for path {}", p.toString());
            }
          }

          for (HashMap.Entry<String, InputStream> entry : allStreams.entrySet()) {
            if (entry.getKey().equals(resource)) {
              _logger.debug("Resource {} found in Jar", resource);
              result = IOUtils.toString(entry.getValue(), StandardCharsets.UTF_8);
            }
          }

        } catch (IOException e) {
          _logger.error("Unable to load checker definition from Jar {}", e.getLocalizedMessage());
          e.printStackTrace();

        }

      }

      //
      // Load the checkers from a FS directory (classpath as for running in the IDE)
      //
      else {
        _logger.debug("Searching for resource '{}' in file system", resource);
        try {
          URL url = Config.class.getResource(path);
          if (url == null) {
            _logger.error("Unable to initialize configuration without a config directory specified.");
          } else {
            File dir = new File(url.toURI());
            File[] listFiles = dir.listFiles();
            if (listFiles != null) {
              for (File nextFile : listFiles) {
                if (nextFile.getPath().endsWith(path + "/" + resource)) {
                  _logger.debug("Resource {} found in File System", resource);
                  result = FileUtils.readFileToString(nextFile, "UTF8");
                }
              }
            }
          }
        } catch (IOException | URISyntaxException e) {
          e.printStackTrace();
        }
      }
    }
    return result;
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * After being loaded, a configuration should be initialized.
   */
  public boolean init() {
    boolean result = true;

    _logger.info("Initializing configuration {}", getName());

    _logger.info("Clearing list of {} enabled checkers.", enabledCheckers.size());
    enabledCheckers.clear();

    _logger.info("Clearing list of {} available checkers.", availableCheckers.size());
    availableCheckers.clear();

    //
    // Create a hard coded checker for METRICS.MODULE_HAS_TOO_MANY_FUNCTIONS
    //
    {
      ModuleHasTooManyFunctions checker = new ModuleHasTooManyFunctions();

      String tmplDefectFile = checker.getJsonDefectTemplateFilename();
      String tmplDefectJson = getResource("/checkers", tmplDefectFile);
      checker.setJsonDefectTemplate(tmplDefectJson);

      String tmplDefectEventFile = checker.getJsonDefectEventTemplateFilename();
      String tmplDefectEventJson = getResource("/checkers", tmplDefectEventFile);
      checker.setJsonDefectEventTemplate(tmplDefectEventJson);

      if (checker.isValid())
        availableCheckers.add(checker);
    }

    //
    // Create a hard coded checker for METRICS.MODULE_HAS_TOO_MANY_FILES
    //
    {
      ModuleHasTooManyFiles checker = new ModuleHasTooManyFiles();

      String tmplDefectFile = checker.getJsonDefectTemplateFilename();
      String tmplDefectJson = getResource("/checkers", tmplDefectFile);
      checker.setJsonDefectTemplate(tmplDefectJson);

      String tmplDefectEventFile = checker.getJsonDefectEventTemplateFilename();
      String tmplDefectEventJson = getResource("/checkers", tmplDefectEventFile);
      checker.setJsonDefectEventTemplate(tmplDefectEventJson);

      if (checker.isValid())
        availableCheckers.add(checker);
    }

    //
    // A Configuration can be initialized from a FileSystem directory where all available
    // checker definitions might be found in files METRICS*.json
    //
    if (getConfigDir() != null) {
      File cfgDir = new File(getConfigDir());
      if (cfgDir.isDirectory()) {
        _logger.info("Loading  list of known checkers from {}.", getConfigDir());
        WildcardFileFilter filter = new WildcardFileFilter("METRICS.*.json");
        Iterator<File> allCheckerFiles = FileUtils.iterateFiles(cfgDir, filter, DirectoryFileFilter.INSTANCE);
        while (allCheckerFiles.hasNext()) {
          File checkerFile = allCheckerFiles.next();
          Checker checker = new Checker(checkerFile);
          if (checker.isValid()) {
            availableCheckers.add(checker);

            {
              String templateDir = checkerFile.getParent();
              String templateFile = checker.getJsonDefectTemplateFilename();
              File f = new File(templateDir + File.separatorChar + templateFile);
              String template = "";
              if (f.exists()) {
                try {
                  template = FileUtils.readFileToString(f, "UTF8");
                } catch (IOException e) {
                  e.printStackTrace();
                }
                checker.setJsonDefectTemplate(template);
              }
            }

            {
              String templateDir = checkerFile.getParent();
              String templateFile = checker.getJsonDefectEventTemplateFilename();
              File f = new File(templateDir + File.separatorChar + templateFile);
              if (f.exists()) {
                String template = "";
                try {
                  template = FileUtils.readFileToString(f, "UTF8");
                } catch (IOException e) {
                  e.printStackTrace();
                }
                checker.setJsonDefectEventTemplate(template);
              }
            }

          } else {
            _logger.error("Unable to load checker from file {}.", checkerFile.getAbsolutePath());
            result = false;
          }
        }
      } else {
        _logger.warn("No configuration directory found at {}", cfgDirPath);
      }
    }

    //
    // A configuration can also be initialized from the resources embedded in the application
    // Jar file. This is a bit more tricky because the way resources can be accessed at
    // runtime depends if we are debugging from the IDE or running from a packaged Jar file.
    //

    {
      String folderPath = "/checkers";
      URI uri = null;
      try {
        URL url = Config.class.getResource(folderPath);
        uri = (url != null) ? url.toURI() : null;
      } catch (URISyntaxException e) {
        _logger.error("Unable to create URI from Jar {}", e.getMessage());
      }

      if (uri == null) {
        _logger.error("Unable to initialize configuration without a config directory specified.");
        result = false;
      } else {

        //
        // Load the checkers from the executable JAR
        //
        if (uri.getScheme().contains("jar")) {
          URL jar = Config.class.getProtectionDomain().getCodeSource().getLocation();
          Path jarFile = Paths.get(jar.toString().substring("file:".length()));
          try (FileSystem fs = FileSystems.newFileSystem(jarFile, null);
               DirectoryStream<Path> directoryStream = Files.newDirectoryStream(fs.getPath(folderPath))) {

            HashMap<String, InputStream> allStreams = new HashMap<>();
            for (Path p : directoryStream) {
              InputStream is = Config.class.getResourceAsStream(p.toString());
              if (is != null) {
                allStreams.put(p.getFileName().toString(), is);
              } else {
                _logger.debug("Unable to create input stream for path {}", p.toString());
              }
            }

            for (Map.Entry<String, InputStream> entry : allStreams.entrySet()) {
              if (entry.getKey().endsWith(".json")) {
                Checker checker = new Checker(entry.getValue());
                if (checker.isValid()) {
                  {
                    String templateFile = checker.getJsonDefectTemplateFilename();
                    for (Map.Entry<String, InputStream> entry2 : allStreams.entrySet()) {
                      if (entry2.getKey().endsWith(templateFile)) {
                        String template = IOUtils.toString(entry2.getValue(), StandardCharsets.UTF_8);
                        if ((template != null) && !template.isEmpty()) {
                          checker.setJsonDefectTemplate(template);
                          availableCheckers.add(checker);
                        }
                      }
                    }
                  }
                  {
                    {
                      String templateFile = checker.getJsonDefectEventTemplateFilename();
                      for (Map.Entry<String, InputStream> entry2 : allStreams.entrySet()) {
                        if (entry2.getKey().endsWith(templateFile)) {
                          String template = IOUtils.toString(entry2.getValue(), StandardCharsets.UTF_8);
                          if ((template != null) && !template.isEmpty()) {
                            checker.setJsonDefectEventTemplate(template);
                            availableCheckers.add(checker);
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

          } catch (IOException e) {
            e.printStackTrace();
            _logger.error("Unable to load checker definition from Jar {}", e.getLocalizedMessage());
          }

        }

        //
        // Load the checkers from a FS directory (classpath as for running in the IDE)
        //
        else {
          _logger.info("Loading  list of known checkers from internal definitions");
          try {
            URL url = Config.class.getResource("/checkers");
            if (url == null) {
              _logger.error("Unable to initialize configuration without a config directory specified.");
              result = false;
            } else {
              File dir = new File(url.toURI());
              File[] listFiles = dir.listFiles();
              if (listFiles != null) {
                for (File nextFile : listFiles) {
                  if (nextFile.getPath().endsWith(".json")) {
                    Checker checker = new Checker(nextFile);
                    if (checker.isValid()) {
                      _logger.info("Loading checker definition from {}", nextFile.getName());
                      availableCheckers.add(checker);
                      {
                        String templateFile = checker.getJsonDefectTemplateFilename();
                        URL tURL = Config.class.getResource("/checkers/" + templateFile);
                        if (tURL != null) {
                          String template = "";
                          try {
                            template = IOUtils.toString(tURL, StandardCharsets.UTF_8);
                          } catch (IOException e) {
                            e.printStackTrace();
                          }
                          checker.setJsonDefectTemplate(template);
                        } else {
                          _logger.error("Template for defect not found at {}", templateFile);
                        }
                      }

                      {
                        String templateFile = checker.getJsonDefectEventTemplateFilename();
                        URL tURL = Config.class.getResource("/checkers/" + templateFile);
                        if (tURL != null) {
                          String template = "";
                          try {
                            template = IOUtils.toString(tURL, StandardCharsets.UTF_8);
                          } catch (IOException e) {
                            e.printStackTrace();
                          }
                          checker.setJsonDefectEventTemplate(template);
                        } else {
                          _logger.error("Template for event defect not found at {}", templateFile);
                        }
                      }

                    } else {
                      _logger.error("Unable to load checker from file {}", nextFile.getAbsolutePath());
                      result = false;
                    }
                  }
                }
              }
            }
          } catch (URISyntaxException e) {
            result = false;
          }
        }
      }
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
    return getIDIR() + "/output" + getOutputTag();
  }

  public String getReportFile() {
    return reportFile;
  }

  public void setFuntionsFileName(String v) {
    metricsFileName = v;
  }

  public String getFunctionsFileName() {
    if (metricsFileName == null) {
      return getOutputDir() + "/FUNCTION.metrics.xml.gz";
    }
    return metricsFileName;
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

  /**
   * Returns the path prefix to strip as configured by users.
   */
  public String getStripPath() {
    return stripPath;
  }

  /**
   * Change the path prefix to strip from function's file name.
   */
  public void setStripPath(String stripPath) {
    this.stripPath = stripPath;
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * Enable all available checkers.
   */
  public boolean enableAllCheckers() {
    boolean result = true;

    while (result && availableCheckers.size() > 0) {
      result = enableChecker(availableCheckers.get(0).getName()) != null;
    }

    return result;
  }

  public boolean isValidCheckerName(String name) {
    return name != null && !name.isEmpty();
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * Move the specified checker to the list of enabled checkers. A checker with the given name should be listed in the
   * available checkers and will then be moved to the list of the enabled checkers.
   *
   * @param name The name of the available checker to search for
   * @return The now enabled checker the name or null if either already enabled or not available.
   */
  public Checker enableChecker(String name) {

    if (!isValidCheckerName(name)) {
      throw new IllegalArgumentException("Invalid checker name to enable : '" + name + "'");
    }

    Checker checker = getAvailableChecker(name);
    if ((checker != null) && (checker.isValid())) {
      enabledCheckers.add(checker);
      availableCheckers.remove(checker);
      _logger.info("Adding checker {}", checker);
    } else {
      _logger.error("Invalid checker submitted to configuration {}", checker);
      throw new IllegalArgumentException("Checker to enabled is unknown : " + name);
    }

    return checker;
  }

  public boolean isEnabled(String checkerName) {
    if (isValidCheckerName(name)) {
      throw new IllegalArgumentException("Checker's name must be non null and not empty");
    }
    return enabledCheckers().filter(c -> checkerName.equals(c.getName())).findFirst().orElse(null) != null;
  }

  public Stream<Checker> enabledCheckers() {
    return enabledCheckers.stream();
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * Returns an available checker identified by the given name if there's one.
   *
   * @param name The name of the available checker to search for
   * @return The available checker matching the name or null.
   */
  public Checker getAvailableChecker(String name) {
    if (!isValidCheckerName(name)) {
      throw new IllegalArgumentException("Checker's name must be non null and not empty");
    }
    Checker result = null;
    if (name != null)
      result = availableCheckers.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
    return result;
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * Returns an enabled checker identified by the given name, if there's one.
   *
   * @param name The name of the enabled checker to search for
   * @return The enabled checker matching the name or null.
   */
  public Checker getEnabledChecker(String name) {
    Checker result = null;
    if (name != null)
      result = enabledCheckers.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
    return result;
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * Adds an exclusion or inclusion file path filter with the given regular expression.
   *
   * @param regex    The regular expression that should match the entire pathname for the filter to apply
   * @param excluded If true then this is an exclusion filter, ohterwise it's an inclusion filter
   */
  public boolean addFileFilter(String regex, boolean excluded) {
    boolean result = regex != null;
    if (result) {
      try {
        Pattern pattern = Pattern.compile(regex);

        // A filter returns TRUE if the fnmetrics is excluded

        Function<Measurable, Boolean> filter = (fnmetrics) -> {
          if (fnmetrics != null) {
            String pathname = fnmetrics.getSourcesLabel();
            Matcher matcher = pattern.matcher(pathname);
            return (excluded) ? (matcher.matches()) : !matcher.matches();
          }
          return false;
        };

        exclusionFilters.add(filter);

        _logger.info("Adding filter, all functions defined in files matching " + regex + " are ignored");
      } catch (PatternSyntaxException e) {
        _logger.error("Failed to add filter, regex pattern invalid " + regex + ":" + e.getMessage());
        result = false;
      }
    }
    return result;
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * Applies all filters at the global level to the given pathname and returns true of the file is to be processed.
   */
  public boolean filter(Measurable metrics) {
    boolean result = !exclusionFilters.stream().anyMatch(filter -> filter.apply(metrics));
    if (!result) {
      _logger.debug("Functions metrics from " + metrics.getSourcesLabel() + " are filtered out.");
    }
    return result;
  }

  //
  // ******************************************************************************************************************
  //

  /**
   * Build a stream of the defects generated by the enabled and not filtered out checkers on the given function.
   */
  public Stream<Defect> check(Measurable metrics) {

    return enabledCheckers.stream() // List all enabled checkers
            .filter(checker -> checker.canCheck(metrics)) // Filter out checkers that doesn't apply to this function
            .map(checker -> checker.check(metrics)) // Check function's metrics with current
            .filter(defect -> defect != null); // Filter out null defects
  }

  //
  // ******************************************************************************************************************
  //

  public boolean isValid() {
    boolean result = true;

    _logger.info("Checking current configuration ...");

    // ----------------------------------------
    // Validate the Intermediate Directory
    // ----------------------------------------

    if (idir == null) {
      // result = false;
      _logger.warn("No Coverity analysis intermediate directory defined.");
    } else {
      File dir = new File(idir);
      if (!dir.isDirectory()) {
        result = false;
        _logger.error("Missing intermediate directory at '{}'", idir);
      } else if (!dir.canWrite()) {
        result = false;
        _logger.error("Write access permissions denied for intermediate directory at '{}'", idir);
      }
    }

    {
      String targetPath = getOutputDir();
      if (targetPath != null) {
        File outputDir = new File(targetPath);
        if (!outputDir.isDirectory()) {
          _logger.warn("Coverity output directory not found at '{}'", targetPath);
          // result = false;
        } else if (!outputDir.canWrite()) {
          _logger.error("Write access permission denied on Coverity output directory at '{}'", targetPath);
          result = false;
        }
      } else {
        _logger.error("Coverity output directory not defined.");
        result = false;
      }
    }

    {
      String fnName = getFunctionsFileName();
      if (fnName != null) {
        File functions = new File(fnName);
        if (!functions.isFile()) {
          _logger.error("The provided intermediate directory doesn't contain a metrics file.");
          result = false;
        } else if (!functions.canRead()) {
          _logger.error("Access permission defined for the metrics file.");
          result = false;
        }
      } else {
        _logger.error("No function metrics file defined.");
        result = false;
      }
    }

    // ----------------------------------------
    // Validate the Enabled Checker list.
    // ----------------------------------------
    if (enabledCheckers.size() + availableCheckers.size() == 0) {
      result = false;
      _logger.error("This configuration is not valid because there's no checker defined or enabled.");
    }

    for (Checker checker : enabledCheckers) {
      if (!checker.isValid()) {
        result = false;
        _logger.error("Invalid enabled checker detected with " + checker.toString());
      }
    }

    // ----------------------------------------
    // Validate the Available Checker list.
    // ----------------------------------------
    for (Checker checker : availableCheckers) {
      if (!checker.isValid()) {
        result = false;
        _logger.error("Invalid available checker definition detected with " + checker.toString());
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

        if (line.hasOption("debug")) {
					/*
					LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
					Configuration config = ctx.getConfiguration();
					LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME); 
					loggerConfig.setLevel(Level.DEBUG);
					ctx.updateLoggers();
					*/
          Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.DEBUG);

          _logger.debug("Activating debug logs");
        }

        // ----------------------------------------------------------------
        // Set all options from the proposed configuration file
        // ----------------------------------------------------------------
        {
          if (line.hasOption("config-file")) {
            String fileName = line.getOptionValue("config-file");
            if (new File(fileName).isFile()) {
              result = jsonLoad(fileName);
              if (!result) {
                _logger.error("Incomplete on bad configuration from '{}'", fileName);
              }
            } else {
              _logger.error("Bad default configuration file (not a file): '{}'", fileName);
              result = false;
            }
          } else {
            _logger
                    .warn("No default configuration file used, all required options must be defined in the command line.");
          }
        }

        // ----------------------------------------------------------------
        // Set all options from the proposed configuration file
        // ----------------------------------------------------------------
        if (line.hasOption("config-dir")) {
          String dirName = line.getOptionValue("config-dir");
          if (new File(dirName).isDirectory()) {
            setConfigDir(dirName);
          } else {
            _logger.error("Bad  configuration directory specified (not a directory): '{}'", dirName);
            result = false;
          }
        } else {
          _logger.info("There's no custom configuration specified, using embedded configurations.");
        }

        // ----------------------------------------------------------------
        // Initialize configuration from the configuration file and folder
        // ----------------------------------------------------------------
        if (!init()) {
          _logger.error("Initialisation from configuration from JSON file failed.");
        }

        // ****************************************************************
        // From here forward, the purpose is to overwrite config params
        // ****************************************************************

        // ----------------------------------------------------------------
        // Overwriting some JSON parameters
        // ----------------------------------------------------------------
        {
          Properties props = line.getOptionProperties("overwrite");
          for (String pname : props.stringPropertyNames()) {
            if ("dir".equals(pname)) {
              setIDIR(props.getProperty(pname));
            } else if ("config".equals(pname)) {
              setConfigDir(props.getProperty(pname));
            } else if ("metrics".equals(pname)) {
              setFuntionsFileName(props.getProperty(pname));
            } else if ("output".equals(pname)) {
              setOutputTag(props.getProperty(pname));
            } else if ("name".equals(pname)) {
              setName(props.getProperty(pname));
            } else if ("description".equals(pname)) {
              setDescription(props.getProperty(pname));
            }
          }
        }

        // ----------------------------------------------------------------
        // Add exclusion file filters
        // ----------------------------------------------------------------
        {
          if (line.hasOption("exclude-pathname-regex")) {
            String list = line.getOptionValue("exclude-pathname-regex");
            String[] patterns = list.split(",");
            for (String strPattern : patterns) {
              if (!addFileFilter(strPattern, true)) {
                _logger.error("Unable to add regex pathname filter from '{}", strPattern);
              }
            }
          } else {
            _logger.info("There's no file pathname exclusion patterns.");
          }
        }

        // ----------------------------------------------------------------
        // Add strip path
        // ----------------------------------------------------------------
        {
          if (line.hasOption("strip-path")) {
            String list = line.getOptionValue("strip-path");
            setStripPath(list);
          } else {
            _logger.info("There's no prefix to strip from file pathnames.");
          }
        }

        // ----------------------------------------------------------------
        // Update the output tag in the intermediate directory
        // ----------------------------------------------------------------
        {
          if (line.hasOption("output-tag")) {
            String tag = line.getOptionValue("output-tag");
            _logger.info("Changing from the CLI option the output tag to {}", tag);
            setOutputTag(tag);
          } else {
            _logger.info("There's no output tag defined, using default output folder in intermediate directory.");
          }
        }

        // ----------------------------------------------------------------
        // Enabling all checkers.
        // ----------------------------------------------------------------
        {
          if (line.hasOption("all")) {
            if (availableCheckers.isEmpty()) {
              _logger.error("There's no available checkers. Check that the configuration directory.");
              result = false;
            } else {
              if (!enableAllCheckers()) {
                result = false;
                _logger.error("Enabling all checkers failed.");
              }
            }
          }
        }

        // Overwriting Checkers options - this enable the checker
        {
          String[] checkerNameList = line.getOptionValues("enable-checker");
          if (checkerNameList != null) {
            for (String checkerName : checkerNameList) {
              Checker checker = enableChecker(checkerName);
              if (checker == null) {
                _logger.error("Unable to enable checker: {}", checkerName);
                result = false;
              } else {
                _logger.info("Manually activating checker: {}", checker);
              }
            }
          }
        }

        // Overwriting Checkers options - this enable the checker
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
                  if (checker == null) {
                    _logger.info("Enabling checker for configuring it.");
                    checker = enableChecker(checkerName);
                  }
                  if (checker != null) {
                    if (checker.hasMetric(metricName)) {
                      checker.setThreshold(metricName, threshold);
                      _logger.info("Threshold for metric {} in checker {} changed to {}.", metricName, checkerName,
                              threshold);
                    } else {
                      _logger.error("Unknown metric name {} for checker {}", metricName, checkerName);
                      result = false;
                    }
                  } else {
                    _logger.error("Unable to find or enable the checker {}", checkerName);
                    result = false;
                  }
                } catch (NumberFormatException e) {
                  _logger.error("Unable to parse threshold for checker {} and metric {}", checkerName, metricName);
                  result = false;
                }
              }
            }
          }
        }

        {
          if (!line.hasOption("disable-default") && !line.hasOption("enable-checker")) {
            _logger.info("No specific checker enabled and no disable all option, enabling all checkers.");
            if (!enableAllCheckers()) {
              result = false;
            }
          }
        }

        // ----------------------------------------------------------------
        // Updating the location of the Coverity intermediate dir
        // ----------------------------------------------------------------
        if (line.hasOption("dir")) {
          String value = line.getOptionValue("dir");
          if (new File(value).isDirectory()) {
            setIDIR(value);
          } else {
            _logger.error("Bad intermediate directory specified (not a directory) : '{}'", value);
            result = false;
          }
        }

        if (line.hasOption("metrics")) {
          String value = line.getOptionValue("metrics");
          if (new File(value).isFile()) {
            setFuntionsFileName(value);
          } else {
            _logger.error("Bad metric fie name (not found) : '{}'", value);
            result = false;
          }
        }

        // ----------------------------------------------------------------
        // Updating the location of the Coverity intermediate dir
        // ----------------------------------------------------------------
        if (line.hasOption("output")) {
          reportFile = line.getOptionValue("output");
        } else {
          _logger.warn("No output file specified for the report,using default {}", reportFile);
        }

      } catch (ParseException exp) {
        // oops, something went wrong
        _logger.error("Parsing failed.  Reason: {}", exp.getMessage());
        result = false;
      }
    } else {
      _logger.error("No command line provided.");
      System.err.println(getStandardBanner());
      System.err.println("Use --help for usage information.");
      result = true;
      return true; // Escape
    }
    return result;
  }

  //
  // ******************************************************************************************************************
  //

  public boolean jsonLoad(String jsonFileName) {
    boolean result = (jsonFileName != null);
    if (result) {
      File file = new File(jsonFileName);
      result = file.isFile();
      if (result) {
        JsonNode root = Utils.getJsonNodeFromFile(jsonFileName);
        if (root != null) {
          _logger.info("Start loading configuration from {}", jsonFileName);

          // Load config name
          Utils.getFieldAsText(root, "name", "", this::setName);

          // Load config description
          Utils.getFieldAsText(root, "description", "", this::setDescription);

          // Load config dir (with all defined checkers)
          Utils.getFieldAsText(root, "config", "./config/", this::setConfigDir);

          // Load Coverity's intermediate directory location
          Utils.getFieldAsText(root, "dir", "", this::setIDIR);

          // Load Coverity's output dir in intermediate directory location
          Utils.getFieldAsText(root, "output", "", this::setOutputTag);

          Utils.getFieldAsText(root, "strip-path", "", this::setStripPath);

          // Add a new exclusion filter for each pattern (delimited by coma)
          Utils.getFieldAsStrArray(root, "excluded-files", null, filter -> addFileFilter(filter, true));

          // Before loading the checker configurations we must load the list of available checkers from config dir.
          if (init()) {

            JsonNode checkersNode = root.get("checkers");
            if (checkersNode.isArray()) {
              for (JsonNode node : checkersNode) {
                String checkerName = Utils.getFieldAsText(node, "name", "", null);
                _logger.info("Attempting to enable checker {}", checkerName);
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
                          _logger.info("Changing default threshold for {} to {}.", metricName, value);
                          checker.setThreshold(metricName, value);
                        } else {
                          result = false;
                          _logger.error("Invalid value for metric {} = {}", metricName, value);
                        }
                      } else {
                        result = false;
                        _logger.error("Undefined metric name.");
                      }
                    }
                  } else {
                    _logger.warn("Missing thresholds, using default ones for checker {}", checkerName);
                  }
                } else {
                  _logger.error("Unknown checker {}", checkerName);
                  result = false;
                }
                if (result) {
                  _logger.info("Checker successfully enabled and configured: {}", checker);
                }
              }
            } else {
              _logger.error("Bad syntax, missing list of enabled checkers in tag checkers");
              result = false;
            }
          } else {
            _logger.error("Unable to load checker configuration.");
            result = false;
          }
        }
      } else {
        // The given filename doesn't point to a file
        _logger.error("Missing file at {}", jsonFileName);
        result = false;
      }
    } else {
      // No filename is given
      _logger.error("No file provided");
      result = false;
    }

    return result;
  }

}
