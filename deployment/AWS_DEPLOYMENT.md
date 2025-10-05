# AWS Deployment Guide

This guide covers deploying EternalJukebox to AWS using multiple deployment options: ECS with Fargate, App Runner, and Elastic Beanstalk.

## Prerequisites

### AWS Resources Required

**For ECS Deployment:**
- ECS Cluster with Fargate launch type
- Application Load Balancer (ALB)
- EFS file system for persistent storage
- CloudWatch Log Groups
- IAM roles for ECS tasks and execution

**For App Runner Deployment:**
- ECR repository for container images
- IAM roles for App Runner service

**For Elastic Beanstalk Deployment:**
- Elastic Beanstalk application and environment
- S3 bucket for application versions
- SSL certificate (optional)

### Required GitHub Secrets

Configure the following secrets in your GitHub repository:

```
AWS_ACCOUNT_ID=123456789012
AWS_ROLE_ARN=arn:aws:iam::123456789012:role/GitHubActionsRole
YOUTUBE_API_KEY=your-youtube-api-key
EB_S3_BUCKET=your-elastic-beanstalk-bucket
```

### AWS IAM Permissions

The GitHub Actions role needs the following permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:*",
        "ecs:*",
        "iam:PassRole",
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "elasticloadbalancing:*",
        "apprunner:*",
        "elasticbeanstalk:*",
        "s3:PutObject",
        "s3:GetObject",
        "ssm:GetParameter",
        "ssm:PutParameter"
      ],
      "Resource": "*"
    }
  ]
}
```

## Deployment Options

### 1. ECS with Fargate (Recommended)

**Features:**
- Serverless container hosting
- Auto-scaling and load balancing
- Persistent storage with EFS
- Health checks and monitoring
- Multi-AZ deployment

**Deployment Steps:**
1. Push to `main` or `llm` branch
2. Select "Deploy to AWS" workflow
3. Choose `ecs` as deployment target
4. Select environment (staging/production)
5. Enter cluster name (default: eternaljukebox-cluster)

**Architecture:**
```
Internet → ALB → ECS Service (Main) → ECS Service (Analysis)
                ↓
            EFS Storage
```

### 2. AWS App Runner

**Features:**
- Simplified serverless deployment
- Automatic scaling
- Built-in load balancing
- Easy CI/CD integration

**Deployment Steps:**
1. Push to `main` or `llm` branch
2. Select "Deploy to AWS" workflow
3. Choose `app-runner` as deployment target
4. Select environment (staging/production)

**Configuration:**
- Main Service: 1 vCPU, 2GB RAM
- Analysis Service: 2 vCPU, 4GB RAM
- Auto-deployments enabled

### 3. Elastic Beanstalk

**Features:**
- Managed platform service
- Easy deployment and scaling
- Integrated monitoring
- Platform updates

**Deployment Steps:**
1. Push to `main` or `llm` branch
2. Select "Deploy to AWS" workflow
3. Choose `elastic-beanstalk` as deployment target
4. Select environment (staging/production)

## Infrastructure Setup

### ECS Setup

**1. Create ECS Cluster:**
```bash
aws ecs create-cluster --cluster-name eternaljukebox-cluster
```

**2. Create Application Load Balancer:**
```bash
aws elbv2 create-load-balancer \
  --name eternaljukebox-alb \
  --subnets subnet-12345 subnet-67890 \
  --security-groups sg-12345
```

**3. Create EFS File System:**
```bash
aws efs create-file-system \
  --creation-token eternaljukebox-efs \
  --performance-mode generalPurpose \
  --encrypted
```

**4. Create CloudWatch Log Groups:**
```bash
aws logs create-log-group --log-group-name /ecs/eternaljukebox-main
aws logs create-log-group --log-group-name /ecs/eternaljukebox-analysis
```

### App Runner Setup

**1. Create ECR Repository:**
```bash
aws ecr create-repository --repository-name eternaljukebox-main
aws ecr create-repository --repository-name eternaljukebox-analysis
```

### Elastic Beanstalk Setup

**1. Create Application:**
```bash
aws elasticbeanstalk create-application \
  --application-name eternaljukebox \
  --description "EternalJukebox Audio Analysis Platform"
```

**2. Create Environment:**
```bash
aws elasticbeanstalk create-environment \
  --application-name eternaljukebox \
  --environment-name eternaljukebox-staging \
  --solution-stack-name "64bit Amazon Linux 2 v3.4.0 running Docker"
