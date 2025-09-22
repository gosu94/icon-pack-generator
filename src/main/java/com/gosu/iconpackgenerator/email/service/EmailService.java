package com.gosu.iconpackgenerator.email.service;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class EmailService {

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    private SendGrid getSendGridClient() {
        return new SendGrid(sendGridApiKey);
    }

    public boolean sendPasswordSetupEmail(String toEmail, String token) {
        try {
            String subject = "Set up your Icon Pack Generator password";
            String setupUrl = baseUrl + "/password-setup/index.html?token=" + token;
            
            String htmlBody = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Set up your password</title>
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { text-align: center; margin-bottom: 30px; }
                        .button { 
                            display: inline-block; 
                            padding: 12px 24px; 
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); 
                            color: white; 
                            text-decoration: none; 
                            border-radius: 8px; 
                            font-weight: bold;
                        }
                        .footer { margin-top: 30px; font-size: 12px; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Welcome to Icon Pack Generator!</h1>
                        </div>
                        
                        <p>Hi there,</p>
                        
                        <p>You're almost ready to start creating amazing icons! To complete your account setup, please set up your password by clicking the button below:</p>
                        
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" class="button">Set Up Password</a>
                        </div>
                        
                        <p>If the button doesn't work, you can copy and paste this link into your browser:</p>
                        <p><a href="%s">%s</a></p>
                        
                        <p>This link will expire in 24 hours for security reasons.</p>
                        
                        <p>If you didn't request this account, you can safely ignore this email.</p>
                        
                        <div class="footer">
                            <p>Best regards,<br>The Icon Pack Generator Team</p>
                        </div>
                    </div>
                </body>
                </html>
                """, setupUrl, setupUrl, setupUrl);

            String textBody = String.format("""
                Welcome to Icon Pack Generator!
                
                You're almost ready to start creating amazing icons! To complete your account setup, 
                please set up your password by visiting this link:
                
                %s
                
                This link will expire in 24 hours for security reasons.
                
                If you didn't request this account, you can safely ignore this email.
                
                Best regards,
                The Icon Pack Generator Team
                """, setupUrl);

            return sendEmail(toEmail, subject, htmlBody, textBody);
        } catch (Exception e) {
            log.error("Failed to send password setup email to {}", toEmail, e);
            return false;
        }
    }

    public boolean sendPasswordResetEmail(String toEmail, String token) {
        try {
            String subject = "Reset your Icon Pack Generator password";
            String resetUrl = baseUrl + "/password-setup/index.html?token=" + token + "&reset=true";
            
            String htmlBody = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Reset your password</title>
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { text-align: center; margin-bottom: 30px; }
                        .button { 
                            display: inline-block; 
                            padding: 12px 24px; 
                            background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); 
                            color: white; 
                            text-decoration: none; 
                            border-radius: 8px; 
                            font-weight: bold;
                        }
                        .footer { margin-top: 30px; font-size: 12px; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Password Reset Request</h1>
                        </div>
                        
                        <p>Hi there,</p>
                        
                        <p>We received a request to reset your password for your Icon Pack Generator account. Click the button below to reset your password:</p>
                        
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" class="button">Reset Password</a>
                        </div>
                        
                        <p>If the button doesn't work, you can copy and paste this link into your browser:</p>
                        <p><a href="%s">%s</a></p>
                        
                        <p>This link will expire in 24 hours for security reasons.</p>
                        
                        <p>If you didn't request this password reset, you can safely ignore this email.</p>
                        
                        <div class="footer">
                            <p>Best regards,<br>The Icon Pack Generator Team</p>
                        </div>
                    </div>
                </body>
                </html>
                """, resetUrl, resetUrl, resetUrl);

            String textBody = String.format("""
                Password Reset Request
                
                We received a request to reset your password for your Icon Pack Generator account.
                
                To reset your password, visit this link:
                %s
                
                This link will expire in 24 hours for security reasons.
                
                If you didn't request this password reset, you can safely ignore this email.
                
                Best regards,
                The Icon Pack Generator Team
                """, resetUrl);

            return sendEmail(toEmail, subject, htmlBody, textBody);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
            return false;
        }
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
                log.error("Failed to send email to {}. Status code: {}, Response: {}", 
                    toEmail, response.getStatusCode(), response.getBody());
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
