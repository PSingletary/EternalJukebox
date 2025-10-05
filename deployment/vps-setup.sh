#!/bin/bash

# EternalJukebox VPS Setup Script
# This script sets up the VPS environment for EternalJukebox deployment

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROVIDER="${1:-generic}"
ENVIRONMENT="${2:-staging}"
DEPLOY_PATH="/opt/eternaljukebox"
BACKUP_PATH="/opt/backups"

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

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --provider)
            PROVIDER="$2"
            shift 2
            ;;
        --environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        --deploy-path)
            DEPLOY_PATH="$2"
            shift 2
            ;;
        --backup-path)
            BACKUP_PATH="$2"
            shift 2
            ;;
        --help|-h)
            echo "EternalJukebox VPS Setup Script"
            echo
            echo "Usage: $0 [options]"
            echo
            echo "Options:"
            echo "  --provider PROVIDER     VPS provider (generic, digitalocean, linode, vultr, hetzner)"
            echo "  --environment ENV       Environment (staging, production)"
            echo "  --deploy-path PATH      Deployment path (default: /opt/eternaljukebox)"
            echo "  --backup-path PATH      Backup path (default: /opt/backups)"
            echo "  --help, -h              Show this help message"
            echo
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

log_info "Starting EternalJukebox VPS setup..."
log_info "Provider: $PROVIDER"
log_info "Environment: $ENVIRONMENT"
log_info "Deploy Path: $DEPLOY_PATH"
log_info "Backup Path: $BACKUP_PATH"

# Detect OS
detect_os() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$NAME
        VER=$VERSION_ID
    elif type lsb_release >/dev/null 2>&1; then
        OS=$(lsb_release -si)
        VER=$(lsb_release -sr)
    else
        OS=$(uname -s)
        VER=$(uname -r)
    fi
    
    log_info "Detected OS: $OS $VER"
}

# Install Docker
install_docker() {
    log_info "Installing Docker..."
    
    if command -v docker >/dev/null 2>&1; then
        log_success "Docker is already installed"
        return 0
    fi
    
    # Update package index
    sudo apt-get update
    
    # Install required packages
    sudo apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        gnupg \
        lsb-release
    
    # Add Docker's official GPG key
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    
    # Set up stable repository
    echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
        $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    
    # Install Docker Engine
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    
    # Add current user to docker group
    sudo usermod -aG docker $USER
    
    # Enable and start Docker
    sudo systemctl enable docker
    sudo systemctl start docker
    
    log_success "Docker installed successfully"
}

# Install Docker Compose
install_docker_compose() {
    log_info "Installing Docker Compose..."
    
    if command -v docker-compose >/dev/null 2>&1; then
        log_success "Docker Compose is already installed"
        return 0
    fi
    
    # Install Docker Compose
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    
    log_success "Docker Compose installed successfully"
}

# Install additional tools
install_tools() {
    log_info "Installing additional tools..."
    
    # Install curl, jq, and other utilities
    sudo apt-get update
    sudo apt-get install -y \
        curl \
        jq \
        htop \
        unzip \
        wget \
        git \
        nano \
        vim \
        ufw \
        fail2ban
    
    log_success "Additional tools installed successfully"
}

# Configure firewall
configure_firewall() {
    log_info "Configuring firewall..."
    
    # Enable UFW if not already enabled
    sudo ufw --force enable
    
    # Default policies
    sudo ufw default deny incoming
    sudo ufw default allow outgoing
    
    # Allow SSH
    sudo ufw allow ssh
    
    # Allow HTTP and HTTPS
    sudo ufw allow 80/tcp
    sudo ufw allow 443/tcp
    
    # Allow application ports
    sudo ufw allow 8080/tcp
    sudo ufw allow 5000/tcp
    
    # Allow monitoring ports (optional)
    if [ "$ENVIRONMENT" = "production" ]; then
        sudo ufw allow 9090/tcp  # Prometheus
        sudo ufw allow 3000/tcp  # Grafana
    fi
    
    log_success "Firewall configured successfully"
}

# Configure fail2ban
configure_fail2ban() {
    log_info "Configuring fail2ban..."
    
    # Create jail.local configuration
    sudo tee /etc/fail2ban/jail.local > /dev/null << EOF
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 3

[sshd]
enabled = true
port = ssh
filter = sshd
logpath = /var/log/auth.log
maxretry = 3

[nginx-http-auth]
enabled = false

[nginx-limit-req]
enabled = false

[nginx-botsearch]
enabled = false
EOF
    
    # Restart fail2ban
    sudo systemctl restart fail2ban
    sudo systemctl enable fail2ban
    
    log_success "Fail2ban configured successfully"
}

# Create directories
create_directories() {
    log_info "Creating directories..."
    
    # Create deployment directory
    sudo mkdir -p "$DEPLOY_PATH"
    sudo chown $USER:$USER "$DEPLOY_PATH"
    
    # Create backup directory
    sudo mkdir -p "$BACKUP_PATH"
    sudo chown $USER:$USER "$BACKUP_PATH"
    
    # Create data directories
    mkdir -p "$DEPLOY_PATH/data"/{analysis,audio,external_audio,uploaded_audio,uploaded_analysis,profile,log}
    mkdir -p "$DEPLOY_PATH/database"
    
    # Set proper permissions
    chmod 755 "$DEPLOY_PATH"
    chmod 755 "$BACKUP_PATH"
    chmod -R 755 "$DEPLOY_PATH/data"
    
    log_success "Directories created successfully"
}

