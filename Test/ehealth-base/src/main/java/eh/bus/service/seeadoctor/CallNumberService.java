package eh.bus.service.seeadoctor;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.openservices.shade.com.alibaba.rocketmq.shade.io.netty.util.internal.StringUtil;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.his.service.QueryOrderNumService;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.QueryOrderNumRequest;
import eh.entity.bus.QueryOrderNumResponse;
import eh.entity.bus.vo.CallNumberVo;
import eh.entity.his.push.callNum.HisCallNoticeReqMsg;
import eh.entity.his.push.callNum.HisCallNumReqMsg;
import eh.entity.mpi.Patient;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.util.Constant;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.MapValueUtil;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by Administrator on 2016/8/2 0002.
 */
public class CallNumberService {
    private static final Logger log = LoggerFactory.getLogger(CallNumberService.class);

    /**
     * 查询预约叫号详细信息
     * @param map
     * @return
     */
    @RpcService
    public CallNumberVo findAppointCallNumInfo(Map<String, Object> map){
        try {
            CallNumberVo vo = new CallNumberVo();
            Integer doctorId = null;
            Integer organId = null;
            String appointDepartId = "";
            String departName = "";
            String addr = "";
            String cerId = "";
            String patientName = "";
            Date workDate = null;
            Integer orderNum = null;
            String jobNum = "";
            Integer appointRecordId = MapValueUtil.getInteger( map, "aid");
            if(null == appointRecordId){
                throw new DAOException(DAOException.VALUE_NEEDED, "appointRecordId is needed");
            }
            if(-1 != appointRecordId) {
                AppointRecord ar = DAOFactory.getDAO(AppointRecordDAO.class).getByAppointRecordId(appointRecordId);
                if(ar==null || ValidateUtil.nullOrZeroInteger(ar.getAppointRecordId())){
                    throw new DAOException("there is no record in db with id:" + appointRecordId + "!");
                }
                patientName = ar.getPatientName();
                workDate = ar.getStartTime();
                doctorId = ar.getDoctorId();
                organId = ar.getOrganId();
                appointDepartId = ar.getAppointDepartId();
                orderNum = ar.getOrderNum();
                departName = ar.getAppointDepartName();
                addr = ar.getConfirmClinicAddr();
                cerId = ar.getCertId();
                List<String> jobNumbers = DAOFactory.getDAO(EmploymentDAO.class).findJobNumberByDoctorIdAndOrganId(doctorId, organId);
                if(ValidateUtil.blankList(jobNumbers) || ValidateUtil.blankString(jobNumbers.get(0))){
                    throw new DAOException(LocalStringUtil.format("job num can not be null with requestParameters: doctorId[{}], organId[{}]"));
                }
                jobNum = jobNumbers.get(0);
            }else{
                jobNum = MapValueUtil.getString(map, "jobNum");
                organId = MapValueUtil.getInteger(map, "organId");
                if(null == organId){
                    throw new DAOException(DAOException.VALUE_NEEDED, "organId is needed");
                }
                appointDepartId = MapValueUtil.getString(map, "departCode");
                if(StringUtils.isEmpty(appointDepartId)){
                    throw new DAOException(DAOException.VALUE_NEEDED, "appointDepartId is needed");
                }
                departName = MapValueUtil.getString(map, "departName");
                orderNum = MapValueUtil.getInteger(map, "orderNum");
                workDate = MapValueUtil.getDate(map, "startTime");
                addr = MapValueUtil.getString(map, "clinicAddr");

            }


            Integer currentActualNumber = null;
            Integer remainNum = null;
            String sdfFormat = "yyyy-MM-dd";
            // 只有当天存在预约科室的才可以看到预约叫号信息。
            if(DateConversion.getDateFormatter(new Date(), sdfFormat).equals(DateConversion.getDateFormatter(workDate, sdfFormat))){
                QueryOrderNumService queryOrderNumService = new QueryOrderNumService();

                Map<String,Object> patMap = new HashMap<>();
                patMap.put("appointDepartId",appointDepartId);
                patMap.put("jobNum",jobNum);
                patMap.put("cerId",cerId);
                patMap.put("organId",organId);

                if(isCallOld(organId)){
                    currentActualNumber = queryOrderNumService.queryOrderNum(organId, appointDepartId, jobNum);
                    if(ValidateUtil.nullOrZeroInteger(currentActualNumber)){
                        log.info("currentActualNumber is null or zero with requestParameters: organId[{}], appointDepartId[{}], jobNumber[{}]", organId, appointDepartId, jobNum);
                    }else{
                        if(null != orderNum && currentActualNumber > orderNum){  // 实际就诊号大于当前预约单就诊号，则返回null,前端显示默认叫号单页
                            currentActualNumber = null;
                            remainNum = null;
                        }
                       if(currentActualNumber <= orderNum) {
                           remainNum = orderNum - currentActualNumber;
                       }
                    }
                }else{
                    QueryOrderNumRequest request = new QueryOrderNumRequest();
                    request.setOrganID(organId);
                    String organizeCode =  DAOFactory.getDAO(OrganDAO.class).getOrganizeCodeByOrganId(organId);
                    request.setOrganizeCode(organizeCode);
                    request.setCertID(cerId);
                    request.setJobNum(jobNum);
                    request.setDepartCode(appointDepartId);
                    request.setOrderNum(orderNum);
                    request.setPatientName(patientName);
                    AppointRecord ar = DAOFactory.getDAO(AppointRecordDAO.class).getByAppointRecordId(appointRecordId);
                    if(ar!=null){
                        Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(ar.getMpiid());
                        request.setGuardianFlag(patient.getGuardianFlag());
                        request.setGuardianName(patient.getGuardianName());
                        request.setMobile(patient.getMobile());
                    }
                    QueryOrderNumResponse res = queryOrderNumService.queryOrderNumNew(request);
                    currentActualNumber = res.getCurrentNum();//当前叫号
                    orderNum = res.getOrderNum();//病人的排队号
                    if(ValidateUtil.nullOrZeroInteger(currentActualNumber)){
                        log.info("currentActualNumber is null or zero with requestParameters: organId[{}], appointDepartId[{}], jobNumber[{}]", organId, appointDepartId, jobNum);
                    }else{
                        if(null != orderNum && currentActualNumber > orderNum){  // 实际就诊号大于当前预约单就诊号，则返回null,前端显示默认叫号单页
                            currentActualNumber = null;
                            remainNum = null;
                        }
                        if(currentActualNumber <=orderNum) {
                            remainNum = orderNum - currentActualNumber;
                        }
                    }
                }

            }
            Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            if(null == doctorId){
                throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is needed");
            }
            Doctor doctor = doctorDAO.getByDoctorId(doctorId);
            vo.setAppointRecordId(appointRecordId);
            vo.setHospitalName(organ.getShortName());
            vo.setDepartCode(appointDepartId);
            vo.setDoctorId(doctorId);
            vo.setOrganId(organId);
            vo.setDeptName(departName);
            vo.setDoctorName(doctor.getName());
            vo.setJobTitle(DictionaryController.instance().get("eh.base.dictionary.ProTitle").getText(doctor.getProTitle()));
            vo.setAddress(addr);
            vo.setCurrentNum(currentActualNumber);
            vo.setYourNum(orderNum);
            vo.setWorkDate(workDate);
            vo.setRemainNum(remainNum==null?null:String.valueOf(remainNum));
            if(null != workDate){
                vo.setStartTime(DateConversion.getDateFormatter(workDate, "yyyy-MM-dd HH:mm"));
            }
            return vo;
        }catch (Exception e){
            log.error("findAppointCallNumInfo error! parameter[{}], errorMessage[{}], errorStackTrace[{}]", JSONUtils.toString(map), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return null;
    }

    /**
     * 对于当天就诊的患者，排队人数<=5个的时候发微信推送或短信；通知一遍后不再推送；仅限有对接医院叫号系统的情况
     *
     */
    @RpcService
    public void autoRemindNumberInfo(){
        int startRemindHour = 7, endRemindHour = 19, midRemindHour = 12;
        //获取当前上下午时间
        Calendar calendar = Calendar.getInstance();
        int currentHourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
        Date midDatePoint = DateConversion.getCurrentDate(DateConversion.getDateFormatter(new Date(), "yyyyMMdd") + " 12:00:00", "yyyyMMdd HH:mm:ss");
        calendar.setTime(midDatePoint);
        calendar.add(Calendar.HOUR_OF_DAY, -11);
        Date startPoint = calendar.getTime();
        calendar.setTime(midDatePoint);
        calendar.add(Calendar.HOUR_OF_DAY, 11);
        Date endPoint = calendar.getTime();
        if(currentHourOfDay>startRemindHour && currentHourOfDay<midRemindHour){
            //上午
            endPoint = midDatePoint;
        }else if(currentHourOfDay>midRemindHour && currentHourOfDay<endRemindHour){
            //下午
            startPoint = midDatePoint;
        }else {
            log.info("[{}] current time is not in remind time beween startRemindHour[{}] and endRemindHour[{}]", this.getClass().getSimpleName(), startRemindHour, endRemindHour);
            return;
        }
        List<Integer> organIdList = DAOFactory.getDAO(HisServiceConfigDAO.class).findByCallNum();
        //第一步：查询出当天就诊的患者列表，条件：预约成功，当天就诊，有对接医院叫号系统，orderNum不为null且不为0，remindFlag为0或者null
        if(ValidateUtil.blankList(organIdList)){
            log.info("autoRemindNumberInfo , organIdList is null!");
            return;
        }
        List<AppointRecord> arList = DAOFactory.getDAO(AppointRecordDAO.class).findTodaysAppointRecordWithNumAndNotRemind(organIdList, startPoint, endPoint);
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        QueryOrderNumService queryOrderNumService = new QueryOrderNumService();
        for(AppointRecord ar : arList) {
            Integer organid = ar.getOrganId();
            List<String> jobNumbers = employmentDAO.findJobNumberByDoctorIdAndOrganId(ar.getDoctorId(), ar.getOrganId());
            if (ValidateUtil.blankList(jobNumbers) || ValidateUtil.blankString(jobNumbers.get(0))) {
                continue;
            }
            if(isCallOld(organid)){
                try {

                    Integer actualSequenceNum = queryOrderNumService.queryOrderNum(organid, ar.getAppointDepartId(), jobNumbers.get(0));
                    log.info("callNumberService actualSequenceNum[{}]", actualSequenceNum);
                    if (ValidateUtil.nullOrZeroInteger(actualSequenceNum) || ValidateUtil.nullOrZeroInteger(ar.getOrderNum())) {
                        continue;
                    }
                    int minusValue = ar.getOrderNum() - actualSequenceNum;
                    if (minusValue <= 5 && minusValue >= 0) {
                        //第二步：推送消息
                        pushMessageFor5Left(ar, minusValue);
                    }
                }catch (Exception e){
                    log.info("autoRemindNumberInfo push msg fail, errorMessage[{}], errorStackTrace[{}], current[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()), JSONObject.toJSONString(ar));
                }
            }else{
                QueryOrderNumRequest request = new QueryOrderNumRequest();
                request.setOrganID(ar.getOrganId());
                String organizeCode =  DAOFactory.getDAO(OrganDAO.class).getOrganizeCodeByOrganId(ar.getOrganId());
                request.setOrganizeCode(organizeCode);
                request.setCertID(ar.getCertId());
                request.setJobNum(jobNumbers.get(0));
                request.setDepartCode(ar.getAppointDepartId());
                request.setOrderNum(ar.getOrderNum());
                request.setPatientName(ar.getPatientName());
                QueryOrderNumResponse res = queryOrderNumService.queryOrderNumNew(request);
                Integer currentNum = res.getCurrentNum();
                if(currentNum==null || currentNum.intValue()==0){
                    continue;
                }
                Integer orderNum = res.getOrderNum();
                if(orderNum==null || orderNum.intValue()==0){
                    continue;
                }
                int minusValue = res.getMinusNum();
                if(minusValue<=5){
                    ar.setOrderNum(currentNum);
                    DAOFactory.getDAO(AppointRecordDAO.class).updateOrderNumByAppointRecordId(currentNum,ar.getAppointRecordId());
                    pushMessageFor5Left(ar, minusValue);
                }

            }

        }

    }

    private boolean isCallOld(Integer organid) {
        int organ = organid.intValue();
        if(organ==1 || organ==1000423 || organ==1000714|| organ==1000007){
            return true;
        }
        return false;
    }

    /**
     * 关注推送短信和微信的区别：
     * 1、若患者申请，申请人和就诊人为同一个人，给申请人发微信推送；若申请人和就诊人不是同一个人，给就诊人发短信；
     * 2、若医生申请，若就诊人有纳里健康账号发微信推送，若没有发短信；
     * @param ar
     * @param minusValue
     */
    private void pushMessageFor5Left(AppointRecord ar, int minusValue) {
        if(ValidateUtil.blankString(ar.getAppointOragn())){   // 患者申请
            if(ar.getMpiid().equals(ar.getAppointUser())){    // 申请人和就诊人为同一个，发微信推送
                sendWxTemplateMessage(ar, minusValue);
            }else{                                            // 申请人和就诊人不是同一个，发短信
                sendMsg(ar.getAppointRecordId(),minusValue);
            }
        }else{  // 医生申请
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(ar.getMpiid());
            if(ValidateUtil.blankString(patient.getLoginId())){   // 无纳里健康账号
                sendMsg(ar.getAppointRecordId(),minusValue);
            }else{                                                // 有纳里账号
                sendWxTemplateMessage(ar, minusValue);
            }
        }
    }

    public void sendWxTemplateMessage(AppointRecord ar, int minusValue){
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(ar.getAppointRecordId());
        smsInfo.setBusType("CallNumberByPat");
        smsInfo.setSmsType("CallNumberByPat");
        smsInfo.setOrganId(ar.getOrganId());
        smsInfo.setExtendValue(String.valueOf(minusValue));
        smsInfo.setExtendWithoutPersist("wx");
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }

    @RpcService
    public  void  sendMsg(Integer  recordID, int minusValue) {
        AppointRecord ar = DAOFactory.getDAO(AppointRecordDAO.class).get(recordID);
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(recordID);
        smsInfo.setBusType("CallNumberByPat");
        smsInfo.setSmsType("CallNumberByPat");
        smsInfo.setOrganId(ar.getOrganId());
        smsInfo.setExtendValue(String.valueOf(minusValue));
        smsInfo.setExtendWithoutPersist("sms");
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }

    public <T> void sendWxTemplateMessageHis(T t,String... busType){
    	if(busType.length == 0){
    		// 邵逸夫原有需求
    		sendWxTemplateMessageHis((HisCallNumReqMsg)t);
		}else{
			for(int i = 0; i < busType.length; i ++ ){
				if(!StringUtil.isNullOrEmpty(busType[i]) && busType[i].equals(Constant.WX_NOTICE_BUSTYPE)){
					sendWxNoticeMessageHis((HisCallNoticeReqMsg)t,Constant.WX_NOTICE_BUSTYPE);
				}	
				if(!StringUtil.isNullOrEmpty(busType[i]) && busType[i].equals(Constant.WX_MESSAGE_BUSTYPE)){
					sendWxNoticeMessageHis((HisCallNoticeReqMsg)t,Constant.WX_MESSAGE_BUSTYPE);
				}
			}
		}
    }

    public void sendWxNoticeMessageHis(HisCallNoticeReqMsg hisCallNoticeReqMsg,String param){
    	Patient patient = DAOFactory.getDAO(PatientDAO.class).getByIdCard(hisCallNoticeReqMsg.getIdCard());
    	if(patient == null){
    		return;
    	}
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(1); // 该字段目前不使用
        smsInfo.setBusType(param);
        smsInfo.setSmsType(param);
        smsInfo.setOrganId(hisCallNoticeReqMsg.getOrganID());
        smsInfo.setExtendValue(JSONUtils.toString(hisCallNoticeReqMsg));
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }
    //线下叫号推送
    public void sendWxTemplateMessageHis(HisCallNumReqMsg callNumReqMsg){
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord ar = null;
        if(ValidateUtil.notBlankString(callNumReqMsg.getAppointId())){
            ar = appointRecordDAO.getByOrganAppointIdAndOrganId(callNumReqMsg.getAppointId(), callNumReqMsg.getOrganID());
        }
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(ar==null?-1:ar.getAppointRecordId());
        smsInfo.setBusType("CallNumberByHis");
        smsInfo.setSmsType("CallNumberByHis");
        smsInfo.setOrganId(callNumReqMsg.getOrganID());
        smsInfo.setExtendValue(JSONUtils.toString(callNumReqMsg));
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }
    
    
}
