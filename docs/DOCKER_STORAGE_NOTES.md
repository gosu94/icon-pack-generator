# Docker Storage Notes

This project serves and writes generated user assets from filesystem paths configured in `application.yaml`.

## Problem Summary

The application worked on macOS but failed on the Linux VPS with errors such as:

- `AccessDeniedException` while creating request directories under `/app/data/user-icons/...`
- `Failed to save icon: ...`
- gallery assets returning `404` after switching storage paths

The root cause was a mismatch between:

- the filesystem paths Spring Boot used for reading/writing generated assets
- the Docker bind mounts from the host
- Linux file ownership/permission behavior for mounted directories

macOS Docker Desktop was more permissive, so the problem stayed hidden there.

## Current Storage Model

In `src/main/resources/application.yaml` the app now reads storage roots from environment variables when present:

- `app.file-storage.base-path -> APP_FILE_STORAGE_BASE_PATH`
- `app.file-storage.private-base-path -> APP_FILE_STORAGE_PRIVATE_BASE_PATH`
- `app.illustrations-storage.base-path -> APP_ILLUSTRATIONS_STORAGE_BASE_PATH`
- `app.illustrations-storage.private-base-path -> APP_ILLUSTRATIONS_STORAGE_PRIVATE_BASE_PATH`
- `app.mockups-storage.base-path -> APP_MOCKUPS_STORAGE_BASE_PATH`
- `app.labels-storage.base-path -> APP_LABELS_STORAGE_BASE_PATH`

Inside the container, these resolve to:

- `/app/data/user-icons`
- `/app/data/user-icons-private`
- `/app/data/user-illustrations`
- `/app/data/user-illustrations-private`
- `/app/data/user-mockups`
- `/app/data/user-labels`

## Host Directories

Docker Compose mounts the existing host directories into those container paths:

- `./static/user-icons -> /app/data/user-icons`
- `./static/user-icons-private -> /app/data/user-icons-private`
- `./static/user-illustrations -> /app/data/user-illustrations`
- `./static/user-illustrations-private -> /app/data/user-illustrations-private`
- `./static/user-mockups -> /app/data/user-mockups`
- `./static/user-labels -> /app/data/user-labels`
- `./static-backup -> /app/static-backup`

This preserves the host-side layout used for backups. Backups should still target the same host directories under `static/`.

## Why Old Icons Broke Temporarily

At one point the app was changed to serve assets from `/app/data/...`, but Compose was still mounting old files into `/app/static/...`.

That caused:

- new writes to go to `/app/data/...`
- old files to remain in `./static/...`
- Spring MVC to serve `/user-icons/**` from `/app/data/user-icons`
- gallery requests for older assets to return `404`

Fixing the bind mount targets resolved that by making the old host directories visible at the new container storage paths.

## Why Saving Failed on Linux

Even after the storage roots were corrected, Linux still failed when creating nested request directories.

Typical failure:

```text
java.nio.file.AccessDeniedException: /app/data/user-icons/<user-id>/<request-id>
```

This happened because some existing directories inside mounted host folders were created previously with different ownership, often by `root` or another UID. The app runs inside the container as:

```text
uid=1001(app) gid=1001(app)
```

On Linux bind mounts, real host ownership and permissions are enforced. If an existing user directory is not writable by UID `1001`, Java cannot create a new child directory there.

macOS did not expose the problem in the same way because Docker Desktop handles mounted filesystem permissions differently.

## Why `docker-entrypoint.sh` Exists

The custom entrypoint exists to normalize mounted asset directories before the Java application starts.

It does the following:

1. Ensures the expected storage directories exist.
2. Attempts to `chown -R app:app` the mounted asset trees.
3. Applies `chmod -R u+rwX` so the container runtime user can write into them.
4. Starts the Spring Boot application as the `app` user.

Without this step, the app may start successfully but still fail later when it tries to create new request folders under an existing mounted directory tree with mismatched ownership.

## Why the App Is Still Started as `app`

The container needs root privileges briefly at startup to fix permissions on mounted host directories.

After that, the entrypoint switches back to the `app` user before launching Java. This keeps the actual application process non-root while still allowing startup-time repair of Linux bind mount permissions.

## Files Involved

- `src/main/resources/application.yaml`
  Controls storage base paths used by Spring Boot and static resource serving.

- `docker-compose.yml`
  Maps host directories into the correct container storage locations.

- `src/main/java/com/gosu/iconpackgenerator/config/WebConfig.java`
  Serves `/user-icons/**`, `/user-illustrations/**`, `/user-mockups/**`, and `/user-labels/**` directly from the configured filesystem paths.

- `src/main/java/com/gosu/iconpackgenerator/util/FileStorageService.java`
  Writes generated files into the configured storage roots.

- `docker-entrypoint.sh`
  Repairs directory permissions on mounted storage paths before starting the app.

- `Dockerfile`
  Installs the entrypoint and starts the container through it.

## Operational Notes

- Host backup paths do not change. Continue backing up the `static/` directories on the host.
- If asset writes fail again on Linux, inspect ownership of nested directories inside `static/user-icons`, `static/user-illustrations`, etc.
- If the container is rebuilt without the custom entrypoint, Linux write failures may reappear even though reads still work.

## VPS Verification Commands

Useful checks on the server:

```bash
sudo docker compose exec icon-pack-generator sh -lc 'id'
sudo docker compose exec icon-pack-generator sh -lc 'printenv | grep APP_'
sudo ls -ld static static/user-icons static/user-illustrations static/user-mockups static/user-labels
sudo docker compose logs icon-pack-generator --tail=100
```
