#!/bin/bash

# EternalJukebox Emoji Removal Script
# This script removes emojis from all markdown files in the codebase

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
LOG_FILE="$SCRIPT_DIR/emoji-removal-$(date +%Y%m%d-%H%M%S).log"

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

# Find all markdown files
find_markdown_files() {
    info "Finding all markdown files..."
    
    local markdown_files=($(find "$PROJECT_ROOT" -name "*.md" -type f | grep -v backup-))
    local count=${#markdown_files[@]}
    
    info "Found $count markdown files"
    
    for file in "${markdown_files[@]}"; do
        echo "  - $file"
    done
    
    echo "${markdown_files[@]}"
}

# Remove emojis from a single file
remove_emojis_from_file() {
    local file="$1"
    local temp_file="${file}.tmp"
    
    # Create a backup of the original file
    cp "$file" "${file}.backup"
    
    # Remove common emojis using sed
    sed -E 's/[🎵🧠🐳📋🔒🐛✨📚❓📊🚀✅⚠️🔧📈📉🎯💡🛡️🔍📝📖🎨🔧⚙️🎪🎭🎨🎬🎮🎯🎲🎳🎴🎵🎶🎸🎹🎺🎻🎼🎽🎾🎿🏀🏁🏂🏃🏄🏅🏆🏇🏈🏉🏊🏋🏌🏍🏎🏏🏐🏑🏒🏓🏔🏕🏖🏗🏘🏙🏚🏛🏜🏝🏞🏟🏠🏡🏢🏣🏤🏥🏦🏧🏨🏩🏪🏫🏬🏭🏮🏯🏰🏱🏲🏳🏴🏵🏶🏷🏸🏹🏺🏻🏼🏽🏾🏿]/ /g' "$file" > "$temp_file"
    
    # Additional emoji patterns
    sed -i -E 's/[😀😁😂😃😄😅😆😇😈😉😊😋😌😍😎😏😐😑😒😓😔😕😖😗😘😙😚😛😜😝😞😟😠😡😢😣😤😥😦😧😨😩😪😫😬😭😮😯😰😱😲😳😴😵😶😷😸😹😺😻😼😽😾😿]/ /g' "$temp_file"
    sed -i -E 's/[🙀🙁🙂🙃🙄🙅🙆🙇🙈🙉🙊🙋🙌🙍🙎🙏]/ /g' "$temp_file"
    
    # Remove any remaining emoji-like characters
    sed -i -E 's/[^\x00-\x7F]//g' "$temp_file"
    
    # Clean up extra spaces
    sed -i -E 's/ +/ /g' "$temp_file"
    sed -i -E 's/^ +//g' "$temp_file"
    sed -i -E 's/ +$//g' "$temp_file"
    
    # Replace the original file
    mv "$temp_file" "$file"
    
    # Check if file was modified
    if ! diff -q "${file}.backup" "$file" >/dev/null 2>&1; then
        info "Removed emojis from: $file"
        return 0
    else
        info "No emojis found in: $file"
        rm "${file}.backup"
        return 1
    fi
}

# Remove emojis from all markdown files
remove_emojis_from_all_files() {
    info "Removing emojis from all markdown files..."
    
    local markdown_files=($(find_markdown_files))
    local files_modified=0
    local files_processed=0
    
    for file in "${markdown_files[@]}"; do
        files_processed=$((files_processed + 1))
        
        if remove_emojis_from_file "$file"; then
            files_modified=$((files_modified + 1))
        fi
    done
    
    success "Processed $files_processed files, modified $files_modified files"
}

# Validate markdown files after emoji removal
validate_markdown_files() {
    info "Validating markdown files after emoji removal..."
    
    local markdown_files=($(find "$PROJECT_ROOT" -name "*.md" -type f | grep -v backup-))
    local validation_errors=0
    
    for file in "${markdown_files[@]}"; do
        # Check if file is valid UTF-8
        if ! file -b --mime-encoding "$file" | grep -q "utf-8"; then
            warning "File encoding issue in: $file"
            validation_errors=$((validation_errors + 1))
        fi
        
        # Check for basic markdown syntax
        if ! grep -q "^#" "$file"; then
            warning "No headers found in: $file"
        fi
    done
    
    if [[ $validation_errors -eq 0 ]]; then
        success "All markdown files validated successfully"
    else
        warning "$validation_errors validation errors found"
    fi
}

# Generate emoji removal report
generate_report() {
    info "Generating emoji removal report..."
    
    local report_file="$SCRIPT_DIR/emoji-removal-report-$(date +%Y%m%d-%H%M%S).txt"
    
    cat > "$report_file" << EOF
EternalJukebox Emoji Removal Report
Generated: $(date)
Script Version: 1.0

Files Processed:
EOF
    
    # List all markdown files
    find "$PROJECT_ROOT" -name "*.md" -type f | grep -v backup- >> "$report_file"
    
    echo "" >> "$report_file"
    echo "Backup Files Created:" >> "$report_file"
    find "$PROJECT_ROOT" -name "*.backup" -type f >> "$report_file"
    
    echo "" >> "$report_file"
    echo "Log File: $LOG_FILE" >> "$report_file"
    
    success "Emoji removal report generated: $report_file"
}

# Clean up backup files (optional)
cleanup_backups() {
    info "Cleaning up backup files..."
    
    local backup_files=($(find "$PROJECT_ROOT" -name "*.backup" -type f))
    local backup_count=${#backup_files[@]}
    
    if [[ $backup_count -gt 0 ]]; then
        info "Found $backup_count backup files"
        echo "Backup files:"
        for file in "${backup_files[@]}"; do
            echo "  - $file"
        done
        
        echo ""
        echo "To remove backup files, run:"
        echo "find $PROJECT_ROOT -name '*.backup' -type f -delete"
    else
        info "No backup files found"
    fi
}

# Main execution
main() {
    log "Starting EternalJukebox emoji removal process..."
    
    # Pre-flight checks
    check_directory
    
    # Remove emojis
    remove_emojis_from_all_files
    
    # Validate files
    validate_markdown_files
    
    # Generate report
    generate_report
    
    # Cleanup info
    cleanup_backups
    
    success "Emoji removal process completed successfully!"
    info "Log file: $LOG_FILE"
    
    echo ""
    echo "${GREEN}Summary:${NC}"
    echo "- All emojis have been removed from markdown files"
    echo "- Backup files created for each modified file"
    echo "- Validation completed on all files"
    echo ""
    echo "${YELLOW}Note: Review the changes and remove backup files when satisfied${NC}"
}

# Run main function
main "$@"