```

## Configuration

### Environment Variables

**Main Service:**
- `PORT`: Application port (8080)
- `ANALYSIS_SERVICE_URL`: Analysis service URL
- `YOUTUBE_API_KEY`: YouTube Data API key
- `STORAGE_TYPE`: Storage type (LOCAL)
- `DATABASE_TYPE`: Database type (H2)

**Analysis Service:**
- `FLASK_ENV`: Flask environment (production)
- `PYTHONPATH`: Python path (/app)
- `WORKERS`: Number of workers (2)

### Secrets Management

Store sensitive data in AWS Systems Manager Parameter Store:

```bash
# Store YouTube API key
aws ssm put-parameter \
  --name "/eternaljukebox/youtube-api-key" \
  --value "your-api-key" \
  --type "SecureString"
```

### Networking

**ECS VPC Configuration:**
- Public subnets for ALB
- Private subnets for ECS tasks
- Security groups for service isolation
- NAT Gateway for outbound internet access

## Monitoring and Logging

### CloudWatch Integration

**Log Groups:**
- `/ecs/eternaljukebox-main`
- `/ecs/eternaljukebox-analysis`

**Metrics:**
- CPU and memory utilization
- Request count and latency
- Error rates
- Health check status

### Health Checks

**Main Service:** `GET /healthy`
**Analysis Service:** `GET /health`

**Health Check Configuration:**
- Interval: 30 seconds
- Timeout: 5-10 seconds
- Healthy threshold: 2
- Unhealthy threshold: 3

## Scaling Configuration

### ECS Auto Scaling

**Target Tracking Policies:**
- CPU utilization: 70%
- Memory utilization: 80%
- Request count: 1000 requests/minute

**Scaling Limits:**
- Main Service: 1-10 tasks
- Analysis Service: 1-5 tasks

### App Runner Auto Scaling

- Automatic scaling based on traffic
- Min instances: 1
- Max instances: 25
- CPU threshold: 70%

## Security

### Network Security

**Security Groups:**
- ALB: Allow HTTP/HTTPS from internet
- ECS Tasks: Allow traffic from ALB only
- Analysis Service: Internal access only

### Container Security

**Image Scanning:**
- Trivy vulnerability scanning
- ECR image scanning
- Regular base image updates

**Runtime Security:**
- Non-root user execution
- Read-only root filesystem
- Resource limits and quotas

## Cost Optimization

### ECS Cost Optimization

**Fargate Spot:**
- Use Spot instances for non-critical workloads
- Up to 90% cost savings

**Right-sizing:**
- Monitor CPU and memory usage
- Adjust task definitions based on metrics

**Reserved Capacity:**
- Consider Reserved Instances for predictable workloads

### App Runner Cost Optimization

**Auto-scaling:**
- Scale down during low traffic periods
- Optimize memory allocation

## Troubleshooting

### Common Issues

**Service Not Starting:**
```bash
# Check ECS service events
aws ecs describe-services \
  --cluster eternaljukebox-cluster \
  --services eternaljukebox-main

# Check task logs
aws logs get-log-events \
  --log-group-name /ecs/eternaljukebox-main \
  --log-stream-name ecs/eternaljukebox-main/task-id
```

**Health Check Failures:**
```bash
# Check health check configuration
aws elbv2 describe-target-health \
  --target-group-arn arn:aws:elasticloadbalancing:...

# Test health endpoint
curl -f http://your-alb-dns/healthy
```

**High Memory Usage:**
```bash
# Check CloudWatch metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name MemoryUtilization \
  --dimensions Name=ServiceName,Value=eternaljukebox-main
```

### Debug Commands

```bash
# Get service status
aws ecs describe-services --cluster eternaljukebox-cluster --services eternaljukebox-main eternaljukebox-analysis

# List running tasks
aws ecs list-tasks --cluster eternaljukebox-cluster --service-name eternaljukebox-main

# Get task definition
aws ecs describe-task-definition --task-definition eternaljukebox-main

# Check ALB health
aws elbv2 describe-target-health --target-group-arn your-target-group-arn
```

## Maintenance

### Updates

**Rolling Updates:**
- ECS supports rolling deployments
- App Runner handles automatic updates
- Elastic Beanstalk supports blue/green deployments

**Monitoring Updates:**
- Watch deployment progress
- Monitor health checks
- Verify service functionality

### Backups

**EFS Backups:**
- Enable EFS backup policies
- Cross-region replication for disaster recovery

**Database Backups:**
- Regular H2 database backups
- Store in S3 with lifecycle policies

## Support

For deployment issues:
1. Check GitHub Actions workflow logs
2. Review CloudWatch logs and metrics
3. Verify AWS resource status
4. Check security group and network configurations

For more information, see the main project README.md and deployment documentation.
