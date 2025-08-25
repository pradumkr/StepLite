# Deployment Guide

## Overview

This guide covers deploying the Workflow Engine in various environments, from local development to production. It includes Docker, Kubernetes, and traditional deployment methods.

## Deployment Options

### 1. Docker Compose (Development/Testing)
- Quick setup for development
- Single-node deployment
- Easy to manage and debug

### 2. Docker Swarm (Staging)
- Multi-node orchestration
- Service discovery
- Rolling updates

### 3. Kubernetes (Production)
- Enterprise-grade orchestration
- Auto-scaling and load balancing
- High availability

### 4. Traditional Deployment (On-premises)
- Direct installation on servers
- Manual configuration
- Full control over infrastructure

## Docker Compose Deployment

### Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- At least 2GB available memory

### Quick Start

1. **Clone the repository:**
```bash
git clone <repository-url>
cd se-assignment-distributed-workflow-engine-pradumkumar
```

2. **Start services:**
```bash
docker-compose up -d
```

3. **Verify deployment:**
```bash
docker-compose ps
```

### Configuration

The `docker-compose.yml` file defines all services:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: workflow_engine
      POSTGRES_USER: workflow_user
      POSTGRES_PASSWORD: workflow_pass
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U workflow_user -d workflow_engine"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  workflow-engine:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=workflow_engine
      - DB_USER=workflow_user
      - DB_PASSWORD=workflow_pass
      - REDIS_HOST=redis
      - REDIS_PORT=6379
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

volumes:
  postgres_data:
  redis_data:
```

### Environment Variables

Create a `.env` file for environment-specific configuration:

```bash
# Database Configuration
DB_HOST=postgres
DB_PORT=5432
DB_NAME=workflow_engine
DB_USER=workflow_user
DB_PASSWORD=workflow_pass

# Redis Configuration
REDIS_HOST=redis
REDIS_PORT=6379

# Application Configuration
SERVER_PORT=8080
JAVA_OPTS=-Xmx2g -Xms1g

# Logging
LOGGING_LEVEL_COM_FREIGHTMATE_WORKFLOW=INFO
```

### Scaling

Scale individual services:

```bash
# Scale workflow engine instances
docker-compose up -d --scale workflow-engine=3

# Scale with specific limits
docker-compose up -d --scale workflow-engine=3 --scale redis=2
```

### Health Monitoring

Monitor service health:

```bash
# Check all services
docker-compose ps

# View logs
docker-compose logs -f workflow-engine

# Health check endpoint
curl http://localhost:8080/actuator/health
```

## Docker Swarm Deployment

### Prerequisites

- Docker Swarm initialized
- Multiple nodes (optional)

### Initialize Swarm

```bash
# Initialize swarm on manager node
docker swarm init

# Join worker nodes (run on worker nodes)
docker swarm join --token <token> <manager-ip>:2377
```

### Deploy Stack

1. **Create stack file (`docker-stack.yml`):**

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: workflow_engine
      POSTGRES_USER: workflow_user
      POSTGRES_PASSWORD: workflow_pass
    volumes:
      - postgres_data:/var/lib/postgresql/data
    deploy:
      replicas: 1
      placement:
        constraints:
          - node.role == manager
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
    networks:
      - workflow-network

  redis:
    image: redis:7-alpine
    deploy:
      replicas: 1
      placement:
        constraints:
          - node.role == manager
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
    networks:
      - workflow-network

  workflow-engine:
    image: workflow-engine:latest
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=workflow_engine
      - DB_USER=workflow_user
      - DB_PASSWORD=workflow_pass
      - REDIS_HOST=redis
      - REDIS_PORT=6379
    ports:
      - "8080:8080"
    deploy:
      replicas: 3
      update_config:
        parallelism: 1
        delay: 10s
        failure_action: rollback
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
      resources:
        limits:
          memory: 2G
          cpus: '1.0'
        reservations:
          memory: 1G
          cpus: '0.5'
    networks:
      - workflow-network
    depends_on:
      - postgres
      - redis

networks:
  workflow-network:
    driver: overlay

volumes:
  postgres_data:
    driver: local
```

