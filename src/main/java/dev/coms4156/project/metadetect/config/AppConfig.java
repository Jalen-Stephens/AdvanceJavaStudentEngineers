package dev.coms4156.project.metadetect.config;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configuation class to provide constants to other processes. */
@Configuration
public class AppConfig {

  /**
  * Creates a C2paToolInvoker bean with the specified tool path.
  * This method initializes the C2paToolInvoker with the path to the C2PA tool binary.
  * The tool is used to extract metadata and manifests from image files.
  *
  * @return a C2paToolInvoker instance configured with the tool path.
  */
  @Bean
  public C2paToolInvoker c2paToolInvoker() {
    // Path to the C2PA tool binary
    String c2paToolPath = "tools/c2patool/c2patool";
    return new C2paToolInvoker(c2paToolPath);
  }
}