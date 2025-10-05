#!/bin/bash

# EternalJukebox Email Update Validation Script
# This script validates that all email updates were applied correctly

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CONFIG_FILE="$SCRIPT_DIR/email-mapping.conf"
VALIDATION_LOG="$SCRIPT_DIR/email-validation-$(date +%Y%m%d-%H%M%S).log"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$VALIDATION_LOG"
}

# Error handling
error_exit() {
    log "${RED}ERROR: $1${NC}"
    exit 1
}

# Success message
success() {
    log "${GREEN}SUCCESS: $1${NC}"
}

# Warning message
warning() {
    log "${YELLOW}WARNING: $1${NC}"
}

# Info message
info() {
    log "${BLUE}INFO: $1${NC}"
}

# Check if running from correct directory
check_directory() {
    if [[ ! -f "$PROJECT_ROOT/README.md" ]]; then
        error_exit "Please run this script from the EternalJukebox project root directory"
    fi
    info "Running validation from project root: $PROJECT_ROOT"
}

# Load configuration
load_config() {
    if [[ ! -f "$CONFIG_FILE" ]]; then
        error_exit "Configuration file not found: $CONFIG_FILE"
    fi
    
    source "$CONFIG_FILE"
    info "Configuration loaded successfully"
    info "New domain: $NEW_DOMAIN"
    info "New local domain: $NEW_LOCAL_DOMAIN"
}

# Check for remaining old email addresses
check_old_emails() {
    info "Checking for remaining old email addresses..."
    
    local old_emails_found=0
    
    # Check for eternaljukebox.com emails
    local eternaljukebox_com=$(grep -r "eternaljukebox\.com" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null | wc -l)
    if [[ $eternaljukebox_com -gt 0 ]]; then
        warning "Found $eternaljukebox_com instances of eternaljukebox.com"
        grep -r "eternaljukebox\.com" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null | head -10
        old_emails_found=$((old_emails_found + eternaljukebox_com))
    fi
    
    # Check for eternaljukebox.local emails
    local eternaljukebox_local=$(grep -r "eternaljukebox\.local" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null | wc -l)
    if [[ $eternaljukebox_local -gt 0 ]]; then
        warning "Found $eternaljukebox_local instances of eternaljukebox.local"
        grep -r "eternaljukebox\.local" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null | head -10
        old_emails_found=$((old_emails_found + eternaljukebox_local))
    fi
    
    # Check for your-domain.com templates
    local your_domain_com=$(grep -r "your-domain\.com" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null | wc -l)
    if [[ $your_domain_com -gt 0 ]]; then
        warning "Found $your_domain_com instances of your-domain.com"
        grep -r "your-domain\.com" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null | head -10
        old_emails_found=$((old_emails_found + your_domain_com))
    fi
    
    if [[ $old_emails_found -eq 0 ]]; then
        success "No old email addresses found"
    else
        warning "Total old email addresses found: $old_emails_found"
    fi
}

# Validate YAML files
validate_yaml_files() {
    info "Validating YAML files..."
    
    local yaml_errors=0
    local yaml_files=0
    
    for yaml_file in $(find "$PROJECT_ROOT" -name "*.yml" -o -name "*.yaml" | grep -v backup-); do
        yaml_files=$((yaml_files + 1))
        
        if ! python3 -c "import yaml; yaml.safe_load(open('$yaml_file'))" 2>/dev/null; then
            warning "YAML validation failed for: $yaml_file"
            yaml_errors=$((yaml_errors + 1))
        else
            info "YAML validation passed for: $yaml_file"
        fi
    done
    
    if [[ $yaml_errors -eq 0 ]]; then
        success "All $yaml_files YAML files validated successfully"
    else
        warning "$yaml_errors out of $yaml_files YAML files failed validation"
    fi
}

# Validate shell scripts
validate_shell_scripts() {
    info "Validating shell scripts..."
    
    local shell_errors=0
    local shell_files=0
    
    for sh_file in $(find "$PROJECT_ROOT" -name "*.sh" | grep -v backup-); do
        shell_files=$((shell_files + 1))
        
        if ! bash -n "$sh_file" 2>/dev/null; then
            warning "Shell script validation failed for: $sh_file"
            shell_errors=$((shell_errors + 1))
        else
            info "Shell script validation passed for: $sh_file"
        fi
    done
    
    if [[ $shell_errors -eq 0 ]]; then
        success "All $shell_files shell scripts validated successfully"
    else
        warning "$shell_errors out of $shell_files shell scripts failed validation"
    fi
}

