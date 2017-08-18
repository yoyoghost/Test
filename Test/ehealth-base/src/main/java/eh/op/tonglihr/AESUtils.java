package eh.op.tonglihr;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by houxr on 2016/8/26.
 */
public class AESUtils {

    public static final String ECB_CIPHER_ALGORITHM = "AES/ECB/PKCS5Padding";


    public static String base64Encode(byte[] bytes) {
        return new Base64().encodeBase64String(bytes);
    }

    public static byte[] base64Decode(String base64Code) throws Exception {
        return new Base64().decodeBase64(base64Code);
    }

    /**
     * AES/ECB/PKCS5Padding
     *
     * @param input      待加密的明文
     * @param encryptKey 加密密钥
     * @return 加密后的密文 base64编码
     */
    public static String aesEncrypt(String input, String encryptKey) throws Exception {
        SecretKeySpec skey = new SecretKeySpec(encryptKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance(ECB_CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, skey);
        return base64Encode(cipher.doFinal(input.getBytes()));
    }

    /**
     * 解密方法暂时不需要使用，仅作验证加密结果用途
     **/
    public static String aesDecrypt(String encryptStr, String decryptKey) throws Exception {
        byte[] input = base64Decode(encryptStr);
        SecretKeySpec skey = new SecretKeySpec(decryptKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance(ECB_CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, skey);
        return new String(cipher.doFinal(input));
    }

    /**
     * md5加密
     *
     * @param content
     * @return
     */
    public static String md5Encrypt(String content) {
        try {
            return DigestUtils.md5Hex(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}