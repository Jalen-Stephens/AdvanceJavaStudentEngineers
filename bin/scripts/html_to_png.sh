#!/bin/bash

set -euo pipefail

echo "Converting HTML reports to PNG..."

# Ensure reports directory exists
mkdir -p reports

# Check for JaCoCo HTML report
JACOCO_HTML="target/site/jacoco/index.html"
if [ ! -f "$JACOCO_HTML" ]; then
    echo "ERROR: JaCoCo HTML report not found at $JACOCO_HTML"
    exit 1
fi

# Check for PMD HTML report (try both possible locations)
PMD_HTML=""
if [ -f "target/site/pmd.html" ]; then
    PMD_HTML="target/site/pmd.html"
elif [ -f "target/site/pmd/index.html" ]; then
    PMD_HTML="target/site/pmd/index.html"
else
    echo "ERROR: PMD HTML report not found at target/site/pmd.html or target/site/pmd/index.html"
    exit 1
fi

echo "Converting JaCoCo report: $JACOCO_HTML -> reports/jacoco.png"
wkhtmltoimage --width 1600 --quality 90 "$JACOCO_HTML" reports/jacoco.png

echo "Converting PMD report: $PMD_HTML -> reports/pmd.png"
wkhtmltoimage --width 1600 --quality 90 "$PMD_HTML" reports/pmd.png

echo "HTML to PNG conversion completed successfully"
echo "Generated files:"
ls -la reports/