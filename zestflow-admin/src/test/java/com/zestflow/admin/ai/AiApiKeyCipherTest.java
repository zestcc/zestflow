package com.zestflow.admin.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiApiKeyCipherTest {

    private static final String JWT_SECRET = "ZestFlow_dev_JWT_Secret_Key_Change_Me_In_Production_!!_";

    @Test
    void encryptDecrypt_roundTrip() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(JWT_SECRET);
        String plain = "sk-test-key-12345678";

        String encrypted = cipher.encrypt(plain);
        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).isNotEqualTo(plain);

        String decrypted = cipher.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void padTo16Bytes_shouldPadShortSecret() {
        byte[] key = AiApiKeyCipher.padTo16Bytes("short");
        assertThat(key).hasSize(16);
        assertThat(key[0]).isEqualTo((byte) 's');
    }

    @Test
    void encrypt_nullOrBlank_returnsNull() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(JWT_SECRET);
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.encrypt("  ")).isNull();
    }
}
