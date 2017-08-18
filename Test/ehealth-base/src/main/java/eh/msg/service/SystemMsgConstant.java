package eh.msg.service;

/**
 * Created by Administrator on 2016/8/5 0005.
 */
public interface SystemMsgConstant {

    /**
     * 订阅信息类型：1系统提醒，2今日就诊提醒，3明日就诊提醒，5群发记录，6患者评价, 9签约提醒
     */
    int SYSTEM_MSG_PUBLISH_TYPE_SYSTEM_REMIND = 1;
    int SYSTEM_MSG_PUBLISH_TYPE_TODAY_SEEADOCTOR_REMIND = 2;
    int SYSTEM_MSG_PUBLISH_TYPE_TOMOROW_SEEADOCTOR_REMIND = 3;
    int SYSTEM_MSG_PUBLISH_TYPE_MASS = 5;
    int SYSTEM_MSG_PUBLISH_TYPE_EVALUATION = 6;
    int SYSTEM_MSG_PUBLISH_TYPE_SIGN = 9;

    /**
     * busType 业务类型 (1转诊；2会诊；3咨询；4预约;5收入;6业务设置;7检查;11签约;12患者关注医生;13群发记录,19随访发创建人,20随访发签约医生,25急诊,26急诊——紧急转诊)
     */
    int SYSTEM_MSG_BUS_TYPE_TRANSFER = 0;
    int SYSTEM_MSG_BUS_TYPE_MEETCLINIC = 1;
    int SYSTEM_MSG_BUS_TYPE_CONSULT = 2;
    int SYSTEM_MSG_BUS_TYPE_APPOINT = 3;
    int SYSTEM_MSG_BUS_TYPE_INCOME = 4;
    int SYSTEM_MSG_BUS_TYPE_BUS_SET = 5;
    int SYSTEM_MSG_BUS_TYPE_CHECK = 6;
    int SYSTEM_MSG_BUS_TYPE_SIGN = 11;
    int SYSTEM_MSG_BUS_TYPE_PATIENT_ATTENTION_DOCTOR = 12;
    int SYSTEM_MSG_BUS_TYPE_MASS = 13;
    int SYSTEM_MSG_BUS_TYPE_FOLLOW_OWN = 19;
    int SYSTEM_MSG_BUS_TYPE_FOLLOW_SIGN = 20;
    int SYSTEM_MSG_BUS_TYPE_EMERGENCY = 25;
    int SYSTEM_MSG_BUS_TYPE_EMERGENCY_URGENT_TRANSFER = 26;

    /**
     * 接收用户类型  1医生， 2患者，统一会话号类消息则可为0
     */
    int SYSTEM_MSG_RECIEVER_TYPE_SESSION = 0;
    int SYSTEM_MSG_RECIEVER_TYPE_DOCTOR = 1;
    int SYSTEM_MSG_RECIEVER_TYPE_PATIENT = 2;

    /**
     * 系统消息类型 文本——"text"
     */
    String SYSTEM_MSG_TYPE_TEXT = "text";

    /**
     * flag        跳转标志-0申请1目标2今日目标医生3明日申请医生4特需目标5转诊接收医生
     */
    int SYSTEM_MSG_FLAG_APPLY = 0;
    int SYSTEM_MSG_FLAG_TARGET = 1;
    int SYSTEM_MSG_FLAG_TODAY_TARGET_DOCTOR = 2;
    int SYSTEM_MSG_FLAG_TOMOROW_APPLY_DOCTOR = 3;
    int SYSTEM_MSG_FLAG_SPECIAL_NEED_TARGET = 4;
    int SYSTEM_MSG_FLAG_TRANSGER_RECIEVER_DOCTOR = 5;

    /**
     * 接收设备类型，--0所有端 1原生app端 2pc端
     */
    int SYSTEM_MSG_RECIEVER_DEVICE_TYPE_ALL = 0;
    int SYSTEM_MSG_RECIEVER_DEVICE_TYPE_APP = 1;
    int SYSTEM_MSG_RECIEVER_DEVICE_TYPE_PC = 2;


}
