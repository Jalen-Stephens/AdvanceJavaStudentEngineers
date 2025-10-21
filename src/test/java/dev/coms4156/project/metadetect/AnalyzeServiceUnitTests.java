package dev.coms4156.project.metadetect;

import dev.coms4156.project.metadetect.service.AnalyzeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;


/**
 * This class contains the unit tests for the AnalyzeService class.
 */


// TODO: Add springboot test later. Breaks the tests for now.
class AnalyzeServiceTest {

    @Autowired
    private AnalyzeService analyzeService;

    private InputStream in;

    @BeforeEach
    void setup() {
        in = getClass().getResourceAsStream("/mock-images/Spaghetti.PNG");
        System.out.println("InputStream: " + in);
        assertNotNull(in, "Test image not found in resources/mock-images/Spaghetti.PNG");
    }

    @Test
    void testMetadataExtraction() throws Exception {
        // fetchMetadata should return a JSON String
        analyzeService = new AnalyzeService();
        String json = analyzeService.fetchMetadata(in);

        assertNotNull(json, "fetchMetadata returned null");

        System.out.println("Extracted Metadata JSON:\n" + json);

        assertTrue(json.contains("\"directories\""), "Expected JSON to contain 'directories'");
    }
}
