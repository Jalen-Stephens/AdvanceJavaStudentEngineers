#!/bin/bash

################################################################################
# MetaDetect Local CI Runner
# 
# This script replicates the GitHub Actions CI pipeline locally.
# Use it to validate changes before pushing to remote.
################################################################################

set -euo pipefail

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Track results
ERRORS=0
WARNINGS=0

################################################################################
# Step 1: Clean workspace
################################################################################
print_header "Step 1: Clean Workspace"
if ./mvnw clean; then
    print_success "Workspace cleaned"
else
    print_error "Failed to clean workspace"
    ((ERRORS++))
fi

################################################################################
# Step 2: Compile source code
################################################################################
print_header "Step 2: Compile Source Code"
if ./mvnw -B compile; then
    print_success "Compilation successful"
else
    print_error "Compilation failed"
    ((ERRORS++))
    exit 1
fi

################################################################################
# Step 3: Run unit tests
################################################################################
print_header "Step 3: Run Unit Tests"
if ./mvnw -B test; then
    print_success "All tests passed"
    
    # Count test results
    if [ -d "target/surefire-reports" ]; then
        TEST_COUNT=$(find target/surefire-reports -name "TEST-*.xml" | wc -l)
        print_info "Executed $TEST_COUNT test suites"
    fi
else
    print_error "Tests failed"
    ((ERRORS++))
fi

################################################################################
# Step 4: Generate coverage report
################################################################################
print_header "Step 4: Generate Code Coverage Report"
if ./mvnw -B jacoco:report; then
    print_success "Coverage report generated"
    
    # Check if coverage report exists
    if [ -f "target/site/jacoco/index.html" ]; then
        print_info "Coverage report: target/site/jacoco/index.html"
    else
        print_warning "Coverage report not found"
        ((WARNINGS++))
    fi
else
    print_error "Failed to generate coverage report"
    ((ERRORS++))
fi

################################################################################
# Step 5: Run Checkstyle analysis
################################################################################
print_header "Step 5: Run Checkstyle Analysis"
if ./mvnw -B checkstyle:checkstyle; then
    print_success "Checkstyle analysis completed"
    
    if [ -f "target/checkstyle-result.xml" ]; then
        VIOLATIONS=$(grep -c "severity=" target/checkstyle-result.xml || echo "0")
        if [ "$VIOLATIONS" -gt "0" ]; then
            print_warning "Found $VIOLATIONS style violations"
            ((WARNINGS++))
        else
            print_success "No style violations found"
        fi
    fi
else
    print_error "Checkstyle analysis failed"
    ((ERRORS++))
fi

################################################################################
# Step 6: Run PMD static analysis
################################################################################
print_header "Step 6: Run PMD Static Analysis"
if ./mvnw -B pmd:pmd -Dpmd.failOnViolation=false; then
    print_success "PMD analysis completed"
    
    if [ -f "target/pmd.xml" ]; then
        PMD_VIOLATIONS=$(grep -c "<violation" target/pmd.xml || echo "0")
        if [ "$PMD_VIOLATIONS" -gt "0" ]; then
            print_warning "Found $PMD_VIOLATIONS PMD violations"
            ((WARNINGS++))
        else
            print_success "No PMD violations found"
        fi
    fi
else
    print_error "PMD analysis failed"
    ((ERRORS++))
fi

################################################################################
# Step 7: Generate Maven site
################################################################################
print_header "Step 7: Generate Maven Site"
if ./mvnw -B site -Dcheckstyle.failsOnError=false -Dpmd.failOnViolation=false; then
    print_success "Maven site generated"
    
    if [ -f "target/site/index.html" ]; then
        print_info "Site homepage: target/site/index.html"
    fi
else
    print_warning "Maven site generation failed (non-critical)"
    ((WARNINGS++))
fi

################################################################################
# Step 8: Run quality gates
################################################################################
print_header "Step 8: Run Quality Gates"

# Checkstyle quality gate
print_info "Running Checkstyle quality gate..."
if ./mvnw -B checkstyle:check; then
    print_success "Checkstyle quality gate passed"
else
    print_warning "Checkstyle quality gate failed"
    ((WARNINGS++))
fi

# PMD quality gate
print_info "Running PMD quality gate..."
if ./mvnw -B pmd:check; then
    print_success "PMD quality gate passed"
else
    print_warning "PMD quality gate failed"
    ((WARNINGS++))
fi

################################################################################
# Step 9: Package application
################################################################################
print_header "Step 9: Package Application"
if ./mvnw -B package -DskipTests; then
    print_success "Application packaged"
    
    if [ -f "target/"*.jar ]; then
        JAR_FILE=$(ls -1 target/*.jar | head -n 1)
        JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
        print_info "JAR file: $JAR_FILE ($JAR_SIZE)"
    fi
else
    print_error "Packaging failed"
    ((ERRORS++))
fi

################################################################################
# Step 10: Generate report screenshots
################################################################################
print_header "Step 10: Generate Report Screenshots"

# Check if wkhtmltoimage is available
if command -v wkhtmltoimage &> /dev/null; then
    if [ -f "scripts/html_to_png.sh" ]; then
        chmod +x scripts/html_to_png.sh
        if bash scripts/html_to_png.sh; then
            print_success "Report screenshots generated"
            ls -lh reports/*.png 2>/dev/null || true
        else
            print_warning "Failed to generate screenshots"
            ((WARNINGS++))
        fi
    else
        print_warning "Screenshot script not found"
        ((WARNINGS++))
    fi
else
    print_warning "wkhtmltoimage not installed - skipping screenshots"
    print_info "Install with: sudo apt-get install wkhtmltoimage (Linux)"
    print_info "           or: brew install wkhtmltopdf (macOS)"
    ((WARNINGS++))
fi

################################################################################
# Summary
################################################################################
print_header "CI Pipeline Summary"

echo ""
echo "Results:"
echo "--------"

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    print_success "All checks passed! ✨"
    echo ""
    print_info "Your code is ready to push 🚀"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    print_warning "Checks completed with $WARNINGS warning(s)"
    echo ""
    print_info "Review warnings before pushing"
    exit 0
else
    print_error "CI pipeline failed with $ERRORS error(s) and $WARNINGS warning(s)"
    echo ""
    print_error "Please fix errors before pushing"
    exit 1
fi
