# Fixing Bugs

## Compilation Errors:

### Book.java
 - Error: incompatible types: Type error in equals function
 - Fix: Used compareTo function to compare the books used conditional return to check if object is type Book


 - Error: missing return statement in getLanguage
 - Fix: Added return this.language to return the language

### RouteController.java
 - Error: Missing Return statement in addCopy catch portion of the try-catch
 - Fix: Added error return statement, and printed the error to the terminal

## Errors Found Testing and Debugging:

### Book.java
 - Error: In setShelvingLocation it was setting to a string not the variable
 - Fix: Removed quotes and set to actual variable


 - Error: In deleteCopy return statements were swapped
 - Fix: Fixed by returning true when a copy was successfully deleted else false


 - Error: In checkoutCopy, amountOfTimesCheckedOut was decreased instead of increased.
 - Fix: Switched to ++ and increased variable


 - Error: In returnCopy, if statement was incorrect and should be negated
 - Fix: Negated returnDates.isEmpty() because we need to check the array that isn't empty


 - Error: In hasCopies, was >= 0, wrong because if there's 0 copies none are avail
 - Fix: Switched to > 0 because only true if 1 or above


 - Error: Unused private variable bookmarks
 - Fix: Removed because we don't have a usecase for this at all


 - Error: Avoid using implementation types like 'ArrayList'; use the interface instead
 - Fix: Had multiple of these errors just used List then


 - Error: In Equals mehtod - SuspiciousEqualsMethodName & SimplifyBooleanReturns
 - Fix: Error in my original, but used override for equals, and then simplified down to a one line conditional return

### BookUnitTest.java
 - Error: In nullEqualsBook,  EqualsNull: Avoid using equals() to compare against null
 - Fix: I created a Book var = null; then did the comparison with that, this resolved the error


 - Error: Avoid using implementation types like 'ArrayList'; use the interface instead
 - Fix: Had multiple of these errors just used List then


### RouteController.java
 - Error: missing space between browser and or, caused a error in testing
 - Fix: Added this additional space for test cases


 - Error: In addCopy Route, there was I_AM_TEAPOT return
 - Fix: Replaced with NOT_FOUND


 - Error: getAvailable Route was Patch
 - Fix: Switched to GET route, this wasnt updating anything


 - Error: addCopy Route, on error returned ok
 - Fix: Made it return internal server error


 - Error: UnusedLocalVariable: Avoid unused local variables such as 'currBookId'.
 - Fix: Removed unused Variable also renamed bookId to just id

### MockApiService.java
 - Error: In updateBook, setting this.books incorrectly
 - Fix: this.books = tmpBooks


### WHEN YOU RUN PMD CHECK THIS IS THE ONLY ERROR THAT APPEARS
I saw on ed and in the file not to modify it so i left it be:
 - src/main/java/dev/coms4156/project/individualproject/IndividualProjectApplication.java:11:  UseUtilityClass:   This utility class has a non-private constructor



