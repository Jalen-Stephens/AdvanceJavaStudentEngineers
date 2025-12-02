package dev.coms4156.project.metadetect.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogisticRegressionServiceTest {

  private FeatureExtractor featureExtractor;
  private ModelLoader modelLoader;
  private LogisticRegressionService service;

  @BeforeEach
  void setup() {
    featureExtractor = mock(FeatureExtractor.class);
    modelLoader = mock(ModelLoader.class);
    service = new LogisticRegressionService(featureExtractor, modelLoader);
    reset(featureExtractor, modelLoader);
  }

  @Test
  void predict_happyPath_usesFeaturesAndModelAndFlagsC2pa() {
    when(modelLoader.loadModel()).thenReturn(
        new ModelLoader.ModelParameters(new double[] {0.5, 0.5}, 0.0, "v42")
    );
    when(featureExtractor.extractAllFeatures(eq("img.jpg"), any()))
        .thenReturn(new double[] {1.0, 2.0});

    var c2pa = new C2paToolInvoker.C2paMetadata(1, 1, "gen", 0, 0, null, 0, null);

    LogisticRegressionService.InferenceResult result = service.predict("img.jpg", c2pa);

    assertThat(result.modelVersion()).isEqualTo("v42");
    assertThat(result.c2paUsed()).isTrue();
    assertThat(result.confidenceScore()).isCloseTo(0.8176, within(1e-3));
  }

  @Test
  void predict_truncatesWhenWeightsAndFeaturesDiffer_andMarksC2paFalseOnErrorFlag() {
    when(modelLoader.loadModel()).thenReturn(
        new ModelLoader.ModelParameters(new double[] {2.0, 2.0, 5.0}, -1.0, "v2")
    );
    when(featureExtractor.extractAllFeatures(eq("img2.jpg"), any()))
        .thenReturn(new double[] {1.0, 1.0});

    var c2pa = new C2paToolInvoker.C2paMetadata(1, 1, "gen", 0, 0, null, 1, "err");

    LogisticRegressionService.InferenceResult result = service.predict("img2.jpg", c2pa);

    assertThat(result.c2paUsed()).isFalse(); // error flag disables
    assertThat(result.modelVersion()).isEqualTo("v2");
    // dot = (2*1)+(2*1)=4, bias=-1 -> z=3 => sigmoid ~0.9526
    assertThat(result.confidenceScore()).isCloseTo(0.9526, within(1e-3));
  }

  @Test
  void predict_handlesNegativeZ_viaSigmoidLowerBranch() {
    when(modelLoader.loadModel()).thenReturn(
        new ModelLoader.ModelParameters(new double[] {-10.0}, 0.0, "v-neg")
    );
    when(featureExtractor.extractAllFeatures(eq("img3.jpg"), any()))
        .thenReturn(new double[] {1.0});

    LogisticRegressionService.InferenceResult result = service.predict(
        "img3.jpg",
        new C2paToolInvoker.C2paMetadata(0, 0, null, 0, 0, null, 0, null)
    );

    assertThat(result.c2paUsed()).isFalse();
    assertThat(result.confidenceScore()).isCloseTo(0.0000454, within(1e-6));
    assertThat(result.modelVersion()).isEqualTo("v-neg");
  }

  @Test
  void getModelVersion_delegatesToModelLoader() {
    when(modelLoader.loadModel()).thenReturn(
        new ModelLoader.ModelParameters(new double[] {1.0}, 0.0, "v-get")
    );

    assertThat(service.getModelVersion()).isEqualTo("v-get");
  }

  private static org.assertj.core.data.Offset<Double> within(double tolerance) {
    return org.assertj.core.data.Offset.offset(tolerance);
  }
}
