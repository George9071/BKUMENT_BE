package vn.edu.hcmut.identity.controller;

import java.text.ParseException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;

import org.springframework.web.bind.annotation.*;

import com.nimbusds.jose.JOSEException;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import vn.edu.hcmut.identity.dto.request.*;
import vn.edu.hcmut.identity.dto.response.APIResponse;
import vn.edu.hcmut.identity.dto.response.AuthenticationResponse;
import vn.edu.hcmut.identity.dto.response.IntrospectResponse;
import vn.edu.hcmut.identity.service.AccountService;
import vn.edu.hcmut.identity.service.AuthenticationService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationController {

    AuthenticationService authenticationService;
    AccountService accountService;

    @PostMapping("/login")
    APIResponse<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) {
        var result = authenticationService.authenticate(request);
        return APIResponse.<AuthenticationResponse>builder().result(result).build();
    }

    @PostMapping("/introspect")
    APIResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request) {
        var result = authenticationService.introspect(request);
        return APIResponse.<IntrospectResponse>builder().result(result).build();
    }

    @PostMapping("/refresh")
    APIResponse<AuthenticationResponse> authenticate(@RequestBody RefreshRequest request)
            throws ParseException, JOSEException {
        var result = authenticationService.refreshToken(request);
        return APIResponse.<AuthenticationResponse>builder().result(result).build();
    }

    @PostMapping("/logout")
    APIResponse<Void> logout(@RequestBody LogoutRequest request) {
        authenticationService.logout(request);
        return APIResponse.<Void>builder().build();
    }

    @PostMapping("/verify-email")
    public APIResponse<Void> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        accountService.verifyEmail(request.getToken());

        return APIResponse.<Void>builder()
                .message("Email đã được xác minh thành công")
                .build();
    }

    @PostMapping("/forgot-password")
    public APIResponse<Void> forgotPassword(@RequestParam @Email String email) {
        accountService.forgotPassword(email);
        return APIResponse.<Void>builder()
                .message("Nếu email tồn tại, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu")
                .build();
    }

    @PostMapping("/reset-password")
    public APIResponse<Void> resetPassword(
            @RequestBody @Valid ResetPasswordRequest request) {
        accountService.resetPassword(
                request.getEmail(),
                request.getOtp(),
                request.getNewPassword()
        );
        return APIResponse.<Void>builder()
                .message("Mật khẩu đã được đặt lại thành công")
                .build();
    }
}
