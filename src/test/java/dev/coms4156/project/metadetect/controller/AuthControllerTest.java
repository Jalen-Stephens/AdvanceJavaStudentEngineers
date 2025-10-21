package dev.coms4156.project.metadetect.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.project.metadetect.service.AuthProxyService;
import dev.coms4156.project.metadetect.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller-slice tests for AuthController.
 * Uses MockMvc + @WebMvcTest with mocked collaborators.
 *
 */
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
        .body("{\"user\":{\"id\":\"u1\"},\"session\":null}"));

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
        .body("{\"access_token\":\"tkn\",\"token_type\":\"bearer\"}"));

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
        .body("{\"access_token\":\"new\",\"token_type\":\"bearer\"}"));

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
        .andExpect(content().json("{\"msg\":\"bad\"}"));
  }

  @Test
  @DisplayName("GET /auth/me returns 401 when unauthenticated")
  void me_unauthenticated_401() throws Exception {
    // No @WithMockUser annotation -> expect 401 if Spring Security is active.
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
}
