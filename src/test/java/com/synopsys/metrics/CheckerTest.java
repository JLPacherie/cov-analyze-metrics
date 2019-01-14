package com.synopsys.metrics;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CheckerTest {

  public String cfgDir = "./config";
  public List<String> checkerFileNames = new ArrayList<String>();

  //
  // ******************************************************************************************************************
  //

  @BeforeAll
  static void initAll() {

  }

  @BeforeEach
  void init() {
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
  void isValid() {
    Checker checker = new Checker();
    assertFalse(checker.isValid(), "A new checker is initialized as invalid.");

  }

  @Test
  void load() {

    Config config = new Config();
    config.setConfigDir("./config");
    config.init();

    for (Checker checker : config.availableCheckers) {
      System.out.println("Checker: " + checker.getName());
      System.out.println("  Description: " + checker.getDescription());
      checker.metrics().forEach(metric ->
              System.out.println("  Metric: " + metric.name + "(" + metric.metric + ") <= " + metric.value)
      );

    }
  }

  @Test
  void save() {
  }
}