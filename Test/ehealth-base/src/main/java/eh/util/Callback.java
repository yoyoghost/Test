package eh.util;

import com.cloopen.rest.sdk.CCPRestSDK;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.bus.dao.CallRecordDAO;
import eh.bus.dao.ConsultDAO;
import eh.entity.bus.CallRecord;
import eh.entity.bus.Consult;
import eh.utils.LocalStringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;

public class Callback {

    public static final Logger log = Logger.getLogger(Callback.class);

    private static String HangupCdrUrl = "";
    private static String MaxCallTime = "";
    private static String CountDownTime = "";

    public static void setHangupCdrUrl(String hangupCdrUrl) {
        HangupCdrUrl = hangupCdrUrl + "/call/record";
    }

    public static void setMaxCallTime(String maxCallTime) {
        MaxCallTime = maxCallTime;
    }

    public static void setCountDownTime(String countDownTime) {
        CountDownTime = countDownTime;
    }

    /**
     * 双向拨号
     *
     * @param calling
     * @param called
     * @return
     * @author LF
     */
    @SuppressWarnings("unchecked")
    // public static void main(String[] args) {
    public String SDKCallbackCoolpen(String calling, String called) {
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
        // *参数一：主叫电话号码。被叫为座机时需要添加区号，如：01052823298；被叫为分机时分机号由‘-’隔开，如：01052823298-3627
        // *
        // *参数二:
        // 被叫电话号码。被叫为座机时需要添加区号，如：01052823298；被叫为分机时分机号由‘-’隔开，如：01052823298-3627
        // *
        // *参数三：被叫侧显示的号码，根据平台侧显号规则控制，不在平台规则内或空则显示云通讯平台默认号码。默认值空。注：被叫侧显号不能和被叫号码相同，否则显示云通讯平台默认号码。
        // *
        // *参数四：主叫侧显示的号码，根据平台侧显号规则控制，不在平台规则内或空则显示云通讯平台默认号码。默认值空。注：主叫侧显号不能和主叫号码相同，否则显示云通讯平台默认号码。
        // *
        // *参数五：wav格式的文件名，第三方自定义回拨提示音，为空则播放云通讯平台公共提示音，默认值为空。语音文件通过官网上传审核后才可使用，放音文件的格式样本如下：位速
        // 128kbps，音频采样大小16位，频道 1(单声道)， 音频采样级别 8 kHz，音频格式 PCM，这样能保证放音的清晰度。*
        // *参数六：第三方私有数据，可在鉴权通知(CallAuth)或实时话单通知接口中获取此参数。默认值为空。支持英文字母和数字，长度最大支持256字节。
        // *
        // *参数七：通话的最大时长,单位为秒。当通话时长到达最大时长则挂断通话。默认值空，不限制通话时长。优先以鉴权通知(CallAuth)响应的sessiontime参数有效。
        // *
        // *参数八：实时话单通知接口回调地址，云通讯平台将向该Url地址（必须符合URL规范，完整的url路径地址，如:http://www.cloopen.com/hangupurl
        // ）发送实时话单通知。勾选应用鉴权则此参数无效。 *
        // *应用ID的获取：登陆官网，在“应用-应用列表”，点击应用名称，看应用详情获取APP ID*
        // *******************************************************************************
        String callingAndCalledShow = "057188890008";
        result = restAPI.callback(calling, called, callingAndCalledShow,
                callingAndCalledShow, "", "", "", "", MaxCallTime, HangupCdrUrl, "", "1", CountDownTime, "");
        // System.out.println("SDKTestCallback result=" + result);
        log.info("SDKTestCallback result=" + result);
        CallRecordDAO dao = DAOFactory.getDAO(CallRecordDAO.class);
        CallRecord call = new CallRecord();
        call.setFromMobile(calling);
        call.setToMobile(called);
        call.setResult((String) result.get("statusCode"));
        call.setCreateTime(new Date());
        call.setCallTime(0);
        if ("000000".equals(result.get("statusCode"))) {
            // 正常返回输出data包体信息（map）
            HashMap<String, Object> data = (HashMap<String, Object>) result
                    .get("data");
            Set<String> keySet = data.keySet();
            for (String key : keySet) {
                Object object = data.get(key);
                // System.out.println(key + " = " + object);
                log.info(key + " = " + object);
                HashMap<String, String> callBack = (HashMap<String, String>) data
                        .get("CallBack");
                if (!StringUtils.isEmpty(callBack.get("callSid"))) {
                    call.setCallSid(callBack.get("callSid"));
                }
                dao.save(call);
                return key + " = " + object;
            }
        } else {
            // 异常返回输出错误码和错误信息
            // System.out.println("错误码=" + result.get("statusCode") + " 错误信息= "
            // + result.get("statusMsg"));
            log.error(result);
            return "错误码=" + result.get("statusCode") + " 错误信息= "
                    + result.get("statusMsg");
        }
        return null;
    }

