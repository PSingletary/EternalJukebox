#!/bin/bash

# EternalJukebox Health Check Script
# This script performs comprehensive health checks for the EternalJukebox deployment

set -e

# Configuration
MAIN_SERVICE_URL="${MAIN_SERVICE_URL:-http://localhost:8080}"
ANALYSIS_SERVICE_URL="${ANALYSIS_SERVICE_URL:-http://localhost:5000}"
TIMEOUT="${TIMEOUT:-30}"
RETRY_COUNT="${RETRY_COUNT:-3}"
RETRY_DELAY="${RETRY_DELAY:-10}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if a service is responding
check_service() {
    local service_name="$1"
    local service_url="$2"
    local endpoint="$3"
    local expected_status="${4:-200}"
    
    log_info "Checking $service_name health..."
    
    for i in $(seq 1 $RETRY_COUNT); do
        if response=$(curl -s -w "%{http_code}" -o /dev/null --max-time $TIMEOUT "$service_url$endpoint" 2>/dev/null); then
            if [ "$response" = "$expected_status" ]; then
                log_success "$service_name is healthy (HTTP $response)"
                return 0
            else
                log_warning "$service_name returned HTTP $response (expected $expected_status)"
            fi
        else
            log_warning "$service_name not responding (attempt $i/$RETRY_COUNT)"
        fi
        
        if [ $i -lt $RETRY_COUNT ]; then
            sleep $RETRY_DELAY
        fi
    done
    
    log_error "$service_name health check failed after $RETRY_COUNT attempts"
    return 1
}

# Check Docker container health
check_docker_containers() {
    log_info "Checking Docker container health..."
    
    # Check if docker is available
    if ! command -v docker &> /dev/null; then
        log_warning "Docker not available, skipping container health checks"
        return 0
    fi
    
    # Get list of EternalJukebox containers
    containers=$(docker ps --filter "name=eternaljukebox" --format "{{.Names}}" 2>/dev/null || true)
    
    if [ -z "$containers" ]; then
        log_warning "No EternalJukebox containers found"
        return 0
    fi
    
    local all_healthy=true
    
    for container in $containers; do
        health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "unknown")
        
        case $health in
            "healthy")
                log_success "Container $container is healthy"
                ;;
            "unhealthy")
                log_error "Container $container is unhealthy"
                all_healthy=false
                ;;
            "starting")
                log_warning "Container $container is starting"
                ;;
            "unknown")
                log_warning "Container $container health status unknown"
                ;;
        esac
    done
    
    if [ "$all_healthy" = false ]; then
        return 1
    fi
    
    return 0
}

# Check disk space
check_disk_space() {
    log_info "Checking disk space..."
    
    # Check available disk space (require at least 1GB free)
    available_space=$(df / | awk 'NR==2 {print $4}')
    required_space=1048576  # 1GB in KB
    
    if [ "$available_space" -lt "$required_space" ]; then
        log_error "Insufficient disk space: $(($available_space / 1024))MB available, 1GB required"
        return 1
    else
        log_success "Disk space OK: $(($available_space / 1024))MB available"
    fi
    
    return 0
}

# Check memory usage
check_memory() {
    log_info "Checking memory usage..."
    
    if command -v free &> /dev/null; then
        # Get memory info
        total_mem=$(free -m | awk 'NR==2{print $2}')
        used_mem=$(free -m | awk 'NR==2{print $3}')
        available_mem=$(free -m | awk 'NR==2{print $7}')
        
        # Check if less than 10% memory is available
        available_percent=$((available_mem * 100 / total_mem))
        
        if [ "$available_percent" -lt 10 ]; then
            log_warning "Low memory available: ${available_mem}MB (${available_percent}%)"
        else
            log_success "Memory OK: ${available_mem}MB available (${available_percent}%)"
        fi
    else
        log_warning "Memory check not available on this system"
    fi
    
    return 0
}

