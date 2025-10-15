package dev.coms4156.project.individualproject;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.coms4156.project.individualproject.model.Book;
import dev.coms4156.project.individualproject.service.MockApiService;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;


/**
 * This class contains the unit tests for the RouteController class.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class RouteControllerUnitTests {

  @Autowired
  private MockMvc mockMvc;
  public static Book book;

  @MockitoSpyBean
  private MockApiService mockApiService;

  @Test
  void getInvalidBookRecommendations() throws Exception {
    Mockito.doReturn(List.of()).when(mockApiService).getBooks();

    this.mockMvc.perform(get("/books/recommendation"))
            .andExpect(status().isInternalServerError());
  }

  @Test
  void getAvailBooksError() throws Exception {
    Mockito.when(mockApiService.getBooks()).thenThrow(new RuntimeException("Error"));
    this.mockMvc.perform(get("/books/available"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(
                    containsString("Error occurred when getting all available books")));
  }

  @Test
  void addCopyError() throws Exception {
    Mockito.when(mockApiService.getBooks()).thenThrow(new RuntimeException("Error"));
    this.mockMvc.perform(patch("/book/23/add"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(containsString("Error occurred when adding copy of book")));
  }

  @Test
  void indexValidIndexHomepageMessage() throws Exception {
    this.mockMvc.perform(get("/")).andDo(print()).andExpect(status().isOk())
              .andExpect(content().string(containsString("Welcome to the home page! In order"
                      + " to make an API call direct your browser or Postman to an endpoint.")));
  }

  @Test
  void getBookValidBookFound() throws Exception {
    this.mockMvc.perform(get("/book/2")).andDo(print()).andExpect(status().isOk())
            .andExpect(jsonPath("$.title", Matchers.is("All the mighty world :")))
            .andExpect(jsonPath("$.id", Matchers.is(2)));
  }

  @Test
  void getBookInvalidBookNotFound() throws Exception {
    this.mockMvc.perform(get("/book/1000000000"))
            .andDo(print()).andExpect(status().isNotFound());
  }

  @Test
  void addCopyValidBookFound() throws Exception {
    this.mockMvc.perform(patch("/book/2/add"))
            .andExpect(jsonPath("$.copiesAvailable", Matchers.is(2)))
            .andExpect(jsonPath("$.totalCopies", Matchers.is(3)));
  }

  @Test
  void addCopyInvalidBookNotFound() throws Exception {
    this.mockMvc.perform(patch("/book/1000000000/add"))
            .andDo(print()).andExpect(status().isNotFound());
  }

  @Test
  void getValidBookRecommendations() throws Exception {
    this.mockMvc.perform(get("/books/recommendation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", Matchers.hasSize(10)));
  }

  @Test
  void getValidCheckoutBook() throws Exception {
    this.mockMvc.perform(get("/checkout?id=1")).andDo(print()).andExpect(status().isOk())
            .andExpect(jsonPath("$.amountOfTimesCheckedOut", Matchers.is(2)))
            .andExpect(jsonPath("$.copiesAvailable", Matchers.is(0)))
            .andExpect(jsonPath("$.returnDates", Matchers.hasSize(1)));
  }

  @Test
  void getInvalidCheckoutBookNoCopies() throws Exception {
    this.mockMvc.perform(get("/checkout?id=5")).andDo(print()).andExpect(status().isOk());

    this.mockMvc.perform(get("/checkout?id=5")).andDo(print()).andExpect(status().isConflict())
            .andExpect(content().string(containsString("No Available Copies!")));

  }

  @Test
  void getInvalidCheckoutBookNotFound() throws Exception {
    this.mockMvc.perform(get("/checkout?id=50000")).andDo(print()).andExpect(status().isNotFound());
  }

}
