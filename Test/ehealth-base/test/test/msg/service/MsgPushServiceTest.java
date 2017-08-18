package test.msg.service;

import ctd.util.JSONUtils;
import eh.bus.service.HisRemindService;
import eh.entity.msg.Article;
import eh.entity.msg.SessionMessage;
import eh.msg.service.CustomContentService;
import eh.msg.service.MsgPushService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by zhangx on 2016/6/20.
 */
public class MsgPushServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    /**
     * 患者评价后，给医生发送推送消息，跳转到列表页
     */
    public void testEvaluationMsg() {
        //发送推送消息
        String deviceMsg = "您有一条新的评价，请及时查看~";
        String userId = "18758263534";
        HashMap<String, Object> msgCustom = CustomContentService.getEvaluationCustomContent(1507);
        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);
        getCustomContent(msgCustom);
    }


    /**
     * 签约申请，给医生发送推送消息，跳转到列表页
     */
    public void testSendSignMsg() {
        //发送推送消息
        String deviceMsg = "您收到一条家庭医生申请消息";
        String userId = "18768177768";
        HashMap<String, Object> msgCustom = CustomContentService.getSignCustomContent();
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);


        getCustomContent(msgCustom);
    }

    /**
     * 患者管理给患者群发短信后，发送系统消息给医生
     */
    public void testSendSysMsg() {
        //发送推送消息
        String deviceMsg = "您有一条系统消息";
        String userId = "18768177768";

        HashMap<String, Object> msgCustom = CustomContentService.getSystemCustomContent();
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);


        getCustomContent(msgCustom);
    }

    /**
     * 每天五点向申请医生发推送明日待就诊患者信息
     */
    public void testSendTomorrowClinicMsg() {
        //发送推送消息
        String deviceMsg = "您今日有10位转诊患者前来就诊，请及时查看~";
        String userId = "18768177768";

        HashMap<String, Object> msgCustom = CustomContentService.getTomorrowClinicCustomContent();
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);

        getCustomContent(msgCustom);
    }

    /**
     * 每日早上七点向目标医生发推送今日待就诊患者信息
     */
    public void testSendTodayClinicMsg() {
        //发送推送消息
        String deviceMsg = "您今日有10位转诊患者前来就诊，请及时查看~";
        String userId = "18768177768";
        HashMap<String, Object> msgCustom = CustomContentService.getTodayClinicCustomContent();
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);

        getCustomContent(msgCustom);
    }

    /**
     * 患者推荐开通，给医生发送推送消息
     */
    public void testSendConsultSetMsg() {
        //发送推送消息
        String deviceMsg = "患者张三向您所在的xxx（团队名称）发送了一个请求，希望您可以开通特需预约/图文咨询业务." +
                "注：特需预约/图文咨询是向患者提供有偿的加号预约服务/咨询服务。";
        String userId = "18768177768";
        HashMap<String, Object> msgTeamCustom = CustomContentService.getConsultSetCustomContent(7444, true);//团队
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgTeamCustom);

        HashMap<String, Object> msgCustom = CustomContentService.getConsultSetCustomContent(1182, false);//个人
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);

        getCustomContent(msgTeamCustom);//{"custom_content":{"action_type":1,"aty_attr":{"teams":true,"doctorId":7444},"activity":"CONSULTSET_DETAIL"}}
        getCustomContent(msgCustom);//{"custom_content":{"action_type":1,"aty_attr":{"teams":true,"doctorId":1182},"activity":"CONSULTSET_DETAIL"}}
    }

    /**
     * 患者完成咨询单发送推送给医生
     */
    public void testSendConsultMsg() {
        //发送推送消息
        String deviceMsg = "您有一条图文咨询已完成，请及时查看~";
        String userId = "18768177768";
        HashMap<String, Object> msgTeamCustom = CustomContentService.getConsultCustomContent(1, true);//团队
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgTeamCustom);

        HashMap<String, Object> msgCustom = CustomContentService.getConsultCustomContent(1, false);//个人
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);

        getCustomContent(msgTeamCustom);//{"custom_content":{"action_type":1,"aty_attr":{"teams":true,"doctorId":7444},"activity":"CONSULTSET_DETAIL"}}
        getCustomContent(msgCustom);//{"custom_content":{"action_type":1,"aty_attr":{"teams":true,"doctorId":1182},"activity":"CONSULTSET_DETAIL"}}
    }

    /**
     * 转诊单
     */
    public void testSendTransferMsg() {
        //发送推送消息
        String deviceMsg = "您有一条转诊申请已完成，请及时查看~";
        String userId = "18768177768";
        HashMap<String, Object> msgTeamCustomToRequest = CustomContentService.getTransferCustomContentToRequest(1, false, true);//团队转诊单发送[申请医生]
        HashMap<String, Object> msgCustomToRequest = CustomContentService.getTransferCustomContentToRequest(1, false, false);//个人转诊单发送[申请医生]
        HashMap<String, Object> msgTeamCustomDocToTarget = CustomContentService.getTransferCustomContentToTarget(1, false, true);//医生团队转诊单发送[目标医生]
        HashMap<String, Object> msgTeamCustomPatToTarget = CustomContentService.getTransferCustomContentToTarget(1, true, true);//患者团队转诊单发送[目标医生]
        HashMap<String, Object> msgCustomDocToTarget = CustomContentService.getTransferCustomContentToTarget(1, false, false);//医生个人转诊单发送[目标医生]
        HashMap<String, Object> msgCustomPatToTarget = CustomContentService.getTransferCustomContentToTarget(1, true, false);//患者个人转诊单发送[目标医生]

//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);
        getCustomContent(msgTeamCustomToRequest);
        getCustomContent(msgCustomToRequest);
        getCustomContent(msgTeamCustomDocToTarget);
        getCustomContent(msgTeamCustomPatToTarget);
        getCustomContent(msgCustomDocToTarget);
        getCustomContent(msgCustomPatToTarget);
    }

    /**
     * 预约详情
     */
    public void testSendAppointRecordMsg() {
        //发送推送消息
        String deviceMsg = "您有一条预约申请失败";
        String userId = "18768177768";
        HashMap<String, Object> msgTeamCustomToRequest = CustomContentService.getAppointCustomContentToRequest(1999, false);
        HashMap<String, Object> msgCustomDocToTarget = CustomContentService.getAppointCustomContentToTarget(1, false);
        HashMap<String, Object> msgPatCustomDocToTarget = CustomContentService.getAppointCustomContentToTarget(1, true);
//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);
        getCustomContent(msgTeamCustomToRequest);
        getCustomContent(msgCustomDocToTarget);
        getCustomContent(msgPatCustomDocToTarget);
    }

    /**
     * 医技检查单
     */
    public void testSendCheckMsg() {
        //发送推送消息
        String deviceMsg = "亲，您有一份检查报告待验收哦~";
        String userId = "18768177768";
        HashMap<String, Object> msgCustom = CustomContentService.getCheckCustomContent(1);

//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);
        getCustomContent(msgCustom);
    }

    /**
     * 医技检查单
     */
    public void testSendMeetMsg() {
        //发送推送消息
        String deviceMsg = "会诊消息";
        String userId = "18768177768";
        HashMap<String, Object> singleMsgCustomToTar = CustomContentService.getMeetClinicCustomContentToTarget(1, 1, false);
        HashMap<String, Object> teamMsgCustomToTar = CustomContentService.getMeetClinicCustomContentToTarget(1, 1, true);
        HashMap<String, Object> singleMsgCustomToRequest = CustomContentService.getMeetClinicCustomContentToRequest(1, false);
        HashMap<String, Object> teamMsgCustomToRequest = CustomContentService.getMeetClinicCustomContentToRequest(1, true);

//        MsgPushService.pushMsgToDoctor(userId,deviceMsg,msgCustom);
        getCustomContent(singleMsgCustomToTar);
        getCustomContent(teamMsgCustomToTar);
        getCustomContent(singleMsgCustomToRequest);
        getCustomContent(teamMsgCustomToRequest);
    }

    /**
     * 处方
     */
    public void testSendRecipeMsg() {
        //发送推送消息
        String deviceMsg = "会诊消息";
        String userId = "18768177768";
        HashMap<String, Object> singleMsgCustomToTar = CustomContentService.getRecipeCustomContent(1212);
        getCustomContent(singleMsgCustomToTar);
    }

    private void getCustomContent(HashMap<String, Object> msgCustom) {
        HashMap<String, Object> custom = new HashMap<>();
        custom.put("custom_content", msgCustom);
        System.out.println(JSONUtils.toString(custom));
    }
}
