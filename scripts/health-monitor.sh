#!/bin/bash

# EternalJukebox Enhanced Health Monitoring Script
# This script performs comprehensive health checks and integrates with the monitoring stack

set -e

# Configuration
MAIN_SERVICE_URL="${MAIN_SERVICE_URL:-http://localhost:8080}"
ANALYSIS_SERVICE_URL="${ANALYSIS_SERVICE_URL:-http://localhost:5000}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
TIMEOUT="${TIMEOUT:-30}"
RETRY_COUNT="${RETRY_COUNT:-3}"
RETRY_DELAY="${RETRY_DELAY:-10}"
LOG_LEVEL="${LOG_LEVEL:-INFO}"
METRICS_ENABLED="${METRICS_ENABLED:-true}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
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

log_debug() {
    if [ "$LOG_LEVEL" = "DEBUG" ]; then
        echo -e "${PURPLE}[DEBUG]${NC} $1"
    fi
}

log_metrics() {
    if [ "$METRICS_ENABLED" = "true" ]; then
        echo -e "${CYAN}[METRICS]${NC} $1"
    fi
}

# Health check results storage
declare -A HEALTH_RESULTS
declare -A METRICS_DATA

# Check if a service is responding with detailed metrics
check_service_detailed() {
    local service_name="$1"
    local service_url="$2"
    local endpoint="$3"
    local expected_status="${4:-200}"
    
    log_info "Checking $service_name health..."
    
    local start_time=$(date +%s.%N)
    local response_time=0
    local http_status=0
    local success=false
    
    for i in $(seq 1 $RETRY_COUNT); do
        local attempt_start=$(date +%s.%N)
        
        if response=$(curl -s -w "%{http_code}|%{time_total}" -o /dev/null --max-time $TIMEOUT "$service_url$endpoint" 2>/dev/null); then
            http_status=$(echo "$response" | cut -d'|' -f1)
            response_time=$(echo "$response" | cut -d'|' -f2)
            
            if [ "$http_status" = "$expected_status" ]; then
                success=true
                break
            else
                log_warning "$service_name returned HTTP $http_status (expected $expected_status)"
            fi
        else
            log_warning "$service_name not responding (attempt $i/$RETRY_COUNT)"
        fi
        
        if [ $i -lt $RETRY_COUNT ]; then
            sleep $RETRY_DELAY
        fi
    done
    
    local end_time=$(date +%s.%N)
    local total_time=$(echo "$end_time - $start_time" | bc -l)
    
    # Store results
    HEALTH_RESULTS["${service_name}_success"]=$success
    HEALTH_RESULTS["${service_name}_http_status"]=$http_status
    HEALTH_RESULTS["${service_name}_response_time"]=$response_time
    HEALTH_RESULTS["${service_name}_total_time"]=$total_time
    
    if [ "$success" = true ]; then
        log_success "$service_name is healthy (HTTP $http_status, ${response_time}s)"
        log_metrics "Response time: ${response_time}s, Total check time: ${total_time}s"
        return 0
    else
        log_error "$service_name health check failed after $RETRY_COUNT attempts"
        return 1
    fi
}

