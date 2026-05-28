package com.archipelago.service.impl;

import com.archipelago.exception.IllegalStateException;
import com.archipelago.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Override
    public void sendEmail(String to, String subject, String content) {
        if (!mailEnabled) {
            logger.info("Mail disabled. Intended email to {} with subject {} and content {}", to, subject, content);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("Mail is enabled but no JavaMailSender is configured");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(mailFrom);
            helper.setText(content, true);
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("Failed to send email");
        }
    }
}
