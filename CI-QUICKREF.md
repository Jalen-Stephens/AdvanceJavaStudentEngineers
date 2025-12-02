# CI/CD Quick Reference Card

## 🚀 Quick Commands

### Run All CI Checks Locally
```bash
# Linux/macOS
./scripts/run-ci-locally.sh

# Windows PowerShell
.\scripts\run-ci-locally.ps1
```

### Individual Checks
```bash
# Compile only
./mvnw compile

# Run tests
./mvnw test

# Code coverage
./mvnw jacoco:report

# Style check
./mvnw checkstyle:check

# Static analysis
./mvnw pmd:pmd

# Generate all reports
./mvnw site

# Package app
./mvnw package
```

## 📊 View Reports

### Local Reports
- **Coverage**: `target/site/jacoco/index.html`
- **Checkstyle**: `target/site/checkstyle.html`
- **PMD**: `target/site/pmd.html`
- **Tests**: `target/site/surefire-report.html`
- **Site**: `target/site/index.html`

### CI Reports
1. Go to [Actions](https://github.com/Jalen-Stephens/AdvanceJavaStudentEngineers/actions)
2. Click latest workflow run
3. Download artifacts at bottom

## ✅ Pre-Push Checklist

- [ ] Code compiles: `./mvnw compile`
- [ ] Tests pass: `./mvnw test`
- [ ] No style violations: `./mvnw checkstyle:check`
- [ ] No critical PMD issues: `./mvnw pmd:check`
- [ ] Code coverage acceptable: View `target/site/jacoco/index.html`
- [ ] Manual tests done (if applicable)
- [ ] README updated (if needed)

## 🧪 Manual Testing

### E2E Tests
```bash
# Set environment variables first
export LIVE_E2E=true
mvn -Dtest=dev.coms4156.project.metadetect.e2e.ClientServiceLiveE2eTest test
```

### API Testing
```bash
# 1. Start backend
mvn spring-boot:run

# 2. Run API tests with cURL or Postman
# See README for full examples
```

### Client Testing
```bash
# Terminal 1: Backend
mvn spring-boot:run

# Terminal 2: Client
python3 -m http.server 4173 --directory client

# Browser: http://localhost:4173
```

## 🔍 CI Pipeline Status

### What's Automated ✅
- ✅ Compilation
- ✅ Unit tests
- ✅ Code coverage
- ✅ Style checking
- ✅ Static analysis
- ✅ Report generation
- ✅ Artifact upload

### What's Manual ⚠️
- ⚠️ End-to-end tests (requires live services)
- ⚠️ API integration tests (requires manual setup)
- ⚠️ Client UI tests (requires visual validation)

## 🆘 Common Issues

### Tests Fail Locally
```bash
# Clean and retry
./mvnw clean test

# Check for compilation errors
./mvnw clean compile
```

### Style Violations
```bash
# See violations
./mvnw checkstyle:checkstyle
open target/site/checkstyle.html

# Auto-format (if available)
./mvnw spotless:apply
```

### PMD Warnings
```bash
# Generate report
./mvnw pmd:pmd
open target/site/pmd.html

# Review and fix manually
```

### Coverage Too Low
```bash
# See uncovered lines
./mvnw test jacoco:report
open target/site/jacoco/index.html

# Write more tests
```

## 📚 Documentation

- **Full CI Docs**: [CI-PIPELINE.md](CI-PIPELINE.md)
- **README**: [README.md](README.md)
- **GitHub Actions**: [.github/workflows/](.github/workflows/)

## 🔗 Useful Links

- [GitHub Actions](https://github.com/Jalen-Stephens/AdvanceJavaStudentEngineers/actions)
- [JaCoCo Docs](https://www.jacoco.org/jacoco/trunk/doc/)
- [Checkstyle Rules](https://checkstyle.org/checks.html)
- [PMD Rules](https://pmd.github.io/latest/pmd_rules_java.html)
- [Maven Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html)

## 💡 Pro Tips

1. **Run CI locally before pushing** to catch issues early
2. **Check coverage trends** to ensure quality doesn't degrade
3. **Fix style violations immediately** to keep codebase clean
4. **Review PMD warnings** for potential bugs
5. **Keep tests fast** for quick feedback loops
6. **Use Git hooks** to automate pre-commit checks
7. **Monitor CI build times** and optimize if needed

## 🎯 Quality Targets

- **Test Coverage**: ≥ 80%
- **Checkstyle Violations**: 0
- **PMD Critical Issues**: 0
- **Build Time**: < 5 minutes
- **Test Success Rate**: ≥ 95%

---

**Last Updated**: November 29, 2025  
**For Issues**: Open GitHub issue with `ci-pipeline` label
