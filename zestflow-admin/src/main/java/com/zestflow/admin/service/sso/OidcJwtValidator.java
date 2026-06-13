package com.zestflow.admin.service.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OidcJwtValidator {

    private static final Duration JWKS_CACHE_TTL = Duration.ofHours(1);

    private final SsoProperties ssoProperties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final Map<String, CachedJwks> jwksCache = new ConcurrentHashMap<>();

    public Claims parseAndValidate(String jwt) {
        if (!StringUtils.hasText(jwt)) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "Token 为空");
        }
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "Token 格式错误");
        }
        String kid = readKid(parts[0]);
        PublicKey publicKey = resolvePublicKey(kid);
        Jws<Claims> jws;
        try {
            jws = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(jwt);
        } catch (ExpiredJwtException ex) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "Token 已过期");
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "Token 无效");
        }
        Claims claims = jws.getPayload();
        if (claims.getExpiration() != null && claims.getExpiration().toInstant().isBefore(Instant.now())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "Token 已过期");
        }
        if (StringUtils.hasText(ssoProperties.getIssuer())
                && !ssoProperties.getIssuer().equals(claims.getIssuer())) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "Issuer 不匹配");
        }
        Object aud = claims.get("aud");
        String clientId = ssoProperties.getClientId();
        if (aud instanceof String audStr && !clientId.equals(audStr)) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "Audience 不匹配");
        }
        if (aud instanceof java.util.List<?> audList && !audList.contains(clientId)) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "Audience 不匹配");
        }
        return claims;
    }

    private String readKid(String headerPart) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(headerPart);
            JsonNode header = objectMapper.readTree(decoded);
            return header.path("kid").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private PublicKey resolvePublicKey(String kid) {
        String jwksUri = ssoProperties.getJwksUri();
        if (!StringUtils.hasText(jwksUri)) {
            jwksUri = ssoProperties.getIssuer().replaceAll("/$", "") + "/oauth2/jwks";
        }
        CachedJwks cached = jwksCache.get(jwksUri);
        if (cached == null || cached.expiresAt().isBefore(Instant.now())) {
            cached = loadJwks(jwksUri);
            jwksCache.put(jwksUri, cached);
        }
        JsonNode keyNode = findKey(cached.keys(), kid);
        if (keyNode == null) {
            cached = loadJwks(jwksUri);
            jwksCache.put(jwksUri, cached);
            keyNode = findKey(cached.keys(), kid);
        }
        if (keyNode == null) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "JWKS 密钥未找到");
        }
        return toPublicKey(keyNode);
    }

    private CachedJwks loadJwks(String jwksUri) {
        try {
            String body = restClientBuilder.build()
                    .get()
                    .uri(jwksUri)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            return new CachedJwks(root.path("keys"), Instant.now().plus(JWKS_CACHE_TTL));
        } catch (Exception ex) {
            log.warn("JWKS 加载失败: {}", jwksUri, ex);
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "JWKS 加载失败");
        }
    }

    private JsonNode findKey(JsonNode keys, String kid) {
        if (keys == null || !keys.isArray()) {
            return null;
        }
        for (JsonNode key : keys) {
            if (!StringUtils.hasText(kid) || kid.equals(key.path("kid").asText(null))) {
                if ("RSA".equals(key.path("kty").asText())) {
                    return key;
                }
            }
        }
        return keys.size() > 0 ? keys.get(0) : null;
    }

    private PublicKey toPublicKey(JsonNode keyNode) {
        try {
            byte[] nBytes = Base64.getUrlDecoder().decode(keyNode.path("n").asText());
            byte[] eBytes = Base64.getUrlDecoder().decode(keyNode.path("e").asText());
            RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, nBytes), new BigInteger(1, eBytes));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "JWKS 公钥解析失败");
        }
    }

    private record CachedJwks(JsonNode keys, Instant expiresAt) {
    }
}
