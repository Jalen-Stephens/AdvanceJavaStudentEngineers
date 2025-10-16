package dev.coms4156.project.metadetect.repo;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository for performing CRUD operations on the {@code users} table.
 *
 * <p>This class uses {@link JdbcTemplate} for direct SQL access and provides
 * simple data manipulation methods for inserting and retrieving users.</p>
 */
@Repository
public class UserRepository {
  private final JdbcTemplate jdbc;

  /**
   * Creates a new {@code UserRepository} using the provided JDBC template.
   *
   * @param jdbc the {@link JdbcTemplate} used for executing SQL queries
   */
  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Inserts a new user record into the {@code users} table.
   *
   * @param email the user's email address
   * @param passwordHash the hashed password to store
   * @return the unique {@link UUID} of the newly inserted user
   */
  public UUID insert(String email, String passwordHash) {
    return jdbc.queryForObject(
      "insert into users(email, password_hash) values (?, ?) returning id",
      (rs, i) -> (UUID) rs.getObject("id"),
      email, passwordHash);
  }
}
