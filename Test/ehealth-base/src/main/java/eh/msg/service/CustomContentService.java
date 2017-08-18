package eh.msg.service;

import eh.msg.constant.MessagePushConstant;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;


/**
 * 自定义推送参数
 * Created by zhangx on 2016/6/29.
 */
public class CustomContentService {
    private static final Log logger = LogFactory.getLog(CustomContentService.class);

    /**
     * 系统推送消息体
     *
     * @return
     */
    public static HashMap<String, Object> getSystemCustomContent() {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_HOME;
        HashMap<String, Object> attr = null;
        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 转诊推送消息体,发送给[申请医生],跳转到详情页
     *
     * @param busid          transferId
     * @param patientRequest 是否患者申请(true是；false不是)
     * @param teams          是否团队(true是；false不是)
     * @return
     */
    public static HashMap<String, Object> getTransferCustomContentToRequest(Integer busid, Boolean patientRequest, Boolean teams) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_TRANSFER_DETAIL;
        String to = MessagePushConstant.TO_REQUEST;

        HashMap<String, Object> attr = AtyAttrService.getTransferAtyAttr(busid, to, patientRequest, teams);
        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 转诊推送消息体,发送给[目标医生],跳转到详情页
     *
     * @param busid          transferId
     * @param patientRequest 是否患者申请(true是；false不是)
     * @param teams          teams 是否团队(true是；false不是)
     * @return
     */
    public static HashMap<String, Object> getTransferCustomContentToTarget(Integer busid, Boolean patientRequest, Boolean teams) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_TRANSFER_DETAIL;
        String to = MessagePushConstant.TO_TARGET;

        HashMap<String, Object> attr = AtyAttrService.getTransferAtyAttr(busid, to, patientRequest, teams);
        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 预约推送消息体,发送给[申请医生],跳转到详情页
     *
     * @param busid          transferId
     * @param patientRequest 是否患者申请(true是；false不是)
     * @param teams          是否团队(true是；false不是)
     * @return
     */
    public static HashMap<String, Object> getAppointCustomContentToRequest(Integer busid, Boolean patientRequest) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_APPOINT_DETAIL;
        String to = MessagePushConstant.TO_REQUEST;

        HashMap<String, Object> attr = AtyAttrService.getAppointAtyAttr(busid, to, patientRequest);
        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 预约推送消息体,发送给[目标医生],跳转到详情页
     *
     * @param busid          transferId
     * @param patientRequest 是否患者申请(true是；false不是)
     * @param teams          teams 是否团队(true是；false不是)
     * @return
     */
    public static HashMap<String, Object> getAppointCustomContentToTarget(Integer busid, Boolean patientRequest) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_APPOINT_DETAIL;
        String to = MessagePushConstant.TO_TARGET;

        HashMap<String, Object> attr = AtyAttrService.getAppointAtyAttr(busid, to, patientRequest);
        return getCustomContent(actionType, activity, attr);
    }


    /**
     * 咨询推送消息体,跳转到详情页
     *
     * @param busid consultId
     * @param teams teams 是否团队(true是；false不是)
     * @return
     */
    public static HashMap<String, Object> getConsultCustomContent(Integer busid, Boolean teams) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_CONSULT_DETAIL;

