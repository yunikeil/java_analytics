package dev.local.analytics.service;

import dev.local.analytics.config.AppConfig;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class MailService {
    private final AppConfig config;

    public MailService(AppConfig config) {
        this.config = config;
    }

    public void sendOtp(String to, String otp) {
        try {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", config.smtpHost());
            properties.put("mail.smtp.port", String.valueOf(config.smtpPort()));
            Session session = Session.getInstance(properties);
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.mailFrom()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("Analytics Trainer OTP");
            message.setText("Your Analytics Trainer confirmation code: " + otp + "\nThe code expires in 10 minutes.");
            Transport.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot send OTP email", e);
        }
    }
}
