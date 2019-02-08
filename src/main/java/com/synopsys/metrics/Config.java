package com.synopsys.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.*;
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

    /**
     * The list of filters defined at the configuration level to include or exclude some function prefix for checking.
     */
    private List<Function<FuncMetrics, Boolean>> exclusionFilters = new ArrayList<>();

    /**
     * The list of enabled chechets by the current configuration.
     */
    public List<Checker> enabledCheckers = new ArrayList<Checker>();

    /**
     * The list of all available checkers (and not yet enabled).
     */
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
                    .desc("Exclude all functions defined in file with pathname matching a regex")
                    .build());

            options.addOption(Option.builder()
                    .required(false)
                    .longOpt("strip-path")
                    .numberOfArgs(1)
                    .desc("Remove prefix in the pathname of analyzed files.")
                    .build());

            options.addOption(Option.builder("cf")
                    .required(false)
                    .longOpt("config-file")
                    .numberOfArgs(1)
                    .desc("Specify the JSON configuration file")
                    .build());

            options.addOption(Option.builder()
                    .required(false)
                    .longOpt("dir")
                    .numberOfArgs(1)
                    .desc("Specify the Coverity intermediate directory")
                    .build());

            options.addOption(Option.builder("cd")
                    .required(false)
                    .longOpt("config-dir")
                    .numberOfArgs(1)
                    .desc("Specify the main configuration directory")
                    .build());

            options.addOption(Option.builder()
                    .required(false)
                    .longOpt("output-tag")
                    .numberOfArgs(1)
                    .desc("Specify the Coverity output in the intermediate directory")
                    .build());

            options.addOption(Option.builder("D")
                    .longOpt("overwrite")
                    .numberOfArgs(2)
                    .valueSeparator('=')
                    .desc("Overwrite value for given JSON property")
                    .build());


            options.addOption(Option.builder()
                    .required(false)
                    .longOpt("all")
                    .desc("Enable all known checkers")
                    .build());


            options.addOption(Option.builder("co")
                    .longOpt("checker-option")
                    .numberOfArgs(1)
                    .desc("Overwrite checker metric threshold CHECKER:METRIC:THRESHOLD")
                    .build());

            options.addOption(Option.builder("ec")
                    .longOpt("enable-checker")
                    .numberOfArgs(1)
                    .desc("Enable checker metric")
                    .build());

            options.addOption(Option.builder("ot")
                    .longOpt("output")
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


        if (getConfigDir() != null) {
            logger.info("Loading  list of known checkers from " + getConfigDir());
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
        }

        // Loading the checker definitions from the resources embedded into the Jar.

        else {
            String folderPath = "/checkers";
            URI uri = null;
            try {
                uri = Config.class.getResource(folderPath).toURI();
            } catch (URISyntaxException e) {
                logger.error("Unable to create URI from Jar " + e.getMessage());
            } catch (NullPointerException e) {
                logger.error("Unable to create URI from Jar " + e.getMessage());
            }

            if (uri == null) {
                logger.error("Unable to initialize configuration without a config directory specified.");
                result = false;
            } else {

                //
                // Load the checkers from the executable JAR
                //
                if (uri.getScheme().contains("jar")) {
                    try {
                        URL jar = Config.class.getProtectionDomain().getCodeSource().getLocation();
                        Path jarFile = Paths.get(jar.toString().substring("file:".length()));
                        FileSystem fs = FileSystems.newFileSystem(jarFile, null);
                        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(fs.getPath(folderPath));
                        for (Path p : directoryStream) {
                            InputStream is = Config.class.getResourceAsStream(p.toString());
                            Checker checker = new Checker(is);
                            if (checker.isValid()) {
                                logger.info("Loading checker definition from " + p.getFileName());
                                availableCheckers.add(checker);
                            } else {
                                logger.error("Unable to load checker from file " + p.getFileName());
                                result = false;
                            }
                        }
                    } catch (IOException e) {
                        logger.error("Unable to load checker definition from Jar" + e.getLocalizedMessage());
                    }
                }

                //
                // Load the checkers from a FS directory (classpath as for running in the IDE)
                //
                else {
                    logger.info("Loading  list of known checkers from internal definitions (embedded in jar)");
                    try {
                        URL url = Config.class.getResource("/checkers");
                        if (url == null) {
                            logger.error("Unable to initialize configuration without a config directory specified.");
                            result = false;
                        } else {
                            File dir = new File(url.toURI());
                            for (File nextFile : dir.listFiles()) {
                                Checker checker = new Checker(nextFile);
                                if (checker.isValid()) {
                                    logger.info("Loading checker definition from " + nextFile.getName());
                                    availableCheckers.add(checker);
                                } else {
                                    logger.error("Unable to load checker from file " + nextFile.getAbsolutePath());
                                    result = false;
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

    //
    // ******************************************************************************************************************
    //

    /**
     * Move the specified checker to the list of enabled checkers. A checker with the given name should be
     * listed in the available checkers and will then be moved to the list of the enabled checkers.
     *
     * @param name The name of the available  checker to search for
     * @return The now enabled checker  the name or null if either already enabled or not available.
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


    //
    // ******************************************************************************************************************
    //

    /**
     * Returns an available checker identified by the given name if there's one.
     *
     * @param name The name of the available  checker to search for
     * @return The available checker matching the name or null.
     */
    public Checker getAvailableChecker(String name) {
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

                Function<FuncMetrics, Boolean> filter = (fnmetrics) -> {
                    if (fnmetrics != null) {
                        String pathname = fnmetrics.getPathname();
                        Matcher matcher = pattern.matcher(pathname);
                        return (excluded) ? (matcher.matches()) : !matcher.matches();
                    }
                    return false;
                };

                exclusionFilters.add(filter);

                logger.info("Adding filter, all functions defined in files matching " + regex + " are ignored");
            } catch (PatternSyntaxException e) {
                logger.error("Failed to add filter, regex pattern invalid " + regex + ":" + e.getMessage());
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
    public boolean filter(FuncMetrics fnMetrics) {
        boolean result = !exclusionFilters.stream().anyMatch(filter -> filter.apply(fnMetrics));
        if (!result) {
            logger.debug("Functions metrics from " + fnMetrics.getPathname() + " are filtered out.");
        }
        return result;
    }

    //
    // ******************************************************************************************************************
    //

    /**
     * Build a stream of the defects generated by the enabled and not filtered out checkers on the given function.
     */
    public Stream<Defect> check(FuncMetrics fnMetrics) {

        return enabledCheckers.stream() // List all enabled checkers
                .filter(checker -> checker.filter(fnMetrics)) // Filter out checkers that doesn't apply to this function
                .map(checker -> checker.check(fnMetrics)) // Check function's metrics with current
                .filter(defect -> defect != null); // Filter out null defects
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
            if (!functions.isFile()) {
                logger.error("The provided intermediate directory doesn't contain a metrics file.");
                result = false;
            } else if (!functions.canRead()) {
                logger.error("Access permission defined for the metrics file.");
                result = false;
            }

        }
        // ----------------------------------------
        // Validate the Enabled Checker list.
        // ----------------------------------------
        if (enabledCheckers.size() + availableCheckers.size() == 0) {
            result = false;
            logger.error("This configuration is not valid because there's no checker defined or enabled.");
        }

        for (Checker checker : enabledCheckers) {
            if (!checker.isValid()) {
                result = false;
                logger.error("Invalid enabled checker detected with " + checker.toString());
            }
        }

        // ----------------------------------------
        // Validate the Available Checker list.
        // ----------------------------------------
        for (Checker checker : availableCheckers) {
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

                // ----------------------------------------------------------------
                // Set all options from the proposed configuration file
                // ----------------------------------------------------------------
                {
                    if (line.hasOption("config-file")) {
                        String fileName = line.getOptionValue("config-file");
                        if (new File(fileName).isFile()) {
                            result = jsonLoad(fileName);
                            if (!result) {
                                logger.error("Incomplete on bad configuration from '" + fileName + "'");
                            }
                        } else {
                            logger.error("Bad default configuration file (not a file): '" + fileName + "'");
                        }
                    } else {
                        logger.warn("No default configuration file used, all required options must be defined in the command line.");
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
                        logger.error("Bad  configuration directory specified (not a directory): '" + dirName + "'");
                    }
                } else {
                    logger.info("There's no custom configuration specified, using embedded configurations.");
                }

                // ----------------------------------------------------------------
                // Initialize configuration from the configuration file and folder
                // ----------------------------------------------------------------
                if (!init()) {
                    logger.error("Initialisation from configuration from JSON file failed.");
                }

                // ****************************************************************
                // From here forward, the purpose is to overwrite config params
                // ****************************************************************

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
                // Add exclusion file filters
                // ----------------------------------------------------------------
                {
                    if (line.hasOption("exclude-pathname-regex")) {
                        String list = line.getOptionValue("exclude-pathname-regex");
                        String[] patterns = list.split(",");
                        for (String strPattern : patterns) {
                            if (!addFileFilter(strPattern, true)) {
                                logger.error("Unable to add regex pathname filter from '" + strPattern + "'");
                            }
                        }
                    } else {
                        logger.info("There's file pathname exclusion patterns.");
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
                        logger.info("There's file pathname exclusion patterns.");
                    }
                }


                // ----------------------------------------------------------------
                // Update the output tag in the intermediate directory
                // ----------------------------------------------------------------
                {
                    if (line.hasOption("output-tag")) {
                        String tag = line.getOptionValue("output-tag");
                        logger.info("Changing from the CLI option the output tag to " + tag);
                        setOutputTag(tag);
                    } else {
                        logger.info("There's no output tag defined, using default output folder in intermediate directory.");
                    }
                }

                // ----------------------------------------------------------------
                // Enabling all checkers.
                // ----------------------------------------------------------------
                {
                    if (line.hasOption("all")) {
                        if (availableCheckers.size() == 0) {
                            logger.error("There's no available checkers. Check that the configuration directory.");
                            result = false;
                        } else {
                            result = enableAllCheckers();
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
                                logger.error("Unable to enable checker: " + checkerName);
                                result = false;
                            } else {
                                logger.info("Manually activating checker: " + checker.toString());
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
                                        logger.info("Enabling checker for configuring it.");
                                        checker = enableChecker(checkerName);
                                    }
                                    if (checker != null) {
                                        if (checker.hasMetric(metricName)) {
                                            checker.setThreshold(metricName, threshold);
                                            logger.info("Threshold for metric " + metricName + " in checker " + checkerName + " changed to " + threshold);
                                        } else {
                                            logger.error("Unknown metric name " + metricName + " for checker " + checkerName);
                                            result = false;
                                        }
                                    } else {
                                        logger.error("Unable to find or enable the checker " + checkerName);
                                        result = false;
                                    }
                                } catch (NumberFormatException e) {
                                    logger.error("Unable to parse metric threshold for checker " + checkerName + " and metric " + metricName);
                                    result = false;
                                }
                            }
                        }
                    }
                }


                {
                    if (!line.hasOption("disable-default") && !line.hasOption("enable-checker")) {
                        logger.info("No specific checker enabled and no disable all option, enabling all checkers.");
                        result = enableAllCheckers();
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
                        logger.error("Bad intermediate directory specified (not a directory) : '" + value + "'");
                        result = false;
                    }
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

                    // Add a new exclusion filter for each pattern (delimited by coma)
                    Utils.getFieldAsStrArray(root, "excluded-files", null, filter -> addFileFilter(filter, true));

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

        //assert !result || isValid() : "Loaded configuration is not valid ?";
        return result;
    }

}
