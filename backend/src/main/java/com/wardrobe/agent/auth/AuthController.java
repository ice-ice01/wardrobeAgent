package com.wardrobe.agent.auth;

import com.wardrobe.agent.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
/** 登录和当前用户信息的 HTTP 接口；构造器由 Spring 自动注入唯一的 AuthService。 */
public class AuthController {
    private final AuthService authService;


    public AuthController(AuthService authService) { this.authService = authService; }


    @PostMapping("/auth/login")
    /** @Valid 在进入业务方法前执行 LoginRequest 上的 Bean Validation 规则。 */
    AuthResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }

    @GetMapping("/users/me")
    AuthResponse.UserView me() { return authService.get(CurrentUser.require().id()); }
}
