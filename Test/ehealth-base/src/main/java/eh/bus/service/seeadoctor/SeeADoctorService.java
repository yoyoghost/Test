package eh.bus.service.seeadoctor;

import com.alibaba.fastjson.JSONObject;
import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.bus.dao.AppointDepartDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.bus.AppointDepart;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.seeadoctor.SeeADoctorOrgan;
import eh.entity.bus.seeadoctor.SeeADoctorSmsInfo;
import eh.entity.bus.seeadoctor.SmsTemplateType;
import eh.entity.mpi.Patient;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.task.executor.AliSmsSendExecutor;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * Created by Administrator on 2016/8/3 0003.
 */
public class SeeADoctorService {
    private static final Logger log = LoggerFactory.getLogger(SeeADoctorService.class);

    /**
     * 预约就诊提醒：每天晚上7点推送给第二天有预约门诊的患者；预约等待时间小于1天的，不推送（比如，今天预约了明天的号源，则不推送消息）；
     */
    @RpcService
    public void autoRemindBeforeOneDayForAppoint(){
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecord> appointRecordList = appointRecordDAO.findAppointListWaitTimeBigThanOneDay();
        if(ValidateUtil.blankList(appointRecordList)){
            return;
        }
        for(AppointRecord ar : appointRecordList){
            try {
                pushMessageForOneDayBeforeRemind(ar);
            }catch (Exception e){
                log.info("autoRemindBeforeOneDayForAppoint push msg fail, currentAr[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(ar), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            }
        }
    }

    private void pushMessageForOneDayBeforeRemind(AppointRecord ar) {
        if(ValidateUtil.blankString(ar.getAppointOragn())){   // 患者申请
            if(ar.getMpiid().equals(ar.getAppointUser())){    // 申请人和就诊人为同一个，发微信推送
                sendWxTemplateMessage(ar);
                log.info("pushMessageForOneDayBeforeRemind wxTemplate with parameters[{}]", JSONObject.toJSONString(ar));
            }else{                                            // 申请人和就诊人不是同一个，发短信
//                sendSmsMessage(ar);
                sendSmsMessageNew(ar);
                log.info("pushMessageForOneDayBeforeRemind sms with parameters[{}]", JSONObject.toJSONString(ar));
            }
        }else{  // 医生申请
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(ar.getMpiid());
            if(ValidateUtil.blankString(patient.getLoginId())){   // 无纳里健康账号
//                sendSmsMessage(ar);
                sendSmsMessageNew(ar);
            }else{                                                // 有纳里账号
                sendWxTemplateMessage(ar);
            }
        }
    }

    private void sendWxTemplateMessage(AppointRecord ar){
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(ar.getAppointRecordId());
        smsInfo.setBusType("SeeADoctor");
        smsInfo.setSmsType("SeeADoctor");
        smsInfo.setOrganId(ar.getOrganId());
        smsInfo.setExtendValue("wx");
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }

    private void sendSmsMessage(AppointRecord ar){
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(ar.getMpiid());
        Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(ar.getOrganId());
        AppointDepart dept = DAOFactory.getDAO(AppointDepartDAO.class).getAppointDepartByOrganIDAndAppointDepartCode(ar.getOrganId(),ar.getAppointDepartId());
        Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(ar.getDoctorId());
        SeeADoctorSmsInfo smsInfo = new SeeADoctorSmsInfo();
        smsInfo.setBusId(ar.getAppointRecordId());
        smsInfo.setBusType("appointRecord");
        smsInfo.setSmsType("smsForCallANumber");
        smsInfo.setStatus(0);
        smsInfo.setOrganId(0);
        smsInfo.setMobile(patient.getMobile());
        HashMap<String, String> smsParamMap = new HashMap<>();
        String docinfo = organ.getShortName() + "" + dept.getAppointDepartName() + "" + doctor.getName() + "医生";
        smsParamMap.put("docinfo",docinfo);
        SeeADoctorOrgan sadOrgan = null;
        try {
            sadOrgan = SeeADoctorController.instance().get(String.valueOf(ar.getOrganId()));
        } catch (ControllerException e) {
            log.error("sendSmsMessage SeeADoctorController.instance().get by organId error! ar[{}]", JSONObject.toJSONString(ar));
        }
        if (sadOrgan != null && sadOrgan.isConnectHisCallNumSystem() && ValidateUtil.notNullAndZeroInteger(ar.getOrderNum())) {
            smsInfo.setSmsTemplateType(SmsTemplateType.ONE_DAY_LEFT_REMIND_APPOINT_CONNECT_HOSPITAL_CNS);
        } else {
            smsInfo.setSmsTemplateType(SmsTemplateType.ONE_DAY_LEFT_REMIND_APPOINT_NOT_CONNECT_HOSPITAL_CNS);
        }
        smsInfo.setSmsParamMap(smsParamMap);
        AliSmsSendExecutor executor = new AliSmsSendExecutor(smsInfo);
        executor.execute();

    }

    public void sendSmsMessageNew(AppointRecord ar) {
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2Ons(ar.getAppointRecordId(), ar.getOrganId(), "SeeADoctor", "SeeADoctor", null);
    }

    //获取有无对接医院的接口
    @RpcService
    public SeeADoctorOrgan getSadOrgan(String organId) {
        SeeADoctorOrgan sadOrgan = null;
        try {
            sadOrgan = SeeADoctorController.instance().get(String.valueOf(organId));
        } catch (ControllerException e) {
            log.error("sendSmsMessage SeeADoctorController.instance().get by organId error!");
        }
        return sadOrgan;
    }

}
