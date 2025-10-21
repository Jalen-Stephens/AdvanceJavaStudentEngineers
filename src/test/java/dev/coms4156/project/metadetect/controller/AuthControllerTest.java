package dev.coms4156.project.metadetect.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.project.metadetect.service.AuthProxyService;
import dev.coms4156.project.metadetect.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@Import(dev.coms4156.project.metadetect.config.SecurityTestConfig.class)
@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private UserService userService;
  @MockBean private AuthProxyService authProxyService;

  @Test
  @DisplayName("POST /auth/signup proxies to Supabase and returns raw JSON")
  void signup_ok() throws Exception {
    String reqJson = "{\"email\":\"a@b.com\",\"password\":\"pw\"}";
    String supabaseJson = "{\"user\":{\"id\":\"u1\"},\"session\":null}";

    when(authProxyService.signup("a@b.com", "pw"))
        .thenReturn(ResponseEntity.ok()
        .contentType(APPLICATION_JSON)
        .body(supabaseJson));

    mockMvc.perform(post("/auth/signup")
        .with(csrf())
        .contentType(APPLICATION_JSON)
        .content(reqJson))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().json(supabaseJson));
  }

  @Test
  @DisplayName("POST /auth/login proxies to Supabase and returns raw JSON")
  void login_ok() throws Exception {
    String reqJson = "{\"email\":\"a@b.com\",\"password\":\"pw\"}";
    String supabaseJson = "{\"access_token\":\"tkn\",\"token_type\":\"bearer\"}";

    when(authProxyService.login("a@b.com", "pw"))
        .thenReturn(ResponseEntity.ok()
        .contentType(APPLICATION_JSON)
        .body(supabaseJson));

    mockMvc.perform(post("/auth/login")
        .with(csrf())
        .contentType(APPLICATION_JSON)
        .content(reqJson))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().json(supabaseJson));
  }

  @Test
  @DisplayName("POST /auth/refresh proxies to Supabase and returns raw JSON")
  void refresh_ok() throws Exception {
    String reqJson = "{\"refreshToken\":\"rfr\"}";
    String supabaseJson = "{\"access_token\":\"new\",\"token_type\":\"bearer\"}";

    when(authProxyService.refresh("rfr"))
        .thenReturn(ResponseEntity.ok()
        .contentType(APPLICATION_JSON)
        .body(supabaseJson));

    mockMvc.perform(post("/auth/refresh")
        .with(csrf())
        .contentType(APPLICATION_JSON)
        .content(reqJson))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(content().json(supabaseJson));
  }

  @Test
  @DisplayName("POST proxy endpoints bubble up Supabase error status/body via @ExceptionHandler")
  void proxy_error_bubbled() throws Exception {
    String reqJson = "{\"email\":\"bad\",\"password\":\"pw\"}";
    var ex = new AuthProxyService.ProxyException(HttpStatus.BAD_REQUEST, "{\"msg\":\"bad\"}");
    when(authProxyService.signup(anyString(), anyString())).thenThrow(ex);

    mockMvc.perform(post("/auth/signup")
        .with(csrf())
        .contentType(APPLICATION_JSON)
        .content(reqJson))
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", startsWith("application/json")))
        .andExpect(content().json("{\"msg\":\"bad\"}"));
  }

  @Test
  @DisplayName("GET /auth/me returns 401 when unauthenticated")
  void me_unauthenticated_401() throws Exception {
    mockMvc.perform(get("/auth/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "user@example.com")
  @DisplayName("GET /auth/me returns {id,email} when authenticated")
  void me_authenticated_ok() throws Exception {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(userService.getCurrentUserIdOrThrow()).thenReturn(id);
    when(userService.getCurrentUserEmail()).thenReturn(Optional.of("user@example.com"));

    mockMvc.perform(get("/auth/me"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.id", is(id.toString())))
        .andExpect(jsonPath("$.email", is("user@example.com")));
  }

  // ---------- Extra branches for controller coverage ----------

  @Test
  @DisplayName("GET /auth/signup -> 405 Method Not Allowed")
  void signup_method_not_allowed_405() throws Exception {
    mockMvc.perform(get("/auth/signup"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  @DisplayName("POST /auth/signup with invalid JSON -> 400")
  void signup_invalid_json_400() throws Exception {
    // malformed JSON
    String badJson = "{\"email\":\"a@b.com\",\"password\":pw}";
    mockMvc.perform(post("/auth/signup")
        .contentType(APPLICATION_JSON)
        .content(badJson))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /auth/login with unsupported media type -> 415")
  void login_unsupported_media_type_415() throws Exception {
    String body = "{\"email\":\"a@b.com\",\"password\":\"pw\"}";
    mockMvc.perform(post("/auth/login")
        .contentType(TEXT_PLAIN)   // wrong content type
        .content(body))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  @DisplayName("POST /auth/refresh with empty body -> 400 (framework)")
  void refresh_empty_body_400_framework() throws Exception {
    mockMvc.perform(post("/auth/refresh")
        .contentType(APPLICATION_JSON)
        .content(""))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /auth/refresh with missing refreshToken -> 400 + JSON error (controller)")
  void refresh_missing_field_400_controller() throws Exception {
    mockMvc.perform(post("/auth/refresh")
        .contentType(APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("{\"error\":\"missing refreshToken\"}"));
  }

  @WithMockUser(username = "whoever")
  @Test
  @DisplayName("GET /auth/me when authenticated but email missing -> email omitted")
  void me_authenticated_email_null() throws Exception {
    UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
    when(userService.getCurrentUserIdOrThrow()).thenReturn(id);
    when(userService.getCurrentUserEmail()).thenReturn(Optional.empty());

    mockMvc.perform(get("/auth/me"))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
      .andExpect(jsonPath("$.id", is(id.toString())))
        .andExpect(jsonPath("$.email").doesNotExist());
  }

  @Test
  @DisplayName("Proxy error handler sets status and JSON content type")
  void proxy_error_handler_sets_json() throws Exception {
    var ex = new AuthProxyService.ProxyException(
        org.springframework.http.HttpStatusCode.valueOf(429),
        "{\"error\":\"rate_limit\"}");
    when(authProxyService.login(anyString(), anyString())).thenThrow(ex);

    mockMvc.perform(post("/auth/login")
        .contentType(APPLICATION_JSON)
        .content("{\"email\":\"a@b.com\",\"password\":\"pw\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Content-Type", startsWith("application/json")))
        .andExpect(content().json("{\"error\":\"rate_limit\"}"));
  }
}
