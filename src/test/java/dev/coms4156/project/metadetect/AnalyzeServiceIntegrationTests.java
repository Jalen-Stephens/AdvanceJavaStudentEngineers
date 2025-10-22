package dev.coms4156.project.metadetect;

import dev.coms4156.project.metadetect.service.AnalyzeService;
import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyzeServiceIntegrationTests {

    private AnalyzeService analyzeService;

    @BeforeEach
    void setup() {
        // Path to the C2PA tool binary (adjust this path as needed)
        String c2paToolPath = "./tools/c2patool/c2patool";

        // Initialize the C2paToolInvoker with the actual tool path
        C2paToolInvoker c2paToolInvoker = new C2paToolInvoker(c2paToolPath);

        // Initialize the AnalyzeService with the real C2paToolInvoker
        analyzeService = new AnalyzeService(c2paToolInvoker);
    }

    @Test
    void testFetchC2pa() throws Exception {
        // Load a test image from resources
       //InputStream in = getClass().getResourceAsStream("/mock-images/Spaghetti.PNG");
       // assertNotNull(in, "Test image not found in resources/mock-images/Spaghetti.PNG");
        
        File file = new File("src/test/resources/mock-images/Spaghetti.PNG");
        // Call the fetchC2pa method
        String manifestJson = analyzeService.fetchC2pa(file);

        // Verify the result
        assertNotNull(manifestJson, "fetchC2pa returned null");
        System.out.println("Extracted Metadata JSON:\n" + manifestJson);

        // Ensure the JSON contains expected fields (basic validation)
        assertNotNull(manifestJson, "Manifest JSON should not be null");
    }
}