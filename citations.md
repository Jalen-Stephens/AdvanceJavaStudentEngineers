### **Commit / Ticket Reference**
- **Commit:** `<your commit message>`
- **Ticket:** `#<ticket number> — <ticket title>`
- **Date:** <month day, year>
- **Team Member:** <name>

---

### **AI Tool Information**
- **Tool Used:** OpenAI ChatGPT (GPT-5)
- **Access Method:** ChatGPT Web (.edu academic access)
- **Configuration:** Default model settings
- **Cost:** $0 (no paid API calls)

---

### **Purpose of AI Assistance**
<Briefly describe what part of the task the AI assisted with — e.g., refactoring, writing docs, fixing errors, setting up build tools, etc.>

---

### **Prompts / Interaction Summary**
<List or paraphrase the key prompts you used.>

---

### **Resulting Artifacts**
<List files, configurations, or code generated/edited with AI help.>

---

### **Verification**
<List how you tested/validated the AI-assisted changes (build, test suite, manual review, etc.).>

---

### **Attribution Statement**
> Portions of this commit or configuration were generated with assistance from OpenAI ChatGPT (GPT-5) on <date>. All AI-generated content was reviewed, verified, and finalized by the development team.

---

### **Commit / Ticket Reference**

* **Commit:** `chore(init): renamed project to MetaDetect, updated package structure, pom.xml coordinates, and Spring Boot configuration (#2)`
* **Ticket:** [#2 — INIT Project Skeleton Code](https://github.com/Jalen-Stephens/AdvanceJavaStudentEngineers/issues/2)
* **Date:** October 15 2025
* **Team Member:** Jalen Stephens

---

### **AI Tool Information**

* **Tool Used:** OpenAI ChatGPT (GPT-5)
* **Access Method:** ChatGPT Web Interface — free academic access via `.edu` email
* **Configuration:** Default model settings (balanced reasoning, no custom temperature or paid APIs)
* **Cost:** $0 (educational access only)

---

### **Purpose of AI Assistance**

The AI assistant was used to help set up the initial iteration of the MetaDetect AI Image Detection Service project by providing technical guidance on:

* Refactoring and renaming the base Spring Boot application from **IndividualProject** → **MetaDetect**
* Updating Java package paths to `dev.coms4156.project.metadetect`
* Revising the `pom.xml` to reflect new coordinates (`groupId`, `artifactId`, `version`, `name`) and to add Checkstyle, PMD, and JaCoCo (≥ 55 % coverage)
* Adjusting `application.properties` to use `spring.application.name=metadetect-service` and `spring.application.version=0.1.0`
* Cleaning Git tracking (`.gitignore` fixes and `.idea` untracking)
* Crafting consistent commit messages and PR templates mapped to the Kanban workflow

---

### **Prompts / Interaction Summary**

Prompts and questions provided to ChatGPT included:

* “What should my Kanban ticket be called for setting up the skeleton code?”
* “How should I rename my Spring Boot app and packages to MetaDetect?”
* “Update my `pom.xml` to include PMD and JaCoCo 55 % coverage.”
* “How do I remove tracked `.idea` files if they’re already in `.gitignore`?”
* “Give me a proper commit message for the init branch (#2).”

---

### **Resulting Artifacts**

* Updated project structure → `dev/coms4156/project/metadetect`
* Updated `MetaDetectApplication.java` and `application.properties`
* Rewritten `pom.xml` with MetaDetect metadata, PMD, Checkstyle, and JaCoCo rules
* Cleaned `.gitignore` and removed `.idea` from Git index
* Verified successful application startup (`mvn spring-boot:run`) on port 8080

---

### **Verification**

* Ran `mvn clean verify` → build success, no Checkstyle or PMD violations
* Confirmed app startup log:

  > Starting MetaDetectApplication v0.1.0 using Java 24.0.2
* Verified all configuration files tracked under branch `2-init-project-skeleton-code`

---

### **Attribution Statement**

> Portions of the project configuration, Maven setup, and documentation for this commit were generated with assistance from **OpenAI ChatGPT (GPT-5)** on October 15 2025.
> The AI was used to standardize naming conventions, refactor project metadata, and ensure compliance with Iteration 1 setup requirements.
> All AI-assisted content was reviewed, tested, and approved by the development team before commit and merge.

---

### **Commit / Ticket Reference**

* **Commit:** `chore(init): finalize MetaDetect skeleton structure with controllers, services, and DTO layer setup (#2)`
* **Ticket:** `#2 — INIT: Project Skeleton Code`
* **Date:** October 15, 2025
* **Team Member:** Jalen Stephens

---

### **AI Tool Information**

* **Tool Used:** OpenAI ChatGPT (GPT-5)
* **Access Method:** ChatGPT Web (.edu academic access)
* **Configuration:** Default model settings
* **Cost:** $0 (no paid API calls)

---

### **Purpose of AI Assistance**

The AI was used to finalize the **MetaDetect project skeleton setup**.
Specifically, assistance covered:

* Refactoring and renaming the original “IndividualProject” codebase to “MetaDetect.”
* Updating the `pom.xml`, `application.properties`, and `MetaDetectApplication.java` to align with the new naming.
* Creating and organizing the initial **Spring Boot project structure** with `controller`, `service`, `model`, and `dto` packages.
* Generating placeholder controller/service classes with TODO markers.
* Creating a unified `DTOs.java` file containing request and response record definitions for API endpoints.
* Advising on Git commit message format, Kanban naming conventions, and AI documentation best practices.

---

### **Prompts / Interaction Summary**

* “Can you give suggestions on renaming the project to match our group project name?”
* “My `application.properties` still says IndividualProject—how should I change it?”
* “Can you make controller, model, and service skeletons for our proposal?”
* “What are DTOs and should I use one file or separate files?”
* “Can you generate a commit message for final skeleton code setup?”
* “Can you generate a citation with this template for everything since last commit?”

---

### **Resulting Artifacts**

* **`pom.xml`** — Updated artifact ID, name, and version for `metadetect-service`
* **`application.properties`** — Renamed to `spring.application.name=MetaDetect`
* **`MetaDetectApplication.java`** — Main entrypoint updated with new package path
* **Controller Files:**

  * `AnalyzeController.java`
  * `ImageController.java`
  * `AuthController.java`
  * `HealthController.java`
* **Service Files:**

  * `AnalyzeService.java`
  * `ImageService.java`
  * `UserService.java`
* **Model/DTO Files:**

  * `DTOs.java` (centralized DTO record definitions)
* **.gitignore** — Confirmed `.idea/` exclusion
* **Kanban Mapping:** confirmed linkage to ticket `#2 INIT: Project Skeleton Code`

---

### **Verification**

* Successfully ran the project using:

  ```bash
  mvn spring-boot:run
  ```

  confirming proper boot under `metadetect-service`.
* Verified Maven build success via:

  ```bash
  mvn clean verify -DskipTests
  ```

  which returned **BUILD SUCCESS**.
* Conducted manual review of project structure in IntelliJ IDEA to ensure correct package resolution, imports, and naming consistency.
* No runtime or dependency errors observed; ready for future feature development.

---