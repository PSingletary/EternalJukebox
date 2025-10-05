#!/bin/bash

# EternalJukebox Email Address Update Script
# This script automatically updates all email addresses in the codebase
# based on the configuration file email-mapping.conf

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
BACKUP_DIR="$PROJECT_ROOT/backup-$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$SCRIPT_DIR/email-update.log"

# Logging function
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
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
    info "Running from project root: $PROJECT_ROOT"
}

# Check if config file exists
check_config() {
    if [[ ! -f "$CONFIG_FILE" ]]; then
        error_exit "Configuration file not found: $CONFIG_FILE"
    fi
    info "Using configuration file: $CONFIG_FILE"
}

# Create backup
create_backup() {
    info "Creating backup of current codebase..."
    mkdir -p "$BACKUP_DIR"
    
    # Copy all files except .git directory
    rsync -av --exclude='.git' --exclude='backup-*' "$PROJECT_ROOT/" "$BACKUP_DIR/"
    
    if [[ $? -eq 0 ]]; then
        success "Backup created: $BACKUP_DIR"
    else
        error_exit "Failed to create backup"
    fi
}

# Validate configuration file
validate_config() {
    info "Validating configuration file..."
    
    # Check for required sections
    local required_sections=("PRODUCTION_EMAILS" "INTERNAL_EMAILS" "TEMPLATE_EMAILS")
    
    for section in "${required_sections[@]}"; do
        if ! grep -q "^\[$section\]" "$CONFIG_FILE"; then
            error_exit "Missing required section: $section"
        fi
    done
    
    # Check for required variables
    local required_vars=("NEW_DOMAIN" "NEW_LOCAL_DOMAIN")
    
    for var in "${required_vars[@]}"; do
        if ! grep -q "^$var=" "$CONFIG_FILE"; then
            error_exit "Missing required variable: $var"
        fi
    done
    
    success "Configuration file validation passed"
}

# Load configuration
load_config() {
    info "Loading configuration..."
    
    # Source the config file
    source "$CONFIG_FILE"
    
    # Validate loaded variables
    if [[ -z "$NEW_DOMAIN" ]]; then
        error_exit "NEW_DOMAIN not set in configuration"
    fi
    
    if [[ -z "$NEW_LOCAL_DOMAIN" ]]; then
        error_exit "NEW_LOCAL_DOMAIN not set in configuration"
    fi
    
    info "Configuration loaded successfully"
    info "New domain: $NEW_DOMAIN"
    info "New local domain: $NEW_LOCAL_DOMAIN"
}

# Update production emails
update_production_emails() {
    info "Updating production email addresses..."
    
    local files_updated=0
    
    # Update security email
    if [[ -n "$NEW_SECURITY_EMAIL" ]]; then
        find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" \) \
            -exec sed -i "s/security@eternaljukebox\.com/$NEW_SECURITY_EMAIL/g" {} \;
        files_updated=$((files_updated + 1))
        info "Updated security email to: $NEW_SECURITY_EMAIL"
    fi
    
    # Update support email
    if [[ -n "$NEW_SUPPORT_EMAIL" ]]; then
        find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" \) \
            -exec sed -i "s/support@eternaljukebox\.com/$NEW_SUPPORT_EMAIL/g" {} \;
        files_updated=$((files_updated + 1))
        info "Updated support email to: $NEW_SUPPORT_EMAIL"
    fi
    
    success "Updated $files_updated production email types"
}

# Update internal emails
update_internal_emails() {
    info "Updating internal email addresses..."
    
    local files_updated=0
    
    # Update all eternaljukebox.local emails
    find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" -o -name "*.sh" \) \
        -exec sed -i "s/@eternaljukebox\.local/@$NEW_LOCAL_DOMAIN/g" {} \;
    
    files_updated=$((files_updated + 1))
    success "Updated internal email domain to: $NEW_LOCAL_DOMAIN"
}

# Update template emails
update_template_emails() {
    info "Updating template email addresses..."
    
    local files_updated=0
    
    # Update your-domain.com templates
    find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" \) \
        -exec sed -i "s/admin@your-domain\.com/admin@$NEW_DOMAIN/g" {} \;
    
    find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" \) \
        -exec sed -i "s/alerts@your-domain\.com/alerts@$NEW_DOMAIN/g" {} \;
    
    find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" \) \
        -exec sed -i "s/your-email@domain\.com/your-email@$NEW_DOMAIN/g" {} \;
    
    find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" \) \
        -exec sed -i "s/github-actions@your-domain\.com/github-actions@$NEW_DOMAIN/g" {} \;
    
    # Update example.com defaults
    find "$PROJECT_ROOT" -type f \( -name "*.yml" -o -name "*.yaml" \) \
        -exec sed -i "s/admin@example\.com/admin@$NEW_DOMAIN/g" {} \;
    
    files_updated=$((files_updated + 1))
    success "Updated template email addresses"
}

