package dev.coms4156.project.metadetect.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.AnalyzeService;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Slice tests for {@link AnalyzeController}.
 * Focuses on HTTP contract (status + JSON payload) while mocking the service layer.
 */
@WebMvcTest(AnalyzeController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyzeControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private AnalyzeService analyzeService;

  @Test
  void submit_returnsAcceptedWithBody() throws Exception {
    UUID imageId = UUID.randomUUID();
    var response = new Dtos.AnalyzeStartResponse("analysis-123");
    when(analyzeService.submitAnalysis(imageId)).thenReturn(response);

    mvc.perform(MockMvcRequestBuilders.post("/api/analyze/" + imageId)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.analysisId").value("analysis-123"));

    verify(analyzeService, times(1)).submitAnalysis(imageId);
  }

  @Test
  void getStatus_returnsDtoFromService() throws Exception {
    UUID analysisId = UUID.randomUUID();
    var response =
        new Dtos.AnalyzeConfidenceResponse(analysisId.toString(), "COMPLETED", 0.97d, true, "v1");
    when(analyzeService.getConfidence(analysisId)).thenReturn(response);

    mvc.perform(MockMvcRequestBuilders.get("/api/analyze/" + analysisId))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.confidenceScore").value(0.97d))
        .andExpect(jsonPath("$.c2paUsed").value(true))
        .andExpect(jsonPath("$.modelVersion").value("v1"));

    verify(analyzeService, times(1)).getConfidence(analysisId);
  }

  @Test
  void getManifest_returnsManifestJson() throws Exception {
    UUID analysisId = UUID.randomUUID();
    var response =
        new Dtos.AnalysisManifestResponse(analysisId.toString(), "{\"manifest\":true}");
    when(analyzeService.getMetadata(analysisId)).thenReturn(response);

    mvc.perform(MockMvcRequestBuilders.get("/api/analyze/" + analysisId + "/manifest"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
        .andExpect(jsonPath("$.manifestJson").value("{\"manifest\":true}"));

    verify(analyzeService, times(1)).getMetadata(analysisId);
  }

  @Test
  void compare_returnsComparisonResult() throws Exception {
    UUID left = UUID.randomUUID();
    UUID right = UUID.randomUUID();
    var response = new Dtos.AnalyzeCompareResponse("OK", 0.42d, "stub");
    when(analyzeService.compare(left, right)).thenReturn(response);

    mvc.perform(MockMvcRequestBuilders.get("/api/analyze/compare")
            .param("left", left.toString())
            .param("right", right.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.status").value("OK"))
        .andExpect(jsonPath("$.similarity").value(0.42d))
        .andExpect(jsonPath("$.note").value("stub"));

    verify(analyzeService, times(1)).compare(left, right);
  }

  @Test
  void compare_missingParam_returnsBadRequest() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/analyze/compare")
            .param("left", UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getStatus_invalidUuid_returns400() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/analyze/not-a-uuid"))
        .andExpect(status().isBadRequest());
  }
}
