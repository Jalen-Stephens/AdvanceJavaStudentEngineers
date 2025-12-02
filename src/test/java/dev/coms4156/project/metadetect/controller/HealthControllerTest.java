package dev.coms4156.project.metadetect.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Verifies {@link HealthController} contract without touching a real database.
 */
@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private JdbcTemplate jdbcTemplate;

  @Test
  void dbHealth_returnsUpWhenDatabaseResponds() throws Exception {
    when(jdbcTemplate.queryForObject(eq("select 1"), eq(Integer.class))).thenReturn(1);

    mvc.perform(MockMvcRequestBuilders.get("/db/health"))
        .andExpect(status().isOk())
        .andExpect(content().string("UP"));

    verify(jdbcTemplate, times(1)).queryForObject("select 1", Integer.class);
  }

  @Test
  void dbHealth_returnsDownWhenResultMissing() throws Exception {
    when(jdbcTemplate.queryForObject(eq("select 1"), eq(Integer.class))).thenReturn(0);

    mvc.perform(MockMvcRequestBuilders.get("/db/health"))
        .andExpect(status().isOk())
        .andExpect(content().string("DOWN"));
  }

  @Test
  void dbHealth_returnsDownWhenNull() throws Exception {
    when(jdbcTemplate.queryForObject(eq("select 1"), eq(Integer.class))).thenReturn(null);

    mvc.perform(MockMvcRequestBuilders.get("/db/health"))
        .andExpect(status().isOk())
        .andExpect(content().string("DOWN"));
  }

  @Test
  void version_returnsStaticMetadata() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/db/version")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.service").value("metadetect-service"))
        .andExpect(jsonPath("$.version").value("0.1.0"));
  }
}
