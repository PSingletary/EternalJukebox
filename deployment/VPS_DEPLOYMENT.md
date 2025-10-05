# VPS Deployment Guide

This guide covers deploying EternalJukebox to any VPS provider using GitHub Actions and Docker Compose.

## Supported VPS Providers

- **Generic VPS** - Any Linux VPS with Docker support
- **DigitalOcean** - Optimized Droplet configuration
- **Linode** - Linode instance optimization
- **Vultr** - Vultr Cloud Compute optimization
- **Hetzner** - Hetzner Cloud optimization

## Prerequisites

### VPS Requirements

**Minimum Specifications:**
- CPU: 2 vCPUs
- RAM: 4GB
- Storage: 20GB SSD
- OS: Ubuntu 20.04 LTS or newer

**Recommended Specifications:**
- CPU: 4 vCPUs
- RAM: 8GB
- Storage: 50GB SSD
- Bandwidth: 1TB/month

### Required GitHub Secrets

Configure the following secrets in your GitHub repository:

```
VPS_HOST=your-vps-ip-or-domain
VPS_USER=your-username
VPS_PORT=22
VPS_SSH_KEY=your-private-ssh-key
DEPLOY_PATH=/opt/eternaljukebox
BACKUP_PATH=/opt/backups
YOUTUBE_API_KEY=your-youtube-api-key
DOMAIN_NAME=your-domain.com
ACME_EMAIL=your-email@domain.com
GRAFANA_PASSWORD=your-grafana-password
ANALYSIS_WORKERS=2
DOCKER_REGISTRY=your-registry (optional)
```

### SSH Key Setup

1. Generate SSH key pair:
```bash
ssh-keygen -t ed25519 -C "github-actions@your-domain.com"
```

2. Copy public key to VPS:
```bash
ssh-copy-id -i ~/.ssh/id_ed25519.pub user@your-vps-ip
```

3. Add private key to GitHub secrets as `VPS_SSH_KEY`

## Deployment Options

### 1. Automated Deployment via GitHub Actions

**Trigger deployment:**
- Push to `main` or `llm` branches
- Manual trigger via GitHub Actions UI

**Manual deployment:**
1. Go to Actions tab in GitHub
2. Select "Deploy to VPS" workflow
3. Click "Run workflow"
4. Choose environment (staging/production)
5. Select VPS provider
6. Choose deployment strategy

### 2. Manual Deployment

**Initial setup:**
```bash
# Clone repository
git clone https://github.com/your-username/EternalJukebox.git
cd EternalJukebox

# Run setup script
chmod +x deployment/vps-setup.sh
./deployment/vps-setup.sh --provider=digitalocean --environment=production
```

**Deploy application:**
```bash
# Set environment variables
export YOUTUBE_API_KEY=your-api-key
export DOMAIN_NAME=your-domain.com
export ACME_EMAIL=your-email@domain.com

# Deploy with Docker Compose
docker-compose -f docker-compose.yml -f deployment/docker-compose.vps.yml up -d
```

## VPS Setup Script

The `vps-setup.sh` script automatically configures your VPS:

### Features
- **Docker Installation**: Latest Docker and Docker Compose
- **Security Configuration**: UFW firewall and fail2ban
- **System Optimization**: File limits and kernel parameters
- **Monitoring Tools**: Optional Prometheus and Grafana
- **SSL Setup**: Let's Encrypt integration
- **Provider Optimization**: Specific configurations for each provider

### Usage
```bash
# Generic VPS
./deployment/vps-setup.sh --provider=generic --environment=production

# DigitalOcean Droplet
./deployment/vps-setup.sh --provider=digitalocean --environment=production

# Custom paths
./deployment/vps-setup.sh \
  --provider=linode \
  --environment=staging \
  --deploy-path=/opt/eternaljukebox \
  --backup-path=/opt/backups
```

## Architecture

### Services
- **Main Service**: Kotlin application (port 8080)
- **Analysis Service**: Python Flask API (port 5000)
- **Redis**: Caching layer (port 6379)
- **Traefik**: Reverse proxy with SSL (ports 80, 443)
- **Prometheus**: Metrics collection (port 9090, production only)
- **Grafana**: Metrics visualization (port 3000, production only)
- **Loki**: Log aggregation (port 3100, production only)
- **Promtail**: Log collection (production only)

### Networking
- Internal Docker network for service communication
- Traefik handles SSL termination and routing
- External access via domain name or IP
- Health checks for all services

### Storage
- Local volumes for persistent data
- Backup system with rotation
- Log aggregation and retention

## Configuration

### Environment Variables

**Required:**
- `YOUTUBE_API_KEY`: YouTube Data API key
- `DOMAIN_NAME`: Your domain name (optional)
- `ACME_EMAIL`: Email for Let's Encrypt certificates

