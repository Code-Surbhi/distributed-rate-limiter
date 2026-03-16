#!/bin/bash

# Rate Limiter Startup Script

echo "========================================"
echo "Starting Rate Limiter Service"
echo "========================================"

# Set environment variables
export REDIS_HOST=${REDIS_HOST:-localhost}
export REDIS_PORT=${REDIS_PORT:-6379}
export SPRING_PROFILES_ACTIVE=prod

# Get the JAR file
JAR_FILE=$(ls target/core-*.jar 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "ERROR: No JAR file found in target/"
    echo "Please run: mvn clean package"
    exit 1
fi

echo "Using JAR: $JAR_FILE"
echo "Redis: $REDIS_HOST:$REDIS_PORT"
echo "========================================"

# Start the application
java -jar \
    -Xmx512m \
    -Xms256m \
    -Dspring.profiles.active=prod \
    "$JAR_FILE"