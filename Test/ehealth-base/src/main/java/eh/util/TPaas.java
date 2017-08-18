package eh.util;

import ctd.mvc.support.HttpClientUtils;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.apache.axis.encoding.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;
import javax.xml.rpc.ServiceException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;


/**
 * 已废弃
 */
@Deprecated
public class TPaas {
    private static final Logger logger = Logger.getLogger(TPaas.class);

    private static HttpClient httpClient;
    //    private static String requestUriPrefix = "https://tpm.onecc.me/tpaas/api/v3";
//    private static String wsdlUrl = "http://115.236.178.228:12340/esb/webservice";
//    private static String duration = "300";
    private static String pin = "123456";
    private static String statusFeedback = "";
    private static String requestUriPrefix = "";
    private static String wsdlUrl = "";
    private static String duration = "";
    private static String adminUserAndPwd = "nljk_admin@onecc.me:135246";

    static {
        HttpClientUtils httpClientUtils = AppContextHolder.getBean("httpClientUtils", HttpClientUtils.class);
        httpClient = httpClientUtils.getHttpClient();
    }

    public static void setStatusFeedback(String statusFeedback) {
        TPaas.statusFeedback = statusFeedback + "/videoCall/feedback";
    }

    public static void setRequestUriPrefix(String requestUriPrefix) {
        TPaas.requestUriPrefix = requestUriPrefix;
    }

    public static void setWsdlUrl(String wsdlUrl) {
        TPaas.wsdlUrl = wsdlUrl;
    }

    public static void setDuration(String duration) {
        TPaas.duration = duration;
    }