    /**
     * 双向拨号(添加业务类型和业务ID)
     * <p>
     * eh.util
     *
     * @param calling  主叫号码
     * @param called   被叫号码
     * @param bussType 业务类型-1转诊2会诊3咨询4预约
     * @param bussId   业务ID
     * @return String
     * @author luf 2016-2-1
     */
    @SuppressWarnings("unchecked")
    // public static void main(String[] args) {
    public String SDKCallbackTwoCloopen(String calling, String called, int bussType,
                                        int bussId) {
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
        // *参数一：主叫电话号码。被叫为座机时需要添加区号，如：01052823298；被叫为分机时分机号由‘-’隔开，如：01052823298-3627
        // *
        // *参数二:
        // 被叫电话号码。被叫为座机时需要添加区号，如：01052823298；被叫为分机时分机号由‘-’隔开，如：01052823298-3627
        // *
        // *参数三：被叫侧显示的号码，根据平台侧显号规则控制，不在平台规则内或空则显示云通讯平台默认号码。默认值空。注：被叫侧显号不能和被叫号码相同，否则显示云通讯平台默认号码。
        // *
        // *参数四：主叫侧显示的号码，根据平台侧显号规则控制，不在平台规则内或空则显示云通讯平台默认号码。默认值空。注：主叫侧显号不能和主叫号码相同，否则显示云通讯平台默认号码。
        // *
        // *参数五：wav格式的文件名，第三方自定义回拨提示音，为空则播放云通讯平台公共提示音，默认值为空。语音文件通过官网上传审核后才可使用，放音文件的格式样本如下：位速
        // 128kbps，音频采样大小16位，频道 1(单声道)， 音频采样级别 8 kHz，音频格式 PCM，这样能保证放音的清晰度。*
        // *参数六：第三方私有数据，可在鉴权通知(CallAuth)或实时话单通知接口中获取此参数。默认值为空。支持英文字母和数字，长度最大支持256字节。
        // *
        // *参数七：通话的最大时长,单位为秒。当通话时长到达最大时长则挂断通话。默认值空，不限制通话时长。优先以鉴权通知(CallAuth)响应的sessiontime参数有效。
        // *
        // *参数八：实时话单通知接口回调地址，云通讯平台将向该Url地址（必须符合URL规范，完整的url路径地址，如:http://www.cloopen.com/hangupurl
        // ）发送实时话单通知。勾选应用鉴权则此参数无效。 *
        // *应用ID的获取：登陆官网，在“应用-应用列表”，点击应用名称，看应用详情获取APP ID*
        // *******************************************************************************
        String callingAndCalledShow = "057188890008";
        result = restAPI.callback(calling, called, callingAndCalledShow,
                callingAndCalledShow, "", "", "", "", MaxCallTime, HangupCdrUrl, "", "1", CountDownTime, "");
        // System.out.println("SDKTestCallback result=" + result);
        log.info("SDKTestCallback result=" + result);
        CallRecordDAO dao = DAOFactory.getDAO(CallRecordDAO.class);
        CallRecord call = new CallRecord();
        call.setFromMobile(calling);
        call.setToMobile(called);
        call.setResult((String) result.get("statusCode"));
        call.setCreateTime(new Date());
        call.setBussType(bussType);
        call.setBussId(bussId);
        call.setCallTime(0);
        if ("000000".equals(result.get("statusCode"))) {
            // 正常返回输出data包体信息（map）
            HashMap<String, Object> data = (HashMap<String, Object>) result
                    .get("data");
            Set<String> keySet = data.keySet();
            for (String key : keySet) {
                Object object = data.get(key);
                // System.out.println(key + " = " + object);
                log.info(key + " = " + object);
                HashMap<String, String> callBack = (HashMap<String, String>) data
                        .get("CallBack");
                if (!StringUtils.isEmpty(callBack.get("callSid"))) {
                    call.setCallSid(callBack.get("callSid"));
                }
                dao.save(call);
                return key + " = " + object;
            }
        } else {
            // 异常返回输出错误码和错误信息
            // System.out.println("错误码=" + result.get("statusCode") + " 错误信息= "
            // + result.get("statusMsg"));
            log.error(result);
            return "错误码=" + result.get("statusCode") + " 错误信息= "
                    + result.get("statusMsg");
        }
        return null;
    }

