const OAUTH_REMEMBER_ME_COOKIE = "oauth-remember-me";
const OAUTH_REMEMBER_ME_MAX_AGE_SECONDS = 10 * 60;

export function persistOAuthRememberMePreference(rememberMe: boolean) {
  if (typeof document === "undefined") {
    return;
  }

  const secure = window.location.protocol === "https:" ? "; Secure" : "";
  document.cookie = `${OAUTH_REMEMBER_ME_COOKIE}=${rememberMe ? "true" : "false"}; Path=/; Max-Age=${OAUTH_REMEMBER_ME_MAX_AGE_SECONDS}; SameSite=Lax${secure}`;
}
