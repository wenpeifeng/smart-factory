package com.mes.integration.oidc;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PKCE 工具单测（RFC 7636 S256）。
 */
class PkceUtilTest {

    @Test
    void verifierShouldBeUrlSafeBase64() {
        String verifier = PkceUtil.newVerifier();
        // 仅含 BASE64URL 字符集，长度 43~128
        assertTrue(verifier.matches("[A-Za-z0-9_-]{43,128}"), "verifier 形如 " + verifier);
    }

    @Test
    void challengeShouldEqualSha256Base64Url() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        // RFC 7636 附录 B 的官方示例
        String expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        assertEquals(expected, PkceUtil.challenge(verifier));
    }

    @Test
    void challengeShouldBeDeterministic() {
        String v = PkceUtil.newVerifier();
        assertEquals(PkceUtil.challenge(v), PkceUtil.challenge(v));
        // 解码长度 = SHA-256 32 字节
        byte[] decoded = Base64.getUrlDecoder().decode(PkceUtil.challenge(v));
        assertEquals(32, decoded.length);
    }
}
