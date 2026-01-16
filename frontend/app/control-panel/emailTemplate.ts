const DEFAULT_EMAIL_BODY = `<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <title>IconPackGen AI Update</title>
    <style>
      body {
        margin: 0;
        padding: 0;
        background: #f8fafc;
        font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        color: #0f172a;
      }
      .email-wrapper {
        width: 100%;
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
        background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
        -webkit-background-clip: text;
        color: transparent;
        display: inline-flex;
        align-items: center;
        gap: 4px;
      }
      .email-header .brand-accent span {
        font-size: 18px;
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
      .feature-list {
        padding: 0;
        margin: 24px 0 32px;
        list-style: none;
      }
      .feature-list li {
        margin-bottom: 16px;
        padding-left: 32px;
        position: relative;
        font-size: 15px;
        color: #1e293b;
      }
      .cta-button {
        display: inline-block;
        padding: 14px 32px;
        background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
        color: #ffffff !important;
        border-radius: 9999px;
        text-decoration: none;
        font-weight: 600;
        box-shadow: 0 20px 35px rgba(99, 102, 241, 0.35);
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
            src="https://iconpackgen.com/images/logo%20small.webp"
            alt="IconPackGen AI"
            width="48"
            height="48"
            style="border-radius: 12px"
          />
          <h1>
            IconPackGen
              <span style="color:#6366f1;font-weight:700;">AI ✨</span>
           </h1>
        </div>
        <div class="email-content">
          <h2>Hey there,</h2>
          <p>
            We have something exciting to share with you from IconPackGen AI. Here’s a quick
            overview of the latest updates, improvements, and insider tips to help you create your
            next standout project.
          </p>
          <ul class="feature-list">
            <li>✨ Add your feature highlight or announcement here.</li>
            <li>✨ Share an upcoming launch, workshop, or promotion.</li>
            <li>✨ Include a helpful tip, resource, or community spotlight.</li>
          </ul>
          <a
            href="https://iconpackgen.com/dashboard"
            class="cta-button"
            target="_blank"
            rel="noopener"
          >
            Jump back into IconPackGen
          </a>
        </div>
        <div class="email-footer">
          <p>
            IconPackGen AI &bull; Crafted with creativity for designers and teams around the globe.
          </p>
          <p style="margin-top: 12px">
            You're receiving this email because you're part of the IconPackGen community. 
            <a href="{{UNSUBSCRIBE_LINK}}" style="color: #6366f1; text-decoration: underline;">Unsubscribe</a> 
            if you no longer wish to receive these emails.
          </p>
        </div>
      </div>
    </div>
  </body>
</html>`;

export default DEFAULT_EMAIL_BODY;
