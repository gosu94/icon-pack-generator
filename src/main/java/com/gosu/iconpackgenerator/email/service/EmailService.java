package com.gosu.iconpackgenerator.email.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

@Service
@Slf4j
public class EmailService {

    @Value("${aws.ses.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    private SesClient getSesClient() {
        return SesClient.builder()
                .region(Region.of(awsRegion))
                .build();
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
        try (SesClient sesClient = getSesClient()) {
            Destination destination = Destination.builder()
                    .toAddresses(toEmail)
                    .build();

            Content subjectContent = Content.builder()
                    .data(subject)
                    .charset("UTF-8")
                    .build();

            Content htmlContent = Content.builder()
                    .data(htmlBody)
                    .charset("UTF-8")
                    .build();

            Content textContent = Content.builder()
                    .data(textBody)
                    .charset("UTF-8")
                    .build();

            Body body = Body.builder()
                    .html(htmlContent)
                    .text(textContent)
                    .build();

            Message message = Message.builder()
                    .subject(subjectContent)
                    .body(body)
                    .build();

            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(destination)
                    .message(message)
                    .source(fromEmail)
                    .build();

            SendEmailResponse response = sesClient.sendEmail(emailRequest);
            log.info("Email sent successfully to {} with message ID: {}", toEmail, response.messageId());
            return true;

        } catch (Exception e) {
            log.error("Failed to send email to {}", toEmail, e);
            return false;
        }
    }
}