# Update specific email addresses from config
update_specific_emails() {
    info "Updating specific email addresses from configuration..."
    
    local files_updated=0
    
    # Read specific email mappings from config
    while IFS='=' read -r old_email new_email; do
        # Skip comments and empty lines
        [[ "$old_email" =~ ^#.*$ ]] && continue
        [[ -z "$old_email" ]] && continue
        
        # Skip section headers
        [[ "$old_email" =~ ^\[.*\]$ ]] && continue
        
        # Skip variable assignments
        [[ "$old_email" =~ ^[A-Z_]+= ]] && continue
        
        if [[ -n "$new_email" ]]; then
            find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" -o -name "*.sh" -o -name "*.kt" \) \
                -exec sed -i "s|$old_email|$new_email|g" {} \;
            
            files_updated=$((files_updated + 1))
            info "Updated: $old_email -> $new_email"
        fi
    done < "$CONFIG_FILE"
    
    success "Updated $files_updated specific email addresses"
}

# Validate updated files
validate_updates() {
    info "Validating updated files..."
    
    local validation_errors=0
    
    # Check YAML files
    for yaml_file in $(find "$PROJECT_ROOT" -name "*.yml" -o -name "*.yaml"); do
        if ! python3 -c "import yaml; yaml.safe_load(open('$yaml_file'))" 2>/dev/null; then
            warning "YAML validation failed for: $yaml_file"
            validation_errors=$((validation_errors + 1))
        fi
    done
    
    # Check shell scripts
    for sh_file in $(find "$PROJECT_ROOT" -name "*.sh"); do
        if ! bash -n "$sh_file" 2>/dev/null; then
            warning "Shell script validation failed for: $sh_file"
            validation_errors=$((validation_errors + 1))
        fi
    done
    
    if [[ $validation_errors -eq 0 ]]; then
        success "All file validations passed"
    else
        warning "$validation_errors validation errors found"
    fi
}

# Generate update report
generate_report() {
    info "Generating update report..."
    
    local report_file="$SCRIPT_DIR/email-update-report-$(date +%Y%m%d-%H%M%S).txt"
    
    cat > "$report_file" << EOF
EternalJukebox Email Update Report
Generated: $(date)
Script Version: 1.0

Configuration Used:
- Config File: $CONFIG_FILE
- New Domain: $NEW_DOMAIN
- New Local Domain: $NEW_LOCAL_DOMAIN

Files Updated:
EOF
    
    # Find all files that were modified
    find "$PROJECT_ROOT" -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" -o -name "*.sh" -o -name "*.kt" \) \
        -exec grep -l "@$NEW_DOMAIN\|@$NEW_LOCAL_DOMAIN" {} \; >> "$report_file"
    
    echo "" >> "$report_file"
    echo "Backup Location: $BACKUP_DIR" >> "$report_file"
    echo "Log File: $LOG_FILE" >> "$report_file"
    
    success "Update report generated: $report_file"
}

# Main execution
main() {
    log "Starting EternalJukebox email update process..."
    
    # Pre-flight checks
    check_directory
    check_config
    validate_config
    
    # Load configuration
    load_config
    
    # Create backup
    create_backup
    
    # Update emails
    update_production_emails
    update_internal_emails
    update_template_emails
    update_specific_emails
    
    # Validate updates
    validate_updates
    
    # Generate report
    generate_report
    
    success "Email update process completed successfully!"
    info "Backup available at: $BACKUP_DIR"
    info "Log file: $LOG_FILE"
    
    echo ""
    echo "${GREEN}Next steps:${NC}"
    echo "1. Review the changes in your code editor"
    echo "2. Test the updated configuration files"
    echo "3. Update your DNS records for the new domain"
    echo "4. Configure your email server for the new domain"
    echo "5. Test email functionality"
    echo ""
    echo "${YELLOW}If you need to rollback, restore from: $BACKUP_DIR${NC}"
}

# Run main function
main "$@"