# Check Docker container health with detailed metrics
check_docker_containers_detailed() {
    log_info "Checking Docker container health..."
    
    if ! command -v docker &> /dev/null; then
        log_warning "Docker not available, skipping container health checks"
        HEALTH_RESULTS["docker_available"]=false
        return 0
    fi
    
    HEALTH_RESULTS["docker_available"]=true
    
    # Get list of EternalJukebox containers
    local containers=$(docker ps --filter "name=eternaljukebox" --format "{{.Names}}" 2>/dev/null || true)
    
    if [ -z "$containers" ]; then
        log_warning "No EternalJukebox containers found"
        HEALTH_RESULTS["containers_found"]=0
        return 0
    fi
    
    local container_count=0
    local healthy_count=0
    local unhealthy_count=0
    local starting_count=0
    local unknown_count=0
    
    for container in $containers; do
        container_count=$((container_count + 1))
        
        local health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "unknown")
        local status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "unknown")
        local uptime=$(docker inspect --format='{{.State.StartedAt}}' "$container" 2>/dev/null || echo "unknown")
        
        case $health in
            "healthy")
                log_success "Container $container is healthy (status: $status)"
                healthy_count=$((healthy_count + 1))
                ;;
            "unhealthy")
                log_error "Container $container is unhealthy (status: $status)"
                unhealthy_count=$((unhealthy_count + 1))
                ;;
            "starting")
                log_warning "Container $container is starting (status: $status)"
                starting_count=$((starting_count + 1))
                ;;
            "unknown")
                log_warning "Container $container health status unknown (status: $status)"
                unknown_count=$((unknown_count + 1))
                ;;
        esac
        
        log_debug "Container $container: health=$health, status=$status, uptime=$uptime"
    done
    
    # Store container metrics
    HEALTH_RESULTS["containers_found"]=$container_count
    HEALTH_RESULTS["containers_healthy"]=$healthy_count
    HEALTH_RESULTS["containers_unhealthy"]=$unhealthy_count
    HEALTH_RESULTS["containers_starting"]=$starting_count
    HEALTH_RESULTS["containers_unknown"]=$unknown_count
    
    if [ $unhealthy_count -gt 0 ]; then
        return 1
    fi
    
    return 0
}

# Check system resources with detailed metrics
check_system_resources() {
    log_info "Checking system resources..."
    
    # CPU Usage
    if command -v top &> /dev/null; then
        local cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | awk -F'%' '{print $1}')
        HEALTH_RESULTS["cpu_usage"]=$cpu_usage
        log_metrics "CPU Usage: ${cpu_usage}%"
    fi
    
    # Memory Usage
    if command -v free &> /dev/null; then
        local total_mem=$(free -m | awk 'NR==2{print $2}')
        local used_mem=$(free -m | awk 'NR==2{print $3}')
        local available_mem=$(free -m | awk 'NR==2{print $7}')
        local mem_usage_percent=$((used_mem * 100 / total_mem))
        
        HEALTH_RESULTS["memory_total"]=$total_mem
        HEALTH_RESULTS["memory_used"]=$used_mem
        HEALTH_RESULTS["memory_available"]=$available_mem
        HEALTH_RESULTS["memory_usage_percent"]=$mem_usage_percent
        
        log_metrics "Memory: ${used_mem}MB/${total_mem}MB used (${mem_usage_percent}%), ${available_mem}MB available"
    fi
    
    # Disk Usage
    if command -v df &> /dev/null; then
        local disk_usage=$(df / | awk 'NR==2 {print $5}' | sed 's/%//')
        local disk_available=$(df / | awk 'NR==2 {print $4}')
        
        HEALTH_RESULTS["disk_usage_percent"]=$disk_usage
        HEALTH_RESULTS["disk_available_kb"]=$disk_available
        
        log_metrics "Disk: ${disk_usage}% used, $(($disk_available / 1024))MB available"
    fi
    
    # Load Average
    if [ -f /proc/loadavg ]; then
        local load_1min=$(cat /proc/loadavg | awk '{print $1}')
        local load_5min=$(cat /proc/loadavg | awk '{print $2}')
        local load_15min=$(cat /proc/loadavg | awk '{print $3}')
        
        HEALTH_RESULTS["load_1min"]=$load_1min
        HEALTH_RESULTS["load_5min"]=$load_5min
        HEALTH_RESULTS["load_15min"]=$load_15min
        
        log_metrics "Load Average: ${load_1min} (1m), ${load_5min} (5m), ${load_15min} (15m)"
    fi
    
    return 0
}

# Check monitoring stack health
check_monitoring_stack() {
    log_info "Checking monitoring stack health..."
    
    # Check Prometheus
    if [ "$PROMETHEUS_URL" != "" ]; then
        if curl -s --max-time 10 "$PROMETHEUS_URL/-/healthy" > /dev/null 2>&1; then
            log_success "Prometheus is healthy"
            HEALTH_RESULTS["prometheus_healthy"]=true
        else
            log_warning "Prometheus is not responding"
            HEALTH_RESULTS["prometheus_healthy"]=false
        fi
    fi
    
    # Check Grafana
    if [ "$GRAFANA_URL" != "" ]; then
        if curl -s --max-time 10 "$GRAFANA_URL/api/health" > /dev/null 2>&1; then
            log_success "Grafana is healthy"
            HEALTH_RESULTS["grafana_healthy"]=true
        else
            log_warning "Grafana is not responding"
            HEALTH_RESULTS["grafana_healthy"]=false
        fi
    fi
    
    return 0
}

