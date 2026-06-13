package com.zestflow.admin.service.sso.oidc;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PkceUtilsTest {

    @Test
    void randomBase64Url_generatesUrlSafeString() {
        String value = PkceUtils.randomBase64Url(32);
        assertThat(value).isNotBlank();
        assertThat(value).doesNotContain("+", "/", "=");
    }

    @Test
    void sha256Base64Url_isDeterministic() {
        String hash1 = PkceUtils.sha256Base64Url("verifier-abc");
        String hash2 = PkceUtils.sha256Base64Url("verifier-abc");
        assertThat(hash1).isEqualTo(hash2);
        Base64.getUrlDecoder().decode(hash1);
    }

    @Test
    void urlEncode_encodesSpaces() {
        assertThat(PkceUtils.urlEncode("a b+c")).isEqualTo("a+b%2Bc");
    }
}
