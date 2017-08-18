package eh.wxpay.util;

import com.alibaba.fastjson.JSONObject;
import com.thoughtworks.xstream.XStream;
import ctd.account.AccountCenter;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import eh.base.dao.DeviceDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.UserRolesDAO;
import eh.bus.service.common.AsyncMsgSenderExecutor;
import eh.bus.service.common.ClientPlatformEnum;
import eh.entity.base.Device;
import eh.entity.base.UserRoles;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Consult;
import eh.entity.bus.Transfer;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.bus.msg.WxTemplateMsg;
import eh.entity.mpi.Patient;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.PatientDAO;
import eh.msg.service.CustomContentService;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: rizenguo
 * Date: 2014/10/23
 * Time: 14:59
 */
public class Util {
    private static final Logger log = LoggerFactory.getLogger(Util.class);


    public static byte[] readInput(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = 0;
        byte[] buffer = new byte[1024];
        while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        out.close();
        in.close();
        return out.toByteArray();
    }

    public static String inputStreamToString(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int i;
        while ((i = is.read()) != -1) {
            baos.write(i);
        }
        return baos.toString();
    }


    public static InputStream getStringStream(String sInputString) {
        ByteArrayInputStream tInputStringStream = null;
        if (sInputString != null && !sInputString.trim().equals("")) {
            tInputStringStream = new ByteArrayInputStream(sInputString.getBytes());
        }
        return tInputStringStream;
    }

    public static String getStringFromMap(Map<String, Object> map, String key, String defaultValue) {
        if (StringUtils.isEmpty(key)) {
            return defaultValue;
        }
        String result = (String) map.get(key);
        if (result == null) {
            return defaultValue;
        } else {
            return result;
        }
    }

    public static int getIntFromMap(Map<String, Object> map, String key) {
        if (StringUtils.isEmpty(key)) {
            return 0;
        }
        if (map.get(key) == null) {
            return 0;
        }
        return Integer.parseInt((String) map.get(key));
    }
    
    @SuppressWarnings("rawtypes")
	public static Object getObjectFromXML(String xml, Class tClass) {
        //将从API返回的XML数据映射到Java对象
        XStream xStreamForResponseData = new XStream();
        xStreamForResponseData.alias("xml", tClass);
        xStreamForResponseData.ignoreUnknownElements();//暂时忽略掉一些新增的字段
        return xStreamForResponseData.fromXML(xml);
    }
    /**
     *  httpServletRequest -> map
     * 
     * @param httpServletRequest  httpservlet请求
     * @return
     */
    public static  Map<String, String> buildRequest(HttpServletRequest httpServletRequest) {

        Map<String, String> params = new HashMap<String, String>();
        for (Object key : httpServletRequest.getParameterMap().keySet()) {
            String keyStr = (String) key;
            params.put(keyStr, httpServletRequest.getParameter(keyStr));
        }
        return params;

    }
 // 返回串
 	public static String createResXML(String return_code, String return_msg) {
 		return "<xml><return_code><![CDATA[" + return_code
 				+ "]]></return_code><return_msg><![CDATA[" + return_msg
 				+ "]]></return_msg></xml>";

 	}

    /*
	 * 将输入流转为字符串
	 */
    public static String getStreamString(InputStream tInputStream){
        if (tInputStream != null){
            try{
                BufferedReader tBufferedReader = new BufferedReader(new InputStreamReader(tInputStream));
                StringBuffer tStringBuffer = new StringBuffer();
                String sTempOneLine = new String("");
                while ((sTempOneLine = tBufferedReader.readLine()) != null){
                    tStringBuffer.append(sTempOneLine);
                }
                return tStringBuffer.toString();
            }catch (Exception ex){
                log.error("getStreamString-->"+ex);
            }
        }
        return null;
    }

    /**
     * 获取患者urt
     * @param mpiId
     * @return
     */
    public static Integer getUrt(String mpiId){
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByMpiId(mpiId);
        String userId = patient.getLoginId();
        return getUrtByMobileForPatient(userId);
    }