# Send metrics to Prometheus (if enabled)
send_metrics_to_prometheus() {
    if [ "$METRICS_ENABLED" != "true" ] || [ "$PROMETHEUS_URL" = "" ]; then
        return 0
    fi
    
    log_info "Sending health check metrics to Prometheus..."
    
    local timestamp=$(date +%s)
    local metrics_data=""
    
    # Service health metrics
    for key in "${!HEALTH_RESULTS[@]}"; do
        local value="${HEALTH_RESULTS[$key]}"
        if [[ "$value" =~ ^[0-9]+\.?[0-9]*$ ]]; then
            metrics_data="${metrics_data}eternaljukebox_health_check_${key} ${value} ${timestamp}\n"
        elif [ "$value" = "true" ]; then
            metrics_data="${metrics_data}eternaljukebox_health_check_${key} 1 ${timestamp}\n"
        elif [ "$value" = "false" ]; then
            metrics_data="${metrics_data}eternaljukebox_health_check_${key} 0 ${timestamp}\n"
        fi
    done
    
    # Send to Prometheus pushgateway (if available)
    if command -v curl &> /dev/null; then
        echo -e "$metrics_data" | curl -s --data-binary @- "$PROMETHEUS_URL/metrics/job/eternaljukebox-health-check" || true
        log_debug "Metrics sent to Prometheus"
    fi
}

# Generate comprehensive health report
generate_comprehensive_report() {
    local exit_code="$1"
    
    echo
    echo "=========================================="
    echo "EternalJukebox Comprehensive Health Report"
    echo "=========================================="
    echo "Timestamp: $(date)"
    echo "Main Service: $MAIN_SERVICE_URL"
    echo "Analysis Service: $ANALYSIS_SERVICE_URL"
    echo "Prometheus: $PROMETHEUS_URL"
    echo "Grafana: $GRAFANA_URL"
    echo "Exit Code: $exit_code"
    echo
    
    # Service Status Summary
    echo "=== SERVICE STATUS ==="
    for key in "${!HEALTH_RESULTS[@]}"; do
        if [[ "$key" == *"_success" ]]; then
            local service_name=$(echo "$key" | sed 's/_success//')
            local success="${HEALTH_RESULTS[$key]}"
            local status="❌ FAILED"
            if [ "$success" = "true" ]; then
                status="✅ HEALTHY"
            fi
            echo "$service_name: $status"
        fi
    done
    echo
    
    # Container Status Summary
    if [ "${HEALTH_RESULTS[docker_available]}" = "true" ]; then
        echo "=== CONTAINER STATUS ==="
        echo "Total Containers: ${HEALTH_RESULTS[containers_found]}"
        echo "Healthy: ${HEALTH_RESULTS[containers_healthy]}"
        echo "Unhealthy: ${HEALTH_RESULTS[containers_unhealthy]}"
        echo "Starting: ${HEALTH_RESULTS[containers_starting]}"
        echo "Unknown: ${HEALTH_RESULTS[containers_unknown]}"
        echo
    fi
    
    # System Resources Summary
    echo "=== SYSTEM RESOURCES ==="
    if [ -n "${HEALTH_RESULTS[cpu_usage]}" ]; then
        echo "CPU Usage: ${HEALTH_RESULTS[cpu_usage]}%"
    fi
    if [ -n "${HEALTH_RESULTS[memory_usage_percent]}" ]; then
        echo "Memory Usage: ${HEALTH_RESULTS[memory_usage_percent]}%"
    fi
    if [ -n "${HEALTH_RESULTS[disk_usage_percent]}" ]; then
        echo "Disk Usage: ${HEALTH_RESULTS[disk_usage_percent]}%"
    fi
    if [ -n "${HEALTH_RESULTS[load_1min]}" ]; then
        echo "Load Average (1m): ${HEALTH_RESULTS[load_1min]}"
    fi
    echo
    
    # Monitoring Stack Status
    echo "=== MONITORING STACK ==="
    if [ -n "${HEALTH_RESULTS[prometheus_healthy]}" ]; then
        local prom_status="❌ DOWN"
        if [ "${HEALTH_RESULTS[prometheus_healthy]}" = "true" ]; then
            prom_status="✅ UP"
        fi
        echo "Prometheus: $prom_status"
    fi
    if [ -n "${HEALTH_RESULTS[grafana_healthy]}" ]; then
        local grafana_status="❌ DOWN"
        if [ "${HEALTH_RESULTS[grafana_healthy]}" = "true" ]; then
            grafana_status="✅ UP"
        fi
        echo "Grafana: $grafana_status"
    fi
    echo
    
    # Overall Status
    if [ "$exit_code" -eq 0 ]; then
        echo "Overall Status: ✅ ALL SYSTEMS HEALTHY"
        log_success "All health checks passed!"
    else
        echo "Overall Status: ❌ ISSUES DETECTED"
        log_error "One or more health checks failed!"
    fi
    
    echo "=========================================="
}

