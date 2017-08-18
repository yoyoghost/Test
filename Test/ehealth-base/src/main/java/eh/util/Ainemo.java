package eh.util;

import ctd.mvc.support.HttpClientUtils;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.bus.constant.VideoInfoConstant;
import eh.utils.DateConversion;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.util.EntityUtils;
import org.jdom.IllegalDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.SignatureException;
import java.util.*;


public class Ainemo {
    private static final Logger logger = LoggerFactory.getLogger(Ainemo.class);
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static byte[] hashEntity = "".getBytes();//请求的body
    private static HttpClient httpClient;

    static {
        HttpClientUtils httpClientUtils = AppContextHolder.getBean("httpClientUtils", HttpClientUtils.class);
        httpClient = httpClientUtils.getHttpClient();
    }


    //2016-10-17 zhangx 将这些变量抽取出到config.properties里面
    //正式环境
//    private static String product_requestUriPrefix="https://www.ainemo.com/api/rest/external/v1/";
//    private static final String product_token="9106a8cf7fa5ac4985021e7d557b3630d335764133d76ac74f4327dfea9ffd2e";
//    private static final String product_extid="5886885697deb9f4760b3a5e1ab912b9a3b7dfd3";//企业id

    //测试环境
//    private static String dev_requestUriPrefix="https://dev.xiaoyuonline.com/api/rest/external/v1/";
//    private static final String dev_extid="346d569fc78988f82ec183111ded661761f20aea";//企业id
//    private static String dev_token="5886885697deb9f4760b3a5e1ab912b9a3b7dfd3";

    private static String requestUriPrefix = "https://www.ainemo.com/api/rest/external/v1/";
    private static String token = "9106a8cf7fa5ac4985021e7d557b3630d335764133d76ac74f4327dfea9ffd2e";
    private static String extid = "5886885697deb9f4760b3a5e1ab912b9a3b7dfd3";//企业id
    private static String token_other = "9106a8cf7fa5ac4985021e7d557b3630d335764133d76ac74f4327dfea9ffd2e";
    private static String extid_other = "";//第三方所用企业id
    private static String env = "product";

//    public static String getEnv() {
//        return env;
//    }
//
//    public static void setEnv(String env) {
//        Ainemo.env = env;
//    }


    public static void setConfig(String env, String requestUriPrefix, String extid, String token, String extid_other, String token_other) {
        if (!StringUtils.isEmpty(env) && !StringUtils.isEmpty(requestUriPrefix) &&
                !StringUtils.isEmpty(token) && !StringUtils.isEmpty(extid)) {
            Ainemo.env = env;
            Ainemo.requestUriPrefix = requestUriPrefix.trim();
            Ainemo.extid = extid.trim();
            Ainemo.token = token.trim();

            //2016-10-17 zhangx 将这些变量抽取出到config.properties里面
//            if("product".equalsIgnoreCase(Ainemo.env.trim())){
////                requestUriPrefix=product_requestUriPrefix;
//                token=product_token;
//                extid=product_extid;
//            }else{
////                requestUriPrefix=dev_requestUriPrefix;
//                token=dev_token;
//                extid=dev_extid;
//            }

        }
        if (!StringUtils.isEmpty(env) && !StringUtils.isEmpty(requestUriPrefix) &&
                !StringUtils.isEmpty(token_other) && !StringUtils.isEmpty(extid_other)) {
            Ainemo.env = env;
            Ainemo.requestUriPrefix = requestUriPrefix.trim();
            Ainemo.extid_other = extid_other.trim();
            Ainemo.token_other = token_other.trim();
        }

    }

    public static String getExtid_other() {
        return extid_other;
    }

    public static String getRestUrl(String url) {
        return requestUriPrefix + url;
    }

    /**
     * 获取待签名的字符串
     *
     * @return eg.GET\ncreate_meeting\nend_time=1450923006431&enterprise_id=QYACCESSKEYIDEXAMPLE&max_participant=50&meeting_name=%E5%B0%8F%E6%98%8E%E7%9A%84%E4%BC%9A%E8%AE%AE%E5%AE%A4&require_password=true&start_time=1450923006431\n47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=
     */
    protected static String computeStringToSign(String reqMethod, String requestPath, String paramsStr, byte[] reqEntity) {
        //1. request method
        StringBuffer strToSign = new StringBuffer(reqMethod);
        strToSign.append("\n");

        //2. request path
        strToSign.append(requestPath.substring(requestUriPrefix.length()));
        strToSign.append("\n");

        //3. sorted request param and value
        strToSign.append(paramsStr);
        strToSign.deleteCharAt(strToSign.length() - 1);
        strToSign.append("\n");

        //4. request entity
        if (reqEntity.length == 0) {
            byte[] entity = DigestUtils.sha256("");
            strToSign.append(Base64.encodeBase64String(entity));
        } else {
            byte[] data = null;
            if (reqEntity.length <= 100) {
                data = reqEntity;
            } else {
                data = Arrays.copyOf(reqEntity, 100);
            }
            byte[] entity = DigestUtils.sha256(data);
            strToSign.append(Base64.encodeBase64String(entity));
        }

        String ret = strToSign.toString();
        return ret;
    }

