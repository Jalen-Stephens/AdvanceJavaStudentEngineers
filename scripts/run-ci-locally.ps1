################################################################################
# MetaDetect Local CI Runner (PowerShell)
# 
# This script replicates the GitHub Actions CI pipeline locally on Windows.
# Use it to validate changes before pushing to remote.
################################################################################

$ErrorActionPreference = "Continue"

# Color output functions
function Write-Header($message) {
    Write-Host "`n========================================" -ForegroundColor Blue
    Write-Host $message -ForegroundColor Blue
    Write-Host "========================================`n" -ForegroundColor Blue
}

function Write-Success($message) {
    Write-Host "✓ $message" -ForegroundColor Green
}

function Write-Failure($message) {
    Write-Host "✗ $message" -ForegroundColor Red
}

function Write-Warning2($message) {
    Write-Host "⚠ $message" -ForegroundColor Yellow
}

function Write-Info($message) {
    Write-Host "ℹ $message" -ForegroundColor Cyan
}

# Track results
$script:Errors = 0
$script:Warnings = 0

################################################################################
# Step 1: Clean workspace
################################################################################
Write-Header "Step 1: Clean Workspace"
try {
    & .\mvnw.cmd clean
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Workspace cleaned"
    } else {
        Write-Failure "Failed to clean workspace"
        $script:Errors++
    }
} catch {
    Write-Failure "Failed to clean workspace: $_"
    $script:Errors++
}

################################################################################
# Step 2: Compile source code
################################################################################
Write-Header "Step 2: Compile Source Code"
try {
    & .\mvnw.cmd -B compile
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Compilation successful"
    } else {
        Write-Failure "Compilation failed"
        $script:Errors++
        exit 1
    }
} catch {
    Write-Failure "Compilation failed: $_"
    $script:Errors++
    exit 1
}

################################################################################
# Step 3: Run unit tests
################################################################################
Write-Header "Step 3: Run Unit Tests"
try {
    & .\mvnw.cmd -B test
    if ($LASTEXITCODE -eq 0) {
        Write-Success "All tests passed"
        
        # Count test results
        if (Test-Path "target\surefire-reports") {
            $testCount = (Get-ChildItem "target\surefire-reports" -Filter "TEST-*.xml").Count
            Write-Info "Executed $testCount test suites"
        }
    } else {
        Write-Failure "Tests failed"
        $script:Errors++
    }
} catch {
    Write-Failure "Tests failed: $_"
    $script:Errors++
}

################################################################################
# Step 4: Generate coverage report
################################################################################
Write-Header "Step 4: Generate Code Coverage Report"
try {
    & .\mvnw.cmd -B jacoco:report
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Coverage report generated"
        
        # Check if coverage report exists
        if (Test-Path "target\site\jacoco\index.html") {
            Write-Info "Coverage report: target\site\jacoco\index.html"
        } else {
            Write-Warning2 "Coverage report not found"
            $script:Warnings++
        }
    } else {
        Write-Failure "Failed to generate coverage report"
        $script:Errors++
    }
} catch {
    Write-Failure "Failed to generate coverage report: $_"
    $script:Errors++
}

################################################################################
# Step 5: Run Checkstyle analysis
################################################################################
Write-Header "Step 5: Run Checkstyle Analysis"
try {
    & .\mvnw.cmd -B checkstyle:checkstyle
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Checkstyle analysis completed"
        
        if (Test-Path "target\checkstyle-result.xml") {
            $violations = (Select-String -Path "target\checkstyle-result.xml" -Pattern "severity=" -AllMatches).Matches.Count
            if ($violations -gt 0) {
                Write-Warning2 "Found $violations style violations"
                $script:Warnings++
            } else {
                Write-Success "No style violations found"
            }
        }
    } else {
        Write-Failure "Checkstyle analysis failed"
        $script:Errors++
    }
} catch {
    Write-Failure "Checkstyle analysis failed: $_"
    $script:Errors++
}

