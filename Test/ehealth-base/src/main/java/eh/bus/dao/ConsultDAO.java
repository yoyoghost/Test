package eh.bus.dao;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.dictionary.service.DictionaryLocalService;
import ctd.dictionary.service.DictionarySliceRecordSet;
import ctd.mvc.alilife.entity.OauthMP;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.converter.support.StringToDate;
import eh.account.constant.ServerPriceConstant;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.*;
import eh.base.service.DoctorAccountConsultService;
import eh.base.service.DoctorInfoService;
import eh.base.service.OauthMPService;
import eh.base.user.UserSevice;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.push.MessagePushExecutorConstant;
import eh.bus.service.consult.ConsultMessageService;
import eh.bus.service.consult.PatientCancelConsultService;
import eh.bus.service.consult.RefuseConsultService;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.cdr.dao.RecipeDAO;
import eh.coupon.service.CouponService;
import eh.entity.base.Doctor;
import eh.entity.base.PatientFeedback;
import eh.entity.bus.*;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.msg.SmsInfo;
import eh.evaluation.dao.EvaluationDAO;
import eh.evaluation.service.EvaluationService;
import eh.mpi.dao.FollowScheduleDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.msg.dao.GroupDAO;
import eh.msg.dao.SessionDetailDAO;
import eh.msg.service.SessionDetailService;
import eh.op.auth.service.SecurityService;
import eh.push.SmsPushService;
import eh.remote.IWXServiceInterface;
import eh.task.executor.AliSmsSendExecutor;
import eh.task.executor.WxRefundExecutor;
import eh.util.DoctorUtil;
import eh.util.Easemob;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.collections.CollectionUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.persistence.Column;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.*;

import static eh.bus.constant.ConsultConstant.*;

