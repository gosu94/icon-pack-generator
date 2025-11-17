#!/bin/bash
set -euo pipefail

STATUS_URL="${STATUS_URL:-http://localhost:8060/api/status/generation}"

echo "Checking generation status at ${STATUS_URL}..."
if STATUS_JSON=$(curl -s --max-time 5 "${STATUS_URL}" 2>/dev/null); then
  IN_PROGRESS=$(echo "${STATUS_JSON}" | jq -r '.inProgress // empty' 2>/dev/null || echo "")
  if [ "${IN_PROGRESS}" = "true" ]; then
    ACTIVE_COUNT=$(echo "${STATUS_JSON}" | jq -r '.activeCount // "unknown"' 2>/dev/null || echo "unknown")
    echo "⚠️  Generation currently in progress (${ACTIVE_COUNT} active). Aborting restart to avoid disruption."
    exit 1
  fi
else
  echo "Could not query generation status; proceeding with caution."
fi

docker-compose build
docker-compose down
docker-compose up -d
systemctl reload nginx
