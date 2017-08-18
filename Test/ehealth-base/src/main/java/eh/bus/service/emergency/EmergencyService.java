package eh.bus.service.emergency;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.service.DoctorInfoService;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.EmergencyConstant;
import eh.bus.dao.EmergencyDao;
import eh.bus.dao.EmergencyDoctorDao;
import eh.bus.dao.TransferDAO;
import eh.bus.push.MessagePushExecutorConstant;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.bus.*;
import eh.entity.mpi.Patient;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.util.Callback;
import eh.utils.DateConversion;
import eh.utils.ValidateUtil;
import eh.utils.params.support.DBParamLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2017/6/2 0002.
 *
 */
@SuppressWarnings({"unused", "all"})
public class EmergencyService {
    private static final Logger log = LoggerFactory.getLogger(EmergencyService.class);
    private static EmergencyDao emergencyDao = DAOFactory.getDAO(EmergencyDao.class);
    private static EmergencyDoctorDao emergencyDoctorDao = DAOFactory.getDAO(EmergencyDoctorDao.class);
    private AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);
    private SmsPushService smsPushService = AppContextHolder.getBean("smsPushService", SmsPushService.class);
    private static DBParamLoader paramLoader = AppContextHolder.getBean("paramLoader", DBParamLoader.class);

    /**
     * 红色按钮请求
     * @return map#code
     */
    @RpcService
    public Map<String, Object> redButtonRequest(){
        Map<String, Object> resultMap = Maps.newHashMap();
        try {
            // 第一步，检查当前患者是否有未支付的急诊单
            Emergency emergency = getUnPayEmergencyOrder();
            if(emergency!=null){
                resultMap.put("code", 1);
                resultMap.put("emergency", emergency);
                return resultMap;
            }
            // 第二步，自动匹配合适医生并返回
            List<SimpleEmergencyDoctor> doctorList = findDoctorListForEmergency();
            resultMap.put("code", 0);
            resultMap.put("doctor", doctorList.get(0));
            return resultMap;
        }catch (Exception e){
            log.error("clickRedButton error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "拨打失败");
        }
    }

    /**
     * 获取急诊医生列表，按优先级排序
     * @return SimpleEmergencyDoctor列表
     */
    @RpcService
    public List<SimpleEmergencyDoctor> findDoctorListForEmergency(){
        try{
            Patient patient = currentPatient();
            return tempDoctorList();
        }catch (Exception e){
            log.error("clickRedButton error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "拨打失败");
        }
    }

    public List<SimpleEmergencyDoctor> tempDoctorList(){
        List<SimpleEmergencyDoctor> seDoctorList = Lists.newArrayList();
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        int[] fixedDoctorIds = getFixedDoctorIds();
        for (int doctorId : fixedDoctorIds){
            Doctor doctor = doctorDao.getByDoctorId(doctorId);
            if(doctor==null){
                log.error("tempDoctorList doctor not exist with doctorId[{}]", doctorId);
                continue;
            }
            SimpleEmergencyDoctor seDoctor = BeanUtils.map(doctor, SimpleEmergencyDoctor.class);
            seDoctor.setEmergencyPrice(EmergencyPriceCalculator.getPrice());
            DoctorInfoService doctorInfoService = AppContextHolder.getBean("doctorInfoService", DoctorInfoService.class);
            ConsultSet doctorSet = doctorInfoService.getNoDisCountSet(seDoctor.getDoctorId());
            seDoctor.setConsultPrice(doctorSet.getAppointConsultPrice());
            seDoctorList.add(seDoctor);
        }
        return seDoctorList;
    }

    /**
     * 急诊患者呼叫接口
     * @param doctorId
     * @return
     */
    @RpcService
    public String emergencyCall(Integer doctorId){
        try{
            Patient patient = currentPatient();
            List<Emergency> emergencyList = emergencyDao.findTemporaryEmergencyByRequestMpi(patient.getMpiId(), EmergencyConstant.EMERGENCY_STATUS_TEMPORARY);
            Emergency emergency = saveOrUpdateEmergency(patient, emergencyList);
            Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(doctorId);
            String callerMobile = patient.getMobile();
            String calledMobile = doctor.getMobile();
            Callback callback = AppContextHolder.getBean("callback", Callback.class);
            String result = callback.SDKCallbackTwo(callerMobile, calledMobile, EmergencyConstant.CALLRECORD_BUSSTYPE_PATIENT_CALL_DOCTOR, emergency.getEmergencyId());
            log.info("emergencyCall result[{}]", result);
            // TODO 临时方案
            afterDoctorAcceptEmergency(emergency.getEmergencyId(), calledMobile);
            log.info("communityDoctor[{}] accept emergency[{}]", JSONObject.toJSONString(doctor), JSONObject.toJSONString(emergency));
            return result;
        }catch (Exception e){
            log.error("call error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "拨打失败");
        }
    }

    /**
     * 社区医生给医院医生拨打电话
     * @param emergencyDoctorId
     * @param hospitalDoctorId
     * @return
     */
    @RpcService
    public String communityDoctorCallHospitalDoctor(Integer emergencyDoctorId, Integer hospitalDoctorId){
        log.info("communityDoctorCallHospitalDoctor start in with params: emergencyDoctorId[{}], hospitalDoctorId[{}]", emergencyDoctorId, hospitalDoctorId);
        if(ValidateUtil.nullOrZeroInteger(emergencyDoctorId) || ValidateUtil.nullOrZeroInteger(hospitalDoctorId)){
            log.error("communityDoctorCallHospitalDoctor requestParam null, please check!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try{
            Doctor communityDoctor = currentDoctor();
            EmergencyDoctor emergencyDoctor = emergencyDoctorDao.get(emergencyDoctorId);
            if(emergencyDoctor==null){
                log.error("communityDoctorCallHospitalDoctor input emergency not exists, emergencyDoctorId[{}]", emergencyDoctorId);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "急诊单不存在");
            }
            Doctor hospitalDoctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(hospitalDoctorId);
            if(hospitalDoctor==null){
                log.error("communityDoctorCallHospitalDoctor hospitalDoctor not exists, hospitalDoctorId[{}]", hospitalDoctorId);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "急诊单不存在");
            }
            String callerMobile = communityDoctor.getMobile();
            String calledMobile = hospitalDoctor.getMobile();
            Callback callback = AppContextHolder.getBean("callback", Callback.class);
            String result = callback.SDKCallbackTwo(callerMobile, calledMobile, EmergencyConstant.CALLRECORD_BUSSTYPE_C_DOCTOR_CALL_H_DOCTOR, emergencyDoctor.getEmergencyId());
            log.info("communityDoctorCallHospitalDoctor result[{}]", result);
            // TODO 临时方案
            convertToUrgentTransfer(emergencyDoctor.getEmergencyId(), calledMobile);
            log.info("hospitalDoctor[{}] accept emergencyDoctor[{}] for urgentTransfer", JSONObject.toJSONString(hospitalDoctor), JSONObject.toJSONString(emergencyDoctor));
            return result;
        }catch (Exception e){
            log.error("communityDoctorCallHospitalDoctor error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "拨打失败");
        }
    }

    /**
     * 医生给患者拨打电话
     * @param emergencyDoctorId
     * @return
     */
    @RpcService
    public String doctorCallPatient(Integer emergencyDoctorId){
        log.info("doctorCallPatient start in with params: emergencyDoctorId[{}]", emergencyDoctorId);
        if(ValidateUtil.nullOrZeroInteger(emergencyDoctorId)){
            log.error("doctorCallPatient requestParam null, please check!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try{
            Doctor hospitalDoctor = currentDoctor();
            EmergencyDoctor emergencyDoctor = emergencyDoctorDao.get(emergencyDoctorId);
            if(emergencyDoctor==null){
                log.error("doctorCallPatient input emergency not exists, emergencyDoctorId[{}]", emergencyDoctorId);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "急诊单不存在");
            }
            Emergency emergency = emergencyDao.get(emergencyDoctor.getEmergencyId());
            Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(emergency.getRequestMpi());
            String callerMobile = hospitalDoctor.getMobile();
            String calledMobile = patient.getMobile();
            Callback callback = AppContextHolder.getBean("callback", Callback.class);
            String result = callback.SDKCallbackTwo(callerMobile, calledMobile, EmergencyConstant.CALLRECORD_BUSSTYPE_H_DOCTOR_CALL_PATIENT, emergency.getEmergencyId());
            log.info("doctorCallPatient result[{}]", result);
            return result;
        }catch (Exception e){
            log.error("doctorCallPatient error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, "拨打失败");
        }
    }

    private Emergency saveOrUpdateEmergency(Patient patient, List<Emergency> emergencyList) {
        Emergency emergency;
        if(ValidateUtil.blankList(emergencyList)){
            emergency = new Emergency();
            emergency.setRequestMpi(patient.getMpiId());
            emergency.setPatientName(patient.getPatientName());
            emergency.setAge(DateConversion.getAge(patient.getBirthday()));
            emergency.setSex(Integer.valueOf(patient.getPatientSex()));
            emergency.setContactPhone(patient.getMobile());
            emergency.setCallTime(new Date());
            emergency.setPrice(EmergencyPriceCalculator.getPrice());
            emergency.setActualPrice(emergency.getPrice());
            emergency.setStatus(EmergencyConstant.EMERGENCY_STATUS_TEMPORARY);
            emergency.setCreateTime(new Date());
            emergency.setUpdateTime(new Date());
            emergencyDao.save(emergency);
        }else {
            emergency = emergencyList.get(0);
            emergency.setPatientName(patient.getPatientName());
            emergency.setAge(DateConversion.getAge(patient.getBirthday()));
            emergency.setSex(Integer.valueOf(patient.getPatientSex()));
            emergency.setContactPhone(patient.getMobile());
            emergency.setCallTime(new Date());
            emergency.setPrice(EmergencyPriceCalculator.getPrice());
            emergency.setActualPrice(emergency.getPrice());
            emergency.setStatus(EmergencyConstant.EMERGENCY_STATUS_TEMPORARY);
            emergency.setUpdateTime(new Date());
            emergencyDao.update(emergency);
        }
        return emergency;
    }

    /**
     * 查询当前患者是否有未支付的急诊单
     * @return
     */
    @RpcService
    public Map<String, Object> fetchUnPayEmergencyOrder(){
        Map<String, Object> resultMap = Maps.newHashMap();
        Emergency emergency = getUnPayEmergencyOrder();
        if(emergency!=null) {
            resultMap.put("existsUnpayOrder", true);
            resultMap.put("emergency", emergency);
        }else {
            resultMap.put("existsUnpayOrder", false);
        }
        return resultMap;
    }

    public Emergency getUnPayEmergencyOrder(){
        try{
            Patient patient = currentPatient();
            List<Emergency> unPayEmergencyList = emergencyDao.findUnPayEmergencyList(patient.getMpiId());
            if(ValidateUtil.notBlankList(unPayEmergencyList)){
                return unPayEmergencyList.get(0);
            }
            return null;
        }catch (Exception e){
            log.error("fetchUnPayEmergencyOrder error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            return null;
        }
    }

    private Patient currentPatient(){
        UserRoleToken userRoleToken = UserRoleToken.getCurrent();
        Patient patient = userRoleToken.getProperty(SystemConstant.ROLES_PATIENT, Patient.class);
        return patient;
    }

    private Doctor currentDoctor() {
        UserRoleToken userRoleToken = UserRoleToken.getCurrent();
        Doctor doctor = userRoleToken.getProperty(SystemConstant.ROLES_DOCTOR, Doctor.class);
        return doctor;
    }

    /**
     * 查询当前患者的急诊单列表
     * @return
     */
    @RpcService
    public List<Emergency> findPatientEmergencyList(){
        try{
            Patient patient = currentPatient();
            List<Emergency> emergencyList = emergencyDao.findAllEmergencyList(patient.getMpiId());
            if(ValidateUtil.blankList(emergencyList)){
                return Collections.emptyList();
            }
            for(Emergency e : emergencyList){
                fullfillDoctor(e);
            }
            return emergencyList;
        }catch (Exception e){
            log.error("findEmergencyListForPatient error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            return Collections.emptyList();
        }
    }

    /**
     * 查询患者端急诊单详情
     * @param emergencyId
     * @return
     */
    @RpcService
    public Emergency getPatientEmergencyDetail(Integer emergencyId){
        try{
            Emergency emergency = emergencyDao.get(emergencyId);
            return fullfillDoctor(emergency);
        } catch (Exception e){
            log.error("getPatientEmergencyDetail error, emergencyId[{}], errorMessage[{}], stackTrace[{}]", emergencyId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    private Emergency fullfillDoctor(Emergency emergency) {
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDao.getByDoctorId(emergency.getDoctorId());
        emergency.setAcceptDoctor(doctor);
        return emergency;
    }

    /**
     * 查询当前登录医生的急诊单列表
     * @return
     */
    @RpcService
    public List<EmergencyDoctor> findDoctorEmergencyList(){
        try{
            Doctor doctor = currentDoctor();
            return findEmergencyListByDoctorId(doctor.getDoctorId());
        }catch (Exception e){
            log.error("findEmergencyListForPatient error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            return Collections.emptyList();
        }
    }

    /**
     * 查询医生急诊单详情
     * @param emergencyDoctorId
     * @return
     */
    @RpcService
    public EmergencyDoctor getDoctorEmergencyDetail(Integer emergencyDoctorId){
        try{
            EmergencyDoctor emergencyDoctor = emergencyDoctorDao.get(emergencyDoctorId);
            return fullfillPatientDoctorEmergency(emergencyDoctor);
        } catch (Exception e){
            log.error("getDoctorEmergencyDetail error, emergencyDoctorId[{}], errorMessage[{}], stackTrace[{}]", emergencyDoctorId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 查询给定医生的急诊单列表
     * @param doctorId
     * @return
     */
    public List<EmergencyDoctor> findEmergencyListByDoctorId(Integer doctorId){
        try{
            List<EmergencyDoctor> emergencyDoctorList = emergencyDoctorDao.findEmergencyDoctorList(doctorId);
            if(ValidateUtil.blankList(emergencyDoctorList)){
                return Collections.emptyList();
            }
            for(EmergencyDoctor ed : emergencyDoctorList){
                fullfillPatientDoctorEmergency(ed);
            }
            return emergencyDoctorList;
        } catch (Exception e){
            log.error("findEmergencyListByDoctorId error, doctorId[{}], errorMessage[{}], stackTrace[{}]", doctorId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            return Collections.emptyList();
        }
    }

    private EmergencyDoctor fullfillPatientDoctorEmergency(EmergencyDoctor emergencyDoctor) {
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDao.getByMpiId(emergencyDoctor.getMpiId());
        Emergency emergency = emergencyDao.get(emergencyDoctor.getEmergencyId());
        Doctor doctor = doctorDao.getByDoctorId(emergency.getDoctorId());
        emergencyDoctor.setPatient(patient);
        emergencyDoctor.setApplyDoctor(doctor);
        emergencyDoctor.setEmergency(emergency);
        if(EmergencyConstant.EMERGENCY_TYPE_COMMON_TRANSFER==emergencyDoctor.getType()){
            return fullfillTransferDoctorIfHas(emergencyDoctor, emergencyDoctor.getType());
        } else if(EmergencyConstant.EMERGENCY_TYPE_REDCALL==emergencyDoctor.getType()){
            return fullfillTransferDoctorIfHas(emergencyDoctor, EmergencyConstant.EMERGENCY_TYPE_URGENT_TRANSFER);
        }
        return emergencyDoctor;
    }

    private EmergencyDoctor fullfillTransferDoctorIfHas(EmergencyDoctor emergencyDoctor, Integer type) {
        List<EmergencyDoctor> urgentTransferedEmergencyDoctorList = emergencyDoctorDao.findUrgentTransferedEmergencyDoctorListWithEmergencyId(emergencyDoctor.getEmergencyId(), type);
        if(ValidateUtil.blankList(urgentTransferedEmergencyDoctorList)){
            return emergencyDoctor;
        }
        Integer transferDoctorId = urgentTransferedEmergencyDoctorList.get(0).getDoctorId();
        Doctor transferDoctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(transferDoctorId);
        emergencyDoctor.setTransferDoctor(transferDoctor);
        return emergencyDoctor;
    }

    /**
     * 获取给定急诊单详情信息
     * @param emergencyId
     * @return
     */
    @RpcService
    public Emergency getEmergencyById(Integer emergencyId){
        try {
            Emergency emergency = emergencyDao.get(emergencyId);
            if (emergency != null) {
                Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(emergency.getDoctorId());
                emergency.setAcceptDoctor(doctor);
            }
            return emergency;
        } catch (Exception e){
            log.error("getEmergencyById error, emergencyId[{}], errorMessage[{}], stackTrace[{}]", emergencyId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 获取紧急转诊通讯录列表
     * @return
     */
    @RpcService
    public List<SimpleEmergencyDoctor> findEmergencyContacts(){
        List<SimpleEmergencyDoctor> seDoctorList = Lists.newArrayList();
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        int[] fixedContactsDoctorIds = getFixedTransferDoctorIds();
        for (int doctorId : fixedContactsDoctorIds){
            Doctor doctor = doctorDao.getByDoctorId(doctorId);
            if(doctor==null){
                log.error("findEmergencyContacts doctor not exist with doctorId[{}]", doctorId);
                continue;
            }
            SimpleEmergencyDoctor seDoctor = BeanUtils.map(doctor, SimpleEmergencyDoctor.class);
            seDoctorList.add(seDoctor);
        }
        return seDoctorList;
    }

    /**
     * 判断是否能将当前急诊单转为普通转诊或紧急转诊
     * @param emergencyId
     * @return
     */
    @RpcService
    public boolean canRequestTransfer(Integer emergencyId){
        log.info("canRequstTransfer start in with params: emergencyId[{}]", emergencyId);
        if(ValidateUtil.nullOrZeroInteger(emergencyId)){
            log.error("canRequstTransfer necessary parameters is null, please check!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try{
            List<EmergencyDoctor> emergencyDoctorList = emergencyDoctorDao.findTransferedEmergencyDoctorListWithEmergencyId(emergencyId);
            if(ValidateUtil.notBlankList(emergencyDoctorList)){
                return false;
            }
            return true;
        } catch (Exception e){
            log.error("canRequstTransfer error, params: emergencyId[{}], errorMessage[{}], stackTrace[{}]", emergencyId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }

    }

    /**
     * 将急诊单转为紧急转诊单，该接口在社区医生给医院医生电话接通以后触发
     * @param emergencyId
     * @param doctorMobile
     */
    public void convertToUrgentTransfer(Integer emergencyId, String doctorMobile){
        log.info("convertToUrgentTransfer start in with params: emergencyId[{}], doctorMobile[{}]", emergencyId, doctorMobile);
        if(ValidateUtil.nullOrZeroInteger(emergencyId) || ValidateUtil.blankString(doctorMobile)){
            log.error("convertToUrgentTransfer necessary parameters is null, please check!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try{
            Emergency emergency = emergencyDao.get(emergencyId);
            Doctor targetDoctor = DAOFactory.getDAO(DoctorDAO.class).getByMobile(doctorMobile);
            if(targetDoctor==null){
                log.error("convertToUrgentTransfer targetDoctor not exists, please check! doctorMobile[{}]", doctorMobile);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "目标医生不存在");
            }
            EmergencyDoctor emergencyDoctor = existsEmergencyDoctor(emergencyId, targetDoctor.getDoctorId());
            if(emergencyDoctor!=null){
                log.error("convertToUrgentTransfer the emergency[{}] has build relation with the doctor[{}]", emergencyId, targetDoctor.getDoctorId());
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该急诊单已与该医生关联，请勿重复关联");
            }
            emergencyDoctor = new EmergencyDoctor();
            emergencyDoctor.setEmergencyId(emergencyId);
            emergencyDoctor.setDoctorId(targetDoctor.getDoctorId());
            emergencyDoctor.setMpiId(emergency.getRequestMpi());
            emergencyDoctor.setType(EmergencyConstant.EMERGENCY_TYPE_URGENT_TRANSFER);
            emergencyDoctor.setUrgent(true);
            emergencyDoctor.setCreateTime(new Date());
            emergencyDoctor.setUpdateTime(new Date());
            emergencyDoctor.setEmergency(emergency);
            emergencyDoctorDao.save(emergencyDoctor);
            smsPushService.pushMsgData2Ons(emergencyDoctor.getEmergencyDoctorId(), emergency.getOrganId(), MessagePushExecutorConstant.EMERGENCY_AFTER_URGENT_TRANSFER_ACCEPT, MessagePushExecutorConstant.EMERGENCY_AFTER_URGENT_TRANSFER_ACCEPT, null);
            asynDoBussService.fireEvent(new BussCreateEvent(emergencyDoctor, BussTypeConstant.REDCALL));
            log.info("convertToUrgentTransfer save success, emergencyId[{}]", emergencyId);
        } catch (Exception e){
            log.error("convertToUrgentTransfer error, params: emergencyId[{}], doctorNumber[{}], errorMessage[{}], stackTrace[{}]", emergencyId, doctorMobile, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    /**
     * 急诊单转为普通转诊接口
     * @param emergencyId
     * @param transferId
     * @return
     */
    @RpcService
    public void convertToCommonTransfer(Integer emergencyDoctorId, Integer transferId){
        log.info("convertToCommonTransfer start in with params: emergencyDoctorId[{}], transferId[{}]", emergencyDoctorId, transferId);
        if(ValidateUtil.nullOrZeroInteger(emergencyDoctorId) || ValidateUtil.nullOrZeroInteger(transferId)){
            log.error("convertToCommonTransfer necessary parameters is null, please check!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try{
            EmergencyDoctor preEmergencyDoctor = emergencyDoctorDao.get(emergencyDoctorId);
            Integer emergencyId = preEmergencyDoctor.getEmergencyId();
            Emergency emergency = emergencyDao.get(emergencyId);
            Transfer transfer = DAOFactory.getDAO(TransferDAO.class).getById(transferId);
            if(transfer==null){
                log.error("transfer not exists with transferId[{}]", transferId);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单不存在");
            }
            Doctor currentDoctor = currentDoctor();
            Doctor targetDoctor = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(transfer.getTargetDoctor());
            if(targetDoctor==null){
                log.error("convertToCommonTransfer targetDoctor not exists, please check! transferId[{}]", transferId);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "目标医生不存在");
            }
            EmergencyDoctor emergencyDoctor = existsEmergencyDoctor(emergencyId, targetDoctor.getDoctorId());
            if(emergencyDoctor!=null){
                log.error("convertToCommonTransfer the emergency[{}] has build relation with the doctor[{}]", emergencyId, targetDoctor.getDoctorId());
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该急诊单已与该医生关联，请勿重复关联");
            }
            updateEmergencyDoctorForCommunityDoctor(currentDoctor, emergencyId, transferId);
            emergencyDoctor = new EmergencyDoctor();
            emergencyDoctor.setEmergencyId(emergencyId);
            emergencyDoctor.setDoctorId(targetDoctor.getDoctorId());
            emergencyDoctor.setMpiId(emergency.getRequestMpi());
            emergencyDoctor.setType(EmergencyConstant.EMERGENCY_TYPE_COMMON_TRANSFER);
            emergencyDoctor.setUrgent(false);
            emergencyDoctor.setCreateTime(new Date());
            emergencyDoctor.setUpdateTime(new Date());
            emergencyDoctor.setTransferId(transferId);
            emergencyDoctorDao.save(emergencyDoctor);
            log.info("convertToCommonTransfer save success, emergencyId[{}]", emergencyId);
        } catch (Exception e){
            log.error("convertToCommonTransfer error, params: emergencyDoctorId[{}], errorMessage[{}], stackTrace[{}]", emergencyDoctorId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
    }

    private void updateEmergencyDoctorForCommunityDoctor(Doctor currentDoctor, Integer emergencyId, Integer transferId) {
        List<EmergencyDoctor> emergencyDoctorList = emergencyDoctorDao.findEmergencyDoctorListWithDoctorIdAndEmergencyId(currentDoctor.getDoctorId(), emergencyId);
        if(ValidateUtil.blankList(emergencyDoctorList)){
            log.error("updateEmergencyDoctorForCommunityDoctor error, currentDoctor[{}] has no relation with the emergency[{}]", currentDoctor.getDoctorId(), emergencyId);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "当前医生与该急诊单无关联关系");
        }
        EmergencyDoctor emergencyDoctor = emergencyDoctorList.get(0);
        emergencyDoctor.setTransferId(transferId);
        emergencyDoctor.setType(EmergencyConstant.EMERGENCY_TYPE_COMMON_TRANSFER);
        emergencyDoctor.setUpdateTime(new Date());
        emergencyDoctorDao.update(emergencyDoctor);
    }

    private EmergencyDoctor existsEmergencyDoctor(Integer emergencyId, Integer doctorId) {
        List<EmergencyDoctor> emergencyDoctorList = emergencyDoctorDao.findEmergencyDoctorListWithDoctorIdAndEmergencyId(doctorId, emergencyId);
        if(ValidateUtil.blankList(emergencyDoctorList)){
            return null;
        }
        return emergencyDoctorList.get(0);
    }

    /**
     * 社区医生接听电话之后，更改急诊单状态：暂存 —> 待支付
     * @param emergencyId
     * @param doctorMobile
     */
    public void afterDoctorAcceptEmergency(Integer emergencyId, String doctorMobile){
        log.info("afterDoctorAcceptEmergency start in with params: emergencyId[{}], doctorMobile[{}]", emergencyId, doctorMobile);
        if(ValidateUtil.nullOrZeroInteger(emergencyId) || ValidateUtil.blankString(doctorMobile)){
            log.error("afterDoctorAcceptEmergency necessary parameters is null, please check!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "必填参数为空");
        }
        try{
            Emergency emergency = emergencyDao.get(emergencyId);
            Doctor acceptDoctor = DAOFactory.getDAO(DoctorDAO.class).getByMobile(doctorMobile);
            if(acceptDoctor==null){
                log.error("afterDoctorAcceptEmergency targetDoctor not exists, please check! doctorMobile[{}]", doctorMobile);
                throw new DAOException(ErrorCode.SERVICE_ERROR, "目标医生不存在");
            }
            EmergencyDoctor emergencyDoctor = existsEmergencyDoctor(emergencyId, acceptDoctor.getDoctorId());
            if(emergencyDoctor!=null){
                log.error("afterDoctorAcceptEmergency the emergency[{}] has build relation with the doctor[{}]", emergencyId, acceptDoctor.getDoctorId());
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该急诊单已与该医生关联，请勿重复关联");
            }
            emergency.setDoctorId(acceptDoctor.getDoctorId());
            Employment emp = DAOFactory.getDAO(EmploymentDAO.class).getPrimaryEmpByDoctorId(acceptDoctor.getDoctorId());
            emergency.setDepartId(emp.getDepartment());
            emergency.setOrganId(acceptDoctor.getOrgan());
            emergency.setStatus(EmergencyConstant.EMERGENCY_STATUS_UNPAID);
            emergency.setUpdateTime(new Date());
            emergencyDao.update(emergency);
            emergencyDoctor = new EmergencyDoctor();
            emergencyDoctor.setEmergencyId(emergencyId);
            emergencyDoctor.setDoctorId(acceptDoctor.getDoctorId());
            emergencyDoctor.setMpiId(emergency.getRequestMpi());
            emergencyDoctor.setType(EmergencyConstant.EMERGENCY_TYPE_REDCALL);
            emergencyDoctor.setUrgent(true);
            emergencyDoctor.setCreateTime(new Date());
            emergencyDoctor.setUpdateTime(new Date());
            emergencyDoctorDao.save(emergencyDoctor);
            emergencyDoctor.setEmergency(emergency);
            asynDoBussService.fireEvent(new BussCreateEvent(emergencyDoctor, BussTypeConstant.REDCALL));
            smsPushService.pushMsgData2Ons(emergencyDoctor.getEmergencyDoctorId(), emergency.getOrganId(), MessagePushExecutorConstant.EMERGENCY_AFTER_DOCTOR_ACCEPT, MessagePushExecutorConstant.EMERGENCY_AFTER_DOCTOR_ACCEPT, null);
            log.info("afterDoctorAcceptEmergency save success, emergencyId[{}]", emergencyId);
        } catch (Exception e){
            log.error("afterDoctorAcceptEmergency error, params: emergencyId[{}], doctorNumber[{}], errorMessage[{}], stackTrace[{}]", emergencyId, doctorMobile, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }

    }

    public void paySuccess(Integer emergencyId){
        List<EmergencyDoctor> emergencyDoctorList = emergencyDoctorDao.findTransferedEmergencyDoctorListWithEmergencyId(emergencyId);
        if(ValidateUtil.blankList(emergencyDoctorList)){
            return;
        }
        asynDoBussService.fireEvent(new BussFinishEvent(emergencyDoctorList.get(0).getEmergencyDoctorId(), BussTypeConstant.REDCALL));
    }

    @RpcService
    public Emergency getEmergencyByEmergencyId(Integer emergencyId){
        return emergencyDao.get(emergencyId);
    }

    @RpcService
    public EmergencyDoctor getUrgentTransferEmergencyDoctorByEmergencyId(Integer emergencyId){
        List<EmergencyDoctor> emergencyDoctorList = emergencyDoctorDao.findUrgentTransferedEmergencyDoctorListWithEmergencyId(emergencyId, EmergencyConstant.EMERGENCY_TYPE_URGENT_TRANSFER);
        if(ValidateUtil.blankList(emergencyDoctorList)){
            return null;
        }
        return emergencyDoctorList.get(0);
    }

    @RpcService
    public EmergencyDoctor getEmergencyDoctorById(Integer emergencyDoctorId){
        return emergencyDoctorDao.get(emergencyDoctorId);
    }


    private int[] getFixedDoctorIds() {
        String communityDoctors = paramLoader.getParam("TMP_EMERGENCY_DOCTORS");
        String[] cDoctorIds = communityDoctors.split(",");
        int[] ids = new int[cDoctorIds.length];
        for(int i=0; i<ids.length; i++){
            ids[i] = Integer.valueOf(cDoctorIds[i].trim());
        }
        return ids;
    }

    private int[] getFixedTransferDoctorIds() {
        String emergencyContacts = paramLoader.getParam("TMP_EMERGENCY_URGENT_DOCTORS");
        String[] cDoctorIds = emergencyContacts.split(",");
        int[] ids = new int[cDoctorIds.length];
        for(int i=0; i<ids.length; i++){
            ids[i] = Integer.valueOf(cDoctorIds[i].trim());
        }
        return ids;
    }
}