################################################################################
# Step 6: Run PMD static analysis
################################################################################
Write-Header "Step 6: Run PMD Static Analysis"
try {
    & .\mvnw.cmd -B pmd:pmd -Dpmd.failOnViolation=false
    if ($LASTEXITCODE -eq 0) {
        Write-Success "PMD analysis completed"
        
        if (Test-Path "target\pmd.xml") {
            $pmdViolations = (Select-String -Path "target\pmd.xml" -Pattern "<violation" -AllMatches).Matches.Count
            if ($pmdViolations -gt 0) {
                Write-Warning2 "Found $pmdViolations PMD violations"
                $script:Warnings++
            } else {
                Write-Success "No PMD violations found"
            }
        }
    } else {
        Write-Failure "PMD analysis failed"
        $script:Errors++
    }
} catch {
    Write-Failure "PMD analysis failed: $_"
    $script:Errors++
}

################################################################################
# Step 7: Generate Maven site
################################################################################
Write-Header "Step 7: Generate Maven Site"
try {
    & .\mvnw.cmd -B site -Dcheckstyle.failsOnError=false -Dpmd.failOnViolation=false
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Maven site generated"
        
        if (Test-Path "target\site\index.html") {
            Write-Info "Site homepage: target\site\index.html"
        }
    } else {
        Write-Warning2 "Maven site generation failed (non-critical)"
        $script:Warnings++
    }
} catch {
    Write-Warning2 "Maven site generation failed: $_"
    $script:Warnings++
}

################################################################################
# Step 8: Run quality gates
################################################################################
Write-Header "Step 8: Run Quality Gates"

# Checkstyle quality gate
Write-Info "Running Checkstyle quality gate..."
try {
    & .\mvnw.cmd -B checkstyle:check
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Checkstyle quality gate passed"
    } else {
        Write-Warning2 "Checkstyle quality gate failed"
        $script:Warnings++
    }
} catch {
    Write-Warning2 "Checkstyle quality gate failed"
    $script:Warnings++
}

# PMD quality gate
Write-Info "Running PMD quality gate..."
try {
    & .\mvnw.cmd -B pmd:check
    if ($LASTEXITCODE -eq 0) {
        Write-Success "PMD quality gate passed"
    } else {
        Write-Warning2 "PMD quality gate failed"
        $script:Warnings++
    }
} catch {
    Write-Warning2 "PMD quality gate failed"
    $script:Warnings++
}

################################################################################
# Step 9: Package application
################################################################################
Write-Header "Step 9: Package Application"
try {
    & .\mvnw.cmd -B package -DskipTests
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Application packaged"
        
        $jarFiles = Get-ChildItem "target\*.jar" -ErrorAction SilentlyContinue
        if ($jarFiles) {
            $jarFile = $jarFiles[0]
            $jarSize = "{0:N2} MB" -f ($jarFile.Length / 1MB)
            Write-Info "JAR file: $($jarFile.Name) ($jarSize)"
        }
    } else {
        Write-Failure "Packaging failed"
        $script:Errors++
    }
} catch {
    Write-Failure "Packaging failed: $_"
    $script:Errors++
}

################################################################################
# Step 10: Generate report screenshots
################################################################################
Write-Header "Step 10: Generate Report Screenshots"

# Note: wkhtmltoimage is not commonly available on Windows
# Users can install it from: https://wkhtmltopdf.org/downloads.html
Write-Warning2 "Screenshot generation requires wkhtmltoimage"
Write-Info "Install from: https://wkhtmltopdf.org/downloads.html"
Write-Info "Then run: bash scripts/html_to_png.sh (in Git Bash or WSL)"
$script:Warnings++

################################################################################
# Summary
################################################################################
Write-Header "CI Pipeline Summary"

Write-Host "`nResults:" -ForegroundColor White
Write-Host "--------" -ForegroundColor White

if ($script:Errors -eq 0 -and $script:Warnings -eq 0) {
    Write-Success "All checks passed! ✨"
    Write-Host ""
    Write-Info "Your code is ready to push 🚀"
    exit 0
} elseif ($script:Errors -eq 0) {
    Write-Warning2 "Checks completed with $($script:Warnings) warning(s)"
    Write-Host ""
    Write-Info "Review warnings before pushing"
    exit 0
} else {
    Write-Failure "CI pipeline failed with $($script:Errors) error(s) and $($script:Warnings) warning(s)"
    Write-Host ""
    Write-Failure "Please fix errors before pushing"
    exit 1
}
