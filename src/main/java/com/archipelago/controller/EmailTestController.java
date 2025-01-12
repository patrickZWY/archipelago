package com.archipelago.controller;

import com.archipelago.service.EmailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailTestController {
    private final EmailService emailService;

    public EmailTestController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/api/test/send-email")
    public String sendTestEmail(@RequestParam String to, @RequestParam String subject, @RequestParam String message) {
        emailService.sendEmail(to, subject, message);
        return "Email sent success to: " + to;
    }
}
