# Remember Me Feature Setup

## What It Covers

The application now supports persistent remember-me login for:

- email/password login from `/login`
- email/password login from the landing page login modal
- Google OAuth login from `/login`
- Google OAuth login from the landing page login modal

Logout at `/api/auth/logout` clears:

- `JSESSIONID`
- `remember-me`
- `oauth-remember-me`

## How It Works

### Email/Password Login

Email login posts `rememberMe` to `/api/auth/login`.

If `rememberMe=true`, Spring Security issues the persistent `remember-me` cookie using `PersistentTokenBasedRememberMeServices`.

If `rememberMe=false`, any existing `remember-me` cookie is explicitly cleared.

### Google OAuth Login

OAuth does not submit a JSON payload, so the frontend writes a short-lived helper cookie first:

- cookie name: `oauth-remember-me`
- values: `true` or `false`
- max age: 10 minutes

After Google sign-in succeeds, `OAuth2RememberMeSuccessHandler` reads that cookie:

- if `true`, it creates the persistent Spring `remember-me` cookie
- if `false`, it clears any existing `remember-me` cookie

The helper `oauth-remember-me` cookie is always deleted after OAuth success.

## Backend Requirements

### Secret Key

The remember-me feature uses a configurable secret key:

```bash
REMEMBER_ME_SECRET_KEY=your-very-strong-secret-key-here-at-least-32-characters-long
```

Configuration source:

- production/default: `src/main/resources/application.yaml`
- local dev override: `src/main/resources/application-local.yaml`

### Database Table

Spring persistent remember-me requires the `persistent_logins` table.

This project now creates it through Liquibase:

- [src/main/resources/db/changelog/changes/002-create-persistent-logins.yaml](/Users/tomasz.pilarczyk/IdeaProjects/icon-pack-generator/src/main/resources/db/changelog/changes/002-create-persistent-logins.yaml)

The changelog is idempotent:

- if `persistent_logins` already exists, Liquibase marks the table changeset as ran
- if the username index already exists, Liquibase marks the index changeset as ran

## Security Recommendations

### Production

- Use a strong random key with at least 32 characters.
- Store `REMEMBER_ME_SECRET_KEY` in your secret manager, not in git.
- Rotate the key if it is exposed.
- Expect all existing remember-me sessions to be invalidated after key rotation.

### Key Generation

```bash
# OpenSSL
openssl rand -base64 32

# Python
python -c "import secrets; print(secrets.token_urlsafe(32))"

# Node.js
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

## Deployment Examples

### Docker

```bash
docker run -e REMEMBER_ME_SECRET_KEY=your-secret-key your-app
```

### Docker Compose

```yaml
environment:
  - REMEMBER_ME_SECRET_KEY=your-secret-key
```

### Kubernetes

```yaml
env:
  - name: REMEMBER_ME_SECRET_KEY
    valueFrom:
      secretKeyRef:
        name: app-secrets
        key: remember-me-key
```

## Troubleshooting

- If users are not staying logged in, verify `REMEMBER_ME_SECRET_KEY` is set consistently across restarts.
- If all remember-me sessions suddenly stop working, check whether the key changed.
- If startup fails around remember-me persistence, verify Liquibase applied the `persistent_logins` changelog successfully.
- If Google login does not persist but email login does, verify the frontend can set the `oauth-remember-me` helper cookie before redirecting to `/oauth2/authorization/google`.
