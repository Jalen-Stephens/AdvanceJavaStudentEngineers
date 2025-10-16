package dev.coms4156.project.metadetect;

import dev.coms4156.project.metadetect.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simple startup configuration that inserts a demo user into the database
 * at application startup to verify that the database connection and
 * repository layer are working correctly.
 *
 * <p>This class is primarily used for Iteration 1 smoke testing and can
 * be safely removed or disabled in later iterations once full services
 * and controllers are implemented.</p>
 */
@Configuration
public class BootSmoke {

  /**
   * Inserts a test user record into the database when the application starts.
   *
   * @param users the {@link UserRepository} used to insert the demo user
   * @return a {@link CommandLineRunner} that performs the insertion
   */
  @Bean
  CommandLineRunner smoke(UserRepository users) {
    return args -> {
      try {
        var id = users.insert("smoke@example.com", "hash123");
        System.out.println("[BOOT] Inserted demo user id=" + id);
      } catch (Exception e) {
        // Expected if the demo user already exists — safe to ignore for smoke testing
        System.out.println("[BOOT] Skipping demo user insert: " + e.getMessage());
      }

    };
  }
}
