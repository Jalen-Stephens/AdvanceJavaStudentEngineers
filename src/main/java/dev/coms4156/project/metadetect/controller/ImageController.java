package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.service.ImageService;
import dev.coms4156.project.metadetect.service.UserService;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thin HTTP adapter for image operations.
 * Delegates orchestration (DB + storage) to ImageService and identity to UserService.
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

  private final ImageService imageService;
  private final UserService userService;

  public ImageController(ImageService imageService, UserService userService) {
    this.imageService = imageService;
    this.userService = userService;
  }

  /** GET /api/images?page=0&size=20 — list current user's images (paging). */
  @GetMapping
  public ResponseEntity<List<Dtos.ImageDto>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    if (page < 0 || size <= 0) {
      return ResponseEntity.badRequest().build();
    }

    UUID userId = userService.getCurrentUserIdOrThrow();
    List<Image> results = imageService.listByOwner(userId, page, size);

    List<Dtos.ImageDto> items = results.stream().map(this::toDto).collect(Collectors.toList());
    return ResponseEntity.ok(items);
  }

  /** GET /api/images/{id} — fetch a single image (ownership enforced in service). */
  @GetMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> get(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    UUID imageId = parseUuidOrThrow(id);
    Image img = imageService.getById(userId, imageId);
    return ResponseEntity.ok(toDto(img));
  }

  /** PUT /api/images/{id} — update mutable metadata (labels, note). */
  @PutMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> update(
      @PathVariable String id,
      @RequestBody Dtos.UpdateImageRequest req) {

    UUID userId = userService.getCurrentUserIdOrThrow();
    UUID imageId = parseUuidOrThrow(id);

    String[] labels = (req.labels() == null) ? null : req.labels().toArray(new String[0]);

    Image updated = imageService.update(
        userId,
        imageId,
        /* newFilename */ null,     // FIX: your DTO doesn't expose filename()
        /* newStoragePath */ null,
        /* newLabels */ labels,
        /* newNote */ req.note()
    );

    return ResponseEntity.ok(toDto(updated));
  }

  /** DELETE /api/images/{id} — hard delete metadata + storage object (service orchestrates). */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    UUID imageId = parseUuidOrThrow(id);

    imageService.deleteAndPurge(userId, bearer, imageId);
    return ResponseEntity.noContent().build();
  }

  /** POST /api/images/upload — upload binary, persist metadata, return DTO. */
  @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Dtos.ImageDto> upload(@RequestPart("file") MultipartFile file)
      throws Exception {
    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    Image created = imageService.upload(userId, bearer, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
  }

  /** GET /api/images/{id}/url — return short-lived signed URL for private object. */
  @GetMapping("/{id}/url")
  public ResponseEntity<Object> signedUrl(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    UUID imageId = parseUuidOrThrow(id);

    String url = imageService.getSignedUrl(userId, bearer, imageId);
    return ResponseEntity.ok(Map.of("url", url));
  }

  // ---- Exception → HTTP mapping (controller-scoped) ----

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<String> handleNotFound(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<String> handleForbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
  }

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<String> handleBadRequest(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body("Invalid request: " + ex.getMessage());
  }

  // ---- Helpers ----

  private UUID parseUuidOrThrow(String raw) {
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid UUID: " + raw);
    }
  }

  private Dtos.ImageDto toDto(Image img) {
    return new Dtos.ImageDto(
      img.getId().toString(),
      img.getFilename(),
      img.getUserId().toString(),
      img.getUploadedAt(),
      img.getLabels() == null ? List.of() : Arrays.asList(img.getLabels()),
      img.getNote()
    );
  }
}
