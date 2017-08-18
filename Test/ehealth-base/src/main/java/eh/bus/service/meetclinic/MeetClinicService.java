package eh.bus.service.meetclinic;

import com.google.common.collect.Maps;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorGroupDAO;
import eh.base.dao.RelationLabelDAO;
import eh.base.dao.RelationPatientDAO;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.EvaluateConstant;
import eh.bus.constant.MeetClinicConstant;
import eh.bus.constant.MsgTypeEnum;
import eh.bus.dao.EvaluateDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.MeetClinicResultDAO;
import eh.bus.service.consult.ConsultMessageService;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.bus.Evaluate;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.msg.Group;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.GroupDAO;
import eh.msg.service.EasemobIMService;
import eh.utils.DateConversion;
import eh.wxpay.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by zhangsl on 2016/12/15.
 */
public class MeetClinicService {
    private static final Logger logger = LoggerFactory.getLogger(MeetClinicService.class);

    /**
     * 会诊单详情
     *
     * @param meetClinicId 会诊单号
     * @param doctorId     当前登录医生内码
     * @return HashMap<String, Object>
     * --meetClinic:会诊单信息,cdrOtherdocs:图片资料列表,patient：患者信息(全部),
     * meetClinicResultIds:执行单Id列表,inDoctors:参与医生列表(剔除拒绝),
     * targetPhones:目标医生姓名及电话列表,status:详情单状态,statusText:状态名,
     * cancelbutton:是否显示取消会诊按钮,cancelCause:取消原因,refusecause:拒绝原因,
     * count:回复/拒绝总数,requestDoctor:申请医生信息,resultId:当前医生执行单号,
     * readOnly:是否只读,meetCenter:当前医生是否会诊中心,meetCenterStatus:会诊中心接收状态
     * @author zhangsl
     * @Date 2016-12-15 16:57:00
     */
    @RpcService
    public Map<String, Object> getDetailByMeetClinicId(int meetClinicId,
                                                       int doctorId) {
        return getDetailByMeetClinicIdWithResultId(meetClinicId, doctorId, null);
    }

    /**
     * 会诊单详情
     *
     * @param meetClinicId
     * @param doctorId
     * @param meetClinicResultId
     * @return Map<String, Object>
     * @detail meetClinic:会诊单信息,cdrOtherdocs:图片资料列表,patient：患者信息(全部),
     * meetClinicResultIds:执行单Id列表,inDoctors:参与医生列表(剔除拒绝),
     * targetPhones:目标医生姓名及电话列表,status:详情单状态,statusText:状态名,
     * cancelbutton:是否显示取消会诊按钮,cancelCause:取消原因,refusecause:拒绝原因,
     * count:回复/拒绝总数,requestDoctor:申请医生信息,resultId:当前医生执行单号,
     * readOnly:是否只读,meetCenter:当前医生是否会诊中心,meetCenterStatus:会诊中心接收状态
     */
    @RpcService
    public Map<String, Object> getDetailByMeetClinicIdWithResultId(int meetClinicId, int doctorId, Integer meetClinicResultId) {
        Map<String, Object> map = new HashMap<>();
        List<Object> phones = new ArrayList<>();
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        MeetClinicDAO mcDao = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        CdrOtherdocDAO cdrDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
        DoctorGroupDAO dgDao = DAOFactory.getDAO(DoctorGroupDAO.class);
        DoctorDAO dDao = DAOFactory.getDAO(DoctorDAO.class);
        Dictionary dic = null;
        Dictionary mcStatusDic = null;
        Dictionary exeStatusDic = null;
        try {
            dic = DictionaryController.instance().get("eh.base.dictionary.Doctor");
            mcStatusDic = DictionaryController.instance().get("eh.bus.dictionary.MeetClinicStatus");
            exeStatusDic = DictionaryController.instance().get("eh.bus.dictionary.ExeStatus");
        } catch (ControllerException e) {
            logger.error(e.getMessage());
        }

        Integer status = 0;
        String statusText = "待处理";
        MeetClinic meetClinic = mcDao.get(meetClinicId);
        if (meetClinic == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "会诊单不存在");
        }
        Integer requestDoctor = meetClinic.getRequestDoctor();
        meetClinic.setMobile(dDao.getMobileByDoctorId(requestDoctor));
        meetClinic.setRequestBusyFlag(dDao.getBusyFlagByDoctorId(requestDoctor));
        String mpiId = meetClinic.getMpiid();
        List<Otherdoc> cdrOtherdocs = cdrDAO.findByClinicTypeAndClinicId(2,
                meetClinicId);
        if (requestDoctor == doctorId) {
            status = meetClinic.getMeetClinicStatus();
            statusText = mcStatusDic.getText(status);
            Date oneDayAgo = DateConversion.getDaysAgo(1);
            if (meetClinic.getRequestTime().before(oneDayAgo)) {
                map.put("cancelButton", true);
            } else {
                map.put("cancelButton", false);
            }
            if (status == 9 && StringUtils.isNotBlank(meetClinic.getCancelCause())) {
                map.put("cancelCause", meetClinic.getCancelCause());
            }
        } else {
            Doctor reqDoctor = dDao.getByDoctorId(requestDoctor);
            map.put("requestDoctor", reqDoctor);
        }

