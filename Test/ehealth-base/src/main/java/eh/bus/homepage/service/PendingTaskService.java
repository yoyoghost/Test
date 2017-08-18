package eh.bus.homepage.service;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.dictionary.support.XMLDictionary;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.PageConstant;
import eh.base.dao.DoctorGroupDAO;
import eh.bus.constant.EmergencyConstant;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.MeetClinicResultDAO;
import eh.bus.dao.TransferDAO;
import eh.bus.homepage.constant.PendingTaskTypeEnum;
import eh.bus.homepage.dao.PendingTaskDAO;
import eh.cdr.thread.RecipeBusiThreadPool;
import eh.entity.bus.*;
import eh.entity.mpi.Patient;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.PatientTypeDAO;
import eh.mpi.dao.SignPatientLabelDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.utils.DateConversion;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ctd.persistence.DAOFactory.getDAO;

/**
 * 首页业务处理service
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19
 */
public class PendingTaskService {

    private static final Logger logger = LoggerFactory.getLogger(PendingTaskService.class);

    /**
     * 获取医生待处理任务总数
     *
     * @param doctorId 医生ID
     * @return long
     */
    @RpcService
    public long getTaskCount(Integer doctorId) {
        Assert.notNull(doctorId, "PendingTaskService getTaskCount doctorId is null.");

        return DAOFactory.getDAO(PendingTaskDAO.class).getCountByDoctorId(doctorId);
    }

    /**
     * 获取医生待处理列表(grade=10级任务)
     *
     * @param doctorId 医生ID
     * @param taskId 上一页最后一条任务的申请ID，第一页用 0 或者 null
     * @param limit    每页展示数
     * @return List
     */
    @RpcService
    public List<PendingTask> findPendingTask(Integer doctorId, Integer taskId, Integer limit) {
        Assert.notNull(doctorId, "PendingTaskService findPendingTask doctorId is null.");
        limit = PageConstant.getPageLimit(limit);
        taskId = (null == taskId || 0 == taskId)?Integer.MAX_VALUE:taskId;

        PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
        List<PendingTask> taskList = pendingTaskDAO.findByDoctorId(doctorId, taskId, 0, limit);
        if (CollectionUtils.isNotEmpty(taskList)) {
            List<String> mpiIdList = FluentIterable.from(taskList).transform(new Function<PendingTask, String>() {
                @Override
                public String apply(PendingTask input) {
                    return input.getMpiId();
                }
            }).toList();

            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            List<Patient> patientList = patientDAO.findByMpiIdIn(mpiIdList);
            if (CollectionUtils.isNotEmpty(patientList)) {
                Map<String, Patient> patientMap = Maps.uniqueIndex(patientList, new Function<Patient, String>() {
                    @Override
                    public String apply(Patient input) {
                        return input.getMpiId();
                    }
                });

                Patient patient;
                PatientTypeDAO patientTypeDAO = DAOFactory.getDAO(PatientTypeDAO.class);
                XMLDictionary xmlDictionary = null;
                try {
                    xmlDictionary = (XMLDictionary) DictionaryController.instance().get("eh.base.dictionary.Gender");
                } catch (ControllerException e) {
                    logger.error("findPendingTask Gender dic error.");
                }
                for (PendingTask pendingTask : taskList) {
                    pendingTask.setShowDate(DateConversion.convertRequestDateForBussNew(pendingTask.getRequestTime()));
                    if (StringUtils.isNotEmpty(pendingTask.getMpiId())) {
                        patient = patientMap.get(pendingTask.getMpiId());
                        if (null != patient) {
                            pendingTask.setPatientName(patient.getPatientName());
                            if (StringUtils.isNotEmpty(patient.getPatientSex()) && null != xmlDictionary) {
                                pendingTask.setPatientSex(xmlDictionary.getText(patient.getPatientSex()));
                            }

                            pendingTask.setPatientBirthday(patient.getBirthday());

                            //签约申请单增加医保类型
                            if (pendingTask.getPageBussType().equals(PendingTaskTypeEnum.SIGN.getBussType())) {
                                DictionaryItem item = patientTypeDAO.getDictionaryItem(patient.getPatientType());
                                if (null != item) {
                                    pendingTask.setPatientType(item.getText());
                                }
                            }
                        }
                    }
                }
            }
        }

        return taskList;
    }