# Main health check function
main() {
    log_info "Starting EternalJukebox comprehensive health checks..."
    echo "Configuration:"
    echo "  Main Service URL: $MAIN_SERVICE_URL"
    echo "  Analysis Service URL: $ANALYSIS_SERVICE_URL"
    echo "  Prometheus URL: $PROMETHEUS_URL"
    echo "  Grafana URL: $GRAFANA_URL"
    echo "  Metrics Enabled: $METRICS_ENABLED"
    echo "  Log Level: $LOG_LEVEL"
    echo
    
    local exit_code=0
    
    # Run all health checks
    check_service_detailed "Main Service" "$MAIN_SERVICE_URL" "/healthy" "200" || exit_code=1
    check_service_detailed "Analysis Service" "$ANALYSIS_SERVICE_URL" "/health" "200" || exit_code=1
    check_docker_containers_detailed || exit_code=1
    check_system_resources || exit_code=1
    check_monitoring_stack || exit_code=1
    
    # Send metrics to Prometheus
    send_metrics_to_prometheus
    
    # Generate comprehensive report
    generate_comprehensive_report $exit_code
    
    exit $exit_code
}

# Handle script arguments
case "${1:-}" in
    --help|-h)
        echo "EternalJukebox Enhanced Health Monitoring Script"
        echo
        echo "Usage: $0 [options]"
        echo
        echo "Options:"
        echo "  --help, -h          Show this help message"
        echo "  --main-url URL      Main service URL (default: http://localhost:8080)"
        echo "  --analysis-url URL  Analysis service URL (default: http://localhost:5000)"
        echo "  --prometheus-url URL Prometheus URL (default: http://localhost:9090)"
        echo "  --grafana-url URL   Grafana URL (default: http://localhost:3000)"
        echo "  --timeout SECONDS   Request timeout (default: 30)"
        echo "  --retries COUNT     Number of retries (default: 3)"
        echo "  --retry-delay SEC   Delay between retries (default: 10)"
        echo "  --log-level LEVEL   Log level: DEBUG, INFO, WARNING, ERROR (default: INFO)"
        echo "  --metrics-enabled   Enable metrics collection (default: true)"
        echo
        echo "Environment Variables:"
        echo "  MAIN_SERVICE_URL    Main service URL"
        echo "  ANALYSIS_SERVICE_URL Analysis service URL"
        echo "  PROMETHEUS_URL      Prometheus URL"
        echo "  GRAFANA_URL         Grafana URL"
        echo "  TIMEOUT            Request timeout in seconds"
        echo "  RETRY_COUNT        Number of retry attempts"
        echo "  RETRY_DELAY        Delay between retries in seconds"
        echo "  LOG_LEVEL          Log level"
        echo "  METRICS_ENABLED    Enable metrics collection"
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
    --prometheus-url)
        PROMETHEUS_URL="$2"
        shift 2
        ;;
    --grafana-url)
        GRAFANA_URL="$2"
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
    --log-level)
        LOG_LEVEL="$2"
        shift 2
        ;;
    --metrics-enabled)
        METRICS_ENABLED="$2"
        shift 2
        ;;
esac

# Run main health check
main "$@"