    /**
     * 将请求参数组装成参数字符串
     *
     * @param reqParams 请求参数
     * @return 参数字符串
     */
    private static String getReqParamsStr(Map<String, String> reqParams) {
        StringBuffer paramsStr = new StringBuffer("");
        List<String> params = new ArrayList<>(reqParams.keySet());
        Collections.sort(params);
        for (String param : params) {
            paramsStr.append(param);
            paramsStr.append("=");
            try {
                paramsStr.append(URLEncoder.encode(reqParams.get(param), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                logger.error("小鱼在线请求串转化失败" + e.getMessage());
            }
            paramsStr.append("&");
        }
        return paramsStr.toString();
    }

    /**
     * 将从小鱼获得的token作为key，生成被签名串的 HMAC-SHA256签名。
     */
    private static String calculateHMAC(String data, String key) throws SignatureException {
        String result;
        try {
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes("UTF8"), HMAC_SHA256_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(data.getBytes("UTF8"));
            result = Base64.encodeBase64String(rawHmac);
        } catch (Exception e) {
            throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
        }
        return result;
    }


    /**
     * 生成签名串
     *
     * @param method    GET/POST/PUT
     * @param reqPath   请求路径，即url
     * @param paramsStr 请求参数
     * @param reqEntity 请求的body(GET请求为"",post/put为序列化后的body请求)
     * @return 签名串
     * 参考文档：http://open.ainemo.com/xiaoyu-sdk/sdk/wikis/rest-api-sign
     */
    public static String computeSignature(String method, String reqPath, String paramsStr, byte[] reqEntity, int flag) {

        String strToSign = computeStringToSign(method, reqPath, paramsStr, reqEntity);
        try {
            String mySignature = "";
            if (flag == VideoInfoConstant.VIDEO_FLAG_NGARI) {
                mySignature = calculateHMAC(strToSign, token);
            } else {
                mySignature = calculateHMAC(strToSign, token_other);
            }
            mySignature = mySignature.replace(" ", "+");
            return URLEncoder.encode(mySignature, "utf-8");
        } catch (Exception e) {
            logger.error("小鱼生成签名失败" + e.getMessage());
            return null;
        }
    }

    /**
     * 获取有入参的url
     *
     * @param method    GET/POST/PUT
     * @param reqPath   REST URL(https://www.ainemo.com/api/rest/external/v1/create_meeting)
     * @param reqParams 请求参数
     * @param reqEntity 请求的body(GET请求为"",post/put为序列化后的body请求)
     * @return
     */
    public static String getRequestUrl(String method, String reqPath, Map<String, String> reqParams, byte[] reqEntity, int flag) {
        StringBuffer urlStr = new StringBuffer(reqPath);
        urlStr.append("?");
        String paramsStr = getReqParamsStr(reqParams);
        String signature = "";
        if ("product".equalsIgnoreCase(Ainemo.env.trim())) {
            signature = computeSignature(method, reqPath, paramsStr, reqEntity, flag);

        }
        urlStr.append(paramsStr);
        urlStr.append("signature=").append(signature);

        return urlStr.toString();
    }

    /**
     * 创建小鱼会议室
     *
     * @return 返回创建结果
     * 参考文档：http://open.ainemo.com/xiaoyu-sdk/sdk/wikis/rest-api-create-conf
     * eg.{meetingNumber: 会议号, password: 密码, shareUrl:分享参会方式的链接}
     */
    public static String createMeeting(Map<String, String> params, int flag) {
        if (flag == VideoInfoConstant.VIDEO_FLAG_NGARI) {
            params.put("enterprise_id", extid);
        } else {
            params.put("enterprise_id", extid_other);
        }
        String restUrl = getRestUrl("create_meeting");
        String url = getRequestUrl("GET", restUrl, params, hashEntity, flag);
        logger.info("请求url=" + url);
        String respone = "{\"meetingNumber\":\"\"}";
        try {
            respone = HttpClientUtils.get(url);
//            respone=ctd.mvc.weixin.support.HttpClientUtils.doGet(url);
        } catch (Exception e) {
            logger.error("创建小鱼会议失败" + e.getMessage());
        }

        return respone;
    }

    /**
     * 查询云会议室号码状态
     *
     * @return 返回创建结果
     * 参考文档：http://open.ainemo.com/xiaoyu-sdk/sdk/wikis/conferenceControl
     * eg.{meetingNumber: 会议号, password: 密码, shareUrl:分享参会方式的链接}
     */
    public static String meetingInfo(String meetingRoomNumber, int flag) {
        Map<String, String> params = new HashMap<String, String>();
        if (flag == VideoInfoConstant.VIDEO_FLAG_NGARI) {
            params.put("enterpriseId", extid);
        } else {
            params.put("enterpriseId", extid_other);
        }
        String restUrl = getRestUrl("meetingInfo/" + meetingRoomNumber);
        String url = getRequestUrl("GET", restUrl, params, hashEntity, flag);
        logger.info("请求url=" + url);
        String response = "{\"meetingRoomState\":\'\'}";
        try {
            response = HttpClientUtils.get(url);
        } catch (Exception e) {
            logger.error("查询云会议室号码状态" + e.getMessage());
        }

        return response;
    }

    /**
     * 结束会议
     *
     * @return 参考文档：http://open.ainemo.com/xiaoyu-sdk/sdk/wikis/conferenceControl
     * eg.{meetingNumber: 会议号, password: 密码, shareUrl:分享参会方式的链接}
     */
    public static void conferenceControl(String meetingRoomNumber, int flag) {
        Map<String, String> params = new HashMap<String, String>();
        if (flag == VideoInfoConstant.VIDEO_FLAG_NGARI) {
            params.put("enterpriseId", extid);
        } else {
            params.put("enterpriseId", extid_other);
        }
        String restUrl = getRestUrl("conferenceControl/" + meetingRoomNumber + "/end");
        String url = getRequestUrl("PUT", restUrl, params, hashEntity, flag);
        logger.info("请求url=" + url);

        String response = null;

        HttpPut method = new HttpPut(url);
        try {
            //2016-11-24 luf:put方法没有body，不需要setEntity，删除 method.setEntity(new StringEntity(JSONUtils.toString(params), ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = httpClient.execute(method);
            int statusCode = httpResponse.getStatusLine().getStatusCode();

            HttpEntity entity = httpResponse.getEntity();
            response = EntityUtils.toString(entity);
            if (statusCode >= 300) {
                logger.error("小鱼结束会议失败======" + JSONUtils.toString(response));
//                throw new IOException(JSONUtils.toString(dao));
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("conferenceControl"+e);
        } catch (IOException e) {
           logger.error("conferenceControl"+e);
        } finally {
            method.releaseConnection();
        }
    }

    /**
     * 查询某个会议室的视频列表
     *
     * @param roomNumber
     * @param startTime         起始时间
     * @param endTime           截止时间
     * startTime必须小于endTime且若startTime不为0则时间差不能超过24小时
     * @author zhangsl 2017-02-21 14:20:32
     */
    public static String searchVodIdList(String roomNumber, Date startTime, Date endTime, int flag) {
        Map<String, String> params = new HashMap<String, String>();
        if (flag == VideoInfoConstant.VIDEO_FLAG_NGARI) {
            params.put("enterpriseId", extid);
        } else {
            params.put("enterpriseId", extid_other);
        }
        String response="";
        try {
            if (startTime != null && (startTime.after(endTime) || DateConversion.getDateAftHour(startTime, 24).before(endTime))) {
                throw new IllegalDataException("params is not valid");
            }
            params.put("startTime", startTime == null ? "" : String.valueOf(startTime.getTime()));
            params.put("endTime", endTime == null ? "" : String.valueOf(endTime.getTime()));
            String restUrl = getRestUrl("meetingroom/" + roomNumber + "/vods");
            String url = getRequestUrl("GET", restUrl, params, hashEntity, flag);
            logger.info("请求url=" + url);
            response  = HttpClientUtils.get(url);
        } catch (Exception e) {
            logger.error("查询会议室视频列表失败：" + e.getMessage());
            response="";
        }
        finally {
            return response;
        }
    }

    /**
     * 查询某段时间的视频列表
     *
     * @param vodIds 视频序号列表
     *               若startTime不为0，则startTime必须大于endTime且时间差不能超过24小时
     * @author zhangsl 2017-02-21 14:20:32
     */
    public static List<Map<String, Object>> getVodUrlList(List<Integer> vodIds, int flag) {
        List<Map<String, Object>> urlList = new ArrayList<>();
        Map<String, String> params = new HashMap<String, String>();
        if (flag == VideoInfoConstant.VIDEO_FLAG_NGARI) {
            params.put("enterpriseId", extid);
        } else {
            params.put("enterpriseId", extid_other);
        }
        for (Integer vodId : vodIds) {
            String restUrl = getRestUrl("vods/" + vodId + "/download");
            String url = getRequestUrl("GET", restUrl, params, hashEntity, flag);
            Map<String, Object> urlMap = new HashMap<String, Object>();
            urlMap.put("vodId", vodId);
            urlMap.put("url", url);
            urlList.add(urlMap);
            logger.info("请求url=" + url);
        }
        return urlList;
    }

}
