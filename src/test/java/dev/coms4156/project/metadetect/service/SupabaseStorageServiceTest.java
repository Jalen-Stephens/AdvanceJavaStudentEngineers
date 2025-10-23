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
  private String anonKey;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    projectBase = server.url("/").toString(); // ends with "/"
    anonKey = "anon-test-key";
    WebClient webClient = WebClient.builder().build();

    // NOTE: constructor now needs anonKey
    storageService = new SupabaseStorageService(
      webClient,
      projectBase,
      "metadetect-images",
      900,
      anonKey
    );
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

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

    // Service returns the objectPath you asked it to upload to (not prefixed with bucket)
    assertEquals("user-123/img-uuid--photo.png", returnedPath);

    RecordedRequest req = server.takeRequest();
    // We now use PUT (not POST) with a raw body
    assertEquals("PUT", req.getMethod());
    assertEquals("/storage/v1/object/metadetect-images/user-123/img-uuid--photo.png",
          req.getPath());

    // Authorization header present
    String auth = req.getHeader("Authorization");
    assertNotNull(auth);
    assertEquals("Bearer bearer.jwt.here", auth);

    // apikey header must be sent to Supabase
    String apikey = req.getHeader("apikey");
    assertEquals(anonKey, apikey);

    // Optional: upsert header
    assertEquals("true", req.getHeader("x-upsert"));

    // Content-Type is the file's content type (no multipart)
    String ct = req.getHeader("Content-Type");
    assertNotNull(ct);
    assertEquals(MediaType.IMAGE_PNG_VALUE, ct);
  }

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
    // Service prefixes the relative path with the (trimmed) project base
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
