#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting troubleshooting process...${NC}\n"

# 1. Check container status
echo -e "${GREEN}1. Checking container status...${NC}"
docker-compose ps

# 2. Check container logs
echo -e "\n${GREEN}2. Checking container logs...${NC}"
docker-compose logs --tail=100 app

# 3. Check container resources
echo -e "\n${GREEN}3. Checking container resources...${NC}"
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"

# 4. Check container health
echo -e "\n${GREEN}4. Checking container health...${NC}"
docker inspect --format='{{json .State.Health}}' mia-app-container

# 5. Check container configuration
echo -e "\n${GREEN}5. Checking container configuration...${NC}"
docker inspect mia-app-container

# 6. Check volume mounts
echo -e "\n${GREEN}6. Checking volume mounts...${NC}"
docker volume ls

# 7. Check network connectivity
echo -e "\n${GREEN}7. Checking network connectivity...${NC}"
docker network inspect cursor-test-uat_default

# 8. Check application health endpoint
echo -e "\n${GREEN}8. Checking application health endpoint...${NC}"
curl -s http://localhost:8080/actuator/health

echo -e "\n${YELLOW}Troubleshooting complete.${NC}" 