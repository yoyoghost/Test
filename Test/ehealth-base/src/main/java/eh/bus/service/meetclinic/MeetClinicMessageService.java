package eh.bus.service.meetclinic;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.MsgTypeEnum;
import eh.bus.dao.MeetClinicMessageDAO;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicMsg;
import eh.entity.bus.MeetMsgBody4MQ;
import eh.entity.bus.PatientDiseaseDescForMC;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2016/11/18 0018.
 */
public class MeetClinicMessageService {
    private static final Logger log = LoggerFactory.getLogger(MeetClinicMessageService.class);

    @RpcService
    public void handleDoctorMsg(MeetMsgBody4MQ meetMsgBody){
        log.info("handleDoctorMsg start in, meetMsgBody[{}]", JSONObject.toJSONString(meetMsgBody));
        if(meetMsgBody==null){
            log.error("handleDoctorMsg necessary param null");
            return;
        }
        try{
            MeetClinicMsg msg = new MeetClinicMsg();
            msg.setMeetClinicId(meetMsgBody.getMeetClinicId());
            msg.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
            msg.setSenderId(String.valueOf(meetMsgBody.getDoctorId()));
            msg.setReceiverRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
            msg.setMsgType(Short.valueOf(meetMsgBody.getMsgType()));
            msg.setMsgContent(meetMsgBody.getMsgContent());
            msg.setHasRead(true);
            msg.setDeleted(0);
            msg.setCreateTime(new Date());
            msg.setSendTime(new Date());
            MeetClinicMessageDAO meetClinicMessageDAO = DAOFactory.getDAO(MeetClinicMessageDAO.class);
            meetClinicMessageDAO.save(msg);
        }catch (Exception e){
            log.error("handleDoctorMsg error, meetMsgBody[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(meetMsgBody), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
    }

    /**
     * 发起会诊时，插入患者会诊病情描述信息
     * @param mc
     */
    public void defaultHandleWhenPatientStartMeetClinic(MeetClinic mc){
        try {
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getPatientByMpiId(mc.getMpiid());
            List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(CdrOtherdocDAO.class)
                    .findByClinicTypeAndClinicId(2, mc.getMeetClinicId());
            PatientDiseaseDescForMC pdd = new PatientDiseaseDescForMC();
            pdd.setName(patient.getPatientName());
            pdd.setAge(DateConversion.getAge(patient.getBirthday()) + "岁");
            pdd.setSex(patient.getPatientSex());
            pdd.setDesc(mc.getPatientCondition());
            pdd.setMeetClinicId(mc.getMeetClinicId());
            pdd.setDiagnosDesc(mc.getDiagianName());
            if (ValidateUtil.blankList(cdrOtherdocs)) {
                pdd.setImgs("");
            } else {
                pdd.setImgs(String.valueOf(cdrOtherdocs.get(0).getDocContent()));
            }
            MeetMsgBody4MQ tMsg = new MeetMsgBody4MQ();
            tMsg.setMeetClinicId(mc.getMeetClinicId());
            tMsg.setMsgType(MsgTypeEnum.PATIENT_DISEASE_DESCRIPTION.getId()+"");
            tMsg.setMsgContent(JSONObject.toJSONString(pdd));
            tMsg.setDoctorId(mc.getRequestDoctor());
            tMsg.setSendTime(new Date());
            tMsg.setCreateTime(new Date());
            tMsg.setHxMsgId(null);
            tMsg.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
            this.handleDoctorMsg(tMsg);
        } catch (Exception e){
            log.error(LocalStringUtil.format("defaultHandleWhenPatientStartMeetClinic error! meetClinic[{}],errorMessage[{}],stackTrace0[{}]", JSONObject.toJSONString(mc), e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
    }

}
