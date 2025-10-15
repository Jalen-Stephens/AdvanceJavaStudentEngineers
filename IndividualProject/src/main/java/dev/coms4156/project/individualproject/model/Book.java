package dev.coms4156.project.individualproject.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class defines the BOOK entry model.
 */
public class Book implements Comparable<Book> {
  private String title;
  private List<String> authors;
  private String language;
  private String shelvingLocation;
  private String publicationDate;
  private String publisher;
  private List<String> subjects;
  private int id;
  private int amountOfTimesCheckedOut;
  private int copiesAvailable;
  private List<String> returnDates;
  private int totalCopies;

  /**
   * Very basic BOOK constructor.
   *
   * @param title The title of the BOOK.
   * @param id The id of the BOOK.
   */
  public Book(String title, int id) {
    this.title = title;
    this.id = id;
    this.authors = new ArrayList<>();
    this.language = "";
    this.shelvingLocation = "";
    this.publicationDate = "";
    this.publisher = "";
    this.subjects = new ArrayList<>();
    this.amountOfTimesCheckedOut = 0;
    this.copiesAvailable = 1;
    this.returnDates = new ArrayList<>();
    this.totalCopies = 1;
  }

  /**
   * Complete BOOK constructor.
   *
   * @param title title of the BOOK.
   * @param authors list of author(s).
   * @param language language of the BOOK.
   * @param shelvingLocation shelving location of the BOOK.
   * @param publicationDate publication date of the BOOK.
   * @param publisher publisher of the BOOK.
   * @param subjects list of subject(s) of the BOOK.
   * @param id unique id of the BOOK.
   * @param copiesAvailable number of copies available of the BOOK.
   * @param totalCopies number of available and checked-out copies of the BOOK.
   */
  public Book(String title, List<String> authors, String language, String shelvingLocation,
              String publicationDate, String publisher, List<String> subjects,
              int id, int copiesAvailable, int totalCopies) {
    this.title = title;
    this.authors = authors;
    this.language = language;
    this.shelvingLocation = shelvingLocation;
    this.publicationDate = publicationDate;
    this.publisher = publisher;
    this.subjects = subjects;
    this.id = id;
    this.amountOfTimesCheckedOut = 0;
    this.copiesAvailable = copiesAvailable;
    this.returnDates = new ArrayList<>();
    this.totalCopies = totalCopies;
  }

  /**
   * No args constructor for Jackson.
   */
  public Book() {
    this.authors = new ArrayList<>();
    this.subjects = new ArrayList<>();
    this.returnDates = new ArrayList<>();
    this.language = "";
    this.shelvingLocation = "";
    this.publicationDate = "";
    this.publisher = "";
    this.title = "";
    this.amountOfTimesCheckedOut = 0;
    this.copiesAvailable = 1;
    this.totalCopies = 1;
    this.id = 0;
  }

  public boolean hasCopies() {
    return copiesAvailable > 0;
  }

  public boolean hasMultipleAuthors() {
    return authors.size() > 1;
  }

  /**
   * Deletes a single copy of the BOOK if at least one copy exists and is available.
   *
   * @return {@code true} if a copy was successfully deleted; {@code false} if no copies
   *         are available or exist to delete.
   */

  public boolean deleteCopy() {
    if (totalCopies > 0 && copiesAvailable > 0) {
      totalCopies--;
      copiesAvailable--;
      return true;
    }
    return false;
  }

  public void addCopy() {
    this.copiesAvailable++;
    this.totalCopies++;
  }

  /**
   * Checks out a copy of the BOOK if available and generates a due date two weeks from today.
   *
   * @return A {@code String} representing the due date if the checkout is successful;
   *         otherwise, {@code null} if no copies are available.
   */

  public String checkoutCopy() {
    if (copiesAvailable > 0) {
      copiesAvailable--;
      amountOfTimesCheckedOut++;
      LocalDate today = LocalDate.now();
      LocalDate dueDate = today.plusWeeks(2);
      String dueDateStr = dueDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
      returnDates.add(dueDateStr);
      return dueDateStr;
    }

    return null;
  }

  /**
   * Returns a previously checked-out copy of the BOOK corresponding to the given due date.
   *
   * @param date A {@code String} representing the due date of the BOOK being returned.
   * @return {@code true} if the return was successful and a matching date was removed;
   *         {@code false} if no matching due date is found.
   */
  public boolean returnCopy(String date) {
    if (!returnDates.isEmpty()) {
      for (int i = 0; i < returnDates.size(); i++) {
        if (returnDates.get(i).equals(date)) {
          returnDates.remove(i);
          copiesAvailable++;
          return true;
        }
      }
    }

    return false;
  }


  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public List<String> getAuthors() {
    return authors;
  }

  public void setAuthors(List<String> authors) {
    this.authors = authors;
  }

  public String getLanguage() {
    return this.language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getShelvingLocation() {
    return shelvingLocation;
  }

  public void setShelvingLocation(String shelvingLocation) {
    this.shelvingLocation = shelvingLocation;
  }

  public String getPublicationDate() {
    return publicationDate;
  }

  public void setPublicationDate(String publicationDate) {
    this.publicationDate = publicationDate;
  }

  public String getPublisher() {
    return publisher;
  }

  public void setPublisher(String publisher) {
    this.publisher = publisher;
  }

  public List<String> getSubjects() {
    return subjects;
  }

  public void setSubjects(List<String> subjects) {
    this.subjects = subjects;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getAmountOfTimesCheckedOut() {
    return amountOfTimesCheckedOut;
  }

  public int getCopiesAvailable() {
    return copiesAvailable;
  }

  public List<String> getReturnDates() {
    return returnDates;
  }

  public void setReturnDates(List<String> returnDates) {
    this.returnDates = returnDates != null ? returnDates : new ArrayList<>();
  }

  public int getTotalCopies() {
    return totalCopies;
  }

  /**
   * Sets total copies to the new total copies and corrects the available copies number.
   *
   * @param totalCopies A {@code int} representing the due date of the BOOK being returned.
   */
  public void setTotalCopies(int totalCopies) {
    // need to see if there are any books checked out
    int booksCheckedOut = returnDates.size();
    this.totalCopies = totalCopies;
    this.copiesAvailable = totalCopies - booksCheckedOut;
  }

  @Override
  public int compareTo(Book other) {
    return Integer.compare(this.id, other.id);
  }

  /**
   * Returns a previously checked-out copy of the BOOK corresponding to the given due date.
   *
   * @param obj A {@code BOOK} representing the BOOK we are seeing if the current BOOK is equal to.
   * @return {@code true} if the return was successful and the BOOK ids match.
   *         {@code false} if the BOOK ids do not match.
   */
  @Override
  public boolean equals(Object obj) {
    // Checks if obj is not null and instance of Book
    // and then does the comparison
    return obj instanceof Book other && this.compareTo(other) == 0;
  }

  @Override
  public String toString() {
    return String.format("(%d)\t%s", this.id, this.title);
  }
}

