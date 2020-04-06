package com.synopsys.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

class MainTest {

  protected static Logger _logger;

  //
  // ******************************************************************************************************************
  //

  @BeforeAll
  static void initAll() throws IOException {
    String log4jConfigFile = "./tests/log4j2.xml";
    ConfigurationSource source = new ConfigurationSource(new FileInputStream(log4jConfigFile));
    Configurator.initialize(null, source);
    _logger = LogManager.getLogger(ConfigTest.class);
  }

  @BeforeEach
  void init() {
    _logger.info("");
    _logger.info("+---------------------------+");
    _logger.info("| New Test Case             |");
    _logger.info("+---------------------------+");
    _logger.info("");
  }

  @AfterEach
  void tearDown() {
    _logger.info("");
    _logger.info("+---------------------------+");
    _logger.info("| End Test Case             |");
    _logger.info("+---------------------------+");
    _logger.info("");
  }

  @AfterAll
  static void tearDownAll() {
  }

  @Test
  void mainTest() {

    Config config = new Config();
    config.setIDIR("./tests");
    config.setOutputTag("");

    String baseArgs = "-v -g --all --dir ./tests ";

    _logger.info("Search for all metrics files in ./tests/metrics");

    File srcDir = new File("./tests/metrics");
    if (srcDir.exists()) {
      File[] metricsList = srcDir.listFiles(file -> file.getAbsolutePath().endsWith("FUNCTION.metrics.xml.gz"));
      if (metricsList != null) {
        for (File metricsFile : metricsList) {
          _logger.info("Processing metrics file {}", metricsFile.getName());
          String defectsFile = metricsFile.getAbsolutePath().substring(0, metricsFile.getAbsolutePath().length() - 24);
          defectsFile += "-defects.json";

          String args = baseArgs + "--metrics " + metricsFile + " -o " + defectsFile;

          String[] argsList = args.split(" ");

          Main.main(argsList);
        }
      }
    }


  }

}