        HashMap<String, Object> attr = AtyAttrService.getConsultAtyAttr(busid, teams);
        return getCustomContent(actionType, activity, attr);
    }


    /**
     * 会诊推送消息体,发送给[申请医生],跳转到详情页
     *
     * @param busid
     * @param teams 是否团队(true是；false不是)
     * @return
     */
    public static HashMap<String, Object> getMeetClinicCustomContentToRequest(Integer busid, Boolean teams) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_MEETCLINIC_DETAIL;
        String to = MessagePushConstant.TO_REQUEST;

        HashMap<String, Object> attr = AtyAttrService.getMeetClinicAtyAttr(busid, null, to, teams);
        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 会诊推送消息体,发送给[目标医生],跳转到详情页
     *
     * @param resultid
     * @param teams    是否团队(true是；false不是)
     * @return
     */
    public static HashMap<String, Object> getMeetClinicCustomContentToTarget(Integer meetid, Integer resultid, Boolean teams) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_MEETCLINIC_DETAIL;
        String to = MessagePushConstant.TO_TARGET;

        //2016-07-19 luf：由于系统消息旧数据无法处理，暂时使用会诊单号
        HashMap<String, Object> attr = AtyAttrService.getMeetClinicAtyAttr(meetid, resultid, to, teams);
        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 业务设置推送消息体,跳转到业务设置
     *
     * @param busid consultId
     * @param teams teams 是否团队(true是；false不是)
     * @return
     */
    public static HashMap<String, Object> getConsultSetCustomContent(Integer doctorId, Boolean teams) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_CONSULTSET_DETAIL;

        HashMap<String, Object> attr = AtyAttrService.getConsultSetAtyAttr(doctorId, teams);
        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 签约业务推送消息体,跳转到签约列表页
     *
     * @return
     */
    public static HashMap<String, Object> getSignCustomContent() {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_SIGN_LIST;
        HashMap<String, Object> attr = null;

        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 明日就诊推送消息体,发送给[申请医生],跳转到明日就诊列表
     *
     * @return
     */
    public static HashMap<String, Object> getTomorrowClinicCustomContent() {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_TOMORROWCLINIC_LIST;
        HashMap<String, Object> attr = null;

        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 今日就诊推送消息体,发送给[目标医生],跳转到明日就诊列表
     *
     * @return
     */
    public static HashMap<String, Object> getTodayClinicCustomContent() {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_TODAYCLINIC_LIST;
        HashMap<String, Object> attr = null;

        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 医技检查推送消息体,发送给[申请医生],跳转到医技检查详情页
     */
    public static HashMap<String, Object> getCheckCustomContent(Integer busid) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_CHECK_DETAIL;
        HashMap<String, Object> attr = AtyAttrService.getCheckAtyAttr(busid);

        return getCustomContent(actionType, activity, attr);
    }


    /**
     * 处方推送消息体,发送给[医生],跳转处方详情页
     */
    public static HashMap<String, Object> getRecipeCustomContent(Integer busid) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_RECIPE_DETAIL;
        HashMap<String, Object> attr = AtyAttrService.getRecipeAtyAttr(busid);

        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 患者关注推送消息体,发送给[医生],跳转处方详情页
     *
     * @param mpiId
     * @return
     */
    public static HashMap<String, Object> getPatientCustomContent(String mpiId) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_PATIENT_DETAIL;
        HashMap<String, Object> attr = AtyAttrService.getPatientAtyAttr(mpiId);

        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 处方待审核推送消息体，发送给【药师】(doctor的userType=5),跳转处方待审核列表页
     * zhongzx
     *
     * @return
     */
    public static HashMap<String, Object> getRecipeCheckCustomContent(String url) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_RECIPE_CHECK_LIST;
        HashMap<String, Object> attr = AtyAttrService.getUrlAtyAttr(url);

        return getCustomContent(actionType, activity, attr);
    }

    /**
     * 患者评价推送信鸽消息给医生,跳转到患者评价页面
     *
     * @return
     */
    public static HashMap<String, Object> getEvaluationCustomContent(Integer busid) {
        Integer actionType = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_PATIENT_EVALUATION_DETAIL;
        HashMap<String, Object> atyAttr = new HashMap<>();
        atyAttr.put("busid", busid);

        HashMap<String, Object> map = new HashMap<>();
        map.put("action_type", actionType);
        map.put("activity", activity);
        if (atyAttr != null) {
            map.put("aty_attr", atyAttr);
        }
        return map;
    }


    /**
     * 自定义推送参数
     *
     * @param actionType 动作类型，1打开activity或app本身
     * @param activity   指定点播模块(见MessagePushConstant.ACTION_TYPE_*)
     * @param attr       activity属性，只针对action_type=1的情况
     * @return 最终发送的消息参数
     */
    private static HashMap<String, Object> getCustomContent(Integer actionType, String activity, HashMap<String, Object> attr) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("action_type", actionType);// 动作类型，1打开activity或app本身
        map.put("activity", activity);//指定点播模块
        if (attr != null) {
            map.put("aty_attr", attr);// activity属性，只针对action_type=1的情况
        }
        return map;
    }

    public static HashMap<String, Object> getVideoCallCustomContent(String name, Integer photo, String roomId, String pwd) {
        Integer action_type = MessagePushConstant.ACTION_TYPE_OPEN_APP;
        String activity = MessagePushConstant.ACTIVITY_VIDEOCALL;
        HashMap<String, Object> attr = AtyAttrService.getVideoCallAtyAttr(name, photo, roomId, pwd);
        return getCustomContent(action_type, activity, attr);
    }
}
