package dev.coms4156.project.metadetect.c2pa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Invokes the C2PA command-line tool to extract ML-friendly metadata from images. */
public class C2paToolInvoker {

  private static final Logger log = LoggerFactory.getLogger(C2paToolInvoker.class);

  /**
   * Keywords used to flag AI generators in the claim_generator field.
   * This list is intentionally conservative and can be refined in future iterations.
   */
  private static final List<String> AI_GENERATOR_KEYWORDS = Arrays.asList(
      "firefly",
      "dall-e",
      "dalle",
      "midjourney",
      "stable diffusion",
      "stability",
      "sdxl",
      "gpt",
      "openai",
      "bing image creator",
      "copilot",
      "canva",
      "runway"
  );

  /**
   * Keywords used to conservatively infer that an asset is a screenshot.
   * This list intentionally focuses on obvious OS/browser capture tools.
   */
  private static final List<String> SCREENSHOT_KEYWORDS = Arrays.asList(
      "screenshot",
      "screen shot",
      "screen capture",
      "screen-capture",
      "screen grab",
      "screen-grab",
      "snipping tool",
      "snip & sketch",
      "snip and sketch",
      "windows snip",
      "macos screenshot",
      "screencapture"
  );

  private final String c2paToolPath;

  /**
   * Kept for backwards compatibility / reuse in logs; no longer used for exceptions.
   */
  public static final String NO_C2PA_MANIFEST_MESSAGE =
      "This image does not contain C2PA data (no C2PA manifest found).";

  public C2paToolInvoker(String c2paToolPath) {
    this.c2paToolPath = c2paToolPath;
  }



