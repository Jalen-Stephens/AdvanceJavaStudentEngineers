package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.model.Image;
import dev.coms4156.project.metadetect.service.ImageService;
import dev.coms4156.project.metadetect.service.UserService;
import dev.coms4156.project.metadetect.service.errors.ForbiddenException;
import dev.coms4156.project.metadetect.service.errors.NotFoundException;
import dev.coms4156.project.metadetect.supabase.SupabaseStorageService;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * CRUD for image metadata (not binary files). Enforces ownership via ImageService.
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

  private final ImageService imageService;
  private final UserService userService;

  private final SupabaseStorageService storage;

  /**
   * Constructs the ImageController with required services for user-authenticated
   * image operations. The controller delegates ownership enforcement to the
   * UserService, database persistence to the ImageService, and binary storage
   * to the SupabaseStorageService.
   *
   * @param imageService service handling persistence and metadata for images
   * @param userService service for authentication and ownership checks
   * @param storage storage utility for uploading images and generating signed URLs
   */
  public ImageController(ImageService imageService, UserService userService,
                         SupabaseStorageService storage) {
    this.imageService = imageService;
    this.userService = userService;
    this.storage = storage;
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


  /** DELETE /api/images/{id} — hard delete metadata + storage object. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    UUID imageId = UUID.fromString(id);

    // Fetch to (a) verify ownership and (b) learn storage path
    var img = imageService.getById(userId, imageId); // implement/find method that 404s if not owner
    var storagePath = img.getStoragePath();


    if (storagePath != null && !storagePath.isBlank()) {
      storage.deleteObject(storagePath, bearer); // delete from bucket first
    }

    imageService.delete(userId, imageId);        // then remove DB row
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
      img.getLabels() == null ? List.of() : Arrays.asList(img.getLabels()),
      img.getNote()
    );
  }

  /** POST /api/images/upload — upload binary, persist metadata, return DTO. */
  @PostMapping(path = "/upload", consumes = "multipart/form-data")
  public ResponseEntity<Dtos.ImageDto> upload(@RequestPart("file") MultipartFile file)
      throws Exception {
    var userId = userService.getCurrentUserIdOrThrow();
    var bearer = userService.getCurrentBearerOrThrow();

    // Build storage key: <userId>/<imageId>--<originalFilename>
    String original = Optional.ofNullable(file.getOriginalFilename())
        .orElse("upload.bin")
        .replaceAll("[/\\\\]", "_");
    // Create DB row first to get the generated id (service-level create should exist)
    Image created = imageService.create(userId, original,
      /*storagePath*/ null, /*labels*/ null, /*note*/ null);

    String storageKey = userId + "/" + created.getId() + "--" + original;
    String contentType = file.getContentType() != null
        ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

    storage.uploadObject(
        file.getBytes(),
        Optional.ofNullable(file.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE),
        storageKey,
        bearer  // user JWT
    );

    // persist storage path
    Image updated = imageService.update(userId, created.getId(),
      /*filename*/ null, storageKey, /*labels*/ null, /*note*/ null);

    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(updated));
  }

  /** GET /api/images/{id}/url — return short-lived signed URL for private object. */
  @GetMapping("/{id}/url")
  public ResponseEntity<Object> signedUrl(@PathVariable String id) {
    var userId = userService.getCurrentUserIdOrThrow();
    var bearer = userService.getCurrentBearerOrThrow();
    var imageId = UUID.fromString(id);

    Image img = imageService.getById(userId, imageId);
    if (img.getStoragePath() == null || img.getStoragePath().isBlank()) {
      throw new NotFoundException("Image has no storage object");
    }
    String url = storage.createSignedUrl(img.getStoragePath(), bearer);
    return ResponseEntity.ok(java.util.Map.of("url", url));
  }

}
