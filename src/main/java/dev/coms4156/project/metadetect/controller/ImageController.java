package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.service.ImageService;
import dev.coms4156.project.metadetect.service.UserService;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD for image metadata (not binary files). Enforces ownership via ImageService.
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

  /** GET /api/images?page=0&size=20 — list current user's images (simple paging). */
  @GetMapping
  public ResponseEntity<List<Dtos.ImageDto>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    if (page < 0 || size <= 0) {
      return ResponseEntity.badRequest().build();
    }

    UUID userId = userService.getCurrentUserIdOrThrow();
    List<Image> all = imageService.listByOwner(userId);

    int from = Math.min(page * size, all.size());
    int to = Math.min(from + size, all.size());

    List<Dtos.ImageDto> items = all.subList(from, to)
        .stream()
        .map(this::toDto)
        .collect(Collectors.toList());

    return ResponseEntity.ok(items);
  }

  /** GET /api/images/{id} — fetch a single image (ownership enforced in service). */
  @GetMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> get(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    UUID imageId = UUID.fromString(id);

    Image img = imageService.getById(userId, imageId);
    return ResponseEntity.ok(toDto(img));
  }

  /** PUT /api/images/{id} — update mutable fields. */
  @PutMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> update(
      @PathVariable String id,
      @RequestBody Dtos.UpdateImageRequest req) {

    UUID userId = userService.getCurrentUserIdOrThrow();
    UUID imageId = UUID.fromString(id);

    // Convert List<String> -> String[] for the service layer
    String[] labels = (req.labels() == null) ? null : req.labels().toArray(new String[0]);

    Image updated = imageService.update(
        userId,
        imageId,
        null,          // filename (not changed via this endpoint)
        null,          // storagePath (not changed via this endpoint)
        labels,        // labels
        req.note()     // note
    );

    return ResponseEntity.ok(toDto(updated));
  }


  /** DELETE /api/images/{id} — hard delete metadata record. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    UUID imageId = UUID.fromString(id);

    imageService.delete(userId, imageId);
    return ResponseEntity.noContent().build();
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

  // ---- Mapping helper ----
  // Matches Dtos.ImageDto(id, filename, ownerId, uploadedAt)
  private Dtos.ImageDto toDto(Image img) {
    return new Dtos.ImageDto(
      img.getId().toString(),
      img.getFilename(),
      img.getUserId().toString(),
      img.getUploadedAt(),
      (img.getLabels() == null ? List.of() : List.of(img.getLabels())),
      img.getNote()
    );
  }

}
