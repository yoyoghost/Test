package eh.bus.service.common;

import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import eh.base.constant.SystemConstant;
import eh.entity.bus.msg.*;
import eh.entity.msg.Article;
import eh.entity.msg.SmsInfo;
import eh.msg.service.SystemMsgConstant;
import eh.task.executor.AliSmsSendExecutor;
import eh.task.executor.MsgSendExecutor;
import eh.util.Easemob;

import java.util.HashMap;
import java.util.Map;

/**
 * 异步消息执行器
 * Created by Administrator on 2016/8/29 0029.
 */
public class AsyncMsgSenderExecutor {

    /**
     * >>>>>>系统消息
     *
     * 发送（添加）系统消息，默认属性：系统提醒类、文本类型、目标为医生
     *
     * @param receiverDeviceType 该系统消息对应的接收设备类型，参见SystemMsgConstant常量说明
     * @param mobile             该字段为接收用户的loginId(手机号）
     * @param article            该字段为用户接收的系统消息内容对象
     */
    public static void sendTextSystemMsgToDoctor(int receiverDeviceType, String mobile, Article article) {
        sendTextSystemMsg(SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_DOCTOR, receiverDeviceType, mobile, new Article[]{article});
    }

    /**
     * >>>>>>系统消息
     *
     * 发送（添加）系统消息，默认属性：系统提醒类、文本类型、目标为患者
     *
     * @param receiverDeviceType 该系统消息对应的接收设备类型，参见SystemMsgConstant常量说明
     * @param mobile             该字段为接收用户的loginId(手机号）
     * @param article            该字段为用户接收的系统消息内容对象
     */
    public static void sendTextSystemMsgToPatient(int receiverDeviceType, String mobile, Article article) {
        sendTextSystemMsg(SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_PATIENT, receiverDeviceType, mobile, new Article[]{article});
    }

    /**
     * >>>>>>系统消息
     *
     * 发送（添加）系统消息，默认属性：系统提醒、文本类型
     *
     * @param receiverType       该字段表示接收用户类型，参见SystemMsgConstant常量说明
     * @param receiverDeviceType 该系统消息对应的接收设备类型，参见SystemMsgConstant常量说明
     * @param mobile             该字段为接收用户的loginId(手机号）
     * @param articles           该字段为用户接收的系统消息内容对象
     */
    public static void sendTextSystemMsg(int receiverType, int receiverDeviceType, String mobile, Article... articles) {
        SystemMsg msg = new SystemMsg();
        msg.setLoginId(mobile);
        msg.setMsgType(SystemMsgConstant.SYSTEM_MSG_TYPE_TEXT);
        msg.setReceiverType(receiverType);
        msg.setPublisherId(SystemMsgConstant.SYSTEM_MSG_PUBLISH_TYPE_SYSTEM_REMIND);
        msg.setReceiverDeviceType(receiverDeviceType);
        msg.addArticles(articles);
        sendSystemMsg(msg);
    }

    /**
     * >>>>>>环信消息
     *
     * 发送环信消息给医生
     *
     * @param urt
     * @param groupId
     * @param content
     * @param ext
     */
    public static void sendEaseMobMsgToGroupByDoctorUrt(Integer urt, String groupId, String content, Map<String, String> ext) {
        EaseMobMsg msg = new EaseMobMsg();
        msg.setSender(Easemob.getDoctor(urt));
        msg.setReceiver(groupId);
        msg.setContent(content);
        msg.setExt(ext);
        sendEaseMobMsg(msg);
    }

    /**
     * >>>>>>环信消息
     *
     * 发送环信消息给患者
     *
     * @param urt
     * @param groupId
     * @param content
     * @param ext
     */
    public static void sendEaseMobMsgToGroupByPatientUrt(Integer urt, String groupId, String content, Map<String, String> ext) {
        EaseMobMsg msg = new EaseMobMsg();
        msg.setSender(Easemob.getPatient(urt));
        msg.setReceiver(groupId);
        msg.setContent(content);
        msg.setExt(ext);
        sendEaseMobMsg(msg);
    }

    /**
     * >>>>>>信鸽消息
     *
     * 发送信鸽消息给患者
     *
     * @param userId
     * @param content
     * @param customContent
     */
    public static void sendXinGeMsgToPatient(String userId, String content, HashMap<String, Object> customContent) {
        XinGeMsg msg = new XinGeMsg();
        msg.setUserId(userId);
        msg.setContent(content);
        msg.setCustomContent(customContent);
        msg.setReceiverRole(SystemConstant.ROLES_PATIENT);
        sendXinGeMsg(msg);
    }

    /**
     * >>>>>>信鸽消息
     *
     * 发送信鸽消息给医生
     *
     * @param userId
     * @param content
     * @param customContent
     */
    public static void sendXinGeMsgToDoctor(String userId, String content, HashMap<String, Object> customContent) {
        XinGeMsg msg = new XinGeMsg();
        msg.setUserId(userId);
        msg.setContent(content);
        msg.setCustomContent(customContent);
        msg.setReceiverRole(SystemConstant.ROLES_DOCTOR);
        sendXinGeMsg(msg);
    }

    /**
     * 发送短信
     *
     * @param smsInfo
     */
    public static void sendSms(SmsInfo smsInfo) {
        AliSmsSendExecutor executor = (AliSmsSendExecutor) AppContextHolder.getBean("smsSendExecutor");
        executor.setSmsInfo(smsInfo);
        executor.execute();
    }

    /**
     * 发送（添加）系统消息
     *
     * @param msg
     */
    public static void sendSystemMsg(SystemMsg msg) {
        getAndSendMsg("systemMsgSendExecutor", msg);
    }

    /**
     * 发送环信消息
     *
     * @param msg
     */
    public static void sendEaseMobMsg(EaseMobMsg msg) {
        getAndSendMsg("easeMobMsgSendExecutor", msg);
    }

    /**
     * 发送信鸽消息
     *
     * @param msg
     */
    public static void sendXinGeMsg(XinGeMsg msg) {
        getAndSendMsg("xingeMsgSendExecutor", msg);
    }

    /**
     * 发送微信消息
     *
     * @param wxMsg   通过实例化其子类WxTemplateMsg（模板消息）、WxCustomerMsg（客服消息）控制发送不同类型的微信消息，
     *                其子类封装了公众号信息、消息接受者信息、内容等等信息
     * @throws Exception
     */
    public static void sendWeixinMsg(WxMsg wxMsg) {
        WxMsg newMsg = null;
        if(wxMsg instanceof WxTemplateMsg){
            newMsg = BeanUtils.map(wxMsg, WxTemplateMsg.class);
        }else if(wxMsg instanceof WxCustomerMsg){
            newMsg = BeanUtils.map(wxMsg, WxCustomerMsg.class);
        }
        getAndSendMsg("wxMsgSendExecutor", newMsg);
    }

    private static void getAndSendMsg(String taskExecutorId, SendSucessCallbackMsg msg) {
        MsgSendExecutor executor = (MsgSendExecutor) AppContextHolder.getBean(taskExecutorId);
        executor.setMsg(msg);
        executor.execute();
    }
}