2. **Deploy the stack:**

```bash
docker stack deploy -c docker-stack.yml workflow-stack
```

3. **Verify deployment:**

```bash
docker stack services workflow-stack
docker stack ps workflow-stack
```

### Scaling and Updates

```bash
# Scale service
docker service scale workflow-stack_workflow-engine=5

# Update service
docker service update --image workflow-engine:new-version workflow-stack_workflow-engine

# Rollback
docker service rollback workflow-stack_workflow-engine
```

## Kubernetes Deployment

### Prerequisites

- Kubernetes cluster (1.20+)
- kubectl configured
- Helm 3.0+ (optional)

### Manual Deployment

#### 1. Create Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: workflow-engine
  labels:
    name: workflow-engine
```

#### 2. Create ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: workflow-config
  namespace: workflow-engine
data:
  application.yml: |
    spring:
      datasource:
        url: jdbc:postgresql://postgres-service:5432/workflow_engine
        username: ${DB_USER}
        password: ${DB_PASSWORD}
      redis:
        host: redis-service
        port: 6379
    
    workflow:
      worker:
        batch-size: 10
        stuck-step-timeout-minutes: 30
    
    server:
      port: 8080
    
    logging:
      level:
        com.freightmate.workflow: INFO
```

#### 3. Create Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: workflow-secrets
  namespace: workflow-engine
type: Opaque
data:
  DB_USER: d29ya2Zsb3dfdXNlcg==  # workflow_user
  DB_PASSWORD: d29ya2Zsb3dfcGFzcw==  # workflow_pass
```

#### 4. Create PostgreSQL Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: workflow-engine
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15
        env:
        - name: POSTGRES_DB
          value: workflow_engine
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: workflow-secrets
              key: DB_USER
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: workflow-secrets
              key: DB_PASSWORD
        ports:
        - containerPort: 5432
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
      volumes:
      - name: postgres-storage
        persistentVolumeClaim:
          claimName: postgres-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-service
  namespace: workflow-engine
spec:
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
  type: ClusterIP
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: workflow-engine
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
```

#### 5. Create Redis Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: workflow-engine
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
---
apiVersion: v1
kind: Service
metadata:
  name: redis-service
  namespace: workflow-engine
spec:
  selector:
    app: redis
  ports:
  - port: 6379
    targetPort: 6379
  type: ClusterIP
```

#### 6. Create Workflow Engine Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: workflow-engine
  namespace: workflow-engine
spec:
  replicas: 3
  selector:
    matchLabels:
      app: workflow-engine
  template:
    metadata:
      labels:
        app: workflow-engine
    spec:
      containers:
      - name: workflow-engine
        image: workflow-engine:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: workflow-secrets
              key: DB_USER
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: workflow-secrets
              key: DB_PASSWORD
        - name: DB_HOST
          value: postgres-service
        - name: DB_PORT
          value: "5432"
        - name: DB_NAME
          value: workflow_engine
        - name: REDIS_HOST
          value: redis-service
        - name: REDIS_PORT
          value: "6379"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
      volumes:
      - name: config-volume
        configMap:
          name: workflow-config
---
apiVersion: v1
kind: Service
metadata:
  name: workflow-engine-service
  namespace: workflow-engine
spec:
  selector:
    app: workflow-engine
  ports:
  - port: 80
    targetPort: 8080
  type: LoadBalancer
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: workflow-engine-ingress
  namespace: workflow-engine
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: workflow-engine.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: workflow-engine-service
            port:
              number: 80
```

### Helm Chart Deployment

#### 1. Create Helm Chart

```bash
helm create workflow-engine
```

#### 2. Customize Values

Edit `workflow-engine/values.yaml`:

```yaml
replicaCount: 3

image:
  repository: workflow-engine
  tag: latest
  pullPolicy: IfNotPresent

service:
  type: LoadBalancer
  port: 80

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: workflow-engine.example.com
      paths:
        - path: /
          pathType: Prefix

resources:
  limits:
    cpu: 1000m
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 512Mi

postgresql:
  enabled: true
  postgresqlPassword: workflow_pass
  postgresqlDatabase: workflow_engine
  persistence:
    enabled: true
    size: 10Gi

redis:
  enabled: true
  persistence:
    enabled: true
    size: 5Gi
```

