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
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.base.user.UserSevice;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.MeetClinicConstant;
import eh.bus.service.meetclinic.MeetClinicPushService;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.base.PatientFeedback;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.bus.MeetClinicResult;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.msg.Group;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.GroupDAO;
import eh.utils.DateConversion;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public abstract class MeetClinicDAO extends
        HibernateSupportDelegateDAO<MeetClinic> {
    public static final Logger log = Logger.getLogger(MeetClinicDAO.class);

    public MeetClinicDAO() {
        super();
        this.setEntityName(MeetClinic.class.getName());
        this.setKeyField("meetClinicId");
    }

    @RpcService
    @DAOMethod(sql = "select meetClinicStatus from MeetClinic where meetClinicId=:meetClinicId")
    public abstract Integer getMeetClinicStatusById(
            @DAOParam("meetClinicId") Integer meetClinicId);

    /**
     * 结束会诊申请单，供强制结束会诊服务调用(供申请医生结束会诊调用)
     *
     * @param meetClinicStatus
     * @param meetClinicId
     * @author LF
     */
    @RpcService
    @DAOMethod
    public abstract void updateMeetClinicStatusByMeetClinicId(
            Integer meetClinicStatus, Integer meetClinicId);

    /**
     * 更新群聊ID到会诊单中（供会诊申请服务requestMeetClinic调用）
     *
     * @param sessionID
     * @param meetClinicId
     * @author LF
     */
    @DAOMethod
    @RpcService
    public abstract void updateSessionIDByMeetClinicId(String sessionID,
                                                       Integer meetClinicId);

    /**
     * 更新群聊开始时间到会诊单中（供会诊申请服务requestMeetClinic调用）
     *
     * @param sessionStartTime
     * @param meetClinicId
     * @author LF
     */
    @DAOMethod
    @RpcService
    public abstract void updateSessionStartTimeByMeetClinicId(
            Date sessionStartTime, Integer meetClinicId);

    /**
     * 结束会诊申请单,供申请医生结束会诊调用
     *
     * @param meetClinicStatus
     * @param cancelCause
     * @param meetClinicId
     * @author LF
     */
    @DAOMethod(sql = "Update MeetClinic set meetClinicStatus=:meetClinicStatus,cancelCause=:cancelCause where meetClinicId=:meetClinicId")
    public abstract void updateMeetClinicStatusAndCancelCauseByMeetClinicId(
            @DAOParam("meetClinicStatus") Integer meetClinicStatus,
            @DAOParam("cancelCause") String cancelCause,
            @DAOParam("meetClinicId") Integer meetClinicId);

    @DAOMethod(sql = "UPDATE MeetClinicResult SET exeStatus=8,endTime=NOW(),cause=:cause WHERE meetClinicResultId=:meetClinicResultId AND exeStatus<2")
    public abstract void updateStatusToRefusedOld(
            @DAOParam("meetClinicResultId") Integer meetClinicResultId,
            @DAOParam("cause") String cause);

    /**
     * 更新会话结束时间(供会诊结束服务调用)
     *
     * @param sessionEndTime
     * @param meetClinicId
     * @author LF
     */
    @DAOMethod
    public abstract void updateSessionEndTimeByMeetClinicId(
            Date sessionEndTime, Integer meetClinicId);

    /**
     * 会诊医生拒绝服务
     *
     * @param meetClinicResultId
     * @author LF
     */
    @RpcService
    public Boolean updateStatusToRefused(Integer meetClinicResultId,
                                         String cause) {
        MeetClinicResultDAO mrDao = DAOFactory.getDAO(MeetClinicResultDAO.class);
        MeetClinicResult mr = mrDao.get(meetClinicResultId);
        //zhangsl 2017-05-25 17:57:31 统一pc、app拒绝流程
        return this.refuseMeetClinic(mr.getMeetClinicId(), cause, meetClinicResultId);
    }

    /**
     * @param meetClinicResultId 执行单ID
     * @param cause              拒绝原因
     * @return Boolean
     * @throws
     * @Class eh.bus.dao.MeetClinicDAO.java
     * @Title: refuseMeetClinicResult
     * @Description: 参考updateStatusToRefused，医生拒绝会诊服务
     * @author Zhongzx
     * @Date 2015-12-30下午2:54:20
     */
    @RpcService
    public Boolean refuseMeetClinicResult(Integer meetClinicId,
                                          Integer meetClinicResultId, String cause) {

        log.info("会诊医生拒绝服务(updateStatusToRefused)-meetClinicResultId="
                + meetClinicResultId + ";cause=" + cause);

        MeetClinicResultDAO mrDao = DAOFactory.getDAO(MeetClinicResultDAO.class);
        MeetClinicResult mr = mrDao.get(meetClinicResultId);
        Integer effeStatus = mr.getEffectiveStatus();
        if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您已被移出该会诊~");
        }
        if (mr.getExeStatus() == 9) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，对方医生已取消该会诊申请");
        }
        if (mr.getExeStatus() >= 2) {
            return false;
        }
        // 执行单状态更新为拒绝
        updateStatusToRefusedOld(meetClinicResultId, cause);
        if (mr.getMeetCenter() != null && mr.getMeetCenter()) {
            mrDao.updateMeetCenterStatus(meetClinicResultId, 2);//更新接收状态为拒绝
        }
        // 该医生退出讨论组
        DAOFactory.getDAO(GroupDAO.class).deleteUserFromGroup(2, meetClinicId,
                mr.getExeDoctor());
        return true;
    }

    @RpcService
    @DAOMethod
    public abstract MeetClinic getByMeetClinicId(Integer meetClinicId);

    /**
     * 获取会诊单信息服务+其他资料
     *
     * @param meetClinicId
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-4-12 下午4:47:34
     */
    @RpcService
    public List<MeetClinicAndResult> getMeetClinicAndCdrOtherdoc(
            final int meetClinicId) {
        List<MeetClinicAndResult> list1 = null;
        HibernateStatelessResultAction<List<MeetClinicAndResult>> action = new AbstractHibernateStatelessResultAction<List<MeetClinicAndResult>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.bus.MeetClinicAndResult(mc,mr,pt) from MeetClinic mc,MeetClinicResult mr,Patient pt where mc.meetClinicId =:meetClinicId and mr.meetClinicId =:meetClinicId and pt.mpiId = mc.mpiid";
                Query q = ss.createQuery(hql);
                q.setParameter("meetClinicId", meetClinicId);
                @SuppressWarnings("unchecked")
                List<MeetClinicAndResult> list = q.list();

                int doctorId = (list.get(0)).getMc().getRequestDoctor();
                hql = "SELECT mobile FROM Doctor WHERE doctorId=:doctorId";
                Query q2 = ss.createQuery(hql);
                q2.setParameter("doctorId", doctorId);
                String mobile = (String) (q2.uniqueResult());
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                Integer requestBusyFlag = doctorDAO
                        .getBusyFlagByDoctorId(doctorId);
                Doctor reqDoctor = doctorDAO.get(doctorId);

                for (int i = 0; i < list.size(); i++) {
                    list.get(i).setMobile(mobile);
                    int targetDoctorId = list.get(i).getMr().getTargetDoctor();
                    hql = "SELECT mobile,teams FROM Doctor WHERE doctorId=:doctorId";
                    Query q3 = ss.createQuery(hql);
                    q3.setParameter("doctorId", targetDoctorId);
                    Object[] objs = (Object[]) q3.uniqueResult();
                    String targetMobile = (String) objs[0];
                    Boolean targetTeams = (Boolean) objs[1];
                    list.get(i).setTargetMobile(targetMobile);
                    list.get(i).setTargetTeams(targetTeams);
                    // 添加聊天群组号
                    GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
                    Group group = groupDAO.getByBussTypeAndBussId(2, list
                            .get(i).getMc().getMeetClinicId());// 2-会诊
                    if (group != null) {
                        list.get(i).setGroupId(group.getGroupId());
                    }

                    // 获取其他资料列表
                    Integer clinicId = list.get(i).getMc().getMeetClinicId();

                    List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(
                            CdrOtherdocDAO.class).findByClinicTypeAndClinicId(
                            2, clinicId);
                    list.get(i).setCdrOtherdocs(cdrOtherdocs);

                    // 获取医生忙闲状态
                    Integer exeDoctor = list.get(i).getMr().getExeDoctor() == null ? null
                            : list.get(i).getMr().getExeDoctor();
                    Integer targetBusyFlag = null;
                    if (exeDoctor != null) {
                        targetBusyFlag = doctorDAO
                                .getBusyFlagByDoctorId(exeDoctor);
                    }
                    list.get(i).getMc().setRequestBusyFlag(requestBusyFlag);
                    list.get(i).getMr().setTargetBusyFlag(targetBusyFlag);

                    // 获取医生对象
                    list.get(i).getMc().setDoctor(reqDoctor);
                    Doctor tarDoctor = doctorDAO.get(targetDoctorId);
                    list.get(i).getMr().setDoctor(tarDoctor);

                    UserSevice userSevice = new UserSevice();
                    int reqDocUid = userSevice
                            .getDoctorUrtIdByDoctorId(doctorId);

                    // 获取是否点评
                    PatientFeedbackDAO pfDao = DAOFactory
                            .getDAO(PatientFeedbackDAO.class);
                    List<PatientFeedback> feedBacks = pfDao.findPfsByServiceAndUser(
                            targetDoctorId, "2", list.get(i).getMr()
                                    .getMeetClinicId().toString(), reqDocUid,
                            "doctor");

                    if (feedBacks != null && !feedBacks.isEmpty()) {
                        list.get(i).getMr().setFeedBack(true);
                    } else {
                        list.get(i).getMr().setFeedBack(false);
                    }
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        list1 = action.getResult();
        return list1;
    }


    /**
     * 获取会诊单信息服务+其他资料(PC)
     *
     * @param meetClinicId       会诊申请单编号
     * @param preDoctorId        当前医生内码
     * @param meetClinicResultId 执行单编号
     * @return
     * @throws DAOException
     * @author zhangsl
     * @date 2016-08-22 下午3:47:34
     */
    @RpcService
    public MeetClinicAndResult getMeetClinicAndCdrOtherdocPC(
            final Integer meetClinicId, final Integer preDoctorId, final Integer meetClinicResultId) {
        if (meetClinicId == null || preDoctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "meetClinic and preDoctorId is required!");
        }
        HibernateStatelessResultAction<MeetClinicAndResult> action = new AbstractHibernateStatelessResultAction<MeetClinicAndResult>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.bus.MeetClinicAndResult(mc,pt) from MeetClinic mc,Patient pt where mc.meetClinicId =:meetClinicId and pt.mpiId = mc.mpiid";
                Query q = ss.createQuery(hql);
                q.setParameter("meetClinicId", meetClinicId);
                @SuppressWarnings("unchecked")
                MeetClinicAndResult mar = (MeetClinicAndResult) q.list().get(0);
                MeetClinicResultDAO meetClinicResultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
                mar.setMeetClinicResults(meetClinicResultDAO.findByMeetOrderByReport(meetClinicId));

                int doctorId = (mar.getMc().getRequestDoctor());
                hql = "SELECT mobile FROM Doctor WHERE doctorId=:doctorId";
                Query q2 = ss.createQuery(hql);
                q2.setParameter("doctorId", doctorId);
                String mobile = (String) (q2.uniqueResult());
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                Integer requestBusyFlag = doctorDAO
                        .getBusyFlagByDoctorId(doctorId);
                Doctor reqDoctor = doctorDAO.get(doctorId);
                mar.setMobile(mobile);
                mar.getMc().setRequestBusyFlag(requestBusyFlag);
                mar.getMc().setDoctor(reqDoctor);
                // 添加聊天群组号
                GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
                Group group = groupDAO.getByBussTypeAndBussId(2,
                        mar.getMc().getMeetClinicId());// 2-会诊
                if (group != null) {
                    mar.setGroupId(group.getGroupId());
                }
                // 获取其他资料列表
                List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(
                        CdrOtherdocDAO.class).findByClinicTypeAndClinicId(
                        2, mar.getMc().getMeetClinicId());
                mar.setCdrOtherdocs(cdrOtherdocs);

                UserSevice userSevice = new UserSevice();
                int reqDocUid = userSevice
                        .getDoctorUrtIdByDoctorId(preDoctorId);
                PatientFeedbackDAO pfDao = DAOFactory
                        .getDAO(PatientFeedbackDAO.class);

                mar.setTargetTeams(false);
                Boolean needMatch = meetClinicResultId != null && !preDoctorId.equals(mar.getMc().getRequestDoctor());
                List<MeetClinicResult> mrs = mar.getMeetClinicResults();
                for (int i = 0; i < mrs.size(); i++) {
                    Integer targetDoctorId = mrs.get(i).getTargetDoctor();
                    hql = "SELECT mobile,teams FROM Doctor WHERE doctorId=:doctorId";
                    Query q3 = ss.createQuery(hql);
                    q3.setParameter("doctorId", targetDoctorId);
                    Object[] objs = (Object[]) q3.uniqueResult();
                    String targetMobile = (String) objs[0];
                    Boolean targetTeams = (Boolean) objs[1];
                    mrs.get(i).setTargetMobile(targetMobile);
                    mrs.get(i).setTargetTeams(targetTeams);
                    if (needMatch) {
                        if (mrs.get(i).getMeetClinicResultId().equals(meetClinicResultId)) {
                            if (targetTeams != null && targetTeams) {
                                DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
                                List<DoctorGroup> doctorGroups = doctorGroupDAO.findByMemberId(preDoctorId);
                                if (doctorGroups != null) {
                                    for (DoctorGroup dgp : doctorGroups) {
                                        if (dgp.getDoctorId().equals(targetDoctorId)) {
                                            mar.setTargetTeams(true);
                                        }
                                    }
                                }
                            }
                            needMatch = false;
                        }
                    }

                    // 获取医生忙闲状态
                    Integer exeDoctorId = mrs.get(i).getExeDoctor() == null ? null
                            : mrs.get(i).getExeDoctor();
                    Integer targetBusyFlag = null;
                    Integer exeStatus = mrs.get(i).getExeStatus();

                    // 获取医生对象
                    Doctor tarDoctor = doctorDAO.get(targetDoctorId);
                    Doctor exeDoctor;

                    if (exeDoctorId != null) {
                        targetBusyFlag = doctorDAO
                                .getBusyFlagByDoctorId(exeDoctorId);
                        exeDoctor = doctorDAO.get(exeDoctorId);
                        if (targetTeams != null && targetTeams) {
                            exeDoctor.setName(exeDoctor.getName() + "（" + tarDoctor.getName() + "）");
                        }
                        if (exeStatus == 2 || exeStatus == 8) {
                            // 获取会诊报告点评数
                            Long feedBackNum = pfDao.getClinicNumByDoctor(mrs.get(i).getExeDoctor(),
                                    "2", mrs.get(i).getMeetClinicId().toString());
                            mrs.get(i).setFeedBackNum(feedBackNum == null ? 0 : feedBackNum);
                            // 获取是否点评
                            List<PatientFeedback> feedBacks = pfDao.findPfsByServiceAndUser(exeDoctorId,
                                    "2", mrs.get(i).getMeetClinicId().toString(),
                                    reqDocUid, "doctor");
                            if (feedBacks != null && !feedBacks.isEmpty()) {
                                mrs.get(i).setFeedBack(true);
                            } else {
                                mrs.get(i).setFeedBack(false);
                            }
                        }
                    } else {
                        exeDoctor = tarDoctor;
                    }
                    mrs.get(i).setDoctor(exeDoctor);
                    mrs.get(i).setTargetBusyFlag(targetBusyFlag);
                }
                mar.setMeetClinicResults(mrs);
                setResult(mar);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 会诊意见列表服务
     *
     * @param meetClinicId
     * @return
     * @throws DAOException
     */
    @RpcService
    @DAOMethod(sql = "FROM MeetClinicResult WHERE meetClinicId=:meetClinicId AND exestatus=2")
    public abstract List<MeetClinicResult> findByMeetClinicId(
            @DAOParam("meetClinicId") Integer meetClinicId);

    /**
     * 会诊意见及理由列表服务（PC）
     *
     * @param meetClinicId
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "FROM MeetClinicResult WHERE meetClinicId=:meetClinicId AND exestatus>1")
    public abstract List<MeetClinicResult> findRBymeetClinicId(
            @DAOParam("meetClinicId") Integer meetClinicId);

    /**
     * 会诊申请服务（PC）
     *
     * @param meetClinicId
     * @return
     * @author xiebz
     */
    @RpcService
    @DAOMethod(sql = "FROM MeetClinicResult WHERE meetClinicId=:meetClinicId AND exestatus=:exestatus")
    public abstract List<MeetClinicResult> findBymeetClinicIdAndStatus(
            @DAOParam("meetClinicId") Integer meetClinicId,
            @DAOParam("exestatus") Integer exestatus);

    /**
     * 修改会诊费用
     *
     * @param meetClinicCost
     * @param meetClinicId
     */
    @RpcService
    @DAOMethod
    public abstract void updateMeetClinicCostByMeetClinicId(
            Double meetClinicCost, Integer meetClinicId);

    /**
     * 获取当天总会诊单数
     *
     * @param requestTime
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "SELECT COUNT(*) FROM MeetClinic WHERE DATE(requestTime)=DATE(:requestTime)")
    public abstract Long getNowMeetClinicNum(
            @DAOParam("requestTime") Date requestTime);

    /**
     * 获取当天人均会诊数
     *
     * @param requestTime
     * @return
     * @author LF
     */
    @RpcService
    public Double getAverageNum(Date requestTime) {
        Long meetNum = getNowMeetClinicNum(requestTime);
        Long allDoctorNum = DAOFactory.getDAO(DoctorDAO.class)
                .getAllDoctorNum();
        if (allDoctorNum <= 0) {
            return (double) 0;
        }
        return meetNum / (double) allDoctorNum;
    }

    /**
     * 根据申请医生和申请单状态获取申请单列表
     *
     * @param requestDoctor    申请医生id
     * @param meetClinicStatus 会诊申请单状态(0:待处理;1:会诊中;2已完成;9取消)
     * @return
     * @author ZX
     * @date 2015-9-7 下午5:23:20
     */
    @RpcService
    @DAOMethod
    public abstract List<MeetClinic> findByRequestDoctorAndMeetClinicStatus(
            int requestDoctor, int meetClinicStatus);

    /**
     * 我的会诊申请列表
     *
     * @param doctorId 当前登陆医生内码
     * @param flag     -0全部（未完成：待处理，会诊中；已结束：已完成，取消，拒绝），1未处理（待处理），2会诊中（会诊中），3已完成（已完成）
     * @param mark     -0未完成，1已结束
     * @param start    分页开始位置
     * @param limit    每页限制条数
     * @return List<HashMap<String, Object>>
     * @author luf
     */
    @RpcService
    public HashMap<String, List<HashMap<String, Object>>> findMeetClinicRequest(
            int doctorId, int flag, int mark, int start, int limit) {
        HashMap<String, List<HashMap<String, Object>>> result = new HashMap<>();
        List<MeetClinic> mcs = this.findListByRequestAndMark(doctorId, flag,
                mark, start, limit);
        if (flag == 0) {
            if (mark == 0) {
                result.put("unfinished", convertForMeetClinic(mcs));
                if (mcs == null) {
                    mcs = new ArrayList<>();
                }
                if (mcs.size() < limit) {
                    List<MeetClinic> finished = this.findListByRequestAndMark(
                            doctorId, 0, 1, 0, limit - mcs.size());
                    result.put("completed", convertForMeetClinic(finished));
                } else {
                    result.put("completed",
                            new ArrayList<HashMap<String, Object>>());
                }
            } else {
                result.put("completed", convertForMeetClinic(mcs));
                result.put("unfinished",
                        new ArrayList<HashMap<String, Object>>());
            }
        } else {
            result.put("unfinished", convertForMeetClinic(mcs));
            result.put("completed", new ArrayList<HashMap<String, Object>>());
        }
        return result;
    }

    /**
     * 供 我的会诊申请列表 调用
     *
     * @param doctorId 当前登陆医生内码
     * @param flag     -0全部（未完成：待处理，会诊中；已结束：已完成，取消，拒绝），1未处理（待处理），2会诊中（会诊中），3已完成（已完成）
     * @param mark     -0未完成，1已结束
     * @param start    分页开始位置
     * @param limit    每页限制条数
     * @return List<HashMap<String, Object>>
     * @author luf
     */
    public List<MeetClinic> findListByRequestAndMark(final int doctorId,
                                                     final int flag, final int mark, final int start, final int limit) {
        HibernateStatelessResultAction<List<MeetClinic>> action = new AbstractHibernateStatelessResultAction<List<MeetClinic>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) {
                String pending = "meetClinicStatus=0";
                String consultation = "meetClinicStatus=1";
                String completed = "meetClinicStatus=2";
                String refuse = "meetClinicStatus=8";
                String cancel = "meetClinicStatus=9";
                StringBuffer hql = new StringBuffer(
                        "From MeetClinic where requestDoctor=:doctorId and(");
                String condition = null;
                switch (flag) {
                    case 0:
                        if (mark == 0) {
                            condition = pending + " or " + consultation;
                        } else {
                            condition = completed + " or " + refuse + " or "
                                    + cancel;
                        }
                        break;
                    case 1:
                        condition = pending;
                        break;
                    case 2:
                        condition = consultation;
                        break;
                    case 3:
                        condition = completed;
                    default:
                        break;
                }
                hql.append(condition);
                hql.append(") order by requestTime desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供 我的会诊申请列表 调用
     *
     * @param mcs 会诊单列表
     * @return List<HashMap<String, Object>>
     * @author luf
     */
    public List<HashMap<String, Object>> convertForMeetClinic(
            List<MeetClinic> mcs) {
        List<HashMap<String, Object>> result = new ArrayList<>();
        EndMeetClinicDAO endDao = DAOFactory.getDAO(EndMeetClinicDAO.class);
        PatientDAO paDao = DAOFactory.getDAO(PatientDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        for (MeetClinic mc : mcs) {
            HashMap<String, Object> target = new HashMap<>();
            String mpiId = mc.getMpiid();
            List<MeetClinicResult> mrs = endDao.findByMeetClinicIdAndEffectiveStatus(mc
                    .getMeetClinicId(), MeetClinicConstant.EFFECTIVESTATUS_VALID);
            Patient patient = paDao.get(mpiId);
            RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId,
                    mc.getRequestDoctor());
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
            mc.setRequestString(DateConversion.convertRequestDateForBuss(mc
                    .getRequestTime()));
            target.put("meetClinic", mc);
            target.put("meetClinicResults", mrs);
            target.put("patient", patient);
            result.add(target);
        }
        return result;
    }

    /**
     * @param meetClinicId       会诊单Id
     * @param cause              拒绝原因
     * @param meetClinicResultId 执行单Id
     * @return boolean true 拒绝成功
     * @throws
     * @Class eh.bus.dao.MeetClinicDAO.java
     * @Title: refuseMeetClinic 医生拒绝会诊服务
     * @author Zhongzx
     * @Date 2015-12-25下午4:37:32
     * @author Luf
     * @Date 修改 2016-1-25 20:35
     */
    @RpcService
    public boolean refuseMeetClinic(final Integer meetClinicId,
                                    final String cause, final Integer meetClinicResultId) {
        if (meetClinicId == null || meetClinicResultId == null
                || StringUtils.isEmpty(cause)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "meetClinicId or meetClinicResultId or cause is required");
        }
        log.info("会诊拒绝服务(refuseMeetClinic):meetClinicId=" + meetClinicId
                + "; meetClinicResultId=" + meetClinicResultId + ";cause="
                + cause);

        final MeetClinicResultDAO mrDao = DAOFactory.getDAO(MeetClinicResultDAO.class);

        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 医生执行单为拒绝
                boolean doctorRefuse = refuseMeetClinicResult(meetClinicId,
                        meetClinicResultId, cause);
                if (doctorRefuse) {
                    // 拒绝会诊单（如果所有医生拒绝则为拒绝）
                    AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);
                    asynDoBussService.fireEvent(new BussCancelEvent(meetClinicResultId, BussTypeConstant.MEETCLINIC, null));

                    List<MeetClinicResult> list = mrDao
                            .findByMeetClinicId(meetClinicId);
                    int finished = 0;
                    int refused = 0;
                    int mcCount = 0;
                    for (MeetClinicResult mr : list) {
                        Integer effeStatus = mr.getEffectiveStatus();
                        if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                            continue;
                        }
                        Integer mrId = mr.getMeetClinicResultId();
                        if (mr.getExeStatus() == 2
                                && !mrId.equals(meetClinicResultId)) {
                            finished++;
                        }
                        if (mr.getExeStatus() == 8
                                && !mrId.equals(meetClinicResultId)) {
                            refused++;
                        }
                        mcCount++;
                    }

                    if (mcCount == (finished + refused + 1)) {
                        if (finished > 0) {
                            updateMeetClinicStatusByMeetClinicId(2,
                                    meetClinicId);
                        } else {
                            updateMeetClinicStatusByMeetClinicId(8,
                                    meetClinicId);
                        }
                        asynDoBussService.fireEvent(new BussCancelEvent(meetClinicId, BussTypeConstant.MEETCLINIC, meetClinicId));
                        setResult(true);
                    }
                } else {
                    setResult(false);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        Boolean refuseOrNot = action.getResult();
        if (refuseOrNot == null) {
            return true;
        } else if (!refuseOrNot) {
            return false;
        }

        MeetClinicPushService pushService = new MeetClinicPushService();

        MeetClinic mc = this.getByMeetClinicId(meetClinicId);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        MeetClinicResult mr = mrDao.get(meetClinicResultId);
        Doctor targetDoctor = doctorDAO.get(mr.getTargetDoctor());
        boolean teams = targetDoctor.getTeams() == null ? false : targetDoctor.getTeams();

        // 发送短信及系统消息
        Integer requestDoctor = mc.getRequestDoctor();
        pushService.refuseMeetClinicPush(meetClinicResultId, requestDoctor);

        // 添加会话结束时间
        if (!StringUtils.isEmpty(mc.getSessionID())) {
            Date endTime = new Date();
            this.updateSessionEndTimeByMeetClinicId(endTime, meetClinicId);
        }
        // 结束群聊===============================================================
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        groupDAO.closeMeetClinckGroup(meetClinicId);
        // 结束会诊单，发推送和短信，给申请医生价钱
        if (mc.getMeetClinicStatus() == 2) {
            DoctorAccountDAO daDao = DAOFactory.getDAO(DoctorAccountDAO.class);

            // 调取【增加账户收入】服务
            daDao.addDoctorIncome(requestDoctor, 3, meetClinicId, 0);

            pushService.endMeetClinicPush(meetClinicId, requestDoctor, teams);
        }
        return true;
    }

    /**
     * 申请医生会诊结束服务
     *
     * @param meetClinicId 会诊单序号
     * @param cancelCause  取消或强制结束或拒绝理由
     * @return Boolean
     * @author LF
     */
    @RpcService
    public Boolean endByRequest(final Integer meetClinicId,
                                final String cancelCause) {
        log.info("申请医生会诊结束服务(MeetClinicDAO--endByRequest):meetClinicId="
                + meetClinicId + "; cancelCause=" + cancelCause);
        if (meetClinicId == null) {
            new DAOException(DAOException.VALUE_NEEDED,
                    "meetClinicId is required!");
        }
        if (StringUtils.isEmpty(cancelCause)) {
            new DAOException(DAOException.VALUE_NEEDED,
                    "cancelCause is required!");
        }
        final MeetClinic mc = this.get(meetClinicId);
        if (mc.getMeetClinicStatus() == 8) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "会诊申请已被拒绝");
        }
        if (mc.getMeetClinicStatus() >= 2) {
            return false;
        }
        // 定义一个List用于存放需发送【取消】推送的医生信息
        final List<HashMap<String, Object>> cancelDoctors = new ArrayList<>();
        // 定义一个List用于存放需发送【自动完成】推送的医生信息
        final List<HashMap<String, Object>> endDoctors = new ArrayList<>();
        // 定义一个List用于存放需发送【取消理由】推送的医生信息
        final List<HashMap<String, Object>> cancelCauseDoctors = new ArrayList<>();
        final Date endTime = new Date();
        final String keyDoc = "doctor";
        final String keyTeams = "teams";
        final String mcrId = "meetClinicResultId";
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                MeetClinicResultDAO resultDAO = DAOFactory
                        .getDAO(MeetClinicResultDAO.class);
                // 查询出该会诊单所对应的所有执行单内容
                List<MeetClinicResult> meetClinicResults = resultDAO
                        .findByMeetClinicId(meetClinicId);
                DoctorAccountDAO doctorAccountDAO = DAOFactory
                        .getDAO(DoctorAccountDAO.class);
                EndMeetClinicDAO endMeetClinicDAO = DAOFactory
                        .getDAO(EndMeetClinicDAO.class);
                // 计算执行单中状态为结束的数量
                Integer endNum = 0;
                Integer refuseNum = 0;
                for (MeetClinicResult clinicResult : meetClinicResults) {
                    Integer effeStatus = clinicResult.getEffectiveStatus();
                    if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                        continue;
                    }
                    Integer exeStatus = clinicResult.getExeStatus();
                    Integer meetClinicResultId = clinicResult
                            .getMeetClinicResultId();
                    Integer exeDoctor = clinicResult.getExeDoctor();
                    if (exeStatus == 2) {
                        endNum++;
                        continue;
                    }
                    if (exeStatus > 2) {
                        if (exeStatus == 8) {
                            refuseNum++;
                        }
                        continue;
                    }

                    Integer doctorId = clinicResult.getTargetDoctor();
                    Doctor targetDoctor = doctorDAO.getByDoctorId(doctorId);
                    Boolean teams = targetDoctor.getTeams() == null ? false : targetDoctor.getTeams();


                    // 将填写了报告的执行单结束(自动完成)
                    if (!StringUtils.isEmpty(clinicResult.getMeetReport())) {
                        endMeetClinicDAO
                                .updateExeStatusAndEndTimeByMeetClinicResultId(
                                        2, endTime, meetClinicResultId);

                        HashMap<String, Object> map = new HashMap<>();
                        map.put(keyDoc, exeDoctor);
                        map.put(keyTeams, teams);
                        map.put(mcrId, meetClinicResultId);
                        endDoctors.add(map);

                        // 调取【增加账户收入】服务，给执行医生加钱
                        Date startDate = clinicResult.getStartTime();
                        Long timeDifference = endTime.getTime()
                                - startDate.getTime();
                        Integer addFlag = 0;
                        // 允许小额时间差，对外公布为30分钟,追加奖金
                        if ((timeDifference / 60000.0) <= 31) {
                            addFlag = 1;
                        }
                        doctorAccountDAO.addDoctorIncome(exeDoctor, 4,
                                meetClinicId, addFlag);
                        endNum++;
                        continue;
                    }
                    // 对未回复的执行单做强制取消动作
                    endMeetClinicDAO
                            .updateExeStatusAndEndTimeByMeetClinicResultId(9,
                                    endTime, meetClinicResultId);
                    // 有执行医生(会诊开始，但未回复)，则存放执行医生。
                    if (exeDoctor != null) {
                        doctorId = exeDoctor;

                        HashMap<String, Object> map = new HashMap<>();
                        map.put(keyDoc, doctorId);
                        map.put(keyTeams, teams);
                        map.put(mcrId, meetClinicResultId);
                        cancelCauseDoctors.add(map);
                        continue;
                    }

                    DoctorGroupDAO doctorGroupDAO = DAOFactory
                            .getDAO(DoctorGroupDAO.class);
                    List<DoctorGroup> dgs = doctorGroupDAO
                            .findByDoctorId(doctorId);
                    // 目标医生不是团队医生,直接存放目标医生
                    if (dgs == null || dgs.isEmpty()) {
                        HashMap<String, Object> map = new HashMap<>();
                        map.put(keyDoc, doctorId);
                        map.put(keyTeams, false);
                        map.put(mcrId, meetClinicResultId);

                        cancelDoctors.add(map);
                        continue;
                    } else {
                        // 目标医生是团队医生，且没有执行医生，存放团队中所有成员
                        for (int j = 0; j < dgs.size(); j++) {
                            HashMap<String, Object> map = new HashMap<>();
                            map.put(keyDoc, dgs.get(j).getMemberId());
                            map.put(keyTeams, true);
                            map.put(mcrId, meetClinicResultId);
                            cancelDoctors.add(map);
                        }
                    }
                }

                if (endNum > 0) {
                    // 结束会诊单，保存理由
                    updateMeetClinicStatusAndCancelCauseByMeetClinicId(2,
                            cancelCause, meetClinicId);
                    // 调取【增加账户收入】服务,给申请医生加钱
                    Integer requestDoctor = mc.getRequestDoctor();
                    doctorAccountDAO.addDoctorIncome(requestDoctor, 3,
                            meetClinicId, 0);
                } else {
                    mc.setCancelCause(cancelCause);
                    mc.setCancelDepart(mc.getRequestDepart());
                    mc.setCancelDoctor(mc.getRequestDoctor());
                    mc.setCancelOrgan(mc.getRequestOrgan());
                    mc.setCancelTime(endTime);
                    mc.setMeetClinicStatus(9);
                    if (refuseNum > 0) {
                        mc.setMeetClinicStatus(8);
                    }
                    update(mc);
                    // 添加会话结束时间
                    if (!StringUtils.isEmpty(mc.getSessionID())) {
                        updateSessionEndTimeByMeetClinicId(endTime,
                                meetClinicId);
                    }
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        Integer requestDoctor = mc.getRequestDoctor();
        MeetClinicPushService pushService = new MeetClinicPushService();
        pushService.endMeetClinicByRequestPush(meetClinicId, requestDoctor, cancelDoctors, endDoctors, cancelCauseDoctors, cancelCause);

        for (int i = 0; i < endDoctors.size(); i++) {
            HashMap<String, Object> map = endDoctors.get(i);
            Integer exeDoctorId = (Integer) map.get(keyDoc);

            // 给写了报告的会诊医生的推荐医生推荐奖励
            DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
            accDao.recommendReward(exeDoctorId);
        }
        // 结束群聊
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        groupDAO.closeMeetClinckGroup(meetClinicId);
        // 添加会话结束时间
        if (!StringUtils.isEmpty(mc.getSessionID())) {
            updateSessionEndTimeByMeetClinicId(endTime, meetClinicId);
        }

        AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);
        asynDoBussService.fireEvent(new BussFinishEvent(meetClinicId, BussTypeConstant.MEETCLINIC, meetClinicId));
        return true;
    }

    /**
     * 会诊单详情
     *
     * @param meetClinicId 会诊单号
     * @param doctorId     当前登录医生内码
     * @return HashMap<String, Object>
     * --meetClinic:会诊单信息,cdrOtherdocs:图片资料列表,patient：患者信息(全部),
     * meetClinicResultIds:执行单Id列表,news:最新答复信息,newsPhoto:最新答复头像,
     * inDoctors:参与医生内码(剔除拒绝),inNames:参与会诊医生姓名(剔除拒绝),
     * targetPhones:目标医生姓名及电话列表,status:详情单状态,statusText:状态名
     * @throws ControllerException
     * @author luf
     */
    @RpcService
    public HashMap<String, Object> getDetailByMeetClinicId(int meetClinicId,
                                                           int doctorId) throws ControllerException {
        HashMap<String, Object> map = new HashMap<>();
        List<Object> phones = new ArrayList<>();
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        MeetClinicResultDAO resultDAO = DAOFactory
                .getDAO(MeetClinicResultDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        CdrOtherdocDAO cdrDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
        DoctorGroupDAO dgDao = DAOFactory.getDAO(DoctorGroupDAO.class);
        DoctorDAO dDao = DAOFactory.getDAO(DoctorDAO.class);
        Dictionary dic = DictionaryController.instance().get(
                "eh.base.dictionary.Doctor");

        Integer status = 0;
        String statusText = "待处理";
        MeetClinic meetClinic = this.get(meetClinicId);
        Integer requestDoctor = meetClinic.getRequestDoctor();
        meetClinic.setMobile(dDao.getMobileByDoctorId(requestDoctor));
        meetClinic
                .setRequestBusyFlag(dDao.getBusyFlagByDoctorId(requestDoctor));
        String mpiId = meetClinic.getMpiid();
        List<Otherdoc> cdrOtherdocs = cdrDAO.findByClinicTypeAndClinicId(2,
                meetClinicId);
        if (requestDoctor == doctorId) {
            status = meetClinic.getMeetClinicStatus();
            statusText = DictionaryController.instance()
                    .get("eh.bus.dictionary.MeetClinicStatus").getText(status);
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
        List<Integer> inDoctors = new ArrayList<>();
        StringBuilder inNames = new StringBuilder();
        int count = 0;
        Date endTime = new Date(0);
        Date lastTime = new Date(0);
        Integer photo = 0;
        String name = null;
        Integer tmId = 0;
        Integer emId = 0;
        Integer smId = 0;
        for (MeetClinicResult mr : mrs) {
            Integer effeStatus = mr.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            HashMap<String, Object> phone = new HashMap<>();
            Boolean team = false;
            String phoneName = null;
            String phoneNum = null;
            Integer busyFlag = 0;
            Integer mrId = mr.getMeetClinicResultId();
            mrIds.add(mrId);

            Integer target = mr.getTargetDoctor();
            Integer exe = mr.getExeDoctor();
            Integer exeStatus = mr.getExeStatus();
            List<DoctorGroup> dgs = dgDao.findByDoctorId(target);
            if (dgs != null) {
                for (DoctorGroup dg : dgs) {
                    if (dg.getMemberId() == doctorId) {
                        team = true;
                        break;
                    }
                }
            }
            // 2016-3-9 luf 系统消息跳转时，给予前端随机执行单号
            if (target == doctorId) {
                smId = mrId;
            } else if (team) {
                if (exe != null && exe == doctorId) {
                    emId = mrId;
                } else {
                    tmId = mr.getMeetClinicResultId();
                }
            }

            if (exeStatus != 8) {
                if (exe != null && exe > 0) {
                    inNames.append(dic.getText(exe));
                    if (dgs != null && !dgs.isEmpty()) {
                        inNames.append("(");
                        inNames.append(dic.getText(target));
                        inNames.append(")");
                    }
                    inDoctors.add(exe);
                    phoneName = dic.getText(exe);
                    phoneNum = dDao.getMobileByDoctorId(exe);
                    busyFlag = dDao.getBusyFlagByDoctorId(exe);
                } else {
                    inNames.append(dic.getText(target));
                    inDoctors.add(target);
                    phoneName = dic.getText(target);
                    phoneNum = dDao.getMobileByDoctorId(target);
                    busyFlag = dDao.getBusyFlagByDoctorId(target);
                }
                inNames.append(",");
                phone.put("name", phoneName);
                phone.put("phone", phoneNum);
                phone.put("busyFlag", busyFlag);
                phones.add(phone);
            }

            if (exeStatus == 2 || exeStatus == 8) {
                Date endDate = mr.getEndTime();
                if (endDate.after(endTime)) {
                    endTime = endDate;
                    name = dic.getText(exe);
                    photo = dDao.getPhotoByDoctorId(exe);
                }
                count++;
            }

            if (exeStatus >= 2) {
                Date endDate = mr.getEndTime();
                if (endDate.after(lastTime)) {
                    lastTime = endDate;
                }
            }
        }
        String news = name + "医生答复了您，共" + count + "条答复";
        Date cancelTime = new Date();
        Date twoDaysDate = DateConversion.getDaysAgo(2);
        String timePoint = DateConversion.getDateFormatter(cancelTime, DateConversion.DEFAULT_TIME);
        Date twoDaysAgo = DateConversion.getDateByTimePoint(twoDaysDate,
                timePoint);
        if (lastTime.before(twoDaysAgo)) {
            meetClinic.setIsOverTime(true);
        } else {
            meetClinic.setIsOverTime(false);
        }
        map.put("meetClinic", meetClinic);
        map.put("cdrOtherdocs", cdrOtherdocs);
        map.put("patient", patient);
        map.put("meetClinicResultIds", mrIds);
        map.put("news", news);
        map.put("newsPhoto", photo);
        map.put("inDoctors", inDoctors);
        if (inNames.length() > 0) {
            map.put("inNames", inNames.substring(0, inNames.length() - 1));
        } else {
            map.put("inNames", inNames);
        }
        map.put("targetPhones", phones);
        map.put("count", count);
        // 2016-3-9 luf 系统消息跳转时，给予前端随机执行单号
        Integer resultId = 0;
        Boolean myTeam = false;
        Integer target = 0;
        if (smId > 0) {
            resultId = smId;
            status = resultDAO.getExeStatusByResultId(smId);
            target = resultDAO.getTargetByResultId(smId);
        } else if (emId > 0) {
            resultId = emId;
            status = resultDAO.getExeStatusByResultId(emId);
            target = resultDAO.getTargetByResultId(emId);
        } else if (tmId > 0) {
            resultId = tmId;
            status = resultDAO.getExeStatusByResultId(tmId);
            target = resultDAO.getTargetByResultId(tmId);
        }
        List<DoctorGroup> dgs = dgDao.findByDoctorId(target);
        for (DoctorGroup dg : dgs) {
            if (dg.getMemberId() == doctorId) {
                myTeam = true;
                break;
            }
        }
        if (requestDoctor != doctorId) {
            statusText = DictionaryController.instance()
                    .get("eh.bus.dictionary.ExeStatus").getText(status);
        }
        map.put("resultId", resultId);
        map.put("status", status);
        map.put("statusText", statusText);
        map.put("team", myTeam);
        return map;
    }

    /**
     * 能否进行群组聊天
     *
     * @param meetClinicId 会诊单号
     * @return Boolean
     * @author luf
     */
    @RpcService
    public Boolean groupEnable(int meetClinicId) {
        MeetClinic mc = this.get(meetClinicId);
        if (mc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "meetClinic is required!");
        }
        if (mc.getMeetClinicStatus() == null || mc.getMeetClinicStatus() < 2) {
            return true;
        } else if (mc.getMeetClinicStatus() >= 8) {
            return false;
        }
        MeetClinicResultDAO mrDao = DAOFactory
                .getDAO(MeetClinicResultDAO.class);
        List<MeetClinicResult> mrs = mrDao.findByMeetClinicId(meetClinicId);
        Date lastTime = new Date(0);
        for (MeetClinicResult mr : mrs) {
            Integer exeStatus = mr.getExeStatus();
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
            return false;
        } else {
            return true;
        }
    }


    /**
     * 会诊详情-优化版
     *
     * @param meetClinicId       会诊单号
     * @param meetClinicResultId 会诊执行单号
     * @param doctorId           当前登录医生内码
     * @return HashMap<String, Object>
     * --meetClinic:会诊单信息,cdrOtherdocs:图片资料列表,patient：患者信息(全部),
     * meetClinicResultIds:执行单Id列表,news:最新答复信息,newsPhoto:最新答复头像,
     * inDoctors:参与医生内码(剔除拒绝),inNames:参与会诊医生姓名(剔除拒绝),
     * targetPhones:目标医生姓名及电话列表,status:详情单状态,statusText:状态名
     * @throws ControllerException
     * @author luf
     * @date 2016-7-18
     * @date 2017-07-03 此接口已作废
     */
    @RpcService
    public HashMap<String, Object> getMeetClinicDetail(Integer meetClinicId, Integer meetClinicResultId,
                                                       int doctorId) throws ControllerException {
        if ((null == meetClinicId || 0 >= meetClinicId) && (null == meetClinicResultId || 0 >= meetClinicResultId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "meetClinicId and meetClinicResultId is required!");
        }
        MeetClinicResultDAO resultDAO = DAOFactory
                .getDAO(MeetClinicResultDAO.class);
        if (null == meetClinicResultId) {
            meetClinicResultId = 0;
        }
        boolean myTeam = false;
        Integer status = 0;
        String statusText;
        MeetClinic meetClinic = this.get(meetClinicId);
        if (0 < meetClinicResultId) {
            MeetClinicResult meetClinicResult = resultDAO.get(meetClinicResultId);
            if (null == meetClinicResult || null == meetClinicResult.getMeetClinicId()) {
                throw new DAOException(DAOException.VALUE_NEEDED, "meetClinicResult is required!");
            }
            meetClinicId = meetClinicResult.getMeetClinicId();
            if (null != meetClinicResult.getTargetDoctor() && doctorId != meetClinicResult.getTargetDoctor()) {
                myTeam = true;
            }
            status = meetClinicResult.getExeStatus();
            statusText = DictionaryController.instance()
                    .get("eh.bus.dictionary.ExeStatus").getText(status);
            meetClinic = this.get(meetClinicId);
        } else {
            status = meetClinic.getMeetClinicStatus();
            statusText = DictionaryController.instance()
                    .get("eh.bus.dictionary.MeetClinicStatus").getText(status);
        }

        HashMap<String, Object> map = new HashMap<>();
        List<Object> phones = new ArrayList<>();
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        CdrOtherdocDAO cdrDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
        DoctorGroupDAO dgDao = DAOFactory.getDAO(DoctorGroupDAO.class);
        DoctorDAO dDao = DAOFactory.getDAO(DoctorDAO.class);
        Dictionary dic = DictionaryController.instance().get(
                "eh.base.dictionary.Doctor");

        Integer requestDoctor = meetClinic.getRequestDoctor();
        meetClinic.setMobile(dDao.getMobileByDoctorId(requestDoctor));
        meetClinic
                .setRequestBusyFlag(dDao.getBusyFlagByDoctorId(requestDoctor));
        String mpiId = meetClinic.getMpiid();
        List<Otherdoc> cdrOtherdocs = cdrDAO.findByClinicTypeAndClinicId(2,
                meetClinicId);

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
        List<Integer> inDoctors = new ArrayList<>();
        StringBuilder inNames = new StringBuilder();
        int count = 0;
        Date endTime = new Date(0);
        Date lastTime = new Date(0);
        Integer photo = 0;
        String name = null;
        for (MeetClinicResult mr : mrs) {
            HashMap<String, Object> phone = new HashMap<>();
            String phoneName = null;
            String phoneNum = null;
            Integer busyFlag = 0;
            Integer mrId = mr.getMeetClinicResultId();
            mrIds.add(mrId);

            Integer target = mr.getTargetDoctor();
            Integer exe = mr.getExeDoctor();
            Integer exeStatus = mr.getExeStatus();
            List<DoctorGroup> dgs = dgDao.findByDoctorId(target);

            if (exeStatus != 8) {
                if (exe != null && exe > 0) {
                    inNames.append(dic.getText(exe));
                    if (dgs != null && !dgs.isEmpty()) {
                        inNames.append("(");
                        inNames.append(dic.getText(target));
                        inNames.append(")");
                    }
                    inDoctors.add(exe);
                    phoneName = dic.getText(exe);
                    phoneNum = dDao.getMobileByDoctorId(exe);
                    busyFlag = dDao.getBusyFlagByDoctorId(exe);
                } else {
                    inNames.append(dic.getText(target));
                    inDoctors.add(target);
                    phoneName = dic.getText(target);
                    phoneNum = dDao.getMobileByDoctorId(target);
                    busyFlag = dDao.getBusyFlagByDoctorId(target);
                }
                inNames.append(",");
                phone.put("name", phoneName);
                phone.put("phone", phoneNum);
                phone.put("busyFlag", busyFlag);
                phones.add(phone);
            }

            if (exeStatus == 2 || exeStatus == 8) {
                Date endDate = mr.getEndTime();
                if (endDate.after(endTime)) {
                    endTime = endDate;
                    name = dic.getText(exe);
                    photo = dDao.getPhotoByDoctorId(exe);
                }
                count++;
            }

            if (exeStatus >= 2) {
                Date endDate = mr.getEndTime();
                if (endDate.after(lastTime)) {
                    lastTime = endDate;
                }
            }
        }
        String news = name + "医生答复了您，共" + count + "条答复";
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
        map.put("meetClinic", meetClinic);
        map.put("cdrOtherdocs", cdrOtherdocs);
        map.put("patient", patient);
        map.put("meetClinicResultIds", mrIds);
        map.put("news", news);
        map.put("newsPhoto", photo);
        map.put("inDoctors", inDoctors);
        if (inNames.length() > 0) {
            map.put("inNames", inNames.substring(0, inNames.length() - 1));
        } else {
            map.put("inNames", inNames);
        }
        map.put("targetPhones", phones);
        map.put("count", count);
        map.put("resultId", meetClinicResultId);
        map.put("status", status);
        map.put("statusText", statusText);
        map.put("team", myTeam);
        return map;
    }

    /**
     * 查询医生所有有效会诊列表
     *
     * @param doctorId
     * @return
     */
    public List<MeetClinic> findAllMeetByDoctorId(final int doctorId) {
        HibernateStatelessResultAction<List<MeetClinic>> action = new AbstractHibernateStatelessResultAction<List<MeetClinic>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "select distinct c From MeetClinic c,MeetClinicResult r where r.effectiveStatus=:effectiveStatus and ((c.requestDoctor=:doctorId and " +
                        "(c.meetClinicStatus<2 or (c.meetClinicStatus=2 and c.sessionEndTime>=:twoDaysAgo))) or " +
                        "((r.targetDoctor=:doctorId or r.exeDoctor=:doctorId) and (r.exeStatus<2 or (r.exeStatus=2 and " +
                        "(c.meetClinicStatus<>2 or (c.meetClinicStatus=2 and c.sessionEndTime>=:twoDaysAgo)))))) " +
                        "and c.meetClinicId=r.meetClinicId order by c.requestTime desc";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                Date twoDaysAgo = DateConversion.getDateTimeDaysAgo(2);
                q.setParameter("twoDaysAgo", twoDaysAgo);
                q.setParameter("effectiveStatus", MeetClinicConstant.EFFECTIVESTATUS_VALID);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "from MeetClinic where meetClinicStatus<2 order by meetClinicId asc", limit = 0)
    public abstract List<MeetClinic> findUnFinishedMeetClinics();

    /**
     * 更新会诊安排时间
     *
     * @param meetClinicId
     * @param planTime
     */
    @DAOMethod(sql = "update MeetClinic set planTime=:planTime where meetClinicId=:meetClinicId")
    public abstract void updatePlanTimeByMeetClinicId(@DAOParam("meetClinicId") int meetClinicId,
                                                      @DAOParam("planTime") Date planTime);

}
