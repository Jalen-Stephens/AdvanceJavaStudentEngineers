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
 * HTTP adapter for image operations owned by the authenticated user.
 * Responsibilities:
 * - Thin controller, delegates orchestration to ImageService.
 * - Ownership and bearer validation handled via UserService and ImageService.
 * - Handles request/response shaping and HTTP-specific error mapping.
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

  private final ImageService imageService;
  private final UserService userService;

  /**
   * Constructs the controller with its required collaborators.
   *
   * @param imageService service providing DB + storage orchestration
   * @param userService service for retrieving caller identity/bearer
   */
  public ImageController(ImageService imageService, UserService userService) {
    this.imageService = imageService;
    this.userService = userService;
  }

  /**
   * Lists the current user's images using simple paging.
   * Validates page bounds up front to avoid service calls with invalid args.
   *
   * @param page zero-based page index
   * @param size number of items per page
   * @return paged list of ImageDto objects
   */
  @GetMapping
  public ResponseEntity<List<Dtos.ImageDto>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "5") int size) {

    if (page < 0 || size <= 0) {
      return ResponseEntity.badRequest().build();
    }

    UUID userId = userService.getCurrentUserIdOrThrow();
    List<Image> results = imageService.listByOwner(userId, page, size);

    List<Dtos.ImageDto> items =
        results.stream().map(this::toDto).collect(Collectors.toList());

    return ResponseEntity.ok(items);
  }

  /**
   * Returns a single image owned by the authenticated user.
   * Ownership is enforced at the service layer.
   *
   * @param id image identifier (raw string, validated to UUID)
   * @return 200 with the image if authorized
   */
  @GetMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> get(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    UUID imageId = parseUuidOrThrow(id);
    Image img = imageService.getById(userId, imageId);
    return ResponseEntity.ok(toDto(img));
  }

  /**
   * Updates mutable metadata fields for a stored image (labels/note).
   * Filename and storage path are intentionally not client-editable here.
   *
   * @param id image identifier
   * @param req fields to update
   * @return updated image metadata
   */
  @PutMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> update(
      @PathVariable String id,
      @RequestBody Dtos.UpdateImageRequest req) {

    UUID userId = userService.getCurrentUserIdOrThrow();
    UUID imageId = parseUuidOrThrow(id);

    // DTO exposes labels as a List<String>, convert to String[]
    String[] labels =
      (req.labels() == null) ? null : req.labels().toArray(new String[0]);

    Image updated = imageService.update(
        userId,
        imageId,
        null,  // filename not editable via this DTO
        null,  // storage path immutable here
        labels,
        req.note()
    );

    return ResponseEntity.ok(toDto(updated));
  }

  /**
   * Deletes metadata + underlying storage object in Supabase.
   * Service performs auth and RLS alignment.
   *
   * @param id image identifier
   * @return 204 if deletion succeeded
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    UUID imageId = parseUuidOrThrow(id);

    imageService.deleteAndPurge(userId, bearer, imageId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Uploads a new image binary + metadata, returning the created resource.
   *
   * @param file multipart file uploaded from the client
   * @return DTO describing the created image
   */
  @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Dtos.ImageDto> upload(
      @RequestPart("file") MultipartFile file) throws Exception {

    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    Image created = imageService.upload(userId, bearer, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
  }

  /**
   * Returns a short-lived signed URL for the private storage object.
   *
   * @param id image identifier
   * @return signed URL wrapped in a JSON map
   */
  @GetMapping("/{id}/url")
  public ResponseEntity<Object> signedUrl(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    UUID imageId = parseUuidOrThrow(id);

    String url = imageService.getSignedUrl(userId, bearer, imageId);
    return ResponseEntity.ok(Map.of("url", url));
  }

  // ---------------------------------------------------------------------------
  // Exception mapping
  // ---------------------------------------------------------------------------

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<String> handleNotFound(NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<String> handleForbidden(ForbiddenException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<String> handleBadRequest(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body("Invalid request: " + ex.getMessage());
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Parses a raw string to UUID or throws a client-facing 400.
   */
  private UUID parseUuidOrThrow(String raw) {
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid UUID: " + raw);
    }
  }

  /**
   * Maps domain Image to an API-facing DTO.
   */
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
