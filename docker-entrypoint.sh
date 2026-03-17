#!/bin/sh
set -eu

ensure_writable_dir() {
  dir="$1"

  mkdir -p "$dir"
  chown -R app:app "$dir" 2>/dev/null || true
  chmod -R u+rwX "$dir" 2>/dev/null || true
}

ensure_writable_dir /app/data/user-icons
ensure_writable_dir /app/data/user-icons-private
ensure_writable_dir /app/data/user-illustrations
ensure_writable_dir /app/data/user-illustrations-private
ensure_writable_dir /app/data/user-mockups
ensure_writable_dir /app/data/user-labels
ensure_writable_dir /app/static-backup
ensure_writable_dir /app/generated-images
ensure_writable_dir /tmp/rembg

exec su -s /bin/sh app -c "exec java ${JAVA_OPTS:-} -jar /app/icon-pack-generator.jar"
