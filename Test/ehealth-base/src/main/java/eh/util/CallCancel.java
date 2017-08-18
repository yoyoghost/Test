package eh.util;

import com.cloopen.rest.sdk.CCPRestSDK;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

public class CallCancel {

    public static final Logger log = Logger
            .getLogger(CallCancel.class);

    /**
     * 拨号取消
     *
     * @param calling
     * @param called
     * @return
     * @author LF
     */
    // public static void main(String[] args) {
    public HashMap<String, Object> SDKCallcancelCloopen(String callSid) {
        HashMap<String, Object> result = null;

        // 初始化SDK
        CCPRestSDK restAPI = new CCPRestSDK();

        // ******************************注释*********************************************
        // *初始化服务器地址和端口 *
        // *沙盒环境（用于应用开发调试）：restAPI.init("sandboxapp.cloopen.com", "8883");*
        // *生产环境（用户应用上线使用）：restAPI.init("app.cloopen.com", "8883"); *
        // *******************************************************************************
        restAPI.init("app.cloopen.com", "8883");// 初始化服务器地址和端口，格式如下，服务器地址不需要写https://

        // ******************************注释*********************************************
        // *初始化主帐号和主帐号令牌,对应官网开发者主账号下的ACCOUNT SID和AUTH TOKEN *
        // *ACOUNT SID和AUTH TOKEN在登陆官网后，在“应用-管理控制台”中查看开发者主账号获取*
        // *参数顺序：第一个参数是ACOUNT SID，第二个参数是AUTH TOKEN。 *
        // *******************************************************************************
        // restAPI.setAccount("ff8080813d8ab3ed013e04d299680478",
        // "4382c55a7cc44c22ba29de2faec6d1e0");
        // restAPI.setSubAccount("aaf98f894c273858014c2ad410510147",
        // "a9602b330c3949b8a4ffca00d778d42f");// 初始化子帐号名称和子帐号令牌
        restAPI.setSubAccount("e6544f94e24d11e48ad3ac853d9f54f2",
                "846406e6cc1d096175abf2231e9ae82b");// 初始化子帐号名称和子帐号令牌

        // ******************************注释*********************************************
        // *初始化应用ID *
        // *测试开发可使用“测试Demo”的APP ID，正式上线需要使用自己创建的应用的App ID *
        // *应用ID的获取：登陆官网，在“应用-应用列表”，点击应用名称，看应用详情获取APP ID*
        // *******************************************************************************
        // restAPI.setAppId("aaf98f894b00309b014b003149800001");
        restAPI.setAppId("aaf98f894c9d994b014cb5c02a8b1064");// 初始化应用ID

        // ******************************注释*********************************************
        // *参数一appId String 必选 应用ID
        // *参数一callSid String 必选 一个由32个字符组成的电话唯一标识符
        // *参数一type String 可选 0： 任意时间都可以挂断电话；1 ：被叫应答前可以挂断电话，其他时段返回错误代码；2：
        // 主叫应答前可以挂断电话，其他时段返回错误代码；默认值为0。
        // *应用ID的获取：登陆官网，在“应用-应用列表”，点击应用名称，看应用详情获取APP ID*
        // *******************************************************************************
        // String callingAndCalledShow = "057188890008";
        result = restAPI.CallCancel(callSid, "0");
        // System.out.println("SDKTestCallback result=" + result);
        if ("000000".equals(result.get("statusCode"))) {
            return result;
        } else {
            // 异常返回输出错误码和错误信息
            // System.out.println("错误码=" + result.get("statusCode")
            // +" 错误信息= "+result.get("statusMsg"));
            log.error(result);
        }
        return null;
    }

    /**
     * 拨号取消
     *
     * @param calling
     * @param called
     * @return
     * @author LF
     */
    // public static void main(String[] args) {
    @RpcService
    public String SDKCallcancel(String callSid) {
        try {
            String result = TelecomCall.hangup(callSid);
            JSONObject jsonObject = new JSONObject(result);
            if (null == jsonObject ||  null == jsonObject.get("rtCode") || !jsonObject.get("rtCode").equals(2000)) {
                log.error(result);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "取消电话失败");
            }
            log.info("SDKCallcancel result=" + result);
            return result;
        } catch (IOException e) {
            log.error(e);
        }
        return null;
    }
}