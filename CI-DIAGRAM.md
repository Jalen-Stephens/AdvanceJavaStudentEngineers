# CI/CD Pipeline Visualization

## Pipeline Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CONTINUOUS INTEGRATION                           │
│                        GitHub Actions Workflow                           │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│   TRIGGER       │
│                 │
│  • Git Push     │
│  • Pull Request │
│  • Manual Run   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 1: ENVIRONMENT SETUP                                              │
│                                                                          │
│  1. Checkout Repository    ─────────────────────────┐                   │
│  2. Setup JDK 17          ─────────────────────────┤                   │
│  3. Cache Maven Dependencies ─────────────────────┘                    │
│                                                                          │
│  ✓ Ubuntu Latest                                                        │
│  ✓ Temurin JDK 17                                                       │
│  ✓ Maven Wrapper                                                        │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 2: BUILD & COMPILE                                                │
│                                                                          │
│  Command: ./mvnw -B clean compile                                       │
│                                                                          │
│  Input:  src/main/java/**/*.java                                        │
│  Output: target/classes/**/*.class                                      │
│                                                                          │
│  ❌ HARD FAIL on errors (blocks pipeline)                               │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 3: UNIT TESTING                                                   │
│                                                                          │
│  Command: ./mvnw -B test                                                │
│                                                                          │
│  • Runs all JUnit tests in src/test/                                    │
│  • Generates test reports                                               │
│  • Collects coverage data (JaCoCo agent)                                │
│                                                                          │
│  Output:                                                                 │
│    - target/surefire-reports/*.xml                                      │
│    - target/jacoco.exec                                                 │
│                                                                          │
│  ❌ HARD FAIL on test failures                                          │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 4: PARALLEL ANALYSIS                                              │
│                                                                          │
│  ┌───────────────────┐  ┌──────────────────┐  ┌────────────────────┐   │
│  │  CODE COVERAGE    │  │  STYLE CHECKING  │  │  STATIC ANALYSIS   │   │
│  │                   │  │                  │  │                    │   │
│  │  jacoco:report    │  │  checkstyle      │  │  pmd:pmd           │   │
│  │                   │  │                  │  │                    │   │
│  │  • Line coverage  │  │  • Google Style  │  │  • Bug detection   │   │
│  │  • Branch cov.    │  │  • Naming        │  │  • Code smells     │   │
│  │  • Method cov.    │  │  • JavaDoc       │  │  • Duplicates      │   │
│  │                   │  │                  │  │                    │   │
│  │  ✓ Non-blocking   │  │  ✓ Non-blocking  │  │  ✓ Non-blocking    │   │
│  └───────────────────┘  └──────────────────┘  └────────────────────┘   │
│           │                      │                      │               │
│           └──────────────────────┴──────────────────────┘               │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 5: MAVEN SITE GENERATION                                          │
│                                                                          │
│  Command: ./mvnw -B site                                                │
│                                                                          │
│  Aggregates all reports into unified documentation:                     │
│    • Project info                                                        │
│    • Dependencies                                                        │
│    • JaCoCo report                                                       │
│    • Checkstyle report                                                   │
│    • PMD report                                                          │
│    • Test results                                                        │
│                                                                          │
│  Output: target/site/index.html                                         │
│                                                                          │
│  ⚠️  SOFT FAIL (continue on error)                                      │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 6: QUALITY GATES                                                  │
│                                                                          │
│  checkstyle:check  ──┐                                                  │
│  pmd:check          ──┤  Report violations but continue                 │
│                       │                                                  │
│  ⚠️  SOFT FAIL (warnings only)                                          │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 7: REPORT VISUALIZATION                                           │
│                                                                          │
│  1. Install wkhtmltoimage                                               │
│  2. Run scripts/html_to_png.sh                                          │
│  3. Convert key reports to PNG:                                         │
│     • target/site/jacoco/index.html  → reports/jacoco.png               │
│     • target/site/pmd.html           → reports/pmd.png                  │
│                                                                          │
│  ⚠️  SOFT FAIL (optional screenshots)                                   │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 8: ARTIFACT UPLOAD                                                │
│                                                                          │
│  Artifact 1: ci-reports-html-xml                                        │
│    • target/site/**/*                                                   │
│    • target/checkstyle-result.xml                                       │
│    • target/pmd.xml                                                     │
│    • target/surefire-reports/**/*                                       │
│    Retention: 30 days                                                   │
│                                                                          │
│  Artifact 2: ci-reports-screenshots                                     │
│    • reports/*.png                                                      │
│    Retention: 30 days                                                   │
│                                                                          │
│  Artifact 3: test-results                                               │
│    • target/surefire-reports/**/*                                       │
│    Retention: 30 days                                                   │
│                                                                          │
│  Artifact 4: application-jar                                            │
│    • target/*.jar                                                       │
│    Retention: 7 days                                                    │
│                                                                          │
│  ✓ Always runs (even on failure)                                        │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 9: SUMMARY GENERATION                                             │
│                                                                          │
│  GitHub Actions Summary includes:                                       │
│    • Test suite count                                                   │
│    • Available reports list                                             │
│    • Download links                                                     │
│    • Status indicators                                                  │
│                                                                          │
│  Displayed at bottom of workflow run page                               │
└────────┬────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ RESULT                                                                   │
│                                                                          │
│  ✅ SUCCESS   All required checks passed                                │
│  ❌ FAILURE   Compilation or tests failed                               │
│  ⚠️  WARNING   Quality gates have issues                                │
└─────────────────────────────────────────────────────────────────────────┘
```

## Manual Testing Flow (Not Automated)

```
┌─────────────────────────────────────────────────────────────────────────┐
│ MANUAL TESTING (Requires Human Interaction)                             │
└─────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ END-TO-END TESTS                       │
│                                        │
│  ❌ Not in CI because:                 │
│    • Requires live Supabase Auth      │
│    • Needs real PostgreSQL database   │
│    • Uses production S3 storage       │
│    • Sensitive credentials            │
│                                        │
│  📝 How to run:                        │
│    export LIVE_E2E=true                │
│    mvn -Dtest=...LiveE2eTest test      │
│                                        │
│  ✅ Covers:                            │
│    • Real auth flow                   │
│    • Actual DB operations             │
│    • Live storage uploads             │
│    • Complete user journeys           │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ API INTEGRATION TESTS                  │
│                                        │
│  ❌ Not in CI because:                 │
│    • Requires running server          │
│    • Manual token management          │
│    • File upload from filesystem      │
│    • Visual response validation       │
│                                        │
│  📝 How to run:                        │
│    mvn spring-boot:run                 │
│    curl -X POST ... (see README)       │
│    OR use Postman collection          │
│                                        │
│  ✅ Covers:                            │
│    • All REST endpoints               │
│    • Authentication flow              │
│    • File upload/download             │
│    • Error scenarios                  │
└────────────────────────────────────────┘

┌────────────────────────────────────────┐
│ CLIENT UI TESTS (Pulse)                │
│                                        │
│  ❌ Not in CI because:                 │
│    • Browser-based UI                 │
│    • Visual validation needed         │
│    • Interactive forms                │
│    • Real-time status updates         │
│                                        │
│  📝 How to run:                        │
│    mvn spring-boot:run                 │
│    python3 -m http.server 4173 ...    │
│    Open http://localhost:4173          │
│                                        │
│  ✅ Covers:                            │
│    • Signup/login UI                  │
│    • Image upload interface           │
│    • Feed display                     │
│    • Post management                  │
└────────────────────────────────────────┘
```

## Report Access Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│ ACCESSING CI REPORTS                                                    │
└─────────────────────────────────────────────────────────────────────────┘

User Request
    │
    ├─── View on GitHub ────────────────────────────────────┐
    │                                                        │
    │    1. Navigate to Actions tab                         │
    │    2. Select workflow run                             │
    │    3. View summary at bottom                          │
    │    4. Click job for detailed logs                     │
    │                                                        │
    ├─── Download Artifacts ────────────────────────────────┤
    │                                                        │
    │    1. Scroll to bottom of workflow run                │
    │    2. Download desired artifact (ZIP)                 │
    │    3. Extract contents                                │
    │    4. Open index.html in browser                      │
    │                                                        │
    └─── View in Repository ───────────────────────────────┘
                                                             │
         1. Navigate to reports/ directory                  │
         2. View PNG screenshots                            │
         3. Check CI-PIPELINE.md for details               │
```

## Local Development Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│ LOCAL CI VALIDATION                                                     │
└─────────────────────────────────────────────────────────────────────────┘

Developer
    │
    ├─── Quick Check ───────────────────────────────────────┐
    │                                                        │
    │    ./mvnw test                                        │
    │    ./mvnw checkstyle:check                            │
    │                                                        │
    ├─── Full CI Locally ───────────────────────────────────┤
    │                                                        │
    │    ./scripts/run-ci-locally.sh  (Linux/macOS)         │
    │    .\scripts\run-ci-locally.ps1 (Windows)             │
    │                                                        │
    │    Runs:                                              │
    │      ✓ Compile                                        │
    │      ✓ Test                                           │
    │      ✓ Coverage                                       │
    │      ✓ Checkstyle                                     │
    │      ✓ PMD                                            │
    │      ✓ Site                                           │
    │      ✓ Quality gates                                  │
    │      ✓ Package                                        │
    │                                                        │
    └─── Push to GitHub ───────────────────────────────────┘
                   │
                   ▼
          CI Pipeline Runs Automatically
                   │
                   ▼
          ✅ Passes → Merge
          ❌ Fails  → Fix and retry
```

## Legend

```
✅  Success / Enabled / Automated
❌  Failure / Disabled / Not Automated / Hard Fail
⚠️   Warning / Soft Fail / Manual Required
✓  Completed / Included
│  Sequential flow
─  Parallel flow
┌┐└┘  Box borders
```
