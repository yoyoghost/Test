package eh.msg.service;

import ctd.persistence.DAOFactory;
import ctd.schema.exception.ValidateException;
import eh.base.constant.SystemConstant;
import eh.base.dao.DeviceDAO;
import eh.entity.base.Device;
import eh.util.MessagePushUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;


/**
 * 推送消息服务
 * Created by zhangx on 2016/6/30.
 */
public class MsgPushService {
    private static final Log logger = LogFactory.getLog(MsgPushService.class);

    /**
     * 推送信鸽推送消息,给[患者用户]推送消息
     * @param userId 用户名
     * @param content 发送消息文本内容
     * @param customContent 自定义属性，用于跳转
     * @return
     */
    public static HashMap<String,Object> pushMsgToPatient(String userId,String content,HashMap<String,Object> customContent){
        return  pushMsg(userId,SystemConstant.ROLES_PATIENT,content,customContent);
    }

    /**
     * 推送信鸽推送消息,给[患者用户]推送消息-指定提示音
     * @param userId 用户名
     * @param content 发送消息文本内容
     * @param customContent 自定义属性，用于跳转
     * @return
     */
    public static HashMap<String,Object> pushMsgToPatient(String userId,String content,HashMap<String,Object> customContent,String sound){
        return  pushMsgWithSound(userId,SystemConstant.ROLES_PATIENT,content,customContent,sound);
    }

    /**
     * 推送信鸽推送消息,给[医生用户]推送消息
     * @param userId 用户名
     * @param content 发送消息文本内容
     * @param customContent 自定义属性，用于跳转
     * @return
     */
    public static HashMap<String,Object> pushMsgToDoctor(String userId,String content,HashMap<String,Object> customContent){
        return  pushMsg(userId,SystemConstant.ROLES_DOCTOR,content,customContent);
    }

    /**
     * 推送信鸽推送消息,给[医生用户]推送消息-指定提示音
     * @param userId 用户名
     * @param content 发送消息文本内容
     * @param customContent 自定义属性，用于跳转
     * @return
     */
    public static HashMap<String,Object> pushMsgToDoctor(String userId,String content,HashMap<String,Object> customContent,String sound){
        return  pushMsgWithSound(userId,SystemConstant.ROLES_DOCTOR,content,customContent,sound);
    }

    /**
     * 推送信鸽推送消息
     * @param userId 用户名
     * @param toRole 发送的角色目标
     *               (SystemConstant.ROLES_PATIENT患者角色;SystemConstant.ROLES_DOCTOR医生角色)
     * @param content 发送消息文本内容
     * @param customContent 自定义属性，用于跳转
     * @return
     */
    private static HashMap<String,Object> pushMsg(String userId,String toRole,String content,HashMap<String,Object> customContent){
        String sound="beep.wav";
        return pushMsgWithSound(userId,toRole,content,customContent,sound);
    }


    /**
     * 推送信鸽推送消息-指定提示音
     * @param userId 用户名
     * @param toRole 发送的角色目标
     *               (SystemConstant.ROLES_PATIENT患者角色;SystemConstant.ROLES_DOCTOR医生角色)
     * @param content 发送消息文本内容
     * @param customContent 自定义属性，用于跳转
     * @return
     */
    private static HashMap<String,Object> pushMsgWithSound(String userId,String toRole,String content,HashMap<String,Object> customContent,String sound){
        DeviceDAO deviceDAO=DAOFactory.getDAO(DeviceDAO.class);

        try {
            checkParams(customContent);
        } catch (ValidateException e) {
            logger.error(e.getMessage());
            return buildFailedMsg(e.getMessage());
        }

        String appName = null;
        String roleId=null;

        if(SystemConstant.ROLES_DOCTOR.equals(toRole)){
            appName = SystemConstant.DOCTOR_APP_NAME;
            roleId=SystemConstant.ROLES_DOCTOR;
        }

        if(SystemConstant.ROLES_PATIENT.equals(toRole)){
            appName = SystemConstant.PATIENT_APP_NAME;
            roleId=SystemConstant.ROLES_PATIENT;
        }

        if (StringUtils.isEmpty(appName) || StringUtils.isEmpty(roleId)) {
            String msg="发送推送消息失败:appName="+appName+";roleId="+roleId;
            logger.error(msg);
            return buildFailedMsg(msg);
        }

        // 申请医生设备信息
        Device device = deviceDAO.getLastLoginAPP(userId,roleId);
        if(device==null){
            logger.error("the device is not existed!");
            return buildFailedMsg("the device is not existed!");
        }

/*
        HashMap<String,Object> custom=new HashMap<>();

        //自定义消息内容，参见消息定义文档
        if(customContent!=null){
            custom.put("custom_content",customContent);
        }
*/

        // 根据申请医生设备信息，选择消息推送方式
        if (device != null) {
            if (device.getOs().toLowerCase().equals("ios")) {
                MessagePushUtil.MsgPushOfIOSWithSound(userId, customContent, content,sound);
            } else {
                MessagePushUtil.MsgPushOfAND(userId, customContent,
                        appName, content);
            }
        }
        return buildSuccessMsg();
    }


    private static  boolean checkParams(HashMap<String,Object> param) throws ValidateException{
        if(param==null) return true;
        if(!param.containsKey("action_type")){
            throw new ValidateException(-1,"action_type is needed!");
        }
        if(!param.containsKey("activity")){
            throw new ValidateException(-1,"activity is needed!");
        }
        if(!param.containsKey("aty_attr")){
            throw new ValidateException(-1,"aty_attr is needed!");
        }

         return true;
    }
    private static HashMap<String,Object> buildSuccessMsg(){
        HashMap<String,Object> map=new HashMap<>();
        map.put("code",0);
        map.put("msg","success");
        return map;
    }
    private static HashMap<String,Object> buildFailedMsg(String errMsg){
        HashMap<String,Object> map=new HashMap<>();
        map.put("code",-1);
        map.put("msg",errMsg);
        return map;
    }

}
