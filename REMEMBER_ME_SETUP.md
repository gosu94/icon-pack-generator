# Remember Me Feature Setup

## Environment Variable Configuration

The Remember Me feature uses a configurable secret key for enhanced security. Set the following environment variable:

```bash
REMEMBER_ME_SECRET_KEY=your-very-strong-secret-key-here-at-least-32-characters-long
```

## Security Recommendations

### Production Environment
- **MUST** use a strong, randomly generated key (minimum 32 characters)
- Use a cryptographically secure random generator to create the key
- Store the key securely (e.g., in your secret management system)
- Never commit the production key to version control

### Example Key Generation
```bash
# Using openssl
openssl rand -base64 32

# Using Python
python -c "import secrets; print(secrets.token_urlsafe(32))"

# Using Node.js
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

## Configuration Files

### Development
- `application-local.yaml`: Contains a development-specific key
- Used for local development only

### Production
- Set `REMEMBER_ME_SECRET_KEY` environment variable
- Falls back to `defaultRememberMeSecretKey123` if not set (not recommended for production)

## Docker/Container Deployment
```bash
# Docker run example
docker run -e REMEMBER_ME_SECRET_KEY=your-secret-key your-app

# Docker Compose
environment:
  - REMEMBER_ME_SECRET_KEY=your-secret-key

# Kubernetes
env:
- name: REMEMBER_ME_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: app-secrets
      key: remember-me-key
```

## Key Rotation
If you need to rotate the key:
1. Update the environment variable with the new key
2. Restart the application
3. All existing remember-me tokens will be invalidated
4. Users will need to log in again to get new remember-me tokens

## Troubleshooting
- If users can't stay logged in, check that the key is properly set
- If the key changes, all remember-me sessions are invalidated
- Check application logs for remember-me related errors
