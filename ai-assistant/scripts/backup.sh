#!/bin/bash
set -e

# Create backup of AI Assistant

OUTPUT_FILE="./backups/backup-$(date +%Y%m%d-%H%M%S).tar.gz"

usage() {
    echo "Usage: $0 [--output <file>]"
    echo "Default: $OUTPUT_FILE"
    exit 1
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        *)
            usage
            ;;
    esac
done

mkdir -p ./backups

echo "Creating backup: $OUTPUT_FILE"

# Files to backup
FILES=(
    "config/"
    "db/"
    "docker-compose.yml"
    "src/"
    "build.gradle.kts"
    "settings.gradle.kts"
)

tar -czf "$OUTPUT_FILE" "${FILES[@]}"

echo "Backup created: $OUTPUT_FILE"
ls -lh "$OUTPUT_FILE"
