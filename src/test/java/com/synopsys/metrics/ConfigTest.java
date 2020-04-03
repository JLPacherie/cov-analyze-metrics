package com.synopsys.metrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.*;

import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests for Configuration")
class ConfigTest {

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

	//
	// ******************************************************************************************************************
	//

	@Test
	void initialization() {
		Config config = new Config();

		assertFalse(config.isValid(), "Status of default initialization of a Config should be invalid.");

		config.setConfigDir("src/main/resources/checkers");
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

	
		String jsonFileName = "tests/test_config1.json";
		_logger.info("Testing configuration at " + jsonFileName);

		_logger.info("Creating new configuration.");
		Config config = new Config();
		
		_logger.info("Loading JSON info configuration.");
		assertTrue(config.jsonLoad(jsonFileName), "JSON Configuration load failed from " + jsonFileName);

		//
		// For a configuration to be valid, it requires that all mandatory parameters
		// are defined. Some of them doesn't fit in the configuration file and are
		// specific to each run
		//
		
		config.setFuntionsFileName("tests/mbedtls-2.1.0-FUNCTION.metrics.xml.gz");
		config.setIDIR("./tests");
		config.setOutputTag("");
		
		_logger.info("Checking validity of loaded config.");
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

	//
	// ******************************************************************************************************************
	//

	@org.junit.jupiter.api.Test
	@DisplayName("CLI with bad / unknown  parameters")
	void load_cli2() {
		
		Config config = new Config();
		String[] args = null;

		args = new String[] { "" };
		assertTrue(config.cliLoad(args), "CLI Configuration load succeeds with empty options");
		assertFalse(config.isValid(),"Incomplete configuration should not be valid");
		
		args = new String[] { "-zoo" };
		assertFalse(config.cliLoad(args), "CLI Configuration can't be loaded from unknown options");

		args = new String[] { "--help", "-zoo" };
		assertFalse(config.cliLoad(args), "CLI Configuration can't be loaded from unknown options");

		args = new String[] { "--config-file", "./missing/missing.json" };
		assertFalse(config.cliLoad(args), "CLI Configuration can't be loaded from a missing JSON");
	}

	//
	// ******************************************************************************************************************
	//

	@org.junit.jupiter.api.Test
	@DisplayName("CLI with good parameters")
	void load_cli3() {
		
		Config config = new Config();
		String[] args = null;

		args = new String[] { "--help" };
		assertTrue(config.cliLoad(args), "CLI Configuration with only help");

		args = new String[] { "-h" };
		assertTrue(config.cliLoad(args), "CLI Configuration with only help");

		args = new String[] { "-h", "--help" };
		assertTrue(config.cliLoad(args), "CLI Configuration with only help");

		args = new String[] { "--config-file", "./tests/test_config1.json" };
		assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
	}

	//
	// ******************************************************************************************************************
	//

	@org.junit.jupiter.api.Test
	@DisplayName("CLI with overwriting parameters")
	void load_cli4() {
		Config config = new Config();
		String[] args = null;

		args = new String[] { "--config-file", "./tests/test_config1.json", "-D", "name=Hello" };
		assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
		assertEquals("Hello", config.getName(), "Configuration name not redefined");

		args = new String[] { "--config-file", "./tests/test_config1.json", "--overwrite", "name=Hello" };
		assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
		assertEquals("Hello", config.getName(), "Configuration name not redefined");

		args = new String[] { "--config-file", "./tests/test_config1.json", "-D", "config=./tmp" };
		assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");
		assertEquals("./tmp", config.getConfigDir(), "Configuration config dir not redefined");

		args = new String[] { "--config-file", "./tests/test_config1.json", "-co", "METRICS.LOC_TOO_HIGH:loc:666" };
		assertTrue(config.cliLoad(args), "CLI Configuration can't be loaded from JSON");

		Checker checker = config.getEnabledChecker("METRICS.LOC_TOO_HIGH");
		assertNotNull(checker, "Unable to get checker by name for METRICS.LOC_TOO_HIGH");
		assertEquals(666, checker.getThreshold("loc"), "Checker threshold  not redefined");

	}

	//
	// ******************************************************************************************************************
	//

}