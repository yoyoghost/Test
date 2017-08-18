package eh.op.tonglihr;

import ctd.util.JSONUtils;
import eh.util.HttpHelper;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 德科 医生 签约 提现 api相关服务调用
 * Created by houxr on 2016/8/26.
 */
public class TonglihrUtil {
    private static final Logger logger = Logger.getLogger(TonglihrUtil.class);

    private static String url;
    private static String id;
    private static String key;

    public static String getUrl() {
        return url;
    }

    public static void setUrl(String url) {
        TonglihrUtil.url = url;
    }

    public static String getId() {
        return id;
    }

    public static void setId(String id) {
        TonglihrUtil.id = id;
    }

    public static String getKey() {
        return key;
    }

    public static void setKey(String key) {
        TonglihrUtil.key = key;
    }

    /**
     * 德科医生注册信息
     *
     * @param sign
     * @param body
     * @return
     */
    public static String tongliApiAddEmployees(String sign, String body) {
        try {
            String url = String.format(getUrl() + "/open/1.0/employees?id=%s&sign=%s", new Object[]{getId(), sign});
            //System.out.println("\n德科医生注册信息请求url:" + url);
            String result = HttpHelper.httpsPost(url, body);
            return result;
        } catch (IOException e) {
            logger.error(e);
        }
        return null;
    }


    /**
     * 德科提现
     *
     * @param sign
     * @param body
     * @return
     */
    public static String tongliApiForPayments(String sign, String body) {
        try {
            String url = String.format(getUrl() + "/open/1.0/payments?id=%s&sign=%s", new Object[]{getId(), sign});
            //System.out.println("\n德科提现请求url:" + url);
            String result = HttpHelper.httpsPost(url, body);
            return result;
        } catch (IOException e) {
            logger.error(e);
        }
        return null;
    }

    /**
     * 批次号查询德科打款明细
     *
     * @param billId
     * @return
     */
    public static Map<String, Object> tongliApiForEndPayment(String billId) {
        try {
            String url = String.format(getUrl() + "/open/1.0/payments/%s?id=%s", new Object[]{billId, getId()});
            //System.out.println("\n根据批次号查询德科打款结果url:" + url);
            Map<String, Object> result = HttpHelper.httpsGet(url);
            return result;
        } catch (IOException e) {
            logger.error(e);
        }
        return null;
    }

    /**
     * 生成 content 的值
     *
     * @param data
     * @return
     */
    public static String getContent(Object data) {
        //转换为json格式数据
        return JSONUtils.toString(data);
    }

    /**
     * 生成加密后的body
     *
     * @param content
     * @return
     */
    public static String getBody(String content) {
        try {
            //AES秘钥加密后的body数据
            String body = AESUtils.aesEncrypt(content, getKey());
            return body;
        } catch (Exception e) {
            logger.error(e);
        }
        return null;
    }

    /**
     * 生成 sign 签名
     *
     * @param content
     * @return
     */
    public static String getSign(String content) {
        return AESUtils.md5Encrypt(content + getKey());
    }


    /**
     * 医生提现时跟德科支付进行数据验证对接
     *
     * @param data
     * @return
     */
    public static String apiTongliAddEmployees(final List<UserInfo> data) {
        //System.out.println("===key:" + getKey() + ",id:" + getId());
        String content = getContent(data);
        String sign = getSign(content);
        String body = getBody(content);
        //System.out.println("\ncontent:" + content + ",sign:" + sign + ",body:" + body);
        String result = tongliApiAddEmployees(sign, body);
        //System.out.println("deke return add employees result:" + result);
        return result;
    }

    /**
     * cashBill及医生提现单详情对接德科api
     *
     * @param data
     * @return
     */
    public static String apiTongliForPayments(final WithdrawInfo data) {
        //System.out.println("==key:" + getKey() + ",id:" + getId());
        String content = getContent(data);
        String sign = getSign(content);
        String body = getBody(content);
        //System.out.println("\ncontent:" + content + ",sign:" + sign + ",body:" + body);
        String result = tongliApiForPayments(sign, body);
        //System.out.println("deke return payments result:" + result);
        return result;
    }

}