**Optional:**
- `ANALYSIS_WORKERS`: Number of analysis workers (default: 2)
- `GRAFANA_PASSWORD`: Grafana admin password (default: admin)
- `ENVIRONMENT`: Environment (staging/production)

### Docker Compose Profiles

**Default services:**
```bash
docker-compose -f docker-compose.yml -f deployment/docker-compose.vps.yml up -d
```

**With monitoring:**
```bash
docker-compose -f docker-compose.yml -f deployment/docker-compose.vps.yml --profile monitoring up -d
```

**With static file serving:**
```bash
docker-compose -f docker-compose.yml -f deployment/docker-compose.vps.yml --profile static up -d
```

**With backup:**
```bash
docker-compose -f docker-compose.yml -f deployment/docker-compose.vps.yml --profile backup up -d
```

## Security

### Firewall Configuration
- UFW enabled with restrictive rules
- Only necessary ports open (22, 80, 443, 8080, 5000)
- SSH access restricted by fail2ban

### SSL/TLS
- Automatic Let's Encrypt certificates via Traefik
- HTTP to HTTPS redirect
- Modern TLS configuration

### Container Security
- Non-root user execution
- Resource limits and quotas
- Regular image updates

## Monitoring

### Health Checks
- Built-in health endpoints for all services
- Traefik health monitoring
- Docker health checks

### Metrics Collection
- Prometheus scraping all services
- Custom application metrics
- System metrics via node exporter

### Log Management
- Centralized logging with Loki
- Log rotation and retention
- Structured logging format

### Dashboards
- Grafana dashboards for visualization
- Pre-configured EternalJukebox dashboard
- System monitoring dashboards

## Scaling

### Horizontal Scaling
- Multiple instances behind load balancer
- Redis for session sharing
- Shared storage for data consistency

### Vertical Scaling
- Increase VPS resources
- Adjust Docker resource limits
- Optimize application settings

### Auto-scaling
- Implement with external load balancer
- Use VPS provider auto-scaling features
- Monitor and scale based on metrics

## Backup and Recovery

### Automated Backups
```bash
# Enable backup profile
docker-compose -f docker-compose.yml -f deployment/docker-compose.vps.yml --profile backup up -d

# Manual backup
docker-compose exec backup /scripts/backup.sh
```

### Backup Contents
- Application data
- Database files
- Configuration files
- SSL certificates

### Recovery Process
1. Stop services
2. Restore from backup
3. Update configuration
4. Start services
5. Verify functionality

## Troubleshooting

### Common Issues

**Service not starting:**
```bash
# Check logs
docker-compose logs -f main
docker-compose logs -f analysis-service

# Check health
docker-compose ps
curl -f http://localhost:8080/healthy
```

**SSL certificate issues:**
```bash
# Check Traefik logs
docker-compose logs -f traefik

# Verify domain configuration
nslookup your-domain.com
```

**High resource usage:**
```bash
# Check resource usage
docker stats
htop

# Adjust resource limits
docker-compose -f deployment/docker-compose.vps.yml down
# Edit resource limits in docker-compose.vps.yml
docker-compose -f docker-compose.yml -f deployment/docker-compose.vps.yml up -d
```

### Debug Commands

```bash
# Check all services
docker-compose ps

# View logs
docker-compose logs -f

# Check Traefik configuration
curl http://localhost:8080/api/rawdata

# Test health endpoints
curl -f http://localhost:8080/healthy
curl -f http://localhost:5000/health

# Check SSL certificates
openssl s_client -connect your-domain.com:443 -servername your-domain.com
```

## Maintenance

### Updates
```bash
# Pull latest images
docker-compose pull

# Rolling update
docker-compose up -d --force-recreate

# Check for updates
docker-compose config
```

### Log Rotation
```bash
# Configure log rotation
sudo nano /etc/logrotate.d/docker-containers
```

### Security Updates
```bash
# Update system packages
sudo apt update && sudo apt upgrade

# Update Docker images
docker-compose pull
docker-compose up -d
```

## Cost Optimization

### Resource Optimization
- Monitor resource usage
- Adjust container limits
- Use appropriate VPS size

### Storage Optimization
- Regular cleanup of old logs
- Compress backup files
- Use efficient storage drivers

### Network Optimization
- Enable HTTP/2
- Use CDN for static assets
- Optimize Docker images

## Support

For deployment issues:
1. Check GitHub Actions workflow logs
2. Review VPS setup script output
3. Verify SSH connectivity and permissions
4. Check Docker and Docker Compose status
5. Review application logs

For more information, see the main project README.md and other deployment guides.
