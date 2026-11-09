package com.offercoach.identity.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.offercoach.identity.application.TokenEpochService;
import com.offercoach.identity.config.AuthProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 签发与校验 RS256 Access Token。
 */
@Component
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String VERSION_CLAIM = "ver";

    private final AuthProperties properties;
    private final JwtKeyProvider keyProvider;
    private final TokenEpochService tokenEpochService;
    private final JwtEncoder jwtEncoder;

    public JwtService(AuthProperties properties, JwtKeyProvider keyProvider, TokenEpochService tokenEpochService) {
        this.properties = properties;
        this.keyProvider = keyProvider;
        this.tokenEpochService = tokenEpochService;

        RSAKey rsaKey = new RSAKey.Builder(keyProvider.publicKey())
                .privateKey(keyProvider.privateKey())
                .keyID(keyProvider.keyId())
                .build();
        JWKSet jwkSet = new JWKSet(List.<JWK>of(rsaKey));
        JWKSource<SecurityContext> jwkSource = (selector, context) -> selector.select(jwkSet);
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 签发 Access Token：sub=userId、role、ver、jti、iss、aud、iat、exp。
     */
    public IssuedAccessToken issue(long userId, String role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keyProvider.keyId())
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .audience(List.of(properties.getJwt().getAudience()))
                .subject(Long.toString(userId))
                .claim(ROLE_CLAIM, role)
                .claim(VERSION_CLAIM, tokenEpochService.current(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        String token =
                jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
        return new IssuedAccessToken(token, properties.getAccessTokenTtl().toSeconds());
    }

    /**
     * 组装资源服务器 JwtDecoder：校验签名、issuer、audience、时间与黑名单。
     */
    public JwtDecoder jwtDecoder(TokenBlacklistValidator tokenBlacklistValidator) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey(keyProvider.publicKey()).build();
        OAuth2TokenValidator<Jwt> issuerAndTimestamps =
                JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer());
        OAuth2TokenValidator<Jwt> audienceValidator = token -> {
            List<String> audience = token.getAudience();
            if (audience != null && audience.contains(properties.getJwt().getAudience())) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                List.of(issuerAndTimestamps, audienceValidator, tokenBlacklistValidator)));
        return decoder;
    }

    /**
     * 签发结果；expiresIn 单位为秒。
     */
    public record IssuedAccessToken(String token, long expiresIn) {}
}