    /**
     * 根据doctorId获取urt
     * @param doctorId
     * @return
     */
    public static Integer getUrtByDoctorId(Integer doctorId){
        String mobile = DAOFactory.getDAO(DoctorDAO.class).getMobileByDoctorId(doctorId);
        return getUrtForDoctor(mobile);
    }

    /**
     * 获取医生的urt信息
     * @param mobile
     * @return
     */
    public static Integer getUrtForDoctor(String mobile){
        if(StringUtils.isEmpty(mobile)){
            return null;
        }
        User user = null;
        try {
            user = AccountCenter.getUser(mobile);
            if (user != null) {
                List<UserRoleToken> urts = user.findUserRoleTokenByRoleId("doctor");
                UserRoleTokenEntity ure = null;
                if (urts.size() > 0) {
                    ure = (UserRoleTokenEntity) urts.get(0);
                    return ure.getId();
                }
            }
        } catch (ControllerException e) {
            log.error("getUrtForDoctor:"+e.getMessage());
        }
        return null;
    }

    public static Integer getUrtByMobileForPatient(String mobile){
        if(ValidateUtil.blankString(mobile)){
            return null;
        }
        try{
            List<UserRoles> userRolesList = DAOFactory.getDAO(UserRolesDAO.class).findUrtByUserId(mobile);
            if(ValidateUtil.blankList(userRolesList)){
                return null;
            }
            for(UserRoles ur : userRolesList){
                if("patient".equals(ur.getRoleId())){
                    return ur.getId();
                }
            }
        }catch (Exception e){
            log.error("util getUrtByMobileForPatient exception, mobile[{}], errorMessage[{}], stackTrace[{}]", mobile, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return null;
    }

    private static void sendWxOrXingeMsgToPatient(WxTemplateMsg msg, Integer deviceId, String mpiId, HashMap<String, Object> msgCustom, String methodName){
        if(ValidateUtil.notNullAndZeroInteger(deviceId)){
            Device device = DAOFactory.getDAO(DeviceDAO.class).getDeviceById(deviceId);
            SimpleWxAccount wxAccount = (SimpleWxAccount) ClientPlatformEnum.WEIXIN.parsePlatformInfoFromDevice(device);
            if(wxAccount!=null){
                // 发送微信模板消息
                msg.setAppId(wxAccount.getAppId());
                msg.setOpenId(wxAccount.getOpenId());
                AsyncMsgSenderExecutor.sendWeixinMsg(msg);
                log.info("util [{}] weixin success, deviceId[{}], mpiId[{}], msg[{}]", methodName, deviceId, mpiId, JSONObject.toJSONString(msg));
                return;
            }
        }
        // 发送信鸽消息给用户
        String xingeContent = msg.getFirst() + "\n" + msg.getRemark();
        Patient dbPatient = DAOFactory.getDAO(PatientDAO.class).get(mpiId);
        AsyncMsgSenderExecutor.sendXinGeMsgToPatient(dbPatient.getLoginId(), xingeContent, msgCustom);
        log.info("util [{}] xinge success, deviceId[{}], mpiId[{}], msg[{}]", methodName, deviceId, mpiId, JSONObject.toJSONString(msg));
    }

    /**
     * 发送消息（微信或信鸽消息）给咨询患者
     * @param consult
     * @param msg
     */
    public static void sendWxOrXingeMsgToPatientForConsult(Consult consult, WxTemplateMsg msg){
        HashMap<String, Object> msgCustom = CustomContentService.getConsultCustomContent(consult.getConsultId(), ValidateUtil.isTrue(consult.getTeams()));
        sendWxOrXingeMsgToPatient(msg, consult.getDeviceId(), consult.getRequestMpi(), msgCustom, "sendWxOrXingeMsgToPatientForConsult");
    }

    /**
     * 发送消息（微信或信鸽消息）给预约患者
     * @param appointRecord
     * @param msg
     */
    public static void sendWxOrXingeMsgToPatientForAppoint(AppointRecord appointRecord, WxTemplateMsg msg){
        HashMap<String, Object> msgCustom = CustomContentService.getAppointCustomContentToRequest(appointRecord.getAppointRecordId(), true);
        sendWxOrXingeMsgToPatient(msg, appointRecord.getDeviceId(), appointRecord.getMpiid(), msgCustom, "sendWxOrXingeMsgToPatientForAppoint");
    }

    /**
     * 发送消息（微信或信鸽消息）给转诊患者
     * @param transfer
     * @param msg
     */
    public static void sendWxOrXingeMsgToPatientForTransfer(Transfer transfer, WxTemplateMsg msg){
        HashMap<String, Object> msgCustom = CustomContentService.getTransferCustomContentToRequest(transfer.getTransferId(), true, ValidateUtil.isTrue(transfer.getTeams()));
        sendWxOrXingeMsgToPatient(msg, transfer.getDeviceId(), transfer.getRequestMpi(), msgCustom, "sendWxOrXingeMsgToPatientForTransfer");
    }

    /**
     * 发送消息（微信或信鸽消息）给签约患者
     * @param sign
     * @param msg
     */
    public static void sendWxOrXingeMsgToPatientForSign(SignRecord sign, WxTemplateMsg msg){
        HashMap<String, Object> msgCustom = CustomContentService.getSignCustomContent();
        sendWxOrXingeMsgToPatient(msg, sign.getDeviceId(), sign.getFromMpiId(), msgCustom, "sendWxOrXingeMsgToPatientForSign");
    }


    /**
     * 发送微信推送消息给该患者对应的最后登录的微信公众号
     * @param mpiId
     * @param msg
     * @return
     */
    public static boolean sendWxTemplateMsgToMpiIdWithLatestWxDevice(String mpiId, WxTemplateMsg msg){
        if(ValidateUtil.blankString(mpiId)){
            log.info("Util.sendWxTemplateMsgToMpiIdWithLatestWxDevice mpiId is null! mpiId:{}", mpiId);
            return false;
        }
        try{
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(mpiId);
            if(patient==null || ValidateUtil.blankString(patient.getLoginId())){
                log.info("Util.sendWxTemplateMsgToMpiIdWithLatestWxDevice patient or patient.loginId is null! requestParameters: mpiId[{}], patient[{}]", mpiId, JSONObject.toJSONString(patient));
                return false;
            }
            String userId = patient.getLoginId();
            Integer urt = getUrtByMobileForPatient(userId);
            if(ValidateUtil.nullOrZeroInteger(urt)){
                log.info("Util.sendWxTemplateMsgToMpiIdWithLatestWxDevice urt is null! requestParameters: mpiId[{}]", mpiId);
                return false;
            }
            List<Device> deviceList = DAOFactory.getDAO(DeviceDAO.class).findAvailableUserDeviceListOrderByLastModifyDesc(userId, urt, ClientPlatformEnum.WEIXIN.getKey());
            if(ValidateUtil.blankList(deviceList)){
                log.info("Util.sendWxTemplateMsgToMpiIdWithLatestWxDevice deviceList is null! requestParameters: mpiId[{}], urt[{}], loginId[{}]", mpiId, urt, userId);
                return false;
            }
            SimpleWxAccount wxAccount = (SimpleWxAccount) ClientPlatformEnum.WEIXIN.parsePlatformInfoFromDevice(deviceList.get(0));
            if(wxAccount==null){
                log.info("Util.sendWxTemplateMsgToMpiIdWithLatestWxDevice wxAccount is null, deviceList[{}]", JSONObject.toJSONString(deviceList));
                return false;
            }else {
                msg.setAppId(wxAccount.getAppId());
                msg.setOpenId(wxAccount.getOpenId());
                AsyncMsgSenderExecutor.sendWeixinMsg(msg);
                log.info("Util.sendWxTemplateMsgToMpiIdWithLatestWxDevice success mpiId[{}], wxAccount[{}]", mpiId, wxAccount);
            }

        }catch (Exception e){
            log.error("Util.sendWxTemplateMsgToMpiIdWithLatestWxDevice error! requestParameters: mpiId[{}]; errorMessage[{}], errorStackTrace[{}]", mpiId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            return false;
        }
        return true;
    }
}

