RouteControllerTesting with Mockmvc:
 - https://edstem.org/us/courses/78135/discussion/6942679
 - https://stackoverflow.com/questions/30482934/how-to-check-json-in-response-body-with-mockmvc
   - Having trouble parsing out json from controller repsonse used this as a guide
 - https://spring.io/guides/gs/testing-web
 - https://www.baeldung.com/spring-mockmvc-vs-webmvctest

General JUNIT DOCS:
 - https://docs.junit.org/current/user-guide/#overview-getting-started-example-projects
   - BeforeEach was helpful, I was using BeforeAll and it was the same book for all test causing errors

Installing PMD:
 - https://docs.pmd-code.org/pmd-doc-7.16.0/pmd_userdocs_installation.html

UnitTestFileStructure:
 - https://edstem.org/us/courses/78135/discussion/6931816

Java HashMap Docs:
 - https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html

Mockito Docs: Mocking api service book object, original test case were failing when i was setting the mock bean to empty so had to find a way around this and the solution was spy bean
 - https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/test/context/bean/override/mockito/MockitoBean.html
 - https://www.baeldung.com/mockito-behavior
 - https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html

Request Param:
 - https://www.baeldung.com/spring-request-param

Workflow Actions:
 - https://github.com/marketplace/actions/checkout
   - v1 is old
 - https://faun.pub/continuous-integration-of-java-project-with-github-actions-7a8a0e8246ef
   - copied this file and updated the versions
 - https://github.com/marketplace/actions/setup-java-jdk
   - for distributions error i encountered

ReadMe Doc:
 - https://github.com/griffinnewbold/COMS-4156-Service
 - Copied the format of this doc pretty heavily 