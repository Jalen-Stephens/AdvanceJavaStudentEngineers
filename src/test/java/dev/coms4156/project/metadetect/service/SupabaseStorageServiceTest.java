package dev.coms4156.project.metadetect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Unit tests for {@link SupabaseStorageService}.
 * Validates construction of upload and signed URL requests, including:
 * - correct HTTP method and bucket/object path
 * - Authorization + apikey propagation
 * - correct handling of signed URL responses
 * Uses MockWebServer so no real network calls are made.
 */
class SupabaseStorageServiceTest {

  private MockWebServer server;
  private SupabaseStorageService storageService;
  private String projectBase;
  private String anonKey;

  /**
   * Sets up a mock Supabase endpoint and constructs the service using it.
   * Ensures all network traffic is isolated to the mock server.
   */
  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();

    // Mock URLs from MockWebServer always end with a trailing slash.
    projectBase = server.url("/").toString();
    anonKey = "anon-test-key";

    WebClient webClient = WebClient.builder().build();
    storageService = new SupabaseStorageService(
      webClient,
      projectBase,
      "metadetect-images",
      900,
      anonKey
    );
  }

  /**
   * Cleanly shuts down the mock HTTP server after each test.
   */
  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  /**
   * Verifies uploadObject uses PUT with raw bytes and correct Supabase headers.
   * Also checks:
   * - object path is returned unchanged
   * - x-upsert = true
   * - content type matches file type
   */
  @Test
  void uploadObject_putsRawBytesToCorrectPath_withAuthAndApikeyHeaders() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

    byte[] bytes = "hello".getBytes();

    String returnedPath = storageService.uploadObject(
        bytes,
        MediaType.IMAGE_PNG_VALUE,
        "user-123/img-uuid--photo.png",
        "bearer.jwt.here"
    );

    assertEquals("user-123/img-uuid--photo.png", returnedPath);

    RecordedRequest req = server.takeRequest();

    assertEquals("PUT", req.getMethod());
    assertEquals("/storage/v1/object/metadetect-images/user-123/img-uuid--photo.png",
        req.getPath());

    assertEquals("Bearer bearer.jwt.here", req.getHeader("Authorization"));
    assertEquals(anonKey, req.getHeader("apikey"));
    assertEquals("true", req.getHeader("x-upsert"));
    assertEquals(MediaType.IMAGE_PNG_VALUE, req.getHeader("Content-Type"));
  }

  /**
   * Verifies createSignedUrl issues POST to /sign endpoint and
   * reconstructs the final absolute URL using projectBase.
   * Also ensures Authorization and apikey headers are included.
   */
  @Test
  void createSignedUrl_returnsAbsoluteProjectUrl_andSendsHeaders() throws Exception {
    String relative =
        "/storage/v1/object/sign/metadetect-images/"
        + "user-123/img-uuid--photo.png?token=xyz&expires=123";

    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("{\"signedURL\":\"" + relative + "\"}"));

    String abs = storageService.createSignedUrl(
        "user-123/img-uuid--photo.png",
        "bearer.jwt.here"
    );

    assertNotNull(abs);
    assertEquals(true, abs.endsWith(relative));

    RecordedRequest req = server.takeRequest();
    assertEquals("POST", req.getMethod());
    assertEquals("/storage/v1/object/sign/metadetect-images/user-123/img-uuid--photo.png",
        req.getPath());
    assertEquals("Bearer bearer.jwt.here", req.getHeader("Authorization"));
    assertEquals(anonKey, req.getHeader("apikey"));
    assertEquals("application/json", req.getHeader("Content-Type"));
  }
}