# Check for new email addresses
check_new_emails() {
    info "Checking for new email addresses..."
    
    local new_emails_found=0
    
    # Check for new domain emails
    local new_domain=$(grep -r "@$NEW_DOMAIN" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null | wc -l)
    if [[ $new_domain -gt 0 ]]; then
        success "Found $new_domain instances of @$NEW_DOMAIN"
        new_emails_found=$((new_emails_found + new_domain))
    fi
    
    # Check for new local domain emails
    local new_local_domain=$(grep -r "@$NEW_LOCAL_DOMAIN" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null | wc -l)
    if [[ $new_local_domain -gt 0 ]]; then
        success "Found $new_local_domain instances of @$NEW_LOCAL_DOMAIN"
        new_emails_found=$((new_emails_found + new_local_domain))
    fi
    
    if [[ $new_emails_found -gt 0 ]]; then
        success "Total new email addresses found: $new_emails_found"
    else
        warning "No new email addresses found - updates may not have been applied"
    fi
}

# Test email functionality
test_email_functionality() {
    info "Testing email functionality..."
    
    # Test if security email is accessible
    if command -v mail >/dev/null 2>&1; then
        echo "Testing security email: $NEW_SECURITY_EMAIL"
        echo "This is a test email from EternalJukebox validation script" | mail -s "EternalJukebox Email Test" "$NEW_SECURITY_EMAIL" 2>/dev/null || warning "Could not send test email to $NEW_SECURITY_EMAIL"
    else
        warning "Mail command not available - cannot test email functionality"
    fi
    
    # Test if support email is accessible
    if command -v mail >/dev/null 2>&1; then
        echo "Testing support email: $NEW_SUPPORT_EMAIL"
        echo "This is a test email from EternalJukebox validation script" | mail -s "EternalJukebox Email Test" "$NEW_SUPPORT_EMAIL" 2>/dev/null || warning "Could not send test email to $NEW_SUPPORT_EMAIL"
    fi
}

# Check DNS resolution
check_dns_resolution() {
    info "Checking DNS resolution for new domain..."
    
    if command -v nslookup >/dev/null 2>&1; then
        if nslookup "$NEW_DOMAIN" >/dev/null 2>&1; then
            success "DNS resolution successful for $NEW_DOMAIN"
        else
            warning "DNS resolution failed for $NEW_DOMAIN"
        fi
    else
        warning "nslookup command not available - cannot test DNS resolution"
    fi
}

# Generate validation report
generate_validation_report() {
    info "Generating validation report..."
    
    local report_file="$SCRIPT_DIR/email-validation-report-$(date +%Y%m%d-%H%M%S).txt"
    
    cat > "$report_file" << EOF
EternalJukebox Email Update Validation Report
Generated: $(date)
Script Version: 1.0

Configuration Used:
- Config File: $CONFIG_FILE
- New Domain: $NEW_DOMAIN
- New Local Domain: $NEW_LOCAL_DOMAIN

Validation Results:
EOF
    
    # Add validation results
    echo "Old Email Addresses Found:" >> "$report_file"
    grep -r "eternaljukebox\.com\|eternaljukebox\.local\|your-domain\.com" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null >> "$report_file" || echo "None found" >> "$report_file"
    
    echo "" >> "$report_file"
    echo "New Email Addresses Found:" >> "$report_file"
    grep -r "@$NEW_DOMAIN\|@$NEW_LOCAL_DOMAIN" "$PROJECT_ROOT" --exclude-dir=.git --exclude-dir=backup-* 2>/dev/null >> "$report_file" || echo "None found" >> "$report_file"
    
    echo "" >> "$report_file"
    echo "YAML Files Validated:" >> "$report_file"
    find "$PROJECT_ROOT" -name "*.yml" -o -name "*.yaml" | grep -v backup- >> "$report_file"
    
    echo "" >> "$report_file"
    echo "Shell Scripts Validated:" >> "$report_file"
    find "$PROJECT_ROOT" -name "*.sh" | grep -v backup- >> "$report_file"
    
    echo "" >> "$report_file"
    echo "Validation Log: $VALIDATION_LOG" >> "$report_file"
    
    success "Validation report generated: $report_file"
}

# Main execution
main() {
    log "Starting EternalJukebox email validation process..."
    
    # Pre-flight checks
    check_directory
    load_config
    
    # Run validations
    check_old_emails
    validate_yaml_files
    validate_shell_scripts
    check_new_emails
    check_dns_resolution
    test_email_functionality
    
    # Generate report
    generate_validation_report
    
    success "Email validation process completed!"
    info "Validation log: $VALIDATION_LOG"
    
    echo ""
    echo "${GREEN}Validation Summary:${NC}"
    echo "- Check the validation report for detailed results"
    echo "- Review any warnings or errors"
    echo "- Test email functionality manually if needed"
    echo "- Update DNS records if DNS resolution failed"
    echo ""
}

# Run main function
main "$@"
