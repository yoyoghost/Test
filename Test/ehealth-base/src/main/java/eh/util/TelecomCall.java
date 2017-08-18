package eh.util;

import ctd.util.JSONUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 电信双向回拨接口
 * Created by w on 2016/6/20.
 */
public class TelecomCall {
    public static final Logger log = Logger.getLogger(TelecomCall.class);
    private static final String APPKEY = "Ii6rPz4Pg7gGmKu0xFGgRWHqmH5y1eYu";
    private static final String APPSECRET = "rmjOLxwMNZL7hC4eaiiCLzBHe5NwNLXJ";
    //获取token
    private static final String TOKEN_URL = "http://122.229.8.16/hzctopenapi/restful/api/v1/token";
    //双向拨号
    private static final String DOUBLE_CALL_URL = "http://122.229.8.16/hzctopenapi/restful/api/v1/hp/doublecall";
    //挂断通话
    private static final String HANGUP_URL = "http://122.229.8.16/hzctopenapi/restful/api/v1/hp/hangupcall";
    //通话详情
    private static final String CALL_RECORD_URL = "http://122.229.8.16/hzctopenapi/restful/api/v1/hp/calllog";

    /**
     * 发起双呼
     *
     * @param caller 主叫号码
     * @param called 被叫号码
     * @return
     * @throws IOException
     */
    public static String doCall(String caller, String called) throws IOException {
        String token = getToken();//"ooJJcDx7fpDojF7uYAck0JuJdllt6Z0I";
        String reqid = UUID.randomUUID().toString().replace("-", "");
        String sign = sign(token + reqid + caller + called, "UTF-8");
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("token", token);
        map.put("reqId", reqid);
        map.put("caller", caller);
        map.put("called", called);
        map.put("sign", sign);
        return doPost(DOUBLE_CALL_URL, map);

    }

    /**
     * 查询话单
     *
     * @param callId 呼叫单号
     * @return
     * @throws IOException
     */
    public static String getRecord(String callId) throws IOException {
        String token = getToken();//"ooJJcDx7fpDojF7uYAck0JuJdllt6Z0I";
        String reqid = UUID.randomUUID().toString().replace("-", "");

        String sign = sign(token + reqid + callId, "UTF-8");
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("token", token);
        map.put("reqId", reqid);
        map.put("callId", callId);

        // map.put("callAnswerType", callAnswerType);
        map.put("sign", sign);
        return doPost(CALL_RECORD_URL, map);

    }

    private static String getToken() {
        Date date = new Date();
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        String timestamp = df.format(date);
        String reqid = UUID.randomUUID().toString().replace("-", "");

        String sign = sign(APPKEY + APPSECRET + reqid + timestamp, "UTF-8");
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("reqId", reqid);
        map.put("appkey", APPKEY);
        map.put("timestamp", timestamp);
        map.put("sign", sign);
        String res = "";
        try {
            log.info("getToken.map="+JSONUtils.toString(map));
            res = doPost(TOKEN_URL, map);
            HashMap<String, Object> token = JSONUtils.parse(res, HashMap.class);
            int expires = (int) token.get("expires");
            return (String) token.get("token");
        } catch (IOException e) {
            log.error("get token error:" + e.getMessage());
        }
        return res;

    }

    public static String hangup(String callId) throws ClientProtocolException, IOException {
        String token = getToken();//"ooJJcDx7fpDojF7uYAck0JuJdllt6Z0I";
        String reqid = UUID.randomUUID().toString().replace("-", "");

        String sign = sign(token + reqid + callId, "UTF-8");
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("token", token);
        map.put("reqId", reqid);
        map.put("callId", callId);

        // map.put("callAnswerType", callAnswerType);
        map.put("sign", sign);
        return doPost(HANGUP_URL, map);
    }

    /**
     * 签名字符串
     *
     * @param text          需要签名的字符串
     * @param input_charset 编码格式
     * @return 签名结果
     */
    public static String sign(String text, String input_charset) {

        return DigestUtils.md5Hex(getContentBytes(text, input_charset));
    }

    /**
     * @param content
     * @param charset
     * @return
     */
    private static byte[] getContentBytes(String content, String charset) {
        if (charset == null || "".equals(charset)) {
            return content.getBytes();
        }
        try {
            return content.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("MD5签名过程中出现错误,指定的编码集不对,您目前指定的编码集是:" + charset);
        }
    }

    public static String doPost(String uri, HashMap<String, Object> dataMap) throws IOException {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(uri);
        //拼接参数
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        //nvps.add(new BasicNameValuePair("username", "vip"));
        // nvps.add(new BasicNameValuePair("password", "secret"));
        Iterator<Map.Entry<String, Object>> it = dataMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            nvps.add(new BasicNameValuePair(entry.getKey(), (String) entry.getValue()));
        }
        log.info("doPost.nvps="+nvps);
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(nvps);
        // HttpEntity entity=new StringEntity(getParamsFromMap(dataMap), ContentType.APPLICATION_FORM_URLENCODED);
        httpPost.setEntity(entity);

        CloseableHttpResponse response2 = httpclient.execute(httpPost);
        try {
            log.info("doPost.response2.getStatusLine()="+response2.getStatusLine());
            HttpEntity entity2 = response2.getEntity();

            // do something useful with the dao body
            // and ensure it is fully consumed
            //消耗掉response
            String result = EntityUtils.toString(entity2);
            EntityUtils.consume(entity2);
            return result;
        } finally {
            response2.close();
        }
    }
}