    /**
     * 创建多方视频通话
     *
     * @param params
     * @return
     */
    public static String videoCall(Map<String, String> params) {
        params.put("duration", duration);
        params.put("statusFeedback", statusFeedback);
        params.put("pin", pin);
        String response = "{\"success\":false}";
        String url = requestUriPrefix + "/videocall";
        HttpPost method = new HttpPost(url);
        method.setHeader("Accept", "application/json");
        method.setHeader("Content-Type", "application/json;charset=" + "UTF-8".toLowerCase());
        method.setHeader("Authorization", "Basic " + Base64.encode(adminUserAndPwd.getBytes()));
        try {
            method.setEntity(new StringEntity(JSONUtils.toString(params), ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = null;
            httpResponse = httpClient.execute(method);
            HttpEntity entity = httpResponse.getEntity();
            response = EntityUtils.toString(entity);
        } catch (UnsupportedEncodingException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } finally {
            method.releaseConnection();
        }
        return response;
    }

    /**
     * 呼叫终端
     *
     * @param params
     * @return
     */
    public static void callAddress(String params, String videoCallId) {
        String response = "{\"success\":false}";
        String url = requestUriPrefix + "/videocall/" + videoCallId + "/terminal/call";
        HttpPost method = new HttpPost(url);
        method.setHeader("Accept", "application/json");
        method.setHeader("Content-Type", "application/json;charset=" + "UTF-8".toLowerCase());
        method.setHeader("Authorization", "Basic " + Base64.encode(adminUserAndPwd.getBytes()));
        try {
            method.setEntity(new StringEntity(params, ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = null;
            httpResponse = httpClient.execute(method);
            int statusCode = httpResponse.getStatusLine().getStatusCode();
            HttpEntity entity = httpResponse.getEntity();
            response = EntityUtils.toString(entity);
            if (statusCode >= 300) {
                throw new IOException(JSONUtils.toString(response));
            }
        } catch (UnsupportedEncodingException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } finally {
            method.releaseConnection();
        }
    }

    /**
     * 多方视频通话状态查询
     *
     * @param videoCallID
     * @return
     */
    public static String getVideoCallStatus(String videoCallID) {
        String response = "{\"success\":false}";
        String url = requestUriPrefix + "/videocall/" + videoCallID + "/status";
        HttpGet method = new HttpGet(url);
        method.setHeader("Accept", "application/json");
        method.setHeader("Content-Type", "application/json;charset=" + "UTF-8".toLowerCase());
        method.setHeader("Authorization", "Basic " + Base64.encode(adminUserAndPwd.getBytes()));
        try {
            HttpResponse httpResponse = httpClient.execute(method);
            HttpEntity entity = httpResponse.getEntity();
            response = EntityUtils.toString(entity, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } finally {
            method.releaseConnection();
        }
        return response;
    }

    public static void screenControl(String videoCallID, int index) {
        String url = requestUriPrefix + "/videocall/" + videoCallID + "/screen/control";
        HashMap<String, Object> req = new HashMap<String, Object>();
        req.put("index", index);
        logger.info("url=============" + url + "====================req====================" + JSONUtils.toString(req));
        String response = null;
        HttpPut method = new HttpPut(url);
        method.setHeader("Accept", "application/json");
        method.setHeader("Content-Type", "application/json;charset=" + "UTF-8".toLowerCase());
        method.setHeader("Authorization", "Basic " + Base64.encode(adminUserAndPwd.getBytes()));
        try {
            method.setEntity(new StringEntity(JSONUtils.toString(req), ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = httpClient.execute(method);
            int statusCode = httpResponse.getStatusLine().getStatusCode();

            HttpEntity entity = httpResponse.getEntity();
            response = EntityUtils.toString(entity);
            if (statusCode >= 300) {
                throw new IOException(JSONUtils.toString(response));
            }
        } catch (UnsupportedEncodingException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } finally {
            method.releaseConnection();
        }
    }

    public static void endCall(String videoCallId) {
        String url = requestUriPrefix + "/videocall/" + videoCallId + "/end";

        String response = null;
        Map<String, Object> request = new HashMap<String, Object>();
        request.put("videoCallID", videoCallId);
        HttpPut method = new HttpPut(url);
        method.setHeader("Accept", "application/json");
        method.setHeader("Content-Type", "application/json;charset=" + "UTF-8".toLowerCase());
        method.setHeader("Authorization", "Basic " + Base64.encode(adminUserAndPwd.getBytes()));
        try {
            method.setEntity(new StringEntity(JSONUtils.toString(request), ContentType.APPLICATION_JSON));
            HttpResponse httpResponse = httpClient.execute(method);
            int statusCode = httpResponse.getStatusLine().getStatusCode();

            HttpEntity entity = httpResponse.getEntity();
            response = EntityUtils.toString(entity);
            if (statusCode >= 300) {
                throw new IOException(JSONUtils.toString(response));
            }
        } catch (UnsupportedEncodingException e) {
            logger.error(e);
        } catch (IOException e) {
            logger.error(e);
        } finally {
            method.releaseConnection();
        }
    }

    public static String bossWebService(Document requestXML) throws IOException {
        String request = requestXML.asXML();
        //WSDL地址
//        String wsdlUrl = "http://115.236.178.228:12340/esb/webservice";
        //看具体响应的WSDL中的namespace
        String nameSpaceUri = "http://thirdparty.manage.esb.mvno.congxing.com/";
        Service service = new Service();
        Call call = null;
        try {
            call = (Call) service.createCall();
        } catch (ServiceException e) {
            logger.error(e);
        }
        call.setTargetEndpointAddress(wsdlUrl);
        //设置operation 名称，
        call.setOperationName(new QName(nameSpaceUri, "invoke"));
        //设置账户，注意参数为XSD_STRRING
        call.addParameter("xml", org.apache.axis.Constants.XSD_STRING,
                ParameterMode.IN);
        //设置返回类型为对象数组
        call.setReturnClass(String.class);
        String response = (String) call.invoke(new Object[]{request});
        return response;
    }

//    public static void main(String[] args) {
//        String url = "https://dev.zaijia.cn/api/rest/external/v1/create_meeting?enterprise_id=346d569fc78988f82ec183111ded661761f20aea&meeting_name=%E6%82%A3%E8%80%85%E6%9D%A8%E6%96%87%E5%AD%98%E7%9A%84%E8%BF%9C%E7%A8%8B%E4%BA%91%E9%97%A8%E8%AF%8A&require_password=false&start_time=1475138695309&signature=";
//        String url = "https://tpm.onecc.me/tpaas/api/v3/videocall/0000000/status";
//        new HttpClientUtils();
//
//        try {
//            System.out.println(HttpClientUtils.get(url));
//        } catch (IOException var3) {
//            var3.printStackTrace();
//        }
//
//    }

//    public static void main(String[] args) {
//
//        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
//                "<ContractRoot>\n" +
//                "    <operator_id>testuser</operator_id>\n" +
//                "    <operator_pwd>testpasswd</operator_pwd>\n" +
//                "    <serial_number>20140124201122499web8888888</serial_number>\n" +
//                "    <timestamp>20140124201122</timestamp>\n" +
//                "    <channel>web</channel>\n" +
//                "    <ret_type>xml</ret_type>\n" +
//                "    <service_name>cci.uam.AuthSign</service_name>\n" +
//                "    <platform_type>CCI</platform_type>\n" +
//                "    <request_data>\n" +
//                "        <chkAuthPersonRequst>\n" +
//                "            <adminUserName>nali_admin</adminUserName>\n" +
//                "            <custType></custType>\n" +
//                "            <verfityType>TYPEB</verfityType>\n" +
//                "            <pswd>135246</pswd>\n" +
//                "            <backGroundFlag>true</backGroundFlag>\n" +
//                "        </chkAuthPersonRequst>\n" +
//                "    </request_data>\n" +
//                "</ContractRoot>\n";
//        //WSDL地址
//        String wsdlUrl = "http://115.236.178.228:12340/esb/webservice";
//        //看具体响应的WSDL中的namespace
//        String nameSpaceUri = "http://thirdparty.manage.esb.mvno.congxing.com/";
//        Service service = new Service();
//        Call call = null;
//        try {
//            call = (Call) service.createCall();
//        } catch (ServiceException e) {
//            e.printStackTrace();
//        }
//        call.setTargetEndpointAddress(wsdlUrl);
//        //设置operation 名称，
//        call.setOperationName(new QName(nameSpaceUri, "invoke"));
//        //设置账户，注意参数为XSD_STRRING
//        call.addParameter("xml", org.apache.axis.Constants.XSD_STRING,
//                ParameterMode.IN);
//        //设置返回类型为对象数组
//        call.setReturnClass(String.class);
//        String dao = null;
//        try {
//            dao = (String) call.invoke(new Object[]{request});
//            System.out.println(dao);
//        } catch (RemoteException e) {
//            e.printStackTrace();
//        }
//    }
}
