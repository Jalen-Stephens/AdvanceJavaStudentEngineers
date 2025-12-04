# Continuous Integration Pipeline Documentation

## Overview

This document describes the CI/CD pipeline implementation for MetaDetect, including automated checks, manual testing procedures, and how to access CI reports.

## Table of Contents

- [Automated CI Pipeline](#automated-ci-pipeline)
- [Manual Testing Procedures](#manual-testing-procedures)
- [Accessing CI Reports](#accessing-ci-reports)
- [Running CI Checks Locally](#running-ci-checks-locally)
- [CI Pipeline Architecture](#ci-pipeline-architecture)

---

## Automated CI Pipeline

### Trigger Events

The CI pipeline runs automatically on:
- Every push to any branch
- Every pull request to `main` branch
- Manual workflow dispatch (optional)

### Pipeline Stages

#### 1. **Build Stage**
- **What it does:** Compiles the Java source code
- **Command:** `./mvnw -B clean compile`
- **Fails on:** Compilation errors
- **Output:** Compiled `.class` files in `target/classes/`

#### 2. **Test Stage**
- **What it does:** Runs all JUnit unit tests
- **Command:** `./mvnw -B test`
- **Fails on:** Test failures or errors
- **Coverage:** Automatically tracked by JaCoCo
- **Output:**
  - Test results in `target/surefire-reports/`
  - JaCoCo coverage data in `target/jacoco.exec`

#### 3. **Code Coverage Analysis**
- **Tool:** JaCoCo (Java Code Coverage)
- **Command:** `./mvnw -B jacoco:report`
- **Metrics tracked:**
  - Line coverage
  - Branch coverage
  - Method coverage
  - Class coverage
- **Output:** HTML reports in `target/site/jacoco/index.html`

#### 4. **Style Checking**
- **Tool:** Checkstyle
- **Standard:** Google Java Style Guide
- **Command:** `./mvnw -B checkstyle:checkstyle`
- **Checks:**
  - Code formatting
  - Naming conventions
  - Javadoc requirements
  - Import organization
- **Output:**
  - XML: `target/checkstyle-result.xml`
  - HTML: `target/site/checkstyle.html`

#### 5. **Static Code Analysis**
- **Tool:** PMD (Programming Mistake Detector)
- **Command:** `./mvnw -B pmd:pmd`
- **Detects:**
  - Potential bugs
  - Dead code
  - Suboptimal code
  - Overcomplicated expressions
  - Duplicate code
- **Output:**
  - XML: `target/pmd.xml`
  - HTML: `target/site/pmd.html`

#### 6. **Maven Site Generation**
- **Command:** `./mvnw -B site`
- **Includes:**
  - Project information
  - Dependencies report
  - All plugin reports (JaCoCo, Checkstyle, PMD)
  - Aggregated documentation
- **Output:** Complete site in `target/site/index.html`

#### 7. **Quality Gates**
- **Checkstyle Gate:** `./mvnw -B checkstyle:check`
  - Fails build if style violations exceed threshold
  - Set to `continue-on-error: true` for reports
- **PMD Gate:** `./mvnw -B pmd:check`
  - Warns on code quality issues
  - Non-blocking for report generation

#### 8. **Artifact Generation**
- **HTML/XML Reports** - Raw report files
- **PNG Screenshots** - Visual report summaries
- **Test Results** - JUnit XML format
- **Build JAR** - Packaged application

### Workflow Files

```
.github/workflows/
├── ci-reports.yml    # Comprehensive CI with reports
└── maven-main.yml    # Fast build and test pipeline
```

---

## Manual Testing Procedures

### Why Some Tests Are Manual

1. **End-to-End Tests** require live external services with sensitive credentials
2. **API Integration Tests** need interactive token management and file uploads
3. **Client UI Tests** require visual validation and browser interaction

### End-to-End Testing

#### Prerequisites
```bash
# Required environment variables
export SPRING_DATASOURCE_URL="jdbc:postgresql://..."
export SPRING_DATASOURCE_USERNAME="..."
export SPRING_DATASOURCE_PASSWORD="..."
export SUPABASE_URL="https://..."
export SUPABASE_ANON_KEY="..."
export SUPABASE_JWT_SECRET="..."
export SUPABASE_STORAGE_BUCKET="..."
export LIVE_E2E=true
```

#### Running E2E Tests
```bash
# Option 1: Run specific E2E test
mvn -Dtest=dev.coms4156.project.metadetect.e2e.ClientServiceLiveE2eTest test

# Option 2: Run all tests including E2E
mvn test
```

#### What E2E Tests Cover
- ✅ User signup via Supabase Auth
- ✅ User login and token generation
- ✅ Image upload to S3 storage
- ✅ Database persistence verification
- ✅ Image retrieval with signed URLs
- ✅ Image deletion and cleanup

### API Integration Testing

#### Using cURL

```bash
# 1. Sign up a new user
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser@example.com", "password": "SecurePass123"}'

# 2. Log in and save token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser@example.com", "password": "SecurePass123"}' \
  | jq -r '.access_token')

# 3. Upload an image
IMAGE_ID=$(curl -s -X POST http://localhost:8080/api/images/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@src/test/resources/mock-images/Spaghetti.png" \
  | jq -r '.id')

# 4. Analyze the image
ANALYSIS_ID=$(curl -s -X POST "http://localhost:8080/api/analyze/$IMAGE_ID" \
  -H "Authorization: Bearer $TOKEN" \
  | jq -r '.analysisId')

# 5. Get analysis status
curl -X GET "http://localhost:8080/api/analyze/$ANALYSIS_ID" \
  -H "Authorization: Bearer $TOKEN"

# 6. Get manifest (if available)
curl -X GET "http://localhost:8080/api/analyze/$ANALYSIS_ID/manifest" \
  -H "Authorization: Bearer $TOKEN"

# 7. Delete the image
curl -X DELETE "http://localhost:8080/api/images/$IMAGE_ID" \
  -H "Authorization: Bearer $TOKEN"
```

#### Using Postman

1. **Import Collection:**
   - Create a new Postman collection named "MetaDetect API"
   - Add the base URL: `http://localhost:8080`

2. **Set up environment variables:**
   - `baseUrl`: `http://localhost:8080`
   - `accessToken`: (will be set after login)

3. **Test sequence:**
   - POST `/auth/signup` → Save email/password
   - POST `/auth/login` → Copy access_token to environment
   - POST `/api/images/upload` → Upload test image
   - POST `/api/analyze/{imageId}` → Start analysis
   - GET `/api/analyze/{analysisId}` → Check status
   - GET `/api/images` → List all images
   - DELETE `/api/images/{id}` → Clean up

4. **Verify in Supabase Dashboard:**
   - Check user table for new accounts
   - Verify images bucket for uploaded files
   - Confirm database records for metadata

### Client UI Testing (Pulse)

#### Setup
```bash
# Terminal 1: Start backend
mvn spring-boot:run

# Terminal 2: Start client server
python3 -m http.server 4173 --directory client
```

#### Manual Test Checklist

1. **Landing Page** (http://localhost:4173)
   - [ ] Page loads correctly
   - [ ] API URL badge shows correct backend
   - [ ] Both forms render properly

2. **Sign Up Flow**
   - [ ] Enter email and password
   - [ ] Submit form
   - [ ] Response panel shows Supabase JSON
   - [ ] User object contains email
   - [ ] Copy button works

3. **Login Flow**
   - [ ] Enter credentials
   - [ ] Submit form
   - [ ] Response includes access_token
   - [ ] Auto-redirect to Pulse Studio

4. **Pulse Studio** (http://localhost:4173/compose.html)
   - [ ] Token automatically loaded from previous login
   - [ ] Upload form accepts images
   - [ ] Caption and labels can be added
   - [ ] Post appears in feed after upload
   - [ ] Signed URL loads image preview
   - [ ] Delete button removes post
   - [ ] AI badge updates when analysis completes

5. **Error Handling**
   - [ ] Invalid credentials show error
   - [ ] Expired token redirects to login
   - [ ] Upload failures show message
   - [ ] Network errors display properly

---

## Accessing CI Reports

### GitHub Actions Interface

1. **Navigate to Actions tab:**
   ```
   https://github.com/Jalen-Stephens/AdvanceJavaStudentEngineers/actions
   ```

2. **Select a workflow run:**
   - Click on the most recent run at the top
   - Green checkmark = success
   - Red X = failure
   - Yellow circle = in progress

3. **View CI Summary:**
   - Scroll to bottom of workflow run page
   - See test counts and available reports
   - Links to download artifacts

4. **Download Artifacts:**
   - `ci-reports-html-xml` (200-500 KB)
     - Contains all HTML and XML reports
     - Extract and open `target/site/index.html`
   - `ci-reports-screenshots` (50-100 KB)
     - PNG images of key reports
     - Quick visual reference
   - `test-results` (10-50 KB)
     - JUnit XML files
     - Can be imported into test management tools
   - `application-jar` (30-50 MB)
     - Packaged application
     - Ready to deploy

### Report Contents

#### JaCoCo Coverage Report
- **File:** `target/site/jacoco/index.html`
- **Shows:**
  - Overall coverage percentages
  - Per-package breakdown
  - Per-class details
  - Uncovered line highlights

#### Checkstyle Report
- **File:** `target/site/checkstyle.html`
- **Shows:**
  - Violations by severity (error, warning, info)
  - Violations by file
  - Specific rule violations with line numbers

#### PMD Report
- **File:** `target/site/pmd.html`
- **Shows:**
  - Code quality issues by priority
  - Detailed violation descriptions
  - Suggestions for improvement
  - Affected files with line numbers

#### Test Results
- **File:** `target/site/surefire-report.html`
- **Shows:**
  - Test execution summary
  - Pass/fail status per test
  - Execution time
  - Failure stack traces

### Repository Reports

Pre-generated report screenshots in `reports/` directory:
- `checkstyle-report.png` - Latest style check results
- `branch-report.png` - Coverage visualization
- `pmd-report.png` - Static analysis findings
- `api-testing.png` - Manual API test evidence

---

## Running CI Checks Locally

### Full CI Suite
```bash
# Run the complete CI pipeline locally
./scripts/run-ci-locally.sh
```

### Individual Checks

#### Style Checking
```bash
# Check style (fails on violations)
mvn checkstyle:check

# Generate style report
mvn checkstyle:checkstyle

# View report
open target/site/checkstyle.html  # macOS
xdg-open target/site/checkstyle.html  # Linux
start target/site/checkstyle.html  # Windows
```

#### Static Analysis
```bash
# Run PMD analysis
mvn pmd:pmd

# Check against rules (may fail build)
mvn pmd:check

# View report
open target/site/pmd.html
```

#### Code Coverage
```bash
# Run tests with coverage
mvn clean test

# Generate coverage report
mvn jacoco:report

# View report
open target/site/jacoco/index.html
```

#### All Reports
```bash
# Generate complete Maven site
mvn clean test site

# Open main site page
open target/site/index.html
```

### Quick Validation
```bash
# Fast: compile and test
mvn clean test

# Medium: compile, test, and package
mvn clean package

# Full: all checks and reports
mvn clean verify site
```

---

## CI Pipeline Architecture

### Workflow Diagram

```
┌─────────────────┐
│   Git Push      │
│  Pull Request   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│         GitHub Actions Trigger          │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  Environment Setup                      │
│  - Checkout code                        │
│  - Setup JDK 17                         │
│  - Cache Maven dependencies             │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  Build & Compile                        │
│  ./mvnw clean compile                   │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  Unit Tests                             │
│  ./mvnw test                            │
└────────┬────────────────────────────────┘
         │
         ├──────────────────────┐
         ▼                      ▼
┌──────────────────┐   ┌──────────────────┐
│  Code Coverage   │   │  Style Checking  │
│  jacoco:report   │   │  checkstyle      │
└────────┬─────────┘   └────────┬─────────┘
         │                      │
         └──────────┬───────────┘
                    ▼
         ┌──────────────────────┐
         │  Static Analysis     │
         │  pmd:pmd             │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │  Maven Site          │
         │  Aggregate Reports   │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │  Quality Gates       │
         │  checkstyle:check    │
         │  pmd:check           │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │  Generate PNGs       │
         │  wkhtmltoimage       │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │  Upload Artifacts    │
         │  - HTML/XML Reports  │
         │  - PNG Screenshots   │
         │  - Test Results      │
         │  - JAR Files         │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │  Generate Summary    │
         │  GitHub UI           │
         └──────────────────────┘
```

### Parallel Execution

Some stages run in parallel for speed:
- Coverage, Style, and Static Analysis (independent)
- Report generation happens after all analysis complete

### Failure Handling

- **Hard failures** (block pipeline):
  - Compilation errors
  - Test failures
  - Build packaging errors

- **Soft failures** (continue, report issues):
  - Style violations (in report mode)
  - PMD warnings
  - PNG generation failures

### Artifact Retention

- **30 days:** CI reports (HTML/XML/PNG)
- **7 days:** Build artifacts (JAR files)
- **Forever:** Latest reports committed to `reports/` directory

---

## Continuous Improvement

### Metrics Tracked

- Build success rate
- Test execution time
- Code coverage trends
- Style violation counts
- PMD issue trends

### Future Enhancements

- [ ] Automated E2E tests with test database
- [ ] Performance testing integration
- [ ] Security scanning (OWASP Dependency Check)
- [ ] Automated deployment to staging
- [ ] Slack/Discord notifications
- [ ] PR comment reports

---

## Troubleshooting

### Common Issues

**Issue:** Tests pass locally but fail in CI
- **Cause:** Environment differences, missing dependencies
- **Solution:** Check CI logs and verify pom.xml

**Issue:** PMD report not generated
- **Cause:** Analysis errors, missing source files
- **Solution:** Run `mvn pmd:pmd` locally, check target/pmd folder

**Issue:** Coverage report empty
- **Cause:** Tests not run, JaCoCo agent not attached
- **Solution:** Ensure `mvn test` runs before `jacoco:report`

**Issue:** Artifacts not available
- **Cause:** Workflow failed before artifact upload
- **Solution:** Check step that failed, fix and re-run

### Getting Help

- **CI/CD Issues:** Open issue with `ci-pipeline` label
- **Test Failures:** Check logs, open issue with `testing` label
- **Report Questions:** Review this document, contact team

---

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [Checkstyle Documentation](https://checkstyle.org/)
- [PMD Documentation](https://pmd.github.io/)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
