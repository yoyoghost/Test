package eh.bus.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.dao.*;
import eh.base.user.UserSevice;
import eh.bus.asyndobuss.bean.BussAcceptEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.MeetClinicConstant;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.base.Employment;
import eh.entity.base.PatientFeedback;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicReportTable;
import eh.entity.bus.MeetClinicResult;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class MeetClinicResultDAO extends
        HibernateSupportDelegateDAO<MeetClinicResult> implements
        DBDictionaryItemLoader<MeetClinicResult> {

    public MeetClinicResultDAO() {
        super();
        this.setEntityName(MeetClinicResult.class.getName());
        this.setKeyField("meetClinicResultId");
    }

    @RpcService
    @DAOMethod
    public abstract List<MeetClinicResult> findByMeetClinicId(int meetClinicId);

    @RpcService
    @DAOMethod(sql = "from MeetClinicResult where meetClinicId=:meetClinicId order by meetReport desc,cause desc")
    public abstract List<MeetClinicResult> findByMeetOrderByReport(@DAOParam("meetClinicId") int meetClinicId);

    @DAOMethod(sql = "from MeetClinicResult where meetClinicId in:meetClinicIds order by meetClinicId desc")
    public abstract List<MeetClinicResult> findByMeetClinicIds(@DAOParam("meetClinicIds") List<Integer> meetClinicIds);

    @RpcService
    @DAOMethod(sql = "select meetClinicId from MeetClinicResult where meetClinicResultId=:meetClinicResultId")
    public abstract Integer getMeetClinicIdByResultId(
            @DAOParam("meetClinicResultId") int meetClinicResultId);

    @DAOMethod(sql = "select meetClinicResultId from MeetClinicResult where meetClinicId=:meetClinicId")
    public abstract List<Integer> findResultIdBymeetClinicId(
            @DAOParam("meetClinicId") int meetClinicId);

    @RpcService
    @DAOMethod(sql = "from MeetClinicResult where meetClinicResultId in(:meetClinicResultIds)")
    public abstract List<MeetClinicResult> findBymeetClinicResultIds(@DAOParam("meetClinicResultIds") List<Integer> meetClinicResultIds);

    /**
     * 我的会诊列表
     *
     * @param doctorId 当前登陆医生内码
     * @param flag     -0全部（未完成：待处理，处理中；已结束：已会诊，取消，拒绝），1未处理（待处理、处理中），2已完成（已会诊）
     * @param mark     -0未完成，1已结束
     * @param start    分页开始位置
     * @param limit    每页限制条数
     * @return HashMap<String, List<HashMap<String, Object>>>
     * @author luf
     */
    @RpcService
    public HashMap<String, List<HashMap<String, Object>>> findMeetClinicResult(
            int doctorId, int flag, int mark, int start, int limit) {
        HashMap<String, List<HashMap<String, Object>>> result = new HashMap<>();
        List<MeetClinicResult> mrs = this.findResultsByMark(doctorId, flag,
                mark, start, limit);
        if (flag == 0) {
            if (mark == 0) {
                result.put("unfinished", convertForResult(mrs, doctorId));
                if (mrs == null) {
                    mrs = new ArrayList<>();
                }
                if (mrs.size() < limit) {
                    List<MeetClinicResult> finished = this.findResultsByMark(
                            doctorId, 0, 1, 0, limit - mrs.size());
                    result.put("completed",
                            convertForResult(finished, doctorId));
                } else {
                    result.put("completed",
                            new ArrayList<HashMap<String, Object>>());
                }
            } else {
                result.put("completed", convertForResult(mrs, doctorId));
                result.put("unfinished",
                        new ArrayList<HashMap<String, Object>>());
            }
        } else {
            result.put("unfinished", convertForResult(mrs, doctorId));
            result.put("completed", new ArrayList<HashMap<String, Object>>());
        }
        return result;
    }

    /**
     * 供 我的会诊列表 调用
     *
     * @param doctorId 当前登陆医生内码
     * @param flag     -0全部（未完成：待处理，处理中；已结束：已会诊，取消，拒绝），1未处理（待处理、处理中），2已完成（已会诊）
     * @param mark     -0未完成，1已结束
     * @param start    分页开始位置
     * @param limit    每页限制条数
     * @return List<MeetClinic>
     * @author luf
     */
    public List<MeetClinicResult> findResultsByMark(final int doctorId,
                                                    final int flag, final int mark, final int start, final int limit) {
        HibernateStatelessResultAction<List<MeetClinicResult>> action = new AbstractHibernateStatelessResultAction<List<MeetClinicResult>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
                List<Integer> groups = dao.findDoctorIdsByMemberId(doctorId);
                String pending = "exeStatus=0";
                String treatment = "exeStatus=1";
                String completed = "exeStatus=2";
                String refuse = "exeStatus=8";
                String cancel = "exeStatus=9";
                StringBuffer hql = new StringBuffer(
                        "select r From MeetClinicResult r,MeetClinic c where "
                                + "(targetDoctor=:doctorId or exeDoctor=:doctorId ");

                if (groups != null && !groups.isEmpty()) {
                    hql.append("or(targetDoctor in :groups and (exeStatus=0 or "
                            + "(exeDoctor=:doctorId and exeStatus>0)))");
                }

                hql.append(") AND r.meetClinicId=c.meetClinicId and(");
                String condition = null;
                switch (flag) {
                    case 0:
                        if (mark == 1) {
                            condition = completed + " or " + refuse + " or "
                                    + cancel;
                            break;
                        }
                    case 1:
                        condition = pending + " or " + treatment;
                        break;
                    case 2:
                        condition = completed;
                    default:
                        break;
                }
                hql.append(condition);
                hql.append(") and r.effectiveStatus=0 order by c.requestTime desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                if (groups != null && !groups.isEmpty()) {
                    q.setParameterList("groups", groups);
                }
                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供 我的会诊列表 调用
     *
     * @param meetClinicResults 执行单列表
     * @param doctorId          当前登陆医生内码
     * @return List<HashMap<String, Object>>
     * @author luf
     */
    public List<HashMap<String, Object>> convertForResult(
            List<MeetClinicResult> meetClinicResults, int doctorId) {
        MeetClinicDAO clinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        List<HashMap<String, Object>> result = new ArrayList<>();
        for (MeetClinicResult mr : meetClinicResults) {
            HashMap<String, Object> target = new HashMap<>();
            int meetClinicId = mr.getMeetClinicId();
            MeetClinic mc = clinicDAO.get(meetClinicId);
            String mpiId = mc.getMpiid();
            Boolean teams = doctorDAO.getTeamsByDoctorId(mr.getTargetDoctor());
            mr.setTargetTeams(teams);
            mc.setRequestString(DateConversion.convertRequestDateForBuss(mc
                    .getRequestTime()));
            Patient patient = patientDAO.get(mpiId);
            RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
            if (rd != null) {
                patient.setRelationFlag(true);
                if (rd.getRelationType() == 0) {
                    patient.setSignFlag(true);
                } else {
                    patient.setSignFlag(false);
                }
                patient.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                        .getRelationDoctorId()));
            }
            target.put("meetClinicResult", mr);
            target.put("meetClinic", mc);
            target.put("patient", patient);
            result.add(target);
        }
        return result;
    }

    /**
     * @param meetClinicId 会诊单Id
     * @return HashMap<String,Object>
     * @throws ControllerException
     * @throws
     * @Class eh.bus.dao.MeetClinicResultDAO.java
     * @Title: findProgressList
     * @author Zhongzx
     * @Date 2015-12-30上午11:14:58
     */
    @RpcService
    public List<MeetClinicReportTable> findProgressList(Integer meetClinicId)
            throws ControllerException {
        // meetClinicId 是必需的
        if (meetClinicId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "meetClinicId is needed");
        }
        List<MeetClinicResult> clinicResults = this
                .findByMeetClinicId(meetClinicId);
        List<MeetClinicReportTable> clinicReportTables = new ArrayList<>();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        DoctorGroupDAO dgDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        Dictionary dic = DictionaryController.instance().get(
                "eh.base.dictionary.Doctor");
        // 将执行单中所需要的部分存到clinicReportTables中
        for (MeetClinicResult mr : clinicResults) {
            Integer effStatus = mr.getEffectiveStatus();
            if (effStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effStatus)) {
                continue;
            }
            Integer meetClinicReport = 0;
            String meetClinicReportText = "未回复";
            String exeStatusText = "未进行";
            // 定义存放参数的对象
            MeetClinicReportTable clinicReportTable = new MeetClinicReportTable();
            clinicReportTable.setMeetClinicResultId(mr.getMeetClinicResultId());
            Integer target = mr.getTargetDoctor();
            Integer exe = mr.getExeDoctor();
            Doctor doctor = doctorDAO.getByDoctorId(target);
            List<DoctorGroup> dgs = dgDAO.findByDoctorId(target);
            StringBuilder inNames = new StringBuilder();
            if (null != dgs && !dgs.isEmpty()) {
                if (null != exe && exe > 0) {
                    doctor = doctorDAO.getByDoctorId(exe);
                    inNames.append(dic.getText(exe));
                    inNames.append("(");
                    inNames.append(dic.getText(target));
                    inNames.append(")");
                } else {
                    inNames.append(dic.getText(target));
                }
            } else {
                inNames.append(dic.getText(target));
            }
            clinicReportTable.setTargetDoctorName(inNames.toString());
            if (!StringUtils.isEmpty(mr.getMeetReport())) {
                meetClinicReport = 1;// 0未回复，1已回复
                meetClinicReportText = "已回复";
            }
            clinicReportTable.setMeetClinicReport(meetClinicReport);
            clinicReportTable.setMeetClinicReportText(meetClinicReportText);
            clinicReportTable.setExeStatus(mr.getExeStatus());
            clinicReportTable.setPhoto(doctor.getPhoto());
            if (mr.getExeStatus() == 1) {
                exeStatusText = "进行中";
            }
            if (mr.getExeStatus() == 2) {
                exeStatusText = "已完成";
            }
            if (mr.getExeStatus() == 8) {
                exeStatusText = "已拒绝";
            }
            clinicReportTable.setExeStatusText(exeStatusText);
            // 对列表添加对象
            clinicReportTables.add(clinicReportTable);
        }
        return clinicReportTables;
    }

    /**
     * 会诊意见列表
     *
     * @param mrIds    会诊执行单序号列表
     * @param doctorId 当前登录医生内码
     * @param flag     标志--0全部1结束2拒绝
     * @return List<Object>
     * --HashMap=>meetClinicResult:会诊执行单信息,doctor:医生信息,enable:是否可点赞
     * @author luf
     */
    @RpcService
    public List<Object> findReportByList(List<Integer> mrIds, int doctorId,
                                         int flag) {
        UserSevice userSevice = new UserSevice();
        int reqDocUid = userSevice.getDoctorUrtIdByDoctorId(doctorId);

        List<Object> targets = new ArrayList<>();
        PatientFeedbackDAO pfDao = DAOFactory.getDAO(PatientFeedbackDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        EmploymentDAO empDAO = DAOFactory.getDAO(EmploymentDAO.class);

        for (Integer mrId : mrIds) {
            MeetClinicResult mr = this.get(mrId);
            Integer effeStatus = mr.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            Boolean enable = true;
            Integer exeStatus = mr.getExeStatus();
            //zhangsl 2017-05-26 14:03:16 剔除会诊中心意见
            if (mr.getMeetCenter() != null && mr.getMeetCenter() && exeStatus != 8) {
                continue;
            }
            if (flag == 0 && exeStatus != 8 && exeStatus != 2) {
                continue;
            }
            if (flag == 1 && exeStatus != 2) {
                continue;
            }
            if (flag == 2 && exeStatus != 8) {
                continue;
            }
            HashMap<String, Object> map = new HashMap<>();
            String mcId = mr.getMeetClinicId().toString();
            Integer exe = mr.getExeDoctor();

            List<PatientFeedback> feedBacks = pfDao.findPfsByServiceAndUser(exe, "2",
                    mcId, reqDocUid, "doctor");
            if (feedBacks != null && !feedBacks.isEmpty()) {
                mr.setFeedBack(true);
            } else {
                mr.setFeedBack(false);
            }
            mr.setFeedBackNum(pfDao.getClinicNumByDoctor(exe, "2", mcId));
            mr.setShowTime(DateConversion.convertRequestDateForBuss(mr
                    .getEndTime()));

            if (exe == doctorId) {
                enable = false;
            }

            Doctor doctor = doctorDAO.get(exe);
            Employment emp = empDAO.getPrimaryEmpByDoctorId(exe);
            doctor.setDepartment(emp.getDepartment());

            ConsultSetDAO setDAO = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet cs = setDAO.getById(doctorId);
            Integer isOpenTrans = null;
            if (cs != null && cs.getTransferStatus() != null) {
                isOpenTrans = cs.getTransferStatus();
            }
            doctor.setIsOpen(isOpenTrans);

            map.put("meetClinicResult", mr);
            map.put("doctor", doctor);
            map.put("enable", enable);
            targets.add(map);
        }
        return targets;
    }

    /**
     * 会诊开始服务
     *
     * @param meetClinicResult 会诊执行单信息
     * @return Boolean
     * @author luf
     */
    @RpcService
    public Boolean startMeetClinic(MeetClinicResult meetClinicResult) {
        Integer meetClinicResultId = meetClinicResult.getMeetClinicResultId();
        if (meetClinicResultId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== meetClinicResultId is required =====");
        }
        Integer meetClinicId = meetClinicResult.getMeetClinicId();
        if (meetClinicId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== meetClinicId is required =====");
        }
        Integer doctorId = meetClinicResult.getExeDoctor();
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== doctorId is required =====");
        }

        DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        List<MeetClinicResult> mrs = this.findByMeetClinicId(meetClinicId);
        int targetCount = 0;
        int groupCount = 0;
        int exeCount = 0;
        int start = 0;
        for (MeetClinicResult mr : mrs) {
            Integer effeStatus = mr.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            Integer target = mr.getTargetDoctor();
            Integer mrId = mr.getMeetClinicResultId();
            if (mrId.equals(meetClinicResultId) && target.equals(doctorId)) {
                start++;
            }
            if (target.equals(doctorId)) {
                targetCount++;
            }
            if (groupDAO.getByDoctorIdAndMemberId(target, doctorId) != null) {
                groupCount++;
                if (doctorId.equals(mr.getExeDoctor())) {
                    exeCount++;
                }
            }
        }

        if (start <= 0) {
            if (targetCount > 0) {
                if (groupCount > 0) {//个人和所在团队均被邀请
                    throw new DAOException(609, "您已参与这次会诊");
                }
            } else {
                if (groupCount > 1 && exeCount > 0) {//多个所在团队均被邀请
                    throw new DAOException(609, "您已参与这次会诊");
                }
            }
        }

        StartMeetClinicDAO startDao = DAOFactory
                .getDAO(StartMeetClinicDAO.class);
        Boolean b = startDao.startMeetClinicNew(meetClinicResult);
        if (!b) {
            MeetClinicResult result = this.get(meetClinicResultId);
            if (result.getExeStatus() == 9) {
                throw new DAOException(609, "抱歉，对方医生已取消该会诊申请");
            } else if (result.getExeDoctor() != null && !doctorId.equals(result.getExeDoctor())) {
                throw new DAOException(609, "啊哦！您慢了一步，已有团队其他成员参与...");
            }
        }
        AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);
        asynDoBussService.fireEvent(new BussAcceptEvent(meetClinicResultId, BussTypeConstant.MEETCLINIC, doctorId));
        return b;
    }

    @RpcService
    @DAOMethod(sql = "select exeStatus from MeetClinicResult where meetClinicResultId=:meetClinicResultId")
    public abstract Integer getExeStatusByResultId(
            @DAOParam("meetClinicResultId") Integer meetClinicResultId);

    @RpcService
    @DAOMethod(sql = "select targetDoctor from MeetClinicResult where meetClinicResultId=:meetClinicResultId")
    public abstract Integer getTargetByResultId(
            @DAOParam("meetClinicResultId") Integer meetClinicResultId);

    @RpcService
    @DAOMethod
    public abstract MeetClinicResult getByMeetClinicResultId(int meetClinicResultId);

    /**
     * 更新会诊中心接收状态
     *
     * @param meetClinicResultId
     * @param meetCenterStatus
     */
    @DAOMethod(sql = "update MeetClinicResult set meetCenterStatus=:meetCenterStatus where meetClinicResultId=:meetClinicResultId")
    public abstract void updateMeetCenterStatus(@DAOParam("meetClinicResultId") int meetClinicResultId,
                                                @DAOParam("meetCenterStatus") int meetCenterStatus);

    public Integer updateEffectiveStatus(final int meetClinicId, final List<Integer> resultIds) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                List<Integer> exeStatus = new ArrayList<>();
                exeStatus.add(MeetClinicConstant.EXESTATUS_PENDING);
                exeStatus.add(MeetClinicConstant.EXESTATUS_INHAND);
                String hql = "update MeetClinicResult set effectiveStatus=:effectiveStatus where exeStatus in(:exeStatus) " +
                        "and meetClinicResultId in(:resultIds) and effectiveStatus<>:effectiveStatus and meetClinicId=:meetClinicId";
                Query query = statelessSession.createQuery(hql);
                query.setParameter("effectiveStatus", MeetClinicConstant.EFFECTIVESTATUS_INVALID);
                query.setParameterList("exeStatus", exeStatus);
                query.setParameterList("resultIds", resultIds);
                query.setParameter("meetClinicId", meetClinicId);
                setResult(query.executeUpdate());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @DAOMethod(sql = "select effectiveStatus from MeetClinicResult where meetClinicResultId=:meetClinicResultId")
    public abstract Integer getEffectiveStatusByMeetClinicResultId(@DAOParam("meetClinicResultId") int meetClinicResultId);
}
