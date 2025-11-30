#!/bin/bash

set -euo pipefail

echo "Converting HTML reports to PNG..."

REPORTS_DIR="reports"
HTML_OUTPUT="${REPORTS_DIR}/html"
PNG_OUTPUT="${REPORTS_DIR}/png"

# Ensure report output directories exist
mkdir -p "${HTML_OUTPUT}/jacoco" "${HTML_OUTPUT}/pmd" "${PNG_OUTPUT}"

# Check for JaCoCo HTML report
JACOCO_HTML="target/site/jacoco/index.html"
if [ ! -f "$JACOCO_HTML" ]; then
    echo "ERROR: JaCoCo HTML report not found at $JACOCO_HTML"
    echo "Available files in target/site/:"
    ls -la target/site/ || echo "No target/site directory"
    echo "Available files in target/site/jacoco/:"
    ls -la target/site/jacoco/ || echo "No target/site/jacoco directory"
    exit 1
fi

# Check for PMD HTML report (try multiple possible locations)
PMD_HTML=""
if [ -f "target/site/pmd.html" ]; then
    PMD_HTML="target/site/pmd.html"
elif [ -f "target/site/pmd/index.html" ]; then
    PMD_HTML="target/site/pmd/index.html"
elif [ -f "target/site/pmd/pmd.html" ]; then
    PMD_HTML="target/site/pmd/pmd.html"
else
    echo "ERROR: PMD HTML report not found"
    echo "Searched locations:"
    echo "  - target/site/pmd.html"
    echo "  - target/site/pmd/index.html"  
    echo "  - target/site/pmd/pmd.html"
    echo "Available files in target/site/:"
    ls -la target/site/ || echo "No target/site directory"
    echo "Searching for any PMD-related files:"
    find target -name "*pmd*" -type f || echo "No PMD files found"
    exit 1
fi

echo "Converting JaCoCo report: $JACOCO_HTML -> reports/jacoco.png"
wkhtmltoimage --enable-local-file-access --width 1600 --quality 90 \
  "$JACOCO_HTML" "${PNG_OUTPUT}/jacoco.png"

echo "Converting PMD report: $PMD_HTML -> reports/pmd.png"
wkhtmltoimage --enable-local-file-access --width 1600 --quality 90 \
  "$PMD_HTML" "${PNG_OUTPUT}/pmd.png"

# Copy HTML assets for artifact collection
echo "Copying HTML assets into ${HTML_OUTPUT}"
JACOCO_DIR="$(dirname "$JACOCO_HTML")"
PMD_DIR="$(dirname "$PMD_HTML")"
cp -R "${JACOCO_DIR}/." "${HTML_OUTPUT}/jacoco/"
cp -R "${PMD_DIR}/." "${HTML_OUTPUT}/pmd/"

echo "HTML to PNG conversion completed successfully"
echo "Generated files:"
ls -la "${PNG_OUTPUT}"
ls -la "${HTML_OUTPUT}/jacoco" "${HTML_OUTPUT}/pmd"