# Configure system limits
configure_limits() {
    log_info "Configuring system limits..."
    
    # Create limits configuration
    sudo tee /etc/security/limits.d/eternaljukebox.conf > /dev/null << EOF
* soft nofile 65536
* hard nofile 65536
* soft nproc 32768
* hard nproc 32768
EOF
    
    # Configure sysctl for better performance
    sudo tee -a /etc/sysctl.conf > /dev/null << EOF

# EternalJukebox optimizations
vm.max_map_count=262144
net.core.somaxconn=65535
net.core.netdev_max_backlog=5000
EOF
    
    # Apply sysctl changes
    sudo sysctl -p
    
    log_success "System limits configured successfully"
}

# Install monitoring tools (production only)
install_monitoring() {
    if [ "$ENVIRONMENT" != "production" ]; then
        log_info "Skipping monitoring tools for non-production environment"
        return 0
    fi
    
    log_info "Installing monitoring tools..."
    
    # Install Prometheus and Grafana (optional)
    # This would typically be done via Docker Compose in production
    log_success "Monitoring tools configuration completed"
}

# Provider-specific configurations
configure_provider() {
    log_info "Applying provider-specific configurations for $PROVIDER..."
    
    case $PROVIDER in
        digitalocean)
            # DigitalOcean specific optimizations
            log_info "Applying DigitalOcean optimizations..."
            
            # Configure swap if needed
            if [ ! -f /swapfile ]; then
                sudo fallocate -l 2G /swapfile
                sudo chmod 600 /swapfile
                sudo mkswap /swapfile
                sudo swapon /swapfile
                echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
            fi
            ;;
            
        linode)
            # Linode specific optimizations
            log_info "Applying Linode optimizations..."
            
            # Linode monitoring agent (optional)
            # wget -O - https://apt.linode.com/linode.gpg | sudo apt-key add -
            # echo "deb https://apt.linode.com/ stable main" | sudo tee /etc/apt/sources.list.d/linode.list
            # sudo apt-get update && sudo apt-get install -y linode-cli
            ;;
            
        vultr)
            # Vultr specific optimizations
            log_info "Applying Vultr optimizations..."
            ;;
            
        hetzner)
            # Hetzner specific optimizations
            log_info "Applying Hetzner optimizations..."
            ;;
            
        generic|*)
            log_info "Using generic VPS configuration..."
            ;;
    esac
    
    log_success "Provider-specific configuration completed"
}

# Setup SSL certificates (Let's Encrypt)
setup_ssl() {
    if [ "$ENVIRONMENT" != "production" ]; then
        log_info "Skipping SSL setup for non-production environment"
        return 0
    fi
    
    log_info "Setting up SSL certificates..."
    
    # Install certbot
    sudo apt-get install -y certbot python3-certbot-nginx
    
    log_success "SSL tools installed (configure with your domain)"
}

# Create systemd service (optional)
create_systemd_service() {
    log_info "Creating systemd service..."
    
    sudo tee /etc/systemd/system/eternaljukebox.service > /dev/null << EOF
[Unit]
Description=EternalJukebox Application
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$DEPLOY_PATH
ExecStart=/usr/local/bin/docker-compose up -d
ExecStop=/usr/local/bin/docker-compose down
TimeoutStartSec=0
User=$USER
Group=$USER

[Install]
WantedBy=multi-user.target
EOF
    
    # Reload systemd and enable service
    sudo systemctl daemon-reload
    sudo systemctl enable eternaljukebox
    
    log_success "Systemd service created successfully"
}

# Main setup function
main() {
    log_info "Starting VPS setup for EternalJukebox..."
    
    # Check if running as root
    if [ "$EUID" -eq 0 ]; then
        log_error "This script should not be run as root"
        exit 1
    fi
    
    # Detect OS
    detect_os
    
    # Install required software
    install_docker
    install_docker_compose
    install_tools
    
    # Configure system
    configure_firewall
    configure_fail2ban
    configure_limits
    configure_provider
    
    # Create directories
    create_directories
    
    # Setup monitoring and SSL for production
    install_monitoring
    setup_ssl
    
    # Create systemd service
    create_systemd_service
    
    log_success "VPS setup completed successfully!"
    echo
    echo "=== Setup Summary ==="
    echo "Provider: $PROVIDER"
    echo "Environment: $ENVIRONMENT"
    echo "Deploy Path: $DEPLOY_PATH"
    echo "Backup Path: $BACKUP_PATH"
    echo "Docker: $(docker --version)"
    echo "Docker Compose: $(docker-compose --version)"
    echo
    echo "=== Next Steps ==="
    echo "1. Configure your domain name (if using production)"
    echo "2. Set up SSL certificates with certbot"
    echo "3. Deploy the application using the GitHub Actions workflow"
    echo "4. Monitor the deployment with: docker-compose -f $DEPLOY_PATH/docker-compose.yml logs -f"
    echo
    echo "=== Useful Commands ==="
    echo "Check service status: systemctl status eternaljukebox"
    echo "View logs: docker-compose -f $DEPLOY_PATH/docker-compose.yml logs -f"
    echo "Restart services: sudo systemctl restart eternaljukebox"
    echo "Update firewall: sudo ufw status"
    echo "Check fail2ban: sudo fail2ban-client status"
}

# Run main function
main "$@"
