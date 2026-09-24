package com.erp.notify.controller;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import cn.hutool.core.util.RandomUtil;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * 开放平台回调加解密/验签工具（钉钉与企微同一套 AES-CBC 协议族）。
 * <p>
 * 两家协议的共同形态：
 * <ul>
 *   <li>密钥：EncodingAESKey（43 位）Base64 解码 + 补 "=" 得 32 字节 AES-256 密钥；</li>
 *   <li>算法：AES-256-CBC，IV = 密钥前 16 字节，PKCS7 填充（JCE 以 PKCS5Padding 等价支持）；</li>
 *   <li>明文结构：random(16B) + msg_len(4B 网络字节序) + msg + receiveid；</li>
 *   <li>验签：sha1(字典序排序(token, timestamp, nonce, encrypt) 拼接)。</li>
 * </ul>
 */
final class CallbackCrypto {

    private CallbackCrypto() {
    }

    /**
     * 验证回调签名。
     *
     * @param token    通道配置的 token
     * @param timestamp 时间戳
     * @param nonce    随机串
     * @param encrypt  密文（企微 URL 验证时为 echostr）
     * @param signature 平台传来的签名（msg_signature/sign）
     * @return 是否通过
     */
    static boolean verifySignature(String token, String timestamp, String nonce,
                                   String encrypt, String signature) {
        if (token == null || signature == null || encrypt == null) {
            return false;
        }
        String expected = signature(token, timestamp, nonce, encrypt);
        return expected != null && expected.equals(signature);
    }

    /**
     * 生成签名：sha1(字典序排序(token, timestamp, nonce, encrypt) 拼接)。
     *
     * @return 十六进制签名；异常返回 null
     */
    static String signature(String token, String timestamp, String nonce, String encrypt) {
        try {
            String[] parts = {token, timestamp, nonce, encrypt};
            Arrays.sort(parts);
            StringBuilder joined = new StringBuilder();
            for (String part : parts) {
                joined.append(part);
            }
            return SecureUtil.sha1(joined.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解密回调密文。
     *
     * @param encodingAesKey 43 位 EncodingAESKey（钉钉 aesKey / 企微 encodingAesKey）
     * @param cipherText     Base64 密文
     * @return 解出的 msg 明文；失败返回 null
     */
    static String decrypt(String encodingAesKey, String cipherText) {
        if (encodingAesKey == null || cipherText == null) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodingAesKey + "=");
            SymmetricCrypto aes = new SymmetricCrypto("AES/CBC/PKCS5Padding",
                    new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOf(key, 16)));
            byte[] plain = aes.decrypt(Base64.getDecoder().decode(cipherText));
            if (plain == null || plain.length < 20) {
                return null;
            }
            // random(16) + msg_len(4) + msg + receiveid
            int msgLen = ((plain[16] & 0xFF) << 24) | ((plain[17] & 0xFF) << 16)
                    | ((plain[18] & 0xFF) << 8) | (plain[19] & 0xFF);
            if (msgLen <= 0 || 20 + msgLen > plain.length) {
                return null;
            }
            return new String(plain, 20, msgLen, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 加密回调响应（钉钉要求响应同样加密；成功响应体由调用方组装）。
     *
     * @param encodingAesKey 43 位 EncodingAESKey
     * @param reply          明文响应
     * @param receiveId      企业 corpId（钉钉回调协议需要）
     * @return Base64 密文；失败返回 null
     */
    static String encrypt(String encodingAesKey, String reply, String receiveId) {
        if (encodingAesKey == null) {
            return null;
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodingAesKey + "=");
            byte[] random = RandomUtil.randomBytes(16);
            byte[] msg = reply.getBytes(StandardCharsets.UTF_8);
            byte[] receive = receiveId == null ? new byte[0] : receiveId.getBytes(StandardCharsets.UTF_8);
            byte[] plain = new byte[16 + 4 + msg.length + receive.length];
            System.arraycopy(random, 0, plain, 0, 16);
            plain[16] = (byte) (msg.length >>> 24);
            plain[17] = (byte) (msg.length >>> 16);
            plain[18] = (byte) (msg.length >>> 8);
            plain[19] = (byte) msg.length;
            System.arraycopy(msg, 0, plain, 20, msg.length);
            System.arraycopy(receive, 0, plain, 20 + msg.length, receive.length);
            SymmetricCrypto aes = new SymmetricCrypto("AES/CBC/PKCS5Padding",
                    new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOf(key, 16)));
            return Base64.getEncoder().encodeToString(aes.encrypt(plain));
        } catch (Exception e) {
            return null;
        }
    }
}
