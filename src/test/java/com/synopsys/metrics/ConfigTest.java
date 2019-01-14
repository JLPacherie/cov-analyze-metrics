package com.synopsys.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests for Config")
class ConfigTest {

  protected static Logger logger = LogManager.getLogger(ConfigTest.class);

  //
  // ******************************************************************************************************************
  //

  @BeforeAll
  static void initAll() {
    logger.info("");
    logger.info("*****************************");
    logger.info("** Starting Config testing **");
    logger.info("*****************************");
    logger.info("");
  }

  @BeforeEach
  void init() {
    logger.info("");
    logger.info("+---------------------------+");
    logger.info("| New Test Case             |");
    logger.info("+---------------------------+");
    logger.info("");
  }

  @AfterEach
  void tearDown() {
  }

  @AfterAll
  static void tearDownAll() {
  }

  //
  // ******************************************************************************************************************
  //

  @Test
  void initialization() {
    Config config = new Config((String[]) null);

    assertFalse(config.isValid(), "Status of default initialization of a Config should be invalid.");

    config.setConfigDir("./config");
    assertTrue(config.init(), "Unable to initialize configuration");
  }

  //
  // ******************************************************************************************************************
  //

  @org.junit.jupiter.api.Test
  @DisplayName("Detection of bad usages of configuration from JSON files")
  void load_file1() {
    Config config = new Config();

    // We can't load a configuration from an undefined file
    assertFalse(config.jsonLoad(null), "JSON Configuration can't be loaded from an undefined file ");

    // We can't load a configuration from an missing file
    assertFalse(config.jsonLoad(""), "JSON Configuration can't be loaded from a missing file ");

  }

  //
  // ******************************************************************************************************************
  //

  @org.junit.jupiter.api.Test
  @DisplayName("Appropriate usage of load configuration from JSON files")
  void load_file2() {

    String jsonFileName = "config/test_config1.json";
    logger.info("Testing configuration at " + jsonFileName);

    logger.info("Creating new configuration.");
    Config config = new Config();

    logger.info("Loading JSON info configuration.");
    assertTrue(config.jsonLoad(jsonFileName), "JSON Configuration load failed from " + jsonFileName);

    logger.info("Checking validity of loaded config.");
    assertTrue(config.isValid(), "Loaded configuration is not valid.");
  }

  //
  // ******************************************************************************************************************
  //

  @org.junit.jupiter.api.Test
  @DisplayName("CLI with no parameters")
  void load_cli1() {

    Config config = new Config();

    // We can't load a configuration from an undefined file
    String[] args = null;
    assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from an undefined options.");

    // We can't load a configuration from an missing file
    args = new String[0];
    assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from a missing options ");

  }

  @org.junit.jupiter.api.Test
  @DisplayName("CLI with bad / unknown  parameters")
  void load_cli2() {
    Config config = new Config();
    String[] args = null;

    args = new String[]{""};
    assertFalse(config.cliLoad(args), "CLI Configuration fails with empty  options");

    args = new String[]{"", "-h"};
    assertFalse(config.cliLoad(args), "CLI Configuration fails with empty  options");

    args = new String[]{"-zoo"};
    assertFalse(config.cliLoad(args), "CLI Configuration can't be loaded from unknown options");

    args = new String[]{"--help", "-zoo"};
    assertFalse(config.cliLoad(args), "CLI Configuration can't be loaded from unknown options");

    args = new String[]{"--config-file", "./missing/missing.json"};
    assertFalse(config.cliLoad(args), "CLI Configuration can't be loaded from a missing JSON");
  }

  @org.junit.jupiter.api.Test
  @DisplayName("CLI with good parameters")
  void load_cli3() {
    Config config = new Config();
    String[] args= null;

    args = new String[]{"--help"};
    assertTrue(config.cliLoad(args), "CLI Configuration with only help");

    args = new String[]{"-h"};
    assertTrue(config.cliLoad(args), "CLI Configuration with only help");

    args = new String[]{"-h", "--help"};
    assertTrue(config.cliLoad(args), "CLI Configuration with only help");

    args = new String[]{"--config-file", "./config/test_config1.json"};
    assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
  }

  @org.junit.jupiter.api.Test
  @DisplayName("CLI with overwriting parameters")
  void load_cli4() {
    Config config = new Config();
    String[] args = null;

    args = new String[]{"--config-file", "./config/test_config1.json", "-D", "name=Hello"};
    assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
    assertEquals("Hello", config.getName(), "Configuration name not redefined");

    args = new String[]{"--config-file", "./config/test_config1.json", "--overwrite", "name=Hello"};
    assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
    assertEquals("Hello", config.getName(), "Configuration name not redefined");

    args = new String[]{"--config-file", "./config/test_config1.json", "-D", "config=./tmp"};
    assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
    assertEquals("./tmp", config.getConfigDir(), "Configuration config dir not redefined");

    args = new String[]{"--config-file", "./config/test_config1.json", "-co", "METRICS.LOC_TOO_HIGH:loc:666"};
    assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
    assertEquals(666,
            config.getEnabledChecker("METRICS.LOC_TOO_HIGH").getThreshold("loc"),
            "Checker threshold  not redefined");

  }

  //
  // ******************************************************************************************************************
  //

  @org.junit.jupiter.api.Test
  void save() {
    Config config = new Config();

    // We can't load a configuration from an undefined file
    assertFalse(config.save(null), "JSON Configuration can't be saved to an undefined file ");

    // We can't load a configuration from an missing file
    assertFalse(config.save(""), "JSON Configuration can't be saved to an invalid pathname ");
    assertFalse(config.save("/invalidpath"), "JSON Configuration can't be saved to an invalid pathname ");
  }


}