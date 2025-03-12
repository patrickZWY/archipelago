package com.archipelago.controller;

import com.archipelago.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailTestController {
    private final EmailService emailService;

    private final Logger logger = LoggerFactory.getLogger(EmailTestController.class);

    public EmailTestController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/api/test/send-email")
    public String sendTestEmail(@RequestParam String to, @RequestParam String subject, @RequestParam String message) {
        logger.info("Sending email to " + to);
        emailService.sendEmail(to, subject, message);
        logger.info("Email sent");
        return "Email sent success to: " + to;
    }
}