#### 3. Deploy with Helm

```bash
helm install workflow-engine ./workflow-engine
```

### Kubernetes Scaling

```bash
# Scale deployment
kubectl scale deployment workflow-engine --replicas=5

# Horizontal Pod Autoscaler
kubectl autoscale deployment workflow-engine --cpu-percent=70 --min=3 --max=10

# Vertical Pod Autoscaler (if enabled)
kubectl apply -f vpa.yaml
```

## Traditional Deployment

### Prerequisites

- Linux server (Ubuntu 20.04+ recommended)
- Java 17+
- PostgreSQL 12+
- Redis 6+
- Nginx (optional)

### Installation Steps

#### 1. System Preparation

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install required packages
sudo apt install -y openjdk-17-jdk postgresql postgresql-contrib redis-server nginx

# Start and enable services
sudo systemctl start postgresql
sudo systemctl enable postgresql
sudo systemctl start redis-server
sudo systemctl enable redis-server
```

#### 2. Database Setup

```bash
# Create database and user
sudo -u postgres psql -c "CREATE DATABASE workflow_engine;"
sudo -u postgres psql -c "CREATE USER workflow_user WITH PASSWORD 'workflow_pass';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE workflow_engine TO workflow_user;"
```

#### 3. Application Deployment

```bash
# Create application directory
sudo mkdir -p /opt/workflow-engine
sudo chown $USER:$USER /opt/workflow-engine

# Copy application files
cp target/workflow-engine-*.jar /opt/workflow-engine/
cp src/main/resources/application.yml /opt/workflow-engine/

# Create systemd service
sudo tee /etc/systemd/system/workflow-engine.service > /dev/null <<EOF
[Unit]
Description=Workflow Engine
After=network.target postgresql.service redis-server.service

[Service]
Type=simple
User=workflow-engine
WorkingDirectory=/opt/workflow-engine
ExecStart=/usr/bin/java -jar workflow-engine-1.0.0.jar
Restart=always
RestartSec=10
Environment="JAVA_OPTS=-Xmx2g -Xms1g"

[Install]
WantedBy=multi-user.target
EOF

# Create user and set permissions
sudo useradd -r -s /bin/false workflow-engine
sudo chown -R workflow-engine:workflow-engine /opt/workflow-engine

