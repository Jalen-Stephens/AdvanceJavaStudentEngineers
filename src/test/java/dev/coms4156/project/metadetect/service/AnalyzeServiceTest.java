//package dev.coms4156.project.metadetect.service;
//
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assumptions.assumeTrue;
//
//import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
//import dev.coms4156.project.metadetect.service.AnalyzeService;
//import java.io.File;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//class AnalyzeServiceTest {
//
//  private AnalyzeService analyzeService;
//  private static final String TOOL_PATH = "./tools/c2patool/c2patool";
//
//  @BeforeEach
//  void setup() {
//    C2paToolInvoker c2paToolInvoker = new C2paToolInvoker(TOOL_PATH);
//    analyzeService = new AnalyzeService(c2paToolInvoker);
//  }
//
//  @Test
//  void testFetchC2pa() throws Exception {
//    // Skip if tool is not present
//    assumeTrue(Files.exists(Path.of(TOOL_PATH)),
//        "Skipping test: c2patool not installed");
//
//    File file = new File("src/test/resources/mock-images/Spaghetti.png");
//
//    String manifestJson = analyzeService.fetchC2pa(file);
//
//    assertNotNull(manifestJson, "fetchC2pa returned null");
//    System.out.println(manifestJson);
//  }
//}
