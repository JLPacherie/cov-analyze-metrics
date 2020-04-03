package com.synopsys.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Utilities")
class UtilsTest {
  protected static Logger logger = LogManager.getLogger(UtilsTest.class);
  private static ObjectMapper sMapper = new ObjectMapper();

  protected boolean sameJson(String json1, String json2) {
    try {
      JsonNode node2 = sMapper.readTree(json2);
      JsonNode node1 = sMapper.readTree(json1);
      return node1.equals(node2);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }
  //
  // ******************************************************************************************************************
  //

  @BeforeAll
  static void initAll() {
    logger.info("");
    logger.info("*****************************");
    logger.info("** Starting Utils testing  **");
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
  void loadJson() {
    String jsonFilename = "./tests/test_config1.json";
    JsonNode jsonRoot= Utils.getJsonNodeFromFile(jsonFilename);

    assertNotNull(jsonRoot,"Can't parse JSON file");
    assertEquals("test", Utils.getFieldAsText(jsonRoot,"name",null,null),"Unable to read name");
    assertEquals("Coverity metrics analysis test configuration", Utils.getFieldAsText(jsonRoot,"description",null,null),"Unable to read name");



  }
}