# Check network connectivity
check_network() {
    log_info "Checking network connectivity..."
    
    # Check if we can reach external services
    if curl -s --max-time 10 https://www.google.com > /dev/null 2>&1; then
        log_success "External network connectivity OK"
    else
        log_warning "External network connectivity issues detected"
    fi
    
    # Check if we can resolve DNS
    if nslookup google.com > /dev/null 2>&1; then
        log_success "DNS resolution OK"
    else
        log_warning "DNS resolution issues detected"
    fi
    
    return 0
}

# Check service dependencies
check_dependencies() {
    log_info "Checking service dependencies..."
    
    # Check if required commands are available
    local commands=("curl" "docker" "jq")
    local missing_commands=()
    
    for cmd in "${commands[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            missing_commands+=("$cmd")
        fi
    done
    
    if [ ${#missing_commands[@]} -gt 0 ]; then
        log_warning "Missing optional commands: ${missing_commands[*]}"
    else
        log_success "All required commands available"
    fi
    
    return 0
}

# Generate health report
generate_report() {
    local exit_code="$1"
    
    echo
    echo "=================================="
    echo "EternalJukebox Health Check Report"
    echo "=================================="
    echo "Timestamp: $(date)"
    echo "Main Service: $MAIN_SERVICE_URL"
    echo "Analysis Service: $ANALYSIS_SERVICE_URL"
    echo "Exit Code: $exit_code"
    
    if [ "$exit_code" -eq 0 ]; then
        echo "Status: ✅ HEALTHY"
        log_success "All health checks passed!"
    else
        echo "Status: ❌ UNHEALTHY"
        log_error "One or more health checks failed!"
    fi
    
    echo "=================================="
}

# Main health check function
main() {
    log_info "Starting EternalJukebox health checks..."
    echo "Main Service URL: $MAIN_SERVICE_URL"
    echo "Analysis Service URL: $ANALYSIS_SERVICE_URL"
    echo
    
    local exit_code=0
    
    # Run all health checks
    check_dependencies || exit_code=1
    check_network || exit_code=1
    check_disk_space || exit_code=1
    check_memory || exit_code=1
    check_docker_containers || exit_code=1
    check_service "Main Service" "$MAIN_SERVICE_URL" "/healthy" "200" || exit_code=1
    check_service "Analysis Service" "$ANALYSIS_SERVICE_URL" "/health" "200" || exit_code=1
    
    # Generate final report
    generate_report $exit_code
    
    exit $exit_code
}

# Handle script arguments
case "${1:-}" in
    --help|-h)
        echo "EternalJukebox Health Check Script"
        echo
        echo "Usage: $0 [options]"
        echo
        echo "Options:"
        echo "  --help, -h          Show this help message"
        echo "  --main-url URL      Main service URL (default: http://localhost:8080)"
        echo "  --analysis-url URL  Analysis service URL (default: http://localhost:5000)"
        echo "  --timeout SECONDS   Request timeout (default: 30)"
        echo "  --retries COUNT     Number of retries (default: 3)"
        echo "  --retry-delay SEC   Delay between retries (default: 10)"
        echo
        echo "Environment Variables:"
        echo "  MAIN_SERVICE_URL    Main service URL"
        echo "  ANALYSIS_SERVICE_URL Analysis service URL"
        echo "  TIMEOUT            Request timeout in seconds"
        echo "  RETRY_COUNT        Number of retry attempts"
        echo "  RETRY_DELAY        Delay between retries in seconds"
        echo
        exit 0
        ;;
    --main-url)
        MAIN_SERVICE_URL="$2"
        shift 2
        ;;
    --analysis-url)
        ANALYSIS_SERVICE_URL="$2"
        shift 2
        ;;
    --timeout)
        TIMEOUT="$2"
        shift 2
        ;;
    --retries)
        RETRY_COUNT="$2"
        shift 2
        ;;
    --retry-delay)
        RETRY_DELAY="$2"
        shift 2
        ;;
esac

# Run main health check
main "$@"
