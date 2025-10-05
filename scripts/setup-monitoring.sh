#!/bin/bash

# EternalJukebox Monitoring Setup Script
# This script helps set up the complete monitoring stack

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MONITORING_DIR="$PROJECT_ROOT/monitoring"
DEPLOYMENT_DIR="$PROJECT_ROOT/deployment"
SCRIPTS_DIR="$PROJECT_ROOT/scripts"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
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

log_step() {
    echo -e "${PURPLE}[STEP]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites..."
    
    local missing_deps=()
    
    # Check Docker
    if ! command -v docker &> /dev/null; then
        missing_deps+=("docker")
    fi
    
    # Check Docker Compose
    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        missing_deps+=("docker-compose")
    fi
    
    # Check curl
    if ! command -v curl &> /dev/null; then
        missing_deps+=("curl")
    fi
    
    # Check jq (optional)
    if ! command -v jq &> /dev/null; then
        log_warning "jq not found - JSON processing will be limited"
    fi
    
    if [ ${#missing_deps[@]} -gt 0 ]; then
        log_error "Missing required dependencies: ${missing_deps[*]}"
        log_error "Please install the missing dependencies and try again"
        exit 1
    fi
    
    log_success "All prerequisites satisfied"
}

# Create monitoring directory structure
create_monitoring_structure() {
    log_step "Creating monitoring directory structure..."
    
    local dirs=(
        "$MONITORING_DIR"
        "$MONITORING_DIR/rules"
        "$MONITORING_DIR/grafana"
        "$MONITORING_DIR/grafana/dashboards"
        "$MONITORING_DIR/grafana/provisioning"
        "$MONITORING_DIR/grafana/provisioning/datasources"
        "$MONITORING_DIR/grafana/provisioning/dashboards"
        "$SCRIPTS_DIR"
    )
    
    for dir in "${dirs[@]}"; do
        if [ ! -d "$dir" ]; then
            mkdir -p "$dir"
            log_info "Created directory: $dir"
        else
            log_info "Directory already exists: $dir"
        fi
    done
    
    log_success "Monitoring directory structure created"
}

# Verify monitoring configuration files
verify_configuration_files() {
    log_step "Verifying monitoring configuration files..."
    
    local required_files=(
        "$MONITORING_DIR/prometheus.yml"
        "$MONITORING_DIR/alertmanager.yml"
        "$MONITORING_DIR/rules/eternaljukebox.yml"
        "$MONITORING_DIR/blackbox.yml"
        "$MONITORING_DIR/grafana/dashboards/eternaljukebox-overview.json"
        "$MONITORING_DIR/grafana/dashboards/eternaljukebox-system.json"
        "$MONITORING_DIR/grafana/provisioning/datasources/datasources.yml"
        "$MONITORING_DIR/grafana/provisioning/dashboards/dashboards.yml"
        "$DEPLOYMENT_DIR/docker-compose.monitoring.yml"
        "$SCRIPTS_DIR/health-monitor.sh"
    )
    
    local missing_files=()
    
    for file in "${required_files[@]}"; do
        if [ ! -f "$file" ]; then
            missing_files+=("$file")
        else
            log_info "Found: $file"
        fi
    done
    
    if [ ${#missing_files[@]} -gt 0 ]; then
        log_error "Missing required configuration files:"
        for file in "${missing_files[@]}"; do
            log_error "  - $file"
        done
        exit 1
    fi
    
    log_success "All configuration files present"
}

# Create environment file template
create_env_template() {
    log_step "Creating environment file template..."
    
    local env_file="$DEPLOYMENT_DIR/.env.monitoring"
    
    if [ ! -f "$env_file" ]; then
        cat > "$env_file" << 'EOF'
# EternalJukebox Monitoring Environment Configuration
# Copy this file to .env and customize the values

# Domain Configuration
DOMAIN_NAME=localhost
ACME_EMAIL=admin@localhost

# Registry Configuration (for production deployments)
REGISTRY_NAME=eternaljukebox
IMAGE_TAG=latest

# Service Configuration
YOUTUBE_API_KEY=your_youtube_api_key_here
ANALYSIS_WORKERS=2

# Monitoring Configuration
GRAFANA_PASSWORD=admin

# Optional: External monitoring services
# PROMETHEUS_REMOTE_WRITE_URL=
# PROMETHEUS_REMOTE_WRITE_USERNAME=
# PROMETHEUS_REMOTE_WRITE_PASSWORD=

# Optional: Alerting configuration
# SMTP_SMARTHOST=localhost:587
# SMTP_FROM=alerts@eternaljukebox.local
# SMTP_AUTH_USERNAME=alerts@eternaljukebox.local
# SMTP_AUTH_PASSWORD=password
EOF
        log_success "Created environment template: $env_file"
    else
        log_info "Environment template already exists: $env_file"
    fi
}

# Test Docker Compose configuration
test_docker_compose() {
    log_step "Testing Docker Compose configuration..."
    
    local compose_file="$DEPLOYMENT_DIR/docker-compose.monitoring.yml"
    
    if ! docker-compose -f "$compose_file" config > /dev/null 2>&1; then
        log_error "Docker Compose configuration is invalid"
        log_error "Please check the configuration file: $compose_file"
        exit 1
    fi
    
    log_success "Docker Compose configuration is valid"
}

# Create monitoring startup script
create_startup_script() {
    log_step "Creating monitoring startup script..."
    
    local startup_script="$DEPLOYMENT_DIR/start-monitoring.sh"
    
    cat > "$startup_script" << 'EOF'
#!/bin/bash

# EternalJukebox Monitoring Startup Script
# This script starts the complete monitoring stack

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.monitoring.yml"
ENV_FILE="$SCRIPT_DIR/.env.monitoring"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# Check if environment file exists
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    log_error "Please copy .env.monitoring to .env and customize the values"
    exit 1
fi

# Load environment variables
export $(grep -v '^#' "$ENV_FILE" | xargs)

log_info "Starting EternalJukebox monitoring stack..."
log_info "Domain: $DOMAIN_NAME"
log_info "Registry: $REGISTRY_NAME"
log_info "Image Tag: $IMAGE_TAG"

# Start services
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

log_success "Monitoring stack started successfully!"
echo
echo "Access URLs:"
echo "  Main Application: http://$DOMAIN_NAME"
echo "  Grafana:          http://grafana.$DOMAIN_NAME (admin/$GRAFANA_PASSWORD)"
echo "  Prometheus:       http://prometheus.$DOMAIN_NAME"
echo "  Alertmanager:     http://alertmanager.$DOMAIN_NAME"
echo
echo "To view logs: docker-compose -f $COMPOSE_FILE logs -f"
echo "To stop:      docker-compose -f $COMPOSE_FILE down"
EOF
    
    chmod +x "$startup_script"
    log_success "Created startup script: $startup_script"
}

# Create monitoring stop script
create_stop_script() {
    log_step "Creating monitoring stop script..."
    
    local stop_script="$DEPLOYMENT_DIR/stop-monitoring.sh"
    
    cat > "$stop_script" << 'EOF'
#!/bin/bash

# EternalJukebox Monitoring Stop Script
# This script stops the complete monitoring stack

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.monitoring.yml"
ENV_FILE="$SCRIPT_DIR/.env.monitoring"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# Check if environment file exists
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    log_error "Please copy .env.monitoring to .env and customize the values"
    exit 1
fi

# Load environment variables
export $(grep -v '^#' "$ENV_FILE" | xargs)

log_info "Stopping EternalJukebox monitoring stack..."

# Stop services
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down

log_success "Monitoring stack stopped successfully!"
EOF
    
    chmod +x "$stop_script"
    log_success "Created stop script: $stop_script"
}

# Create monitoring status script
create_status_script() {
    log_step "Creating monitoring status script..."
    
    local status_script="$DEPLOYMENT_DIR/status-monitoring.sh"
    
    cat > "$status_script" << 'EOF'
#!/bin/bash

# EternalJukebox Monitoring Status Script
# This script shows the status of the monitoring stack

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.monitoring.yml"
ENV_FILE="$SCRIPT_DIR/.env.monitoring"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# Check if environment file exists
if [ ! -f "$ENV_FILE" ]; then
    log_error "Environment file not found: $ENV_FILE"
    log_error "Please copy .env.monitoring to .env and customize the values"
    exit 1
fi

# Load environment variables
export $(grep -v '^#' "$ENV_FILE" | xargs)

log_info "EternalJukebox Monitoring Stack Status"
echo "=========================================="

# Show container status
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps

echo
echo "Service URLs:"
echo "  Main Application: http://$DOMAIN_NAME"
echo "  Grafana:          http://grafana.$DOMAIN_NAME"
echo "  Prometheus:       http://prometheus.$DOMAIN_NAME"
echo "  Alertmanager:     http://alertmanager.$DOMAIN_NAME"
echo

# Check service health
log_info "Checking service health..."

services=("eternaljukebox-main" "eternaljukebox-analysis" "prometheus" "grafana" "alertmanager")
for service in "${services[@]}"; do
    if docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps "$service" | grep -q "Up"; then
        log_success "$service is running"
    else
        log_error "$service is not running"
    fi
done
EOF
    
    chmod +x "$status_script"
    log_success "Created status script: $status_script"
}

# Generate monitoring documentation
generate_documentation() {
    log_step "Generating monitoring documentation..."
    
    local doc_file="$MONITORING_DIR/README.md"
    
    cat > "$doc_file" << 'EOF'
# EternalJukebox Monitoring Stack

This directory contains the complete monitoring infrastructure for EternalJukebox, including Prometheus, Grafana, Alertmanager, and comprehensive health checks.

## Architecture

The monitoring stack consists of:

- **Prometheus**: Metrics collection and storage
- **Grafana**: Visualization and dashboards
- **Alertmanager**: Alert routing and notification
- **Node Exporter**: System metrics collection
- **cAdvisor**: Container metrics collection
- **Blackbox Exporter**: External health checks
- **Health Monitor**: Custom health check script

## Quick Start

1. **Setup Environment**:
   ```bash
   cd deployment
   cp .env.monitoring .env
   # Edit .env with your configuration
   ```

2. **Start Monitoring Stack**:
   ```bash
   ./start-monitoring.sh
   ```

3. **Access Services**:
   - Main Application: http://your-domain
   - Grafana: http://grafana.your-domain (admin/admin)
   - Prometheus: http://prometheus.your-domain
   - Alertmanager: http://alertmanager.your-domain

## Configuration Files

### Prometheus (`prometheus.yml`)
- Scrape configuration for all services
- Alerting rules for service health
- Retention and storage settings

### Alertmanager (`alertmanager.yml`)
- Alert routing rules
- Notification channels (email, webhook)
- Inhibition rules to reduce noise

### Grafana Dashboards
- **EternalJukebox Overview**: Main application metrics
- **System Metrics**: CPU, memory, disk, network
- **Container Metrics**: Docker container health

### Health Monitoring (`health-monitor.sh`)
- Comprehensive health checks
- Integration with Prometheus metrics
- Detailed reporting and logging

## Monitoring Features

### Service Health Monitoring
- HTTP endpoint health checks
- Container health status
- Service dependency monitoring
- Response time tracking

### System Resource Monitoring
- CPU usage and load average
- Memory utilization
- Disk space and I/O
- Network traffic and errors

### Application Metrics
- Request rates and response times
- Error rates and status codes
- Analysis queue monitoring
- Processing time tracking

### Alerting
- Service down alerts
- High resource usage alerts
- Performance degradation alerts
- Custom application alerts

## Dashboards

### EternalJukebox Overview
- Service status and availability
- Request rates and response times
- Error rates and performance metrics
- Analysis queue and processing metrics

### System Metrics
- CPU, memory, and disk usage
- Network traffic and load average
- System activity and context switches

## Health Checks

The monitoring stack includes comprehensive health checks:

1. **Service Health**: HTTP endpoint monitoring
2. **Container Health**: Docker container status
3. **System Resources**: CPU, memory, disk monitoring
4. **Network Connectivity**: External service reachability
5. **Monitoring Stack**: Prometheus, Grafana, Alertmanager health

## Alerting Rules

### Critical Alerts
- Service down (immediate notification)
- High error rates
- Resource exhaustion
- Database connection issues

### Warning Alerts
- High response times
- Resource usage warnings
- Container restarting frequently
- Network issues

## Customization

### Adding New Metrics
1. Add metric collection to your service
2. Update Prometheus scrape configuration
3. Create Grafana dashboard panels
4. Add alerting rules if needed

### Custom Dashboards
1. Create dashboard JSON in `grafana/dashboards/`
2. Update dashboard provisioning configuration
3. Restart Grafana to load new dashboard

### Custom Alerts
1. Add rules to `rules/eternaljukebox.yml`
2. Update Alertmanager configuration
3. Test alerts with Prometheus

## Troubleshooting

### Common Issues

1. **Services not starting**:
   - Check Docker Compose logs: `docker-compose logs -f`
   - Verify environment configuration
   - Check port conflicts

2. **Metrics not appearing**:
   - Verify Prometheus targets are up
   - Check scrape configuration
   - Verify service metrics endpoints

3. **Alerts not firing**:
   - Check Prometheus rules evaluation
   - Verify Alertmanager configuration
   - Test alert rules manually

4. **Grafana dashboards empty**:
   - Verify Prometheus datasource
   - Check dashboard queries
   - Verify time range selection

### Logs and Debugging

- **Prometheus logs**: `docker-compose logs prometheus`
- **Grafana logs**: `docker-compose logs grafana`
- **Alertmanager logs**: `docker-compose logs alertmanager`
- **Health monitor logs**: `docker-compose logs health-monitor`

## Maintenance

### Data Retention
- Prometheus: 30 days (configurable)
- Grafana: Persistent dashboards and configuration
- Alertmanager: Alert history and silences

### Backup
- Prometheus data: `prometheus-data` volume
- Grafana data: `grafana-data` volume
- Configuration files: Version controlled

### Updates
- Update Docker images regularly
- Review and update alerting rules
- Monitor dashboard performance
- Update health check scripts

## Security Considerations

- Change default Grafana password
- Configure proper authentication
- Use HTTPS in production
- Restrict network access
- Regular security updates

## Performance Tuning

### Prometheus
- Adjust scrape intervals
- Configure retention policies
- Optimize query performance
- Monitor memory usage

### Grafana
- Limit dashboard refresh rates
- Optimize query performance
- Configure caching
- Monitor resource usage

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review logs and metrics
3. Consult Prometheus/Grafana documentation
4. Create an issue in the project repository
EOF
    
    log_success "Generated monitoring documentation: $doc_file"
}

# Main setup function
main() {
    log_info "EternalJukebox Monitoring Setup"
    echo "=================================="
    echo
    
    check_prerequisites
    create_monitoring_structure
    verify_configuration_files
    create_env_template
    test_docker_compose
    create_startup_script
    create_stop_script
    create_status_script
    generate_documentation
    
    echo
    log_success "Monitoring setup completed successfully!"
    echo
    echo "Next steps:"
    echo "1. Copy deployment/.env.monitoring to deployment/.env"
    echo "2. Edit deployment/.env with your configuration"
    echo "3. Run deployment/start-monitoring.sh to start the stack"
    echo "4. Access Grafana at http://grafana.your-domain (admin/admin)"
    echo
    echo "For more information, see monitoring/README.md"
}

# Handle script arguments
case "${1:-}" in
    --help|-h)
        echo "EternalJukebox Monitoring Setup Script"
        echo
        echo "Usage: $0 [options]"
        echo
        echo "Options:"
        echo "  --help, -h          Show this help message"
        echo
        echo "This script sets up the complete monitoring infrastructure"
        echo "including Prometheus, Grafana, Alertmanager, and health checks."
        echo
        exit 0
        ;;
esac

# Run main setup
main "$@"