    /**
     * 忽略任务
     *
     * @param doctorId 医生ID
     * @param taskId   任务ID
     * @param taskType 任务类型
     */
    @RpcService
    public boolean ignoreTask(Integer doctorId, Integer taskId, Integer taskType) {
        boolean success = true;
        try {
            Assert.notNull(doctorId, "PendingTaskService ignoreTask doctorId is null.");
            Assert.notNull(taskId, "PendingTaskService ignoreTask taskId is null.");
            Assert.notNull(taskType, "PendingTaskService ignoreTask taskType is null.");
            PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
            pendingTaskDAO.updateIgnoreBuss(taskType, taskId, doctorId);
        } catch (Exception e) {
            logger.error("ignoreTask updateIgnoreBuss error. doctorId={},taskId={},taskType={}",doctorId,taskId,taskType);
            success = false;
        }

        return success;
    }

    @RpcService
    public void clearPendingTask(){
        PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
        pendingTaskDAO.delAll();
    }

    /**
     * 重置首页待处理任务列表
     */
    @RpcService
    public void resetPendingTask() {
        try {
            new RecipeBusiThreadPool(new Runnable() {
                @Override
                public void run() {
                    //转诊
                    List<Transfer> transferList = DAOFactory.getDAO(TransferDAO.class).findAllPendingTransfer();
                    logger.error("resetPendingTask transferList : " + transferList.size());
                    if (CollectionUtils.isNotEmpty(transferList)) {
                        for (Transfer transfer : transferList) {
                            try {
                                createTransferTask(transfer, null);
                            } catch (Exception e) {
                                logger.error(e.getMessage());
                            }
                        }
                    }
                }
            }).execute();
        } catch (InterruptedException e) {
            logger.error("resetPendingTask-->"+e);
        }

        try {
            new RecipeBusiThreadPool(new Runnable() {
                @Override
                public void run() {
                    //会诊
                    MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
                    List<MeetClinic> meetClinicList = meetClinicDAO.findUnFinishedMeetClinics();
                    logger.error("resetPendingTask meetClinicList : " + meetClinicList.size());
                    if (CollectionUtils.isNotEmpty(meetClinicList)) {
                        for (MeetClinic meetClinic : meetClinicList) {
                            try {
                                createMeetClinicTask(meetClinic, null);
                            } catch (Exception e) {
                                logger.error(e.getMessage());
                            }
                        }
                    }
                }
            }).execute();
        } catch (InterruptedException e) {
            logger.error("resetPendingTask-->"+e);
        }


        try {
            new RecipeBusiThreadPool(new Runnable() {
                @Override
                public void run() {
                    //咨询
                    List<Consult> consultList = DAOFactory.getDAO(ConsultDAO.class).findPendingConsultList();
                    logger.error("resetPendingTask consultList : " + consultList.size());
                    if (CollectionUtils.isNotEmpty(consultList)) {
                        for (Consult consult : consultList) {
                            try {
                                createConsultTask(consult, null);
                            } catch (Exception e) {
                                logger.error("resetPendingTask-->"+e);
                            }
                        }
                    }
                }
            }).execute();
        } catch (InterruptedException e) {
            logger.error("resetPendingTask-->"+e);
        }



        try {
            new RecipeBusiThreadPool(new Runnable() {
                @Override
                public void run() {
                    //签约
                    List<SignRecord> signRecordList = DAOFactory.getDAO(SignRecordDAO.class).findAllPendingSIgnRecords();
                    logger.error("resetPendingTask signRecordList : " + signRecordList.size());
                    if (CollectionUtils.isNotEmpty(signRecordList)) {
                        for (SignRecord signRecord : signRecordList) {
                            try {
                                createSignTask(signRecord, null);
                            } catch (Exception e) {
                                logger.error(e.getMessage());
                            }
                        }
                    }
                }
            }).execute();
        } catch (InterruptedException e) {
           logger.error("resetPendingTask-->"+e);
        }


    }

