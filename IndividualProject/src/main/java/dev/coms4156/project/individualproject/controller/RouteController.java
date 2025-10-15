package dev.coms4156.project.individualproject.controller;

import dev.coms4156.project.individualproject.model.Book;
import dev.coms4156.project.individualproject.service.MockApiService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



/**
 * Public Class RouteController, for HTTP Endpoints.
 */
@RestController
public class RouteController {

  private final MockApiService mockApiService;

  public RouteController(MockApiService mockApiService) {
    this.mockApiService = mockApiService;
  }

  @GetMapping({"/", "/index"})
  public String index() {
    return "Welcome to the home page! In order to make an API call direct your browser "
        + "or Postman to an endpoint.";
  }

  /**
   * Returns the details of the specified book.
   *
   * @param id An {@code int} representing the unique identifier of the book to retrieve.
   *
   * @return A {@code ResponseEntity} containing either the matching {@code Book} object with an
   *         HTTP 200 response, or a message indicating that the book was not
   *         found with an HTTP 404 response.
   */
  @GetMapping({"/book/{id}"})
  public ResponseEntity<?> getBook(@PathVariable int id) {
    for (Book book : mockApiService.getBooks()) {
      if (book.getId() == id) {
        return new ResponseEntity<>(book, HttpStatus.OK);
      }
    }

    return new ResponseEntity<>("Book not found.", HttpStatus.NOT_FOUND);
  }

  /**
   * Checksout a book and returns its updated details.
   *
   * @param id An {@code int} representing the unique identifier of the book to checkout.
   *
   * @return A {@code ResponseEntity} containing either the matching {@code Book} object with an
   *         HTTP 200 response, or a message indicating that the book was not
   *         found with an HTTP 404 response.
   */
  @GetMapping({"/checkout"})
  public ResponseEntity<?> getCheckoutBook(@RequestParam int id) {
    for (Book book : mockApiService.getBooks()) {
      if (book.getId() == id) {
        if (book.checkoutCopy() != null) {
          mockApiService.updateBook(book);
          return new ResponseEntity<>(book, HttpStatus.OK);
        } else {
          return new ResponseEntity<>("No Available Copies!", HttpStatus.CONFLICT);
        }
      }
    }
    return new ResponseEntity<>("Book not found.", HttpStatus.NOT_FOUND);
  }

  /**
   * Get and return a list of all the books with available copies.
   *
   * @return A {@code ResponseEntity} containing a list of available {@code Book} objects with an
   *         HTTP 200 response if sucessful, or a message indicating an error occurred with an
   *         HTTP 500 response.
   */
  @GetMapping({"/books/available"})
  public ResponseEntity<?> getAvailableBooks() {
    try {
      List<Book> availableBooks = new ArrayList<>();

      for (Book book : mockApiService.getBooks()) {
        if (book.hasCopies()) {
          availableBooks.add(book);
        }
      }

      return new ResponseEntity<>(availableBooks, HttpStatus.OK);
    } catch (Exception e) {
      System.err.println(e);
      return new ResponseEntity<>("Error occurred when getting all available books",
              HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Get and return a list of all the books with available copies.
   *
   * @return A {@code ResponseEntity} containing a list of available {@code Book} objects with an
   *         HTTP 200 response if sucessful, or a message indicating an error occurred with an
   *         HTTP 500 response.
   */
  @GetMapping({"/books/recommendation"})
  public ResponseEntity<?> getRecommendedBooks() {
    try {
      // I want to map each book to the amount of times checked out
      // This way at freq 0 I can add all the lower recs than starting at the max key
      // I can start adding the best books
      Map<Integer, List<Book>> recommendedBooksMap = new HashMap<>();
      for (Book book : mockApiService.getBooks()) {
        int timesChecked = book.getAmountOfTimesCheckedOut();
        recommendedBooksMap.putIfAbsent(timesChecked, new ArrayList<>());
        recommendedBooksMap.get(timesChecked).add(book);
      }
      // Add all the books with the lowest times checked out starting form 0
      List<Book> recommendedBooks = new ArrayList<>();
      int freq = 0;
      while (recommendedBooks.size() < 5) {
        for (Book book : recommendedBooksMap.get(freq)) {
          if (recommendedBooks.size() < 5) {
            recommendedBooks.add(book);
          } else {
            break;
          }
        }
        freq += 1;
      }
      // Get the max key to start adding books with the highest freq
      int maxKey = Collections.max(recommendedBooksMap.keySet());
      while (recommendedBooks.size() < 10) {
        for (Book book : recommendedBooksMap.get(maxKey)) {
          if (recommendedBooks.size() < 10) {
            recommendedBooks.add(book);
          } else {
            break;
          }
        }
        maxKey -= 1;
      }
      return new ResponseEntity<>(recommendedBooks, HttpStatus.OK);
    } catch (Exception e) {
      System.err.println(e);
      return new ResponseEntity<>("Error occurred when getting all available books",
              HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Adds a copy to the {@code} Book object if it exists.
   *
   * @param id An {@code Integer} representing the unique id of the book.
   * @return A {@code ResponseEntity} containing the updated {@code Book} object with an
   *         HTTP 200 response if successful or HTTP 404 if the book is not found,
   *         or a message indicating an error occurred with an HTTP 500 code.
   */
  @PatchMapping({"/book/{id}/add"})
  public ResponseEntity<?> addCopy(@PathVariable Integer id) {
    try {
      for (Book book : mockApiService.getBooks()) {
        if (id.equals(book.getId())) {
          book.addCopy();
          return new ResponseEntity<>(book, HttpStatus.OK);
        }
      }
      return new ResponseEntity<>("Book not found.", HttpStatus.NOT_FOUND);
    } catch (Exception e) {
      System.err.println(e);
      return new ResponseEntity<>("Error occurred when adding copy of book",
              HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

}
