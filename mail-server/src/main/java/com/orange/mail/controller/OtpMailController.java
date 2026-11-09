package com.orange.mail.controller;

import com.orange.mail.dto.ApiResponse;
import com.orange.mail.dto.OtpRequest;
import com.orange.mail.service.EmailSenderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mail")
public class OtpMailController {

    private static final Logger log = LoggerFactory.getLogger(OtpMailController.class);

    private final EmailSenderService emailSenderService;

    public OtpMailController(EmailSenderService emailSenderService) {
        this.emailSenderService = emailSenderService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse> sendOtp(@Valid @RequestBody OtpRequest request) {
        try {
            emailSenderService.sendOtpEmail(request.getEmail(), request.getOtp());
            return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã OTP thành công tới: " + request.getEmail()));
        } catch (Exception e) {
            log.error("Không thể gửi OTP tới {}: {}", request.getEmail(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Không thể gửi email OTP: " + e.getMessage()));
        }
    }
}

