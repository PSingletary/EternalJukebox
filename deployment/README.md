# Azure Deployment Guide

This directory contains deployment configurations and scripts for deploying EternalJukebox to Azure Container Apps.

## Prerequisites

### Azure Resources
- Azure subscription with appropriate permissions
- Azure Container Registry (ACR)
- Azure Resource Group
- Azure Container Apps environment

### Required Secrets
Configure the following secrets in your GitHub repository:

```
AZURE_CONTAINER_REGISTRY=your-registry-name
ACR_USERNAME=your-acr-username
ACR_PASSWORD=your-acr-password
AZURE_CREDENTIALS=your-azure-service-principal-json
YOUTUBE_API_KEY=your-youtube-api-key
```

### Optional Environment Variables
```
DOMAIN_NAME=your-domain.com
ACME_EMAIL=your-email@domain.com
GRAFANA_PASSWORD=your-grafana-password
ANALYSIS_WORKERS=2
```

## Deployment Options

### 1. Automated Deployment via GitHub Actions

The GitHub Actions workflow automatically builds and deploys to Azure Container Apps.

**Trigger deployment:**
- Push to `main` or `llm` branches
- Manual trigger via GitHub Actions UI

**Manual deployment:**
1. Go to Actions tab in GitHub
2. Select "Deploy to Azure" workflow
3. Click "Run workflow"
4. Choose environment (staging/production)
5. Enter Container App and Resource Group names

### 2. Manual Deployment with Docker Compose

For local testing or custom deployments:

```bash
# Set environment variables
export REGISTRY_NAME=your-registry
export IMAGE_TAG=latest
export YOUTUBE_API_KEY=your-api-key
export DOMAIN_NAME=your-domain.com

# Deploy with Docker Compose
docker-compose -f deployment/docker-compose.prod.yml up -d
```

## Health Checks

### Automated Health Checks
The deployment includes automated health checks that verify:
- Main service availability (`/healthy` endpoint)
- Analysis service availability (`/health` endpoint)
- Docker container health status
- System resources (disk space, memory)
- Network connectivity

### Manual Health Check
Run the health check script manually:

```bash
# Basic health check
./deployment/health-check.sh

# Custom endpoints
./deployment/health-check.sh \
  --main-url https://your-app.azurecontainerapps.io \
  --analysis-url http://analysis-service:5000 \
  --timeout 60 \
  --retries 5
```

## Architecture

### Services
- **Main Service** (`eternaljukebox-main`): Kotlin application (port 8080)
- **Analysis Service** (`eternaljukebox-analysis`): Python Flask API (port 5000)
- **Redis** (optional): Caching layer
- **Traefik** (optional): Reverse proxy with SSL
- **Prometheus** (optional): Metrics collection
- **Grafana** (optional): Metrics visualization

### Networking
- Internal network for service-to-service communication
- External ingress for main service
- Internal ingress for analysis service
- SSL termination at Traefik (if enabled)

### Scaling
- Main service: 1-10 replicas
- Analysis service: 1-5 replicas
- Auto-scaling based on CPU/memory usage
- Horizontal Pod Autoscaler (HPA) configuration

## Monitoring

### Built-in Health Endpoints
- Main service: `GET /healthy`
- Analysis service: `GET /health`

### Optional Monitoring Stack
- **Prometheus**: Metrics collection and alerting
- **Grafana**: Dashboards and visualization
- **Traefik**: Access logs and metrics

### Access Monitoring
- Grafana: `https://grafana.your-domain.com`
- Prometheus: `https://prometheus.your-domain.com`
- Traefik Dashboard: `https://traefik.your-domain.com`

## Security

### Container Security
- Non-root user execution
- Read-only root filesystem
- Security scanning with Trivy
- Regular image updates

### Network Security
- Internal service communication
- SSL/TLS encryption
- Firewall rules
- Network policies

### Secrets Management
- Azure Key Vault integration
- Environment variable injection
- No hardcoded secrets in images

## Troubleshooting

### Common Issues

**Service not starting:**
```bash
# Check container logs
docker logs eternaljukebox-main
docker logs eternaljukebox-analysis

# Check health status
./deployment/health-check.sh
```

**Analysis service unavailable:**
```bash
# Check service connectivity
curl -f http://analysis-service:5000/health

# Verify environment variables
docker exec eternaljukebox-main env | grep ANALYSIS_SERVICE_URL
```

**High memory usage:**
```bash
# Check resource usage
docker stats

# Scale down if needed
docker-compose -f deployment/docker-compose.prod.yml up -d --scale eternaljukebox-analysis=1
```

### Logs
```bash
# View all logs
docker-compose -f deployment/docker-compose.prod.yml logs -f

# View specific service logs
docker-compose -f deployment/docker-compose.prod.yml logs -f eternaljukebox-main
docker-compose -f deployment/docker-compose.prod.yml logs -f eternaljukebox-analysis
```

## Cost Optimization

### Resource Limits
- CPU limits prevent runaway processes
- Memory limits control resource usage
- Auto-scaling reduces idle costs

### Storage Optimization
- Persistent volumes for data
- Log rotation and cleanup
- Analysis result caching

### Monitoring Costs
- Track resource usage in Azure portal
- Set up billing alerts
- Regular cost reviews

## Updates and Maintenance

### Rolling Updates
```bash
# Update images
docker-compose -f deployment/docker-compose.prod.yml pull

# Rolling restart
docker-compose -f deployment/docker-compose.prod.yml up -d
```

### Backup
```bash
# Backup volumes
docker run --rm -v eternaljukebox-data:/data -v $(pwd):/backup alpine tar czf /backup/data-backup.tar.gz -C /data .

# Backup database
docker run --rm -v eternaljukebox-database:/data -v $(pwd):/backup alpine tar czf /backup/database-backup.tar.gz -C /data .
```

## Support

For deployment issues:
1. Check the health check script output
2. Review container logs
3. Verify Azure resource status
4. Check GitHub Actions workflow logs

For more information, see the main project README.md.
