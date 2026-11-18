package com.offercoach.identity.security;

import java.util.Collection;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 无状态 JWT 资源服务器安全配置。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ROLE_CLAIM = "role";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            JwtDecoder jwtDecoder,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            InternalTokenFilter internalTokenFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/captcha")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/registration-policy")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/plans")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasAnyRole("ADMIN", "SUPPORT")
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // 支付额度发放回调是唯一对外开放的内部接口，令牌由 InternalTokenFilter 校验；
                        // 其余内部接口不对外暴露，应用侧一律拒绝，避免任意 USER Token 越权命中。
                        .requestMatchers(HttpMethod.POST, "/internal/v1/payments/notify")
                        .permitAll()
                        .requestMatchers("/internal/**")
                        .denyAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 内部令牌校验放在安全链内：先过 StrictHttpFirewall，再按解码后的路径判定，
                // 避免作为独立 servlet filter 时早于安全链、按原始 URI 前缀匹配的绕过面
                .addFilterBefore(internalTokenFilter, AuthorizationFilter.class);
        return http.build();
    }

    /**
     * 阻止 Spring Boot 把 {@link InternalTokenFilter} 再注册成独立 servlet filter；
     * 该过滤器只应在安全链内执行一次。
     */
    @Bean
    public FilterRegistrationBean<InternalTokenFilter> internalTokenFilterRegistration(InternalTokenFilter filter) {
        FilterRegistrationBean<InternalTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService, TokenBlacklistValidator tokenBlacklistValidator) {
        return jwtService.jwtDecoder(tokenBlacklistValidator);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> toAuthorities(jwt));
        return converter;
    }

    private Collection<GrantedAuthority> toAuthorities(Jwt token) {
        String role = token.getClaimAsString(ROLE_CLAIM);
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return List.<GrantedAuthority>of(new SimpleGrantedAuthority(ROLE_PREFIX + role));
    }
}