    /**
     * 转诊任务创建
     *
     * @param transfer
     * @param otherInfo
     */
    public void createTransferTask(Transfer transfer, Map<String, Object> otherInfo) {
        //设置首页业务分类
        Integer pageBussType = PendingTaskTypeEnum.UNKNOW.getBussType();
        if (new Integer(2).equals(transfer.getTransferType())) {
            //住院转诊
            pageBussType = PendingTaskTypeEnum.HOSPITAL_TRANSFER.getBussType();
        } else if(new Integer(1).equals(transfer.getTransferType())){
            if (null != transfer.getIsAdd() && transfer.getIsAdd()) {
                //加号转诊
                pageBussType = PendingTaskTypeEnum.ADDNUMBER_TRANSFER.getBussType();
            }

            if (StringUtils.isNotEmpty(transfer.getRequestMpi())) {
                //特需预约
                pageBussType = PendingTaskTypeEnum.VIP_TRANSFER.getBussType();
            }
        }
        if (pageBussType.equals(PendingTaskTypeEnum.UNKNOW.getBussType())) {
            logger.error("createTransferTask pageBussType is error. bussId={}", transfer.getTransferId());
            return;
        }

        Map<String, Object> infoMap = new HashMap<>();
        infoMap.put("desc", transfer.getPatientCondition());
        String infoMapStr = JSONUtils.toString(infoMap);
        String mpiId = transfer.getMpiId();

        PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
        //判断是否为团队业务
        List<Integer> doctorIds = DAOFactory.getDAO(DoctorGroupDAO.class).findMemberIdsByDoctorId(transfer.getTargetDoctor());
        if (CollectionUtils.isNotEmpty(doctorIds)) {
            PendingTask task;
            //如果接收医生为空，说明该转诊未被任何医生接收
            if(null != transfer.getAgreeDoctor()){
                task = new PendingTask(BussTypeConstant.TRANSFER, pageBussType, transfer.getTransferId(),
                        transfer.getAgreeDoctor(), mpiId, transfer.getRequestTime(), infoMapStr);
                task.setTeamFlag(1);
                task.setTargetDoctorId(transfer.getTargetDoctor());
                pendingTaskDAO.save(task);
            }else {
                for (Integer doctorId : doctorIds) {
                    task = new PendingTask(BussTypeConstant.TRANSFER, pageBussType, transfer.getTransferId(),
                            doctorId, mpiId, transfer.getRequestTime(), infoMapStr);
                    task.setTeamFlag(1);
                    task.setTargetDoctorId(transfer.getTargetDoctor());
                    pendingTaskDAO.save(task);
                }
            }
        } else {
            PendingTask task = new PendingTask(BussTypeConstant.TRANSFER, pageBussType, transfer.getTransferId(),
                    transfer.getTargetDoctor(), mpiId, transfer.getRequestTime(), infoMapStr);

            pendingTaskDAO.save(task);
        }
    }


