package com.ocr.javafx;

import com.ocr.javafx.dto.request.LoginRequest;
import com.ocr.javafx.dto.request.RegisterRequest;
import com.ocr.javafx.dto.response.AuthResponse;

public class Launcher {
    public static void main(String[] args) {
        if (args != null && args.length > 0 && "--self-test".equals(args[0])) {
            ApplicationContext ctx = new ApplicationContext();
            String email = "selftest_" + System.currentTimeMillis() + "@gmail.com";
            AuthResponse reg = ctx.getAuthService().register(new RegisterRequest("haha", email, "123456", "123456"));
            System.out.println("SELF_TEST_REGISTER=" + reg.isSuccess() + " | " + reg.getResponse());
            AuthResponse log = ctx.getAuthService().login(new LoginRequest(email, "123456"));
            System.out.println("SELF_TEST_LOGIN=" + log.isSuccess() + " | " + log.getResponse());
            System.exit(reg.isSuccess() && log.isSuccess() ? 0 : 1);
            return;
        }
        MainApplication.main(args);
    }
}