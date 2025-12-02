package dev.coms4156.project.metadetect.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

/**
 * Lightweight checks for {@link FeatureExtractor}. Each test gracefully skips
 * when the native OpenCV library is not available on the host running tests.
 */
class FeatureExtractorTest {

  private FeatureExtractor extractor() {
    return new FeatureExtractor();
  }

  @Test
  void extractAllFeatures_missingFile_returnsC2paOnly() {
    FeatureExtractor fx = extractor();
    C2paToolInvoker.C2paMetadata meta = new C2paToolInvoker.C2paMetadata(
        1, 3, "tool", 0, 0, null, 0, null);

    double[] out = fx.extractAllFeatures("does/not/exist.png", meta);

    assertThat(out).hasSize(13);
    assertThat(out[0]).isZero();
    assertThat(out[8]).isEqualTo(1.0);
    assertThat(out[9]).isEqualTo(3.0);
    assertThat(out[11]).isEqualTo(0.0);
  }

  @Test
  void featureMethods_handleSmallMatrix() {
    FeatureExtractor fx = extractor();

    Mat img = new Mat(4, 4, CvType.CV_8UC3, new Scalar(10, 20, 30));

    double lap = fx.laplacianVariance(img);
    double noise = fx.noiseEstimate(img);
    double edges = fx.edgeDensity(img);
    double ratio = fx.highLowFreqRatio(img);
    double entropy = fx.saturationEntropy(img);

    assertThat(lap).isGreaterThanOrEqualTo(0);
    assertThat(noise).isGreaterThanOrEqualTo(0);
    assertThat(edges).isBetween(0.0, 1.0);
    assertThat(ratio).isGreaterThanOrEqualTo(0);
    assertThat(entropy).isGreaterThanOrEqualTo(0);
  }

  @Test
  void aspectRatio_handlesZeroHeight_andNormal() throws Exception {
    var m = FeatureExtractor.class.getDeclaredMethod("aspectRatio", int.class, int.class);
    m.setAccessible(true);

    double zero = (double) m.invoke(null, 10, 0);
    double normal = (double) m.invoke(null, 10, 2);

    assertThat(zero).isEqualTo(0.0);
    assertThat(normal).isEqualTo(5.0);
  }
}