# Enable and start service
sudo systemctl daemon-reload
sudo systemctl enable workflow-engine
sudo systemctl start workflow-engine
```

#### 4. Nginx Configuration (Optional)

```bash
# Create Nginx configuration
sudo tee /etc/nginx/sites-available/workflow-engine > /dev/null <<EOF
server {
    listen 80;
    server_name workflow-engine.example.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

# Enable site
sudo ln -s /etc/nginx/sites-available/workflow-engine /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## Production Considerations

### Security

#### 1. Network Security

```bash
# Firewall configuration
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw allow 8080/tcp  # Application (if exposed)
sudo ufw enable
```

#### 2. SSL/TLS Configuration

```bash
# Install Certbot
sudo apt install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d workflow-engine.example.com

# Auto-renewal
sudo crontab -e
# Add: 0 12 * * * /usr/bin/certbot renew --quiet
```

#### 3. Database Security

```bash
# PostgreSQL security
sudo -u postgres psql -c "ALTER USER workflow_user PASSWORD 'strong_password';"
sudo -u postgres psql -c "REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;"
sudo -u postgres psql -c "GRANT ALL ON ALL TABLES IN SCHEMA public TO workflow_user;"
```

### Monitoring and Logging

#### 1. Application Monitoring

```bash
# Install Prometheus
wget https://github.com/prometheus/prometheus/releases/download/v2.45.0/prometheus-2.45.0.linux-amd64.tar.gz
tar xvf prometheus-*.tar.gz
cd prometheus-*

# Configure Prometheus
cat > prometheus.yml <<EOF
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'workflow-engine'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
EOF

# Start Prometheus
./prometheus --config.file=prometheus.yml
```

#### 2. Log Aggregation

```bash
# Install Filebeat
wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | sudo apt-key add -
echo "deb https://artifacts.elastic.co/packages/7.x/apt stable main" | sudo tee /etc/apt/sources.list.d/elastic-7.x.list
sudo apt update
sudo apt install filebeat

# Configure Filebeat
sudo tee /etc/filebeat/filebeat.yml > /dev/null <<EOF
filebeat.inputs:
- type: log
  enabled: true
  paths:
    - /opt/workflow-engine/*.log

output.elasticsearch:
  hosts: ["localhost:9200"]

setup.kibana:
  host: "localhost:5601"
EOF

# Start Filebeat
sudo systemctl enable filebeat
sudo systemctl start filebeat
```

### Backup and Recovery

#### 1. Database Backup

```bash
# Create backup script
sudo tee /opt/backup-db.sh > /dev/null <<EOF
#!/bin/bash
BACKUP_DIR="/opt/backups"
DATE=\$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="workflow_engine_\$DATE.sql"

mkdir -p \$BACKUP_DIR
pg_dump -h localhost -U workflow_user -d workflow_engine > \$BACKUP_DIR/\$BACKUP_FILE
gzip \$BACKUP_DIR/\$BACKUP_FILE

# Keep only last 7 days of backups
find \$BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete
EOF

# Make executable and schedule
sudo chmod +x /opt/backup-db.sh
sudo crontab -e
# Add: 0 2 * * * /opt/backup-db.sh
```

#### 2. Application Backup

```bash
# Backup application files
sudo tar -czf /opt/backups/workflow-engine-$(date +%Y%m%d_%H%M%S).tar.gz /opt/workflow-engine
```

## Performance Tuning

### JVM Tuning

```bash
# Optimize JVM settings
export JAVA_OPTS="-server -Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"
```

### Database Tuning

```bash
# PostgreSQL tuning
sudo -u postgres psql -c "ALTER SYSTEM SET shared_buffers = '256MB';"
sudo -u postgres psql -c "ALTER SYSTEM SET effective_cache_size = '1GB';"
sudo -u postgres psql -c "ALTER SYSTEM SET maintenance_work_mem = '64MB';"
sudo -u postgres psql -c "ALTER SYSTEM SET checkpoint_completion_target = 0.9;"
sudo -u postgres psql -c "ALTER SYSTEM SET wal_buffers = '16MB';"
sudo systemctl restart postgresql
```

### Redis Tuning

```bash
# Redis configuration
sudo tee /etc/redis/redis.conf > /dev/null <<EOF
maxmemory 512mb
maxmemory-policy allkeys-lru
save 900 1
save 300 10
save 60 10000
EOF

sudo systemctl restart redis-server
```

## Troubleshooting

### Common Issues

#### 1. Service Won't Start

```bash
# Check service status
sudo systemctl status workflow-engine

# Check logs
sudo journalctl -u workflow-engine -f

# Check Java installation
java -version
```

#### 2. Database Connection Issues

```bash
# Test database connection
psql -h localhost -U workflow_user -d workflow_engine

# Check PostgreSQL status
sudo systemctl status postgresql

# Check PostgreSQL logs
sudo tail -f /var/log/postgresql/postgresql-*.log
```

#### 3. Memory Issues

```bash
# Check memory usage
free -h

# Check JVM memory
jstat -gc <pid>

# Adjust JVM settings
export JAVA_OPTS="-Xmx4g -Xms2g"
```

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Database health
pg_isready -h localhost -U workflow_user

# Redis health
redis-cli ping
```

## Conclusion

This deployment guide covers the most common deployment scenarios for the Workflow Engine. Choose the approach that best fits your infrastructure and requirements.

For production deployments, ensure you have:
- Proper monitoring and alerting
- Backup and recovery procedures
- Security hardening
- Performance tuning
- Disaster recovery planning

Continue to the [Monitoring and Observability](monitoring-observability.md) guide for production monitoring setup.
