package com.orange.mail.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String toEmail, String otp) throws MessagingException {
        log.info("Dang gui email OTP toi: {}", toEmail);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("[O-Range] Ma xac nhan khoi phuc mat khau");

        String htmlContent = buildOtpHtml(otp);
        helper.setText(htmlContent, true);

        mailSender.send(message);
        log.info("Gui email OTP thanh cong toi: {}", toEmail);
    }

    public void sendReminderEmail(String toEmail, String subject, String content) throws MessagingException {
        log.info("Dang gui email nhac nho toi: {}, Chu de: {}", toEmail, subject);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("[O-Range Reminder] " + (subject != null ? subject : "Nhac nho lich hoc"));

        String htmlContent = buildReminderHtml(subject, content);
        helper.setText(htmlContent, true);

        mailSender.send(message);
        log.info("Gui email nhac nho thanh cong toi: {}", toEmail);
    }

    private String buildOtpHtml(String otp) {
        String template = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7f9fc; margin: 0; padding: 24px; color: #333; }
                .container { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.06); overflow: hidden; border: 1px solid #eaeaea; }
                .header { background: linear-gradient(135deg, #FF7A00 0%, #FF9E44 100%); padding: 28px 24px; text-align: center; color: white; }
                .header h1 { margin: 0; font-size: 22px; font-weight: 700; letter-spacing: 0.5px; }
                .content { padding: 32px 28px; text-align: center; }
                .otp-box { display: inline-block; background: #FFF4E5; border: 2px dashed #FF7A00; border-radius: 10px; padding: 14px 28px; font-size: 32px; font-weight: 800; letter-spacing: 6px; color: #FF7A00; margin: 24px 0; font-family: 'Courier New', monospace; }
                .note { font-size: 13px; color: #888; line-height: 1.6; }
                .footer { padding: 16px; text-align: center; font-size: 12px; color: #aaa; background: #fafafa; border-top: 1px solid #eee; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>O-Range Timetable AI</h1>
                </div>
                <div class="content">
                  <h3 style="margin-top:0; color:#222;">Xác thực mã OTP</h3>
                  <p style="color:#555; font-size:15px;">Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản O-Range. Dưới đây là mã xác minh của bạn:</p>
                  <div class="otp-box">%OTP%</div>
                  <p class="note">Mã OTP này có hiệu lực trong vòng <b>5 phút</b>.<br>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</p>
                </div>
                <div class="footer">
                  O-Range Timetable AI Team. All rights reserved.
                </div>
              </div>
            </body>
            </html>
            """;
        return template.replace("%OTP%", otp != null ? otp : "");
    }

    private String buildReminderHtml(String subject, String content) {
        String template = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f7f9fc; margin: 0; padding: 24px; color: #333; }
                .container { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.06); overflow: hidden; border: 1px solid #eaeaea; }
                .header { background: linear-gradient(135deg, #FF7A00 0%, #FF9E44 100%); padding: 24px; text-align: center; color: white; }
                .header h1 { margin: 0; font-size: 20px; font-weight: 700; }
                .content { padding: 28px 24px; }
                .card { background: #FFF4E5; border-left: 4px solid #FF7A00; border-radius: 6px; padding: 16px 20px; margin: 18px 0; }
                .card-title { font-weight: 700; font-size: 16px; color: #C2410C; margin-bottom: 6px; }
                .card-desc { font-size: 14px; color: #EA580C; line-height: 1.5; }
                .tip { background: #FEF9C3; border: 1px solid #FDE047; border-radius: 8px; padding: 12px 16px; font-size: 13px; color: #854D0E; margin-top: 20px; }
                .footer { padding: 16px; text-align: center; font-size: 12px; color: #aaa; background: #fafafa; border-top: 1px solid #eee; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>Loi Nhac Hoc Tap - O-Range</h1>
                </div>
                <div class="content">
                  <p style="font-size:15px; color:#444;">Xin chào bạn, bạn có một lịch học sắp diễn ra:</p>
                  <div class="card">
                    <div class="card-title">%SUBJECT%</div>
                    <div class="card-desc">%CONTENT%</div>
                  </div>
                  <div class="tip">
                    Meo hoc tap: Hay chuan bi sach vo, nuoc uong va chuyen dien thoai sang che do tap trung nhe!
                  </div>
                </div>
                <div class="footer">
                  O-Range Timetable AI Team. All rights reserved.
                </div>
              </div>
            </body>
            </html>
            """;
        return template
                .replace("%SUBJECT%", escapeHtml(subject))
                .replace("%CONTENT%", escapeHtml(content));
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

