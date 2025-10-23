package dev.coms4156.project.metadetect.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.service.AuthProxyService;
import dev.coms4156.project.metadetect.service.ImageService;
import dev.coms4156.project.metadetect.service.UserService;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import dev.coms4156.project.metadetect.supabase.SupabaseStorageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImageControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockBean
  private ImageService imageService;
  @MockBean
  private UserService userService;

  // @MockBean private SupabaseStorageService storage;

  private UUID userId;
  private UUID imgId;

  @BeforeEach
  void setup() {
    userId = UUID.randomUUID();
    imgId = UUID.randomUUID();
    when(userService.getCurrentUserIdOrThrow()).thenReturn(userId);
  }

  private Image makeImage() {
    Image img = new Image();
    img.setId(imgId);
    img.setUserId(userId);
    img.setFilename("test.jpg");
    img.setStoragePath("images/test.jpg");
    img.setLabels(new String[]{"tag1", "tag2"});
    img.setNote("hello");
    return img;
  }

  // ---- GET /api/images (list) ----
  @Test
  void listImages_success() throws Exception {
    when(imageService.listByOwner(userId, 0, 5)).thenReturn(List.of(makeImage()));

    mvc.perform(MockMvcRequestBuilders.get("/api/images"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(imgId.toString()))
      .andExpect(jsonPath("$[0].filename").value("test.jpg"))
      .andExpect(jsonPath("$[0].userId").value(userId.toString()))
      .andExpect(jsonPath("$[0].labels[0]").value("tag1"))
      .andExpect(jsonPath("$[0].note").value("hello"));
  }

  // pagination bounds (empty result if beyond range)
  @Test
  void listImages_outOfRangePagination_returnsEmptyList() throws Exception {
    when(imageService.listByOwner(userId, 0, 5)).thenReturn(List.of(makeImage()));

    mvc.perform(MockMvcRequestBuilders.get("/api/images?page=5&size=10"))
      .andExpect(status().isOk())
      .andExpect(content().json("[]"));
  }

  // ---- GET /api/images/{id} ----
  @Test
  void getImage_success() throws Exception {
    when(imageService.getById(userId, imgId)).thenReturn(makeImage());

    mvc.perform(MockMvcRequestBuilders.get("/api/images/" + imgId))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(imgId.toString()))
      .andExpect(jsonPath("$.note").value("hello"))
      .andExpect(jsonPath("$.labels[1]").value("tag2"));
  }

  @Test
  void getImage_notFound() throws Exception {
    when(imageService.getById(userId, imgId)).thenThrow(new NotFoundException("missing"));

    mvc.perform(MockMvcRequestBuilders.get("/api/images/" + imgId))
      .andExpect(status().isNotFound());
  }

  @Test
  void getImage_forbidden() throws Exception {
    when(imageService.getById(userId, imgId)).thenThrow(new ForbiddenException("nope"));

    mvc.perform(MockMvcRequestBuilders.get("/api/images/" + imgId))
      .andExpect(status().isForbidden());
  }

  // ---- PUT /api/images/{id} ----
  @Test
  void updateImage_success() throws Exception {
    Image updated = makeImage();
    updated.setNote("updated-note");
    updated.setLabels(new String[]{"new1", "new2"});

    when(imageService.update(eq(userId), eq(imgId), eq(null), eq(null),
      any(String[].class), eq("updated-note"))).thenReturn(updated);

    mvc.perform(MockMvcRequestBuilders.put("/api/images/" + imgId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "note": "updated-note",
            "labels": ["new1","new2"]
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.note").value("updated-note"))
      .andExpect(jsonPath("$.labels[0]").value("new1"));
  }

  // ---- DELETE /api/images/{id} ----
  //  @Test
  //  void deleteImage_success() throws Exception {
  //    mvc.perform(MockMvcRequestBuilders.delete("/api/images/" + imgId))
  //        .andExpect(status().isNoContent());
  //
  //    verify(imageService).delete(userId, imgId);
  //  }
  //
  //  @Test
  //  void deleteImage_notFound() throws Exception {
  //    doThrow(new NotFoundException("missing")).when(imageService).delete(userId, imgId);
  //
  //    mvc.perform(MockMvcRequestBuilders.delete("/api/images/" + imgId))
  //        .andExpect(status().isNotFound());
  //  }
  //
  //  @Test
  //  void deleteImage_forbidden() throws Exception {
  //    doThrow(new ForbiddenException("forbidden")).when(imageService).delete(userId, imgId);
  //
  //    mvc.perform(MockMvcRequestBuilders.delete("/api/images/" + imgId))
  //        .andExpect(status().isForbidden());
  //  }

  @Test
  void upload_success_returns201AndPersistsStoragePath() throws Exception {
    UUID user = UUID.randomUUID();
    UUID imgId = UUID.randomUUID();

    when(userService.getCurrentUserIdOrThrow()).thenReturn(user);
    when(userService.getCurrentBearerOrThrow()).thenReturn("jwt");

    Image returned = new Image();
    returned.setId(imgId);
    returned.setUserId(user);
    returned.setFilename("pic.png");
    returned.setStoragePath(user + "/" + imgId + "--pic.png");

    // New orchestration path: controller delegates to service.upload(...)
    when(imageService.upload(eq(user), eq("jwt"), any())).thenReturn(returned);

    MockMultipartFile file = new MockMultipartFile(
      "file", "pic.png", "image/png", "PNGDATA".getBytes()
    );

    mvc.perform(MockMvcRequestBuilders
        .multipart("/api/images/upload")
        .file(file))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(imgId.toString()))
      .andExpect(jsonPath("$.filename").value("pic.png"))
      .andExpect(jsonPath("$.userId").value(user.toString()));
  }

  @Test
  void signedUrl_success_returns200WithUrl() throws Exception {
    UUID user = UUID.randomUUID();
    UUID imgId = UUID.randomUUID();

    when(userService.getCurrentUserIdOrThrow()).thenReturn(user);
    when(userService.getCurrentBearerOrThrow()).thenReturn("jwt");

    when(imageService.getSignedUrl(user, "jwt", imgId))
      .thenReturn("https://example.supabase.co/storage/v1/object/sign/..token..");

    mvc.perform(MockMvcRequestBuilders.get("/api/images/" + imgId + "/url"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.url").exists());
  }
}

