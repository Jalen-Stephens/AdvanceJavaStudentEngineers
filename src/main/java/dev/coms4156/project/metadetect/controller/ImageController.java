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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Images", description = "Upload, list, update, and delete user images")
@SecurityRequirement(name = "bearerAuth")
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
  @Operation(
    summary = "List images for current user",
    description = "Returns a page of images owned by the authenticated user. "
        + "`page` is zero-based; `size` is the page size."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Images returned successfully"),
      @ApiResponse(responseCode = "400",
          description = "Invalid page/size parameters")
  })
  @GetMapping
  public ResponseEntity<List<Dtos.ImageDto>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "5") int size) {

    if (page < 0 || size <= 0) {
      return ResponseEntity.badRequest().build();
    }

    UUID userId = userService.getCurrentUserIdOrThrow();
    List<Image> results = imageService.listByOwner(userId, page, size);

    List<Dtos.ImageDto> items = results.stream().map(this::toDto).collect(Collectors.toList());
    return ResponseEntity.ok(items);
  }

  /** GET /api/images/{id} — fetch a single image (ownership enforced in service). */
  @Operation(
    summary = "Get a single image",
    description = "Fetches a single image by ID, provided it is owned by the current user."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Image returned successfully"),
      @ApiResponse(responseCode = "404",
          description = "Image not found or not owned by user")
  })
  @GetMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> get(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    UUID imageId = parseUuidOrThrow(id);
    Image img = imageService.getById(userId, imageId);
    return ResponseEntity.ok(toDto(img));
  }

  /** PUT /api/images/{id} — update mutable metadata (labels, note). */
  @Operation(
    summary = "Update image metadata",
    description = "Updates mutable fields (labels, note) for an image owned by the current user."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Image updated successfully"),
      @ApiResponse(responseCode = "404",
          description = "Image not found or not owned by user")
  })
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
  @Operation(
    summary = "Delete an image",
    description = "Deletes both DB metadata and backing storage object for the given image, "
        + "if owned by the current user."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "204",
          description = "Image deleted successfully"),
      @ApiResponse(responseCode = "404",
          description = "Image not found or not owned by user")
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    UUID imageId = parseUuidOrThrow(id);

    imageService.deleteAndPurge(userId, bearer, imageId);
    return ResponseEntity.noContent().build();
  }

  /** POST /api/images/upload — upload binary, persist metadata, return DTO. */
  @Operation(
    summary = "Upload a new image",
    description = "Uploads a binary image file, stores it in Supabase, persists metadata, "
        + "and returns an Image DTO for the created record."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "201",
          description = "Image uploaded and created successfully"),
      @ApiResponse(responseCode = "400",
          description = "Invalid file or request"),
      @ApiResponse(responseCode = "413",
          description = "File too large (if enforced by gateway)")
  })
  @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Dtos.ImageDto> upload(@RequestPart("file") MultipartFile file)
      throws Exception {
    UUID userId = userService.getCurrentUserIdOrThrow();
    String bearer = userService.getCurrentBearerOrThrow();
    Image created = imageService.upload(userId, bearer, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
  }

  /** GET /api/images/{id}/url — return short-lived signed URL for private object. */
  @Operation(
    summary = "Get signed download URL",
    description = "Returns a short-lived signed URL that allows the current user to download "
        + "the underlying image object."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200",
          description = "Signed URL returned successfully"),
      @ApiResponse(responseCode = "404",
          description = "Image not found or not owned by user")
  })
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
