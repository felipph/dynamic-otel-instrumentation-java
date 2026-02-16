package com.otel.dynamic.config;

import com.otel.dynamic.config.model.AttributeDefinition;
import com.otel.dynamic.config.model.InstrumentationConfig;
import com.otel.dynamic.config.model.MethodConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for ConfigurationManager.
 */
public class ConfigurationManagerTest {

    private File tempConfigFile;
    private ConfigurationManager manager;

    @Before
    public void setUp() throws IOException {
        // Create temporary config file
        tempConfigFile = File.createTempFile("instrumentation-test", ".json");
        tempConfigFile.deleteOnExit();

        // Reset singleton
        ConfigurationManager.reset();
    }

    @After
    public void tearDown() {
        if (tempConfigFile != null && tempConfigFile.exists()) {
            tempConfigFile.delete();
        }
        ConfigurationManager.reset();
    }

    @Test
    public void testLoadEmptyConfiguration() throws IOException {
        // Write empty config
        try (FileWriter writer = new FileWriter(tempConfigFile)) {
            writer.write("{\"instrumentations\": []}");
        }

        manager = ConfigurationManager.initialize(tempConfigFile.getAbsolutePath());

        assertNotNull(manager);
        assertNotNull(manager.getConfig());
        assertTrue(manager.getConfig().isEmpty());
        assertEquals(0, manager.getConfig().size());
    }

    @Test
    public void testLoadValidConfiguration() throws IOException {
        // Write valid config
        String json = "{\n" +
                "  \"instrumentations\": [\n" +
                "    {\n" +
                "      \"className\": \"com.example.Service\",\n" +
                "      \"methodName\": \"process\",\n" +
                "      \"attributes\": [\n" +
                "        { \"argIndex\": 0, \"methodCall\": \"getBatchId\", \"attributeName\": \"app.batch_id\" },\n" +
                "        { \"argIndex\": 0, \"methodCall\": \"getRootId\", \"attributeName\": \"app.root_id\" }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        try (FileWriter writer = new FileWriter(tempConfigFile)) {
            writer.write(json);
        }

        manager = ConfigurationManager.initialize(tempConfigFile.getAbsolutePath());

        assertNotNull(manager);
        InstrumentationConfig config = manager.getConfig();
        assertNotNull(config);
        assertEquals(1, config.size());

        List<MethodConfig> methods = config.getInstrumentations();
        assertNotNull(methods);
        assertEquals(1, methods.size());

        MethodConfig methodConfig = methods.get(0);
        assertEquals("com.example.Service", methodConfig.getClassName());
        assertEquals("process", methodConfig.getMethodName());

        List<AttributeDefinition> attributes = methodConfig.getAttributes();
        assertNotNull(attributes);
        assertEquals(2, attributes.size());

        AttributeDefinition attr1 = attributes.get(0);
        assertEquals(0, attr1.getArgIndex());
        assertEquals("getBatchId", attr1.getMethodCall());
        assertEquals("app.batch_id", attr1.getAttributeName());
    }

    @Test
    public void testGetConfigForMethod() throws IOException {
        // Write config with multiple methods
        String json = "{\n" +
                "  \"instrumentations\": [\n" +
                "    {\n" +
                "      \"className\": \"com.example.Service\",\n" +
                "      \"methodName\": \"process\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"className\": \"com.example.Handler\",\n" +
                "      \"methodName\": \"handle\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        try (FileWriter writer = new FileWriter(tempConfigFile)) {
            writer.write(json);
        }

        manager = ConfigurationManager.initialize(tempConfigFile.getAbsolutePath());

        // Test lookup by class and method name
        MethodConfig config1 = manager.getConfigFor("com.example.Service", "process");
        assertNotNull(config1);
        assertEquals("com.example.Service", config1.getClassName());

        MethodConfig config2 = manager.getConfigFor("com.example.Handler", "handle");
        assertNotNull(config2);
        assertEquals("com.example.Handler", config2.getClassName());

        // Test non-existent method
        MethodConfig config3 = manager.getConfigFor("com.example.Service", "notfound");
        assertNull(config3);
    }

    @Test
    public void testConfigurationReload() throws IOException {
        // Write initial config
        String json1 = "{\"instrumentations\": []}";
        try (FileWriter writer = new FileWriter(tempConfigFile)) {
            writer.write(json1);
        }

        manager = ConfigurationManager.initialize(tempConfigFile.getAbsolutePath());
        assertEquals(0, manager.getConfig().size());

        // Modify config
        String json2 = "{\n" +
                "  \"instrumentations\": [\n" +
                "    {\n" +
                "      \"className\": \"com.example.NewService\",\n" +
                "      \"methodName\": \"newMethod\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        try (FileWriter writer = new FileWriter(tempConfigFile)) {
            writer.write(json2);
        }

        // Reload
        manager.loadConfiguration();

        // Verify new config loaded
        assertEquals(1, manager.getConfig().size());
        assertNotNull(manager.getConfigFor("com.example.NewService", "newMethod"));
    }

    @Test
    public void testMissingConfigurationFile() {
        manager = ConfigurationManager.initialize("/nonexistent/path/config.json");

        assertNotNull(manager);
        assertNotNull(manager.getConfig());
        // Should return empty config when file not found
        assertTrue(manager.getConfig().isEmpty());
    }

    @Test
    public void testConfigurationChangeListener() throws IOException {
        // Write config
        String json = "{\"instrumentations\": []}";
        try (FileWriter writer = new FileWriter(tempConfigFile)) {
            writer.write(json);
        }

        manager = ConfigurationManager.initialize(tempConfigFile.getAbsolutePath());

        // Add listener
        final boolean[] changed = {false};
        ConfigurationManager.ConfigurationChangeListener listener = newConfig -> changed[0] = true;
        manager.addListener(listener);

        // Trigger reload
        manager.loadConfiguration();

        // Verify listener was called (even for same content)
        assertTrue(changed[0]);

        // Remove listener
        manager.removeListener(listener);
        changed[0] = false;

        // Trigger another reload
        manager.loadConfiguration();

        // Listener should not be called
        assertFalse(changed[0]);
    }
}
