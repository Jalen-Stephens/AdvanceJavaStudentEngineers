package dev.coms4156.project.metadetect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class ModelLoaderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

  @Test
  void loadModel_fromClasspath_parsesAndCaches() {
    ModelLoader loader = new ModelLoader(
        resourceLoader,
        mapper,
        "classpath:model/test-model.json"
    );

    ModelLoader.ModelParameters first = loader.loadModel();
    ModelLoader.ModelParameters second = loader.loadModel();

    assertThat(first).isSameAs(second);
    assertThat(first.weights()).containsExactly(0.1, 0.2, 0.3);
    assertThat(first.bias()).isEqualTo(0.5);
    assertThat(first.version()).isEqualTo("v-test");
  }

  @Test
  void loadModel_withBareFilePath_defaultsToFilePrefixAndNormalizesBiasAndVersion()
      throws IOException {
    Path tmp = Files.createTempFile("model-", ".json");
    Files.writeString(tmp, """
        {"weights":[0.7],"bias":1e309,"version":"  "}
        """);

    ModelLoader loader = new ModelLoader(
        resourceLoader,
        mapper,
        tmp.toAbsolutePath().toString()
    );

    ModelLoader.ModelParameters params = loader.loadModel();
    assertThat(params.weights()).containsExactly(0.7);
    assertThat(params.bias()).isZero(); // NaN bias coerced to 0.0
    assertThat(params.version()).isEqualTo("v1"); // blank version defaults
  }

  @Test
  void loadModel_missingFile_throwsIllegalState() {
    ModelLoader loader = new ModelLoader(
        resourceLoader,
        mapper,
        "/does/not/exist/model.json"
    );

    assertThatThrownBy(loader::loadModel)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Model file not found");
  }

  @Test
  void loadModel_invalidWeights_throwsIllegalState() throws IOException {
    Path tmp = Files.createTempFile("model-invalid-", ".json");
    Files.writeString(tmp, """
        {"weights":[1.0, 1e309],"bias":0.0,"version":"vbad"}
        """);

    ModelLoader loader = new ModelLoader(
        resourceLoader,
        mapper,
        "file:" + tmp.toAbsolutePath()
    );

    assertThatThrownBy(loader::loadModel)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid values");
  }
}
