package com.gosu.iconpackgenerator.email.service;

import com.gosu.iconpackgenerator.singal.SignalMessageService;
import com.gosu.iconpackgenerator.email.template.EmailTemplate;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    private SignalMessageService signalMessageService;

    private SendGrid getSendGridClient() {
        return new SendGrid(sendGridApiKey);
    }

    public boolean sendPasswordSetupEmail(String toEmail, String token) {
        try {
            String subject = "Set up your Icon Pack Gen password";
            String setupUrl = baseUrl + "/password-setup/index.html?token=" + token;
            String htmlBody = EmailTemplate.passwordSetupEmail(setupUrl);

            String textBody = String.format("""
                Welcome to IconPackGen!
                
                You're almost ready to start creating amazing icons! To complete your account setup, 
                please set up your password by visiting this link:
                
                %s
                
                This link will expire in 24 hours for security reasons.
                
                If you didn't request this account, you can safely ignore this email.
                
                Best regards,
                The IconPackGen Team
                """, setupUrl);

            return sendEmail(toEmail, subject, htmlBody, textBody);
        } catch (Exception e) {
            log.error("Failed to send password setup email to {}", toEmail, e);
            return false;
        }
    }

    public boolean sendPasswordResetEmail(String toEmail, String token) {
        try {
            String subject = "Reset your IconPackGen password";
            String resetUrl = baseUrl + "/password-setup/index.html?token=" + token + "&reset=true";
            String htmlBody = EmailTemplate.passwordResetEmail(resetUrl);

            String textBody = String.format("""
                Password Reset Request
                
                We received a request to reset your password for your IconPackGen account.
                
                To reset your password, visit this link:
                %s
                
                This link will expire in 24 hours for security reasons.
                
                If you didn't request this password reset, you can safely ignore this email.
                
                Best regards,
                The IconPackGen Team
                """, resetUrl);

            return sendEmail(toEmail, subject, htmlBody, textBody);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
            return false;
        }
    }

    public boolean sendCustomEmail(String toEmail, String subject, String htmlBody) {
        try {
            String cleanSubject = subject != null ? subject.trim() : "";
            String cleanHtmlBody = htmlBody != null ? htmlBody : "";
            String textBody = htmlToPlainText(cleanHtmlBody);
            return sendEmail(toEmail, cleanSubject, cleanHtmlBody, textBody);
        } catch (Exception e) {
            log.error("Failed to send custom email to {}", toEmail, e);
            return false;
        }
    }
    
    /**
     * Inject unsubscribe link into email HTML body
     * Replaces {{UNSUBSCRIBE_LINK}} placeholder with actual unsubscribe URL
     * @param htmlBody the HTML email body
     * @param unsubscribeToken the unsubscribe token for the user
     * @return HTML body with unsubscribe link injected
     */
    public String injectUnsubscribeLink(String htmlBody, String unsubscribeToken) {
        if (htmlBody == null || htmlBody.isBlank()) {
            return htmlBody;
        }
        
        if (unsubscribeToken == null || unsubscribeToken.isBlank()) {
            log.warn("No unsubscribe token provided, removing unsubscribe placeholder");
            return htmlBody.replace("{{UNSUBSCRIBE_LINK}}", "");
        }
        
        String unsubscribeUrl = baseUrl + "/unsubscribe?token=" + unsubscribeToken;
        return htmlBody.replace("{{UNSUBSCRIBE_LINK}}", unsubscribeUrl);
    }

    private String htmlToPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String normalized = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("&nbsp;", " ");

        String withoutTags = normalized.replaceAll("<[^>]+>", "");
        return withoutTags
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean sendEmail(String toEmail, String subject, String htmlBody, String textBody) {
        try {
            Email from = new Email(fromEmail);
            Email to = new Email(toEmail);
            
            // Create content objects for both HTML and plain text
            Content textContent = new Content("text/plain", textBody);
            Content htmlContent = new Content("text/html", htmlBody);
            
            // Create mail with text content as primary (SendGrid requires text/plain first)
            Mail mail = new Mail(from, subject, to, textContent);
            // Add HTML as additional content
            mail.addContent(htmlContent);

            SendGrid sg = getSendGridClient();
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            
            Response response = sg.api(request);
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("Email sent successfully to {} with status code: {}", toEmail, response.getStatusCode());
                return true;
            } else {
                signalMessageService.sendSignalMessage("[IconPackGen]: Failed to send email to " + toEmail);
                log.error("Failed to send email to {}. Status code: {}, Response: {}", 
                    toEmail, response.getStatusCode(), response.getBody());
                log.info("To send it manually here's all the data: toEmail {}, subject: {}, htmlBody {}, textBody: {}", toEmail, subject, htmlBody, textBody);
                return false;
            }
            
        } catch (IOException e) {
            log.error("Failed to send email to {} due to IO exception", toEmail, e);
            return false;
        } catch (Exception e) {
            log.error("Failed to send email to {} due to unexpected error", toEmail, e);
            return false;
        }
    }
}
