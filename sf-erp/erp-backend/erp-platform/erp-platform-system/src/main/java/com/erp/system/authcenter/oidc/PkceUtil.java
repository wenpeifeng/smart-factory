package com.erp.system.authcenter.oidc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE 工具（RFC 7636，S256）。
 * <p>code_verifier 为 48 字符随机 Base64Url 串；
 * challenge = BASE64URL(SHA256(verifier))。
 */
public final class PkceUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PkceUtil() {
    }

    /** 生成随机 code_verifier（48 字符 Base64Url，满足 43~128 长度约束） */
    public static String newVerifier() {
        byte[] bytes = new byte[36];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 由 verifier 计算 S256 challenge（Base64Url 无填充） */
    public static String challenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