        Patient patient = patientDAO.get(mpiId);
        patient.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
        if (rd != null) {
            patient.setRelationPatientId(rd.getRelationDoctorId());
            patient.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                patient.setSignFlag(true);
            } else {
                patient.setSignFlag(false);
            }
            patient.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        }

        List<MeetClinicResult> mrs = resultDAO.findByMeetClinicId(meetClinicId);
        List<Integer> mrIds = new ArrayList<>();
        List<Object> inDoctors = new ArrayList<>();
        int count = 0;
        Date lastTime = new Date(0);
        Integer tmId = 0;
        Integer emId = 0;
        Integer smId = 0;
        meetClinic.setRequestMode(MeetClinicConstant.MEETREQUESTMODE_DMHZ);
        for (MeetClinicResult mr : mrs) {
            Integer effeStatus = mr.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            HashMap<String, Object> phone = new HashMap<>();
            HashMap<String, Object> docMap = new HashMap<>();
            Integer showType = 0; //前端显示方式：0单个医生 1拼接团队
            Boolean team = false;
            String phoneName;
            String phoneNum;
            Doctor doc;
            Doctor teamDoc;
            Integer busyFlag = 0;
            Integer mrId = mr.getMeetClinicResultId();
            mrIds.add(mrId);

            Integer target = mr.getTargetDoctor();
            Integer exe = mr.getExeDoctor();
            Integer exeStatus = mr.getExeStatus();
            List<DoctorGroup> dgs = dgDao.findByDoctorId(target);
            if (mr.getMeetCenter() != null && mr.getMeetCenter()) {
                meetClinic.setRequestMode(MeetClinicConstant.MEETREQUESTMODE_HZZX);
                meetClinic.setCenterStatus(mr.getMeetCenterStatus());//会诊中心接收状态供前端判断会诊计划类
            }
            if (dgs != null) {
                for (DoctorGroup dg : dgs) {
                    if (dg.getMemberId() == doctorId) {
                        team = true;
                        break;
                    }
                }
            }
            // 2016-3-9 luf 系统消息跳转时，给予前端当前医生执行单号
            if (target == doctorId) {
                smId = mrId;
            } else if (team) {
                if (exe != null) {
                    if (exe == doctorId) {//只有接收的医生能操作会诊单
                        emId = mrId;
                    }
                } else {
                    tmId = mr.getMeetClinicResultId();
                }
            }

            if (exeStatus != 8) {
                if (exe != null && exe > 0) {
                    doc = dDao.getByDoctorId(exe);
                    if (dgs != null && !dgs.isEmpty()) {
                        doc.setName(doc.getName() + "(" + dic.getText(target) + ")");
                        teamDoc = dDao.getByDoctorId(target);
                        showType = 1;
                        docMap.put("teamDoc", teamDoc);
                    }
                    phoneName = dic.getText(exe);
                    phoneNum = dDao.getMobileByDoctorId(exe);
                    busyFlag = dDao.getBusyFlagByDoctorId(exe);
                } else {
                    doc = dDao.getByDoctorId(target);
                    phoneName = dic.getText(target);
                    phoneNum = dDao.getMobileByDoctorId(target);
                    busyFlag = dDao.getBusyFlagByDoctorId(target);
                }
                docMap.put("doc", doc);
                docMap.put("showType", showType);
                inDoctors.add(docMap);
                phone.put("name", phoneName);
                phone.put("phone", phoneNum);
                phone.put("busyFlag", busyFlag);
                phones.add(phone);
            }

            if ((exeStatus == 2 || exeStatus == 8) && (mr.getMeetCenter() == null || !mr.getMeetCenter())) {
                count++;
            }

            if (exeStatus >= 2) {
                Date endDate = mr.getEndTime();
                if (endDate.after(lastTime)) {
                    lastTime = endDate;
                }
            }
        }
        Date cancelTime = new Date();
        Date twoDaysDate = DateConversion.getDaysAgo(2);
        String timePoint = DateConversion.getDateFormatter(cancelTime,
                "HH:mm:ss");
        Date twoDaysAgo = DateConversion.getDateByTimePoint(twoDaysDate,
                timePoint);
        if (lastTime.before(twoDaysAgo)) {
            meetClinic.setIsOverTime(true);
        } else {
            meetClinic.setIsOverTime(false);
        }
        EvaluateDAO evaluateDAO = DAOFactory.getDAO(EvaluateDAO.class);
        meetClinic.setEvaStatus(evaluateDAO.isEvaluate(EvaluateConstant.BUSSTYPE_MEETCLINIC, meetClinicId));//评价状态
        map.put("meetClinic", meetClinic);
        map.put("cdrOtherdocs", cdrOtherdocs);
        map.put("patient", patient);
        map.put("meetClinicResultIds", mrIds);
        map.put("inDoctors", inDoctors);
        map.put("targetPhones", phones);
        map.put("count", count);
        // 2016-3-9 luf 系统消息跳转时，给予前端当前医生执行单号
        Integer resultId = 0;
        Boolean myTeam = false;
        Boolean readOnly = false;
        Integer target = 0;

        if (meetClinicResultId != null && meetClinicResultId > 0) {
            resultId = meetClinicResultId;
        } else if (smId > 0) {
            resultId = smId;
        } else if (emId > 0) {
            resultId = emId;
        } else if (tmId > 0) {
            resultId = tmId;
        }
        if (resultId > 0) {
            status = resultDAO.getExeStatusByResultId(resultId);
            target = resultDAO.getTargetByResultId(resultId);
            MeetClinicResult myResult = resultDAO.get(resultId);
            if (status == 8 && StringUtils.isNotBlank(myResult.getCause())) {
                map.put("refuseCause", myResult.getCause());
            }
        }
        List<DoctorGroup> dgs = dgDao.findByDoctorId(target);
        for (DoctorGroup dg : dgs) {
            if (dg.getMemberId() == doctorId) {
                myTeam = true;
                break;
            }
        }
        if (requestDoctor != doctorId) {
            statusText = exeStatusDic.getText(status);
            if (resultId == 0) {
                readOnly = true;//非团队执行医生只读
            } else {
                readOnly = false;
                MeetClinicResult myResult = resultDAO.getByMeetClinicResultId(resultId);
                Boolean meetCenter = myResult.getMeetCenter() == null ? false : myResult.getMeetCenter();
                map.put("meetCenter", meetCenter);//当前医生是否会诊中心
                if (meetCenter) {
                    map.put("meetCenterStatus", myResult.getMeetCenterStatus() == null ? 0 : myResult.getMeetCenterStatus());
                    map.put("meetCenterEnd", inDoctors.size() > 1);//是否显示会诊中心完成按钮
                }
            }
        }
        map.put("readOnly", readOnly);
        map.put("resultId", resultId);
        map.put("status", status);
        map.put("statusText", statusText);
        map.put("team", myTeam);
        return map;
    }

    /**
     * 会诊单详情(PC)
     *
     * @param meetClinicId       会诊单号
     * @param doctorId           当前登录医生内码
     * @param meetClinicResultId 会诊执行单号
     * @return HashMap<String, Object>
     * --meetClinic:会诊单信息,cdrOtherdocs:图片资料列表,patient：患者信息(全部),
     * meetClinicResultIds:执行单Id列表,inDoctors:参与医生列表(剔除拒绝),groupId:聊天群组id,
     * targetPhones:目标医生姓名及电话列表,status:详情单状态,statusText:状态名,
     * cancelbutton:是否显示取消会诊按钮,cancelCause:取消原因,refusecause:拒绝原因,
     * reportCount:回复/拒绝总数,totalCount:会诊总人数,requestDoctor:申请医生信息,resultId:当前医生执行单号,
     * preDocInfo:当前医生信息,team:是否团队执行单，myTeamName:团队名称,
     * readOnly:是否只读,meetCenter:当前医生是否会诊中心,meetCenterStatus:会诊中心接收状态
     * evaluates:评价信息列表
     * @author zhangsl
     * @Date 2016-12-15 16:57:00
     */
    @RpcService
    public Map<String, Object> getDetailByMeetClinicIdForPC(int meetClinicId,
                                                            int doctorId, int meetClinicResultId) {
        Map<String, Object> map = new HashMap<>();
        List<Object> phones = new ArrayList<>();
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        MeetClinicDAO mcDao = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        CdrOtherdocDAO cdrDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
        DoctorGroupDAO dgDao = DAOFactory.getDAO(DoctorGroupDAO.class);
        DoctorDAO dDao = DAOFactory.getDAO(DoctorDAO.class);
        Dictionary dic = null;
        Dictionary statusDic = null;
        Dictionary exeStatusDic = null;
        try {
            dic = DictionaryController.instance().get("eh.base.dictionary.Doctor");
            statusDic = DictionaryController.instance().get("eh.bus.dictionary.MeetClinicStatus");
            exeStatusDic = DictionaryController.instance().get("eh.bus.dictionary.ExeStatus");
        } catch (ControllerException e) {
            logger.error(e.getMessage());
        }

        Integer status = 0;
        String statusText = "待处理";
        MeetClinic meetClinic = mcDao.get(meetClinicId);
        if (meetClinic == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "会诊单不存在");
        }
        Doctor preDoc = dDao.getByDoctorId(doctorId);
        Doctor preDoctor = new Doctor();
        preDoctor.setName(preDoc.getName());
        preDoctor.setPhoto(preDoc.getPhoto());
        preDoctor.setGender(preDoc.getGender());
        map.put("preDocInfo", preDoctor);
        Integer requestDoctor = meetClinic.getRequestDoctor();
        meetClinic.setMobile(dDao.getMobileByDoctorId(requestDoctor));
        meetClinic
                .setRequestBusyFlag(dDao.getBusyFlagByDoctorId(requestDoctor));
        String mpiId = meetClinic.getMpiid();
        List<Otherdoc> cdrOtherdocs = cdrDAO.findByClinicTypeAndClinicId(2,
                meetClinicId);
        if (requestDoctor == doctorId) {
            status = meetClinic.getMeetClinicStatus();
            statusText = statusDic.getText(status);
            if (status == 9 && StringUtils.isNotBlank(meetClinic.getCancelCause())) {
                map.put("cancelCause", meetClinic.getCancelCause());
            }
        }
        Doctor reqDoctor = dDao.getByDoctorId(requestDoctor);
        map.put("requestDoctor", reqDoctor);
        // 添加聊天群组号
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        Group group = groupDAO.getByBussTypeAndBussId(2, meetClinicId);// 2-会诊
        if (group != null) {
            map.put("groupId", group.getGroupId());
        }
        Patient patient = patientDAO.get(mpiId);
        patient.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
        if (rd != null) {
            patient.setRelationPatientId(rd.getRelationDoctorId());
            patient.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                patient.setSignFlag(true);
            } else {
                patient.setSignFlag(false);
            }
            patient.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        }

        List<MeetClinicResult> mrs = resultDAO.findByMeetClinicId(meetClinicId);
        List<Integer> mrIds = new ArrayList<>();
        List<Object> inDoctors = new ArrayList<>();
        int count = 0;
        int totalCount = 0;
        Date lastTime = new Date(0);
        Integer tmId = 0;
        Integer emId = 0;
        Integer smId = 0;
        meetClinic.setRequestMode(MeetClinicConstant.MEETREQUESTMODE_DMHZ);
        for (MeetClinicResult mr : mrs) {
            Integer effeStatus = mr.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            HashMap<String, Object> phone = new HashMap<>();
            HashMap<String, Object> docMap = new HashMap<>();
            Integer showType = 0; //前端显示方式：0单个医生 1拼接团队
            Boolean team = false;
            String phoneName;
            String phoneNum;
            Doctor doc;
            Integer busyFlag = 0;
            Integer mrId = mr.getMeetClinicResultId();
            mrIds.add(mrId);

            Integer target = mr.getTargetDoctor();
            Integer exe = mr.getExeDoctor();
            Integer exeStatus = mr.getExeStatus();
            List<DoctorGroup> dgs = dgDao.findByDoctorId(target);
            if (mr.getMeetCenter() != null && mr.getMeetCenter()) {
                meetClinic.setRequestMode(MeetClinicConstant.MEETREQUESTMODE_HZZX);
                meetClinic.setCenterStatus(mr.getMeetCenterStatus());//会诊中心接收状态供前端判断会诊计划类
            } else {
                totalCount++;
            }

            if (dgs != null) {
                for (DoctorGroup dg : dgs) {
                    if (dg.getMemberId() == doctorId) {
                        team = true;
                        break;
                    }
                }
            }
            // 2016-3-9 luf 系统消息跳转时，给予前端当前医生执行单号
            if (mrId == meetClinicResultId || meetClinicResultId == 0) {//pc端前端无法判断来自自己团队还是个人
                if (target == doctorId) {
                    smId = mrId;
                } else if (team) {
                    if (exe != null) {
                        if (exe == doctorId) {
                            emId = mrId;
                        }
                    } else {
                        tmId = mrId;
                    }
                }
            }

            if (exeStatus != 8) {
                if (exe != null && exe > 0) {
                    doc = dDao.getByDoctorId(exe);
                    if (dgs != null && !dgs.isEmpty()) {
                        showType = 1;
                        docMap.put("teamName", dic.getText(target));
                    }
                    phoneName = dic.getText(exe);
                    phoneNum = dDao.getMobileByDoctorId(exe);
                    busyFlag = dDao.getBusyFlagByDoctorId(exe);
                } else {
                    doc = dDao.getByDoctorId(target);
                    phoneName = dic.getText(target);
                    phoneNum = dDao.getMobileByDoctorId(target);
                    busyFlag = dDao.getBusyFlagByDoctorId(target);
                }
                docMap.put("doc", doc);
                docMap.put("showType", showType);
                inDoctors.add(docMap);
                phone.put("name", phoneName);
                phone.put("phone", phoneNum);
                phone.put("busyFlag", busyFlag);
                phones.add(phone);
            }

            if ((exeStatus == 2 || exeStatus == 8) && (mr.getMeetCenter() == null || !mr.getMeetCenter())) {
                count++;
            }

            if (exeStatus >= 2) {
                Date endDate = mr.getEndTime();
                if (endDate.after(lastTime)) {
                    lastTime = endDate;
                }
            }
        }
        Date cancelTime = new Date();
        Date twoDaysDate = DateConversion.getDaysAgo(2);
        String timePoint = DateConversion.getDateFormatter(cancelTime,
                "HH:mm:ss");
        Date twoDaysAgo = DateConversion.getDateByTimePoint(twoDaysDate,
                timePoint);
        if (lastTime.before(twoDaysAgo)) {
            meetClinic.setIsOverTime(true);
        } else {
            meetClinic.setIsOverTime(false);
        }
        EvaluateDAO evaluateDAO = DAOFactory.getDAO(EvaluateDAO.class);
        meetClinic.setEvaStatus(evaluateDAO.isEvaluate(EvaluateConstant.BUSSTYPE_MEETCLINIC, meetClinicId));//评价状态
        map.put("meetClinic", meetClinic);
        map.put("cdrOtherdocs", cdrOtherdocs);
        map.put("patient", patient);
        map.put("meetClinicResultIds", mrIds);
        map.put("inDoctors", inDoctors);
        map.put("targetPhones", phones);
        map.put("reportCount", count);
        map.put("totalCount", totalCount);
        if (requestDoctor == doctorId) {
            map.put("cancelButton", count > 0 ? false : true);
        }
        // 2016-3-9 luf 系统消息跳转时，给予前端当前医生执行单号
        Integer resultId = 0;
        Boolean myTeam = false;
        Boolean readOnly = false;
        Integer target = 0;

        if (meetClinicResultId > 0) {
            resultId = meetClinicResultId;
        } else if (smId > 0) {
            resultId = smId;
        } else if (emId > 0) {
            resultId = emId;
        } else if (tmId > 0) {
            resultId = tmId;
        }
        if (resultId > 0) {
            status = resultDAO.getExeStatusByResultId(resultId);
            target = resultDAO.getTargetByResultId(resultId);
            MeetClinicResult myResult = resultDAO.get(resultId);
            if (status == 8 && StringUtils.isNotBlank(myResult.getCause())) {
                map.put("refuseCause", myResult.getCause());
            }
            map.put("meetReport", myResult.getMeetReport());
        }
        List<DoctorGroup> dgs = dgDao.findByDoctorId(target);
        for (DoctorGroup dg : dgs) {
            if (dg.getMemberId() == doctorId) {
                myTeam = true;
                break;
            }
        }
        if (myTeam) {
            map.put("myTeamName", dDao.getNameById(target));
        }
        if (requestDoctor != doctorId) {
            statusText = exeStatusDic.getText(status);
            if (resultId == 0) {
                readOnly = true;//非团队执行医生只读
            } else {
                readOnly = false;
                MeetClinicResult myResult = resultDAO.getByMeetClinicResultId(resultId);
                Boolean meetCenter = myResult.getMeetCenter() == null ? false : myResult.getMeetCenter();
                map.put("meetCenter", meetCenter);//是否会诊中心
                if (meetCenter) {
                    map.put("meetCenterStatus", myResult.getMeetCenterStatus() == null ? 0 : myResult.getMeetCenterStatus());
                    map.put("meetCenterEnd", inDoctors.size() > 1);//是否显示会诊中心完成按钮
                }
            }
        }
        map.put("readOnly", readOnly);
        map.put("resultId", resultId);
        map.put("status", status);
        map.put("statusText", statusText);
        map.put("team", myTeam);
        //pc端直接返回评价信息
        if (meetClinic.getEvaStatus()) {
            List<Evaluate> evaluates = evaluateDAO.findEvaluateByBussTypeAndBussId(EvaluateConstant.BUSSTYPE_MEETCLINIC, meetClinicId);
            map.put("evaluates", evaluates);
        }
        return map;
    }

    /**
     * 会诊中心修改安排时间服务
     *
     * @param meetClinicId
     * @param planTime
     */
    @RpcService
    public Boolean updateMeetPlanTimeForCenter(int meetClinicId, int doctorId, Date planTime) {
        if (planTime == null || planTime.before(new Date())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "会诊时间需大于当前时间！");
        }
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinic mc = meetClinicDAO.getByMeetClinicId(meetClinicId);
        if (mc.getMeetClinicStatus() < 2) {//会诊未结束
            meetClinicDAO.updatePlanTimeByMeetClinicId(meetClinicId, planTime);
            //发送会诊时间修改至聊天页
            ConsultMessageService msgService = AppContextHolder.getBean("eh.consultMessageService", ConsultMessageService.class);
            EasemobIMService imService = AppContextHolder.getBean("eh.imService", EasemobIMService.class);
            DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Doctor exeDoc = doctorDao.getByDoctorId(doctorId);
            Patient patient = patientDAO.getByMpiId(mc.getMpiid());
            Integer doctorUrt = Util.getUrtForDoctor(exeDoc.getMobile());
            String msgContent = "会诊时间调整为：" + DateConversion.getDateFormatter(planTime, DateConversion.DEFAULT_DATETIME_WITHOUTSECOND);
            Map<String, String> ext = Maps.newHashMap();
            ext.put("busId", Integer.toString(meetClinicId));
            ext.put("busType", "1");    //会诊是 1 咨询可以是2
            ext.put("name", exeDoc.getName());
            ext.put("groupName", patient.getPatientName() + "的会诊");
            ext.put("avatar", exeDoc.getPhoto() == null ? null : exeDoc.getPhoto().toString());
            ext.put("gender", exeDoc.getGender());
            ext.put("uuid", String.valueOf(UUID.randomUUID()));
            //发送环信消息
            imService.sendMsgToGroupByDoctorUrt(doctorUrt, mc.getSessionID(), msgContent, ext);
            //消息记录至数据库
            msgService.doctorSendMsgWithConsultId("", ConsultConstant.BUS_TYPE_MEET, meetClinicId, String.valueOf(MsgTypeEnum.TEXT.getId()), msgContent);
            logger.info("doctor[{}] update MeetClinic[{}] planTime to huanxin:[{}]", doctorId, meetClinicId, msgContent);
            return true;
        } else {
            return false;
        }
    }

    /**
     * 会诊获取详情单接口
     *
     * @param meetClinicId       会诊申请单号
     * @param doctorId           当前登陆医生内码
     * @param meetClinicResultId 会诊执行单号
     * @param flag               标志-0纳里医生app端1pc端
     * @return Map<String, Object>
     */
    @RpcService
    public Map<String, Object> getMeetclinicDetail(int meetClinicId, int doctorId, Integer meetClinicResultId, int flag) {
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        if (meetClinicResultId != null && meetClinicResultId > 0) {
            MeetClinicResult clinicResult = resultDAO.getByMeetClinicResultId(meetClinicResultId);
            if (clinicResult == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "clinicResult is required!");
            }
            Integer effeStatus = clinicResult.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您已被移出该会诊~");
            }
        }
        if (flag == 0) {
            return this.getDetailByMeetClinicIdWithResultId(meetClinicId, doctorId, meetClinicResultId);
        } else {
            return this.getDetailByMeetClinicIdForPC(meetClinicId, doctorId, meetClinicResultId);
        }
    }

    /**
     * 根据meetclinicId获取会诊相关信息-供信令调用
     *
     * @param meetclinicId 会诊申请单号
     * @return
     */
    @RpcService
    public String getPatientNameByMeetclinicId(int meetclinicId) {
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinic meetClinic = meetClinicDAO.getByMeetClinicId(meetclinicId);
        if (meetClinic == null || meetClinic.getMpiid() == null) {
            return null;
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        return patientDAO.getNameByMpiId(meetClinic.getMpiid());
    }

}
