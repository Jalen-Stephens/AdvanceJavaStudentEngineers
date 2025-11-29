package dev.coms4156.project.metadetect.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Live E2e that hits real Supabase auth, storage, and the configured database.
 * Requires environment variables to be set (SPRING_DATASOURCE_*, SUPABASE_*).
 * Opt-in only via LIVE_E2E=true to avoid accidental external calls.
 */
@EnabledIfEnvironmentVariable(named = "LIVE_E2E", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e-live")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientServiceLiveE2eTest {

  @LocalServerPort
  private int port;

  private String email;
  private String password;

  @BeforeEach
  void setupRestAssured() {
    RestAssured.baseURI = "http://localhost";
    RestAssured.port = port;

    // Unique email to avoid Supabase conflicts on repeated runs.
    email = "pulse-live+" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    password = "Supasecret123!";
  }

  @Test
  void liveSignupLoginUploadListDelete() {
    Response signup = given()
        .contentType("application/json")
        .body(Map.of("email", email, "password", password))
        .when()
        .post("/auth/signup");

    assumeTrue(
        signup.statusCode() == 200,
        "Signup failed (" + signup.statusCode() + "): " + signup.asString()
    );

    signup.then().body("user.email", equalTo(email));

    Response login = given()
        .contentType("application/json")
        .body(Map.of("email", email, "password", password))
        .when()
        .post("/auth/login");

    assumeTrue(
        login.statusCode() == 200,
        "Login failed (" + login.statusCode() + "): " + login.asString()
    );

    login.then().body("access_token", not(empty()));
    String loginBody = login.asString();

    String accessToken = new JsonPath(loginBody).getString("access_token");

    File imageFile = new File("src/test/resources/mock-images/spaghetti.png");

    var uploadResp = given()
        .header("Authorization", "Bearer " + accessToken)
        .multiPart("file", imageFile)
        .when()
        .post("/api/images/upload");

    // Log details if we hit gateway limits or other errors (e.g., 413)
    if (uploadResp.statusCode() >= 400) {
      System.out.println("Upload failed: status=" + uploadResp.statusCode());
      System.out.println("Upload body: " + uploadResp.asString());
    }

    String uploadBody = uploadResp
        .then()
        .statusCode(201)
        .body("filename", equalTo(imageFile.getName()))
        .extract()
        .asString();

    String imageId = new JsonPath(uploadBody).getString("id");
    String userId = new JsonPath(uploadBody).getString("userId");

    String listBody = given()
        .header("Authorization", "Bearer " + accessToken)
        .when()
        .get("/api/images")
        .then()
        .statusCode(200)
        .body("find { it.id == '" + imageId + "' }.userId", equalTo(userId))
        .extract()
        .asString();

    assertFalse(listBody.isBlank(), "List response should not be empty");

    given()
        .header("Authorization", "Bearer " + accessToken)
        .when()
        .delete("/api/images/" + imageId)
        .then()
        .statusCode(204);

    given()
        .header("Authorization", "Bearer " + accessToken)
        .when()
        .get("/api/images")
        .then()
        .statusCode(200);
  }
}
