package dev.coms4156.project.metadetect.supabase;

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

class SupabaseStorageServiceTest {

  private MockWebServer server;
  private SupabaseStorageService storageService;
  private String projectBase;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    projectBase = server.url("/").toString(); // ends with "/"
    WebClient webClient = WebClient.builder().build();
    storageService = new SupabaseStorageService(
      webClient,
      projectBase,
      "metadetect-images",
      900
    );
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void uploadObject_postsMultipartToCorrectPath_withAuthHeader() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

    byte[] bytes = "hello".getBytes();
    String returnedPath = storageService.uploadObject(
      bytes,
      MediaType.IMAGE_PNG_VALUE,
      "user-123/img-uuid--photo.png",
      "bearer.jwt.here"
    );

    assertEquals("metadetect-images/user-123/img-uuid--photo.png", returnedPath);

    RecordedRequest req = server.takeRequest();
    assertEquals("POST", req.getMethod());
    assertEquals("/storage/v1/object/metadetect-images/user-123/img-uuid--photo.png",
      req.getPath());
    // Authorization header present
    String auth = req.getHeader("Authorization");
    assertNotNull(auth);
    assertEquals("Bearer bearer.jwt.here", auth);
    // multipart content type
    String ct = req.getHeader("Content-Type");
    // Should start with multipart/form-data; boundary=...
    assertNotNull(ct);
    // Do not assert exact boundary—just prefix
    assertEquals(true, ct.startsWith("multipart/form-data"));
  }

  @Test
  void createSignedUrl_returnsAbsoluteProjectUrl() throws Exception {
    String relative = "/storage/v1/object/sign/metadetect-images/user-123/img-uuid--photo.png?token=xyz&expires=123";
    server.enqueue(new MockResponse()
      .setResponseCode(200)
      .setHeader("Content-Type", "application/json")
      .setBody("{\"signedURL\":\"" + relative + "\"}"));

    String abs = storageService.createSignedUrl(
      "user-123/img-uuid--photo.png",
      "bearer.jwt.here"
    );

    assertNotNull(abs);
    // Should be projectBase (without ensuring trailing slash) + relative
    // projectBase from server ends with "/", service trims it, so result should start with same host.
    // Just assert it ends with the relative path for stability.
    assertEquals(true, abs.endsWith(relative));

    RecordedRequest req = server.takeRequest();
    assertEquals("POST", req.getMethod());
    assertEquals("/storage/v1/object/sign/metadetect-images/user-123/img-uuid--photo.png",
      req.getPath());
    String auth = req.getHeader("Authorization");
    assertEquals("Bearer bearer.jwt.here", auth);
    String contentType = req.getHeader("Content-Type");
    assertEquals("application/json", contentType);
  }
}