    /**
     * 会诊任务创建
     *
     * @param meetClinic
     * @param otherInfo
     */
    public void createMeetClinicTask(MeetClinic meetClinic, Map<String, Object> otherInfo) {
        //设置首页业务分类
        Integer pageBussType = PendingTaskTypeEnum.NORMAL_MEETCLINIC.getBussType();

        Map<String, Object> infoMap = new HashMap<>();
        infoMap.put("desc", meetClinic.getPatientCondition());
        infoMap.put("meetClinicId", meetClinic.getMeetClinicId());
        String infoMapStr = JSONUtils.toString(infoMap);
        String mpiId = meetClinic.getMpiid();

        //获取会诊结果表单数据
        MeetClinicResultDAO meetClinicResultDAO = getDAO(MeetClinicResultDAO.class);
        PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
        List<MeetClinicResult> results = meetClinicResultDAO.findByMeetClinicId(meetClinic.getMeetClinicId());
        if (otherInfo != null && otherInfo.get("results") != null) {
            results = (List<MeetClinicResult>) otherInfo.get("results");
        }
        if (CollectionUtils.isNotEmpty(results)) {
            DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            for (MeetClinicResult result : results) {
                //  exeStatus: 0未开始 1进行中  2会诊结束
                if (result.getExeStatus() < 2) {
                    //判断是否为团队业务
                    List<Integer> doctorIds = doctorGroupDAO.findMemberIdsByDoctorId(result.getTargetDoctor());
                    if (CollectionUtils.isNotEmpty(doctorIds)) {
                        PendingTask task;
                        if (0 == result.getExeStatus()) {
                            for (Integer doctorId : doctorIds) {
                                task = new PendingTask(BussTypeConstant.MEETCLINIC, pageBussType, result.getMeetClinicResultId(),
                                        doctorId, mpiId, meetClinic.getRequestTime(), infoMapStr);
                                task.setTeamFlag(1);
                                task.setTargetDoctorId(result.getTargetDoctor());
                                pendingTaskDAO.savePendingWithSelect(task);
                            }
                        } else if (1 == result.getExeStatus()) {
                            task = new PendingTask(BussTypeConstant.MEETCLINIC, pageBussType, result.getMeetClinicResultId(),
                                    result.getExeDoctor(), mpiId, meetClinic.getRequestTime(), infoMapStr);
                            task.setTeamFlag(1);
                            task.setTargetDoctorId(result.getTargetDoctor());
                            pendingTaskDAO.savePendingWithSelect(task);
                        }
                    } else {
                        PendingTask task = new PendingTask(BussTypeConstant.MEETCLINIC, pageBussType, result.getMeetClinicResultId(),
                                result.getTargetDoctor(), mpiId, meetClinic.getRequestTime(), infoMapStr);

                        pendingTaskDAO.savePendingWithSelect(task);
                    }
                }
            }
        }
    }


    /**
     * 咨询任务创建
     *
     * @param consult
     * @param otherInfo
     */
    public void createConsultTask(Consult consult, Map<String, Object> otherInfo) {
        Map<String, Object> infoMap = new HashMap<>();
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        //设置问卷信息
        String desc = consult.getLeaveMess();
        Boolean b = consultDAO.setConsultQuestionnaire(consult);
        if(b){
            desc = consult.getQuestionnaire().getQuestionDesc()+"\n"+consult.getLeaveMess();
        }
        infoMap.put("desc", desc);
        //设置首页业务分类
        Integer pageBussType = PendingTaskTypeEnum.UNKNOW.getBussType();
        if (new Integer(1).equals(consult.getRequestMode())) {
            pageBussType = PendingTaskTypeEnum.TEL_CONSULT.getBussType();
            // 2017-01-06 08:00~18:00
            StringBuilder appointDate = new StringBuilder();
            if(null != consult.getAppointTime()) {
                String date = DateConversion.getDateFormatter(consult.getAppointTime(), DateConversion.DEFAULT_DATE_WITHOUTYEAR);
                String startTime = DateConversion.getDateFormatter(consult.getAppointTime(), DateConversion.DEFAULT_TIME_WITHOUTSECOND);
                appointDate.append(date+" "+startTime);
                if(null != consult.getAppointEndTime()) {
                    String endTime = DateConversion.getDateFormatter(consult.getAppointEndTime(), DateConversion.DEFAULT_TIME_WITHOUTSECOND);
                    appointDate.append("~"+endTime);
                }
            }
            infoMap.put("appointDate",appointDate.toString());
        } else if (new Integer(2).equals(consult.getRequestMode())) {
            pageBussType = PendingTaskTypeEnum.PICTURE_CONSULT.getBussType();
        } else if (new Integer(4).equals(consult.getRequestMode())) {
            pageBussType = PendingTaskTypeEnum.RECIPE_CONSULT.getBussType();
        } else if (new Integer(5).equals(consult.getRequestMode())) {
            pageBussType = PendingTaskTypeEnum.PROFESSOR_CONSULT.getBussType();
        }
        if (pageBussType.equals(PendingTaskTypeEnum.UNKNOW.getBussType())) {
            logger.error("createConsultTask pageBussType is error. bussId={}", consult.getConsultId());
            return;
        }

        String infoMapStr = JSONUtils.toString(infoMap);
        String mpiId = consult.getMpiid();

        PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
        //判断是否为团队业务
        List<Integer> doctorIds = DAOFactory.getDAO(DoctorGroupDAO.class).findMemberIdsByDoctorId(consult.getConsultDoctor());
        if (CollectionUtils.isNotEmpty(doctorIds)) {
            PendingTask task;
            if(null != consult.getExeDoctor()){
                task = new PendingTask(BussTypeConstant.CONSULT, pageBussType, consult.getConsultId(),
                        consult.getExeDoctor(), mpiId, consult.getRequestTime(), infoMapStr);
                task.setTeamFlag(1);
                task.setTargetDoctorId(consult.getConsultDoctor());
                pendingTaskDAO.save(task);
            }else {
                for (Integer doctorId : doctorIds) {
                    task = new PendingTask(BussTypeConstant.CONSULT, pageBussType, consult.getConsultId(),
                            doctorId, mpiId, consult.getRequestTime(), infoMapStr);
                    task.setTeamFlag(1);
                    task.setTargetDoctorId(consult.getConsultDoctor());
                    pendingTaskDAO.save(task);
                }
            }
        } else {
            PendingTask task = new PendingTask(BussTypeConstant.CONSULT, pageBussType, consult.getConsultId(),
                    consult.getConsultDoctor(), mpiId, consult.getRequestTime(), infoMapStr);

            pendingTaskDAO.save(task);
        }
    }

