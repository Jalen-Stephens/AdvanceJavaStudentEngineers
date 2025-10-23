package dev.coms4156.project.metadetect.db;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RlsContext {
  private final JdbcTemplate jdbc;

  public RlsContext(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public <T> T asUser(UUID userId, Supplier<T> work) {
    String claims = "{\"sub\":\"" + userId + "\",\"role\":\"authenticated\"}";
    jdbc.update("set local request.jwt.claims = ?", claims);  // <-- no result
    return work.get();
  }

  @Transactional
  public void asUser(UUID userId, Runnable work) {
    String claims = "{\"sub\":\"" + userId + "\",\"role\":\"authenticated\"}";
    jdbc.update("set local request.jwt.claims = ?", claims);  // <-- no result
    work.run();
  }
}
