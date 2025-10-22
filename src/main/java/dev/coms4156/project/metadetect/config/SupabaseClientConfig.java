package dev.coms4156.project.metadetect.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Preconfigured WebClient for calling Supabase Auth endpoints.
 * Uses the anon public key (never the service role key).
 */
@Configuration
public class SupabaseClientConfig {

  /**
   * Builds a preconfigured WebClient for Supabase Auth requests
   * using the anon (public) key and project base URL.
   *
   * @param baseUrl the Supabase project base url
   * @param anonKey the public anon key for calling auth endpoints
   * @return configured WebClient
   */
  @Bean
  public WebClient supabaseWebClient(
      @Value("${metadetect.supabase.url}") String baseUrl,
      @Value("${metadetect.supabase.anonKey}") String anonKey
  ) {
    return WebClient.builder()
      .baseUrl(baseUrl)
      .defaultHeader("apikey", anonKey)
      .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + anonKey)
      .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
      // allow reasonably sized responses without truncation
      .exchangeStrategies(ExchangeStrategies.builder()
        .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB
        .build())
      .build();
  }
}
