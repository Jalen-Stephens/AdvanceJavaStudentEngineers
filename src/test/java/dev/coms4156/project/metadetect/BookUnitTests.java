package dev.coms4156.project.metadetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.individualproject.model.Book;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * This class contains the unit tests for the Book class.
 */
@SpringBootTest
public class BookUnitTests {

  public static Book book;

  //Switched to before each because I wanted a new book before every test
  @BeforeEach
  void setUpBookForTesting() {
    book = new Book("The Cat in The Hat", 0);
  }

  @Test
  public void equalsBothAreTheSameTest() {
    Book cmpBook = book;
    assertEquals(cmpBook, book);
  }

  @Test
  void equalsFalseInvalidType() {
    Book cmpBook = book;
    Object falseBook = "fake book";
    assertFalse(cmpBook.equals(falseBook));
  }

  @Test
  void multipleAuthorsFalseWithNoAuthors() {
    Book cmpBook = book;
    assertFalse(cmpBook.hasMultipleAuthors());
  }

  @Test
  void multipleAuthorsFalseWithOneAuthor() {
    Book cmpBook = book;
    List<String> author = new ArrayList<>();
    author.add("Dr.Seuss");
    cmpBook.setAuthors(author);
    assertFalse(cmpBook.hasMultipleAuthors());
  }

  @Test
  public void equalsBookAuthors() {

    Book testBook = book;
    List<String> authors = new ArrayList<>();
    authors.add("Dr.Seuss");
    testBook.setAuthors(authors);

    List<String> expectedAuthors = new ArrayList<>();
    expectedAuthors.add("Dr.Seuss");

    assertEquals(expectedAuthors, testBook.getAuthors());
  }

  @Test
  public void multipleAuthors() {

    Book testBook = book;
    List<String> authors = new ArrayList<>();
    authors.add("Dr.Seuss");
    authors.add("Stephen King");
    testBook.setAuthors(authors);
    assertTrue(testBook.hasMultipleAuthors());
  }

  @Test
  public void oneAuthor() {
    Book testBook = book;
    List<String> authors = new ArrayList<>();
    authors.add("Dr.Seuss");
    testBook.setAuthors(authors);
    assertEquals(1, testBook.getAuthors().size());
  }

  @Test
  public void noAuthor() {
    Book testBook = book;
    assertTrue(testBook.getAuthors().size() < 1);
  }

  @Test
  public void multipleCopies() {
    Book testBook = book;
    testBook.setTotalCopies(2);
    assertTrue(testBook.hasCopies());
  }

  @Test
  public void noCopies() {
    Book testBook = book;
    testBook.setTotalCopies(0);
    assertFalse(testBook.hasCopies());
  }

  @Test
  public void equalsBookLanguage() {
    Book testBook = book;
    testBook.setLanguage("English");
    assertEquals("English", testBook.getLanguage());
  }


  @Test
  public void negCompareTo() {
    Book testBook = book;
    Book book2 = new Book("Green Eggs and Ham", 1);
    assertTrue(testBook.compareTo(book2) < 0);
  }

  @Test
  public void equalCompareTo() {
    Book testBook = book;
    Book book2 = book;
    assertEquals(0, testBook.compareTo(book2));
  }

  @Test
  public void posCompareTo() {
    Book testBook = book;
    Book book2 = new Book("Green Eggs and Ham", 1);
    assertTrue(book2.compareTo(testBook) > 0);
  }

  @Test
  public void equalsBookShelvingLocation() {
    Book testBook = book;
    testBook.setShelvingLocation("Butler Library - Fiction");
    assertEquals("Butler Library - Fiction", testBook.getShelvingLocation());
  }

  @Test
  public void equalsBooks() {
    Book testBook = book;
    Book book2 = book;
    assertTrue(testBook.equals(book2));
  }

  @Test
  public void notEqualsBook() {
    Book testBook = book;
    Book book2 = new Book("Green Eggs and Ham", 1);
    assertFalse(testBook.equals(book2));
  }

  @Test
  public void nullEqualsBook() {
    Book testBook = book;
    Book cmpBook = null;
    assertFalse(testBook.equals(cmpBook));
  }

  @Test
  public void testEqualAvailAndCopiesDeleteCopy() {
    Book testBook = book;
    testBook.setTotalCopies(5);
    assertTrue(testBook.deleteCopy());
  }

  @Test
  public void timesCheckedOut() {
    Book testBook = book;
    testBook.checkoutCopy();
    assertEquals(1, testBook.getAmountOfTimesCheckedOut());
  }

  @Test
  public void testLessAvailAndCopiesDeleteCopy() {
    Book testBook = book;
    testBook.setTotalCopies(2);
    testBook.checkoutCopy();
    testBook.checkoutCopy();
    assertFalse(testBook.deleteCopy());
  }

  @Test
  public void copiesAvailCheckoutCopy() {
    Book testBook = book;
    testBook.setTotalCopies(2);
    assertNotNull(testBook.checkoutCopy());
  }

  @Test
  public void noCopiesCheckoutCopy() {
    Book testBook = book;
    testBook.setTotalCopies(0);
    assertNull(testBook.checkoutCopy());
  }

  @Test
  public void trueReturnCopy() {
    Book testBook = book;
    String date = testBook.checkoutCopy();
    assertTrue(testBook.returnCopy(date));
  }

  @Test
  public void emptyReturnCopy() {
    Book testBook = book;
    assertFalse(testBook.returnCopy("2025-09-24"));
  }

  @Test
  public void setReturnDates() {
    List<String> dates = new ArrayList<>();
    dates.add("2025-09-24");
    dates.add("2025-09-25");
    dates.add("2025-09-26");

    Book testBook = book;
    testBook.setReturnDates(dates);
    assertEquals(dates, testBook.getReturnDates());
  }

  @Test
  public void emptyReturnDates() {
    Book testBook = book;
    List<String> dates = new ArrayList<>();
    testBook.setReturnDates(dates);
    assertNotNull(testBook.getReturnDates());
  }
}