    /**
     * 双向拨号
     *
     * @param calling
     * @param called
     * @return
     * @author LF
     */
    @SuppressWarnings("unchecked")
    // public static void main(String[] args) {
    @RpcService
    public String SDKCallback(String calling, String called) {
        try {
            String result = TelecomCall.doCall(calling, called);
            JSONObject jsonObject = new JSONObject(result);
            if (null == jsonObject || null == jsonObject.get("rtCode") || !jsonObject.get("rtCode").equals(2000)) {
                log.error(result);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "免费电话连接失败");
            }
            String callSid = jsonObject.getString("callId");
            log.info("SDKCallback result=" + result);
            CallRecordDAO dao = DAOFactory.getDAO(CallRecordDAO.class);
            CallRecord call = new CallRecord();
            call.setFromMobile(calling);
            call.setToMobile(called);
            call.setResult(String.valueOf(jsonObject.get("rtCode")));
            call.setCreateTime(new Date());
            call.setCallTime(0);
            call.setCallSid(callSid);
            dao.save(call);
            String returnString = "CallBack = {customerSerNum=057188890008, appId=aaf98f894c9d994b014cb5c02a8b1064, fromSerNum=057188890008, dateCreated=2016-06-23 16:37:22, callSid=";
            return returnString + callSid + ", orderId=}";
        } catch (IOException e) {
           log.error(e);
        }
        return null;
    }

    /**
     * 双向拨号(添加业务类型和业务ID)
     * <p>
     * eh.util
     *
     * @param calling  主叫号码
     * @param called   被叫号码
     * @param bussType 业务类型-1转诊2会诊3咨询4预约11签约
     * @param bussId   业务ID
     * @return String
     * @author luf 2016-2-1
     */
    @SuppressWarnings("unchecked")
    // public static void main(String[] args) {
    @RpcService
    public String SDKCallbackTwo(String calling, String called, int bussType,
                                 int bussId) {
        try {
            String result = TelecomCall.doCall(calling, called);
            JSONObject jsonObject = new JSONObject(result);
            if (null == jsonObject || null == jsonObject.get("rtCode") || !jsonObject.get("rtCode").equals(2000)) {
                log.error(result);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "免费电话连接失败");
            }
            //2017-5-31 12:47:19 zhangx wx3.0 咨询流程有做修改，修改过后，电话咨询打完电话，无法完成咨询单.
            // 将此代码注释掉，安卓前端打电话获取code=200，可使咨询单状态consultStatus=1;
            // 2017-5-31 13:11:39 zhangx 电话咨询结束咨询单后，依然可以拨打电话，不应将咨询单状态限制在处理中
//            if(bussType==3){
//                ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
//                Consult consult = consultDAO.getById(bussId);
//                if(consult.getConsultStatus()!=1){
//                    throw new DAOException(ErrorCode.SERVICE_ERROR, "免费电话连接失败");
//                }
//            }
            String callSid = jsonObject.getString("callId");
            log.info("SDKCallbackTwo result=" + result);
            CallRecordDAO dao = DAOFactory.getDAO(CallRecordDAO.class);
            CallRecord call = new CallRecord();
            call.setFromMobile(calling);
            call.setToMobile(called);
            call.setResult(String.valueOf(jsonObject.get("rtCode")));
            call.setCreateTime(new Date());
            call.setBussType(bussType);
            call.setBussId(bussId);
            call.setCallTime(0);
            call.setCallSid(callSid);
            dao.save(call);
            String returnString = "CallBack = {customerSerNum=057188890008, appId=aaf98f894c9d994b014cb5c02a8b1064, fromSerNum=057188890008, dateCreated=2016-06-23 16:37:22, callSid=";
            return returnString + callSid + ", orderId=}";
        } catch (IOException e) {
            log.error(LocalStringUtil.format("SDKCallbackTwo busId[{}], busType[{}], errorMessage[{}], stacktrace[{}] ", bussId, bussType, e.getMessage(), com.alibaba.fastjson.JSONObject.toJSONString(e.getStackTrace())));
        }
        return null;
    }

}