  /**
   * Backwards-compatible wrapper used by existing code/tests.
   * Historically, the service used a plain JSON string "manifest" coming
   * from c2patool. We now normalize that into {@link C2paMetadata}, but
   * expose this helper for callers that still expect a String.
   * Contract:
   * - Never throws.
   * - Returns a JSON serialization of {@link C2paMetadata}.
   */
  @Deprecated
  public String extractManifest(File imageFile) {
    C2paMetadata metadata = extractMetadata(imageFile);
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.writeValueAsString(metadata);
    } catch (Exception e) {
      String msg = "Failed to serialize C2PA metadata to JSON: " + e;
      log.warn(msg, e);
      C2paMetadata error = C2paMetadata.error(msg);
      try {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(error);
      } catch (Exception inner) {
        log.warn("Secondary failure serializing error C2PA metadata: {}", inner.toString());
        // Last-resort JSON so callers never get null
        return "{\"c2paErrorFlag\":1,\"c2paErrorMessage\":\"serialization failure\"}";
      }
    }
  }
  
  /**
   * Executes the C2PA tool and converts its output into ML-ready metadata.
   *
   * <p>Contract:
   * <ul>
   *   <li>Never throws to callers.</li>
   *   <li>Always returns a fully populated {@link C2paMetadata} instance.</li>
   *   <li>"No claim found" is treated as a soft success (no manifest, no error).</li>
   *   <li>Unexpected CLI / JSON errors set {@code c2paErrorFlag = 1}.</li>
   * </ul>
   */
  public C2paMetadata extractMetadata(File imageFile) {
    if (imageFile == null) {
      String msg = "imageFile is null";
      log.warn(msg);
      return C2paMetadata.error(msg);
    }

    File toolBinary = new File(c2paToolPath).getAbsoluteFile();
    if (!toolBinary.exists()) {
      String msg = "C2PA tool not found at path: " + toolBinary;
      log.warn(msg);
      return C2paMetadata.error(msg);
    }
    if (!toolBinary.canExecute()) {
      log.warn("C2PA tool at {} is not executable; invocation may fail", toolBinary);
    }

    try {
      // Invoke the binary directly (no shell) to avoid accidental script parsing on Heroku.
      ProcessBuilder pb = new ProcessBuilder(
          toolBinary.getAbsolutePath(),
          imageFile.getAbsolutePath(),
          "-d" // detailed JSON output
      );
      if (toolBinary.getParentFile() != null) {
        pb.directory(toolBinary.getParentFile());
      }
      pb.redirectErrorStream(false);

      Process proc = pb.start();
      try (InputStream out = proc.getInputStream();
           InputStream err = proc.getErrorStream();
           Scanner so = new Scanner(out, StandardCharsets.UTF_8);
           Scanner se = new Scanner(err, StandardCharsets.UTF_8)) {

        int exit = proc.waitFor();
        String stdout = so.useDelimiter("\\A").hasNext() ? so.next() : "";
        String stderr = se.useDelimiter("\\A").hasNext() ? se.next() : "";

        if (exit != 0) {
          String lowerStderr = stderr == null ? "" : stderr.toLowerCase();

          // Soft failure: no manifest present
          if (!lowerStderr.isBlank() && lowerStderr.contains("no claim found")) {
            log.debug("c2patool reported no claim found for file {}: {}",
                imageFile.getName(), stderr);
            return C2paMetadata.noManifest();
          }

          // Hard CLI failure: record as error for ML layer
          String msg = "C2PA tool failed with exit code " + exit
              + (stderr == null || stderr.isBlank() ? "" : " | stderr: " + stderr.trim());
          log.warn(msg);
          return C2paMetadata.error(msg);
        }

        if (stdout == null || stdout.isBlank()) {
          String msg = "c2patool returned empty output for file " + imageFile.getName();
          log.warn(msg);
          return C2paMetadata.error(msg);
        }

        // Raw JSON is still easy to inspect locally via logs.
        log.debug("c2patool output for {}: {}", imageFile.getName(), stdout);
        return parseMetadataFromJson(stdout);

      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        String msg = "C2PA tool execution was interrupted: " + ie;
        log.warn(msg);
        return C2paMetadata.error(msg);
      }
    } catch (Exception e) {
      String msg = "Unexpected error while invoking C2PA tool (" + toolBinary + "): " + e;
      log.warn(msg, e);
      return C2paMetadata.error(msg);
    }
  }

  /**
   * Parses the JSON output from {@code c2patool -d} into the ML metadata schema.
   *
   * <p>On any JSON/shape error, this returns a metadata object with errorFlag=1.
   */
  private C2paMetadata parseMetadataFromJson(String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(json);
      if (root == null || root.isMissingNode()) {
        String msg = "Unable to parse c2patool JSON output (root is missing)";
        log.warn(msg);
        return C2paMetadata.error(msg);
      }

      int manifestCount = 0;
      int hasManifest = 0;
      String claimGenerator = null;
      int claimGeneratorIsAi = 0;
      int c2paIsScreenshot = 0;
      String c2paScreenshotReason = null;
      JsonNode claimNode = null;

      // Count manifests
      JsonNode manifestsNode = root.get("manifests");
      if (manifestsNode != null && manifestsNode.isObject()) {
        manifestCount = manifestsNode.size();
      }

      if (manifestCount > 0) {
        hasManifest = 1;

        // Resolve active manifest (preferred) or fall back to first manifest.
        JsonNode activeManifestField = root.get("active_manifest");
        String activeManifestId =
            activeManifestField != null && !activeManifestField.isNull()
                ? activeManifestField.asText(null)
                : null;

        JsonNode activeManifestNode = null;
        if (activeManifestId != null && manifestsNode != null && manifestsNode.isObject()) {
          activeManifestNode = manifestsNode.get(activeManifestId);
        }
        if (activeManifestNode == null && manifestsNode != null && manifestsNode.isObject()) {
          var it = manifestsNode.elements();
          if (it.hasNext()) {
            activeManifestNode = it.next();
          }
        }

        if (activeManifestNode != null && !activeManifestNode.isMissingNode()) {
          claimNode = activeManifestNode.get("claim");
          if (claimNode == null || claimNode.isMissingNode()) {
            claimNode = activeManifestNode;
          }
          // 1) New standard location: claim.claim_generator_info.name
          JsonNode genInfo = claimNode.get("claim_generator_info");
          if (genInfo != null && genInfo.has("name")) {
            claimGenerator = genInfo.get("name").asText(null);
          }
          // 2) Legacy location: claim.claim_generator
          if (claimGenerator == null) {
            JsonNode cgLegacy = claimNode.get("claim_generator");
            if (cgLegacy != null && !cgLegacy.isNull()) {
              claimGenerator = cgLegacy.asText(null);
            }
          }
        }
      } else {
        hasManifest = 0;
      }

      if (claimGenerator != null && !claimGenerator.isBlank()
          && isAiClaimGenerator(claimGenerator)) {
        claimGeneratorIsAi = 1;
      } else {
        claimGeneratorIsAi = 0;
      }

      // Heuristic: detect screenshot tools via claim generator or capture_type.
      if (claimGenerator != null && !claimGenerator.isBlank()) {
        String gen = claimGenerator.toLowerCase();
        for (String kw : SCREENSHOT_KEYWORDS) {
          if (gen.contains(kw)) {
            c2paIsScreenshot = 1;
            c2paScreenshotReason = "claim_generator contains \"" + kw + "\"";
            break;
          }
        }
      }
      if (c2paIsScreenshot == 0 && claimNode != null) {
        JsonNode captureType = claimNode.get("capture_type");
        if (captureType == null) {
          captureType = claimNode.get("captureType");
        }
        String captureVal = captureType != null && !captureType.isNull()
            ? captureType.asText(null)
            : null;
        if (captureVal != null && captureVal.equalsIgnoreCase("screenshot")) {
          c2paIsScreenshot = 1;
          c2paScreenshotReason = "capture_type indicates screenshot";
        }
      }

      return new C2paMetadata(
          hasManifest,
          manifestCount,
          claimGenerator,
          claimGeneratorIsAi,
          c2paIsScreenshot,
          c2paScreenshotReason,
          /*c2paErrorFlag*/ 0,
          /*c2paErrorMessage*/ null
      );
    } catch (Exception e) {
      String msg = "Failed to parse c2patool JSON output: " + e;
      log.warn(msg, e);
      return C2paMetadata.error(msg);
    }
  }

  /**
   * Exposed for tests to validate JSON parsing on platforms where the bundled
   * c2patool binary cannot execute (e.g., non-macOS CI runners).
   */
  public C2paMetadata parseMetadataFromJsonForTests(String json) {
    return parseMetadataFromJson(json);
  }

  private static boolean isAiClaimGenerator(String generator) {
    if (generator == null) {
      return false;
    }
    String lower = generator.toLowerCase();
    for (String kw : AI_GENERATOR_KEYWORDS) {
      if (lower.contains(kw)) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------
  // ML-ready metadata schema for future model integration.
  // ---------------------------------------------------------------------------

  /**
   * C2PA metadata schema suitable for ML ingestion.
   * All fields are always populated per contract.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @JsonPropertyOrder({
      "c2paHasManifest",
      "c2paManifestCount",
      "c2paClaimGenerator",
      "c2paClaimGeneratorIsAi",
      "c2paIsScreenshot",
      "c2paScreenshotReason",
      "c2paErrorFlag",
      "c2paErrorMessage"
  })
  public static final class C2paMetadata {

    private final int c2paHasManifest;
    private final int c2paManifestCount;
    private final String c2paClaimGenerator;
    private final int c2paClaimGeneratorIsAi;
    private final int c2paIsScreenshot;
    private final String c2paScreenshotReason;
    private final int c2paErrorFlag;
    private final String c2paErrorMessage;

    /**
     * Constructs a C2PA metadata instance with all fields populated.
     *
     * @param c2paHasManifest integer flag: 1 if manifest present, 0 if not
     * @param c2paManifestCount number of manifests detected in the file
     * @param c2paClaimGenerator claim generator string from the active manifest
     * @param c2paClaimGeneratorIsAi integer flag: 1 if AI generator detected, 0 if not
     * @param c2paIsScreenshot integer flag: 1 if screenshot heuristics matched, 0 otherwise
     * @param c2paScreenshotReason optional note describing which heuristic fired
     * @param c2paErrorFlag integer flag: 1 if error occurred, 0 if no error
     * @param c2paErrorMessage error message if errorFlag=1, null otherwise
     */
    public C2paMetadata(
        int c2paHasManifest,
        int c2paManifestCount,
        String c2paClaimGenerator,
        int c2paClaimGeneratorIsAi,
        int c2paIsScreenshot,
        String c2paScreenshotReason,
        int c2paErrorFlag,
        String c2paErrorMessage) {

      this.c2paHasManifest = c2paHasManifest;
      this.c2paManifestCount = c2paManifestCount;
      this.c2paClaimGenerator = c2paClaimGenerator;
      this.c2paClaimGeneratorIsAi = c2paClaimGeneratorIsAi;
      this.c2paIsScreenshot = c2paIsScreenshot;
      this.c2paScreenshotReason = c2paScreenshotReason;
      this.c2paErrorFlag = c2paErrorFlag;
      this.c2paErrorMessage = c2paErrorMessage;
    }

    /** Factory for the “no manifest” soft-success case. */
    public static C2paMetadata noManifest() {
      return new C2paMetadata(
          /*c2paHasManifest*/ 0,
          /*c2paManifestCount*/ 0,
          /*c2paClaimGenerator*/ null,
          /*c2paClaimGeneratorIsAi*/ 0,
          /*c2paIsScreenshot*/ 0,
          /*c2paScreenshotReason*/ null,
          /*c2paErrorFlag*/ 0,
          /*c2paErrorMessage*/ null
      );
    }

    /** Factory for hard failures (CLI / JSON / unexpected). */
    public static C2paMetadata error(String message) {
      return new C2paMetadata(
          /*c2paHasManifest*/ 0,
          /*c2paManifestCount*/ 0,
          /*c2paClaimGenerator*/ null,
          /*c2paClaimGeneratorIsAi*/ 0,
          /*c2paIsScreenshot*/ 0,
          /*c2paScreenshotReason*/ null,
          /*c2paErrorFlag*/ 1,
          /*c2paErrorMessage*/ message
      );
    }

    public int getc2paHasManifest() {
      return c2paHasManifest;
    }

    public int getc2paManifestCount() {
      return c2paManifestCount;
    }

    public String getc2paClaimGenerator() {
      return c2paClaimGenerator;
    }

    public int getc2paClaimGeneratorIsAi() {
      return c2paClaimGeneratorIsAi;
    }

    public int getc2paIsScreenshot() {
      return c2paIsScreenshot;
    }

    public String getc2paScreenshotReason() {
      return c2paScreenshotReason;
    }

    public int getc2paErrorFlag() {
      return c2paErrorFlag;
    }

    public String getc2paErrorMessage() {
      return c2paErrorMessage;
    }
  }
}
