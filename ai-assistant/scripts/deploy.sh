#!/bin/bash
set -e

# Deploy AI Assistant from backup to target host

usage() {
    echo "Usage: $0 --host <user@hostname> [--backup <backup-file>]"
    echo "Example: $0 --host user@stend --backup ./backups/backup-2024-01-01.tar.gz"
    exit 1
}

BACKUP_FILE=""
HOST=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --host)
            HOST="$2"
            shift 2
            ;;
        --backup)
            BACKUP_FILE="$2"
            shift 2
            ;;
        *)
            usage
            ;;
    esac
done

if [ -z "$HOST" ]; then
    usage
fi

echo "Deploying to $HOST..."

# Create backup if not provided
if [ -z "$BACKUP_FILE" ]; then
    BACKUP_FILE="./backups/backup-$(date +%Y%m%d-%H%M%S).tar.gz"
    echo "Creating backup: $BACKUP_FILE"
    ./scripts/backup.sh --output "$BACKUP_FILE"
fi

# Transfer and deploy
echo "Transferring to host..."
scp "$BACKUP_FILE" "$HOST:/tmp/ai-assistant-backup.tar.gz"

echo "Deploying on host..."
ssh "$HOST" << 'EOF'
    cd /tmp
    tar -xzf ai-assistant-backup.tar.gz
    cd ai-assistant

    # Start docker-compose
    docker-compose up -d

    # Wait for services
    echo "Waiting for services..."
    sleep 10

    # Check status
    docker-compose ps
EOF

echo "Deployment complete!"
