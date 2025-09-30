# Admin Control Panel Setup

## Overview
The application includes an admin control panel accessible at `/control-panel` for managing users and viewing system statistics.

## Configuration

### Setting the Admin Email

To grant admin access to a user, set the admin email address in your application configuration:

#### Application Properties (Spring Boot)

Add the following property to your `application.yaml` or `application-local.yaml`:

```yaml
app:
  admin:
    email: your-admin-email@example.com
```

Or in `application.properties`:

```properties
app.admin.email=your-admin-email@example.com
```

#### Environment Variable

Alternatively, you can set it as an environment variable:

```bash
export APP_ADMIN_EMAIL=your-admin-email@example.com
```

Or in your `.env` file (if using):

```
APP_ADMIN_EMAIL=your-admin-email@example.com
```

## Features

The admin control panel provides:

1. **User Management Table** with columns:
   - Email
   - Last Login
   - Trial Coins
   - Coins
   - Generated Icons (with view button)
   - Registered Date
   - Auth Provider

2. **Icons Modal** - Click the "View" button in the Generated Icons column to see all icons created by a specific user

3. **System Statistics** - Total users and total icons generated

## Access Control

- Only users with email matching the configured admin email can access the control panel
- Non-admin users attempting to access `/control-panel` will be redirected to the dashboard
- Admin users will see a shield icon in the navigation bar to access the control panel
- All admin API endpoints (`/api/admin/**`) are protected and require authentication

## Security Notes

- The admin email should be kept confidential
- Only one admin email is supported in the current implementation
- Admin status is checked on every request to admin endpoints
- Frontend access is controlled by the `isAdmin` flag in the authentication response
