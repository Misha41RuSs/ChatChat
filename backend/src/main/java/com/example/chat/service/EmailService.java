package com.example.chat.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendInvitationEmail(String toEmail, String roomName, String invitedBy) {
        if (mailSender == null || toEmail == null || toEmail.isBlank()) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Приглашение в группу: " + roomName);
            message.setText(String.format(
                    "Здравствуйте!\n\n" +
                    "Пользователь %s пригласил вас в группу \"%s\".\n\n" +
                    "Войдите в приложение, чтобы принять или отклонить приглашение.\n\n" +
                    "С уважением,\n" +
                    "Команда ChatChat",
                    invitedBy, roomName
            ));
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Не удалось отправить email: " + e.getMessage());
        }
    }
}
