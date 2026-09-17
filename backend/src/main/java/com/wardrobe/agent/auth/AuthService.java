package com.wardrobe.agent.auth;

import com.wardrobe.agent.common.BusinessException;
import com.wardrobe.agent.security.JwtService;
import com.wardrobe.agent.user.AppUser;
import com.wardrobe.agent.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
/** 认证应用服务：验证 BCrypt 密码并签发 JWT，错误时只返回统一提示以避免泄露账号状态。 */
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** 验证用户名、启用状态和密码，成功后返回 Token 及安全的用户视图。 */
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByUsername(request.username().trim()).orElseThrow(this::invalidCredentials);
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return new AuthResponse(jwtService.issue(user), view(user));
    }

    public AuthResponse.UserView get(String id) {
        return view(users.findById(id).orElseThrow(this::invalidCredentials));
    }

    private AuthResponse.UserView view(AppUser user) {
        return new AuthResponse.UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getWardrobeRevision());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
    }
}