    /**
     * 签约任务创建
     *
     * @param signRecord
     * @param otherInfo
     */
    public void createSignTask(SignRecord signRecord, Map<String, Object> otherInfo) {
        //设置首页业务分类
        Integer pageBussType = PendingTaskTypeEnum.SIGN.getBussType();

        Map<String, Object> infoMap = new HashMap<>();
        //设置居民类型
        List<Integer> labels = DAOFactory.getDAO(SignPatientLabelDAO.class).findSplLabelBySignRecordId(signRecord.getSignRecordId());
        StringBuilder labelStr = new StringBuilder();
        if (CollectionUtils.isNotEmpty(labels)) {
            try {
                XMLDictionary xmlDictionary = (XMLDictionary) DictionaryController.instance().get("eh.mpi.dictionary.PatientLabel");
                for (Integer label : labels) {
                    labelStr.append("," + xmlDictionary.getText(label));
                }
            } catch (ControllerException e) {
                labelStr.setLength(0);
                logger.error("createSignTask PatientLabel dic error. bussId={}", signRecord.getSignRecordId());
            }
        }
        infoMap.put("label", (labelStr.length() > 0) ? labelStr.substring(1) : "");

        String mpiId = signRecord.getRequestMpiId();
        //签约没有团队业务
        PendingTask task = new PendingTask(BussTypeConstant.SIGN, pageBussType, signRecord.getSignRecordId(),
                signRecord.getDoctor(), mpiId, signRecord.getRequestDate(), JSONUtils.toString(infoMap));

        DAOFactory.getDAO(PendingTaskDAO.class).save(task);
    }

    public void createEmergencyTask(EmergencyDoctor emergencyDoctor, Map<String, Object> otherInfo) {
        //设置首页业务分类
        Integer pageBussType = (EmergencyConstant.EMERGENCY_TYPE_REDCALL==emergencyDoctor.getType())?PendingTaskTypeEnum.RED_CALL.getBussType():PendingTaskTypeEnum.URGENT_TRANSFER.getBussType();
        String mpiId = emergencyDoctor.getEmergency().getRequestMpi();
        PendingTask task = new PendingTask(BussTypeConstant.REDCALL, pageBussType, emergencyDoctor.getEmergencyDoctorId(),
                emergencyDoctor.getDoctorId(), mpiId, emergencyDoctor.getCreateTime(), JSONUtils.toString(ImmutableMap.of("emergency", "")));

        DAOFactory.getDAO(PendingTaskDAO.class).save(task);
    }
}
