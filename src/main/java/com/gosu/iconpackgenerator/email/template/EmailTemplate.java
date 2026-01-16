package com.gosu.iconpackgenerator.email.template;

public final class EmailTemplate {

    private static final String LOGO_URL = "https://iconpackgen.com/images/logo%20small.webp";

    private EmailTemplate() {
    }

    public static String passwordSetupEmail(String setupUrl) {
        return actionEmail(
                "Set up your IconPackGen password",
                "Welcome to IconPackGen",
                "You're almost ready to start creating icons. Finish setting up your account by creating a password.",
                "Set Up Password",
                setupUrl,
                "This link will expire in 24 hours for security reasons.",
                "If you did not request this account, you can safely ignore this email."
        );
    }

    public static String passwordResetEmail(String resetUrl) {
        return actionEmail(
                "Reset your IconPackGen password",
                "Password reset request",
                "We received a request to reset the password for your IconPackGen account.",
                "Reset Password",
                resetUrl,
                "This link will expire in 24 hours for security reasons.",
                "If you did not request this password reset, you can safely ignore this email."
        );
    }

    private static String actionEmail(
            String title,
            String headline,
            String intro,
            String buttonLabel,
            String actionUrl,
            String note,
            String footer
    ) {
        return String.format("""
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <title>%s</title>
                    <style>
                      body {
                        margin: 0;
                        padding: 0;
                        background: #f8fafc;
                        font-family: "Manrope", "Segoe UI", Arial, sans-serif;
                        color: #0f172a;
                      }
                      .email-wrapper {
                        width: 100%%;
                        padding: 32px 16px;
                        background: linear-gradient(135deg, #eef2ff, #f5f3ff);
                      }
                      .email-card {
                        max-width: 640px;
                        margin: 0 auto;
                        background: #ffffff;
                        border-radius: 24px;
                        box-shadow: 0 30px 60px rgba(79, 70, 229, 0.12);
                        overflow: hidden;
                        border: 1px solid rgba(79, 70, 229, 0.08);
                      }
                      .email-header {
                        display: flex;
                        align-items: center;
                        gap: 16px;
                        padding: 32px;
                        background: linear-gradient(135deg, rgba(99, 102, 241, 0.12), rgba(124, 58, 237, 0.08));
                      }
                      .email-header h1 {
                        margin: 0;
                        font-size: 26px;
                        font-weight: 700;
                        color: #0f172a;
                      }
                      .email-header .brand-accent {
                        background: linear-gradient(135deg, #6366f1 0%%, #a855f7 100%%);
                        -webkit-background-clip: text;
                        color: transparent;
                      }
                      .email-content {
                        padding: 32px;
                      }
                      .email-content h2 {
                        font-size: 22px;
                        margin: 0 0 16px;
                        color: #312e81;
                      }
                      .email-content p {
                        margin: 0 0 16px;
                        line-height: 1.6;
                        color: #334155;
                        font-size: 16px;
                      }
                      .cta-button {
                        display: inline-block;
                        padding: 14px 32px;
                        background: linear-gradient(135deg, #6366f1 0%%, #a855f7 100%%);
                        color: #ffffff !important;
                        border-radius: 9999px;
                        text-decoration: none;
                        font-weight: 600;
                        box-shadow: 0 20px 35px rgba(99, 102, 241, 0.35);
                      }
                      .cta-wrapper {
                        margin: 24px 0 24px;
                        text-align: left;
                      }
                      .link-fallback {
                        font-size: 14px;
                        color: #475569;
                      }
                      .email-footer {
                        padding: 24px 32px 32px;
                        background: #f8fafc;
                        border-top: 1px solid rgba(99, 102, 241, 0.08);
                        font-size: 13px;
                        color: #64748b;
                        line-height: 1.6;
                      }
                      @media (max-width: 600px) {
                        .email-header,
                        .email-content,
                        .email-footer {
                          padding: 24px;
                        }
                        .email-header h1 {
                          font-size: 22px;
                        }
                      }
                    </style>
                  </head>
                  <body>
                    <div class="email-wrapper">
                      <div class="email-card">
                        <div class="email-header">
                          <img
                            src="%s"
                            alt="IconPackGen"
                            width="48"
                            height="48"
                            style="border-radius: 12px"
                          />
                          <h1>IconPackGen <span class="brand-accent">AI</span></h1>
                        </div>
                        <div class="email-content">
                          <h2>%s</h2>
                          <p>%s</p>
                          <div class="cta-wrapper">
                            <a href="%s" class="cta-button">%s</a>
                          </div>
                          <p class="link-fallback">If the button does not work, copy and paste this link:</p>
                          <p><a href="%s">%s</a></p>
                          <p>%s</p>
                        </div>
                        <div class="email-footer">
                          <p>%s</p>
                        </div>
                      </div>
                    </div>
                  </body>
                </html>
                """,
                title,
                LOGO_URL,
                headline,
                intro,
                actionUrl,
                buttonLabel,
                actionUrl,
                actionUrl,
                note,
                footer
        );
    }
}
