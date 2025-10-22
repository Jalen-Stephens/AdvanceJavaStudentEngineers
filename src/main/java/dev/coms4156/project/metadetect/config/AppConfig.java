package dev.coms4156.project.metadetect.config;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public C2paToolInvoker c2paToolInvoker() {
        // Path to the C2PA tool binary
        String c2paToolPath = "tools/c2patool/c2patool";
        return new C2paToolInvoker(c2paToolPath);
    }
}