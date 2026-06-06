package com.zestflow.admin.ai;

import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * API Key AES 加解密 — 密钥来自 zestflow.jwt.secret（补齐至 16 字节）
 */
@Component
public class AiApiKeyCipher {

    private static final String ALGORITHM = "AES";

    private final SecretKeySpec secretKey;

    public AiApiKeyCipher(@Value("${zestflow.jwt.secret}") String jwtSecret) {
        this.secretKey = new SecretKeySpec(padTo16Bytes(jwtSecret), ALGORITHM);
    }

    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_KEY_ENCRYPT_FAILED);
        }
    }

    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AI_KEY_DECRYPT_FAILED);
        }
    }

    static byte[] padTo16Bytes(String secret) {
        byte[] raw = secret != null ? secret.getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] key = new byte[16];
        int len = Math.min(raw.length, 16);
        System.arraycopy(raw, 0, key, 0, len);
        return key;
    }
}