public abstract class ConsultDAO extends HibernateSupportDelegateDAO<Consult> {
    private static final Logger logger = LoggerFactory.getLogger(ConsultDAO.class);
    private AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);

    public static final String MsgTitle = "纳里医生";

    public ConsultDAO() {
        super();
        setEntityName(Consult.class.getName());
        setKeyField("consultId");
    }

    @RpcService
    @DAOMethod
    public abstract List<Consult> findById(int id);

    @DAOMethod
    public abstract List<Consult> findBySessionID(String sessionID);



    @RpcService
    @DAOMethod
    public abstract List<Consult> findByRequestMpi(String requestMpi);

    @RpcService
    public List<Consult> findAllConsultDoctors(final String requestMpi) {
        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
            public void execute(StatelessSession ss) throws Exception {
                String sql = "SELECT DISTINCT consultDoctor, exeDoctor,groupMode from Consult WHERE requestMpi=:requestMpi";
                Query query = ss.createQuery(sql);
                query.setParameter("requestMpi", requestMpi);
                query.setFirstResult(0);
                query.setMaxResults(10000);
                List<Object[]> oList = query.list();
                List<Consult> consultList = new ArrayList<>();
                for (Object[] co : oList) {
                    Consult c = new Consult();
                    c.setConsultDoctor((Integer) co[0]);
                    c.setExeDoctor((Integer) co[1]);
                    c.setGroupMode((Integer) co[2]);
                    consultList.add(c);
                }
                setResult(consultList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "select count(*) from Consult where ConsultStatus=2 AND payFlag = 1 and requestMode in(1,2) and requestMpi=:requestMpi and endDate<=:endDate")
    public abstract Long getEndedOnlineOrAppointConsultByRequestMpi(@DAOParam("requestMpi") String requestMpi,@DAOParam("endDate") Date endDate);

    /**
     *  图文咨询  电话咨询 专家解读这三个业务的业务量,在线续方暂时不做统计
     * @param doctorId 医生的主键
     * @return
     */
    @DAOMethod(sql = "SELECT count(*) FROM Consult WHERE requestMode <> 4 AND consultDoctor =:doctorId OR exeDoctor =:doctorId")
    public abstract Long getConsultAmountByDoctorId(@DAOParam("doctorId") Integer doctorId);


    @DAOMethod(sql = "from Consult where ConsultStatus in (0, 1) AND payFlag = 1 order by consultId asc", limit = 0)
    public abstract List<Consult> findPendingConsultList();


    @RpcService
    @DAOMethod(sql = "from Consult where requestMpi=:requestMpi and requestMode=:requestMode  and Payflag>0 and ConsultStatus <>9")
    public abstract List<Consult> findEffectiveByRequestMpiAndRequestMode(@DAOParam("requestMpi") String requestMpi, @DAOParam("requestMode") Integer requestMode);

    @DAOMethod(sql = "from Consult  where ((payflag=1 and consultStatus<2) OR (payflag=0 AND consultStatus=4)) and requestMpi=:requestMpi and requestMode=:requestMode")
    public abstract List<Consult> findApplyingConsultByRequestMpi(
            @DAOParam("requestMpi") String requestMpi, @DAOParam("requestMode") Integer requestMode);

    @DAOMethod(sql = "from Consult  where ((payflag=1 and consultStatus<2) OR (payflag=0 AND consultStatus=4)) and mpiid=:mpiid and requestMpi=:requestMpi and requestMode=:requestMode and (consultDoctor=:doctorId or exeDoctor=:doctorId)")
    public abstract List<Consult> findApplyingConsultByPatientsAndDoctor(
            @DAOParam("mpiid") String mpiid, @DAOParam("requestMpi") String requestMpi, @DAOParam("doctorId") Integer doctorId, @DAOParam("requestMode") Integer requestMode);

    @DAOMethod(sql = "from Consult  where ((payflag=1 and consultStatus<2) OR (payflag=0 AND consultStatus=4)) and requestMpi=:requestMpi and requestMode=:requestMode and (consultDoctor=:doctorId or exeDoctor=:doctorId)")
    public abstract List<Consult> findApplyingConsultByPatientsAndDoctorAndRequestMode(
            @DAOParam("requestMpi") String requestMpi, @DAOParam("doctorId") Integer doctorId, @DAOParam("requestMode") Integer requestMode);

    @DAOMethod(sql = "from Consult  where ((payflag=1 and consultStatus<2) OR (payflag=0 AND consultStatus=4)) and requestMpiUrt=:requestMpiUrt and requestMode=:requestMode")
    public abstract List<Consult> findApplyingConsultByRequestMpiUrt(
            @DAOParam("requestMpiUrt") Integer requestMpiUrt, @DAOParam("requestMode") Integer requestMode);

    @DAOMethod(sql = "select DISTINCT requestMpi from Consult where consultStatus=1 AND requestMode=4 AND payFlag = 1 AND mpiid=:mpiid AND (consultDoctor=:doctorId or exeDoctor=:doctorId) order by consultId desc", limit = 0)
    public abstract List<String> findPendingConsultByMpiIdAndDoctor(@DAOParam("mpiid") String mpiid, @DAOParam("doctorId") Integer doctorId);

    /**
     * 供createConsultGroup()使用
     *
     * @return
     */
    @DAOMethod(sql = "select c from Consult c,eh.entity.msg.Group g  where c.requestMpi=:requestMpi and ( c.consultDoctor=:doctorId or c.exeDoctor=:doctorId )  and c.sessionID=g.groupId and g.nick like :nick and c.groupMode=1 and requestMode=:requestMode order by consultId desc")
    public abstract List<Consult> findByRequestMpiAndDoctorIdAndSession(
            @DAOParam("requestMpi") String requestMpi, @DAOParam("doctorId") Integer doctorId, @DAOParam("nick") String nick, @DAOParam("requestMode") Integer requestMode);

    /**
     * 供createConsultGroup()使用
     *
     * @return
     */
    @DAOMethod(sql = "select c from Consult c,eh.entity.msg.Group g  where c.requestMpi=:requestMpi and ( c.consultDoctor=:doctorId or c.exeDoctor=:doctorId )  and c.sessionID=g.groupId and g.nick like :nick and (c.groupMode=0 or c.groupMode is null or c.groupMode='') AND requestMode=:requestMode order by consultId desc")
    public abstract List<Consult> findByRequestMpiAndDoctorIdAndSessionGroup(
            @DAOParam("requestMpi") String requestMpi, @DAOParam("doctorId") Integer doctorId, @DAOParam("nick") String nick, @DAOParam("requestMode") Integer requestMode);

    /**
     * 根据申请者mpiId和doctorId获取最新的咨询单列表
     *
     * @param requestMpi
     * @param doctorId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "from Consult where requestMpi=:requestMpi and (exeDoctor=:doctorId OR ConsultDoctor=:doctorId) AND requestMode=2 ORDER BY consultId desc")
    public abstract List<Consult> findLatestGraphicConsultByMpiAndDoctorId(@DAOParam("requestMpi") String requestMpi, @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据咨询类型、患者mpi、医生id查询最新的咨询
     * @param requestMpi
     * @param doctorId
     * @param requestMode
     * @return
     */
    @RpcService
    @DAOMethod(sql = "from Consult where requestMpi=:requestMpi and (exeDoctor=:doctorId OR ConsultDoctor=:doctorId) AND requestMode=:requestMode and consultStatus <> 4 ORDER BY consultId desc")
    public abstract List<Consult> findLatestConsultByMpiAndDoctorIdAndRequestMode(
            @DAOParam("requestMpi") String requestMpi, @DAOParam("doctorId") Integer doctorId, @DAOParam("requestMode") Integer requestMode);

    @RpcService
    @DAOMethod
    public abstract Consult getById(int id);

    /**
     * 根据申请人mpi，咨询方式，判断是否能够进行咨询
     *
     * @param requestMpi  申请人mpi
     * @param requestMode 咨询方式(1电话咨询2图文咨询3视频咨询)
     * @return true能申请；false不能申请
     */
    @RpcService
    public Boolean canApplyConsult(String requestMpi, Integer requestMode) {
        Boolean canApplyConsult = true;

        if (null == requestMode) {
            throw new DAOException(DAOException.VALUE_NEEDED, "requestMode is needed");
        }

        if (2 == requestMode) {
            List<Consult> list = findApplyingConsultByRequestMpi(requestMpi, requestMode);
            if (list.size() > 0) {
                canApplyConsult = false;
            }
        }

        return canApplyConsult;
    }

    /**
     * 获取咨询单信息服务+病人信息--hyj
     *
     * @param id
     * @return
     */
    @RpcService
    public ConsultAndPatient getConsultAndPatientById(final int id) {
        HibernateStatelessResultAction<ConsultAndPatient> action = new AbstractHibernateStatelessResultAction<ConsultAndPatient>() {
            ConsultAndPatient result = new ConsultAndPatient();

            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where a.consultId=:consultId and b.mpiId=a.mpiid");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("consultId", id);
                result = (ConsultAndPatient) q.uniqueResult();
                DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                result.setTeams((DoctorDAO.getByDoctorId(result.getConsult()
                        .getConsultDoctor())).getTeams());
                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                result.setSignFlag(RelationDoctorDAO.getSignFlag(result
                        .getConsult().getMpiid(), result.getConsult()
                        .getConsultDoctor()));
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (ConsultAndPatient) action.getResult();
    }

    /**
     * 获取咨询单信息服务+病人信息+其他资料--lf
     *
     * @param id
     * @return
     */
    @RpcService
    public ConsultAndPatient getConsultAndPatientAndCdrOtherdocById(
            final Integer id) {
        HibernateStatelessResultAction<ConsultAndPatient> action = new AbstractHibernateStatelessResultAction<ConsultAndPatient>() {
            ConsultAndPatient result = new ConsultAndPatient();

            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where a.consultId=:consultId and b.mpiId=a.mpiid");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("consultId", id);
                result = (ConsultAndPatient) q.uniqueResult();
                if (result != null) {
                    // 计算咨询单的倒计时
                    Consult c = result.getConsult();
                    SubConsult subConsult = BeanUtils.map(c, SubConsult.class);
                    subConsult.setStatusText((subConsult.getStatus() == 5 || subConsult.getStatus() == 6) ? "已完成" : subConsult.getStatusText());
                    result.setConsult(subConsult);
                    int mode = c.getRequestMode();
                    Date nowDate = new Date();
                    Date cDate = new Date();
                    int time = 0;
                    int timesum = SystemConstant.APPOINT_CONSULT_TIME;
                    if (mode == CONSULT_TYPE_POHONE) { // 1是电话咨询
                        if (c.getAppointEndTime() != null) {
                            cDate = c.getAppointEndTime();
                        } else {
                            cDate = c.getAppointTime();
                        }
                        timesum = SystemConstant.APPOINT_CONSULT_TIME;
                    }
                    if (mode == CONSULT_TYPE_GRAPHIC || mode == CONSULT_TYPE_PROFESSOR|| mode == CONSULT_TYPE_RECIPE) { // 2是图文咨询
                        cDate = c.getRequestTime();
                        timesum = SystemConstant.ONLINE_CONSULT_TIME;
                    }

                    long times = ((nowDate.getTime() - cDate.getTime()) / (60 * 1000));
                    time = (int) times / 60;
                    if (times % 60 < 0) {
                        time = time - 1;
                    }

                    result.setCountDown(timesum - time);

                    DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    result.setTeams((doctorDAO.getByDoctorId(c
                            .getConsultDoctor())).getTeams());
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    String mpiId = c.getMpiid();
                    Integer doctor = c.getConsultDoctor();
                    result.setSignFlag(RelationDoctorDAO.getSignFlag(mpiId,
                            doctor));
                    PatientFeedbackDAO pfDao = DAOFactory
                            .getDAO(PatientFeedbackDAO.class);
                    List<PatientFeedback> pfs = pfDao.findPfsByDoctorService(doctor, "3",
                            id.toString(), "patient");
                    if (pfs == null || pfs.isEmpty()) {
                        result.setFeedback(false);
                    } else {
                        result.setFeedback(true);
                    }

                    // 获取其他资料列表
                    Integer clinicId = c.getConsultId();

                    // 先注释掉，回头整理纳里健康的时候，进行测试，再进行修改
                    List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(
                            CdrOtherdocDAO.class).findByClinicTypeAndClinicId(
                            3, clinicId);

                    result.setCdrOtherdocs(cdrOtherdocs);

                    if (c.getGroupMode() != null && c.getGroupMode().equals(1)) {
                        Integer exeId = c.getExeDoctor();
                        if (exeId != null && exeId > 0) {
                            result.setExeDoctor(doctorDAO.getByDoctorId(exeId));
                            result.setGroupName(doctorDAO.getNameById(doctor));
                        }
                    }
                }
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (ConsultAndPatient) action.getResult();
    }

    /**
     * @param consultId
     * @param doctorId
     * @return ConsultAndPatient
     * @throws
     * @Class eh.bus.dao.ConsultDAO.java
     * @Title: getConsultAndPatientAndCdrOtherdocByIdAndDoctorId
     * @Description:  在getConsultAndPatientAndCdrOtherdocById基础上加了一个
     * doctorId 入参
     * @author Zhongzx
     * @Date 2016-3-1下午5:37:28
     */
    @RpcService
    public ConsultAndPatient getConsultAndPatientAndCdrOtherdocByIdAndDoctorId(
            final Integer consultId, final Integer doctorId) {
        HibernateStatelessResultAction<ConsultAndPatient> action = new AbstractHibernateStatelessResultAction<ConsultAndPatient>() {
            ConsultAndPatient result = new ConsultAndPatient();

            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where a.consultId=:consultId and b.mpiId=a.mpiid");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("consultId", consultId);
                result = (ConsultAndPatient) q.uniqueResult();
                if (result != null) {
                    // 计算咨询单的倒计时
                    Consult c = result.getConsult();
                    int mode = c.getRequestMode();
                    Date nowDate = new Date();
                    Date cDate = new Date();
                    int time = 0;
                    int timesum = SystemConstant.APPOINT_CONSULT_TIME;
                    if (mode == CONSULT_TYPE_POHONE) { // 1是电话咨询
                        if (c.getAppointEndTime() != null) {
                            cDate = c.getAppointEndTime();
                        } else {
                            cDate = c.getAppointTime();
                        }
                        timesum = SystemConstant.APPOINT_CONSULT_TIME;
                    }
                    if (mode == CONSULT_TYPE_GRAPHIC || mode == CONSULT_TYPE_PROFESSOR|| mode == CONSULT_TYPE_RECIPE) { // 2是图文咨询
                        cDate = c.getRequestTime();
                        timesum = SystemConstant.ONLINE_CONSULT_TIME;
                    }

                    long times = ((nowDate.getTime() - cDate.getTime()) / (60 * 1000));
                    time = (int) times / 60;
                    if (times % 60 < 0) {
                        time = time - 1;
                    }

                    result.setCountDown(timesum - time);

                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    RelationPatientDAO reDao = DAOFactory
                            .getDAO(RelationPatientDAO.class);
                    RelationLabelDAO labelDAO = DAOFactory
                            .getDAO(RelationLabelDAO.class);
                    RelationDoctorDAO rdao = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    result.setTeams((DoctorDAO.getByDoctorId(c
                            .getConsultDoctor())).getTeams());
                    RelationDoctor rd = reDao.getByMpiidAndDoctorId(
                            c.getMpiid(), doctorId);
                    result.setSignFlag(rdao.getSignFlag(c.getMpiid(), doctorId));
                    if (rd != null) {
                        result.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                                .getRelationDoctorId()));
                    }
                    Integer doctor = c.getConsultDoctor();
                    PatientFeedbackDAO pfDao = DAOFactory
                            .getDAO(PatientFeedbackDAO.class);
                    List<PatientFeedback> pfs = pfDao.findPfsByDoctorService(doctor, "3",
                            consultId.toString(), "patient");
                    if (pfs == null || pfs.isEmpty()) {
                        result.setFeedback(false);
                    } else {
                        result.setFeedback(true);
                    }

                    // 获取其他资料列表
                    Integer clinicId = c.getConsultId();

                    // 先注释掉，回头整理纳里健康的时候，进行测试，再进行修改
                    List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(
                            CdrOtherdocDAO.class).findByClinicTypeAndClinicId(
                            3, clinicId);

                    result.setCdrOtherdocs(cdrOtherdocs);
                }
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (ConsultAndPatient) action.getResult();
    }

    @RpcService
    @DAOMethod
    public abstract void deleteById(int id);

    /**
     * 获取待处理咨询申请单数服务--hyj
     *
     * @param doctorID  --医生代码
     * @param groupFlag --团队标志
     * @return
     * @throws DAOException
     */
    @RpcService
    public long getUnConsultNum(final int doctorID, final boolean groupFlag, final Integer flag)
            throws DAOException {

        HibernateStatelessResultAction<Object> action = new AbstractHibernateStatelessResultAction<Object>() {
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select count(*) from Consult where consultStatus<2 and (consultDoctor=:DoctorID or exeDoctor=:DoctorID) and payflag=1");
                if(flag == 2){
                    hql.append(" and requestMode<>4 ");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("DoctorID", doctorID);
                long totalCount = (long) q.uniqueResult();
                setResult(totalCount);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        long result1 = ((Long) action.getResult()).longValue();
        long result2 = 0;
        if (groupFlag) {
            HibernateStatelessResultAction<Object> action2 = new AbstractHibernateStatelessResultAction<Object>() {
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql = new StringBuilder(
                            "select count(a) from Consult a,DoctorGroup b where a.consultStatus<1 and a.consultDoctor=b.doctorId and b.memberId=:DoctorID and a.payflag=1");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("DoctorID", doctorID);
                    long totalCount = (long) q.uniqueResult();
                    setResult(totalCount);
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action2);
            result2 = ((Long) action2.getResult()).longValue();
        }
        return result1 + result2;
    }

    /**
     * 查询待处理咨询单列表服务
     *
     * @param doctorID  --医生代码
     * @param groupFlag --团队标志
     * @return
     * @throws DAOException
     * @author hyj
     */

    @RpcService
    public List<ConsultAndPatient> queryConsult(final int doctorID,
                                                final boolean groupFlag) throws DAOException {
        HibernateStatelessResultAction<List<ConsultAndPatient>> action = new AbstractHibernateStatelessResultAction<List<ConsultAndPatient>>() {
            List<ConsultAndPatient> list = new ArrayList<ConsultAndPatient>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (groupFlag) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where ((consultStatus<2 and (consultDoctor=:DoctorID or exeDoctor=:DoctorID)) or (consultStatus<1 and consultDoctor in (select doctorId from DoctorGroup where memberId=:DoctorID))) and b.mpiId=a.mpiid and a.payflag=1 order by appointTime asc");
                } else {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where consultStatus<2 and (consultDoctor=:DoctorID or exeDoctor=:DoctorID) and b.mpiId=a.mpiid and a.payflag=1 order by appointTime asc");
                }

                Query q = ss.createQuery(hql.toString());
                q.setParameter("DoctorID", doctorID);
                list = (List<ConsultAndPatient>) q.list();
                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                for (int i = 0; i < list.size(); i++) {
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                            .get(i).getConsult().getMpiid(), doctorID);
                    list.get(i).setSignFlag(signFlag);
                    Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                            .getConsult().getConsultDoctor())).getTeams();
                    list.get(i).setTeams(teams);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<ConsultAndPatient>) action.getResult();
    }

    /**
     * 查询待处理咨询单列表服务--分页
     *
     * @param doctorID  --医生代码
     * @param groupFlag --团队标志
     * @param start
     * @return
     * @throws DAOException
     * @author hyj
     */

    @RpcService
    public List<ConsultAndPatient> queryConsultWithPage(final int doctorID,
                                                        final boolean groupFlag, final int start) throws DAOException {
        return queryConsultWithPageLimit(doctorID, groupFlag, start, 10);
    }

    /**
     * 查询待处理咨询单列表服务--分页
     *
     * @param doctorID  --医生代码
     * @param groupFlag --团队标志
     * @param start     记录起始位置
     * @param limit     查询记录数
     * @return
     * @throws DAOException
     * @author yaozh
     */

    @RpcService
    public List<ConsultAndPatient> queryConsultWithPageLimit(
            final int doctorID, final boolean groupFlag, final int start,
            final int limit) throws DAOException {
        HibernateStatelessResultAction<List<ConsultAndPatient>> action = new AbstractHibernateStatelessResultAction<List<ConsultAndPatient>>() {
            List<ConsultAndPatient> list = new ArrayList<ConsultAndPatient>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (groupFlag) {
                    //2016-12-16 luf：非抢单模式团队
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where ((consultStatus<2 and (consultDoctor=:DoctorID or exeDoctor=:DoctorID)) or (consultStatus<1 and consultDoctor in (select d.doctorId from Doctor d,DoctorGroup g where g.memberId=:DoctorID AND d.doctorId=g.doctorId) and (a.groupMode is null or a.groupMode='' or a.groupMode=0)) or (consultStatus<2 and consultDoctor in (select d.doctorId from Doctor d,DoctorGroup g where g.memberId=:DoctorID AND d.doctorId = g.doctorId) and a.groupMode=1)) and b.mpiId=a.mpiid and a.payflag=1 and requestMode in (1,2,5) order by requestTime desc");
                } else {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where consultStatus<2 and (consultDoctor=:DoctorID or exeDoctor=:DoctorID) and b.mpiId=a.mpiid and a.payflag=1 and requestMode in (1,2,5) order by requestTime desc");
                }

                Query q = ss.createQuery(hql.toString());
                q.setParameter("DoctorID", doctorID);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                list = (List<ConsultAndPatient>) q.list();
                DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                RelationPatientDAO reDao = DAOFactory
                        .getDAO(RelationPatientDAO.class);
                RelationLabelDAO labelDAO = DAOFactory
                        .getDAO(RelationLabelDAO.class);
                for (int i = 0; i < list.size(); i++) {
                    Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                            .getConsult().getConsultDoctor())).getTeams();
                    list.get(i).setTeams(teams);
                    RelationDoctor rd = reDao.getByMpiidAndDoctorId(list.get(i)
                            .getConsult().getMpiid(), doctorID);
                    if (rd != null) {
                        if (rd.getRelationType() == 0) {
                            list.get(i).setSignFlag(true);
                        } else {
                            list.get(i).setSignFlag(false);
                        }
                        list.get(i).setLabelNames(
                                labelDAO.findLabelNamesByRPId(rd
                                        .getRelationDoctorId()));
                    }
                    Date requestT = list.get(i).getConsult().getRequestTime();
                    list.get(i).getConsult().setRequestDate(DateConversion.convertRequestDateForBuss(requestT));
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<ConsultAndPatient> resultList = action.getResult();
        if(ValidateUtil.notBlankList(resultList)){
            for(ConsultAndPatient cp : resultList){
                if(cp.getConsult()!=null) {
                    SubConsult subConsult = BeanUtils.map(cp.getConsult(), SubConsult.class);
                    subConsult.setStatusText((subConsult.getStatus() == 5 || subConsult.getStatus() == 6) ? "已完成" : subConsult.getStatusText());
                    cp.setConsult(subConsult);
                }
            }
        }
        logger.info("queryConsultWithPageLimit resultList[{}]", JSONObject.toJSONString(resultList));
        return resultList;
    }

    /**
     * 咨询开始服务--hyj
     *
     * @param consultID --咨询单序号
     * @param exeDoctor --执行医生
     * @param exeDepart --执行科室
     * @param exeOrgan  --执行机构
     * @return
     */
    @RpcService
    public boolean startConsult(final int consultID, final int exeDoctor,
                                final int exeDepart, final int exeOrgan) {
        logger.info("咨询开始,consultID:" + consultID + ",exeDoctor:" + exeDoctor
                + ",exeDepart:" + exeDepart + ",exeOrgan:" + exeOrgan);
        Consult consult = this.getById(consultID);
        Boolean b = false;
        if (consult.getConsultStatus() == 0) {
            try {
                HibernateSessionTemplate.instance().execute(
                        new HibernateStatelessAction() {
                            public void execute(StatelessSession ss)
                                    throws Exception {
                                String hql = "update Consult set startDate=:StartDate,exeDoctor=:ExeDoctor,exeDepart=:ExeDepart,exeOrgan=:ExeOrgan,consultStatus=1 where consultId=:ConsultID";

                                Query q = ss.createQuery(hql);
                                q.setInteger("ConsultID", consultID);
                                q.setTimestamp("StartDate", new Date());
                                q.setInteger("ExeDoctor", exeDoctor);
                                q.setInteger("ExeDepart", exeDepart);
                                q.setInteger("ExeOrgan", exeOrgan);

                                q.executeUpdate();
                            }
                        });
            } catch (DAOException e) {
                logger.error(e.getMessage());
            }
            b = true;
        } else if (consult.getConsultStatus() == 1) {
            if (consult.getExeDoctor().equals(exeDoctor)) {
                b = true;
            } else {
                b = false;
            }
        } else {
            b = false;
        }
        if (!b) {
            return b;
        }
        GroupDAO gDao = DAOFactory.getDAO(GroupDAO.class);
        Integer bussType = 3;
        gDao.addUserToGroup(bussType, consultID, exeDoctor);
        return b;
    }

    /**
     * 上个月历史咨询单列表查询服务测试
     *
     * @param exeDoctor
     * @param mpiId
     * @return
     * @author hyj
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHisLastMonth(int exeDoctor,
                                                            String mpiId) {
        Date endDate = Context.instance().get("date.getToday", Date.class);
        Date startDate = Context.instance().get("date.getDateOfLastMonth",
                Date.class);
        return queryConsultHis(startDate, endDate, exeDoctor, mpiId);
    }

    /**
     * 上个月历史咨询单列表查询服务测试--分页
     *
     * @param exeDoctor
     * @param mpiId
     * @return
     * @author hyj
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHisLastMonthWithPage(
            int exeDoctor, String mpiId, int start) {
        Date endDate = Context.instance().get("date.getToday", Date.class);
        Date startDate = Context.instance().get("date.getDateOfLastMonth",
                Date.class);
        return queryConsultHisWithPage(startDate, endDate, exeDoctor, mpiId,
                start);
    }

    /**
     * 上个月历史咨询单列表查询服务测试--分页
     *
     * @param exeDoctor
     * @param mpiId
     * @param start     记录起始位置
     * @param limit     查询记录数
     * @return
     * @author yaozh
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHisLastMonthWithPageLimit(
            int exeDoctor, String mpiId, int start, int limit) {
        Date endDate = Context.instance().get("date.getDatetime", Date.class);
        Date startDate = Context.instance().get("date.getDateOfLastMonth",
                Date.class);
        return queryConsultHisWithPageLimit(startDate, endDate, exeDoctor,
                mpiId, start, limit);
    }

    /**
     * 查询历史咨询单列表服务
     *
     * @param startDate --开始时间
     * @param endDate   --结束时间
     * @param exeDoctor --执行医生
     * @param mpiId     --病人主索引
     * @return
     * @author hyj
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHis(final Date startDate,
                                                   final Date endDate, final Integer exeDoctor, final String mpiId) {
        if (startDate == null || startDate.toString().equals("")) {
//            logger.error("startDate[" + startDate + "] can not null");
            throw new DAOException(600, "startDate[" + startDate
                    + "] can not null");
        }

        if (endDate == null || endDate.toString().equals("")) {
//            logger.error("endDate[" + endDate + "] can not null");
            throw new DAOException(600, "endDate[" + endDate + "] can not null");
        }

        HibernateStatelessResultAction<List<ConsultAndPatient>> action = new AbstractHibernateStatelessResultAction<List<ConsultAndPatient>>() {
            List<ConsultAndPatient> list = new ArrayList<ConsultAndPatient>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (exeDoctor == null) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and a.mpiid=:MPIID and consultStatus>1 and b.mpiId=a.mpiid and a.payflag=1 order by a.requestTime desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("MPIID", mpiId);
                    list = q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                } else if (mpiId == null || mpiId.equals("")) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and (exeDoctor=:ExeDoctor or consultDoctor=:ExeDoctor) and consultStatus>1 and b.mpiId=a.mpiid and  a.payflag=1 order by a.requestTime desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("ExeDoctor", exeDoctor);
                    list = (List<ConsultAndPatient>) q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                } else {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and (exeDoctor=:ExeDoctor or consultDoctor=:ExeDoctor) and a.mpiid=:MPIID and consultStatus>1 and b.mpiId=a.mpiid and a.payflag=1 order by a.requestTime desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("ExeDoctor", exeDoctor);
                    q.setParameter("MPIID", mpiId);
                    list = (List<ConsultAndPatient>) q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<ConsultAndPatient>) action.getResult();

    }

    /**
     * 查询历史咨询单列表服务--分页
     *
     * @param startDate --开始时间
     * @param endDate   --结束时间
     * @param exeDoctor --执行医生
     * @param mpiId     --病人主索引
     * @return
     * @author hyj
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHisWithPage(
            final Date startDate, final Date endDate, final Integer exeDoctor,
            final String mpiId, final int start) {
        return queryConsultHisWithPageLimit(startDate, endDate, exeDoctor,
                mpiId, start, 10);
    }

    /**
     * 查询历史咨询单列表服务--分页
     *
     * @param startDate --开始时间
     * @param endDate   --结束时间
     * @param exeDoctor --执行医生
     * @param mpiId     --病人主索引
     * @param start     记录起始位置
     * @param limit     查询记录数
     * @return
     * @author hyj
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHisWithPageLimit(
            final Date startDate, final Date endDate, final Integer exeDoctor,
            final String mpiId, final int start, final int limit) {
        if (startDate == null || startDate.toString().equals("")) {
//            logger.error("startDate[" + startDate + "] can not null");
            throw new DAOException(600, "startDate[" + startDate
                    + "] can not null");
        }

        if (endDate == null || endDate.toString().equals("")) {
//            logger.error("endDate[" + endDate + "] can not null");
            throw new DAOException(600, "endDate[" + endDate + "] can not null");
        }

        HibernateStatelessResultAction<List<ConsultAndPatient>> action = new AbstractHibernateStatelessResultAction<List<ConsultAndPatient>>() {
            List<ConsultAndPatient> list = new ArrayList<ConsultAndPatient>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (exeDoctor == null) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<=:EndDate and a.mpiid=:MPIID and consultStatus>1 and b.mpiId=a.mpiid and a.payflag=1 order by a.requestTime desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("MPIID", mpiId);
                    q.setFirstResult(start);
                    q.setMaxResults(limit);
                    list = q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                } else if (mpiId == null || mpiId.equals("")) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and (exeDoctor=:ExeDoctor or consultDoctor=:ExeDoctor) and consultStatus>1 and b.mpiId=a.mpiid and a.payflag=1 order by a.requestTime desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("ExeDoctor", exeDoctor);
                    q.setFirstResult(start);
                    q.setMaxResults(10);
                    list = (List<ConsultAndPatient>) q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                } else {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and (exeDoctor=:ExeDoctor or consultDoctor=:ExeDoctor) and a.mpiid=:MPIID and consultStatus>1 and b.mpiId=a.mpiid and a.payflag=1 order by a.requestTime desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("ExeDoctor", exeDoctor);
                    q.setParameter("MPIID", mpiId);
                    q.setFirstResult(start);
                    q.setMaxResults(10);
                    list = (List<ConsultAndPatient>) q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<ConsultAndPatient>) action.getResult();

    }

    /**
     * 根据病人姓名查询历史咨询单列表服务
     *
     * @param startDate   --开始时间
     * @param endDate     --结束时间
     * @param exeDoctor   --执行医生
     * @param mpiId       --病人主索引
     * @param patientName --病人姓名
     * @return
     * @author hyj
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHisByPatientName(
            final Date startDate, final Date endDate, final Integer exeDoctor,
            final String mpiId, final String patientName) {
        if (startDate == null || startDate.toString().equals("")) {
//            logger.error("startDate[" + startDate + "] can not null");
            throw new DAOException(600, "startDate[" + startDate
                    + "] can not null");
        }

        if (endDate == null || endDate.toString().equals("")) {
//            logger.error("endDate[" + endDate + "] can not null");
            throw new DAOException(600, "endDate[" + endDate + "] can not null");
        }

        HibernateStatelessResultAction<List<ConsultAndPatient>> action = new AbstractHibernateStatelessResultAction<List<ConsultAndPatient>>() {
            List<ConsultAndPatient> list = new ArrayList<ConsultAndPatient>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (exeDoctor == null) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and a.mpiid=:MPIID and consultStatus>1 and b.mpiId=a.mpiid and b.patientName=:patientName and a.payflag=1 order by a.endDate desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("MPIID", mpiId);
                    q.setParameter("patientName", patientName);
                    list = q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                } else if (mpiId == null || mpiId.equals("")) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and (exeDoctor=:ExeDoctor or consultDoctor=:ExeDoctor) and consultStatus>1 and b.mpiId=a.mpiid and b.patientName=:patientName and a.payflag=1 order by a.endDate desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("ExeDoctor", exeDoctor);
                    q.setParameter("patientName", patientName);
                    list = (List<ConsultAndPatient>) q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                } else {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and (exeDoctor=:ExeDoctor or consultDoctor=:ExeDoctor) and a.mpiid=:MPIID and consultStatus>1 and b.mpiId=a.mpiid and b.patientName=:patientName and a.payflag=1 order by a.endDate desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("ExeDoctor", exeDoctor);
                    q.setParameter("MPIID", mpiId);
                    q.setParameter("patientName", patientName);
                    list = (List<ConsultAndPatient>) q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<ConsultAndPatient>) action.getResult();

    }

    /**
     * 根据病人姓名查询历史咨询单列表服务--分页
     *
     * @param startDate   --开始时间
     * @param endDate     --结束时间
     * @param exeDoctor   --执行医生
     * @param mpiId       --病人主索引
     * @param patientName --病人姓名
     * @return
     * @author hyj
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHisByPatientNameWithPage(
            final Date startDate, final Date endDate, final Integer exeDoctor,
            final String mpiId, final String patientName, final int start) {
        if (startDate == null || startDate.toString().equals("")) {
//            logger.error("startDate[" + startDate + "] can not null");
            throw new DAOException(600, "startDate[" + startDate
                    + "] can not null");
        }

        if (endDate == null || endDate.toString().equals("")) {
//            logger.error("endDate[" + endDate + "] can not null");
            throw new DAOException(600, "endDate[" + endDate + "] can not null");
        }

        HibernateStatelessResultAction<List<ConsultAndPatient>> action = new AbstractHibernateStatelessResultAction<List<ConsultAndPatient>>() {
            List<ConsultAndPatient> list = new ArrayList<ConsultAndPatient>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (exeDoctor == null) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and a.mpiid=:MPIID and consultStatus>1 and b.mpiId=a.mpiid and b.patientName=:patientName and a.payflag=1 order by a.endDate desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("MPIID", mpiId);
                    q.setParameter("patientName", patientName);
                    q.setFirstResult(start);
                    q.setMaxResults(10);
                    list = q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                } else if (mpiId == null || mpiId.equals("")) {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and (exeDoctor=:ExeDoctor or consultDoctor=:ExeDoctor) and consultStatus>1 and b.mpiId=a.mpiid and b.patientName=:patientName and a.payflag=1 order by a.endDate desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("ExeDoctor", exeDoctor);
                    q.setParameter("patientName", patientName);
                    q.setFirstResult(start);
                    q.setMaxResults(10);
                    list = (List<ConsultAndPatient>) q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                } else {
                    hql = new StringBuilder(
                            "select new eh.entity.bus.ConsultAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientName,b.patientType) from Consult a,Patient b where requestTime>=:StartDate and requestTime<:EndDate and (exeDoctor=:ExeDoctor or consultDoctor=:ExeDoctor) and a.mpiid=:MPIID and consultStatus>1 and b.mpiId=a.mpiid and b.patientName=:patientName and a.payflag=1 order by a.endDate desc");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("StartDate", startDate);
                    q.setParameter("EndDate", endDate);
                    q.setParameter("ExeDoctor", exeDoctor);
                    q.setParameter("MPIID", mpiId);
                    q.setParameter("patientName", patientName);
                    q.setFirstResult(start);
                    q.setMaxResults(10);
                    list = (List<ConsultAndPatient>) q.list();
                    RelationDoctorDAO RelationDoctorDAO = DAOFactory
                            .getDAO(RelationDoctorDAO.class);
                    DoctorDAO DoctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    for (int i = 0; i < list.size(); i++) {
                        Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                                .get(i).getConsult().getMpiid(), exeDoctor);
                        list.get(i).setSignFlag(signFlag);
                        Boolean teams = (DoctorDAO.getByDoctorId(list.get(i)
                                .getConsult().getConsultDoctor())).getTeams();
                        list.get(i).setTeams(teams);
                    }
                    setResult(list);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<ConsultAndPatient>) action.getResult();

    }

    /**
     * 根据sessionId结束最新的咨询单
     *
     * @param sessionId
     */
    @RpcService
    @Deprecated
    public void endConsultBySessionId(String sessionId) {
        if (StringUtils.isEmpty(sessionId)) {
            throw new DAOException(609, "sessionId is null");
        }
        List<Consult> consults = this.findBySessionID(sessionId);
        if (null != consults && !consults.isEmpty() && consults.size() > 0) {

            Integer newestConsultId = this.getNewestConsultId(consults.get(0));

            logger.info("endConsultBySessionId consultId:" + newestConsultId);
            if (null != newestConsultId) {
                endConsult(newestConsultId);
            }
        }
    }

    /**
     * 根据consultId获取最新的consultId结束咨询
     *
     * @param consultId
     */
    @RpcService
    public void endConsultByConsultId(Integer consultId) {
        logger.info("endConsultByConsultId consultId:" + consultId);
        Assert.notNull(consultId, "endConsultByConsultId consultId is null");

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDao.get(consultId);
        Assert.notNull(consult, "endConsultByConsultId consult is null! consultId:" + consultId);

        Integer newConsultId = consultDao.getNewestConsultId(consult);
        if (null != newConsultId) {
            logger.info("endConsultByConsultId consultId:" + newConsultId);
            endConsult(newConsultId);
        }
    }

    /**
     * 咨询结束服务--hyj
     */
    @RpcService
    public void endConsult(final int consultID) {
        logger.info("doctor endConsult, consultId[{}]", consultID);
        endConsultByRole(consultID, ConsultConstant.CONSULT_END_ROLE_DOCTOR);
    }

    /**
     * 咨询结束服务--hyj
     */
    public void endConsultByRole(final int consultID, final int endRole) {
        logger.info("咨询结束, consultId[{}], endRole[{}]", consultID, endRole);
        final Consult c = getById(consultID);
        final DoctorAccountDAO doctoraccountdao = DAOFactory
                .getDAO(DoctorAccountDAO.class);
        if (endRole == ConsultConstant.CONSULT_END_ROLE_DOCTOR) {
            UserRoleToken urt = UserRoleToken.getCurrent();
            if (urt == null || urt.getProperty("doctor") == null) {
                return;
            }
            Doctor doctor = (Doctor) urt.getProperty("doctor");
            Integer curDocId = doctor.getDoctorId();
            if (!c.getExeDoctor().equals(curDocId)) {
                logger.error("咨询[" + consultID + "]不是接收的执行医生，不能结束咨询");
                return;
            }
        }
        if ((ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(c.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_RECIPE.equals(c.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(c.getRequestMode())) && !c.getHasChat()) {
//            logger.error("图文咨询[" + consultID + "]未进行环信回复，不能结束咨询");
            throw new DAOException(609, "请先答复患者，再完成该咨询！");
        }
        Integer payFlag = c.getPayflag();
        if (payFlag != 1) {
            logger.error("咨询[" + consultID + "]未支付，不能结束咨询");
            return;
        }
        try {
            final int status = (ValidateUtil.isTrue(c.getTeams())&&ValidateUtil.notNullAndZeroInteger(c.getGroupMode()))?11:5;
            HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
                public void execute(StatelessSession ss) throws Exception {
                    String hql = "update Consult set endDate=:EndDate,consultStatus=2,status=:status,endRole=:endRole where consultId=:ConsultID AND consultStatus!=2";

                    Query q = ss.createQuery(hql);
                    q.setInteger("ConsultID", consultID);
                    q.setTimestamp("EndDate", new Date());
                    q.setInteger("endRole", endRole);
                    q.setInteger("status", status);
                    int updateRows = q.executeUpdate();
                    if (updateRows <= 0) {
                        throw new DAOException(DAOException.VALUE_NEEDED, "repeat reqeust for consult:" + consultID);
                    }
                }
            };
            HibernateSessionTemplate.instance().execute(action);
        } catch (DAOException e) {
            //用于防止重复提交
            logger.info(LocalStringUtil.format("app endConsult exception，countId[{}],message:[{}],stackTrace[{}]", consultID, e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            if (DAOException.VALUE_NEEDED == e.getCode()) {
                return;
            } else {
                throw e;
            }
        }
        // 增加医生收入
        logger.info("咨询结束,给执行医生增加收入,doctorId:" + c.getExeDoctor());
        Integer serverId = null;
        if (c.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_POHONE)) {// 电话咨询
            serverId = ServerPriceConstant.ID_CONSULT_PHONE;
        } else if (c.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_GRAPHIC)) {// 图文咨询
            serverId = ServerPriceConstant.ID_CONSULT_GRAPHIC;
        }else if(c.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_RECIPE)){//寻医问药
            serverId = ServerPriceConstant.ID_CONSULT_RECIPE;
        }else if(c.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_PROFESSOR)){//专家解读
            serverId = ServerPriceConstant.ID_CONSULT_PROFESSOR;
        }

        if (serverId != null) {
            //新增医生积分收入
            doctoraccountdao.addDoctorRevenue(c.getExeDoctor(), serverId,
                    c.getConsultId(), c.getConsultPrice());
            //新增医生咨询补贴
            new DoctorAccountConsultService().rewardConsult(c.getConsultId());
        }

        asynDoBussService.fireEvent(new BussFinishEvent(consultID, BussTypeConstant.CONSULT));
        // 不考虑成功不成功，都给予奖励
        logger.info("咨询结束,给执行医生的推荐医生奖励,doctorId:" + c.getExeDoctor());
        doctoraccountdao.recommendReward(c.getExeDoctor());

        try {
            if (ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(c.getRequestMode())
                    || ConsultConstant.CONSULT_TYPE_RECIPE.equals(c.getRequestMode())
                    || ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(c.getRequestMode())) {

                //图文咨询医生回复后给患者发送短信
                //sendSMSForEndConsult(c.getConsultId()); //2016-04-28 10:34:20 取消咨询短信发送服务

                //pushMsgForEndConsult(c);

                //图文咨询完成后给患者发送微信推送消息
                //wxEndConsultService.pushMessForEndConsult(c.getConsultId());

                Integer clientId = c.getDeviceId();
                SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                String executor = "DocFinishConsult";
                if(c.getRequestMode()==4){
                    executor = "DocFinishConsultForRecipe";
                }
                smsPushService.pushMsgData2Ons(consultID, c.getConsultOrgan(), executor, executor, clientId);

                // 记录系统通知消息到医患消息表
                ConsultMessageService msgService = new ConsultMessageService();
                //String notificationText = "咨询已结束，为医生的耐心解答<a href=\"" + c.getConsultId() + "\">评价</a>";
                EvaluationNotificationMsgBody msgObj = new EvaluationNotificationMsgBody();
                msgObj.setEvaSwitch(ConsultConstant.EVALUATION_MSG_SWITCH_OFF);//显示评价按钮
                msgObj.setEvaValue(null);
                msgObj.setEvaText(null);
                msgService.handleEvaluationNotificationMessage(c.getConsultId(), msgObj);

                IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
                wxService.reloadConsult(c.getAppId(), consultID);   // 刷新微信咨询单缓存
                logger.info("endConsult over consultID:[" + consultID + "]");

            } else if (ConsultConstant.CONSULT_TYPE_POHONE.equals(c.getRequestMode())) {
                //电话咨询完成后给患者发送微信推送消息
                //wxEndConsultService.pushMessForEndConsultByMobile(c.getConsultId());
                Integer clientId = c.getDeviceId();
                SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                String executor = "DocFinishConsult";
                if(c.getRequestMode()==4){
                    executor = "DocFinishConsultForRecipe";
                }
                smsPushService.pushMsgData2Ons(consultID, c.getConsultOrgan(), executor, executor, clientId);
                logger.info("电话咨询完成给患者推送微信消息consultID:[" + consultID + "]");
            }

            //yuanb 2017年6月30日10:13:09  为了同步咨询状态 发送一条CMD消息  到session组
            c.setConsultStatus(2);
            sendCMDMsgToRefreshConsult(c);

        } catch (Exception e) {
            logger.error("endConsult error:" + e.getMessage());
        }


    }

    /**
     * pc端获取首页业务
     */
    @RpcService
    public Map<String, Object> firstPageServiceForPc(int doctorID, boolean groupFlag){
        return firstPageServiceExt(doctorID, groupFlag,2);
    }

    /**
     * app端获取首页业务
     */
    @RpcService
    public Map<String, Object> firstPageServiceForApp(int doctorID, boolean groupFlag){
        return firstPageServiceExt(doctorID, groupFlag,1);
    }

    /**
     * 获取待转诊、待会诊、待咨询申请单条数服务
     * 获取待审核处方数量 zhongzx
     *
     * @param doctorID  --医生编号
     * @param groupFlag --团队标志
     * @return
     * @throws DAOException
     */
    @RpcService
    public Map<String, Object> firstPageService(int doctorID, boolean groupFlag)
            throws DAOException {
        return firstPageServiceExt(doctorID, groupFlag,0);
    }

    /**
     *
     * @param doctorID
     * @param groupFlag
     * @param flag --接收标志  1：app   2：pc
     * @return
     * @throws DAOException
     */
    @RpcService
    public Map<String, Object> firstPageServiceExt(int doctorID, boolean groupFlag, Integer flag)
            throws DAOException {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("UnConsultNum", this.getUnConsultNum(doctorID, groupFlag, flag));
        UnMeetClinicNumDAO UnMeetClinicNumDAO = DAOFactory
                .getDAO(UnMeetClinicNumDAO.class);
        map.put("UnMeetClinicNum",
                UnMeetClinicNumDAO.getUnMeetClinicNum(doctorID, groupFlag));
        TransferDAO TransferDAO = DAOFactory.getDAO(TransferDAO.class);
        map.put("UnTransferNum",
                TransferDAO.getUnTransferNum(doctorID, groupFlag));
        FollowScheduleDAO followScheduleDAO = DAOFactory.getDAO(FollowScheduleDAO.class);
        map.put("ScheduleNum",
                followScheduleDAO.getScheduleByDateCount(doctorID, new Date()).size());
        //获取待审核处方数量 zhongzx
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        map.put("UncheckedRecipeNum", recipeDAO.getUncheckedRecipeNum(doctorID));
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        map.put("SignNum",
                signRecordDAO.getCountSignList(doctorID));
        return map;
    }

    /**
     * 更新群聊开始时间到咨询表（供咨询申请服务(新增其他病历文档保存)调用）
     *
     * @param sessionStartTime
     * @param consultId
     * @author LF
     */
    @DAOMethod
    @RpcService
    public abstract void updateSessionStartTimeByConsultId(
            Date sessionStartTime, Integer consultId);

    /**
     * 更新群聊ID到咨询表（供咨询申请服务(新增其他病历文档保存)调用）
     *
     * @param sessionID
     * @param consultId
     * @author LF
     */
    @DAOMethod
    @RpcService
    public abstract void updateSessionIDByConsultId(String sessionID,
                                                    Integer consultId);


    @DAOMethod
    public abstract void updateHasChatByConsultId(Boolean hasChat,
                                                  Integer consultId);
    @DAOMethod
    public abstract void updateStatusByConsultId(Integer status,
                                                 Integer consultId);

    @DAOMethod(sql = "update Consult set hasChat = :hasChat , status = :status  where consultId = :consultId")
    public abstract  void updateHaschatAndStatusByConsultId(@DAOParam("hasChat") Boolean hasChat, @DAOParam("status") Integer status, @DAOParam("consultId") Integer consultId);

    /**
     * 将咨询单更新成已聊过天
     *
     * @return
     */
    @RpcService
    public void updateHasChat(Integer consultId) {
        logger.info("将咨询单更新成已聊过天-"+consultId);
        Consult consult = getById(consultId);
        Boolean hasChat = consult.getHasChat();
        Integer status = 4;
        if (null == hasChat || !hasChat) {
//            updateHasChatByConsultId(true, consultId);
//            updateStatusByConsultId(status,consultId);
            updateHaschatAndStatusByConsultId(true,status,consultId);
        }
    }

    /**
     * 根据前端传入的相关数据，重新设置discountType,consultCost,consultPrice三个字段
     *
     * @param consult
     * @return
     */
    public Consult resertConsultData(Consult consult) {
        // 获取签约标记，关注标记，关注ID
        RelationDoctorDAO relationDao = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        String mpi = consult.getRequestMpi();
        Integer docId = consult.getConsultDoctor();
        RelationDoctor relation = relationDao
                .getByMpiIdAndDoctorIdAndRelationType(mpi, docId);
        Boolean isSign = false;

        if (relation != null) {
            Integer type = relation.getRelationType();
            if (type != null && type == 0) {
                isSign = true;
            }
        }

        ConsultSet set = new DoctorInfoService().getDoctorDisCountSet(docId, mpi, isSign);


        if (consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_POHONE)) {
            //电话咨询
            consult.setDisCountType(set.getAppointDisCountType());
            consult.setConsultCost(set.getAppointConsultActualPrice());
            consult.setConsultPrice(set.getAppointConsultPrice());
        } else if (ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(consult.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(consult.getRequestMode())) {
            //图文咨询
            consult.setDisCountType(set.getOnlineDisCountType());
            consult.setConsultCost(set.getOnLineConsultActualPrice());
            consult.setConsultPrice(set.getOnLineConsultPrice());
        } else {
            throw new DAOException(ErrorCode.SERVICE_ERROR,
                    "目前不支持该业务哦！");
        }

        return consult;
    }

    /**
     * 取消咨询服务--hyj
     *
     * @param cancelCause --取消原因
     * @param consultID   --咨询单号
     */
    @RpcService
    public void updateCancelConsult(String cancelCause, Integer consultID) {
        if (consultID == null) {
//            logger.error("consultId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId is required");
        }
        Consult c = this.getById(consultID);
        if (c.getConsultStatus() > 0) {
//            logger.error("Can not cancel the consult");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "Can not cancel the consult");
        } else {
            logger.info("咨询取消,consultId:" + consultID + ",cancelCause:"
                    + cancelCause);
            this.updateConsult(new Date(), cancelCause, consultID, 9 ,0,8);
        }
    }

    /**
     * 患者取消咨询服务
     *
     * @param consultID --咨询单号
     */
    @RpcService
    public void patientCancelConsult(Integer consultID) {
        if (consultID == null) {
//            logger.error("consultId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId is required");
        }
        // 获取当前患者信息
        UserRoleToken urt = UserRoleToken.getCurrent();
        Patient patient = (Patient) urt.getProperty("patient");
        String requestMpiId = patient.getMpiId();
        //判断该咨询是否满足取消条件：医生是否有回复；咨询人当天取消次数是否已达取消上限（3次）
        Consult c = this.getById(consultID);


//        Consult c = this.getById(consultID);
//        if (c.getConsultStatus() > 0) {
//            logger.error("Can not cancel the consult");
//            throw new DAOException(DAOException.VALUE_NEEDED,
//                    "Can not cancel the consult");
//        } else {
//            logger.info("咨询取消,consultId:" + consultID + ",cancelCause:"
//                    + cancelCause);
//            this.updateConsult(new Date(), cancelCause, consultID, 9);
//        }
    }

    /**
     * 查询给定患者当天取消的图文咨询列表
     *
     * @param requestMpi
     * @return
     */
    @DAOMethod(sql = "from Consult where requestMpi=:requestMpi and ConsultStatus = 9 and payflag = 1 and DATE_FORMAT(cancelTime,'%Y-%m-%d') = DATE_FORMAT(NOW(),'%Y-%m-%d') and requestMode = :requestMode and cancelRole = 0")
    public abstract List<Consult> findTodayCancelConsultTimesByRequestMpiId(
            @DAOParam("requestMpi") String requestMpi, @DAOParam("requestMode") Integer requestMode);


    @DAOMethod(sql = "update Consult set endDate=:cancelTime, cancelTime=:cancelTime,cancelCause=:cancelCause,consultStatus=:consultStatus,cancelRole=:cancelRole,status = :status where consultId=:consultID")
    public abstract void updateConsult(@DAOParam("cancelTime") Date cancelTime,
                                       @DAOParam("cancelCause") String cancelCause,
                                       @DAOParam("consultID") Integer consultID,
                                       @DAOParam("consultStatus") int consultStatus,
                                       @DAOParam("cancelRole") Integer cancelRole,
                                       @DAOParam("status") Integer status);

	/*
     * 专门给 拒绝咨询用的更新方法，拒绝咨询新增了一个refuseFlag的参数(0是系统拒绝，1是医生拒绝)
	 */

    /**
     * 增加endDate赋值，解决咨询已完成列表排序问题
     * <p>
     * eh.bus.dao
     *
     * @param cancelTime
     * @param cancelCause
     * @param consultID
     * @param consultStatus
     * @param refuseFlag
     * @author luf 2016-3-7
     */
    @DAOMethod(sql = "update Consult set cancelTime=:cancelTime,endDate=:cancelTime,cancelCause=:cancelCause,consultStatus=:consultStatus,refuseFlag=:refuseFlag where consultId=:consultID")
    public abstract void updateConsultForRefuse(
            @DAOParam("cancelTime") Date cancelTime,
            @DAOParam("cancelCause") String cancelCause,
            @DAOParam("consultID") Integer consultID,
            @DAOParam("consultStatus") int consultStatus,
            @DAOParam("refuseFlag") int refuseFlag);

    /**
     * 防止刷单，在健康2.1.1版本后，新增咨询拒绝更新的咨询状态限制
     */
    @DAOMethod(sql = "update Consult set cancelTime=:cancelTime,endDate=:cancelTime,cancelCause=:cancelCause,consultStatus=:consultStatus,refuseFlag=:refuseFlag where consultId=:consultID and consultStatus<2")
    public abstract void updateConsultForRefuse2(
            @DAOParam("cancelTime") Date cancelTime,
            @DAOParam("cancelCause") String cancelCause,
            @DAOParam("consultID") Integer consultID,
            @DAOParam("consultStatus") int consultStatus,
            @DAOParam("refuseFlag") int refuseFlag);


    /**
     * 咨询拒绝服务(已废弃,前端调用refuseConsultAndbackMoney)
     *
     * @param cancelCause
     * @param consultId   zhongzx
     *                    修改：把方法最后调用的更新方法由原来的updateConsult替换成了updateConsultForRefuse
     * @author hyj
     */
    @RpcService
    public void refuseConsult(String cancelCause, Integer consultId) {
        if (consultId == null ) {
//            logger.error("consultId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId is required");
        }
        Consult consult = getById(consultId);
        if (consult == null ) {
//            logger.error("consultId is required");
            throw new DAOException(609,
                    "consult is not find");
        }
        if (StringUtils.isEmpty(cancelCause)) {
//            logger.error("cancelCause is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "cancelCause is required");
        }
        logger.info("咨询拒绝,consultId:" + consultId + ",cancelCause:"
                + cancelCause);
        this.updateConsultForRefuse2(new Date(), cancelCause, consultId, 3, 1);

        //desc_2016.04.05 加患者退款代码
        // 退还患者咨询费
        WxRefundExecutor executor = new WxRefundExecutor(consultId, "consult");
        executor.execute();

        Integer refuseFlag = 1;
        // desc_2016.4.1 系统自动拒绝给目标医生发系统消息  zhangjr
        if (refuseFlag != null && refuseFlag == 0) {
            // desc_2016.3.8 给目标医生发送系统消息 zx
            // 增加系统提醒消息
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient reqPatient = patientDAO.getByMpiId(consult.getRequestMpi());
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            Doctor doc = doctorDAO.get(consult.getConsultDoctor());
            String title = "咨询自动取消提醒";

            Integer requestMode = consult.getRequestMode();
            Integer time = SystemConstant.APPOINT_CONSULT_TIME;
            if (requestMode != null && requestMode == 2) {
                time = SystemConstant.ONLINE_CONSULT_TIME;
            }

            String detailMsg = "由于您超过" + time + "小时未处理，患者" + reqPatient.getPatientName()
                    + "向您发起的咨询申请已自动取消。";

            SessionDetailService sessionDetailservice = new SessionDetailService();
            boolean teams = consult.getTeams() == null ? false : consult.getTeams();
            sessionDetailservice.addSysTextMsgConsultToTarDoc(consultId, doc.getMobile(), title, detailMsg, teams, true);
//            sessionDetailservice.addMsgDetail(consultId, 3, 1, doc.getMobile(), "text",
//                    title, detailMsg, "", teams, true);
        }

        try {
            if (ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(consult.getRequestMode())
                    || ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult.getRequestMode())
                    || ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(consult.getRequestMode())) {
                IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
                wxService.reloadConsult(consult.getAppId(), consultId); // 刷新微信咨询单缓存
            }
        } catch (Exception e) {
            logger.error("refuseConsult-->"+e);
        }
    }

    /**
     * 咨询医生查询服务--hyj
     *
     * @param organId    --机构编码
     * @param profession --专科代码
     * @param addrArea   --属地区域
     * @param online     --接收状态
     * @return
     */
    @RpcService
    public List<Doctor> queryConsultDoctor(final Integer organId,
                                           final String profession, final String addrArea, final Integer online) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                switch (online) {
                    case 0:
                        if (organId == null || profession == null) {
                            hql = new StringBuilder(
                                    "select a from Doctor a,ConsultSet b,Organ c where a.doctorId=b.doctorId and a.organ=c.organId and c.addrArea like :addrArea and (b.onLineStatus=1 or b.appointStatus=1) and a.status=1 order by a.profession,a.doctorId");
                            Query q = ss.createQuery(hql.toString());
                            q.setParameter("addrArea", addrArea + "%");
                            list = (List<Doctor>) q.list();
                        } else {
                            hql = new StringBuilder(
                                    "select a from Doctor a,ConsultSet b where a.doctorId=b.doctorId and a.organ=:organId and a.profession=:profession and (b.onLineStatus=1 or b.appointStatus=1) and a.status=1 order by a.profession,a.doctorId");
                            Query q = ss.createQuery(hql.toString());
                            q.setParameter("organId", organId);
                            q.setParameter("profession", profession);
                            list = (List<Doctor>) q.list();
                        }
                        break;
                    case 1:
                        if (organId == null || profession == null) {
                            hql = new StringBuilder(
                                    "select a from Doctor a,ConsultSet b,Organ c where a.doctorId=b.doctorId and a.organ=c.organId and c.addrArea like :addrArea and b.onLineStatus=1 and a.status=1 and a.online=1 order by a.profession,a.doctorId");
                            Query q = ss.createQuery(hql.toString());
                            q.setParameter("addrArea", addrArea + "%");
                            list = (List<Doctor>) q.list();
                        } else {
                            hql = new StringBuilder(
                                    "select a from Doctor a,ConsultSet b where a.doctorId=b.doctorId and a.organ=:organId and a.profession=:profession and b.onLineStatus=1 and a.status=1 and a.online=1 order by a.profession,a.doctorId");
                            Query q = ss.createQuery(hql.toString());
                            q.setParameter("organId", organId);
                            q.setParameter("profession", profession);
                            list = (List<Doctor>) q.list();
                        }
                        break;
                    case 2:
                        if (organId == null || profession == null) {
                            hql = new StringBuilder(
                                    "select a from Doctor a,ConsultSet b,Organ c where a.doctorId=b.doctorId and a.organ=c.organId and c.addrArea like :addrArea and b.appointStatus=1 and a.status=1 order by a.profession,a.doctorId");
                            Query q = ss.createQuery(hql.toString());
                            q.setParameter("addrArea", addrArea + "%");
                            list = (List<Doctor>) q.list();
                        } else {
                            hql = new StringBuilder(
                                    "select a from Doctor a,ConsultSet b where a.doctorId=b.doctorId and a.organ=:organId and a.profession=:profession and b.appointStatus=1 and a.status=1 order by a.profession,a.doctorId");
                            Query q = ss.createQuery(hql.toString());
                            q.setParameter("organId", organId);
                            q.setParameter("profession", profession);
                            list = (List<Doctor>) q.list();
                        }
                        break;
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Doctor>) action.getResult();
    }

    /**
     * 病人咨询记录查询服务
     *
     * @param mpiId
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.bus.ConsultAndDoctor(a,b) from Consult a,Doctor b where a.requestMpi=:mpiId and a.consultDoctor=b.doctorId and a.payflag=1 order by a.requestTime desc")
    public abstract List<ConsultAndDoctor> findConsult(
            @DAOParam("mpiId") String mpiId);

    /**
     * 病人咨询记录查询服务--分页
     *
     * @param mpiId
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(limit = 10, sql = "select new eh.entity.bus.ConsultAndDoctor(a,b) from Consult a,Doctor b where a.requestMpi=:mpiId and a.consultDoctor=b.doctorId and a.payflag=1 order by a.requestTime desc")
    public abstract List<ConsultAndDoctor> findConsultWithPage(
            @DAOParam("mpiId") String mpiId,
            @DAOParam(pageStart = true) int start);

    /**
     * 未登陆时获取属地区域
     *
     * @param parentKey
     * @param sliceType
     */
    @RpcService
    public List<DictionaryItem> getAddrArea(String parentKey, int sliceType) {
        DictionaryLocalService ser = AppContextHolder.getBean("dictionaryService", DictionaryLocalService.class);
        List<DictionaryItem> list = new ArrayList<DictionaryItem>();
        try {
            DictionarySliceRecordSet var = ser.getSlice(
                    "eh.base.dictionary.AddrArea", parentKey, sliceType, "", 0,
                    0);
            list = var.getItems();

        } catch (ControllerException e) {
            logger.error("getAddrArea-->"+e);
        }
        return list;
    }

    /**
     * 未登录时获取病人类型
     *
     * @param parentKey
     * @param sliceType
     */
    @RpcService
    public List<DictionaryItem> getPatientType(String parentKey, int sliceType) {
        DictionaryLocalService ser = AppContextHolder.getBean("dictionaryService", DictionaryLocalService.class);
        List<DictionaryItem> list = new ArrayList<DictionaryItem>();
        try {
            DictionarySliceRecordSet var = ser.getSlice(
                    "eh.mpi.dictionary.PatientType", parentKey, sliceType, "",
                    0, 0);
            list = var.getItems();

        } catch (ControllerException e) {
            logger.error("getPatientType-->"+e);
        }
        return list;
    }

    /**
     * 查询指定日期的咨询总量
     *
     * @param date
     * @return
     * @author ZX
     * @date 2015-4-21 下午12:01:04
     */
    @RpcService
    @DAOMethod(sql = "select count(*) from Consult where date(requestTime)=date(:date) and payflag=1")
    public abstract Long getConsultTotalNumByDate(@DAOParam("date") Date date);

    /**
     * 获取当天人均咨询数(患者)
     *
     * @param requestTime
     * @return
     * @author LF
     */
    @RpcService
    public Double getAverageConsultNumForPatient(Date requestTime) {
        Long tranNum = getConsultTotalNumByDate(requestTime);
        Long patientNum = DAOFactory.getDAO(PatientDAO.class)
                .getAllPatientNum();

        if (patientNum <= 0) {
            return (double) 0;
        }
        return tranNum / (double) patientNum;
    }

    /**
     * 目标方接收咨询数统计
     *
     * @param manageUnit
     * @param startDate
     * @param endDate
     * @param consultStatus
     * @return
     * @author ZX
     * @date 2015-5-26 下午3:24:52
     */
    @RpcService
    public Long getTargetNumFromTo(final String manageUnit,
                                   final Date startDate, final Date endDate,
                                   final int... consultStatus) {
        if (startDate == null) {
//            logger.error("统计开始时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endDate == null) {
//            logger.error("统计结束时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select count(*) from Consult a ,Organ o where a.consultOrgan = o.organId and o.manageUnit like :manageUnit and  DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime)");

                // 添加转诊单状态
                if (consultStatus.length > 0) {
                    hql.append(" and (");

                    for (int string : consultStatus) {
                        hql.append(" a.consultStatus=" + string + " or ");
                    }

                    hql.delete(hql.length() - 4, hql.length());
                    hql.append(")");
                }

                Query query = ss.createQuery(hql.toString());
                query.setString("manageUnit", manageUnit);
                query.setDate("startTime", startDate);
                query.setDate("endTime", endDate);

                long num = (long) query.uniqueResult();

                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 目标方昨日咨询总数统计
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-26 下午3:27:31
     */
    @RpcService
    public Long getTargetNumForYesterday(String manageUnit) {
        Date date = Context.instance().get("date.getYesterday", Date.class);
        int[] consultStatus = {2};
        return getTargetNumFromTo(manageUnit + "%", date, date, consultStatus);
    }

    /**
     * 目标方今日咨询总数统计
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-26 下午3:28:01
     */
    @RpcService
    public Long getTargetNumForToday(String manageUnit) {
        Date date = Context.instance().get("date.getToday", Date.class);
        int[] consultStatus = {2};
        return getTargetNumFromTo(manageUnit + "%", date, date, consultStatus);
    }

    /**
     * 目标方总咨询数
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-26 下午3:28:41
     */
    @RpcService
    public Long getTargetNum(String manageUnit) {
        Date startDate = new StringToDate().convert("2014-05-06");
        Date endDate = Context.instance().get("date.getToday", Date.class);
        int[] consultStatus = {2};
        return getTargetNumFromTo(manageUnit + "%", startDate, endDate,
                consultStatus);
    }

    /**
     * 目标方一段时间内总咨询数
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-8-5 下午4:21:50
     */
    @RpcService
    public Long getTargetNumForTime(String manageUnit, Date startDate,
                                    Date endDate) {
        int[] consultStatus = {2};
        return getTargetNumFromTo(manageUnit + "%", startDate, endDate,
                consultStatus);
    }

    /**
     * 支付成功后更新支付标志
     *
     * @param tradeNo
     */
    @RpcService
    @DAOMethod(sql = "update Consult set payFlag=1 , paymentDate=:paymentDate , tradeNo=:tradeNo, consultStatus=0,status = :status, outTradeNo=:outTradeNo where consultId=:consultId ")
    public abstract void updatePayFlagByOutTradeNo(
            @DAOParam("paymentDate") Date paymentDate,
            @DAOParam("tradeNo") String tradeNo,
            @DAOParam("outTradeNo") String outTradeNo,
            @DAOParam("status") Integer status,
            @DAOParam("consultId") Integer consultId);

    /**
     * @param payflag
     * @param outTradeNo
     * @return void
     * @function 根据商户号更新业务表支付状态
     * @author zhangjr
     * @date 2015-12-28
     */
    @RpcService
    @DAOMethod(sql = "update Consult set payflag=:payflag where outTradeNo=:outTradeNo")
    public abstract void updateSinglePayFlagByOutTradeNo(
            @DAOParam("payflag") int payflag,
            @DAOParam("outTradeNo") String outTradeNo);

    /**
     * 根据咨询订单号 查询咨询单信息
     *
     * @param tradeNo
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract Consult getByOutTradeNo(String tradeNo);

    /**
     * 咨询统计查询
     *
     * @param startTime
     * @param endTime
     * @param consult
     * @param start
     * @return
     * @author ZX
     * @date 2015-5-8 上午11:49:44
     */
    @RpcService
    public List<ConsultAndPatients> findConsultWithStatic(final Date startTime,
                                                          final Date endTime, final Consult consult, final int start) {

        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<List<ConsultAndPatients>> action = new AbstractHibernateStatelessResultAction<List<ConsultAndPatients>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.ConsultAndPatients(a,b.patientSex,b.patientName,b.patientType)  from Consult a,Patient b where a.mpiid=b.mpiId and DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime) ");

                // 添加目标机构条件
                if (consult.getConsultOrgan() != null) {
                    hql.append(" and a.consultOrgan="
                            + consult.getConsultOrgan());
                }

                // 添加目标医生条件
                if (consult.getConsultDoctor() != null) {
                    hql.append(" and a.consultDoctor="
                            + consult.getConsultDoctor());
                }

                // 添加咨询单状态
                if (consult.getConsultStatus() != null) {
                    hql.append(" and a.consultStatus="
                            + consult.getConsultStatus());
                }

                hql.append(" order by a.requestTime desc");

                Query query = ss.createQuery(hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                query.setFirstResult(start);
                query.setMaxResults(10);

                List<ConsultAndPatients> tfList = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

                for (ConsultAndPatients consultAndPatients : tfList) {
                    // 目标医生电话
                    int consultDocId = consultAndPatients.getConsult()
                            .getConsultDoctor();
                    Doctor doctor = doctorDAO.getByDoctorId(consultDocId);
                    if (!StringUtils.isEmpty(doctor.getMobile())) {
                        consultAndPatients.setConsultDoctorMobile(doctor
                                .getMobile());
                    }

                    // 申请者信息
                    String requestPatientId = consultAndPatients.getConsult()
                            .getRequestMpi();
                    Patient reqPt = patientDAO.get(requestPatientId);
                    if (!StringUtils.isEmpty(reqPt.getMobile())) {
                        consultAndPatients.setRequestPatientMobile(reqPt
                                .getMobile());
                    }
                    consultAndPatients.setRequestPatientName(reqPt
                            .getPatientName());
                }
                setResult(tfList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<ConsultAndPatients>) action.getResult();
    }

    /**
     * 咨询统计记录数
     *
     * @param startTime
     * @param endTime
     * @param consult
     * @return
     * @author ZX
     * @date 2015-5-8 上午11:49:44
     */
    @RpcService
    public long getNumWithStatic(final Date startTime, final Date endTime,
                                 final Consult consult) {

        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select count(*) from Consult a,Patient b where a.mpiid=b.mpiId and DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime) ");

                // 添加目标机构条件
                if (consult.getConsultOrgan() != null) {
                    hql.append(" and a.consultOrgan="
                            + consult.getConsultOrgan());
                }

                // 添加目标医生条件
                if (consult.getConsultDoctor() != null) {
                    hql.append(" and a.consultDoctor="
                            + consult.getConsultDoctor());
                }

                // 添加咨询单状态
                if (consult.getConsultStatus() != null) {
                    hql.append(" and a.consultStatus="
                            + consult.getConsultStatus());
                }

                hql.append(" order by a.requestTime desc");

                Query query = ss.createQuery(hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);

                long num = (long) query.uniqueResult();

                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * Title: 查询咨询单 Description:
     * 在原来的基础上，新加一个搜索参数（患者主键）可为空，跟机构相关的查询条件（目标机构）由原来的一个定值改为一个数组
     *
     * @param startTime     ---咨询申请时间，开始时间（不能为空）
     * @param endTime       ---咨询申请时间，结束时间（不能为空）
     * @param consult       ---医生信息
     * @param start         ---分页使用
     * @param consultOrgans ---咨询医生机构（集合）
     * @param mpiid         ----患者主键（实际业务人）
     * @return QueryResult<ConsultAndPatients>
     * @author AngryKitty
     * @date 2015-8-31
     */
    @RpcService
    public QueryResult<ConsultAndPatients> findConsultAndPatientsByStatic(
            final Date startTime, final Date endTime, final Consult consult,
            final int start, final List<Integer> consultOrgans,
            final String mpiid) {
        this.validateOptionForStatistics(startTime, endTime, consult, start, consultOrgans, mpiid);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime, endTime, consult, start, consultOrgans, mpiid);

        HibernateStatelessResultAction<QueryResult<ConsultAndPatients>> action = new AbstractHibernateStatelessResultAction<QueryResult<ConsultAndPatients>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                int total = 0;
                StringBuilder hql = preparedHql;
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                countQuery.setDate("startTime", startTime);
                countQuery.setDate("endTime", endTime);
                total = ((Long) countQuery.uniqueResult()).intValue();//获取总条数

                hql.append(" order by a.requestTime desc");
                Query query = ss.createQuery("select new eh.entity.bus.ConsultAndPatients(a,b.patientSex,b.patientName,b.patientType) " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                query.setFirstResult(start);
                query.setMaxResults(10);

                List<ConsultAndPatients> tfList = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

                for (ConsultAndPatients consultAndPatients : tfList) {
                    // 目标医生电话
                    int consultDocId = consultAndPatients.getConsult()
                            .getConsultDoctor();
                    Doctor doctor = doctorDAO.getByDoctorId(consultDocId);
                    if (!StringUtils.isEmpty(doctor.getMobile())) {
                        consultAndPatients.setConsultDoctorMobile(doctor
                                .getMobile());
                    }

                    // 申请者信息
                    String requestPatientId = consultAndPatients.getConsult()
                            .getRequestMpi();
                    Patient reqPt = patientDAO.get(requestPatientId);
                    if (!StringUtils.isEmpty(reqPt.getMobile())) {
                        consultAndPatients.setRequestPatientMobile(reqPt
                                .getMobile());
                    }
                    consultAndPatients.setRequestPatientName(reqPt
                            .getPatientName());
                }
                QueryResult<ConsultAndPatients> qResult = new QueryResult<ConsultAndPatients>(
                        total, query.getFirstResult(), query.getMaxResults(),
                        tfList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return (QueryResult<ConsultAndPatients>) action.getResult();
    }

    private StringBuilder generateHQLforStatistics(final Date startTime, final Date endTime, final Consult consult,
                                                   final int start, final List<Integer> consultOrgans,
                                                   final String mpiid) {

        StringBuilder hql = new StringBuilder(
                "from Consult a,Patient b where a.mpiid=b.mpiId and DATE(a.requestTime)>=DATE(:startTime) and DATE(a.requestTime)<=DATE(:endTime) ");

        // 添加目标机构条件
        if (consultOrgans != null && consultOrgans.size() > 0) {
            boolean flag = true;
            for (Integer i : consultOrgans) {
                if (i != null) {
                    if (flag) {
                        hql.append(" and a.consultOrgan in(");
                        flag = false;
                    }
                    hql.append(i + ",");
                }
            }
            if (!flag) {
                hql = new StringBuilder(hql.substring(0,
                        hql.length() - 1) + ") ");
            }
        }

        if (!StringUtils.isEmpty(mpiid)) {
            hql.append(" and a.mpiid= '" + mpiid + "'");
        }

        if (consult != null) {
            // 添加目标医生条件
            if (consult.getConsultDoctor() != null) {
                hql.append(" and a.consultDoctor="
                        + consult.getConsultDoctor());
            }

            // 添加咨询单状态
            if (consult.getConsultStatus() != null) {
                if (consult.getConsultStatus() == -1) { // 增加一个非标准状态未支付订单
                    hql.append(" and a.consultStatus=0 and a.payflag=0");
                } else if (consult.getConsultStatus() == 0) {
                    hql.append(" and a.consultStatus=0 and a.payflag<>0");
                } else if (consult.getConsultStatus() > 0) {
                    hql.append(" and a.consultStatus="
                            + consult.getConsultStatus());
                }
            }

            if (consult.getHasChat() != null) {
                int hc = consult.getHasChat() ? 1 : 0;
                hql.append(" and a.hasChat="
                        + hc);
            }
            if(consult.getRequestMode() != null)
            {
                hql.append(" and a.requestMode="+  consult.getRequestMode() );
            }

        }
        return hql;
    }


    private void validateOptionForStatistics(final Date startTime, final Date endTime, final Consult consult,
                                             final int start, final List<Integer> consultOrgans,
                                             final String mpiid) {
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        Set<Integer> set = new HashSet<Integer>();
        set.addAll(consultOrgans);
        SecurityService.isAuthoritiedOrgan(set);
    }

    /**
     * Title: 根据状态统计
     *
     * @param startTime     ---咨询申请时间，开始时间（不能为空）
     * @param endTime       ---咨询申请时间，结束时间（不能为空）
     * @param consult       ---医生信息
     * @param start         ---分页使用
     * @param consultOrgans ---咨询医生机构（集合）
     * @param mpiid         ----患者主键（实际业务人）
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByStatus(
            final Date startTime, final Date endTime, final Consult consult,
            final int start, final List<Integer> consultOrgans,
            final String mpiid) {
        this.validateOptionForStatistics(startTime, endTime, consult, start, consultOrgans, mpiid);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime, endTime, consult, start, consultOrgans, mpiid);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                int totalNeedPay = 0;
                String hqlTotal = preparedHql.toString();
                hqlTotal = hqlTotal + " and a.payflag = 0  and a.consultStatus = 0";
                Query countQuery = ss.createQuery("select count(*) " + hqlTotal.toString());
                countQuery.setDate("startTime", startTime);
                countQuery.setDate("endTime", endTime);
                totalNeedPay = ((Long) countQuery.uniqueResult()).intValue();//获取总条数

                StringBuilder hql = preparedHql;
                hql.append(" group by a.consultStatus ");
                Query query = ss.createQuery("select a.consultStatus, count(a.consultId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            String status = hps[0].toString();
                            String statusName = DictionaryController.instance()
                                    .get("eh.bus.dictionary.ConsultStatus").getText(status);
                            mapStatistics.put(statusName, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * Title: 根据咨询方式统计
     *
     * @param startTime     ---咨询申请时间，开始时间（不能为空）
     * @param endTime       ---咨询申请时间，结束时间（不能为空）
     * @param consult       ---医生信息
     * @param start         ---分页使用
     * @param consultOrgans ---咨询医生机构（集合）
     * @param mpiid         ----患者主键（实际业务人）
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByRequestMode(
            final Date startTime, final Date endTime, final Consult consult,
            final int start, final List<Integer> consultOrgans,
            final String mpiid) {
        this.validateOptionForStatistics(startTime, endTime, consult, start, consultOrgans, mpiid);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime, endTime, consult, start, consultOrgans, mpiid);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                int total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.requestMode ");
                Query query = ss.createQuery("select a.requestMode, count(a.consultId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            String requestMode = hps[0].toString();
                            String statusName = DictionaryController.instance()
                                    .get("eh.bus.dictionary.RequestMode").getText(requestMode);
                            mapStatistics.put(statusName, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * Title: 根据机构统计
     *
     * @param startTime     ---咨询申请时间，开始时间（不能为空）
     * @param endTime       ---咨询申请时间，结束时间（不能为空）
     * @param consult       ---医生信息
     * @param start         ---分页使用
     * @param consultOrgans ---咨询医生机构（集合）
     * @param mpiid         ----患者主键（实际业务人）
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByConsultOrgan(
            final Date startTime, final Date endTime, final Consult consult,
            final int start, final List<Integer> consultOrgans,
            final String mpiid) {
        this.validateOptionForStatistics(startTime, endTime, consult, start, consultOrgans, mpiid);
        final StringBuilder preparedHql = this.generateHQLforStatistics(startTime, endTime, consult, start, consultOrgans, mpiid);
        HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                int total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.consultOrgan ");
                Query query = ss.createQuery("select a.consultOrgan, count(a.consultId) as count " + hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> tfList = query.list();
                HashMap<Integer, Integer> mapStatistics = new HashMap<Integer, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            Integer consultOrganId = Integer.parseInt(hps[0].toString());
                            mapStatistics.put(consultOrganId, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        HashMap<Integer, Integer> map = action.getResult();
        return DoctorUtil.translateOrganHash(map);
    }


    /**
     * 根据 咨询执行医生 和 执行状态 查询咨询列表
     *
     * @param exeDoctor     执行医生
     * @param consultStatus 咨询状态(0:待处理；1：处理中；2：咨询结束；3：拒绝；9：取消)
     * @return
     * @author ZX
     * @date 2015-9-7 下午6:13:13
     */
    @RpcService
    @DAOMethod
    public abstract List<Consult> findByExeDoctorAndConsultStatus(
            int exeDoctor, int consultStatus);

    /**
     * 查询历史咨询单列表服务 (纯分页)
     *
     * @param exeDoctor --执行医生
     * @param mpiId     --病人主索引
     * @param start     记录起始位置
     * @param limit     查询记录数
     * @return List<ConsultAndPatient>
     * @author luf
     */
    @RpcService
    public List<ConsultAndPatient> queryConsultHisListWithPage(
            Integer exeDoctor, String mpiId, int start, int limit) {
        List<ConsultAndPatient> caps = new ArrayList<ConsultAndPatient>();
        List<Consult> consults = this.findConsultHisListWithPage(exeDoctor,
                mpiId, start, limit);
        RelationDoctorDAO relationDoctorDAO = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        for (Consult consult : consults) {
            ConsultAndPatient cap = new ConsultAndPatient();
            consult.setRequestDate(DateConversion.convertRequestDateForBuss(consult.getRequestTime()));
            String rmpiId = consult.getMpiid();
            Integer consultDoctor = consult.getConsultDoctor();
            Boolean signFlag = false;
            if (exeDoctor != null) {
                signFlag = relationDoctorDAO.getSignFlag(rmpiId, exeDoctor);
            }
            Boolean teams = (doctorDAO.get(consultDoctor)).getTeams();
            cap.setSignFlag(signFlag);
            cap.setTeams(teams);
            Patient patient = patientDAO.get(rmpiId);
            cap.setPatientSex(patient.getPatientSex());
            cap.setBirthday(patient.getBirthday());
            cap.setPhoto(patient.getPhoto());
            cap.setPatientName(patient.getPatientName());
            cap.setPatientType(patient.getPatientType());
            SubConsult subConsult = BeanUtils.map(consult, SubConsult.class);
            subConsult.setStatusText((subConsult.getStatus()==5||subConsult.getStatus()==6)?"已完成":subConsult.getStatusText());
            cap.setConsult(subConsult);
            caps.add(cap);
        }
        return caps;
    }

    /**
     * 供 queryConsultHisListWithPage 调用
     *
     * @param exeDoctor --执行医生
     * @param mpiId     --病人主索引
     * @param start     记录起始位置
     * @param limit     查询记录数
     * @return List<Consult>
     * @author luf
     */
    @RpcService
    public List<Consult> findConsultHisListWithPage(final Integer exeDoctor,
                                                    final String mpiId, final int start, final int limit) {
        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
                List<Integer> groups = dao.findDoctorIdsByMemberId(exeDoctor);

                StringBuilder hql = new StringBuilder(
                        "from Consult where ((consultStatus>1 ");
                if (exeDoctor != null) {
                    hql.append("and (exeDoctor=:exeDoctor or consultDoctor=:exeDoctor) ");
                }
                hql.append(")");
                if (groups != null && !groups.isEmpty()) {
                    hql.append(" OR (exeDoctor is null and consultDoctor in(:groups) and (consultStatus=9 or consultStatus=3) )");
                    //2016-12-16 luf：非抢单模式团队
                    hql.append(" or (consultStatus>1 and consultDoctor in(:groups) and groupMode=1) ");
                }
                hql.append(")");

                if (!StringUtils.isEmpty(mpiId)) {
                    hql.append("and mpiid=:mpiId ");
                }
                hql.append("and payflag=1 and requestMode in (1,2,5) order by endDate desc");
                Query q = ss.createQuery(hql.toString());
                if (exeDoctor != null) {
                    q.setParameter("exeDoctor", exeDoctor);
                }

                if (groups != null && !groups.isEmpty()) {
                    q.setParameterList("groups", groups);
                }

                if (!StringUtils.isEmpty(mpiId)) {
                    q.setParameter("mpiId", mpiId);
                }
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<Consult> consults = q.list();
                setResult(consults);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 咨询有效时段查询
     *
     * @param consultDate 资询时间
     * @param doctorId    医生内码
     * @return List<Object[]>
     * @author luf
     */
    @RpcService
    public List<Object[]> getEffConsultTime(Date consultDate, Integer doctorId) {
        List<Object[]> results = new ArrayList<Object[]>();
        ConsultSetDAO setDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        Object[] items = setDAO.getThreeByDoctorAndDate(consultDate, doctorId);
        if (items == null || items[0] == null || items[1] == null) {
            return results;
        }
        Date startTimeWeek = (Date) items[0];
        if (StringUtils.isEmpty(startTimeWeek)) {
            return results;
        }
        Date endTimeWeek = (Date) items[1];
        if (StringUtils.isEmpty(endTimeWeek)) {
            return results;
        }
        String ymd = DateConversion.getDateFormatter(consultDate, "yyyy-MM-dd");
        Date start = DateConversion.getCurrentDate(
                ymd + " " + startTimeWeek.toString(), "yyyy-MM-dd HH:mm:ss");
        Date end = DateConversion.getCurrentDate(
                ymd + " " + endTimeWeek.toString(), "yyyy-MM-dd HH:mm:ss");
        Integer intervalTime = (Integer) items[2];
        List<Object[]> list = DateConversion.getIntervalTimeList(start, end,
                intervalTime);
        for (Object[] os : list) {
            Date startDate = (Date) os[0];
            List<Consult> consults = findEffTimeByDoctorAndAppointTime(
                    doctorId, startDate);
            if ((consults == null || consults.size() <= 0)
                    && startDate.after(new Date())) {
                results.add(os);
            }
        }
        return results;
    }

    /**
     * 供getEffConsultTime调用
     *
     * @param consultDoctor 目标医生
     * @param appointTime   电话咨询开始时间
     * @return
     * @author luf
     * @date 2016-2-24 下午7:43:08
     */
    @DAOMethod
    public abstract List<Consult> findByConsultDoctorAndAppointTime(
            Integer consultDoctor, Date appointTime);

    /**
     * 替换findByConsultDoctorAndAppointTime
     *
     * @param consultDoctor
     * @param appointTime
     * @return
     */
    @DAOMethod(sql = "From Consult where consultDoctor=:consultDoctor and appointTime=:appointTime and consultStatus<=2 and payflag=1")
    public abstract List<Consult> findConsultByDoctorAndTimePaied(
            @DAOParam("consultDoctor") Integer consultDoctor, @DAOParam("appointTime") Date appointTime);

    /**
     * 替换findByConsultDoctorAndAppointTime
     *
     * @param consultDoctor
     * @param appointTime
     * @return
     */
    @DAOMethod(sql = "From Consult where consultDoctor=:consultDoctor and appointTime=:appointTime and consultStatus<=2")
    public abstract List<Consult> findEffTimeByDoctorAndAppointTime(
            @DAOParam("consultDoctor") Integer consultDoctor, @DAOParam("appointTime") Date appointTime);

    /**
     * @param @param  doctorId 医生编码
     * @param @param  start 分页起始位置 （开始是0）
     * @param @param  limit 最大限制条数 为null不分页
     * @param @return
     * @return List<Consult> 咨询单列表
     * @Description: 分页查询已结束的咨询单列表
     * @author Zhongzx
     * @Date 2015-12-10上午10:37:15
     */
    @RpcService
    public List<? extends Consult> findFinishedConsultByDoctorIdWithPage(
            final Integer doctorId, final Integer start, final Integer limit) {
//        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
//            @SuppressWarnings("unchecked")
//            @Override
//            public void execute(StatelessSession ss) throws Exception {
//                DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
//                List<Integer> groups = dao.findDoctorIdsByMemberId(doctorId);
//
//                StringBuilder hql = new StringBuilder(
//                        "from Consult where ( (exeDoctor=:doctorId or consultDoctor=:doctorId) and (consultStatus=2 or consultStatus=3 or consultStatus=9) )");
//
//                if (groups != null && !groups.isEmpty()) {
//                    hql.append(" OR (exeDoctor is null and consultDoctor in(:groups) and (consultStatus=9 or consultStatus=3) )");
//                    // 2016-12-16 luf:非抢单模式团队医生列表
//                    hql.append(" OR (consultStatus>1 and consultDoctor in(:groups) and groupMode=1)");
//                }
//
//                hql.append(" order by endDate desc");
//                Query q = ss.createQuery(hql.toString());
//                q.setParameter("doctorId", doctorId);
//                if (groups != null && !groups.isEmpty()) {
//                    q.setParameterList("groups", groups);
//                }
//
//                if (limit != null) {
//                    q.setFirstResult(start);
//                    q.setMaxResults(limit);
//                }
//                setResult(q.list());
//            }
//        };
//        HibernateSessionTemplate.instance().execute(action);
//        return action.getResult();
        List<Integer> requestModes = new ArrayList<Integer>();
        requestModes.add(CONSULT_TYPE_GRAPHIC);
        requestModes.add(CONSULT_TYPE_POHONE);
        requestModes.add(CONSULT_TYPE_RECIPE);
        requestModes.add(CONSULT_TYPE_PROFESSOR);
        List<Consult> consultList = findFinishedConsultByDoctorIdAndRequestModeWithPage(doctorId,start,limit,requestModes);
        List<SubConsult> subConsultList = Lists.newArrayList();
        if(ValidateUtil.notBlankList(consultList)){
            for(Consult consult : consultList){
                SubConsult subConsult = BeanUtils.map(consult, SubConsult.class);
                subConsult.setStatusText((subConsult.getStatus()==5||subConsult.getStatus()==6)?"已完成":subConsult.getStatusText());
                subConsultList.add(subConsult);
            }
        }
        return subConsultList;
    }

    /**
     * 应app3.8.1要求 新增咨询类型条件查询已完成订单
     *
     * @param doctorId 医生编码
     * @param start     分页起始位置
     * @param limit     最大限制条数 为null不分页
     * @param requestModes   需要查询的咨询单类型
     * @return List<Consult> 咨询单列表
     * @author yuanb
     * @Date 2017-02-15
     */
    public List<Consult> findFinishedConsultByDoctorIdAndRequestModeWithPage(
            final Integer doctorId, final Integer start, final Integer limit, final List<Integer> requestModes) {
        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
                List<Integer> groups = dao.findDoctorIdsByMemberId(doctorId);

                StringBuilder hql = new StringBuilder(
                        "from Consult where ( ( (exeDoctor=:doctorId or consultDoctor=:doctorId) and (consultStatus=2 or consultStatus=3 or (consultStatus=9 AND Payflag>=1)) )");

                if (groups != null && !groups.isEmpty()) {
                    hql.append(" OR (exeDoctor is null and consultDoctor in(:groups) and ((consultStatus=9 AND Payflag>=1) or consultStatus=3) )");
                    // 2016-12-16 luf:非抢单模式团队医生列表
                    hql.append(" OR ((consultStatus in (2, 3) or (consultStatus=9 AND Payflag>=1)) and consultDoctor in(:groups) and groupMode=1)");
                }

                hql.append(" ) AND requestMode in (:requestModes) order by endDate desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameterList("requestModes",requestModes);
                if (groups != null && !groups.isEmpty()) {
                    q.setParameterList("groups", groups);
                }

                if (limit != null) {
                    q.setFirstResult(start);
                    q.setMaxResults(limit);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }



    /**
     * 询医问药咨询
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    private List<Consult> findRecipeConsultWithPage(
            final Integer doctorId, final Integer start, final Integer limit) {
        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
                List<Integer> groups = dao.findDoctorIdsByMemberId(doctorId);

                StringBuilder hql = new StringBuilder();
                hql.append("FROM Consult WHERE requestMode=:requestMode AND payflag!=0 AND (consultDoctor=:doctorId OR exeDoctor=:doctorId ");
                if (groups != null && !groups.isEmpty()) {
                    hql.append(" OR consultDoctor in(:groups) ");
                }
                hql.append(") order by consultId desc ");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("requestMode", ConsultConstant.CONSULT_TYPE_RECIPE);
                q.setParameter("doctorId", doctorId);
                if (groups != null && !groups.isEmpty()) {
                    q.setParameterList("groups", groups);
                }
                if (limit != null) {
                    q.setFirstResult(start);
                    q.setMaxResults(limit);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * @param @param  doctorId 医生编码
     * @param @param  start 分页起始位置（开始是0）
     * @param @param  limit 最大限制条数 为null不分页
     * @param @return
     * @return List<Consult> 咨询单列表
     * @Description:  分页查询未完成的咨询单列表
     * @author Zhongzx
     * @Date 2015-12-10上午10:51:49
     */
    @RpcService
    public List<Consult> findUnfinishedConsultByDoctorIdWithPage(
            final Integer doctorId, final Integer start, final Integer limit) {
//        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
//            @SuppressWarnings("unchecked")
//            @Override
//            public void execute(StatelessSession ss) throws Exception {
//                DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
//                List<Integer> groups = dao.findDoctorIdsByMemberId(doctorId);
//
//                StringBuilder hql = new StringBuilder(
//                        "FROM Consult WHERE ((consultStatus=0 AND(consultDoctor=:doctorId");
//                if (groups != null && !groups.isEmpty()) {
//                    hql.append(" OR consultDoctor in(:groups)");
//                }
//                hql.append(")) OR (consultStatus=1 AND exeDoctor=:doctorId)");
//                if (groups != null && !groups.isEmpty()) {
//                    //2016-12-16 luf：非抢单模式团队
//                    hql.append(" OR (consultStatus<=1 and consultDoctor in(:groups) and groupMode=1)");
//                }
//                hql.append(") AND payflag=1 order by requestTime desc");
//                Query q = ss.createQuery(hql.toString());
//                q.setParameter("doctorId", doctorId);
//                if (groups != null && !groups.isEmpty()) {
//                    q.setParameterList("groups", groups);
//                }
//                if (limit != null) {
//                    q.setFirstResult(start);
//                    q.setMaxResults(limit);
//                }
//                setResult(q.list());
//            }
//        };
//        HibernateSessionTemplate.instance().execute(action);
//        return action.getResult();
        List<Integer> requestModes = new ArrayList<Integer>();
        requestModes.add(CONSULT_TYPE_GRAPHIC);
        requestModes.add(CONSULT_TYPE_POHONE);requestModes.add(CONSULT_TYPE_RECIPE);requestModes.add(CONSULT_TYPE_PROFESSOR);
        return findUnfinishedConsultByDoctorIdAndRequestModeWithPage(doctorId,start,limit,requestModes);
    }

    /**
     * 应app3.8.1要求 新增咨询类型条件查询未完成订单
     *
     * @param doctorId 医生编码
     * @param start     分页起始位置
     * @param limit     最大限制条数 为null不分页
     * @param requestModes   需要查询的咨询单类型
     * @return List<Consult> 咨询单列表
     * @author yuanb
     * @Date 2017-02-15
     */
    public List<Consult> findUnfinishedConsultByDoctorIdAndRequestModeWithPage(
            final Integer doctorId, final Integer start, final Integer limit, final List<Integer> requestModes){
        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
                List<Integer> groups = dao.findDoctorIdsByMemberId(doctorId);

                StringBuilder hql = new StringBuilder(
                        "FROM Consult WHERE ((consultStatus=0 AND(consultDoctor=:doctorId");
                if (groups != null && !groups.isEmpty()) {
                    hql.append(" OR consultDoctor in(:groups)");
                }
                hql.append(")) OR (consultStatus=1 AND exeDoctor=:doctorId)");
                if (groups != null && !groups.isEmpty()) {
                    //2016-12-16 luf：非抢单模式团队
                    hql.append(" OR (consultStatus<=1 and consultDoctor in(:groups) and groupMode=1)");
                }
                hql.append(") AND payflag=1 AND requestMode in(:requestModes) order by requestTime desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameterList("requestModes",requestModes);
                if (groups != null && !groups.isEmpty()) {
                    q.setParameterList("groups", groups);
                }
                if (limit != null) {
                    q.setFirstResult(start);
                    q.setMaxResults(limit);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 应app3.8需求统一 findUnfinishedConsultAndPatientByDoctorId && findFinishedConsultAndPatientByDoctorId
     *
     * @param doctorId
     * @param start
     * @param limit
     * @param isFinished 0未完成，1已完成
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findConsultByDoctorIdWithPage(
            Integer doctorId, Integer start, Integer limit, int isFinished) {
        if (isFinished == 1) {
            return findFinishedConsultAndPatientByDoctorId(doctorId, start, limit);
        } else {
            return findUnfinishedConsultAndPatientByDoctorId(doctorId, start, limit);
        }
    }

    /**
     * 询医问药咨询单
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findRecipeConsultAndPatientByDoctorId(
            Integer doctorId, Integer start, Integer limit) {
        PatientDAO pdao = DAOFactory.getDAO(PatientDAO.class);
        RelationDoctorDAO rdao = DAOFactory.getDAO(RelationDoctorDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);

        if (null == doctorId) {
            throw new DAOException(DAOException.VALUE_NEEDED,"doctorId is required");
        }
        List<Consult> cons = this.findRecipeConsultWithPage(doctorId, start, limit);
        List<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();
        if (cons != null) {
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            for (Consult c : cons) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                Patient p = pdao.getPatientByMpiId(c.getMpiid());
                RelationDoctor rd = reDao.getByMpiidAndDoctorId(c.getMpiid(),doctorId);
                p.setSignFlag(rdao.getSignFlag(p.getMpiId(), doctorId));
                if (rd != null) {
                    p.setRelationFlag(true);
                    p.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                            .getRelationDoctorId()));
                }
                //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
                //解决方案，idcard字段赋值为空字符串
                if (StringUtils.isEmpty(p.getIdcard())) {
                    p.setIdcard("");
                }
                //设置问卷信息
                Boolean b = setConsultQuestionnaire(c);
                if(b){
                    c.setLeaveMess(c.getQuestionnaire().getQuestionDesc()+"\n"+c.getLeaveMess());
                }

                map.put("patient", p);
                c.setTime(DateConversion.convertRequestDateForBuss(c.getRequestTime()));
                c.setTeams(doctorDAO.getTeamsByDoctorId(c.getConsultDoctor()));
                SubConsult subConsult = BeanUtils.map(c, SubConsult.class);
                subConsult.setStatusText((subConsult.getStatus()==5||subConsult.getStatus()==6)?"已完成":subConsult.getStatusText());
                map.put("consult", subConsult);

                Patient requestPat = pdao.get(c.getRequestMpi());
                Patient reqPat = new Patient();
                reqPat.setPatientName(requestPat.getPatientName());
                map.put("requestPatient", reqPat);

                //咨询会话最新的咨询单ID
                if(ConsultConstant.CONSULT_STATUS_PENDING == c.getConsultStatus() || ConsultConstant.CONSULT_STATUS_HANDLING == c.getConsultStatus()) {
                    Integer newestConsultId = this.getNewestConsultId(c);
                    if (null != newestConsultId) {
                        map.put("newestConsultId", newestConsultId);
                    }
                }

                mapList.add(map);
            }
        }
        return mapList;

    }

    /**
     * @param @param  doctorId 医生编码
     * @param @param  start 分页起始位置（开始是0）
     * @param @param  limit 最大限制条数 为null不分页
     * @param @return
     * @return List<HashMap<String,Object>> 咨询单和对应患者 map 列表
     * @Description:  分页查询 已结束的咨询单和对应的患者 列表
     * @author Zhongzx
     * @Date 2015-12-10上午10:52:44
     * @Date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public List<HashMap<String, Object>> findFinishedConsultAndPatientByDoctorId(
            Integer doctorId, Integer start, Integer limit) {
        PatientDAO pdao = DAOFactory.getDAO(PatientDAO.class);
        RelationDoctorDAO rdao = DAOFactory.getDAO(RelationDoctorDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        if (null == doctorId) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
//      List<Consult> cons = this.findFinishedConsultByDoctorIdWithPage(
//              doctorId, start, limit);
//      app3.8.1新增专家解读，寻医问药  咨询列表需要条件查询
        List<Consult> cons = this.findFinishedConsultByDoctorIdAndRequestModeWithPage(
             doctorId, start, limit, ImmutableList.of(CONSULT_TYPE_GRAPHIC,CONSULT_TYPE_POHONE,CONSULT_TYPE_PROFESSOR));
        List<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();
        if (cons != null) {
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            for (Consult c : cons) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                Patient p = pdao.getPatientByMpiId(c.getMpiid());
                RelationDoctor rd = reDao.getByMpiidAndDoctorId(c.getMpiid(),
                        doctorId);
                p.setSignFlag(rdao.getSignFlag(p.getMpiId(), doctorId));
                if (rd != null) {
                    p.setRelationFlag(true);
                    p.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                            .getRelationDoctorId()));
                }
                //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
                //解决方案，idcard字段赋值为空字符串
                if (StringUtils.isEmpty(p.getIdcard())) {
                    p.setIdcard("");
                }

                //微信3.1 新增判断团队咨询  当前登录医生是否参与过回复
                if(ValidateUtil.isTrue(c.getTeams())){
                    UserRoleToken urt = UserRoleToken.getCurrent();
                    Doctor doc = (Doctor) urt.getProperty("doctor");
                    Integer sendId=null;
                    if (doc != null) {
                        sendId = doc.getDoctorId();
                        List<BusConsultMsg> msgs = findMsgsByConsultIdAndExeDoctor(c.getConsultId(),sendId);
                        if(ValidateUtil.notBlankList(msgs)){
                            c.setDocHasChat(true);
                        }else{
                            c.setDocHasChat(false);
                        }
                    }else{
                        c.setDocHasChat(false);
                    }
                }else{
                    c.setDocHasChat(false);
                }

                map.put("patient", p);
                c.setTime(DateConversion.convertRequestDateForBuss(c
                        .getRequestTime()));
                c.setTeams(doctorDAO.getTeamsByDoctorId(c.getConsultDoctor()));
                SubConsult subConsult = BeanUtils.map(c, SubConsult.class);
                subConsult.setStatusText((subConsult.getStatus()==5||subConsult.getStatus()==6)?"已完成":subConsult.getStatusText());
                map.put("consult", subConsult);

                Patient requestPat = pdao.get(c.getRequestMpi());
                Patient reqPat = new Patient();
                reqPat.setPatientName(requestPat.getPatientName());
                map.put("requestPatient", reqPat);

                //咨询会话最新的咨询单ID
                Integer newestConsultId = this.getNewestConsultId(c);
                if (null != newestConsultId) {
                    map.put("newestConsultId", newestConsultId);
                }

                mapList.add(map);
            }
        }
        return mapList;

    }

    /**
     * @param @param  doctorId 医生编码
     * @param @param  start 分页起始位置（开始是0）
     * @param @param  limit 最大限制条数 为null不分页
     * @param @return
     * @return List<HashMap<String,Object>> 咨询单和对应患者 map 列表
     * @Description:  分页查询 未完成的咨询单和对应的患者 列表
     * @author Zhongzx
     * @Date 2015-12-10上午10:58:41
     * @Date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public List<HashMap<String, Object>> findUnfinishedConsultAndPatientByDoctorId(
            Integer doctorId, Integer start, Integer limit) {
        PatientDAO pdao = DAOFactory.getDAO(PatientDAO.class);
        RelationDoctorDAO rdao = DAOFactory.getDAO(RelationDoctorDAO.class);
        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        if (null == doctorId) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
//      List<Consult> cons = this.findUnfinishedConsultByDoctorIdWithPage(
//                doctorId, start, limit);
//      app3.8.1新增专家解读，寻医问药  咨询列表需要条件查询
        List<Consult> cons = this.findUnfinishedConsultByDoctorIdAndRequestModeWithPage(
               doctorId, start, limit,ImmutableList.<Integer>of(CONSULT_TYPE_GRAPHIC,CONSULT_TYPE_POHONE,CONSULT_TYPE_PROFESSOR));
        List<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();
        if (cons != null) {
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            for (Consult c : cons) {
                HashMap<String, Object> map = new HashMap<String, Object>();
                Patient p = pdao.getPatientByMpiId(c.getMpiid());
                p.setSignFlag(rdao.getSignFlag(p.getMpiId(), doctorId));
                RelationDoctor rd = reDao.getByMpiidAndDoctorId(c.getMpiid(),
                        doctorId);
                if (rd != null) {
                    p.setRelationFlag(true);
                    p.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                            .getRelationDoctorId()));
                }
                //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
                //解决方案，idcard字段赋值为空字符串
                if (StringUtils.isEmpty(p.getIdcard())) {
                    p.setIdcard("");
                }

                //微信3.1 新增判断团队咨询  当前登录医生是否参与过回复
                if(ValidateUtil.isTrue(c.getTeams())){
                    UserRoleToken urt = UserRoleToken.getCurrent();
                    Doctor doc = (Doctor) urt.getProperty("doctor");
                    Integer sendId=null;
                    if (doc != null) {
                        sendId = doc.getDoctorId();
                        List<BusConsultMsg> msgs = findMsgsByConsultIdAndExeDoctor(c.getConsultId(),sendId);
                        if(ValidateUtil.notBlankList(msgs)){
                            c.setDocHasChat(true);
                        }else{
                            c.setDocHasChat(false);
                        }
                    }else{
                        c.setDocHasChat(false);
                    }
                }else{
                    c.setDocHasChat(false);
                }

                map.put("patient", p);
                c.setTime(DateConversion.convertRequestDateForBuss(c
                        .getRequestTime()));
                c.setTeams(doctorDAO.getTeamsByDoctorId(c.getConsultDoctor()));
                SubConsult subConsult = BeanUtils.map(c, SubConsult.class);
                subConsult.setStatusText((subConsult.getStatus()==5||subConsult.getStatus()==6)?"已完成":subConsult.getStatusText());
                map.put("consult", subConsult);

                Patient requestPat = pdao.get(c.getRequestMpi());
                Patient reqPat = new Patient();
                reqPat.setPatientName(requestPat.getPatientName());
                map.put("requestPatient", reqPat);

                //咨询会话最新的咨询单ID
                Integer newestConsultId = this.getNewestConsultId(c);
                if (null != newestConsultId) {
                    map.put("newestConsultId", newestConsultId);
                }

                mapList.add(map);
            }
        }
        return mapList;

    }

    /**
     * @param @param consultId 咨询单序号
     * @param @param replyOpinion 回复意见
     * @return boolean true 保存 回复意见 成功
     * @throws ControllerException
     * @Description:  保存医生回复意见
     * @author Zhongzx
     * @Date 2015-12-9上午10:44:33
     */
    @RpcService
    public boolean saveReplyOpinion(Integer consultId, String replyOpinion) {
        if (null == consultId || StringUtils.isEmpty(replyOpinion)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId or replyOpinion is required");
        }
        Consult c = getById(consultId);
        if (null == c) {
            throw new DAOException(DAOException.VALUE_NEEDED, "Consult is null");
        }
        c.setReplyOpinion(replyOpinion);
        update(c);
        return true;
    }

    /**
     * 根据咨询单Id查询回复意见
     * zhongzx
     *
     * @param consultId
     * @return
     */
    @RpcService
    public String getReplyOpinionByConsultId(Integer consultId) {
        if (consultId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "consultId is needed");
        }
        Consult c = getById(consultId);
        if (c == null) {
            throw new DAOException(609, "此咨询单不存在");
        }
        return c.getReplyOpinion();
    }
    /**
     * 根据mpiId获取咨询单列表
     *
     * @param mpiId     主索引
     * @param startPage 分页
     * @author xiebz
     * @Date 2015-12-19
     */
    @RpcService
    @DAOMethod(orderBy = "RequestTime desc", limit = 10)
    public abstract List<Consult> findByMpiId(String mpiId, long startPage);

    /**
     * 根据requestMpi获取咨询单列表
     *
     * @param requestMpi 患者端当前登陆的患者mpi
     * @param startPage  分页
     * @author xiebz
     * @Date 2016-1-18
     */
    @RpcService
    @DAOMethod(orderBy = "RequestTime desc", limit = 10)
    public abstract List<Consult> findByRequestMpiAndPayflag(String requestMpi,
                                                             Integer payflag, long startPage);

    /**
     * 健康端根据requestMpi获取咨询列表
     */
    @RpcService
    @DAOMethod(sql = "from Consult where requestMpi=:requestMpi and ((payflag=0 AND consultStatus in (4,9)) OR payflag in (1,2,3,4)) order by consultId desc", limit = 10)
    public abstract List<Consult> findConsultListByRequestMpi(@DAOParam("requestMpi") String requestMpi, @DAOParam(pageStart = true) int start);

    @DAOMethod(sql = "from Consult where requestMpi=:requestMpi and ((payflag=0 AND consultStatus in (4,9)) OR payflag in (1,2,3,4)) and requestMode in(1,2) order by consultId desc", limit = 10)
    public abstract List<Consult> findGraphicAndPhoneConsultListByRequestMpi(@DAOParam("requestMpi") String requestMpi, @DAOParam(pageStart = true) int start);


    /**
     * 2017-2-13 by yuanb
     * 健康端增加条件获取咨询列表
     */
    @RpcService
    @DAOMethod(sql = "from Consult where requestMpi=:requestMpi and ((payflag=0 AND consultStatus in (4,9)) OR payflag in (1,2,3,4)) and requestMode = :requestMode order by consultId desc", limit = 10)
    public abstract List<Consult> findConsultListByRequestMpiAndRequestMode(@DAOParam("requestMpi") String requestMpi, @DAOParam(pageStart = true) int start,@DAOParam("requestMode")int requestMode);

    /**
     * 健康APP未做专家解读，寻医问药，列表中返回只包含图文咨询，电话咨询
     * @param requestMpi
     * @param startPage
     * @return
     * @throws ParseException
     */
    @RpcService
    public List<Object> dealWithConsults2(String requestMpi, int startPage)
            throws ParseException {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);

        List<Consult> consults = findGraphicAndPhoneConsultListByRequestMpi(requestMpi,
                startPage);
        List<Object> list = new ArrayList<>();
        DateConversion d = new DateConversion();
        if (consults == null) {
            return list;
        }
        for (Consult c : consults) {
            Map<String, Object> m = new HashMap<String, Object>();
            if (c.getRequestTime() == null) {
                c.setTime(null);
            } else {
                c.setTime(d.convertRequestDateForBuss(c.getRequestTime()));
            }
            Doctor doctor = dao.getByDoctorId(c.getConsultDoctor());
            if (doctor == null) {
                m.put("consults", c);
                list.add(m);
                continue;
            }
            // 医生信息
            Doctor doc = new Doctor();
            doc.setPhoto(doctor.getPhoto());// 头像
            doc.setName(doctor.getName());// 姓名
            doc.setProTitle(doctor.getProTitle());// 职称
            doc.setDepartment(doctor.getDepartment());// 科室
            doc.setOrgan(doctor.getOrgan());// 机构
            doc.setGender(doctor.getGender());// 性别
            doc.setProfession(doctor.getProfession());// 专科编码
            doc.setTeams(doctor.getTeams());
            m.put("consults", c);
            m.put("doctor", doc);
            list.add(m);
        }
        return list;
    }

    /**
     * 健康端根据requestMpi获取咨询列表(改变时间格式)
     *
     * @param requestMpi 患者端当前登陆的患者mpi
     * @param startPage  分页
     * @return consults 咨询列表
     * @throws ParseException
     * @author xiebz
     * @Date 2016-1-18
     */
    @SuppressWarnings("static-access")
    @RpcService
    public List<Object> dealWithConsults(String requestMpi, int startPage)
            throws ParseException {
//        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
//
//        List<Consult> consults = findConsultListByRequestMpi(requestMpi,
//                startPage);
//        List<Object> list = new ArrayList<>();
//        DateConversion d = new DateConversion();
//        if (consults == null) {
//            return list;
//        }
//        for (Consult c : consults) {
//            Map<String, Object> m = new HashMap<String, Object>();
//            if (c.getRequestTime() == null) {
//                c.setTime(null);
//            } else {
//                c.setTime(d.convertRequestDateForBuss(c.getRequestTime()));
//            }
//            Doctor doctor = dao.getByDoctorId(c.getConsultDoctor());
//            if (doctor == null) {
//                m.put("consults", c);
//                list.add(m);
//                continue;
//            }
//            // 医生信息
//            Doctor doc = new Doctor();
//            doc.setPhoto(doctor.getPhoto());// 头像
//            doc.setName(doctor.getName());// 姓名
//            doc.setProTitle(doctor.getProTitle());// 职称
//            doc.setDepartment(doctor.getDepartment());// 科室
//            doc.setOrgan(doctor.getOrgan());// 机构
//            doc.setGender(doctor.getGender());// 性别
//            doc.setProfession(doctor.getProfession());// 专科编码
//            doc.setTeams(doctor.getTeams());
//            m.put("consults", c);
//            m.put("doctor", doc);
//            list.add(m);
//        }

        Integer requestMode = 0;
        List<Object> list = dealWithConsultsByRequestMode(requestMpi,startPage,requestMode);
        return list;
    }

    /**
     * 健康端2.8新增： 健康端根据requestMpi和requestMode获取咨询列表(改变时间格式)
     *
     * @param requestMpi 患者端当前登陆的患者mpi
     * @param startPage  分页
     * @param requestMode   患者咨询类型
     * @return consults 咨询列表
     * @throws ParseException
     * @author yuanb
     * @Date 2017-2-13
     */
    @SuppressWarnings("static-access")
    @RpcService
    public List<Object> dealWithConsultsByRequestMode(String requestMpi, int startPage,Integer requestMode)
            throws ParseException {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        List<Consult> consults  = new ArrayList<Consult>();
        if(0 == requestMode){
            consults = findConsultListByRequestMpi(requestMpi,
                    startPage);
        }else{
            if(2==( requestMode)){
                int rm = CONSULT_TYPE_GRAPHIC;
                consults = findConsultListByRequestMpiAndRequestMode(requestMpi,startPage,rm);
            }else if(1==(requestMode)){
                int rm = CONSULT_TYPE_POHONE;
                consults = findConsultListByRequestMpiAndRequestMode(requestMpi,startPage,rm);
            }else if(4==(requestMode)){
                int rm = CONSULT_TYPE_RECIPE;
                consults = findConsultListByRequestMpiAndRequestMode(requestMpi,startPage,rm);
            }else if(5==(requestMode)){
                int rm = CONSULT_TYPE_PROFESSOR;
                consults = findConsultListByRequestMpiAndRequestMode(requestMpi,startPage,rm);
            }else{
                throw  new DAOException(DAOException.VALUE_NEEDED,"requestMode 不符合格式");
            }
        }
        List<Object> list = new ArrayList<>();
        DateConversion d = new DateConversion();
        if (consults == null) {
            return list;
        }
        for (Consult c : consults) {
            Map<String, Object> m = new HashMap<String, Object>();
            if (c.getRequestTime() == null) {
                c.setTime(null);
            } else {
                c.setTime(d.convertRequestDateForBuss(c.getRequestTime()));
            }
            //设置问卷信息
            setConsultQuestionnaire(c);
            Doctor doctor = dao.getByDoctorId(c.getConsultDoctor());
            if (doctor == null) {
                m.put("consults", c);
                list.add(m);
                continue;
            }
            // 医生信息
            Doctor doc = new Doctor();
            doc.setPhoto(doctor.getPhoto());// 头像
            doc.setName(doctor.getName());// 姓名
            doc.setProTitle(doctor.getProTitle());// 职称
            doc.setDepartment(doctor.getDepartment());// 科室
            doc.setOrgan(doctor.getOrgan());// 机构
            doc.setGender(doctor.getGender());// 性别
            doc.setProfession(doctor.getProfession());// 专科编码
            doc.setTeams(doctor.getTeams());
            m.put("consults", c);
            m.put("doctor", doc);
            list.add(m);
        }
        return list;
    }



    /**
     * 获取咨询信息
     *
     * @param consultId
     * @param userId
     * @return
     */
    @RpcService
    public Consult getConsultInfo(Integer consultId, int userId) {
        Consult c = get(consultId);
        if (c != null) {
            // 是否评价(执行医生)
            if (c.getConsultStatus() == 2 && c.getRefuseFlag() == null && c.getExeDoctor() != null) {
                EvaluationService evaService = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
                EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
                c.setFeedBack(evaService.isEvaluation(c.getExeDoctor(), "3", String.valueOf(consultId), userId, "patient"));
                if (c.getFeedBack()) {
                    c.setFeedbackId(evaDao.
                            findEvaByServiceAndUser(c.getExeDoctor(), "3", String.valueOf(consultId), userId, "patient").get(0).getFeedbackId());
                }
            } else {
                c.setFeedBack(false);
            }
        }
        //是否包含问卷，设置问卷信息
        setConsultQuestionnaire(c);

        return c;
    }

    /**
     * 患者根据consultId获取咨询单、医生及病人信息
     *
     * @param consultId 咨询ID
     * @return map 咨询单详细信息
     * @author xiebz
     * @Date 2015-12-22 上午9:58
     * <p>
     * 将点赞替换为评价，医生信息增加评分 zhangsl
     * @Date 2016-11-15 17:23:30
     */
    @RpcService
    public Map<String, Object> getConsultAndPatientAndDoctorById(
            final Integer consultId, final int userId) {
        Consult c = get(consultId);
        Map<String, Object> map = new HashMap<>();
        if(c==null){
            throw  new DAOException(609,"consult not find");
        }
        if (c.getConsultStatus() == ConsultConstant.CONSULT_STATUS_PENDING) {
            int minusHours = DateConversion.getHoursDiffer(c.getRequestTime(), new Date(), PayConstant.ORDER_OVER_TIME_HOURS);
            c.setLeftTime( (minusHours<1?1:minusHours) + "小时");
        }
        // 是否评价(执行医生)
        if (c.getConsultStatus() == 2 && c.getRefuseFlag() == null && c.getExeDoctor() != null) {
            EvaluationService evaService = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
            EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
            c.setFeedBack(evaService.isEvaluation(c.getExeDoctor(), "3", String.valueOf(consultId), userId, "patient"));
            if (c.getFeedBack()) {
                c.setFeedbackId(evaDao.
                        findEvaByServiceAndUser(c.getExeDoctor(), "3", String.valueOf(consultId), userId, "patient").get(0).getFeedbackId());
            }
        } else {
            c.setFeedBack(false);
        }
        //是否包含问卷，设置问卷信息
        setConsultQuestionnaire(c);
        Integer time = c.getRequestMode()==1?SystemConstant.APPOINT_CONSULT_TIME:SystemConstant.ONLINE_CONSULT_TIME;
        if(c.getStatus()!=null){
            switch (c.getStatus()){
                case 0: c.setStatusDescribe("请在#"+c.getLeftTime()+"内&完成支付，超时将自动取消."); break;
                case 5: c.setStatusDescribe("已完成：咨询已完成.");break;
                case 6: c.setStatusDescribe("已完成：咨询已完成.");break;
                case 7: c.setStatusDescribe("已取消：超时未支付，咨询已自动取消.");break;
                case 8: c.setStatusDescribe("已取消：你已手动取消");break;
                case 9: c.setStatusDescribe("已取消：医生 "+String.valueOf(time)+"小时内 未回复，咨询已自动取消.");break;
                case 10: c.setStatusDescribe("已拒绝："+c.getCancelCause()+".");break;
                case 11:c.setStatusDescribe("已完成：咨询已完成.");break;
                default:
                    c.setStatusDescribe(" ");
            }
        }
        // 咨询记录
        map.put("consult", c);

        // 医生信息
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(c.getConsultDoctor());
        Doctor doc = new Doctor();
        doc.setDoctorId(doctor.getDoctorId());// 医生ID
        doc.setName(doctor.getName());// 姓名
        doc.setPhoto(doctor.getPhoto());// 头像
        doc.setProTitle(doctor.getProTitle());// 职称
        doc.setOrgan(doctor.getOrgan());// 机构
        doc.setGender(doctor.getGender());// 性别
        doc.setProfession(doctor.getProfession());// 专科
        doc.setDepartment(doctor.getDepartment());// 科室
        doc.setTeams(doctor.getTeams());//目标医生团队标记
        doc.setRating(doctor.getRating());//医生评分
        map.put("doctor", doc);

        // 病人信息
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByMpiId(c.getMpiid());
        Patient pat = new Patient();
        pat.setMpiId(patient.getMpiId());
        pat.setPatientSex(patient.getPatientSex());
        pat.setPatientName(patient.getPatientName());
        pat.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));
        pat.setIdcard(patient.getIdcard());
        pat.setMobile(patient.getMobile());
        pat.setPatientType(patient.getPatientType());

        map.put("patient", pat);

        List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(CdrOtherdocDAO.class)
                .findByClinicTypeAndClinicId(3, c.getConsultId());
        map.put("cdrOtherdocs", cdrOtherdocs);

        if (null != c && null != c.getExeDoctor() && c.getExeDoctor() > 0) {
            Doctor exe = doctorDAO.get(c.getExeDoctor());
            Doctor d = new Doctor();
            d.setDoctorId(exe.getDoctorId());
            d.setName(exe.getName());
            d.setPhoto(exe.getPhoto());
            d.setProTitle(exe.getProTitle());
            d.setOrgan(exe.getOrgan());
            d.setGender(exe.getGender());
            d.setProfession(exe.getProfession());
            d.setDepartment(exe.getDepartment());
            d.setRating(exe.getRating());//医生评分
            map.put("exeDoctor", d);
        }

        return map;
    }

    /**
     * 判断咨询是否包含问卷信息,若包含则设置问卷对象
     * @param consult
     */
    public Boolean setConsultQuestionnaire(Consult consult) {
        if(null != consult){
            if(null != consult.getQuestionnaireId()){
                QuestionnaireDAO questionnaireDAO = DAOFactory.getDAO(QuestionnaireDAO.class);
                Questionnaire questionnaire = questionnaireDAO.get(consult.getQuestionnaireId());
                if(null != questionnaire){
                    questionnaire.setQuestionDesc(questionnaire.packQuestionDesc(questionnaire));
                    consult.setQuestionnaire(questionnaire);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param @param cancelCause 拒绝原因
     * @param @param consultId 咨询单主键
     * @return void
     * @author zhongzx 修改：把原来的updateConsult替换成了updateConsultForRefuse,加了refuseFlag形参
     * @Class eh.bus.dao.ConsultDAO.java
     * @Title: refuseConsultAndbackMoney
     * @Description: 拒绝咨询服务，并将钱退还给申请方面
     * @author AngryKitty
     * @Date 2015-12-22上午10:34:31
     */
    @RpcService
    public void refuseConsultAndbackMoney(String cancelCause,
                                          Integer consultId, Integer refuseFlag) {
//        refuseConsultAndbackMoneybefore2_1_1(cancelCause,consultId, refuseFlag);//健康2.1.1版本后该方法不再被调用

        //健康2.1.1版本后
        new RefuseConsultService().refuseConsultAndbackMoney(cancelCause, consultId, refuseFlag);

    }


    /**
     * 作废,前端/定时器自动拒绝调用refuseConsultAndbackMoney,健康2.1.1版本后该方法不再被调用
     *
     * @param @param cancelCause 拒绝原因
     * @param @param consultId 咨询单主键
     * @return void
     * @author zhongzx 修改：把原来的updateConsult替换成了updateConsultForRefuse,加了refuseFlag形参
     * @Class eh.bus.dao.ConsultDAO.java
     * @Title: refuseConsultAndbackMoney
     * @Description: 拒绝咨询服务，并将钱退还给申请方面
     * @author AngryKitty
     * @Date 2015-12-22上午10:34:31
     */
    @RpcService
    public void refuseConsultAndbackMoneybefore2_1_1(String cancelCause,
                                                     Integer consultId, Integer refuseFlag) {
        if (consultId == null) {
//            logger.error("consultId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultId is required");
        }
        Consult consult = getById(consultId);
        if(consult == null){
            throw new DAOException(609,
                    "consult is not find");
        }
        if (StringUtils.isEmpty(cancelCause)) {
//            logger.error("cancelCause is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "cancelCause is required");
        }
        if(refuseFlag == null){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "refuseFlag is required");
        }
        logger.info("咨询拒绝,consultId:" + consultId + ",cancelCause:"
                + cancelCause);
        this.updateConsultForRefuse(new Date(), cancelCause, consultId, 3,
                refuseFlag);


        // 退还患者咨询费
        WxRefundExecutor executor = new WxRefundExecutor(consultId, "consult");
        executor.execute();
        // 发送系统通知消息到医患消息记录表
        String systemNotification = "咨询已被医生拒绝！";
        // desc_2016.4.1 系统自动拒绝给目标医生发系统消息  zhangjr
        if (refuseFlag != null && refuseFlag == 0) {
            systemNotification = "已过" + SystemConstant.ONLINE_CONSULT_TIME + "小时，咨询单自动拒绝";

            // desc_2016.3.8 给目标医生发送系统消息 zx
            // 增加系统提醒消息
            SessionDetailDAO sessionDetailDAO = DAOFactory
                    .getDAO(SessionDetailDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient reqPatient = patientDAO.getByMpiId(consult.getRequestMpi());
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            Doctor doc = doctorDAO.get(consult.getConsultDoctor());
            String title = "咨询自动取消提醒";
            Integer requestMode = consult.getRequestMode();

            Integer time = SystemConstant.APPOINT_CONSULT_TIME;
            if (requestMode != null && requestMode == 2) {
                time = SystemConstant.ONLINE_CONSULT_TIME;
            }

            String detailMsg = "由于您超过" + time + "小时未处理，患者" + reqPatient.getPatientName()
                    + "向您发起的咨询申请已自动取消。";

            SessionDetailService sessionDetailservice = new SessionDetailService();
            boolean teams = consult.getTeams() == null ? false : consult.getTeams();
            sessionDetailservice.addSysTextMsgConsultToTarDoc(consultId, doc.getMobile(), title, detailMsg, teams, true);
//            sessionDetailservice.addMsgDetail(consultId, 3, 1, doc.getMobile(), "text",
//                    title, detailMsg, "", teams, true);
        }
        // 发送系统通知消息到医患消息记录表
        if (ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(consult.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(consult.getRequestMode())) {
            ConsultMessageService msgService = new ConsultMessageService();
            SystemNotificationMsgBody msgObj = new SystemNotificationMsgBody();
            msgObj.setType(ConsultConstant.SYSTEM_MSG_TYPE_WITHOUT_LINK);
            msgObj.setText(systemNotification);
            msgObj.setUrl(null);
            msgService.handleSystemNotificationMessage(consultId, msgObj);
        }
    }

    /**
     * @param @return
     * @return List<Consult> 咨询单列表
     * @throws Exception
     * @Description:  查询 咨询申请时间距现在超过24小时，并且状态为未完成状态的图文咨询单;
     * 咨询预约时间距现在超过48小时，并且状态为未完成状态的电话咨询单
     * @author Zhongzx
     * @Date 2015-12-24上午10:13:26
     */
    @RpcService
    public List<Consult> findConsultTwoDayAgo() throws Exception {
        // 当前时间往前推两天
        final Date yesterday = DateConversion.getDateAftXDays(new Date(), -1);
        final Date date = DateConversion.getDateAftXDays(new Date(), -2);
        //yuanb 修改处理5天内的超时未处理订单  2017年6月30日11:17:45
        final Date latest = DateConversion.getDateAftXDays(new Date(),-5);
        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 把两天前的时间当成参数传入进去
                StringBuilder hql = new StringBuilder(
                        "from Consult where((requestTime<:yesterday and requestTime >:latest and (consultStatus = 0 or consultStatus = 1) and requestMode in (2,4,5)) or "
                                + "(appointTime<:date and appointTime >:latest and (consultStatus =0 or consultStatus = 1) and requestMode = 1)) and payflag=1");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("yesterday", yesterday);
                q.setParameter("date", date);
                q.setParameter("latest",latest);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * @param
     * @return void
     * @throws Exception
     * @Description:  处理超过48小时状态为未完成的咨询单。 若是电话查询 没有打电话或是时间少于两分钟 状态更新为拒绝；
     * 若是图文查询超过24小时，若是没有回复意见则更新为拒绝。之外更新为完成。
     * @author Zhongzx
     * @Date 2015-12-24下午3:22:42
     */
    @RpcService
    public void dealWithUnfinishedConsult() throws Exception {

        List<Consult> clist = this.findConsultTwoDayAgo();
        CallRecordDAO callDao = DAOFactory.getDAO(CallRecordDAO.class);
        PatientCancelConsultService patientCancelConsultService = AppContextHolder.getBean("patientCancelConsultService", PatientCancelConsultService.class);

        for (Consult c : clist) {
            try {
                // 图文咨询
                if (ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(c.getRequestMode()) || ConsultConstant.CONSULT_TYPE_RECIPE.equals(c.getRequestMode()) || ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(c.getRequestMode())) {
                    // 没有回复意见，更新为拒绝
                    //2016-6-1 luf: if (StringUtils.isEmpty(c.getReplyOpinion())) { 更改为用hasChat判断
                    if (ValidateUtil.isNotTrue(c.getHasChat())) {
                        patientCancelConsultService.CancelConsult(c.getConsultId(), SystemConstant.CONSULT_AUTO_CANCEL_GRAPHIC,
                                ConsultConstant.CONSULT_CANCEL_ROLE_SYSTEM);
                    } else {
                        // 有回复意见，更新为已完成,结束咨询
                        this.endConsultByRole(c.getConsultId(), ConsultConstant.CONSULT_END_ROLE_SYSTEM);
                    }
                }
                // 电话咨询
                if (c.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_POHONE)) {
                    // 根据bussType 和bussId 获取通话列表最新的一条记录
                    List<CallRecord> list = callDao.findByBussIdAndBussType(
                            c.getConsultId(), 3);
                    if (null != list && list.size() > 0) {
                        this.endConsultByRole(c.getConsultId(), ConsultConstant.CONSULT_END_ROLE_SYSTEM);
                    } else {
                        patientCancelConsultService.CancelConsult(c.getConsultId(), SystemConstant.CONSULT_AUTO_CANCEL_AUDIO,
                                ConsultConstant.CONSULT_CANCEL_ROLE_SYSTEM);
                    }
                }
            } catch (Exception e) {
                logger.error("dealWithUnfinishedConsult error, c[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(c), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            }
        }

    }

    /**
     * @param @return
     * @return List<Consult> 图文咨询,专家解读，在线续方单列表
     * @throws Exception
     * @Description:  查询 医生有回复但未完成咨询的情况下，当图文咨询,专家解读，在线续方还有2小时结束的咨询单;
     * @author zhaotm
     * @Date 2016-05-25上午11:03:26
     */
    @RpcService
    public List<Consult> findGraphicConsultRemainTwoHours() throws Exception {
        // 当前时间往前推两天
        final double twentyTwoHourSeconds = 22 * 60 * 60;
        final double threeMinuteAgoSeconds = twentyTwoHourSeconds - 3 * 60;
        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 把22小时的秒数当成参数传入进去
                StringBuilder hql = new StringBuilder(
                        "FROM Consult " +
                                "WHERE TO_SECONDS(NOW())-TO_SECONDS(requestTime) < :twentyTwoHourSeconds " +
                                "AND TO_SECONDS(NOW())-TO_SECONDS(requestTime) >= :threeMinuteAgoSeconds " +
                                "AND requestMode in (2,4,5) AND hasChat = 1 AND consultStatus=1 AND remindFlag = 0 "
                );
                Query q = ss.createQuery(hql.toString());
                q.setParameter("twentyTwoHourSeconds", twentyTwoHourSeconds);
                q.setParameter("threeMinuteAgoSeconds", threeMinuteAgoSeconds);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * @param
     * @return void
     * @throws Exception
     * @Description:  医生有回复但未完成咨询的情况下，当图文咨询,专家解读，在线续方还有2小时结束时，给医生发送推送消息;
     * @author zhaotm
     * @Date 2016-05-25下午11:00:42
     */
    @RpcService
    public void sendRemindMessageToDoctorBeforeConsultDeadline() throws Exception {
        List<Consult> clist = this.findGraphicConsultRemainTwoHours();

        for (Consult c : clist) {
            //逐条发送"剩余2小时提醒"推送消息
            //this.sendRemindPushMsgToDoctor(c);
            Integer clientId = c.getDeviceId();
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2Ons(c.getConsultId(), c.getConsultOrgan(), "SendRemindPushMsgToDoc", "SendRemindPushMsgToDoc", clientId);
            //消息发送完成，更新记录remindFlag => 1
            this.updateConsultRemindFlagTrue(c.getConsultId());
        }
    }

    /**
     * 更新图文咨询提醒状态为已提醒
     *
     * @param consultID
     */
    @RpcService
    @DAOMethod(sql = "update Consult set remindFlag=1 where consultID=:consultID")
    public abstract void updateConsultRemindFlagTrue(
            @DAOParam("consultID") int consultID);

    /**
     * 更新咨询状态为“结束”
     *
     * @param endRole
     * @param consultID
     */
    @DAOMethod(sql = "update Consult set endRole=:endRole,consultStatus=2,status=:status,endDate=NOW() where consultID=:consultID")
    public abstract void updateConsultStatusEndByEndRole(
            @DAOParam("endRole") int endRole,
            @DAOParam("consultID") int consultID,
            @DAOParam("status") int status);


    /**
     * @param payflag
     * @return List<Consult>
     * @function 根据支付状态查询咨询列表
     * @author zhangjr
     * @date 2015-12-23
     */
    @RpcService
    @DAOMethod
    public abstract List<Consult> findByPayflag(int payflag);


    /**
     * 供StartConsultService.startConsult使用
     *
     * @param consultID
     * @param exeDoctor
     * @param exeDepart
     * @param exeOrgan
     * @return
     */
    public Integer updateConsultForStart(final int consultID, final int exeDoctor,
                                         final int exeDepart, final int exeOrgan) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "update Consult set startDate=:StartDate,exeDoctor=:ExeDoctor,exeDepart=:ExeDepart,exeOrgan=:ExeOrgan,consultStatus=1,status = :status where consultId=:ConsultID";

                Consult consult = get(consultID);

                if (null == consult.getConsultDoctorUrt()) {
                    DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    Doctor targetDoctor = doctorDAO.get(exeDoctor);
                    String targetMobile = targetDoctor.getMobile();
                    int targetUrtId = new UserSevice().getUrtIdByUserId(targetMobile, "doctor");
                    hql = "update Consult set startDate=:StartDate,exeDoctor=:ExeDoctor,exeDepart=:ExeDepart,exeOrgan=:ExeOrgan,consultStatus=1,status = :status,consultDoctorUrt=" + targetUrtId + " where consultId=:ConsultID";
                }

                Query q = ss.createQuery(hql);
                q.setInteger("ConsultID", consultID);
                q.setTimestamp("StartDate", new Date());
                q.setInteger("ExeDoctor", exeDoctor);
                q.setInteger("ExeDepart", exeDepart);
                q.setInteger("ExeOrgan", exeOrgan);
                if(ConsultConstant.CONSULT_TYPE_POHONE == consult.getRequestMode()){
                    q.setInteger("status", 4);
                }else{
                    q.setInteger("status", 3);
                }

                Integer num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);

        return action.getResult();
    }

    /**
     * 供RequestConsultService.requestConsultAndCdrOtherdoc使用
     *
     * @param tarConsult   咨询单信息
     * @param cdrOtherdocs 文档信息
     * @return
     */
    public Consult saveConsult(final Consult tarConsult, final List<Otherdoc> cdrOtherdocs) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        final Integer groupMode = doctorDAO.getGroupModeByDoctorId(tarConsult.getConsultDoctor());
        HibernateStatelessResultAction<Consult> action = new AbstractHibernateStatelessResultAction<Consult>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Date requestTime = new Date();
                tarConsult.setRequestTime(requestTime);
                tarConsult.setGroupMode(groupMode);
                appendUniqueAttr(tarConsult);
                // 保存咨询单
                logger.info("保存咨询单,consult:" + JSONUtils.toString(tarConsult));
//                Consult aconsult = saveData(tarConsult, ss);
                Consult aconsult = save(tarConsult);
                logger.info("result:[{}]", JSONObject.toJSONString(aconsult));
                // 获取咨询单号
                if(aconsult==null){
                    throw new DAOException(609,"保存失败，返回值为空");
                }
                Integer clinicId = tarConsult.getConsultId();
                // 保存图片
                if (cdrOtherdocs.size() > 0) {
                    // 先注释掉，回头整理纳里健康的时候，进行测试，再进行修改
                    DAOFactory.getDAO(CdrOtherdocDAO.class).saveOtherDocList(3,
                            clinicId, cdrOtherdocs);

                }
                if (aconsult != null) {
                    setResult(aconsult);
                } else {
                    setResult(null);
                }
            }

            private void appendUniqueAttr(Consult tarConsult) {
                StringBuffer sb = new StringBuffer();
                sb.append(tarConsult.getRequestMpi());
                sb.append(tarConsult.getMpiid());
                sb.append(tarConsult.getConsultDoctor());
                sb.append(tarConsult.getRequestMode());
                long currentSeond = tarConsult.getRequestTime() == null ? System.currentTimeMillis() / 1000 : tarConsult.getRequestTime().getTime() / 1000;
                long ten = currentSeond / 5;
                int step = (currentSeond % 5) >= 3 ? 5 : 0;
                sb.append(ten);
                sb.append(step);
                tarConsult.setUniqueAttr(sb.toString());
            }

            private Consult saveData(Consult consult, StatelessSession ss){
                String insertModel = "INSERT INTO `bus_consult` ( {} ) SELECT {}  FROM dual  WHERE NOT EXISTS (SELECT consultId FROM bus_consult WHERE 1=1 {}) ";
                StringBuffer columns = new StringBuffer();
                StringBuffer values = new StringBuffer();
                String orderByClause = " ORDER BY consultId desc ";
                String limitClause = " LIMIT {}, {}";
                int start =0, limit=1;
                String conditions = " AND requestMpi = '{}' " +
                        "AND consultDoctor = {} " +
                        "AND requestMode='{}' " +
                        "AND leaveMess='{}' " +
                        "AND consultStatus='{}'";
                Map<String, Object> kvMap = null;
                try {
                    kvMap = map(consult);
                } catch (Exception e) {
                    logger.error("map error, errorMessage[{}]", e.getMessage());
                }
                if(kvMap == null){
                    throw new DAOException(609,"保存失败");
                }
                Iterator<Map.Entry<String, Object>> it = kvMap.entrySet().iterator();
                String separatorChar = ", ";
                while(it.hasNext()){
                    Map.Entry<String, Object> kv = it.next();
                    if(kv.getKey()!=null && kv.getValue()!=null &&
                            (isPrimitiveType(kv.getValue().getClass()) || String.class.isInstance(kv.getValue()))){
                        columns.append(separatorChar);
                        columns.append(kv.getKey());
                        values.append(separatorChar);
                        values.append("'" + kv.getValue().toString() + "'");
                    }else if(kv.getValue()!=null && Date.class.isInstance(kv.getValue())){
                        String date = DateConversion.getDateFormatter((Date)kv.getValue(), "yyyy-MM-dd HH:mm:ss");
                        columns.append(separatorChar);
                        columns.append(kv.getKey());
                        values.append(separatorChar);
                        values.append("'" + date + "'");
                    }
                }
                String whereClause = LocalStringUtil.format(conditions, consult.getRequestMpi(), consult.getConsultDoctor(), consult.getRequestMode(), consult.getLeaveMess(), consult.getConsultStatus());
                String insertSql = LocalStringUtil.format(insertModel, columns.toString().substring(columns.indexOf(separatorChar)+1), values.toString().substring(columns.indexOf(separatorChar)+1), whereClause);
                String querySql = "FROM Consult WHERE 1=1 " + whereClause + orderByClause;
                Query insertResult = ss.createSQLQuery(insertSql).addEntity(Consult.class);
//                insertResult.setEntity()
                int updateRows = insertResult.executeUpdate();
                logger.info("update rows[{}]", updateRows);
                Query query = ss.createQuery(querySql);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                return (Consult)query.uniqueResult();
            }

            private Map<String, Object> map(Consult consult) throws InvocationTargetException, IllegalAccessException {
                Class clazz = consult.getClass();
                Method[] methods = clazz.getDeclaredMethods();
                Map<String, Object> map = Maps.newHashMap();
                for(Method m : methods){
                    if(m.isAnnotationPresent(Column.class)){
                        Object value = m.invoke(consult, null);
                        Column column = m.getAnnotation(Column.class);
                        if(Boolean.class.isInstance(value)){
                            value = ((boolean)value)?1:0;
                        }
                        map.put(column.name(), value);
                    }
                }
                return map;
            }

            private boolean isPrimitiveType(Class clazz){
                Set<Class<?>> set = new HashSet<>();
                set.add(Byte.class);
                set.add(Short.class);
                set.add(Integer.class);
                set.add(Long.class);
                set.add(Float.class);
                set.add(Double.class);
                set.add(Character.class);
                set.add(Boolean.class);
                set.add(Void.class);
                return set.contains(clazz);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }

    private void isValidRequestConsultAndCdrOtherdocData(Consult consult) {
        if (StringUtils.isEmpty(consult.getMpiid())) {
//            logger.error("mpiid is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (consult.getConsultDoctor() == null) {
//            logger.error("consultDoctor is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultDoctor is required");
        }
        if (consult.getConsultOrgan() == null) {
//            logger.error("consultOrgan is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultOrgan is required");
        }
        if (consult.getConsultDepart() == null) {
//            logger.error("consultDepart is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultDepart is required");
        }
    }


    /**
     * 最近咨询有效时间段
     *
     * @param doctorId 医生内码
     * @return Map<String, Object>
     * @author luf
     */
    @RpcService
    public Map<String, Object> getEffConsultTimeLast(Integer doctorId) {
        Date consultDate = new Date();
        Date consultDate2 = DateConversion.getFormatDate(consultDate,
                "yyyy-MM-dd");
        Map<String, Object> result = new HashMap<String, Object>();
        List<Object[]> os = new ArrayList<Object[]>();
        Date lastDate = null;
        String week = "";
        boolean before = false;
        boolean after = false;
        // 如果当天有咨询有效时间段
        os = this.getEffConsultTime(consultDate, doctorId);
        if (os != null && os.size() > 0) {
            week = DateConversion.getWeekOfDate((Date) os.get(0)[0]);
            week = week.replace("星期", "周");
            List<Object[]> obs = this.getEffConsultTime(
                    DateConversion.getDateAftXDays(consultDate2, 1), doctorId);
            if (obs != null && obs.size() > 0) {
                after = true;
            }
            result.put("os", os);
            result.put("week", week);
            result.put("before", before);
            result.put("after", after);
            return result;
        }
        // 如果当天没有咨询有效时间段，则去除时分秒进行循环查询最近咨询有效时间段
        for (int i = 1; i < 7; i++) {
            os = this.getEffConsultTime(
                    DateConversion.getDateAftXDays(consultDate2, i), doctorId);
            if (os != null && os.size() > 0) {
                lastDate = DateConversion.getFormatDate((Date) os.get(0)[0],
                        "yyyy-MM-dd");
                break;
            }
        }
        if (lastDate != null) {
            week = DateConversion.getWeekOfDate(lastDate);
            week = week.replace("星期", "周");
            List<Object[]> obs = this.getEffConsultTime(
                    DateConversion.getDateAftXDays(lastDate, 1), doctorId);
            if (obs != null && obs.size() > 0) {
                after = true;
            }
        }
        result.put("os", os);
        result.put("week", week);
        result.put("before", before);
        result.put("after", after);
        return result;
    }

    /**
     * 最近咨询有效时间段--翻页
     * <p>
     * eh.bus.dao
     *
     * @param consultDate 资询时间
     * @param doctorId    医生内码
     * @param page        翻页 1下一天，-1上一天
     * @return Map<String,Object>
     * @author luf 2016-2-18
     */
    @RpcService
    public Map<String, Object> getEffConsultTimeNew(Date consultDate,
                                                    Integer doctorId, int page) {
        consultDate = DateConversion.getDateAftXDays(consultDate, page);
        Date consultDate2 = DateConversion.getFormatDate(consultDate,
                "yyyy-MM-dd");
        Date now = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
        if (consultDate2.compareTo(now) < 0) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consultDate is required!");
        }
        consultDate = consultDate2;
        if (consultDate2.compareTo(now) == 0) {
            consultDate = new Date();
        }
        Map<String, Object> result = new HashMap<String, Object>();
        List<Object[]> os = this.getEffConsultTime(consultDate, doctorId);
        String week = "";
        boolean before = false;
        boolean after = false;
        if (os != null && os.size() > 0) {
            week = DateConversion.getWeekOfDate(consultDate2);
            week = week.replace("星期", "周");

            if (consultDate2.compareTo(now) > 0) {
                Date cDate = consultDate2;
                if (consultDate2.compareTo(DateConversion.getDateAftXDays(now,
                        1)) == 0) {
                    String hms = DateConversion.getDateFormatter(new Date(),
                            "HH:mm:ss");
                    String ymd = DateConversion.getDateFormatter(consultDate2,
                            "yyyy-MM-dd");
                    cDate = DateConversion.getCurrentDate(ymd + " " + hms,
                            "yyyy-MM-dd HH:mm:ss");
                }
                List<Object[]> bo = this.getEffConsultTime(
                        DateConversion.getDateAftXDays(cDate, -1), doctorId);
                if (bo != null && bo.size() > 0) {
                    before = true;
                }
            }
            List<Object[]> ao = this.getEffConsultTime(
                    DateConversion.getDateAftXDays(consultDate2, 1), doctorId);
            if (ao != null && ao.size() > 0) {
                after = true;
            }
        }
        result.put("os", os);
        result.put("week", week);
        result.put("before", before);
        result.put("after", after);
        return result;
    }

    /**
     * 咨询有效时间段查询（添加最大预约天数）
     * <p>
     * eh.bus.dao
     *
     * @param consultDate 当前日期
     * @param doctorId    医生内码
     * @param page        翻页 0当天，1下一天，-1上一天
     * @return Map<String,Object>
     * @author luf 2016-3-7
     */
    @RpcService
    public Map<String, Object> getLastEffConsultTime(Date consultDate,
                                                     int doctorId, int page) {
        //根据前端传入的时间，及翻页标记，算出要查询的咨询时间段
        consultDate = DateConversion.getDateAftXDays(consultDate, page);
        consultDate = DateConversion.getFormatDate(consultDate, "yyyy-MM-dd");

        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet cs = csDao.get(doctorId);
        if (cs == null || cs.getAppointDays() == null) {
            return new HashMap<String, Object>();
        }
        //获取最大预约天数
        int MaxDays = cs.getAppointDays();

        //获取当前时间下，最多可预约的咨询时间，即maxDays后的时间
        Date lastDate = DateConversion.getDaysAgo(-MaxDays);
        lastDate = DateConversion.getFormatDate(lastDate, "yyyy-MM-dd");
        Date now = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
        if (!consultDate.before(lastDate) || consultDate.before(now)) {
            return new HashMap<String, Object>();
        }

        Date EffDate = null;
        int days = MaxDays + 7 >= 14 ? MaxDays + 7 : 14;
        if (page >= 0) {
            //获取consultDate向后MaxDays+7天(包含consultDate)距离consultDate最近有号源的时间
            EffDate = this.getEffAfterForLastEff(consultDate, doctorId, days);
        } else {
            //获取今天至consultDate(包含consultDate)距离consultDate最近有号源的时间
            EffDate = this.getEffBeforeForLastEff(consultDate, doctorId);
        }

        //当前可约最大日期内无可约时间段，查询下一个周期内第一个有号源的日期
        if (EffDate == null) {
            return new HashMap<String, Object>();
        }

        if (EffDate.after(lastDate)) {


            HashMap<String, Object> result = new HashMap<String, Object>();
            Date canAppoint = DateConversion.getDateAftXDays(EffDate, -MaxDays);

            result.put("canAppointDate", DateConversion.getDateFormatter(canAppoint, "MM月dd日"));
            result.put("canAppointWeek", DateConversion.getWeekOfDate(canAppoint));

            return result;
        }

        HashMap<String, Object> result = new HashMap<String, Object>();
        List<Object[]> os = this.getEffConsultTime(EffDate, doctorId);
        String week = DateConversion.getWeekOfDate(EffDate);
        week = week.replace("星期", "周");
        result.put("os", os);
        result.put("week", week);
        boolean before = false;
        Date beDate = this.getEffBeforeForLastEff(
                DateConversion.getDateAftXDays(EffDate, -1), doctorId);
        if (beDate != null && !beDate.before(now)) {
            before = true;
        }
        boolean after = false;
        Date afDate = this.getEffAfterForLastEff(
                DateConversion.getDateAftXDays(EffDate, 1), doctorId, days);
        if (afDate != null && afDate.before(lastDate)) {
            after = true;
        }
        result.put("before", before);
        result.put("after", after);
        return result;
    }

    /**
     * 向后获取最近咨询日期
     * <p>
     * eh.bus.dao
     *
     * @param paramDate 入参日期
     * @param doctorId  医生内码
     * @return Date
     * @author luf 2016-3-7
     */
    public Date getEffAfterForLastEff(Date paramDate, int doctorId, int days) {
        Date now = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
        List<Object[]> os = new ArrayList<Object[]>();
        if (paramDate.equals(now)) {
            os = this.getEffConsultTime(new Date(), doctorId);
        } else {
            os = this.getEffConsultTime(paramDate, doctorId);
        }
        if (os != null && os.size() > 0) {
            return paramDate;
        }
        Date lastCD = null;


        for (int i = 1; i < days; i++) {
            paramDate = DateConversion.getDateAftXDays(paramDate, 1);
            os = this.getEffConsultTime(paramDate, doctorId);
            if (os != null && os.size() > 0) {
                lastCD = paramDate;
                break;
            }
        }
        return lastCD;
    }

    /**
     * 向前获取最近咨询日期
     * <p>
     * eh.bus.dao
     *
     * @param paramDate 入参日期
     * @param doctorId  医生内码
     * @return Date
     * @author luf 2016-3-7
     */
    public Date getEffBeforeForLastEff(Date paramDate, int doctorId) {
        Date now = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
        List<Object[]> os = new ArrayList<Object[]>();
        if (paramDate.equals(now)) {
            os = this.getEffConsultTime(new Date(), doctorId);
        } else {
            os = this.getEffConsultTime(paramDate, doctorId);
        }
        if (os != null && os.size() > 0) {
            return paramDate;
        }
        Date lastCD = null;
        for (int i = 1; i < 7; i++) {
            paramDate = DateConversion.getDateAftXDays(paramDate, -1);
            if (paramDate.equals(now)) {
                os = this.getEffConsultTime(new Date(), doctorId);
            } else {
                os = this.getEffConsultTime(paramDate, doctorId);
            }
            if (os != null && os.size() > 0) {
                lastCD = paramDate;
                break;
            }
        }
        return lastCD;
    }

    /**
     * 咨询单详情-原生
     * <p>
     * eh.bus.dao
     *
     * @param consultId 咨询单号
     * @return Map<String,Object>
     * @author luf 2016-3-12
     */
    @RpcService
    public Map<String, Object> getConsultDetailById(Integer consultId) {
        Map<String, Object> map = new HashMap<String, Object>();
        Consult c = this.get(consultId);
        if (c == null) {
            return map;
        }
        int mode = c.getRequestMode();
        Date nowDate = new Date();
        Date cDate = new Date();
        int time = 0;
        int timesum = SystemConstant.APPOINT_CONSULT_TIME;
        if (mode == CONSULT_TYPE_POHONE) { // 1是电话咨询
            if (c.getAppointEndTime() != null) {
                cDate = c.getAppointEndTime();
            } else {
                cDate = c.getAppointTime();
            }
            timesum = SystemConstant.APPOINT_CONSULT_TIME;
        }
        if (mode == CONSULT_TYPE_GRAPHIC || mode == CONSULT_TYPE_PROFESSOR|| mode == CONSULT_TYPE_RECIPE) { // 2是图文咨询
            cDate = c.getRequestTime();
            timesum = SystemConstant.ONLINE_CONSULT_TIME;
        }

        //图文咨询会话最新的咨询单ID
        Integer newestConsultId = getNewestConsultId(c);

        if (cDate != null) {
            long times = ((nowDate.getTime() - cDate.getTime()) / (60 * 1000));
            time = (int) times / 60;
            if (times % 60 < 0) {
                time = time - 1;
            }
            c.setCountDown(timesum - time);
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer conDocId = c.getConsultDoctor();
        c.setTeams((doctorDAO.getByDoctorId(conDocId)).getTeams());
        RelationDoctorDAO relationDoctorDAO = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        RelationPatientDAO relationPatientDAO = DAOFactory
                .getDAO(RelationPatientDAO.class);

        String mpiId = c.getMpiid();
        Integer doctor = c.getConsultDoctor();
        PatientDAO pDao = DAOFactory.getDAO(PatientDAO.class);
        Patient p = pDao.get(mpiId);
        p.setSignFlag(relationDoctorDAO.getSignFlag(mpiId, doctor));
        p.setRelationFlag(relationPatientDAO.isRelationPatient(mpiId,doctor));

        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctor);
        if (rd != null) {
            p.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        }

        //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
        //解决方案，idcard字段赋值为空字符串
        if (StringUtils.isEmpty(p.getIdcard())) {
            p.setIdcard("");
        }

        Patient requestPat = pDao.get(c.getRequestMpi());
        Patient reqPat = new Patient();
        reqPat.setPatientName(requestPat.getPatientName());

        /*评价体系中医生端咨询单页不显示评价信息
        PatientFeedbackDAO pfDao = DAOFactory.getDAO(PatientFeedbackDAO.class);
        PatientFeedback pf = pfDao.getByDoctorService(doctor, "3",
                consultId.toString(), "patient");
        if (pf == null) {
            c.setFeedBack(false);
        } else {
            c.setFeedBack(true);
        }*/

        // 获取其他资料列表
        Integer clinicId = c.getConsultId();
        // 先注释掉，回头整理纳里健康的时候，进行测试，再进行修改
        List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(CdrOtherdocDAO.class)
                .findByClinicTypeAndClinicId(3, clinicId);

        if (c.getGroupMode() != null && c.getGroupMode().equals(1)) {
            Integer exeId = c.getExeDoctor();
            if (exeId != null && exeId > 0) {
                Doctor exe = doctorDAO.getByDoctorId(exeId);
                map.put("exeDoctor", exe);
                map.put("groupName", doctorDAO.getNameById(conDocId));
                Integer photo = doctorDAO.getPhotoByDoctorId(conDocId);
                if(ValidateUtil.notNullAndZeroInteger(photo)){
                    map.put("groupPhoto",photo);
                }
            }
        }
        //若咨询单含有问卷信息，则取出问卷对象，存入consult中
        Boolean b = setConsultQuestionnaire(c);
        if(b){
            c.setLeaveMess(c.getQuestionnaire().getQuestionDesc()+"\n"+c.getLeaveMess());
        }
        //微信3.1 新增判断团队咨询  当前登录医生是否参与过回复
        if(ValidateUtil.isTrue(c.getTeams())){
            UserRoleToken urt = UserRoleToken.getCurrent();
            Doctor doc = (Doctor) urt.getProperty("doctor");
            Integer sendId=null;
            if (doc != null) {
                sendId = doc.getDoctorId();
                List<BusConsultMsg> msgs = Lists.newArrayList();
                msgs = findMsgsByConsultIdAndExeDoctor(consultId,sendId);
                if(ValidateUtil.notBlankList(msgs)){
                    c.setDocHasChat(true);
                }else{
                    c.setDocHasChat(false);
                }
            }else{
                c.setDocHasChat(false);
            }
        }else{
            c.setDocHasChat(false);
        }
        SubConsult subConsult = BeanUtils.map(c, SubConsult.class);
        subConsult.setStatusText((subConsult.getStatus()==5||subConsult.getStatus()==6)?"已完成":subConsult.getStatusText());
        map.put("consult", subConsult);
        map.put("patient", p);
        map.put("requestPatient", reqPat);
        map.put("cdrOtherdocs", cdrOtherdocs);
        if (null != newestConsultId) {
            map.put("newestConsultId", newestConsultId);
        }
        return map;
    }

    /**
     * 患者发起电话/图文咨询成功后给目标医生发送短信
     *
     * @param consultId
     */
    public void sendSmsForRequestConsult(Integer consultId) {
        SmsInfo info = new SmsInfo();
        info.setBusId(consultId);
        info.setBusType("consult");
        info.setSmsType("consultToTarDoc");
        info.setStatus(0);
        info.setOrganId(0);// 短信服务对应的机构， 0代表通用机构
        AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
        exe.execute();

    }


    /**
     * 能否进行群组聊天
     *
     * @param consultId 会诊单号
     * @return Boolean
     * @author luf
     */
    @RpcService
    public Boolean groupEnable(int consultId) {
        logger.info("groupEnable consultId=" + consultId);
        Consult consult = this.get(consultId);
        if (consult == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "consult is required!");
        }

        Integer newConsultId = getNewestConsultId(consult);
        if (null != newConsultId) {
            Consult newConsult = this.get(newConsultId);
            if (null != newConsult && newConsult.getConsultStatus() == 1 && consult.getSessionID().equals(newConsult.getSessionID())) {
                return true;
            }
        }

        return false;

        /*Date last = DateConversion.getDateAftXDays(consult.getRequestTime(), 2);
        if (consult.getConsultStatus() != null && consult.getConsultStatus() < 2 && !last.before(new Date())) {
            return true;
        } else {
            return false;
        }*/
    }

    /**
     * @param appId
     * @param openId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "from Consult where requestMode=2 and payflag=1 and consultStatus<2 and appId=:appId and openId=:openId and requestMpi=:requestMpi")
    public abstract Consult getConsultByAppIdAndOpenId(@DAOParam("appId") String appId, @DAOParam("openId") String openId, @DAOParam("requestMpi") String requestMpi);


    @RpcService
    public HashMap<String, Object> getConsultAndPatientInfo(String appId, String openId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);

        OauthMPService oauthMPService = AppContextHolder.getBean("oauthMPService", OauthMPService.class);
        OauthMP mp = oauthMPService.getByAppIdAndOpenId(appId, openId);
        if (null == mp) {
            return null;
        }
        String userId = mp.getUserId();
        if (org.apache.commons.lang3.StringUtils.isEmpty(userId)) {
            return null;
        }

        Patient reqPat = patientDAO.getByLoginId(userId);

        HashMap<String, Object> map = new HashMap<>();
        Consult consult = getConsultByAppIdAndOpenId(appId, openId, reqPat.getMpiId());
        if (consult == null) {
            return null;
        }
        Integer docId = consult.getExeDoctor();
        if (null == docId) {
            docId = consult.getConsultDoctor();
        }
        Doctor doc = docDao.get(docId);

        if (consult != null) {
            Patient patient = patientDAO.getByMpiId(consult.getRequestMpi());
            map.put("docName", doc.getName());
            map.put("consult", consult);
            map.put("avatar", patient.getPhoto());
            map.put("gender", patient.getPatientSex());
            map.put("name", patient.getPatientName());
            return map;
        }
        return null;
    }

    public List<Consult> findConsultsNeedRemind() {
        HibernateStatelessResultAction<List<Consult>> action = new AbstractHibernateStatelessResultAction<List<Consult>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "SELECT c FROM Consult c,ConsultSet cs where cs.remindInTen=1 and cs.doctorId=c.consultDoctor " +
                        "and c.requestMode=1 and c.appointTime<=:tenLater and c.appointTime>now() and c.remindFlag=0 and c.payflag=1 and c.consultStatus<=1";
                Query q = statelessSession.createQuery(hql);
                Date tenLater = DateConversion.getDateAftXMinutes(new Date(), 10);
                q.setParameter("tenLater", tenLater);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "select hasChat from Consult where consultId=:consultId")
    public abstract boolean getHasChat(@DAOParam("consultId") int consultId);

    @RpcService
    public Boolean getHasChatByConsultId(Integer consultId) {
        logger.info("getHasChatByConsultId consultId:" + consultId);
        Assert.notNull(consultId, "getHasChatByConsultId consultId is null");

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDao.get(consultId);
        Assert.notNull(consult, "getHasChatByConsultId consult is null! consultId:" + consultId);

        Integer newConsultId = consultDao.getNewestConsultId(consult);
        if (null != newConsultId) {
            logger.info("getHasChatByConsultId consultId:" + newConsultId);
            return getHasChat(newConsultId);
        }

        return false;
    }

    @RpcService
    public int getHasChatByConsultIdExt(Integer consultId) {
        logger.info("getHasChatByConsultId consultId:" + consultId);
        Assert.notNull(consultId, "getHasChatByConsultId consultId is null");

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDao.get(consultId);
        Assert.notNull(consult, "getHasChatByConsultId consult is null! consultId:" + consultId);

        Integer newConsultId = consultDao.getNewestConsultId(consult);
        if (null != newConsultId) {
            logger.info("getHasChatByConsultId consultId:" + newConsultId);
            return (true == getHasChat(newConsultId)) ? 1 : 0;
        } else {
            //如果是当前的结束咨询单，则返回2，表示该咨询单已结束
            if (2 == consult.getConsultStatus()) {
                return 2;
            }
        }

        return 0;
    }

    /**
     * 根据咨询单获取sessionId
     *
     * @param consultId
     * @return
     * @author luf
     * @date 2016-6-30
     */
    @RpcService
    @DAOMethod(sql = "select sessionID from Consult where consultId=:consultId")
    public abstract String getSessionIDByConsultId(@DAOParam("consultId") int consultId);

    /**
     * 仅供咨询失败更新状态为取消使用
     * PatientCancelConsultService.cancelAppointForPayFail
     *
     * @param consultStatus
     * @param consultId
     */
    @DAOMethod
    public abstract void updateConsultStatusByConsultId(int consultStatus, int consultId);

    @DAOMethod(sql = "select consultId from Consult  where payflag=1 and consultStatus in (0,1,4) and (consultDoctor=:DoctorID or exeDoctor=:DoctorID) and requestMpi=:requestMpi AND requestMode = :requestMode ORDER BY consultStatus desc")
    public abstract List<Integer> findApplyingConsultByRequestMpiAndDoctorId(
            @DAOParam("requestMpi") String requestMpi, @DAOParam("DoctorID") Integer DoctorID, @DAOParam("requestMode") Integer requestMode);


    @DAOMethod(sql = "select consultId from Consult  where payflag=1 and consultStatus in (0,1,2) and (consultDoctor=:DoctorID or exeDoctor=:DoctorID) and requestMpi=:requestMpi AND requestMode = :requestMode ORDER BY consultId desc")
    public abstract List<Integer> findConsultTypeApplyingConsultByRequestMpiAndDoctorId(
            @DAOParam("requestMpi") String requestMpi, @DAOParam("DoctorID") Integer DoctorID, @DAOParam("requestMode") Integer requestMode);

    /**
     * 获取咨询会话最新的咨询单ID,电话咨询返回值为null
     *
     * @param consult
     * @return
     */
    @RpcService
    public Integer getNewestConsultId(Consult consult) {
        Integer newestConsultId = null;
        if (consult != null && (ConsultConstant.CONSULT_TYPE_GRAPHIC == consult.getRequestMode()
                || ConsultConstant.CONSULT_TYPE_RECIPE == consult.getRequestMode()
                || ConsultConstant.CONSULT_TYPE_PROFESSOR == consult.getRequestMode())) { // 2是图文咨询
            String reqMpi = consult.getRequestMpi();

            Integer doctorId = null;

            if (consult.getGroupMode() != null && consult.getGroupMode().equals(1)) {
                doctorId = consult.getConsultDoctor();
            } else {
                UserRoleToken urt = UserRoleToken.getCurrent();
                Doctor doc = (Doctor) urt.getProperty("doctor");
                if (doc != null) {
                    doctorId = doc.getDoctorId();
                }
            }
            logger.info("getNewestConsultId  reqMpi[" + reqMpi + "],doctorId[" + doctorId + "],originConsultId[" + consult.getConsultId() + "]");
            if (!StringUtils.isEmpty(reqMpi) && doctorId != null) {
                //通过同一个sessionId来判断该患者和该医生是否存在未完成的咨询单
                List<Integer> consultIds = this.findApplyingConsultByRequestMpiAndDoctorId(reqMpi, doctorId, consult.getRequestMode());
                if (CollectionUtils.isNotEmpty(consultIds)) {
                    newestConsultId = consultIds.get(0);
                }
            }
        }

        return newestConsultId;
    }

    @RpcService
    public Integer getConsultTypeNewestConsultId(Consult consult) {
        Integer newestConsultId = null;
        if (consult != null && (ConsultConstant.CONSULT_TYPE_GRAPHIC == consult.getRequestMode()
                || ConsultConstant.CONSULT_TYPE_RECIPE == consult.getRequestMode()
                || ConsultConstant.CONSULT_TYPE_PROFESSOR == consult.getRequestMode())) { // 2是图文咨询
            String reqMpi = consult.getRequestMpi();

            Integer doctorId = null;

            if (consult.getGroupMode() != null && consult.getGroupMode().equals(1)) {
                doctorId = consult.getConsultDoctor();
            } else {
                UserRoleToken urt = UserRoleToken.getCurrent();
                Doctor doc = (Doctor) urt.getProperty("doctor");
                if (doc != null) {
                    doctorId = doc.getDoctorId();
                }
            }
            logger.info("getNewestConsultId  reqMpi[" + reqMpi + "],doctorId[" + doctorId + "],originConsultId[" + consult.getConsultId() + "]");
            if (!StringUtils.isEmpty(reqMpi) && doctorId != null) {
                //通过同一个sessionId来判断该患者和该医生是否存在未完成的咨询单
                List<Integer> consultIds = this.findConsultTypeApplyingConsultByRequestMpiAndDoctorId(reqMpi, doctorId, consult.getRequestMode());
                if (CollectionUtils.isNotEmpty(consultIds)) {
                    newestConsultId = consultIds.get(0);
                }
            }
        }

        return newestConsultId;
    }


    /**
     * 按咨询结束时间查询状态为完成状态的咨询单id
     * @param sevenDate
     * @param startDate
     * @return List<String> 咨询单id列表
     * @author houxr
     * @Date 2016-11-16 上午10:13:26
     * @author zhangsl
     * @Date 2017-02-15 15:10:33
     * 设置查询区间
     */
    @DAOMethod(sql = "select CAST(c.consultId AS string) from Consult c, Doctor d where c.consultDoctor = d.doctorId and d.teams = 0 and c.endDate>:startDate and c.endDate<:sevenDate and c.consultStatus = 2 and c.refuseFlag is null and c.endDate is not null and c.exeDoctor is not null order by c.endDate desc ")
    abstract public List<String> findConsultIdSevenDayAgo(@DAOParam("sevenDate") Date sevenDate,
                                                          @DAOParam("startDate") Date startDate);

    /**
     * 获取医生咨询时间
     *
     * @param consultDate
     * @param doctorId
     * @param page
     * @param limit
     * @return
     */
    @RpcService
    public List<Map<String, Object>> getConsultDateTime(Date consultDate,
                                                        int doctorId, int page, int limit) {
        //根据前端传入的时间，及翻页标记，算出要查询的咨询时间段
        Date dt = DateConversion.getDateAftXDays(consultDate, page);
        dt = DateConversion.getFormatDate(dt, "yyyy-MM-dd");
        Date now = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
        if (dt.before(now)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "consultDate is before now");
        }

        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet cs = csDao.get(doctorId);
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        if (cs == null) {
            return results;
        }

        HashMap<Integer, HashMap<String, Object>> map = this.getDoctorConsultDate(cs);
        if (map == null || map.isEmpty()) {
            return results;
        }
        while (results.size() < limit) {
            int week = DateConversion.getWeekOfDateInt(dt);
            HashMap<String, Object> timeSlot = map.get(week);
            if (timeSlot != null && !timeSlot.isEmpty()) {
                if (dt.equals(now)) {
                    Date end = (Date) timeSlot.get("endTime");
                    if (!end.after(DateConversion.getFormatDate(new Date(), "HH:mm:ss"))) {
                        if (page >= 0) {
                            page++;
                        } else {
                            page--;
                        }
                        dt = DateConversion.getDateAftXDays(consultDate, page);
                        dt = DateConversion.getFormatDate(dt, "yyyy-MM-dd");
                        continue;
                    }
                }
                HashMap<String, Object> result = new HashMap<String, Object>();
                result.put("startTime", timeSlot.get("startTime"));
                result.put("endTime", timeSlot.get("endTime"));
                result.put("consultDate", dt);
                results.add(result);
            }
            if (page >= 0) {
                page++;
            } else {
                page--;
            }
            dt = DateConversion.getDateAftXDays(consultDate, page);
            dt = DateConversion.getFormatDate(dt, "yyyy-MM-dd");
            if (dt.before(now)) {
                break;
            }
        }
        //咨询时间由远及近排序
        Collections.sort(results, new Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                Date consultDate1 = (Date) o1.get("consultDate");
                Date consultDate2 = (Date) o2.get("consultDate");
                return consultDate1.compareTo(consultDate2);
            }
        });
        return results;
    }

    /**
     * 获取医生咨询时间段
     *
     * @param cs
     * @return
     */
    private HashMap<Integer, HashMap<String, Object>> getDoctorConsultDate(ConsultSet cs) {
        HashMap<Integer, HashMap<String, Object>> map = new HashMap<Integer, HashMap<String, Object>>();
        if (cs.getStartTime1() != null && cs.getEndTime1() != null) {
            HashMap<String, Object> timeSlot = new HashMap<String, Object>();
            timeSlot.put("startTime", cs.getStartTime1());
            timeSlot.put("endTime", cs.getEndTime1());
            map.put(1, timeSlot);
        }
        if (cs.getStartTime2() != null && cs.getEndTime2() != null) {
            HashMap<String, Object> timeSlot = new HashMap<String, Object>();
            timeSlot.put("startTime", cs.getStartTime2());
            timeSlot.put("endTime", cs.getEndTime2());
            map.put(2, timeSlot);
        }
        if (cs.getStartTime3() != null && cs.getEndTime3() != null) {
            HashMap<String, Object> timeSlot = new HashMap<String, Object>();
            timeSlot.put("startTime", cs.getStartTime3());
            timeSlot.put("endTime", cs.getEndTime3());
            map.put(3, timeSlot);
        }
        if (cs.getStartTime4() != null && cs.getEndTime4() != null) {
            HashMap<String, Object> timeSlot = new HashMap<String, Object>();
            timeSlot.put("startTime", cs.getStartTime4());
            timeSlot.put("endTime", cs.getEndTime4());
            map.put(4, timeSlot);
        }
        if (cs.getStartTime5() != null && cs.getEndTime5() != null) {
            HashMap<String, Object> timeSlot = new HashMap<String, Object>();
            timeSlot.put("startTime", cs.getStartTime5());
            timeSlot.put("endTime", cs.getEndTime5());
            map.put(5, timeSlot);
        }
        if (cs.getStartTime6() != null && cs.getEndTime6() != null) {
            HashMap<String, Object> timeSlot = new HashMap<String, Object>();
            timeSlot.put("startTime", cs.getStartTime6());
            timeSlot.put("endTime", cs.getEndTime6());
            map.put(6, timeSlot);
        }
        if (cs.getStartTime7() != null && cs.getEndTime7() != null) {
            HashMap<String, Object> timeSlot = new HashMap<String, Object>();
            timeSlot.put("startTime", cs.getStartTime7());
            timeSlot.put("endTime", cs.getEndTime7());
            map.put(7, timeSlot);
        }
        return map;
    }

    public void cancelOverTimeNoPayOrder(Date startTime, Date deadTime) {
        List<Consult> consultList = findTimeOverNoPayOrder(startTime, deadTime);
        if (ValidateUtil.notBlankList(consultList)) {
            for (Consult consult : consultList) {
                try {
                    consult.setCancelCause(PayConstant.OVER_TIME_AUTO_CANCEL_TEXT);
                    consult.setConsultStatus(ConsultConstant.CONSULT_STATUS_CANCEL);
                    consult.setCancelTime(new Date());
                    consult.setStatus(7);
                    consult.setCancelRole(3);
                    update(consult);
                    if (ValidateUtil.notNullAndZeroInteger(consult.getCouponId()) && consult.getCouponId() != -1) {
                        CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                        couponService.unlockCouponById(consult.getCouponId());
                    }
                    SmsPushService smsPushService = AppContextHolder.getBean("smsPushService", SmsPushService.class);
                    smsPushService.pushMsgData2Ons(consult.getConsultId(), consult.getConsultOrgan(), MessagePushExecutorConstant.CONSULT_NO_PAY_OVER_TIME_CANCEL, MessagePushExecutorConstant.CONSULT_NO_PAY_OVER_TIME_CANCEL, consult.getDeviceId());
                } catch (Exception e) {
                    logger.error("cancelOverTimeNoPayOrder error, busId[{}], errorMessage[{}], stackTrace[{}]", consult.getConsultId(), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                }
            }
        }
    }

    @DAOMethod(sql = "from Consult where consultStatus=4 AND payFlag = 0 AND requestTime > :startTime AND requestTime < :deadTime")
    public abstract List<Consult> findTimeOverNoPayOrder(@DAOParam("startTime") Date startTime, @DAOParam("deadTime") Date deadTime);

    @RpcService
    public void updateNewSessionToOld() {
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "SELECT requestMpi,exeDoctor FROM Consult WHERE sessionID is NOT NULL AND sessionID<>'' AND exeDoctor is NOT NULL AND exeDoctor<>'' GROUP BY requestMpi,exeDoctor HAVING COUNT(*)>1 ORDER BY COUNT(*) desc";
                Query query = statelessSession.createQuery(hql);
                List<Object[]> list = query.list();

                for (Object[] os : list) {
                    String requestMpi = (String) os[0];
                    Integer exeDoctor = (Integer) os[1];

                    String h = "select consultId from Consult where requestMpi=:requestMpi and exeDoctor=:exeDoctor and requestMode=2 and sessionID is NOT NULL AND sessionID<>'' order by requestTime asc";
                    Query q = statelessSession.createQuery(h);
                    q.setParameter("requestMpi", requestMpi);
                    q.setParameter("exeDoctor", exeDoctor);
                    List<Integer> consultIds = q.list();

                    if (consultIds == null || consultIds.isEmpty()) {
                        logger.error("requestMpi===" + requestMpi + "===exeDoctor===" + exeDoctor + "===");
                        continue;
                    }

                    String sessionID = getById(consultIds.get(0)).getSessionID();

                    String hup = "update Consult set sessionID=:sessionID where consultId in(:consultIds) and sessionID<>:sessionID";
                    Query qup = statelessSession.createQuery(hup);
                    qup.setParameter("sessionID", sessionID);
                    qup.setParameterList("consultIds", consultIds);
                    logger.info(sessionID + "  update num   " + qup.executeUpdate());
                }
                return;
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        action.getResult();
    }


    /**
     * 查询一个日间手术团队所在的所有的群组
     * @param consultDoctor
     * @return
     */
    public List<String> findAllDaytimeTeamConsultSessionID(final Integer consultDoctor) {
        HibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            public void execute(StatelessSession ss) throws Exception {
                String hql = "SELECT sessionID FROM Consult WHERE consultDoctor=:consultDoctor AND groupMode=1 GROUP BY sessionID";
                Query query = ss.createQuery(hql);
                query.setParameter("consultDoctor", consultDoctor);
                List<String> oList = query.list();
                setResult(oList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 微信3.1新增判断团队咨询单判断当前登录医生参与过回复
     * @param consultId 咨询单id
     * @param exeDoctorId 当前登录医生
     * @return
     */
    @RpcService
    @DAOMethod(sql="from BusConsultMsg where consultId =:consultId and exeDoctorId = :exeDoctorId")
    public abstract List<BusConsultMsg> findMsgsByConsultIdAndExeDoctor(@DAOParam("consultId") Integer consultId,@DAOParam("exeDoctorId") Integer exeDoctorId);


    /**
     * yuanb 2017年6月30日10:08:36
     * 发送CMD消息，用来同步咨询状态
     * @param consult
     * 加RpcService 只用于测试
     */
    @RpcService
    public void sendCMDMsgToRefreshConsult (Consult consult){
        String  sessionId = consult.getSessionID();
        Integer consultStatus = consult.getConsultStatus();
        Integer consultId = consult.getConsultId();
        if(sessionId==null||sessionId.equals("")){
            logger.error("sessionId is null,CMD消息发送失败,consultId[{}]",consult.getConsultId());
            return;
        }
        List<String> groupIds = Lists.newArrayList();
        groupIds.add(sessionId);
        String action = "Consult_State_Synchronization";
        Map<String,String> extProp = Maps.newHashMap();
        extProp.put("consultStatus",consultStatus.toString());
        extProp.put("consultId",consultId.toString());
        JsonNode response =  Easemob.sendSystemCmdMsgToChatgroups(groupIds,action,extProp);
        String result = response.get(sessionId).asText();
       if(!result.equals("success")){
           logger.error("发送CMD消息失败，发送的参数[{}]",response);
        }else{
            logger.info("发送CMD消息成功，consultId[{}]",consult.getConsultId());
        }
    }

}
