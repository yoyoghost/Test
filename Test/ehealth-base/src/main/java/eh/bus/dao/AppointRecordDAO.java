package eh.bus.dao;

import com.alibaba.fastjson.JSONObject;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.appoint.mode.AppointCancelRequestTO;
import com.ngari.his.appoint.mode.CancelRegAccountTO;
import com.ngari.his.appoint.service.IAppointHisService;
import ctd.account.session.SessionItemManager;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
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
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import eh.base.constant.AppointRoadConstant;
import eh.base.constant.ServiceType;
import eh.base.constant.SystemConstant;
import eh.base.dao.*;
import eh.base.service.organ.OrganConfigService;
import eh.base.user.UserSevice;
import eh.bus.constant.*;
import eh.bus.his.service.AppointTodayBillService;
import eh.bus.service.AppointService;
import eh.bus.service.appointrecord.RequestAppointService;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.coupon.service.CouponPushService;
import eh.coupon.service.CouponService;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.Organ;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.cdr.Otherdoc;
import eh.entity.his.AppointCancelRequest;
import eh.entity.his.CancelRegAccount;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.FollowPlan;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.msg.SmsInfo;
import eh.evaluation.constant.EvaluationConstant;
import eh.evaluation.dao.EvaluationDAO;
import eh.mpi.dao.FollowPlanDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.service.follow.FollowAddService;
import eh.mpi.service.follow.FollowPlanTriggerService;
import eh.mpi.service.follow.FollowUpdateService;
import eh.push.SmsPushService;
import eh.remote.IHisServiceInterface;
import eh.task.executor.AppointSendExecutor;
import eh.task.executor.WxRefundExecutor;
import eh.unifiedpay.service.UnifiedPayService;
import eh.unifiedpay.service.UnifiedRefundService;
import eh.util.*;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AppointRecordDAO extends
        HibernateSupportDelegateDAO<AppointRecord> {
    private static final Logger logger = LoggerFactory.getLogger(AppointRecordDAO.class);
    
    

    public AppointRecordDAO() {
        super();
        this.setEntityName(AppointRecord.class.getName());
        this.setKeyField("appointRecordId");
    }

    /**
     * 根据mpiid获取预约记录
     *
     * @param mpiid
     * @return
     * @author LF
     */
    @DAOMethod
    @RpcService
    public abstract List<AppointRecord> findByMpiid(String mpiid);

    @RpcService
    @DAOMethod(sql="from AppointRecord where appointStatus=:appointStatus and PayFlag BETWEEN 0 and 1  and appointDate>:startDate and appointDate<:endDate ORDER BY AppointDate ASC ",limit = 0)
    public abstract List<AppointRecord> findByAppointStatus(@DAOParam("appointStatus") Integer appointStatus, @DAOParam("startDate") Date startDate, @DAOParam("endDate") Date endDate);

    @RpcService
    @DAOMethod(sql="update AppointRecord set requestNum=requestNum+1 where appointRecordId =:appointRecordId")
    public abstract void updateByAppointRecordId(@DAOParam("appointRecordId") Integer appointRecordId);

    /**
     * 根据主键查询单条预约记录
     *
     * @param id
     * @return 增加医生性别返回 genderk
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @RpcService
    public AppointRecordAndPatientAndDoctor getById(final int id) {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            AppointRecordAndPatientAndDoctor result = new AppointRecordAndPatientAndDoctor();

            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndPatientAndDoctor(a,b.patientSex,b.birthday,b.patientType,c.profession,c.proTitle,c.photo,c.gender) from AppointRecord a,Patient b,Doctor c where a.appointRecordId=:appointRecordId and b.mpiId=a.mpiid and c.doctorId=a.doctorId");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("appointRecordId", id);
                result = (AppointRecordAndPatientAndDoctor) q.uniqueResult();

                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (AppointRecordAndPatientAndDoctor) action.getResult();
    }

    /**
     * 获取预约详情单
     *
     * @param id
     * @return
     * @throws ControllerException
     * @desc 按APP(原生端)指定格式输出数据给前端
     * @author zhangx
     * @date 2016-3-11 下午5:06:20
     */
    @RpcService
    public Map<String, Object> getAppointRecordInfoById(int id)
            throws ControllerException {
        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);

        Map<String, Object> map = new HashMap<>();

        AppointRecord r = get(id);
        if (r == null) {
            throw new DAOException(609, "该预约单不存在");
        }

        map.put("appointRecord", r);

        // 看病的患者信息
        String mpi = r.getMpiid();
        if (!StringUtils.isEmpty(mpi)) {
            Patient pat = patDao.getPatientPartInfo(mpi);
            if (pat != null) {
                pat.setPatientName(r.getPatientName());
                pat.setIdcard(r.getCertId());
                pat.setMobile(r.getLinkTel());

                //获取患者是否签约标记
                pat.setSignFlag(convertPatientForAppointRecord(r.getMpiid(), r.getAppointUser()).getSignFlag());
                map.put("patinet", pat);
            }
        }

        // 申请人信息
        String req = r.getAppointUser();
        if (!StringUtils.isEmpty(req)) {
            if (req.length() == 32) {
                Patient appointUser = patDao.getPatientPartInfo(req);
                if (appointUser != null) {
                    appointUser.setMpiId(req);
                    map.put("appointUser", appointUser);
                }
            } else {
                Doctor appointUser = docDao.getDoctorPartInfo(Integer.parseInt(req));
                if (appointUser != null) {
                    map.put("appointUser", appointUser);
                }
            }
        }

        Integer acceptsDocId = null;// 接诊医生Id
        Integer acceptsOrganId = null;
        Integer docId = null;// 出诊医生ID
        Integer organId = null;

        if (r.getTelClinicFlag() == null || r.getTelClinicFlag() == 0) {
            // 普通预约的就诊目标医生
            docId = r.getDoctorId();
            organId = r.getOrganId();

            AppointRecord r2 = new AppointRecord();
            r2.setAppointDepartId(r.getAppointDepartId());
            r2.setAppointDepartName(r.getAppointDepartName());
            r2.setDoctorId(r.getDoctorId());
            r2.setOrganId(r.getOrganId());
            r2.setWorkDate(r.getWorkDate());
            r2.setStartTime(r.getStartTime());
            r2.setEndTime(r.getEndTime());
            r2.setClinicPrice(r.getClinicPrice());

            Map<String, Object> recordInfo = conversionAppointRecordInfo(r2);

            map.put("recordInfo", recordInfo);
        } else {
            // 预约记录为接诊方记录
            if (r.getClinicObject() == 1) {
                acceptsDocId = r.getDoctorId();
                acceptsOrganId = r.getOrganId();
                docId = r.getOppdoctor();
                organId = r.getOppOrgan();

            } else if (r.getClinicObject() == 2) {
                // 预约记录为出诊方记录
                acceptsDocId = r.getOppdoctor();
                acceptsOrganId = r.getOppOrgan();
                docId = r.getDoctorId();
                organId = r.getOrganId();

            }

            String telClinicId = r.getTelClinicId();
            if (!StringUtils.isEmpty(telClinicId)) {
                AppointRecord visitsRecord = getByTelClinicIdAndClinicObject(
                        telClinicId, 2);
                if (visitsRecord != null) {
                    Integer showPayOrgan = visitsRecord.getOrganId();
                    Date workDate = visitsRecord.getWorkDate();

                    AppointRecord r2 = new AppointRecord();
                    r2.setAppointDepartId(visitsRecord.getAppointDepartId());
                    r2.setAppointDepartName(visitsRecord.getAppointDepartName());
                    r2.setDoctorId(visitsRecord.getDoctorId());
                    r2.setOrganId(showPayOrgan);
                    r2.setWorkDate(workDate);
                    r2.setStartTime(visitsRecord.getStartTime());
                    r2.setEndTime(visitsRecord.getEndTime());
                    r2.setClinicPrice(visitsRecord.getClinicPrice());

                    Map<String, Object> recordInfo = conversionAppointRecordInfo(r2);

                    String acceptsDoctorText = DictionaryController.instance()
                            .get("eh.base.dictionary.Doctor")
                            .getText(visitsRecord.getOppdoctor());
                    String acceptsOrganText = DictionaryController.instance()
                            .get("eh.base.dictionary.Organ")
                            .getText(visitsRecord.getOppOrgan());

                    recordInfo.put("acceptsOrgan", visitsRecord.getOppOrgan());
                    recordInfo.put("acceptsOrganText", acceptsOrganText);
                    recordInfo.put("acceptsDepartName", visitsRecord.getOppdepartName());
                    recordInfo.put("acceptsDoctor", visitsRecord.getOppdoctor());
                    recordInfo.put("acceptsDoctorText", acceptsDoctorText);

                    map.put("recordInfo", recordInfo);

                    if (showPayOrgan != null) {
                        OrganConfigService organConfigService = AppDomainContext.getBean("eh.organConfigService", OrganConfigService.class);
                        map.put("canShowPayButton", organConfigService.canShowPayButton(showPayOrgan, workDate));
                    }
                }
            }
        }

        // 出诊医生
        if (docId != null) {
            Doctor d = docDao.getDoctorPartInfo(docId);
            if (d != null) {
                d.setOrgan(organId);
                map.put("doctor", d);
            }
        }

        // 接诊医生
        if (acceptsDocId != null) {
            Doctor acceptsDoctor = docDao.getDoctorPartInfo(acceptsDocId);
            if (acceptsDoctor != null) {
                acceptsDoctor.setOrgan(acceptsOrganId);
                map.put("acceptsDoctor", acceptsDoctor);
            }
        }

        map.put("serverTime", new Date());
        return map;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> conversionAppointRecordInfo(AppointRecord record) {
        HashMap<String, Object> map = BeanUtils.map(record, HashMap.class);
        return map;
    }

    /**
     * 根据主键查询单条预约记录(返回预约记录对象+病人对象+目标医生对象+申请医生对象+对方医生对象)
     *
     * @param appointRecordId
     * @return
     * @author LF
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     * @date 2017-2-20 luf：所有预约相关视频状态只判断是否有设备在线
     */
    @RpcService
    public AppointRecordAndPatientAndDoctor getFullAppointRecordById(
            int appointRecordId) {
        AppointService service = AppContextHolder.getBean("appointService", AppointService.class);
        String platformNgari = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ALL;
        return service.getPlatformFullAppointRecordById(appointRecordId, platformNgari);
    }

    /**
     * 根据主键查询单条预约记录
     *
     * @param appointRecordId
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.bus.AppointRecordAndPatientAndDoctor(a,b,c) from AppointRecord a,Patient b,Doctor c where a.appointRecordId=:appointRecordId and b.mpiId=a.mpiid and c.doctorId=a.doctorId")
    public abstract AppointRecordAndPatientAndDoctor getAppointRecordAndPatientAndDoctorById(
            @DAOParam("appointRecordId") int appointRecordId);

    /**
     * 在线云门诊上传图片使用
     *
     * @param telClinicId
     * @param clinicObject
     * @return
     * @author yaozh
     */
    @RpcService
    @DAOMethod
    public abstract AppointRecord getByTelClinicIdAndClinicObject(
            String telClinicId, int clinicObject);

    /**
     * 预约记录查询服务之情况一（根据机构编码和就诊起始时间进行查询）
     *
     * @param organId   --机构编码
     * @param startTime --开始时间
     * @param endTime   --结束时间
     * @return
     * @throws DAOException
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @RpcService
    public List<AppointRecordAndPatient> queryByOrganIdAndAppointDate(
            final int organId, final Date startTime, final Date endTime)
            throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            List<AppointRecordAndPatient> list = new ArrayList<AppointRecordAndPatient>();

            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) from AppointRecord a,Patient b where (a.organId=:OrganId and startTime>=:StartTime and startTime<:EndTime) and b.mpiId=a.mpiid");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("OrganId", organId);
                q.setParameter("StartTime", startTime);
                q.setParameter("EndTime", endTime);
                list = (List<AppointRecordAndPatient>) q.list();

                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                for (int i = 0; i < list.size(); i++) {
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                            .get(i).getAppointrecord().getMpiid(), list.get(i)
                            .getAppointrecord().getDoctorId());
                    list.get(i).setSignFlag(signFlag);
                }

                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointRecordAndPatient>) action.getResult();
    }

    /**
     * 预约记录查询服务之情况一（根据机构编码和就诊起始时间进行查询）--分页
     *
     * @param organId   --机构编码
     * @param startTime --开始时间
     * @param endTime   --结束时间
     * @return
     * @throws DAOException
     * @author hyj
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @RpcService
    public List<AppointRecordAndPatient> queryByOrganIdAndAppointDateWithPage(
            final int organId, final Date startTime, final Date endTime,
            final int start) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            List<AppointRecordAndPatient> list = new ArrayList<AppointRecordAndPatient>();

            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) from AppointRecord a,Patient b where (a.organId=:OrganId and startTime>=:StartTime and startTime<:EndTime) and b.mpiId=a.mpiid");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("OrganId", organId);
                q.setParameter("StartTime", startTime);
                q.setParameter("EndTime", endTime);
                q.setMaxResults(10);
                q.setFirstResult(start);
                list = (List<AppointRecordAndPatient>) q.list();

                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                for (int i = 0; i < list.size(); i++) {
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                            .get(i).getAppointrecord().getMpiid(), list.get(i)
                            .getAppointrecord().getDoctorId());
                    list.get(i).setSignFlag(signFlag);
                }

                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointRecordAndPatient>) action.getResult();
    }

    /**
     * 查询医生下个月预约记录服务
     *
     * @param doctorId
     * @return
     * @author hyj
     */
    @RpcService
    public List<AppointRecordAndPatient> queryByDoctorIdAndAppointDateNextMonth(
            final int doctorId) {
        Date startDate = Context.instance().get("date.getToday", Date.class);
        Date endDate = Context.instance().get("date.getDateOfNextMonth",
                Date.class);
        List<AppointRecordAndPatient> list = queryByDoctorIdAndAppointDate(
                doctorId, startDate, endDate);
        return list;
    }

    /**
     * 查询医生下个月预约记录服务--分页
     *
     * @param doctorId
     * @return
     * @author hyj
     */
    @RpcService
    public List<AppointRecordAndPatient> queryByDoctorIdAndAppointDateNextMonthWithPage(
            int doctorId, int start) {
        Date startDate = Context.instance().get("date.getToday", Date.class);
        Date endDate = Context.instance().get("date.getDateOfNextMonth",
                Date.class);
        List<AppointRecordAndPatient> list = queryByDoctorIdAndAppointDateWithPage(
                doctorId, startDate, endDate, start);
        return list;
    }

    /**
     * author yaozh 获取医生今后一周的预约记录
     *
     * @param doctorId 医生编号
     * @return 预约记录
     */
    @RpcService
    public List<AppointRecordAndPatient> queryByDoctorIdAndAppointDateNextWeek(
            final int doctorId) {
        Date startDate = Context.instance().get("date.getToday", Date.class);
        Date endDate = DateConversion.getDateOfNextWeek();
        List<AppointRecordAndPatient> list = queryByDoctorIdAndAppointDate(
                doctorId, startDate, endDate);
        return list;
    }

    /**
     * author yaozh 获取医生今后一周的预约记录(分页)
     *
     * @param doctorId 医生编号
     * @param start    记录起始位置
     * @param limit    查询记录数
     * @return 预约记录
     */
    @RpcService
    public List<AppointRecordAndPatient> queryByDoctorIdAndAppointDateNextWeekPageLimit(
            final int doctorId, final int start, final int limit) {
        Date startDate = Context.instance().get("date.getToday", Date.class);
        Date endDate = DateConversion.getDateOfNextWeek();
        List<AppointRecordAndPatient> list = queryByDoctorIdAndAppointDateWithPageLimit(
                doctorId, startDate, endDate, start, limit);
        return list;
    }

    /**
     * 预约记录查询服务之情况二（根据医生编号和就诊起始时间进行查询）
     *
     * @param doctorId  --医生编号
     * @param startTime --开始时间
     * @param endTime   --结束时间
     * @return
     * @throws DAOException
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @RpcService
    public List<AppointRecordAndPatient> queryByDoctorIdAndAppointDate(
            final int doctorId, final Date startTime, final Date endTime)
            throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            List<AppointRecordAndPatient> list = new ArrayList<AppointRecordAndPatient>();

            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) from AppointRecord a,Patient b where (a.doctorId=:DoctorId and startTime>=:StartTime and startTime<:EndTime) and b.mpiId=a.mpiid");

                Query q = ss.createQuery(hql.toString());
                q.setParameter("DoctorId", doctorId);
                q.setParameter("StartTime", startTime);
                q.setParameter("EndTime", endTime);
                list = (List<AppointRecordAndPatient>) q.list();
                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                for (int i = 0; i < list.size(); i++) {
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(list
                            .get(i).getAppointrecord().getMpiid(), list.get(i)
                            .getAppointrecord().getDoctorId());
                    list.get(i).setSignFlag(signFlag);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointRecordAndPatient>) action.getResult();
    }

    /**
     * 预约记录查询服务之情况二（根据医生编号和就诊起始时间进行查询）--分页
     *
     * @param doctorId  --医生编号
     * @param startTime --开始时间
     * @param endTime   --结束时间
     * @return
     * @throws DAOException
     * @author hyj
     */
    @RpcService
    public List<AppointRecordAndPatient> queryByDoctorIdAndAppointDateWithPage(
            final int doctorId, final Date startTime, final Date endTime,
            final int start) throws DAOException {
        return queryByDoctorIdAndAppointDateWithPageLimit(doctorId, startTime,
                endTime, start, 10);
    }

    /**
     * 预约记录查询服务之情况二（根据医生编号和就诊起始时间进行查询）--分页
     *
     * @param doctorId  --医生编号
     * @param startTime --开始时间
     * @param endTime   --结束时间
     * @param start     记录起始位置
     * @param limit     查询记录数
     * @return
     * @throws DAOException
     * @author yaozh
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @RpcService
    public List<AppointRecordAndPatient> queryByDoctorIdAndAppointDateWithPageLimit(
            final int doctorId, final Date startTime, final Date endTime,
            final int start, final int limit) throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            List<AppointRecordAndPatient> list = new ArrayList<AppointRecordAndPatient>();

            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) from AppointRecord a,Patient b where (a.doctorId=:DoctorId and startTime>=:StartTime and appointStatus<>2  and startTime<=:EndTime) and b.mpiId=a.mpiid");

                Query q = ss.createQuery(hql.toString());
                q.setParameter("DoctorId", doctorId);
                q.setParameter("StartTime", startTime);
                q.setParameter("EndTime", endTime);
                q.setMaxResults(limit);
                q.setFirstResult(start);
                list = (List<AppointRecordAndPatient>) q.list();
                RelationDoctorDAO RelationDoctorDAO = DAOFactory
                        .getDAO(RelationDoctorDAO.class);
                List<AppointRecordAndPatient> results = new ArrayList<AppointRecordAndPatient>();
                for (AppointRecordAndPatient arp : list) {
                    AppointRecord ar = arp.getAppointrecord();
                    Boolean signFlag = RelationDoctorDAO.getSignFlag(ar.getMpiid(), ar.getDoctorId());
                    arp.setSignFlag(signFlag);
                    ar.setRequestDate(DateConversion.convertRequestDateForBuss(ar.getStartTime()));
                    arp.setAppointrecord(ar);
                    results.add(arp);
                }
                setResult(results);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointRecordAndPatient>) action.getResult();
    }

    /**
     * 预约记录增加服务修改版--传参为对象--hyj
     * <p>
     * 微信2.1.1--copy次方法到RequestAppointService.addAppointRecordNew，只修改返回值为Integer类型。
     * <p>
     * 修改此方法时注意查看上述服务是否需要修改！！！
     *
     * @param appointRecord
     * @return
     */
    @RpcService
    public boolean addAppointRecordNew(AppointRecord appointRecord) {
        RequestAppointService requestAppointService = new RequestAppointService();
        Integer result = requestAppointService.addAppointRecordNew(appointRecord);
        if (null == result || 0 >= result) {
            return false;
        }
        return true;
    }

    /**
     * 获取同一个患者同一天同一个科室或医师的预约记录
     */
    @DAOMethod(sql = "from AppointRecord where mpiid=:mpiId and workDate=:workDate and organId=:organId and (appointDepartId=:appointDepartId or doctorId=:doctorId) and appointStatus in(0,1) and (telClinicFlag <>2 or telClinicFlag is null) and (appointRoad=5 or (appointRoad=6 and length(appointUser)<32))")
    public abstract List<AppointRecord> findByMpiIdAndWorkDateAndOrganId(
            @DAOParam("mpiId") String mpiId, @DAOParam("organId") int organId,
            @DAOParam("appointDepartId") String appointDepartId,
            @DAOParam("doctorId") int doctorId,
            @DAOParam("workDate") Date workDate);


    /**
     * 查询患者在某些机构内成功的挂号记录
     *
     * @param organIds
     * @param mpiId
     * @return
     */
    public List<AppointRecord> findByOrganAndMpi(final List<Integer> organIds, final String mpiId,final Integer forbidDeleteDays) {

        AbstractHibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {

                StringBuffer findByOrganAndMpiSql = new StringBuffer();
                findByOrganAndMpiSql.append("from AppointRecord where appointStatus in(0,1,4,5)  and MPIID=:mpiid ");
                findByOrganAndMpiSql.append("and DATEDIFF(:now,appointDate)<= :days ");

                if (CollectionUtils.isNotEmpty(organIds)) {
                    findByOrganAndMpiSql.append("and organID in :organIds");
                }

                Query q = statelessSession.createQuery(findByOrganAndMpiSql.toString());

                if (CollectionUtils.isNotEmpty(organIds)) {
                    q.setParameterList("organIds", organIds);
                }
                q.setParameter("mpiid", mpiId);
                q.setParameter("now", new Date());
                q.setParameter("days", forbidDeleteDays);
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();

    }

    /**
     * 获取同一个患者同一个就诊日的预约记录</br> 医院确认中/预约成功的+普通门诊/出诊方预约记录,不包括在线云门诊
     *
     * @param mpiId
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-8-17 上午10:59:10
     */
    @DAOMethod(sql = "from AppointRecord where mpiid=:mpiId and date(workDate)>=date(:startDate) and date(workDate)<=date(:endDate) and appointStatus in(0,1,9) and (telClinicFlag=0 or telClinicFlag is null or (telClinicFlag=1 and clinicObject=2) ) and (appointRoad=5 or (appointRoad=6 and length(appointUser)<32))")
    public abstract List<AppointRecord> findByMpiIdAndWorkDate(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 获取同一个患者一定时间段内的预约记录</br> 医院确认中/预约成功的+普通门诊/出诊方预约记录
     *
     * @param mpiId
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-8-17 下午2:27:27
     */
    @DAOMethod(sql = "from AppointRecord where mpiid=:mpiId and date(appointDate)>=date(:startDate) and date(appointDate)<=date(:endDate) and appointStatus in(0,1,9) and (telClinicFlag=0 or telClinicFlag is null or (telClinicFlag =1 and clinicObject=2) ) and (appointRoad=5 or (appointRoad=6 and length(appointUser)<32))")
    public abstract List<AppointRecord> findByMpiIdAndAppointDate(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    public AppointRecord save(AppointRecord appointRecord){
        if (appointRecord.getRequestNum() == null){
            appointRecord.setRequestNum(0);
        }
        if (appointRecord.getEvaStatus() == null){
            appointRecord.setEvaStatus(0);
        }
        return super.save(appointRecord);
    }

    /**
     * 预约确认中或转诊确认中，可发起重试
     *
     * @param appointRecordId 预约记录id
     */
    @RpcService
    public void reTryAppoint(int appointRecordId) {
        // 此处调用registAppoint
        AppointRecord appointRecord = get(appointRecordId);
        if (appointRecord != null && appointRecord.getAppointStatus() == 9) {
            Patient p = DAOFactory.getDAO(PatientDAO.class).get(
                    appointRecord.getMpiid());
            registAppoint(p, appointRecord);
        }
    }

    /**
     * 调his预约注册服务
     *
     * @return
     */
    public void registAppoint(Patient p, AppointRecord ar) {
        try {
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            Organ o = organDAO.getByOrganId(ar.getOrganId());
            AppointmentRequest appointment = new AppointmentRequest();
            appointment.setCardID(ar.getCardId());
            appointment.setCardType(ar.getCardType());
            appointment.setTelClinicFlag(ar.getTelClinicFlag());
            appointment.setRecordType(ar.getRecordType());
            appointment.setTargetOrganId(ar.getOrganId());
            appointment.setId(ar.getAppointRecordId() + "");
            appointment.setPatientName(p.getPatientName());
            appointment.setPatientSex(p.getPatientSex());
            appointment.setPatientType(p.getPatientType());
            appointment.setBirthday(p.getBirthday());
            appointment.setCredentialsType("身份证");// 该数据无从获取
            appointment.setCertID(ar.getCertId());
            appointment.setOrganizeCode(o.getOrganizeCode());
            appointment.setTransferID(ar.getTransferId());
            appointment.setPatientID(ar.getClinicId());
            String certID = appointment.getCertID();
            String c = LocalStringUtil.getSubstringByDiff(certID, "-");
            appointment.setCertID(c);
            appointment.setMobile(p.getMobile());
            appointment.setHomeAddr(p.getAddress());
            appointment.setPatientID("");
            appointment.setOperjp("1000");
            appointment.setGuardianFlag(p.getGuardianFlag());
            appointment.setGuardianName(p.getGuardianName());
            Integer transferId = ar.getTransferId();
            TransferDAO tdao = DAOFactory.getDAO(TransferDAO.class);
            if (null != transferId && 0 != transferId) {
                Transfer transfer = tdao.get(transferId);
                appointment.setDisease(transfer.getDiagianName());
                appointment.setDiseasesHistory(transfer.getPatientCondition());
            }
            // 根据云平台机构主键获取医院机构主键（平台-->his）
            // OrganDAO OrganDAO=DAOFactory.getDAO(OrganDAO.class);
            // String
            // organizeCode=OrganDAO.getOrganizeCodeByOrganId(ar.getOrganId());

            // 获取医生执业机构信息（平台-->his）
            EmploymentDAO EmploymentDAO = DAOFactory
                    .getDAO(EmploymentDAO.class);
            List<String> jobNumbers = EmploymentDAO
                    .findJobNumberByDoctorIdAndOrganId(ar.getDoctorId(),
                            ar.getOrganId());
            String jobNumber = jobNumbers.get(0);

            // 获取号源信息
            AppointSourceDAO appointSourceDAO = DAOFactory
                    .getDAO(AppointSourceDAO.class);
            AppointSource as = appointSourceDAO
                    .getById(ar.getAppointSourceId());

            if (as != null) {
                // 普通预约
                appointment.setSchedulingID(as.getOrganSchedulingId());
                appointment.setAppointSourceID(as.getOrganSourceId());
                appointment.setOrderNum(as.getOrderNum());
                appointment.setOriginalSourceid(as.getOriginalSourceId());
            } else {
                // 转诊预约
                appointment.setSchedulingID("0");
                appointment.setAppointSourceID("0");
                appointment.setOrderNum(0);

            }
            appointment.setOrganID(ar.getOrganId() + "");
            appointment.setDepartCode(ar.getAppointDepartId());
            appointment.setDepartName(ar.getAppointDepartName());
            appointment.setDoctorID(jobNumber);
            appointment.setWorkDate(ar.getStartTime());// 预约日期 + 时间点
            appointment.setStartTime(ar.getStartTime());
            appointment.setEndTime(ar.getEndTime());
            appointment.setWorkType(ar.getWorkType() + "");
            if (ar.getAppointRoad().equals(5)) {// 医生诊间预约
                appointment.setAppointRoad(2);// 转诊预约
            }
            if (ar.getAppointRoad().equals(6)) {
                // 需判断 有排班 还是 没排班
                boolean isExist = appointSourceDAO.checkIsExistScheduling(ar.getOrganId(),
                        ar.getDoctorId(), ar.getWorkType(), ar.getWorkDate());
                if (isExist) {
                    if (ar.getOrganId().equals(1)) {
                        appointment.setAppointRoad(3);// 有排班的转诊预约
                    } else {
                        appointment.setSchedulingID(ar.getOrganSchedulingId());
                        appointment.setAppointRoad(2);// 有排班的转诊预约,走预约加号通道
                        appointment.setOrderNum(ar.getOrderNum());
                    }

                    // 有排班的转诊 默认计算出一个ordernum,调用普通预约接口
                } else {
                    appointment.setAppointRoad(4);// 无排班的转诊预约
                    // 省中无排班： 副高以上按特需门诊100 来； 副高一下按特需门诊10来；

                    if (ar.getOrganId() == OrganConstant.Organ_SZ
                            || ar.getOrganId() == OrganConstant.Organ_XS) {
                        TransferDAO tDao = DAOFactory.getDAO(TransferDAO.class);
                        Transfer transfer = tDao.getById(ar.getTransferId());
                        // 省中
                        if (ar.getOrganId() == OrganConstant.Organ_SZ) {
                            ar.setAppointDepartId(OrganConstant.TXDepartCode_SZ);
                            ar.setAppointDepartName("特需门诊");
                            appointment.setDepartCode(OrganConstant.TXDepartCode_SZ);
                            transfer.setAppointDepartId(OrganConstant.TXDepartCode_SZ);
                            transfer.setAppointDepartName("特需门诊");
                        } else if (ar.getOrganId() == OrganConstant.Organ_XS) {
                            ar.setAppointDepartId(OrganConstant.TXDepartCode_XS);
                            ar.setAppointDepartName("特需门诊");
                            appointment.setDepartCode(OrganConstant.TXDepartCode_XS);
                            transfer.setAppointDepartId(OrganConstant.TXDepartCode_XS);
                            transfer.setAppointDepartName("特需门诊");
                        }
                        DoctorDAO doctordao = DAOFactory.getDAO(DoctorDAO.class);
                        Doctor doctor = doctordao.getByDoctorId(ar.getDoctorId());
                        String proTitle = doctor.getProTitle();
                        ar.setSourceLevel(3);// 转 特需
                        // 副高以上 无排班 特需转诊100
                        if ("1,2,5,6".contains(proTitle)
                                && !StringUtils.isEmpty(proTitle)) {
                            appointment.setPrice(100d);
                            ar.setClinicPrice(100d);
                            transfer.setClinicPrice(100d);
                        } else {
                            // 副高以下 都 无排班 特需转诊10 -1
                            appointment.setPrice(10d);
                            appointment.setSchedulingID("-1");
                            ar.setClinicPrice(10d);
                            transfer.setClinicPrice(10d);
                        }
                        // tDao.update(transfer);
                    }
                    // 修改科室 价格 号源
                    AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
                    dao.update(ar);
                }
            }

            appointment.setSourceLevel(ar.getSourceLevel());// 号源等级（1普通、2专家 3特需）
            appointment.setPrice(ar.getClinicPrice());
            String address = ar.getConfirmClinicAddr();
            if (address == null || "".equals(address)) {
                AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
                AppointSource appointSource = dao.getById(ar.getAppointSourceId());
                String area = appointSource.getClinicAddr();
                appointment.setClinicArea(area);
                if (area == null || "".equals(area)) {
                    appointment.setClinicArea("指定地点");
                }
            } else {
                appointment.setClinicArea(address);// 转诊地址
            }

            /*if (ar.getRecordType() != null && ar.getRecordType() == 1) {
                if (ar.getPayFlag() != null && ar.getPayFlag() == 1) {
                    AppointSendExecutor executor = new AppointSendExecutor(appointment);
                    executor.execute();
                }
            } else {
                AppointSendExecutor executor = new AppointSendExecutor(appointment);
                executor.execute();
            }*/
            //当天挂号调用预结算接口
            if (null != ar.getRecordType() && 1 == ar.getRecordType()) {
            	AppointTodayBillService appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);
                appointTodayBillService.settlePreBillForBus(ar.getAppointRecordId(), "40");
            } else {
                AppointSendExecutor executor = new AppointSendExecutor(appointment);
                executor.execute();
            }

        } catch (Exception e) {
            logger.error("his invoke error" + e.getMessage());
            throw  e;
        }
    }

    // 逻辑 省中/下沙
    // 如果医院支持加号预约的，转诊接收时处理方式如下：如果医生当天有排班（上午/下午），在有号源的情况下，选择当天最近的一个号源进行预约【选择的上下午号源是否需要根据医生选择的时间来？】
    // 如果当前号源已用完，则当前就诊序号最大号+1 进行预约操作，其他信息和当前最大号信息一样。
    // 取最大号逻辑：取医生转诊日期 号源最大号，取预约记录最大号.再取其中最大号 接收时间以最大号为准
    // 当天无排班直接 提示选择特需门诊进行转诊接收
    public void appointForTransfer(AppointRecord ar) throws DAOException {
        AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        if (ar.getAppointRoad().equals(6)) {
            // 省中接收时间不能为当天
            if (DateConversion.getFormatDate(ar.getWorkDate(), "yyyy-MM-dd")
                    .compareTo(new Date()) < 0) {
                throw new DAOException(609, "转诊接收时间必须大于当天！");
            }
            if (ar.getAppointDepartId().equals(OrganConstant.TXDepartCode_SZ)
                    || ar.getAppointDepartId().equals(
                    OrganConstant.TXDepartCode_XS)) {// 特需门诊 不限制
                return;
            }
            // 需判断 有排班 还是 没排班
            boolean isExist = appointSourceDAO.checkIsExistScheduling(ar.getOrganId(),
                    ar.getDoctorId(), ar.getWorkType(), ar.getWorkDate());
            if (isExist) {
                // appointment.setAppointRoad(3);// 有排班的转诊预约
                // 有排班的转诊 默认计算出一个ordernum,调用普通预约接口,
                AppointSource source = appointSourceDAO
                        .getExistSchedulingSource(ar.getOrganId(),ar.getDoctorId(),
                                ar.getWorkType(), ar.getWorkDate());
                getTransferInfo(source, ar);

            } else {
                // throw new DAOException(609,
                // "您选择的时间没有排班，请选择您有排班的时间或者特需门诊进行转诊接收！");
            }

        }

		/*
         * AppointSendExecutor executor = new AppointSendExecutor(appointment);
		 * executor.execute();
		 */
    }

    private void getTransferInfo(AppointSource source, AppointRecord ar) {
        ar.setWorkType(source.getWorkType());
        ar.setOrderNum(source.getOrderNum());
        // ar.setStartTime(source.getStartTime());
        // ar.setEndTime(source.getEndTime());
        ar.setAppointDepartId(source.getAppointDepartCode());
        ar.setAppointDepartName(source.getAppointDepartName());
        ar.setOrganSchedulingId(source.getOrganSchedulingId());
    }

    @SuppressWarnings("unused")
    private void getTransferInfo(AppointSource ar,
                                 AppointmentRequest appointment) {
        appointment.setWorkType(ar.getWorkType() + "");
        appointment.setOrderNum(ar.getOrderNum());
        appointment.setStartTime(ar.getStartTime());
        appointment.setEndTime(ar.getEndTime());
        appointment.setDepartCode(ar.getAppointDepartCode());
        appointment.setDepartName(ar.getAppointDepartName());
    }

    /**
     * 更新his返回数据服务(更新【机构号源编号】、【预约状态】)
     *
     * @param appointID
     * @param appointRecordId
     */
    @DAOMethod(sql = "update AppointRecord set organAppointId=:appointID,appointStatus=0 where appointRecordId=:appointRecordId")
    @RpcService
    public abstract void updateHisResult(
            @DAOParam("appointID") String appointID,
            @DAOParam("appointRecordId") Integer appointRecordId);

    /**
     * his调用后，回调服务
     */
    @RpcService
    public void updateAppointId(final AppointmentResponse res)
            throws DAOException {

        logger.info("预约注册成功后,his返回数据:" + JSONUtils.toString(res));
        final AppointRecord ar = this.get(Integer.parseInt(res.getId()));
        if (ar == null) {
//            logger.error("can not find the record by id:" + res.getId());
            throw new DAOException("can not find the record by id:"
                    + res.getId());
        }
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                String organMPI = res.getOrganMPI();
                String organAppointId = res.getAppointID();
                String confirmClinicAddr = res.getClinicArea();
                Integer orderNum = res.getOrderNum();
                String regReceipt = res.getRegReceipt();
                Date accountDate = res.getAccountDate();
                String cardId=res.getCardId();
                String cardType=res.getCardType();
                StringBuffer hql = new StringBuffer("update AppointRecord set appointStatus=:status");
                if (!StringUtils.isEmpty(organMPI)) {
                    hql.append(",clinicId=:organMPI");
                }
                if (!StringUtils.isEmpty(organAppointId)) {
                    hql.append(",organAppointId=:organAppointId");
                }
                if (!StringUtils.isEmpty(confirmClinicAddr)) {
                    hql.append(",confirmClinicAddr=:confirmClinicAddr");
                }
                if (null != orderNum) {
                    hql.append(",orderNum=:orderNum");
                }
                //这是挂号结算返回的字段
                if (!StringUtils.isEmpty(regReceipt)) {
                    hql.append(",regReceipt=:regReceipt");
                }
                //这是挂号结算返回的字段
                if (null != accountDate) {
                    hql.append(",accountDate=:accountDate");
                }
                //预约用卡卡号
                if(!StringUtils.isEmpty(cardId)){
                    hql.append(",cardId=:cardId");
                }
                //预约用卡卡类型
                if(!StringUtils.isEmpty(cardType)){
                    hql.append(",cardType=:cardType");
                }
                hql.append(" where appointRecordId=:appointRecordId");
                Query query = ss.createQuery(hql.toString());
                Integer recordType = ar.getRecordType();
                if (recordType != null && recordType.equals(1)) {
                    // 2016-09-08 luf:当天挂号状态回写为，挂号成功
                    logger.info("挂号成功" + ar.getAppointRecordId());
                    query.setParameter("status", 1);
                } else {
                    //预约支付成功
                    if (ar.getPayFlag() != null && ar.getPayFlag().intValue() == 1) {
                        query.setParameter("status", 5);
                        logger.info("预约支付成功" + ar.getAppointRecordId());
                    } else {
                        //预约成功
                        query.setParameter("status", 0);
                        logger.info("预约成功" + ar.getAppointRecordId());
                    }
                }
                if (!StringUtils.isEmpty(organAppointId)) {
                    query.setString("organAppointId", organAppointId);
                }
                if (!StringUtils.isEmpty(confirmClinicAddr)) {
                    query.setString("confirmClinicAddr", confirmClinicAddr);
                }
                if (null != orderNum) {
                    query.setInteger("orderNum", orderNum);
                }
                if (!StringUtils.isEmpty(regReceipt)) {
                    query.setString("regReceipt", regReceipt);
                }
                if (null != accountDate) {
                    query.setTimestamp("accountDate", accountDate);
                }
                if (!StringUtils.isEmpty(organMPI)) {
                    query.setParameter("organMPI", organMPI);
                }
                //预约用卡卡号
                if(!StringUtils.isEmpty(cardId)){
                    query.setString("cardId",cardId);
                }
                //预约用卡卡类型
                if(!StringUtils.isEmpty(cardType)){
                    query.setString("cardType",cardType);
                }
                query.setString("appointRecordId", res.getId());
                query.executeUpdate();

                Integer transId = ar.getTransferId();
                if (transId != null && transId != 0) {
                    TransferDAO transferdao = DAOFactory.getDAO(TransferDAO.class);

                    // 回写状态和就诊点
                    String clinicArea = res.getClinicArea() == null ? "" : res.getClinicArea().replaceAll(" ", "");
                    if (StringUtils.isNotEmpty(clinicArea)) {
                        logger.info("转诊预约成功,回写转诊单状态和就诊点,transId:" + transId + " clinicArea:" + clinicArea);
                        // transferdao.updateTransferFromHosp(transId, clinicArea);
                        transferdao.updateTransferAndResultFromHosp(transId, clinicArea);
                    }
                }
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        if (action.getResult()) {
            // 云门诊预约his成功后 修改AppiontStatus=4
        	updateAppointStatusForcloudClinic(res.getAppointID(), ar,4,null);
            
            Integer recordType = ar.getRecordType();
            Integer payFlag=ar.getPayFlag()==null?0:ar.getPayFlag();
            if (recordType.intValue()==1 || payFlag.intValue()==0) {
                addIncome(ar);
                //wx2.7 普通预约成功、当天挂号支付成功并医院成功、特需预约被医生接收并医院成功发放优惠劵
                CouponPushService couponService = new CouponPushService();
                couponService.sendAppointSuccessCouponMsg(ar);
            }

            //消息推送消息
            try {
                Integer telClinicFlag = ar.getTelClinicFlag();
                if (ar.getAppointUser().length() == 32 && telClinicFlag != null
                        && telClinicFlag == 1) {
                    // 患者预约云门诊暂不发送短信
                } else {
                    sendAppointmentMsg(ar);
                    //当患者预约挂号该医生成功时，向患者自动推送该评估表
                    SmsPushService pushService=AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                    pushService.pushMsgData2Ons(ar.getAppointRecordId(),ar.getOrganId(),"SendEvaluation","",null);
                }
            } catch (Exception e) {
                logger.error("预约成功短信发送失败" + e.getMessage());
            }
            addFollowPlan(ar);
        }
    }
    
    
    /**
     * 结算成功后更新预约记录
     * @param res
     * @param ar
     * @throws DAOException
     */
    public void updateAppointForTodayBill(final AppointmentResponse res,final AppointRecord ar)
            throws DAOException {
        logger.info("结算成功后,his返回数据:" + JSONUtils.toString(res));        
        logger.info("结算成功后,ar请求数据:" + JSONUtils.toString(ar));   
        if (ar == null) {
            throw new DAOException("can not find the record by id:"
                    + res.getId());
        }
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String organMPI = res.getOrganMPI();
                String organAppointId = res.getAppointID();
                String confirmClinicAddr = res.getClinicArea();
                Integer orderNum = res.getOrderNum();
                String regReceipt = res.getRegReceipt();
                Date accountDate = res.getAccountDate();
                String cardId=res.getCardId();
                String cardType=res.getCardType();
                StringBuffer hql = new StringBuffer("update AppointRecord set appointStatus=:status");
                if (!StringUtils.isEmpty(organMPI)) {
                    hql.append(",clinicId=:organMPI");
                }
                if (!StringUtils.isEmpty(organAppointId)) {
                    hql.append(",organAppointId=:organAppointId");
                }
                if (!StringUtils.isEmpty(confirmClinicAddr)) {
                    hql.append(",confirmClinicAddr=:confirmClinicAddr");
                }
                if (null != orderNum) {
                    hql.append(",orderNum=:orderNum");
                }
                //这是挂号结算返回的字段
                if (!StringUtils.isEmpty(regReceipt)) {
                    hql.append(",regReceipt=:regReceipt");
                }
                //这是挂号结算返回的字段
                if (null != accountDate) {
                    hql.append(",accountDate=:accountDate");
                }
                //预约用卡卡号
                if(!StringUtils.isEmpty(cardId)){
                    hql.append(",cardId=:cardId");
                }
                //预约用卡卡类型
                if(!StringUtils.isEmpty(cardType)){
                    hql.append(",cardType=:cardType");
                }
                hql.append(" where appointRecordId=:appointRecordId");
                Query query = ss.createQuery(hql.toString());
                Integer recordType = ar.getRecordType();
                if (recordType != null && recordType.equals(1)) {
                    // 2016-09-08 luf:当天挂号状态回写为，挂号成功
                    logger.info("挂号成功" + ar.getAppointRecordId());
                    query.setParameter("status", 1);
                } else {
                    //预约支付成功
                    if (ar.getPayFlag() != null && ar.getPayFlag().intValue() == 1) {
                        query.setParameter("status", 5);
                        logger.info("预约支付成功" + ar.getAppointRecordId());
                    } else {
                        //预约成功
                        query.setParameter("status", 0);
                        logger.info("预约成功" + ar.getAppointRecordId());
                    }

                }
                if (!StringUtils.isEmpty(organAppointId)) {
                    query.setString("organAppointId", organAppointId);
                }
                if (!StringUtils.isEmpty(confirmClinicAddr)) {
                    query.setString("confirmClinicAddr", confirmClinicAddr);
                }
                if (null != orderNum) {
                    query.setInteger("orderNum", orderNum);
                }
                if (!StringUtils.isEmpty(regReceipt)) {
                    query.setString("regReceipt", regReceipt);
                }
                if (null != accountDate) {
                    query.setTimestamp("accountDate", accountDate);
                }
                if (!StringUtils.isEmpty(organMPI)) {
                    query.setParameter("organMPI", organMPI);
                }
                if(!StringUtils.isEmpty(cardId)){
                    query.setString("cardId", cardId);
                }
                if(!StringUtils.isEmpty(cardType)){
                    query.setString("cardType", cardType);
                }
                query.setString("appointRecordId", res.getId());
                query.executeUpdate();

                Integer transId = ar.getTransferId();
                if (transId != null && transId != 0) {
                    TransferDAO transferdao = DAOFactory.getDAO(TransferDAO.class);

                    // 回写状态和就诊点
                    String clinicArea = res.getClinicArea() == null ? "" : res.getClinicArea().replaceAll(" ", "");
                    if (StringUtils.isNotEmpty(clinicArea)) {
                        logger.info("转诊预约成功,回写转诊单状态和就诊点,transId:" + transId + " clinicArea:" + clinicArea);
                        // transferdao.updateTransferFromHosp(transId, clinicArea);
                        transferdao.updateTransferAndResultFromHosp(transId, clinicArea);
                    }
                }
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        if (action.getResult()) {
            Integer recordType = ar.getRecordType();
            Integer payFlag=ar.getPayFlag()==null?0:ar.getPayFlag();
            if (recordType.intValue()==1 || payFlag.intValue()==0) {
                addIncome(ar);
                //wx2.7 普通预约成功、当天挂号支付成功并医院成功、特需预约被医生接收并医院成功发放优惠劵
                CouponPushService couponService = new CouponPushService();
                couponService.sendAppointSuccessCouponMsg(ar);
            }
            Integer transId = ar.getTransferId();

            //消息推送消息
            try {
                Integer telClinicFlag = ar.getTelClinicFlag();
                if (ar.getAppointUser().length() == 32 && telClinicFlag != null
                        && telClinicFlag == 1) {
                    // 患者预约云门诊暂不发送短信
                } else {
                    sendAppointmentMsg(ar);
                    //当患者预约挂号该医生成功时，向患者自动推送该评估表
                    SmsPushService pushService=AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                    pushService.pushMsgData2Ons(ar.getAppointRecordId(),ar.getOrganId(),"SendEvaluation","",null);
                }
            } catch (Exception e) {
                logger.error("预约成功短信发送失败" + e.getMessage());
            }
            addFollowPlan(ar);
        }
    }
    
    /**
     * 需要发送his的云门诊预约成功 后修改appoointStatus=4
     */
    /**
     * 需要发送his的云门诊，状态、预约id信息更新
     * @param appointID  预约ID，预约成功后的会写； 为null则说明其它业务不需要修改该字段
     * @param ar
     * @param appointStatus 预约表状态 必须更新
     * @param payStatus   支付状态 可选更新
     */
    public void updateAppointStatusForcloudClinic(String appointID,AppointRecord ar,Integer appointStatus,Integer payStatus){
    	if (ar.getClinicObject() != null && ar.getClinicObject() == 2) {
    		HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
    		HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(ar.getOrganId());
    		if (null != cfg && cfg.getCloudClinicNeedToHis() != null && cfg.getCloudClinicNeedToHis() == 1) {
    			String telClinicId = ar.getTelClinicId();
                List<AppointRecord> list = findByTelClinicId(telClinicId);
                AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                for (AppointRecord appointRecord : list) {
                	appointRecord.setAppointStatus(appointStatus);
                	if(StringUtils.isNotEmpty(appointID)){
                		appointRecord.setOrganAppointId(appointID);
                	}
                	if(payStatus != null){
                		appointRecord.setPayFlag(payStatus);
                	}
                    appointRecordDAO.update(appointRecord);
                }
    		}

    	}
    }

    /**
     * 内部方法，用于创建随访计划
     * @param appointRecord
     */
    public void createPlan(AppointRecord appointRecord){
        if(null == appointRecord || null == appointRecord.getAppointRecordId()){
            throw new DAOException(DAOException.VALUE_NEEDED, "appointRecordId is needed");
        }
        FollowPlanDAO followPlanDAO = DAOFactory.getDAO(FollowPlanDAO.class);
        //创建前验证是否已存在预约生成的提前一天通知随访计划,fromType为2
        List<FollowPlan> planList = followPlanDAO.findByAppointRecordIdAndFromType(appointRecord.getAppointRecordId(), "2");
        if(null != planList && 0 != planList.size()){
            return;
        }
        try {
            //EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            FollowAddService followAddService = AppContextHolder.getBean("followAddService",FollowAddService.class);
            Date startDate = DateConversion.getFormatDate(appointRecord.getAppointDate(),"yyyy-MM-dd 00:00:00");
            Date endDate = DateConversion.getFormatDate(appointRecord.getWorkDate(),"yyyy-MM-dd 00:00:00");
            int intervalDay = DateConversion.getDaysBetween(startDate,endDate);
            FollowPlan followPlan = new FollowPlan();
            followPlan.setFromType("2");
            followPlan.setStartDate(startDate);
            followPlan.setEndDate(endDate);
            followPlan.setIntervalDay(intervalDay);
            followPlan.setRemindPatient(1);
            followPlan.setAheadNum(1);
            followPlan.setAheadUnit(2);
            followPlan.setRemindSelf(0);
            followPlan.setIntervalNum(intervalDay);
            followPlan.setIntervalUnit(1);
            followPlan.setPlanCreator(appointRecord.getDoctorId());
            followPlan.setPlanType(1);
            followPlan.setAppointRecordId(appointRecord.getAppointRecordId());
            //Map msgMap = employmentDAO.getUnEmptyEmployment(appointRecord.getDoctorId());
            //System.out.println(msgMap);
            //String docMsg = msgMap.get("organText")+" "+((Map)((List)msgMap.get("addressList")).get(0)).get("professionName")+" "+msgMap.get("departText");
            //修改提醒内容 是机构简称+预约科室+医生姓名
            Integer organId = appointRecord.getOrganId();
            String shortName = organDAO.getShortNameById(organId);
            String docName = doctorDAO.getNameById(appointRecord.getDoctorId());
            String appointDepartName = appointRecord.getAppointDepartName();
            String docMsg = shortName + " " + appointDepartName + " " + docName + "医生";
            followPlan.setRemindContent("您即将于"+DateConversion.getDateFormatter(appointRecord.getStartTime(),"yyyy-MM-dd HH:mm")+"（明天）前往"+docMsg+"门诊处就诊。请及时携带有效身份证件(医保病人请携带医保本和医保卡)至医院挂号窗口或自助机上取号就诊。");
            List<FollowPlan> followPlanList = new ArrayList<>();
            List<String> mpiList = new ArrayList<>();
            followPlanList.add(followPlan);
            mpiList.add(appointRecord.getMpiid());
            followAddService.addFollowPlan(followPlanList,mpiList);
        } catch (Exception e) {
            logger.info("requestAppointService.createPlan to create plan faild and appointRecordId is [{}]",appointRecord.getAppointRecordId());
        }
    }

    /**
     * 内部方法，用于创建随访计划
     * @param appointRecord
     */
    public void addFollowPlan(AppointRecord appointRecord){
        if((appointRecord.getClinicObject() == null && appointRecord.getDoctorId()!=null) || (appointRecord.getClinicObject()!=2 && appointRecord.getDoctorId()!=null)) {
            if(appointRecord.getSourceType() == 3){
                TransferDAO transferdao = DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer = transferdao.getById(appointRecord.getTransferId());
                if(transfer.getIfCreateFollowPlan()!=null){
                    if(transfer.getIfCreateFollowPlan()){
                        createPlan(appointRecord);
                    }
                }
            }
            if (appointRecord.getIfCreateFollowPlan() != null) {
                if (appointRecord.getIfCreateFollowPlan()) {
                    createPlan(appointRecord);
                }
            }
        }
        if((appointRecord.getClinicObject() == null && appointRecord.getDoctorId()!=null) || (appointRecord.getClinicObject()!=1 && appointRecord.getDoctorId()!=null)) {
            FollowPlanTriggerService service = AppContextHolder.getBean("eh.followPlanTriggerService",FollowPlanTriggerService.class);
            if (appointRecord.getTriggerId()!=null && appointRecord.getTriggerId()!=0){
                List<String> list = new ArrayList<>();
                if (appointRecord.getAppointUser().length()==32){
                    list.add(appointRecord.getAppointUser());
                }else {
                    list.add(appointRecord.getMpiid());
                }
                service.addFollowByPatientReport(appointRecord.getTriggerId(),list,appointRecord.getDoctorId(),appointRecord.getStartTime(),appointRecord.getAppointRecordId());
            }
        }
    }

    /**
     * 不调用his，直接转诊成功
     */
    @RpcService
    public void updateAppointId4TransferNottohis(final AppointmentResponse res)
            throws DAOException {

        logger.info("不调用his，直接转诊成功-预约注册成功后,his返回数据:" + JSONUtils.toString(res));
        final AppointRecord ar = this.get(Integer.parseInt(res.getId()));
        if (ar == null) {
            throw new DAOException("can not find the record by id:" + res.getId());
        }
        updateAppointId4TransferNottoHisOpt( res, ar);

    }

    public void updateAppointId4TransferNottoHisOpt(final AppointmentResponse res,final AppointRecord ar){
    	final TransferDAO transferdao = DAOFactory.getDAO(TransferDAO.class);
        final Integer transId = ar.getTransferId();

        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "update AppointRecord set appointStatus=:status,organAppointId=:organAppointId,confirmClinicAddr=:confirmClinicAddr,orderNum=:orderNum where appointRecordId=:appointRecordId";
                Query query = ss.createQuery(hql.toString());
                Integer recordType = ar.getRecordType();
                if (recordType != null && recordType.equals(1)) {
                    // 2016-09-08 luf:当天挂号状态回写为，挂号成功
                    query.setParameter("status", 1);
                } else {
                    query.setParameter("status", 0);
                }
                query.setString("organAppointId", res.getAppointID());
                query.setString("appointRecordId", res.getId());
                query.setString("confirmClinicAddr", res.getClinicArea());
                query.setInteger("orderNum", res.getOrderNum());

                query.executeUpdate();

                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        if (action.getResult()) {
            // 更新转诊单状态
            if (transId != null && transId != 0) {//转诊
                String appointBusType = "";
                transferdao.updateTransferStatusById(2, transId);//更新转诊单状态
                // 如果有号转诊，则给目标医生发送系统消息
                Transfer transfer = transferdao.getById(transId);
                if (transfer.getIsAdd() == false) {// 如果有号则给目标医生发送系统消息
                    //transferdao.pushMessageToTargetDocForSource(ar.getTransferId());
                    //有号转诊给目标医生发送系统消息
                    appointBusType = "HasSourceAppointSucc";
                } else {
                    //加号转诊接收成功给患者发送微信模板消息
                    appointBusType = "AddOrInHospTransferConfirm";
                }
                SmsInfo smsInfo = new SmsInfo();
                smsInfo.setBusId(ar.getTransferId());
                smsInfo.setBusType(appointBusType);
                smsInfo.setSmsType(appointBusType);
                smsInfo.setOrganId(ar.getOrganId());
                smsInfo.setClientId(ar.getDeviceId());
                SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                smsPushService.pushMsgData2OnsExtendValue(smsInfo);
            }

            //2016-11-23 12:05:41 zhangx：将医生奖励放置在业务代码后，消息通知前
            // 增加医生收入
            addIncome(ar);

            //wx2.7 普通预约成功、当天挂号支付成功并医院成功、特需预约被医生接收并医院成功发放优惠劵
            CouponPushService couponService = new CouponPushService();
            couponService.sendAppointSuccessCouponMsg(ar);

            //预约成功消息发送 改造
            try {
                Integer telClinicFlag = ar.getTelClinicFlag();
                if (ar.getAppointUser().length() == 32 && telClinicFlag != null
                        && telClinicFlag == 1) {
                    // 患者预约云门诊暂不发送短信
                } else {
                    sendAppointmentMsg(ar);
                    //当患者预约挂号该医生成功时，向患者自动推送该评估表
                    SmsPushService pushService=AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                    pushService.pushMsgData2Ons(ar.getAppointRecordId(),ar.getOrganId(),"SendEvaluation","",null);
                }
            } catch (Exception e) {
                logger.error("预约成功短信发送失败" + e.getMessage());
            }
             addFollowPlan(ar);
        }
    }

    /**
     * 向患者/患者和申请医生发送预约成功短信 (云门诊/线下门诊预约/转诊成功向申请医生和患者发送门诊转诊确认短信)
     *
     * @param ar
     */
    public void sendAppointmentMsg(AppointRecord ar) {
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        Integer telClinicFlag = ar.getTelClinicFlag();
        if (ar.getTransferId() == null || ar.getTransferId() == 0) {
            // 预约
            String appointBusType = "";
            int payFlay=ar.getPayFlag()==null?0:ar.getPayFlag().intValue();
            int recordType=ar.getRecordType()==null?0:ar.getRecordType().intValue();

            if (telClinicFlag == null || telClinicFlag == 0) {// 线下
                if (ar.getAppointUser().length() == 32) {//患者预约
                    //2017-5-26 22:43:00 wx3.2 消息模块优化，将预约成功推送消息拆分成-预约成功不支付+预约成功已支付+挂号成功
                    if(recordType==1){
                        //挂号成功
                        appointBusType="PatRegisterSucc";
                    }else{
                        if(payFlay==0){
                            //预约成功未支付
                            appointBusType="PatAppointSuccUnPay";
                        }else if(payFlay==1){
                            //预约成功已支付
                            appointBusType="PatAppointSuccAndPay";
                        }
                    }
                    logger.info("患者预约挂号成功推送消息busType:[{}],appointRecordId:[{}]", appointBusType, ar.getAppointRecordId());
                } else {//医生预约
                    if(recordType==1){
                        logger.info("医生端无挂号业务busType:[{}],appointRecordId:[{}]", appointBusType, ar.getAppointRecordId());
                    }else{
                        if(payFlay==0){
                            //预约成功未支付
                            appointBusType="DocAppointSuccUnPay";
                        }else if(payFlay==1){
                            //预约成功已支付
                            appointBusType="DocAppointSuccAndPay";
                        }
                    }
                    logger.info("医生预约成功推送消息busType:[{}],appointRecordId:[{}]", appointBusType, ar.getAppointRecordId());
                }
            } else {// 云门诊
                Integer clinicObject = ar.getClinicObject();
                if (clinicObject != null && clinicObject == 2) {// 出诊方
                    appointBusType = "CloudClinicAppointSucc";
                }
            }

            if (StringUtils.isEmpty(appointBusType)) {
                logger.error("预约成功后未能区分出消息发送类型,消息发送失败");
                return;
            }
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2Ons(ar.getAppointRecordId(), ar.getOrganId(), appointBusType, appointBusType, ar.getDeviceId());
        } else {// 转诊
            int transferId = ar.getTransferId();
            Transfer transfer = transferDAO.getById(transferId);

            HashMap<String, Integer> map = new HashMap<String, Integer>();
            map.put("appointId", ar.getAppointRecordId());

            String busType=getTrasnferBusType(ar,transfer);

            if (StringUtils.isEmpty(busType)) {
                logger.error("转诊接收成功后未能区分出发送的业务,消息发送失败");
                return;
            }

            SmsInfo smsInfo = new SmsInfo();
            smsInfo.setBusId(transferId);
            smsInfo.setBusType(busType);
            smsInfo.setSmsType(busType);
            smsInfo.setOrganId(transfer.getConfirmOrgan());
            smsInfo.setClientId(transfer.getDeviceId());
            smsInfo.setCreateTime(new Date());
            smsInfo.setStatus(0);
            smsInfo.setExtendValue(JSONUtils.toString(map));
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);
        }
    }

    /**
     * 获取转诊busType
     * @param ar
     * @param transfer
     * @return
     */
    private String getTrasnferBusType(AppointRecord ar,Transfer transfer){

        Integer telClinicFlag = ar.getTelClinicFlag();

        String busType = "";
        if (telClinicFlag == null || telClinicFlag == 0) {// 线下
            if (ar.getAppointUser().length() == 32) {//患者预约
                busType = "PatTransferConfirm";
            } else {
                boolean isAdd=transfer.getIsAdd()==null?true:transfer.getIsAdd().booleanValue();
                int payFalg=ar.getPayFlag()==null?0:ar.getPayFlag().intValue();
                if(!isAdd&&payFalg==0){
                    //有号转诊-未支付
                    busType="DocHasSourceTransferConfirmUnPay";
                }
                if(!isAdd&&payFalg==1){
                    //有号转诊-已支付
                    busType="DocHasSourceTransferConfirmAndPay";
                }

                if(isAdd){
                    //以下为发送给患者
                    HisServiceConfigDAO hisService = DAOFactory.getDAO(HisServiceConfigDAO.class);
                    boolean tohis=hisService.isToHisEnable(transfer.getConfirmOrgan(), "TOHIS");
                    //不调用his直接转诊成功
                    if (!tohis && payFalg==0) {
                        busType = "DocTransferConfirmNoHisUnPay";
                    } else if(!tohis && payFalg==1){
                        busType = "DocTransferConfirmNoHisAndPay";
                    }else if(tohis && payFalg==0){
                        busType = "DocTransferConfirmToHisUnPay";
                    }else if(tohis && payFalg==1){
                        busType = "DocTransferConfirmToHisAndPay";
                    }
                }


            }
        } else {// 云门诊
            Integer clinicObject = ar.getClinicObject();
            if (clinicObject != null && clinicObject == 2) {// 出诊方
                busType = "DocTransferConfirmCloud";
            }
        }

        return busType;
    }

    /**
     * 服务名:预约挂号记录爽约服务
     *
     * @param appointRecordId
     * @param appointFail
     * @param appointFailUser
     * @return
     * @throws
     * @author yxq
     */
    @RpcService
    @DAOMethod(sql = "update AppointRecord set appointFail=:appointFail, appointFailUser=:appointFailUser, appointStatus=3 where appointRecordId=:appointRecordId")
    public abstract void updateFail(
            @DAOParam("appointRecordId") int appointRecordId,
            @DAOParam("appointFail") Date appointFail,
            @DAOParam("appointFailUser") String appointFailUser);

    /**
     * 服务名:预约挂号记录确认服务
     *
     * @param appointRecordId
     * @param organID
     * @param organAppointId
     * @param registerDate
     * @param registerUser
     * @param registerName
     * @return
     * @throws
     * @author yxq
     */
    @DAOMethod(sql = "update AppointRecord set organID=:organID, organAppointId=:organAppointId, registerDate=:registerDate, registerUser=:registerUser, registerName=:registerName, appointStatus=1 where appointRecordId=:appointRecordId")
    public abstract void updateSure(
            @DAOParam("appointRecordId") int appointRecordId,
            @DAOParam("organID") int organID,
            @DAOParam("organAppointId") String organAppointId,
            @DAOParam("registerDate") Date registerDate,
            @DAOParam("registerUser") String registerUser,
            @DAOParam("registerName") String registerName);

    @RpcService
    @DAOMethod
    public abstract AppointRecord getByAppointRecordId(int appointRecordId);

    @DAOMethod
    public abstract AppointRecord getByOutTradeNo(String outTradeNo);

    @DAOMethod
    public abstract List<AppointRecord> findByOutTradeNo(String outTradeNo);

    @RpcService
    @DAOMethod(sql = "update AppointRecord set payFlag=:payflag ,appointStatus=:appointStatus where outTradeNo=:outTradeNo")
    public abstract void updateSinglePayFlagByOutTradeNo(
            @DAOParam("payflag") int payflag,@DAOParam("appointStatus") int appointStatus,
            @DAOParam("outTradeNo") String outTradeNo);

    @RpcService
    @DAOMethod(sql = "update AppointRecord set payFlag=:payflag ,appointStatus=:appointStatus where appointRecordId=:appointRecordId")
    public abstract void updateAppointStatusAndPayFlagByAppointRecordId(
            @DAOParam("appointStatus") Integer appointStatus,
            @DAOParam("payflag") int payflag,
            @DAOParam("appointRecordId") Integer appointRecordId);

    /**
     * wnw 根据his预约记录，查询平台上的预约记录
     *
     * @param record
     * @return
     */
    @RpcService
    public AppointRecord getAppointedRecord(final HisAppointRecord record) {
        AbstractHibernateStatelessResultAction<AppointRecord> action = new AbstractHibernateStatelessResultAction<AppointRecord>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "from AppointRecord where organId=:organId and organAppointId=:organAppointId";

                Query q = ss.createQuery(hql);
                q.setParameter("organId", record.getOrganId());
                q.setParameter("organAppointId", record.getOrganAppointId());
                AppointRecord ar = (AppointRecord) q.uniqueResult();
                setResult(ar);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }

    @RpcService
    public void sure(int appointRecordId, int organID, String organAppointId,
                     Date registerDate, String registerUser, String registerName) {
        this.updateSure(appointRecordId, organID, organAppointId, registerDate,
                registerUser, registerName);
        if (this.getByAppointRecordId(appointRecordId).getTransferId() != null) {
            TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
            int transferId = this.getByAppointRecordId(appointRecordId).getTransferId();
            Date date = new Date();
            Timestamp exeTime = new Timestamp(date.getTime());
            transferDAO.updateExeTransfer(transferId, exeTime, 3);
        }
    }


    /**
     * 服务名:预约挂号记录取消服务
     *
     * @param appointRecordId
     * @param cancelDate
     * @param cancelUser
     * @param cancelName
     * @param cancelResean
     * @return
     * @throws
     * @author yxq
     */
//    @DAOMethod(sql = "update AppointRecord set cancelDate=:cancelDate, cancelUser=:cancelUser, cancelName=:cancelName, cancelResean=:cancelResean, appointStatus=2 where appointRecordId=:appointRecordId")
//    public abstract void updateCancel(
//            @DAOParam("appointRecordId") int appointRecordId,
//            @DAOParam("cancelDate") Date cancelDate,
//            @DAOParam("cancelUser") String cancelUser,
//            @DAOParam("cancelName") String cancelName,
//            @DAOParam("cancelResean") String cancelResean);

    /**
     * 供updateCancel调用
     *
     * @param appointRecordId
     * @return
     */
    @DAOMethod(sql = "Select telClinicFlag From AppointRecord where appointRecordId=:appointRecordId")
    public abstract Integer getTelClinicFlagById(@DAOParam("appointRecordId") int appointRecordId);

    /**
     * 根据appointreCordId获取payFlag
     *
     * @param appointRecordId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "Select payFlag From AppointRecord where appointRecordId=:appointRecordId")
    public abstract Integer getPayFlagById(@DAOParam("appointRecordId") int appointRecordId);

    /**
     * 供updateCancel调用
     *
     * @param appointRecordId
     */
    @DAOMethod(sql = "update AppointRecord set clinicStatus=9 where appointRecordId=:appointRecordId and appointStatus=2")
    public abstract void updateClinicStatusById(@DAOParam("appointRecordId") int appointRecordId);

    /**
     * 服务名:预约挂号记录取消服务
     * <p>
     * 根据原有服务修改：更新appointstatus的同时将clinicStatus更新为取消
     *
     * @param appointRecordId
     * @param cancelDate
     * @param cancelUser
     * @param cancelName
     * @param cancelResean
     * @author luf 2016-8-17
     */
    public void updateCancel(final int appointRecordId, final Date cancelDate, final String cancelUser, final String cancelName, final String cancelResean) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "update AppointRecord set cancelDate=:cancelDate, cancelUser=:cancelUser, cancelName=:cancelName, cancelResean=:cancelResean, appointStatus=2 where appointRecordId=:appointRecordId";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("cancelDate", cancelDate);
                q.setParameter("cancelUser", cancelUser);
                q.setParameter("cancelName", cancelName);
                q.setParameter("cancelResean", cancelResean);
                q.setParameter("appointRecordId", appointRecordId);
                int num = q.executeUpdate();
                Integer telClinicFlag = getTelClinicFlagById(appointRecordId);
                if (num > 0 && telClinicFlag != null && telClinicFlag > 0) {
                    updateClinicStatusById(appointRecordId);
                }
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        action.getResult();
    }

    public void updateCancelBySys(final int appointRecordId, final Date cancelDate, final String cancelUser, final String cancelName, final String cancelResean) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "update AppointRecord set cancelDate=:cancelDate, cancelUser=:cancelUser, " +
                        "cancelName=:cancelName, cancelResean=:cancelResean, appointStatus=2 " +
                        ",isAppointFail=1 " +
                        "where appointRecordId=:appointRecordId";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("cancelDate", cancelDate);
                q.setParameter("cancelUser", cancelUser);
                q.setParameter("cancelName", cancelName);
                q.setParameter("cancelResean", cancelResean);
                q.setParameter("appointRecordId", appointRecordId);
                int num = q.executeUpdate();
                Integer telClinicFlag = getTelClinicFlagById(appointRecordId);
                if (num > 0 && telClinicFlag != null && telClinicFlag > 0) {
                    updateClinicStatusById(appointRecordId);
                }
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        action.getResult();
    }

    /**
     * 预约取消走his判断
     */
    private boolean isAppointToHis(AppointRecord a) {
        if (a.getAppointSourceId() == null || a.getAppointSourceId().intValue() == 0) {//排除转诊
            return false;
        }
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig con = hisServiceConfigDao.getByOrganId(a.getOrganId());
        if (con != null) {
            String serverName = con.getAppDomainId() + ".appointmentService";
//            ClientSetDAO dao = DAOFactory.getDAO(ClientSetDAO.class);
//            RpcServiceInfo info = dao.getByOrganIdAndServiceName(serverName);
            RpcServiceInfoStore rpcServiceInfoStore = AppDomainContext.getBean("eh.rpcServiceInfoStore", RpcServiceInfoStore.class);
            RpcServiceInfo info = rpcServiceInfoStore.getInfo(serverName);
            if (info != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 挂号预约支付退款失败短信 并更新payflag
     */
    public void sendSMS_PayRefundFail(Integer busId) {
        AppointRecord a = getByAppointRecordId(busId);

        /*SmsInfo info = new SmsInfo();
        info.setBusId(a.getAppointRecordId());// 业务表主键
        info.setBusType("AppointCancelPayRefundFail");// 业务类型
        info.setSmsType("appCancelPayFailMsg");
        info.setOrganId(a.getOrganId());// 短信服务对应的机构， 0代表通用机构
        info.setClientId(a.getDeviceId());
        AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
        exe.execute();*/
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2Ons(a.getAppointRecordId(), a.getOrganId(),
                "AppointCancelPayRefundFail", "appCancelPayFailMsg", a.getDeviceId());
    }

    /**
     * 平台预约取消服务,需调his取消接口
     *
     * @param appointRecordId
     * @param cancelUser
     * @param cancelName
     * @param cancelResean
     * @return
     */
    @RpcService
    public boolean cancel(final int appointRecordId, final String cancelUser,
                          final String cancelName, final String cancelResean) {
        return this.cancelWithFlag(appointRecordId,cancelUser,cancelName,cancelResean,0);
    }

    /**
     * 平台预约取消/撤销服务,需调his取消接口
     *
     * @param appointRecordId
     * @param cancelUser
     * @param cancelName
     * @param cancelResean
     * @param flag 使用来源-0后台1运营平台
     * @return
     */
    @RpcService
    public boolean cancelWithFlag(final int appointRecordId, final String cancelUser,
                          final String cancelName, final String cancelResean,int flag) {
        logger.info("appointRecordId:" + appointRecordId + ",cancelUser:"
                + cancelUser + ",cancelName:" + cancelName + ",cancelResean:"
                + cancelResean);
        AppointRecord a = getByAppointRecordId(appointRecordId);
        if (a.getAppointStatus().intValue() == 2) {
            throw new DAOException("该预约已经取消");
        }
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        //预约支付取消
        if (a.getPayFlag() != null && a.getPayFlag().intValue() == 1 && a.getRecordType() != null && a.getRecordType().intValue() != 1) {
            //  判断是否可以取消   +   已支付
            if (hisServiceConfigDao.isServiceEnable(a.getOrganId(), ServiceType.cancelPay)) {
                //调用结算取消接口
                HisResponse<String> f = CancelRegAccount(a);
                if (f != null && f.isSuccess()) {
                    logger.info("his取消结算接口：code" + f.getMsgCode() + ",msg" + f.getMsg());
                    String refundReceipt = f.getData();
                    if (refundReceipt != null) {
                        a.setAppointStatus(8);
                        a.setRefundReceipt(refundReceipt);
                        update(a);
                        if (a.getTransferId() != null) {
                            DAOFactory.getDAO(TransferDAO.class).updateTransferStatusById(9, a.getTransferId());
                        }
                    }
                    //微信退款
                    WxRefundExecutor executor = new WxRefundExecutor(appointRecordId, "appoint" , 1);
                    executor.execute();
                    boolean cancelAppoointFlag = doCancelAppoint(appointRecordId, cancelUser, cancelName, cancelResean);
                    if(cancelAppoointFlag){
                        //a.setAppointStatus(6); //退款成功
                        updateStatusById(6, appointRecordId);
                    }else{
//                    	a.setAppointStatus(7); //退款失败
                        updateStatusById(7, appointRecordId);
                    }
                    return cancelAppoointFlag;
                } else {
                    logger.info("his取消结算接口失败");
                    //1.线下已取消
                    if (null != f && StringUtils.isNotEmpty(f.getMsgCode()) && "5".equals(f.getMsgCode())) {
                        updateCancel(appointRecordId, new Date(), cancelUser,
                                cancelName, "已通过其他途径取消");
                        //取消成功 删除随访计划
                        AppContextHolder.getBean("followUpdateService", FollowUpdateService.class).deleteByAppointRecordId(appointRecordId);
                        return true;
                    }
                    return false;
                }
            } else {
                throw new DAOException("根据医院实际情况，支付完成后不能取消预约，请至医院窗口退费！");
            }
        } else if (hisServiceConfigDao.isServiceEnable(a.getOrganId(), ServiceType.TRANSFER)) {
            boolean hisResult = false;
            if (hisServiceConfigDao.isServiceEnable(a.getOrganId(), ServiceType.TOHIS)//转诊是否调用his
                    || isAppointToHis(a)) {                                            //预约是否调用his
                hisResult = cancelHisAppoint(appointRecordId, cancelResean, a.getWorkDate());
            } else {
                hisResult = true;
            }
            logger.info("cancel his Appoint result:[{}],appointRecordId[{}],cancelResean[{}] ",hisResult,appointRecordId,cancelResean);

//            boolean hisResult = true;
            if (!hisResult) {
//                logger.error("his预约取消失败");
                throw new DAOException(
                        "取消预约失败，请重试，如无法取消，请联系客服咨询，电话:" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL));
            }
            TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
            AppointRecordDAO AppointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            AppointRecord ar = AppointRecordDAO.getByAppointRecordId(appointRecordId);

            if (ar.getTransferId() != null && ar.getTransferId() != 0) {
                Transfer t = transferDAO.getById(ar.getTransferId());
                t.setTransferStatus(9);
                t.setCancelCause(cancelResean);
                if (cancelUser.length() != 32 && cancelUser.length() > 0) {
                    Integer doctorId = Integer.valueOf(cancelUser);
                    Doctor d = DAOFactory.getDAO(DoctorDAO.class).get(doctorId);
                    if (d != null) {
                        //医生取消
                        t.setCancelDoctor(doctorId);
                        t.setCancelOrgan(d.getOrgan());
                        t.setCancelDepart(d.getDepartment());
                    } else {
                        // 客服取消
                        t.setCancelDoctor(doctorId);
                        t.setCancelOrgan(a.getOrganId());
                        t.setCancelDepart(3024);//邵逸夫平台测试科室  取消信息不显示。
                    }

                }
                t.setCancelTime(new Timestamp(new Date().getTime()));
                transferDAO.update(t);
            }
            return doCancelAppointWithFlag(appointRecordId, cancelUser, cancelName, cancelResean, flag);
        } else {
            return doCancelAppointWithFlag(appointRecordId, cancelUser, cancelName, cancelResean, flag);
        }
    }

    /**
     * his退费
     */
    protected HisResponse<String> CancelRegAccount(AppointRecord a) {
        CancelRegAccount cancel = new CancelRegAccount();
        cancel.setPatientID(a.getClinicId());
        cancel.setIDCard(a.getCertId());
        cancel.setMobile(a.getLinkTel());
        cancel.setPatientName(a.getPatientName());
        cancel.setOrganAppointID(a.getOrganAppointId());
        cancel.setOrganizeCode(DAOFactory.getDAO(OrganDAO.class).getOrganizeCodeByOrganId(a.getOrganId()));
        cancel.setRegId(a.getRegId());
        cancel.setRegReceipt(a.getRegReceipt());
        cancel.setPayWay(a.getPayWay());
        cancel.setRefundTradeno(a.getRegReceipt());
        cancel.setPrice(a.getClinicPrice());
        cancel.setPayTradeno(a.getOutTradeNo());
        cancel.setTradeno(a.getTradeNo());
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(a.getOrganId());
        String hisServiceId = cfg.getAppDomainId() + ".cancelRegAccountService";
        boolean s = DBParamLoaderUtil.getOrganSwich(a.getOrganId());
        HisResponse<String> hisRes = new HisResponse<>();
        if(s){
        	IAppointHisService iAppointHisService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
            CancelRegAccountTO to = new CancelRegAccountTO();
            BeanUtils.copy(cancel,to);
            HisResponseTO<String> rr = iAppointHisService.cancelRegAccount(to);
            BeanUtils.copy(rr,hisRes);
            BeanUtils.copy(rr.getData(),hisRes.getData());
        }else{
            hisRes = (HisResponse<String>) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "cancelRegAccount", cancel);
        }
        return hisRes;
    }

    /**
     * 取消预约服务</br> 1.要取消的预约记录是普通预约-->cancel服务，按原来方式取消</br>
     * 2.要取消的预约记录是云门诊预约-->获取到相对应的两条云门诊记录
     * ，循环取消，由于目前没有跟医院的云门诊接口未做，目前暂时只取消平台上的预约记录</br>
     *
     * @param appointRecordId 预约记录主键
     * @param cancelUser      取消人Id
     * @param cancelName      取消人姓名
     * @param cancelResean    取消原因
     * @return
     * @author zhangx
     * @date 2015-10-21上午11:22:46
     * @desc 2016.03.23 解决预约模块中远程转诊取消转诊单不能取消的问题
     */
    @RpcService
    public boolean cancelAppoint(int appointRecordId,
                                 String cancelUser, String cancelName,
                                 String cancelResean) {    	        
        return this.cancelAppointWithFlag(appointRecordId, cancelUser, cancelName, cancelResean, 0);
    }


    /**
     * 取消/撤销预约服务
     *
     * @param appointRecordId
     * @param cancelUser
     * @param cancelName
     * @param cancelResean
     * @param flag            使用来源-0后台1运营平台
     * @return
     */
    @RpcService
    public boolean cancelAppointWithFlag(final int appointRecordId,
                                         final String cancelUser, final String cancelName,
                                         final String cancelResean, final int flag) {
        logger.info("cancelAppoint-->appointRecordId:" + appointRecordId
                + ",cancelUser:" + cancelUser + ",cancelName:" + cancelName
                + ",cancelResean:" + cancelResean);

        final AppointRecord appoint = this.getByAppointRecordId(appointRecordId);
        Boolean cancelFlag = true;
        Integer telClinicFlag = appoint.getTelClinicFlag();
        logger.info("cancelAppoint-->telClinicFlag:" + telClinicFlag);
        if (telClinicFlag == null || telClinicFlag.equals(0)) {
            // 取消普通预约
            cancelFlag = cancelWithFlag(appointRecordId, cancelUser, cancelName, cancelResean, flag);
            //取消预约时删除预约生成的随访计划
            /*try {
                AppContextHolder.getBean("followUpdateService", FollowUpdateService.class).deleteByAppointRecordId(appointRecordId);
            } catch (Exception e) {
                logger.info("after cancelAppoint deleteByAppointRecordId faild and appointRecordId is [{}]", appointRecordId);
            }*/

        } else if (telClinicFlag.equals(1)) {
        	
        	String telClinicId = appoint.getTelClinicId();
            List<AppointRecord> list = findByTelClinicId(telClinicId);
            logger.info("cancelAppoint-->telClinicId:" + telClinicId);
            boolean cancelAppointFlag = true;                    
            // 出诊方预约记录信息发送到his
            RequestAppointService requestAppointService = AppContextHolder.getBean("requestAppointService", RequestAppointService.class);
            AppointRecord appointRecord = requestAppointService.isCloudClinicAppointNeedToHis(list);
            if(appointRecord != null){
            	cancelAppointFlag = false;
            }
            logger.info("cancelAppoint-->cancelAppointFlag:" +cancelAppointFlag);
            if(cancelAppointFlag){
            	//取消预约云门诊
                AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
                    @Override
                    public void execute(StatelessSession ss) throws Exception {
                        String telClinicId = appoint.getTelClinicId();
                        List<AppointRecord> list = findByTelClinicId(telClinicId);
                        logger.info("cancelAppoint-->telClinicId:" + telClinicId);
                        boolean cancelAppointFlag = true;                    
                        // 出诊方预约记录信息发送到his
                        for (AppointRecord appointRecord : list) {
                        	HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                    		HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(appointRecord.getOrganId());                		
                            if (null != cfg && cfg.getCloudClinicNeedToHis() != null && cfg.getCloudClinicNeedToHis() == 1 ) {
                            	cancelAppointFlag = false; 
                            	break;
                            }
                        } 
                        logger.info("cancelAppoint-->cancelAppointFlag:" +cancelAppointFlag);
                        if(cancelAppointFlag){
                            boolean cancelOrNot = false;
                            AppointRecord refundAppointRecord = null;
                        	for (AppointRecord appointRecord : list) {
                                Integer appointStatus = appointRecord.getAppointStatus();
                                Integer clinicObject = appointRecord.getClinicObject();
                                if (appointStatus == null) {
                                    throw new DAOException(DAOException.VALUE_NEEDED, "appointStatus is required!");
                                }
                                // 判断预约记录是否已经取消
                                if (!appointStatus.equals(CloudConstant.APPOINT_SUCCESS) && !appointStatus.equals(CloudConstant.WAITTING_PAY)) {
                                    continue;
                                }

                                Integer appointRecordId = appointRecord.getAppointRecordId();
                                doCancelAppointWithFlag(appointRecordId, cancelUser, cancelName, cancelResean, flag);

                                //取消预约时删除预约生成的随访计划
                                /*if (appointRecord.getClinicObject() == 1) {
                                    try {
                                        AppContextHolder.getBean("followUpdateService", FollowUpdateService.class).deleteByAppointRecordId(appointRecord.getAppointRecordId());
                                    } catch (Exception e) {
                                        logger.info("after cancelAppoint deleteByAppointRecordId faild and appointRecordId is [{}]", appointRecord.getAppointRecordId());
                                    }
                                }*/
                                if (clinicObject!=null && clinicObject.equals(CloudConstant.RECORD_VISIT)) {
                                    refundAppointRecord = appointRecord;
                                    cancelOrNot = true;
                                }
                            }
                            if (cancelOrNot) {
                                //取消预约时进行退款
//                                AppointRecord appointRecord = getByTelClinicIdAndClinicObject(telClinicId, 2);
                                String outTradeNo = refundAppointRecord.getOutTradeNo();
                                if (outTradeNo != null && !StringUtils.isEmpty(outTradeNo)) {
                                    //产生交易订单的需要关闭订单或退款
                                    UnifiedRefundService refundService = AppDomainContext.getBean("eh.unifiedRefundService", UnifiedRefundService.class);
                                    Integer payFlag = refundAppointRecord.getPayFlag();

                                    if (payFlag != null && payFlag.equals(BusPayConstant.PayFlag.PAY_SUCCESS)) {
                                        //支付成功的单子调用退款接口，并更新预约状态和支付状态
                                        Map<String, Object> map = refundService.refund(appointRecordId, BusTypeEnum.APPOINTCLOUD.getCode());
                                        if (map != null && BusPayConstant.Result.SUCCESS.equals(map.get(BusPayConstant.Return.CODE))) {
                                            updatePayFlagAndStatusCloud(BusPayConstant.PayFlag.REFUND_SUCCESS, CloudConstant.REFUNDED, telClinicId);
                                        } else {
                                            updatePayFlagAndStatusCloud(BusPayConstant.PayFlag.REFUND_FAIL, CloudConstant.REFUND_FAIL, telClinicId);
                                        }
                                    } else {
                                        //状态不为已支付的单子需要去支付宝查询支付状态
                                        UnifiedPayService payService = AppDomainContext.getBean("eh.unifiedPayService", UnifiedPayService.class);
                                        Map<String, Object> queryMap = payService.orderQuery(appointRecordId, BusTypeEnum.APPOINTCLOUD.getCode());

                                        if (queryMap != null && BusPayConstant.Result.SUCCESS.equals(queryMap.get(BusPayConstant.Return.CODE))
                                                && BusPayConstant.Result.SUCCESS.equals(queryMap.get(BusPayConstant.Return.TRADE_STATUS))) {
                                            //支付成功的单子调用退款接口，并更新预约状态和支付状态
                                            Map<String, Object> map = refundService.refund(appointRecordId, BusTypeEnum.APPOINTCLOUD.getCode());
                                            if (map != null && BusPayConstant.Result.SUCCESS.equals(map.get(BusPayConstant.Return.CODE))) {
                                                updatePayFlagAndStatusCloud(BusPayConstant.PayFlag.REFUND_SUCCESS, CloudConstant.REFUNDED, telClinicId);
                                            } else {
                                                updatePayFlagAndStatusCloud(BusPayConstant.PayFlag.REFUND_FAIL, CloudConstant.REFUND_FAIL, telClinicId);
                                            }
                                        } else {
                                            Map<String, Object> closed = payService.orderCancel(appointRecordId, BusTypeEnum.APPOINTCLOUD.getCode());
                                            if (closed != null && BusPayConstant.Result.SUCCESS.equals(closed.get(BusPayConstant.Return.CODE))
                                                    && BusPayConstant.Result.REFUND.equals(closed.get(BusPayConstant.Return.ACTION))) {
                                                updatePayFlagAndStatusCloud(BusPayConstant.PayFlag.REFUND_SUCCESS, CloudConstant.REFUNDED, telClinicId);
                                            }
                                        }

                                    }
                                }
                            }
                            setResult(true);
                        } 
                    }
                };
                HibernateSessionTemplate.instance().executeTrans(action);
                cancelFlag = action.getResult();
            	
            }else{
                logger.info("cancelAppoint-->telClinicId:" + telClinicId);
                //throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，该机构不支持取消业务操作，请去医院挂号窗口办理！");
                // 出诊方预约记录信息发送到his，暂时这个接口有点问题关闭
                if(appointRecord != null && appointRecord.getPayFlag() == 0){
            		cancelFlag = cancelHisAppoint(appointRecord.getAppointRecordId(), cancelResean, appointRecord.getWorkDate());
            		logger.info("cancelAppoint-->cancelFlag:" +cancelFlag );
            		updateCancelStatusCloud(cancelResean,telClinicId);
            		// 更新预约号源数
            		AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
                    AppointSource appointSource = appointSourceDAO.getByAppointSourceId(appointRecord.getAppointSourceId());
                    if (appointSource.getUsedNum() > 0) {
                        int usedNum = appointSource.getUsedNum() - 1;
                        appointSourceDAO.updateUsedNumByAppointSourceId(usedNum, appointRecord.getAppointSourceId());
                    }
                    // 更新医生表中的haveAppoint字段
                    DoctorDAO doctordao = DAOFactory.getDAO(DoctorDAO.class);
                    Doctor doctor = doctordao.getByDoctorId(appointRecord.getDoctorId());
                    if (doctor.getHaveAppoint() == 0) {
                        doctordao.updateHaveAppointByDoctorId(appointRecord.getDoctorId(), 1);
                    }
                }
            }

            // 取消有号转诊
            if (cancelFlag) {
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer = transferDAO.get(appoint.getTransferId());
                if (transfer != null) {
                    // 取消人是医生
                    if (cancelUser.length() < 32 && cancelUser.length() > 0) {
                        Integer doctorId = Integer.valueOf(cancelUser);
                        Doctor d = DAOFactory.getDAO(DoctorDAO.class).get(doctorId);
                        transfer.setCancelDoctor(doctorId);
                        transfer.setCancelOrgan(d.getOrgan());
                        transfer.setCancelDepart(d.getDepartment());
                    }
                    transfer.setCancelCause(cancelResean);
                    transfer.setTransferStatus(9);
                    transfer.setCancelTime(DateConversion.convertFromDateToTsp(new Date()));
                    transferDAO.update(transfer);
                }
            }
        }

        return cancelFlag;
    }

    /**
     * 平台内部取消流程
     *
     * @param appointRecordId
     * @param cancelUser
     * @param cancelName
     * @param cancelResean
     * @return
     */
    public Boolean doCancelAppoint(final int appointRecordId,
                                   final String cancelUser, final String cancelName,
                                   final String cancelResean) {
        return this.doCancelAppointWithFlag(appointRecordId, cancelUser, cancelName, cancelResean, 0);
    }

    /**
     * 平台内部取消流程
     *
     * @param appointRecordId
     * @param cancelUser
     * @param cancelName
     * @param cancelResean
     * @param flag            使用来源-0后台1运营平台
     * @return
     */
    public Boolean doCancelAppointWithFlag(final int appointRecordId,
                                   final String cancelUser, final String cancelName,
                                   final String cancelResean,int flag) {
        final AppointRecord a = getByAppointRecordId(appointRecordId);
        final Integer telFlag = a.getTelClinicFlag();
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                AppointSourceDAO AppointSourceDAO = DAOFactory
                        .getDAO(AppointSourceDAO.class);
                if (!"0,4,5,8,1,9".contains(a.getAppointStatus().intValue() + "")) {
                    throw new DAOException(609, "该预约单不可取消或已取消!");
                }
//                if (a.getAppointStatus() > 0 && a.getPayFlag().intValue()!=1) {
//                    throw new DAOException(609, "该预约单不可取消或已取消!");
//                }
                // 更新预约记录表
                logger.info("平台内部预约取消时,更新预约记录表,appointRecordId:" + appointRecordId);
                updateCancel(appointRecordId, new Date(), cancelUser,
                        cancelName, cancelResean);

                // 更新预约号源数
                Integer sourceId = a.getAppointSourceId();
                AppointSource as = AppointSourceDAO.getById(sourceId);
                if (as != null && as.getUsedNum() > 0) {
                    int usedNum = as.getUsedNum() - 1;
                    logger.info("平台内部预约取消时,更新预约号源数,appointSourceId:[{}]", sourceId);
                    if (OrganConstant.WhHanOrganId == as.getOrganId()) {// 武汉
                        usedNum = 0;
                    }
                    if (telFlag!=null && telFlag.equals(1)) {
                        // 预约云门诊当天未支付取消，释放号源
                        AppointSourceDAO.updateUsedNumByAppointSourceId(usedNum,sourceId);
                    }else {
                        AppointSourceDAO.updateUsedNumAfterCancel(usedNum, sourceId);
                    }
                    // 更新医生表中的haveAppoint字段
                    DoctorDAO doctordao = DAOFactory.getDAO(DoctorDAO.class);
                    Doctor doctor = doctordao.getByDoctorId(a.getDoctorId());
                    if (doctor.getHaveAppoint() == 0) {
                        logger.info("平台内部预约取消时,更新医生是否有号源标记,doctorId:" + a.getDoctorId());
                        doctordao.updateHaveAppointByDoctorId(a.getDoctorId(), 1);
                    }
                }

                // 增加账户负收入
                subtractIncome(a);

                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        if (action.getResult()) {
            //取消成功 删除随访计划
            AppContextHolder.getBean("followUpdateService", FollowUpdateService.class).deleteByAppointRecordId(appointRecordId);

            if(flag==1){
                return action.getResult();
            }

            // 更新完成之后，重新获取一下数据,否则一些值还是
            AppointRecord ar = getByAppointRecordId(appointRecordId);

            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            // 发送预约取消短信
            Integer telClinicFlag = ar.getTelClinicFlag();

            if (telClinicFlag != null && telClinicFlag == 0) {
                sendSmsForAppointmentCancel(ar);
//                smsPushService.pushMsgData2Ons(ar.getAppointRecordId(), ar.getOrganId(), "AppCancelMsg", "appointCancel", ar.getDeviceId());
//                //微信推送 消息改造
//                smsPushService.pushMsgData2Ons(ar.getAppointRecordId(), ar.getOrganId(), "SystemCancelAppoint", "doCancelAppoint", ar.getDeviceId());
            } else if (telClinicFlag != null && telClinicFlag.equals(1) && ar.getPayFlag() != null && ar.getPayFlag() > 0 && ar.getClinicObject() != null && ar.getClinicObject().equals(2)) {
                SmsInfo info = new SmsInfo();
                info.setBusId(ar.getAppointRecordId());
                info.setBusType("orderPayAppCancelMsg");
                info.setSmsType("orderPayAppCancelMsg");
                info.setOrganId(ar.getOrganId());
                info.setExtendValue(AppointConstant.APPCANCEL_REQUEST);
                if (ar.getDeviceId() != null){
                    info.setInternalClientId(ar.getDeviceId());
                }
                smsPushService.pushMsgData2OnsExtendValue(info);
            }
             }
        return action.getResult();
    }

    /**
     * his预约或转诊失败 回调更新平台状态
     *
     * @param res
     * @throws DAOException
     */
    @RpcService
    public void cancelForHisFail(final AppointmentResponse res)
            throws DAOException {
        logger.error("his转诊或预约失败返回：" + JSONUtils.toString(res));

        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        final AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        final AppointRecord ar = dao.get(Integer.parseInt(res.getId()));
        if (ar == null) {
//            logger.error("can not find the record by id:" + res.getId());
            throw new DAOException("can not find the record by id:" + res.getId());
        }

        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {

                // 更新预约记录表
                updateCancelBySys(Integer.parseInt(res.getId()), new Date(), "",
                        "预约失败，系统自动取消",
                        res.getErrMsg() == null ? "" : res.getErrMsg());

                if (ar.getAppointRoad() == 5) {// 处理预约失败的情况,需要加回号源
                    if (res.getErrCode() != null
                            && res.getErrCode().trim().equals("3")) {// 3停诊，将该号源排版的所有号源设置为停诊不可用
                        logger.error("his停诊将平台号源相应的更新成停诊：" + JSONUtils.toString(ar));
                        appointSourceDAO.updateStopFlagForSchedulingOpenOrStopAndSendSMS( ar.getOrganId(), ar.getOrganSchedulingId(),1,ar.getWorkType());
//                        appointSourceDAO.updateStopFlagForHisFailed(
//                                ar.getOrganId(), ar.getOrganSchedulingId(),
//                                ar.getWorkType());
                    }
                    if (res.getErrCode() != null && (res.getErrCode().equals("4")
                            || res.getErrCode().trim().equals("3"))) {// 4重复挂号,需要将已用的号源加回去
                        // 更新预约号源数
                        logger.error("his重复挂号或停诊将平台号源已用数加回：" + ar.getAppointRecordId());
                        AppointSource as = appointSourceDAO.getById(ar.getAppointSourceId());
                        if (as.getUsedNum() > 0) {
                            int usedNum = as.getUsedNum() - 1;
                            appointSourceDAO.updateUsedNumAfterCancel(usedNum, ar.getAppointSourceId());
                        }

                        // 更新医生表中的haveAppoint字段
                        DoctorDAO doctordao = DAOFactory.getDAO(DoctorDAO.class);
                        Doctor doctor = doctordao.getByDoctorId(ar.getDoctorId());
                        if (doctor.getHaveAppoint() == 0) {
                            doctordao.updateHaveAppointByDoctorId(ar.getDoctorId(), 1);
                        }
                    }
                }

                Integer transId = ar.getTransferId();
                if (transId != null && transId != 0) {

                    // 处理转诊失败的情况
                    TransferDAO transferdao = DAOFactory.getDAO(TransferDAO.class);

                    // 更新转诊单状态为7 his转诊失败
                    transferdao.updateTransferFailed(transId, res.getErrMsg() == null ? "" : res.getErrMsg());
                }
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().execute(action);

        if (action.getResult()) {
            logger.info("his转诊或预约失败，向患者发送短信或系统通知");

            // 预约失败,给申请医生发送系统消息
            AppointRecord record = dao.get(Integer.parseInt(res.getId()));

            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            if (record.getAppointRoad() == 5) {
                // 判断是否errCode=2：序号重复需要重新预约
                if (res.getErrCode()!=null&&res.getErrCode().trim().equals("2")) {
                    List<AppointSource> sourceList;
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(record.getStartTime());
                    int hours = cal.get(Calendar.HOUR_OF_DAY);
                    if (hours < 12) {
                        sourceList = appointSourceDAO.findAppointSourceByDocAndWorkDateAM(record.getDoctorId(), record.getWorkDate(), record.getStartTime());// 获取有效的号源
                    } else {
                        sourceList = appointSourceDAO.findAppointSourceByDocAndWorkDatePM(record.getDoctorId(), record.getWorkDate(), record.getStartTime());// 获取有效的号源
                    }

                    if (sourceList != null && sourceList.size() > 0) {
                        int AppointRecordId = record.getAppointRecordId();
                        AppointSource source = sourceList.get(0);
                        logger.info("auto appoint next appointSource:" + source.getOrganSourceId());
                        source.setUsedNum(1);
                        // 取到号源后将该号源的UsedNum设置为1
                        appointSourceDAO.update(source);

                        //将更新前的数据重新设置号源信息，更新数据库
                        //2017-4-7 18:10:26 zhangx
                        ar.setAppointSourceId(source.getAppointSourceId());
                        ar.setOrganId(source.getOrganId());
                        ar.setAppointDepartId(source.getAppointDepartCode());
                        ar.setAppointDepartName(source.getAppointDepartName());
                        ar.setDoctorId(source.getDoctorId());
                        ar.setWorkDate(source.getWorkDate());
                        ar.setWorkType(source.getWorkType());
                        ar.setSourceType(source.getSourceType());
                        ar.setStartTime(source.getStartTime());
                        ar.setOrderNum(source.getOrderNum());
                        ar.setEndTime(source.getEndTime());
                        ar.setClinicPrice(source.getPrice());
                        ar.setSourceLevel(source.getSourceLevel());
                        ar.setAppointDate(new Date());
                        ar.setAppointStatus(9);
                        ar.setRecordType(ar.getRecordType() == null ? 0 : ar.getRecordType());


                        try {
                            update(ar);
                        }catch (Exception e){
                           logger.error("cancelForHisFail-->"+e);
                        }
                        reTryAppoint(AppointRecordId);
                    } else {
                        //预约his失败消息改造
                        sendFailSms(record, res);
                    }
                } else {
                    sendFailSms(record, res);
                }
            } else {
                // 转诊失败
                int transId = record.getTransferId();
                TransferDAO transferdao = DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer=transferdao.get(transId);
                transferdao.sendConfirmHisFailedMsg(transfer);
            }

        }
    }

    private void sendFailSms(AppointRecord record, AppointmentResponse res) {
        String busType="";
        String appointUser=StringUtils.isEmpty(record.getAppointUser())?"":record.getAppointUser();
        if(appointUser.length()<32){
            busType="DocAppointHisFail";
        }else{
            int recordType=record.getRecordType()==null?0:record.getRecordType().intValue();
            if(recordType==0){
                busType="PatAppointHisFail";
            }else {
                busType="PatRegisterHisFail";
            }
        }

        if(StringUtils.isEmpty(busType)){
            logger.info("未判断出业务["+record.getAppointRecordId()+"]的消息发送场景，不能发送消息");
            return;
        }

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(record.getAppointRecordId());
        smsInfo.setOrganId(record.getOrganId());
        smsInfo.setBusType(busType);
        smsInfo.setSmsType(busType);
        smsInfo.setClientId(record.getDeviceId());
        smsInfo.setExtendValue(res.getErrCode());
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }

    /**
     * His预约挂号取消服务
     *
     * @param appointRecordId
     * @param cancelResean
     * @return
     */
    public boolean cancelHisAppoint(int appointRecordId, String cancelResean, Date workDate) {
        AppointRecordDAO AppointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord ar = AppointRecordDAO.getByAppointRecordId(appointRecordId);
        String appointID = ar.getOrganAppointId();
        String organId = ar.getOrganId() + "";
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(ar.getOrganId());
        String hisServiceId = cfg.getAppDomainId() + ".appointmentService";// 调用服务id

//        IHisServiceInterface cancelAppointService = AppContextHolder.getBean(hisServiceId, IHisServiceInterface.class);
        AppointCancelRequest req = new AppointCancelRequest();
        req.setTelClinicFlag(ar.getTelClinicFlag());
        req.setPatientId(ar.getClinicId());
        req.setAppointRecordId(appointRecordId);
        req.setAppointId(appointID);
        req.setCancelReason(cancelResean);
        req.setOrganId(organId);
        req.setWorkDate(ar.getWorkDate());
        req.setTransferId(ar.getTransferId());
        req.setMobile(ar.getLinkTel());
        req.setPatientName(ar.getPatientName());
        req.setAppointSourceId(ar.getAppointSourceId());
        req.setCardNum(ar.getCertId());
        req.setCardID(ar.getCardId());
        req.setCardType(ar.getCardType());
        String certID = ar.getCertId();
        String c = LocalStringUtil.getSubstringByDiff(certID, "-");
        req.setCardNum(c);
        Patient p = DAOFactory.getDAO(PatientDAO.class).getByMpiId(ar.getMpiid());
        req.setGuardianFlag(p.getGuardianFlag());
        logger.info("send cancel request to his:" + JSONUtils.toString(req));
//        boolean flag = cancelAppointService.cancelAppoint(req);
        boolean s = DBParamLoaderUtil.getOrganSwich(ar.getOrganId());
        boolean flag =false;
        if(s){
        	IAppointHisService appointService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
            AppointCancelRequestTO request = new AppointCancelRequestTO();
            logger.info("send cancel request to his,req=" +req.toString());
            BeanUtils.copy(req,request);
            logger.info("send cancel request to his,request=" +request);
            HisResponseTO<Object> res = appointService.cancelAppoint(request);
            logger.info("send cancel request to his,res=" +res);
            flag = res.isSuccess();
            logger.info(ar.getAppointRecordId()+"取消"+ (flag?"成功":"失败:"+res.getMsg()));
        }else{            
            flag = (boolean) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "cancelAppoint", req);
        }
        return flag;
    }

    /**
     * His预约挂号取消服务
     *
     * @param ar
     * @return
     */
    public boolean cancelHisAppoint(AppointRecord ar) {
        AppointCancelRequest req = new AppointCancelRequest();
        req.setAppointId(ar.getOrganAppointId());
        req.setCancelReason(ar.getCancelResean());
        req.setAppointRecordId(ar.getAppointRecordId());
        req.setOrganId(ar.getOrganId() + "");
        String certID = ar.getCertId();
        String c = LocalStringUtil.getSubstringByDiff(certID, "-");
        req.setCardNum(c);
        Patient p = DAOFactory.getDAO(PatientDAO.class).getByMpiId(ar.getMpiid());
        req.setGuardianFlag(p.getGuardianFlag());
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(ar.getOrganId());
        String hisServiceId = cfg.getAppDomainId() + ".appointmentService";
//        IHisServiceInterface transferService = AppContextHolder.getBean(hisServiceId, IHisServiceInterface.class);
        boolean s = DBParamLoaderUtil.getOrganSwich(ar.getOrganId());
        boolean flag =false;
        if(s){
        	IAppointHisService iAppointHisService = AppContextHolder.getBean("his.iAppointHisService", IAppointHisService.class);
            AppointCancelRequestTO request = new AppointCancelRequestTO();
            BeanUtils.copy(req,request);
            HisResponseTO res = iAppointHisService.cancelAppoint(request);
            flag = res.isSuccess();
            logger.info(ar.getAppointRecordId()+"取消"+ (flag?"成功":"失败:"+res.getMsg()));
        }else{
            flag = (boolean) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, hisServiceId, "cancelAppoint", req);

        }
        return flag;
    }

    private void isValidAppointRecordData(AppointRecord p) {
        if (StringUtils.isEmpty(p.getMpiid())) {
//            logger.error("mpiid is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (StringUtils.isEmpty(p.getPatientName())) {
//            logger.error("patientName is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientName is required");
        }
        if (p.getAppointSourceId() == null) {
//            logger.error("appointSourceId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointSourceId is required");
        }
        if (p.getOrganId() == null) {
//            logger.error("organId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }
        // if (StringUtils.isEmpty(p.getAppointDepartId())) {
        // logger.error("appointDepartId is required");
        // throw new DAOException(DAOException.VALUE_NEEDED,
        // "appointDepartId is required");
        // }
        if (p.getWorkDate() == null) {
//            logger.error("workDate is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "workDate is required");
        }
        if (p.getWorkType() == null) {
//            logger.error("workType is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "workType is required");
        }
        if (p.getSourceType() == null) {
//            logger.error("sourceType is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "sourceType is required");
        }
        if (p.getStartTime() == null) {
//            logger.error("startTime is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "startTime is required");
        }
        if (p.getEndTime() == null) {
//            logger.error("endTime is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "endTime is required");
        }
        if (p.getOrderNum() == null) {
//            logger.error("orderNum is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "orderNum is required");
        }
        if (p.getAppointRoad() == null) {
//            logger.error("appointRoad is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointRoad is required");
        }
        if (p.getAppointStatus() == null) {
//            logger.error("appointStatus is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointStatus is required");
        }
        if (SameUserMatching.patientAndDoctor(p.getMpiid(), p.getDoctorId())) {
//            logger.error("患者与目标医生不能为同一个人");
            throw new DAOException(609, "患者与目标医生不能为同一个人");
        }
    }

    /**
     * 在线云门诊数据校验
     *
     * @param record
     * @author zhangx
     * @date 2015-12-28 下午8:50:20
     */
    private void isValidOnlineAppointRecordData(AppointRecord record) {
        if (record == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "record is required");
        }

        if (StringUtils.isEmpty(record.getMpiid())) {
//            logger.error("mpiid is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiid is required");
        }
        if (StringUtils.isEmpty(record.getPatientName())) {
//            logger.error("patientName is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientName is required");
        }

        if (StringUtils.isEmpty(record.getLinkTel())) {
//            logger.error("linkTel is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "linkTel is required");
        }
        if (StringUtils.isEmpty(record.getCertId())) {
//            logger.error("certId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "certId is required");
        }
        if (StringUtils.isEmpty(record.getAppointUser())) {
//            logger.error("appointUser is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "appointUser is required");
        }
        if (record.getDoctorId() == null) {
//            logger.error("doctorId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
        if (StringUtils.isEmpty(record.getTelClinicId())) {
//            logger.error("telClinicId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "telClinicId is required");
        }

        if (SameUserMatching.patientAndDoctor(record.getMpiid(),
                record.getDoctorId())) {
            throw new DAOException(609, "患者和预约医生不能为同一人");
        }
    }

    /**
     * 预约申请列表查询服务
     *
     * @param appointUser
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) from AppointRecord a,Patient b where a.appointUser=:appointUser and b.mpiId=a.mpiid order by a.appointDate desc")
    public abstract List<AppointRecordAndPatient> findRequestAppointRecord(
            @DAOParam("appointUser") String appointUser);

    /**
     * 预约申请列表查询服务--分页
     *
     * @param appointUser
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(limit = 10, sql = "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) from AppointRecord a,Patient b where a.appointUser=:appointUser and b.mpiId=a.mpiid order by a.appointDate desc")
    public abstract List<AppointRecordAndPatient> findRequestAppointRecordWithPage(
            @DAOParam("appointUser") String appointUser,
            @DAOParam(pageStart = true) int start);

    /**
     * 预约申请列表查询服务--分页
     *
     * @param appointUser 申请医生编号
     * @param start       记录起始位置
     * @param limit       查询记录数
     * @return
     * @author yaozh
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) from AppointRecord a,Patient b where a.appointUser=:appointUser and b.mpiId=a.mpiid order by a.appointDate desc")
    public abstract List<AppointRecordAndPatient> findRequestAppointRecordWithPageLimit(
            @DAOParam("appointUser") String appointUser,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 预约申请列表查询服务--分页(远程门诊只取出诊方的那条记录)
     *
     * @param appointUser 申请医生编号
     * @return
     * @author yaozh
     */
    @RpcService
    public List<AppointRecordAndPatient> findRequestAppointRecordByPage(
            final String appointUser, final int start, final int limit) {
        HibernateStatelessResultAction<List<AppointRecordAndPatient>> action = new AbstractHibernateStatelessResultAction<List<AppointRecordAndPatient>>() {
            List<AppointRecordAndPatient> result = new ArrayList<AppointRecordAndPatient>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) from AppointRecord a,Patient b where a.appointUser=:appointUser and b.mpiId=a.mpiid and (a.telClinicFlag=0 or a.telClinicFlag is null  or (a.telClinicFlag = 1 and a.clinicObject = 2) or (a.telClinicFlag = 2 and a.clinicObject = 2)) order by a.appointDate desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("appointUser", appointUser);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<AppointRecordAndPatient> arps = action.getResult();
        List<AppointRecordAndPatient> results = new ArrayList<AppointRecordAndPatient>();
        if (arps == null || arps.isEmpty()) {
            return results;
        }
        for (AppointRecordAndPatient arp : arps) {
            AppointRecord ar = arp.getAppointrecord();
            ar.setRequestDate(DateConversion.convertRequestDateForBuss(ar.getAppointDate()));
            arp.setAppointrecord(ar);
            results.add(arp);
        }
        return results;
    }

    /**
     * 病人挂号记录查询服务
     *
     * @param mpiId
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.bus.AppointRecordAndDoctor(a,b) from AppointRecord a,Doctor b where a.mpiid=:mpiId and a.doctorId=b.doctorId order by a.appointDate desc")
    public abstract List<AppointRecordAndDoctor> findAppointRecord(
            @DAOParam("mpiId") String mpiId);

    /**
     * 根据云门诊序号和就诊状况查询预约记录
     *
     * @param telClinicId
     * @param clinicStatus
     * @return
     */
    @RpcService
    @DAOMethod(sql = "from AppointRecord where telClinicId = :telClinicId and  clinicStatus= :clinicStatus ")
    public abstract List<AppointRecord> findCloudAppointRecord(
            @DAOParam("telClinicId") String telClinicId,
            @DAOParam("clinicStatus") Integer clinicStatus);

    /**
     * 病人挂号记录查询服务--分页
     *
     * @param mpiId
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(limit = 10, sql = "select new eh.entity.bus.AppointRecordAndDoctor(a,b) from AppointRecord a,Doctor b where a.mpiid=:mpiId and a.doctorId=b.doctorId order by a.appointDate desc")
    public abstract List<AppointRecordAndDoctor> findAppointRecordWithPage(
            @DAOParam("mpiId") String mpiId,
            @DAOParam(pageStart = true) int start);

    /**
     * 获取每日所有医生的总预约数--zsq
     *
     * @param appointDate
     * @return
     */
    @RpcService
    @DAOMethod(sql = "SELECT COUNT(*) FROM AppointRecord WHERE DATE(appointDate) = DATE(:appointDate) and appointRoad=:appointRoad and appointStatus=:appointStatus")
    public abstract long getAllNowAppointRecordNum(
            @DAOParam("appointDate") Date appointDate,
            @DAOParam("appointRoad") Integer appointRoad,
            @DAOParam("appointStatus") Integer appointStatus);



    /**
     * 查询当日新增预约记录
     *
     * @param doctorId
     * @return
     * @author hyj
     */
    @RpcService
    public List<AppointRecordAndPatientAndDoctor> findTodayAppointRecord(
            final int doctorId) {
        HibernateStatelessResultAction<List<AppointRecordAndPatientAndDoctor>> action = new AbstractHibernateStatelessResultAction<List<AppointRecordAndPatientAndDoctor>>() {
            List<AppointRecordAndPatientAndDoctor> result = new ArrayList<AppointRecordAndPatientAndDoctor>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndPatientAndDoctor(a,b.patientSex,b.birthday,b.patientType,c.profession,c.proTitle,b.photo) from AppointRecord a,Patient b,Doctor c where a.doctorId=:doctorId and DATE(a.appointDate)=DATE(:appointDate) and b.mpiId=a.mpiid and c.doctorId=a.doctorId and a.appointStatus in(0,1)");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                Date date = Context.instance().get("date.getToday", Date.class);
                q.setParameter("appointDate", date);
                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询当日新增预约记录--分页
     *
     * @param doctorId
     * @return
     * @author hyj
     */
    @RpcService
    public List<AppointRecordAndPatientAndDoctor> findTodayAppointRecordWithPage(
            final int doctorId, final int start) {
        return findTodayAppointRecordWithPageLimit(doctorId, start, 10);
    }

    /**
     * 查询当日新增预约记录--分页
     *
     * @param doctorId 医生编号
     * @param start    记录起始位置
     * @param limit    查询记录数
     * @return
     * @author yaozh
     */
    @RpcService
    public List<AppointRecordAndPatientAndDoctor> findTodayAppointRecordWithPageLimit(
            final int doctorId, final int start, final int limit) {
        HibernateStatelessResultAction<List<AppointRecordAndPatientAndDoctor>> action = new AbstractHibernateStatelessResultAction<List<AppointRecordAndPatientAndDoctor>>() {
            List<AppointRecordAndPatientAndDoctor> result = new ArrayList<AppointRecordAndPatientAndDoctor>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndPatientAndDoctor(a,b.patientSex,b.birthday,b.patientType,c.profession,c.proTitle,b.photo) from AppointRecord a,Patient b,Doctor c where a.doctorId=:doctorId and DATE(a.appointDate)=:appointDate and b.mpiId=a.mpiid and c.doctorId=a.doctorId and a.appointStatus in(0,1) order by a.workDate asc,a.startTime asc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                Date date = Context.instance().get("date.getToday", Date.class);
                q.setParameter("appointDate", date);
                q.setMaxResults(limit);
                q.setFirstResult(start);
                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<AppointRecordAndPatientAndDoctor> arpds = action.getResult();
        List<AppointRecordAndPatientAndDoctor> results = new ArrayList<AppointRecordAndPatientAndDoctor>();
        if (arpds == null || arpds.isEmpty()) {
            return results;
        }
        for (AppointRecordAndPatientAndDoctor arpd : arpds) {
            AppointRecord ar = arpd.getAppointrecord();
            ar.setRequestDate(DateConversion.convertRequestDateForBuss(ar.getStartTime()));
            arpd.setAppointrecord(ar);
            results.add(arpd);
        }
        return results;
    }

    /**
     * 预约统计查询
     *
     * @param startTime
     * @param endTime
     * @param ar
     * @param start
     * @return
     * @author ZX
     * @date 2015-5-18 下午8:09:59
     */
    @RpcService
    public List<AppointRecordAndDoctors> findAppointRecordWithStatic(
            final Date startTime, final Date endTime, final AppointRecord ar,
            final int start) {

        if (startTime == null) {
//            logger.error("统计开始时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
//            logger.error("统计结束时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<List<AppointRecordAndDoctors>> action = new AbstractHibernateStatelessResultAction<List<AppointRecordAndDoctors>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndDoctors(a) from AppointRecord a where DATE(a.appointDate)>=DATE(:startTime) and DATE(a.appointDate)<=DATE(:endTime) ");

                // 添加申请机构条件
                if (!StringUtils.isEmpty(ar.getAppointOragn())) {
                    hql.append(" and a.appointOragn='" + ar.getAppointOragn()
                            + "'");
                }

                // 添加目标机构条件
                if (ar.getOrganId() != null) {
                    hql.append(" and a.organId=" + ar.getOrganId());
                }

                // 添加申请医生条件
                if (!StringUtils.isEmpty(ar.getAppointName())) {
                    hql.append(" and a.appointName='" + ar.getAppointName()
                            + "'");
                }

                // 添加目标医生条件
                if (ar.getDoctorId() != null) {
                    hql.append(" and a.doctorId=" + ar.getDoctorId());
                }

                // 添加预约单状态
                if (ar.getAppointStatus() != null) {
                    hql.append(" and a.appointStatus=" + ar.getAppointStatus());
                }

                // 添加预约方式
                if (ar.getAppointRoad() != null) {
                    hql.append(" and a.appointRoad=" + ar.getAppointRoad());
                }

                hql.append(" order by a.appointDate desc");

                Query query = ss.createQuery(hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                query.setFirstResult(start);
                query.setMaxResults(10);

                List<AppointRecordAndDoctors> tfList = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

                for (AppointRecordAndDoctors appointRecordAndDoctors : tfList) {

                    // 申请电话
                    String requestId = appointRecordAndDoctors
                            .getAppointrecord().getAppointUser();
                    if (requestId.length() > 11) {
                        Patient pt = patientDAO.getByMpiId(requestId);
                        if (!StringUtils.isEmpty(pt.getMobile())) {
                            appointRecordAndDoctors.setRequestTel(pt
                                    .getMobile());
                        }

                    } else {
                        int requestDocId = Integer.parseInt(requestId);
                        Doctor reqDoctor = doctorDAO
                                .getByDoctorId(requestDocId);
                        if (!StringUtils.isEmpty(reqDoctor.getMobile())) {
                            appointRecordAndDoctors.setRequestTel(reqDoctor
                                    .getMobile());
                        }
                    }

                    // 目标医生电话
                    int targerDocId = appointRecordAndDoctors
                            .getAppointrecord().getDoctorId();
                    Doctor targetDoctor = doctorDAO.getByDoctorId(targerDocId);
                    if (!StringUtils.isEmpty(targetDoctor.getMobile())) {
                        appointRecordAndDoctors.setTargerDocTel(targetDoctor
                                .getMobile());
                    }
                }
                setResult(tfList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointRecordAndDoctors>) action.getResult();
    }

    /**
     * 预约统计查询
     *
     * @param startTime
     * @param endTime
     * @param ar
     * @param start
     * @return
     * @author ZX
     * @date 2015-5-18 下午8:09:59
     */
    @RpcService
    public List<AppointRecordAndDoctors> findAppointRecordWithStatic2(
            final Date startTime, final Date endTime, final AppointRecord ar,
            final int start) {

        if (startTime == null) {
//            logger.error("统计开始时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
//            logger.error("统计结束时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<List<AppointRecordAndDoctors>> action = new AbstractHibernateStatelessResultAction<List<AppointRecordAndDoctors>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndDoctors(a) from AppointRecord a where DATE(a.appointDate)>=DATE(:startTime) and DATE(a.appointDate)<=DATE(:endTime) ");

                // 添加申请机构条件
                if (!StringUtils.isEmpty(ar.getAppointOragn())) {
                    hql.append(" and a.appointOragn='" + ar.getAppointOragn()
                            + "'");
                }

                // 添加目标机构条件
                if (ar.getOrganId() != null) {
                    hql.append(" and a.organId=" + ar.getOrganId());
                }

                // 添加申请医生条件
                if (!StringUtils.isEmpty(ar.getAppointUser())) {
                    hql.append(" and a.appointUser='" + ar.getAppointUser()
                            + "'");
                }

                // 添加目标医生条件
                if (ar.getDoctorId() != null) {
                    hql.append(" and a.doctorId=" + ar.getDoctorId());
                }

                // 添加预约单状态
                if (ar.getAppointStatus() != null) {
                    hql.append(" and a.appointStatus=" + ar.getAppointStatus());
                }

                // 添加预约方式
                if (ar.getAppointRoad() != null) {
                    hql.append(" and a.appointRoad=" + ar.getAppointRoad());
                }

                hql.append(" order by a.appointDate desc");

                Query query = ss.createQuery(hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                query.setFirstResult(start);
                query.setMaxResults(10);

                List<AppointRecordAndDoctors> tfList = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

                for (AppointRecordAndDoctors appointRecordAndDoctors : tfList) {

                    // 申请电话
                    String requestId = appointRecordAndDoctors
                            .getAppointrecord().getAppointUser();
                    if (requestId.length() > 11) {
                        Patient pt = patientDAO.getByMpiId(requestId);
                        if (!StringUtils.isEmpty(pt.getMobile())) {
                            appointRecordAndDoctors.setRequestTel(pt
                                    .getMobile());
                        }

                    } else {
                        int requestDocId = Integer.parseInt(requestId);
                        Doctor reqDoctor = doctorDAO
                                .getByDoctorId(requestDocId);
                        if (!StringUtils.isEmpty(reqDoctor.getMobile())) {
                            appointRecordAndDoctors.setRequestTel(reqDoctor
                                    .getMobile());
                        }
                    }

                    // 目标医生电话
                    int targerDocId = appointRecordAndDoctors
                            .getAppointrecord().getDoctorId();
                    Doctor targetDoctor = doctorDAO.getByDoctorId(targerDocId);
                    if (!StringUtils.isEmpty(targetDoctor.getMobile())) {
                        appointRecordAndDoctors.setTargerDocTel(targetDoctor
                                .getMobile());
                    }
                }
                setResult(tfList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointRecordAndDoctors>) action.getResult();
    }

    /**
     * 预约统计查询记录数
     *
     * @param startTime
     * @param endTime
     * @param ar
     * @return
     * @author ZX
     * @date 2015-5-18 下午8:09:46
     */
    @RpcService
    public long getNumWithStatic(final Date startTime, final Date endTime,
                                 final AppointRecord ar) {

        if (startTime == null) {
//            logger.error("统计开始时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
//            logger.error("统计结束时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select count(*) from AppointRecord a where DATE(a.appointDate)>=DATE(:startTime) and DATE(a.appointDate)<=DATE(:endTime) ");

                // 添加申请机构条件
                if (!StringUtils.isEmpty(ar.getAppointOragn())) {
                    hql.append(" and a.appointOragn=" + ar.getAppointOragn());
                }

                // 添加目标机构条件
                if (ar.getOrganId() != null) {
                    hql.append(" and a.organId=" + ar.getOrganId());
                }

                // 添加申请医生条件
                if (!StringUtils.isEmpty(ar.getAppointName())) {
                    hql.append(" and a.appointName='" + ar.getAppointName()
                            + "'");
                }

                // 添加目标医生条件
                if (ar.getDoctorId() != null) {
                    hql.append(" and a.doctorId=" + ar.getDoctorId());
                }

                // 添加预约单状态
                if (ar.getAppointStatus() != null) {
                    hql.append(" and a.appointStatus=" + ar.getAppointStatus());
                }

                // 添加预约方式
                if (ar.getAppointRoad() != null) {
                    hql.append(" and a.appointRoad=" + ar.getAppointRoad());
                }

                hql.append(" order by a.appointDate desc");

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
     * 按申请机构统计预约数
     *
     * @param manageUnit
     * @param startDate
     * @param endDate
     * @return
     * @author hyj
     */
    @RpcService
    public Long getRequestNumFromTo(final String manageUnit,
                                    final Date startDate, final Date endDate) {
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
                        "select count(*) from AppointRecord a ,Organ o where a.appointOragn = o.organId and o.manageUnit like :manageUnit and  (a.appointDate between :startTime and :endTime) and a.appointStatus !=2 and a.transferId=0");

                Query query = ss.createQuery(hql.toString());
                query.setString("manageUnit", manageUnit + "%");
                Date sTime = DateConversion.firstSecondsOfDay(startDate);
                Date eTime = DateConversion.lastSecondsOfDay(endDate);
                query.setDate("startTime", sTime);
                query.setDate("endTime", eTime);

                long num = (long) query.uniqueResult();

                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();

    }

    /**
     * 按预约机构统计预约数
     *
     * @param manageUnit
     * @param startDate
     * @param endDate
     * @return
     * @author hyj
     */
    @RpcService
    public Long getTargetNumFromTo(final String manageUnit,
                                   final Date startDate, final Date endDate) {
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
                        "select count(*) from AppointRecord a ,Organ o where a.organId = o.organId and o.manageUnit like :manageUnit and  DATE(a.appointDate)>=DATE(:startTime) and DATE(a.appointDate)<=DATE(:endTime) and a.appointStatus !=2 and a.transferId=0");

                Query query = ss.createQuery(hql.toString());
                query.setString("manageUnit", manageUnit + "%");
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
     * 统计申请机构昨日预约数
     *
     * @param manageUnit
     * @return
     * @author hyj
     */
    @RpcService
    public Long getRequestNumForYesterday(String manageUnit) {
        Date date = Context.instance().get("date.getYesterday", Date.class);
        return getRequestNumFromTo(manageUnit, date, date);
    }

    /**
     * 统计申请机构今日预约数
     *
     * @param manageUnit
     * @return
     * @author hyj
     */
    @RpcService
    public Long getRequestNumForToday(String manageUnit) {
        Date date = Context.instance().get("date.getToday", Date.class);
        return getRequestNumFromTo(manageUnit, date, date);
    }

    /**
     * 统计预约机构昨日预约数
     *
     * @param manageUnit
     * @return
     * @author hyj
     */
    @RpcService
    public Long getTargetNumForYesterday(String manageUnit) {
        Date date = Context.instance().get("date.getYesterday", Date.class);
        return getTargetNumFromTo(manageUnit, date, date);
    }

    /**
     * 统计预约机构今日预约数
     *
     * @param manageUnit
     * @return
     * @author hyj
     */
    @RpcService
    public Long getTargetNumForToday(String manageUnit) {
        Date date = Context.instance().get("date.getToday", Date.class);
        return getTargetNumFromTo(manageUnit, date, date);
    }

    /**
     * 云门诊预约记录增加服务
     *
     * @param appointRecords
     * @return
     * @author hyj
     * @desc 由于前端未给远程预约传就诊地址，目前统一在后台根据号源id取就诊地址 zhangjr 2016-04-19
     */
    @RpcService
    public boolean addAppointRecordForCloudClinic(
            List<AppointRecord> appointRecords) {
        RequestAppointService requestAppointService = new RequestAppointService();
        Integer result = requestAppointService.addCloudClinicOrderPay(appointRecords);
        if (null == result || 0 >= result) {
            return false;
        }
        return true;
    }

    /**
     * 创建一个TelClinicId值
     *
     * @return
     * @author yaozh
     */
    @RpcService
    public String getTelClinicId() {
        return String.valueOf(System.currentTimeMillis())
                + String.valueOf(ThreadLocalRandom.current()
                .nextInt(1000, 9999));
    }

    /**
     * 在线云门诊
     *
     * @param record
     * @return
     * @throws ControllerException
     * @author zhangx
     * @date 2015-12-28 上午11:26:45
     */
    @RpcService
    public void addAppointRecordForOnlineCloudClinic(AppointRecord record)
            throws ControllerException {
        logger.info("在线云门诊前端传入数据--->" + JSONUtils.toString(record));

        isValidOnlineAppointRecordData(record);

        String telClinicId = record.getTelClinicId();
        List<AppointRecord> ars = findByTelClinicId(telClinicId);
        if (ars != null && !ars.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "online cloudClinic has already added!");
        }
        final List<AppointRecord> records = getOnlineAppointRecords(record);

        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                for (AppointRecord appointRecord : records) {
                    save(appointRecord);
                }
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        Boolean bool = action.getResult();

        if (bool) {
            for (final AppointRecord appointRecord : records) {
                // 保存日志
                OperationRecordsDAO operationRecordsDAO = DAOFactory
                        .getDAO(OperationRecordsDAO.class);
                operationRecordsDAO
                        .saveOperationRecordsForAppoint(appointRecord);
            }

            // 预约申请，给予推荐奖励,不管预约是否成功
            if (records.size() > 0 && records.get(0).getAppointRoad() == 5
                    && records.get(0).getAppointUser().length() != 32) {
                DoctorAccountDAO accDao = DAOFactory
                        .getDAO(DoctorAccountDAO.class);
                accDao.recommendReward(Integer.parseInt(records.get(0)
                        .getAppointUser()));
            }
        }

    }

    /**
     * 生成在线云门诊基础数据
     *
     * @param r
     * @return
     * @throws ControllerException
     * @author zhangx
     * @date 2015-12-28 下午6:34:01
     */
    private List<AppointRecord> getOnlineAppointRecords(AppointRecord r)
            throws ControllerException {

        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);

        // 接诊方
        Integer acceptsDocId = Integer.parseInt(r.getAppointUser());
        Doctor acceptsDoc = docDao.getByDoctorId(acceptsDocId);
        String acceptsDocName = acceptsDoc.getName();
        Employment acceptsEmp = empDao.getPrimaryEmpByDoctorId(acceptsDocId);
        Integer acceptsOrgan = acceptsEmp.getOrganId();
        String acceptsDept = acceptsEmp.getDepartment().toString();
        String acceptsDeptName = DictionaryController.instance()
                .get("eh.base.dictionary.Depart").getText(acceptsDept);

        // 出诊方
        Integer visitsDocId = r.getDoctorId();
        Employment visitsEmp = empDao.getPrimaryEmpByDoctorId(visitsDocId);
        Integer visitsOrgan = visitsEmp.getOrganId();
        String visitsDept = visitsEmp.getDepartment().toString();
        String visitsDeptName = DictionaryController.instance()
                .get("eh.base.dictionary.Depart").getText(visitsDept);

        // 组装出诊方数据
        AppointRecord visitsRecord = getOnlineData(r);
        visitsRecord.setClinicObject(2);
        visitsRecord.setDoctorId(visitsDocId);
        visitsRecord.setOrganId(visitsOrgan);
        visitsRecord.setAppointDepartId(visitsDept);
        visitsRecord.setAppointDepartName(visitsDeptName);
        visitsRecord.setOppType(1);
        visitsRecord.setOppOrgan(acceptsOrgan);
        visitsRecord.setOppdepart(acceptsDept);
        visitsRecord.setOppdepartName(acceptsDeptName);
        visitsRecord.setOppdoctor(acceptsDocId);
        visitsRecord.setAppointName(acceptsDocName);
        visitsRecord.setAppointOragn(acceptsOrgan.toString());
        visitsRecord.setPayFlag(0);//在线云门诊暂不支持付费 zhangsl 2017-05-16 17:32:35
        visitsRecord.setEvaStatus(0);//评价状态 zhangsl 2017-02-13 19:28:49

        // 组装接诊方数据
        AppointRecord acceptsRecord = getOnlineData(r);
        acceptsRecord.setClinicObject(1);
        acceptsRecord.setDoctorId(acceptsDocId);
        acceptsRecord.setOrganId(acceptsOrgan);
        acceptsRecord.setAppointDepartId(acceptsDept);
        acceptsRecord.setAppointDepartName(acceptsDeptName);
        acceptsRecord.setOppType(1);
        acceptsRecord.setOppOrgan(visitsOrgan);
        acceptsRecord.setOppdepart(visitsDept);
        acceptsRecord.setOppdepartName(visitsDeptName);
        acceptsRecord.setOppdoctor(visitsDocId);
        acceptsRecord.setAppointName(acceptsDocName);
        acceptsRecord.setAppointOragn(acceptsOrgan.toString());
        acceptsRecord.setPayFlag(0);//在线云门诊暂不支持付费 zhangsl 2017-05-16 17:32:35
        acceptsRecord.setEvaStatus(0);//评价状态 zhangsl 2017-02-13 19:28:49

        List<AppointRecord> records = new ArrayList<AppointRecord>();
        records.add(visitsRecord);
        records.add(acceptsRecord);

        return records;
    }

    @SuppressWarnings("deprecation")
    private AppointRecord getOnlineData(AppointRecord r) {
        AppointRecord record = new AppointRecord();
        BeanUtils.map(r, record);

        Date now = new Date();
        record.setAppointSourceId(0);
        record.setWorkDate(now);

        // 获取时间，判断工作类型 workType（1："上午(6:00-12:00)" 2："下午(12:00-18:00)"
        // 3："晚上(18:00-06:00)"）
        int hour = now.getHours();
        int workType;
        if ((hour < 12) && (hour >= 6)) {
            workType = 1;
        } else if ((hour >= 12) && (hour < 18)) {
            workType = 2;
        } else {
            workType = 3;
        }

        record.setWorkType(workType);
        record.setSourceType(1);
        record.setTelClinicFlag(2);// 0普通预约;1预约云门诊;2在线云门诊
        record.setAppointRoad(5);
        record.setAppointStatus(0);
        record.setStartTime(now);
        record.setEndTime(now);
        record.setOrderNum(0);
        record.setAppointDate(now);
        record.setClinicPrice(0);
        record.setTransferId(0);
        record.setSourceLevel(1);
        record.setFactStartTime(now);
        record.setClinicStatus(1);
        record.setRecordType(0);

        return record;
    }

    /**
     * 上传在线云门诊图片资料
     *
     * @param clinicType   业务类型(1转诊；2会诊；3咨询；4其他;5在线云门诊)
     * @param telClinicId  云诊室序号
     * @param clinicObject 联合诊疗方
     * @param otherDocs    文档资料列表
     * @author yaozh
     */
    @RpcService
    public void saveOnlineAppointOtherDocList(final int clinicType,
                                              final String telClinicId, final int clinicObject,
                                              final List<Otherdoc> otherDocs) {
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                CdrOtherdocDAO dao = DAOFactory.getDAO(CdrOtherdocDAO.class);
                AppointRecord record = getByTelClinicIdAndClinicObject(
                        telClinicId, clinicObject);
                Integer appointId = record.getAppointRecordId();
                dao.saveOtherDocList(clinicType, appointId, otherDocs);
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 保存云门诊预约记录后 相关操作
     *
     * @param appointRecords
     * 2017-04-17 16:01:25 zhangsl 云门诊付费
     */
    public void doAfterAddAppointRecordForCloudClinic(List<AppointRecord> appointRecords) {
        AppointRecordDAO appointRecordDAO = DAOFactory
                .getDAO(AppointRecordDAO.class);
        OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
        Integer outDoctorId=0;
        for (AppointRecord ar : appointRecords) {
            if (ar.getClinicObject() == 2) {
                outDoctorId = ar.getDoctorId();
            }
        }
        double clinicPrice=organDAO.getCloudClinicPriceByOrgan(outDoctorId);//获取机构云门诊价格
        if (clinicPrice == 0) {//2017-04-17 15:14:16 非付费机构走原流程
            for (final AppointRecord appointRecord : appointRecords) {
                PatientDAO PatientDAO = DAOFactory.getDAO(PatientDAO.class);
                Patient p = PatientDAO.getByMpiId(appointRecord.getMpiid());
                //更新云门诊价格为免费
                appointRecord.setClinicPrice(clinicPrice);
                appointRecordDAO.update(appointRecord);

                logger.info("云门诊预约注册服务入参打印:Patient:" + JSONUtils.toString(p)
                        + ",appointRecord:" + JSONUtils.toString(appointRecord));
                AppointmentResponse res = new AppointmentResponse();
                res.setId(appointRecord.getAppointRecordId().toString());
                res.setAppointID("");
                res.setClinicArea(appointRecord.getConfirmClinicAddr());
                res.setOrderNum(appointRecord.getOrderNum());
                //appointRecordDAO.updateAppointId4TransferNottohis(res);
                updateAppointId4TransferNottoHisOpt( res, appointRecord);

                // 保存日志
                OperationRecordsDAO operationRecordsDAO = DAOFactory
                        .getDAO(OperationRecordsDAO.class);
                operationRecordsDAO.saveOperationRecordsForAppoint(appointRecord);
            }
        }else{
            for (AppointRecord appointRecord : appointRecords) {
                PatientDAO PatientDAO = DAOFactory.getDAO(PatientDAO.class);
                Patient p = PatientDAO.getByMpiId(appointRecord.getMpiid());
                logger.info("云门诊预约注册服务入参打印:Patient:" + JSONUtils.toString(p)
                        + ",appointRecord:" + JSONUtils.toString(appointRecord));
                appointRecord.setAppointStatus(4);
                //TODO:更新云门诊价格
                appointRecord.setClinicPrice(clinicPrice);                
                appointRecordDAO.update(appointRecord);

                // 保存日志
                OperationRecordsDAO operationRecordsDAO = DAOFactory
                        .getDAO(OperationRecordsDAO.class);
                operationRecordsDAO.saveOperationRecordsForAppoint(appointRecord);
            }
        }

            // 预约申请，给予推荐奖励,不管预约是否成功
            if (appointRecords.size() > 0
                    && appointRecords.get(0).getAppointRoad() == 5
                    && appointRecords.get(0).getAppointUser().length() != 32) {
                DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
                accDao.recommendReward(Integer.parseInt(appointRecords.get(0)
                        .getAppointUser()));
            }
    }

    /**
     * 远程门诊开始服务
     *
     * @param telClinicId
     * @author zsq
     */
    @RpcService
    public boolean startRemoteInquiry(final String telClinicId) {
        logger.info("远程门诊开始,telClinicId:" + telClinicId);
        HibernateSessionTemplate.instance().execute(
                new HibernateStatelessAction() {
                    @Override
                    public void execute(StatelessSession ss) throws Exception {
                        String hql = "update AppointRecord set factStartTime=:factStartTime,clinicStatus=1 where telClinicId=:telClinicId";
                        Query q = ss.createQuery(hql);
                        q.setParameter("telClinicId", telClinicId);
                        q.setTimestamp("factStartTime", new Date());
                        q.executeUpdate();
                    }
                });
        return true;
    }

    /**
     * 云门诊开始服务
     *
     * @param telClinicId
     * @return
     */
    @RpcService
    public Boolean startedRemoteInquiry(final String telClinicId) {
        if (StringUtils.isEmpty(telClinicId)) {
//            logger.error("telClinicId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "telClinicId is required");
        }
        List<AppointRecord> ars = findCloudAppointRecord(telClinicId, 0);
        if (ars == null || ars.size() < 1) {
            throw new DAOException(DAOException.EVAL_FALIED, "没有找到云诊室序号为【"
                    + telClinicId + "】,就诊状态为等待中的预约记录");
        }

        boolean result = startRemoteInquiry(telClinicId);
        if (!result) {
            throw new DAOException(DAOException.VALUE_NEEDED, "开始远程门诊失败");
        }
        return result;
    }

    /**
     * 远程会诊结束服务
     *
     * @param telClinicId
     * @author zsq
     */
    @RpcService
    public void endRemoteInquiry(final String telClinicId) {
        logger.info("远程会诊结束，telClinicId:" + telClinicId);
        @SuppressWarnings("rawtypes")
        HibernateStatelessAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "update AppointRecord set factEndTime=:factEndTime,clinicStatus=2 where telClinicId=:telClinicId and factEndTime is null";
                Query q = ss.createQuery(hql);
                q.setParameter("telClinicId", telClinicId);
                q.setTimestamp("factEndTime", new Date());
                q.executeUpdate();
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }

    /**
     * 云门诊结束服务
     *
     * @param telClinicId
     * @return
     */
    @RpcService
    public void endedRemoteInquiry(final String telClinicId) {
        if (StringUtils.isEmpty(telClinicId)) {
//            logger.error("telClinicId is required");
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "telClinicId is required");
        }
        List<AppointRecord> ars = findCloudAppointRecord(telClinicId, 1);
        if (ars == null || ars.size() < 1) {
            throw new DAOException(DAOException.EVAL_FALIED, "没有找到云诊室序号为【"
                    + telClinicId + "】,就诊状态为就诊中的预约记录");
        }
        endRemoteInquiry(telClinicId);

        //结束云门诊，添加预约云门诊奖励(就诊结束给予接诊方/出诊方奖励)
        addCloudIncome(ars);
    }

    /**
     * 根据MpiId更新病人姓名
     *
     * @param patientName
     * @param mpiid
     * @author ZX
     * @date 2015-7-23 上午10:10:10
     */
    @RpcService
    @DAOMethod
    public abstract void updatePatientNameByMpiid(String patientName,
                                                  String mpiid);

    /**
     * Title: 按就诊机构，就诊日期 查询该就诊日期当天的全部有效的预约记录 Description: 按就诊机构，就诊日期
     * 查询该就诊日期当天的全部有效的预约记录，按就诊时间正排序。要求分页显示，每页显示数量作为入参。
     *
     * @param organId  --就诊机构编码
     * @param workDate --就诊日期
     * @param start    --查询起始条目
     * @param limit    --查询最大数据量
     * @return
     * @throws DAOException List<AppointRecord>
     * @author AngryKitty
     * @date 2015-8-21
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    @RpcService
    public List<AppointRecord> findByOrganIdAndWorkDate(final int organId,
                                                        final Date workDate, final int start, final int limit)
            throws DAOException {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            List<AppointRecord> result = new ArrayList<AppointRecord>();

            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        " from AppointRecord ppt where ppt.appointStatus in(0,1) and ppt.organId=:OrganId and ppt.workDate=:WorkDate order by ppt.workDate ");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("OrganId", organId);
                query.setParameter("WorkDate", workDate);
                query.setMaxResults(limit);
                query.setFirstResult(start);
                result = query.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<AppointRecord>) action.getResult();
    }

    ;

    /**
     * 查询预约业务数据 Description: 根据开始时间和结束时间查询转诊业务数据，按预约提交时间降序排列
     *
     * @param startTime
     * @param endTime
     * @return
     * @author Qichengjian
     */
    @RpcService
    public List<AppointRecord> findByStartTimeAndEndTime(final Date startTime,
                                                         final Date endTime) throws DAOException {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            List<AppointRecord> result = new ArrayList<AppointRecord>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) {
                String sql = "from AppointRecord a where a.appointDate>=:startTime and a.appointDate<=:endTime order by appointDate desc";
                Query q = ss.createQuery(sql);
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * Title: 统计预约信息 Description:
     * 新增一个服务，在原来的基础上，新加一个搜索参数（患者主键），跟机构相关的查询条件（申请机构、目标机构）由原来的一个定值改为一个数组
     *
     * @param startTime     ---转诊申请开始时间
     * @param endTime       ---转诊申请结束时间
     * @param ar            ---预约信息
     * @param start         ---分页，开始条目数
     * @param mpiId         ---患者主键
     * @param appointOragns ---预约申请机构（集合）
     * @param organIds      ---预约目标机构（集合）
     * @return List<AppointRecordAndDoctors>
     * @author AngryKitty
     * @date 2015-8-31
     */
    @RpcService
    public QueryResult<AppointRecordAndDoctors> findAppointRecordAndDoctorsByStatic(
            final Date startTime, final Date endTime, final AppointRecord ar,
            final int start, final String mpiId,
            final List<Integer> appointOragns, final List<Integer> organIds) {

        if (startTime == null) {
//            logger.error("统计开始时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
//            logger.error("统计结束时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<QueryResult<AppointRecordAndDoctors>> action = new AbstractHibernateStatelessResultAction<QueryResult<AppointRecordAndDoctors>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecordAndDoctors(a) from AppointRecord a where DATE(a.appointDate)>=DATE(:startTime) and DATE(a.appointDate)<=DATE(:endTime) ");

                // 添加申请机构条件
                if (appointOragns != null && appointOragns.size() > 0) {
                    boolean flag = true;
                    for (Integer i : appointOragns) {
                        if (i != null) {
                            if (flag) {
                                hql.append(" and  a.appointOragn in(");
                                flag = false;
                            }
                            hql.append("'" + i + "',");
                        }
                    }
                    if (!flag) {
                        hql = new StringBuilder(hql.substring(0,
                                hql.length() - 1) + ") ");
                    }
                }

                // 添加目标机构条件
                if (organIds != null && organIds.size() > 0) {
                    boolean flag = true;
                    for (Integer i : organIds) {
                        if (i != null) {
                            if (flag) {
                                hql.append(" and a.organId in(");
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
                // 患者主键
                if (!StringUtils.isEmpty(mpiId)) {
                    hql.append(" and a.mpiid= '" + mpiId + "'");
                }
                if (ar != null) {
                    // 添加申请医生条件
                    if (ar.getAppointUser() != null
                            && !StringUtils.isEmpty(ar.getAppointUser())) {
                        hql.append(" and a.appointUser='" + ar.getAppointUser()
                                + "'");
                    }

                    // 添加目标医生条件
                    if (ar.getDoctorId() != null) {
                        hql.append(" and a.doctorId=" + ar.getDoctorId());
                    }

                    // 添加预约单状态
                    if (ar.getAppointStatus() != null) {
                        hql.append(" and a.appointStatus="
                                + ar.getAppointStatus());
                    }

                    // 添加预约方式
                    if (ar.getAppointRoad() != null) {
                        hql.append(" and a.appointRoad=" + ar.getAppointRoad());
                    }
                }

                hql.append(" order by a.appointDate desc ");

                Query query = ss.createQuery(hql.toString());
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                total = query.list().size();
                query.setFirstResult(start);
                query.setMaxResults(10);

                List<AppointRecordAndDoctors> tfList = query.list();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

                for (AppointRecordAndDoctors appointRecordAndDoctors : tfList) {

                    // 申请电话
                    String requestId = appointRecordAndDoctors
                            .getAppointrecord().getAppointUser();
                    if (requestId.length() > 11) {
                        Patient pt = patientDAO.getByMpiId(requestId);
                        if (!StringUtils.isEmpty(pt.getMobile())) {
                            appointRecordAndDoctors.setRequestTel(pt
                                    .getMobile());
                        }

                    } else {
                        int requestDocId = Integer.parseInt(requestId);
                        Doctor reqDoctor = doctorDAO
                                .getByDoctorId(requestDocId);
                        if (!StringUtils.isEmpty(reqDoctor.getMobile())) {
                            appointRecordAndDoctors.setRequestTel(reqDoctor
                                    .getMobile());
                        }
                    }

                    // 目标医生电话
                    int targerDocId = appointRecordAndDoctors
                            .getAppointrecord().getDoctorId();
                    Doctor targetDoctor = doctorDAO.getByDoctorId(targerDocId);
                    if (!StringUtils.isEmpty(targetDoctor.getMobile())) {
                        appointRecordAndDoctors.setTargerDocTel(targetDoctor
                                .getMobile());
                    }
                }
                QueryResult<AppointRecordAndDoctors> qResult = new QueryResult<AppointRecordAndDoctors>(
                        total, query.getFirstResult(), 10, tfList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return (QueryResult<AppointRecordAndDoctors>) action.getResult();
    }

    /**
     * 根据医生id，预约途径，预约状态查询预约单记录
     *
     * @param appointUser   医生id
     * @param appointRoad   预约途径(5医生诊间预约;6转诊预约)
     * @param appointStatus 预约状态(0:预约成功;1挂号;2取消;3爽约;9医院确认中)
     * @return
     * @author ZX
     * @date 2015-9-7 下午3:05:14
     */
    @RpcService
    @DAOMethod(sql = "from AppointRecord where (appointUser=:appointUser or doctorId=:appointUser) and appointRoad=:appointRoad and appointStatus=:appointStatus")
    public abstract List<AppointRecord> findTransferByAppointUserAndAppointStatus(
            @DAOParam("appointUser") String appointUser,
            @DAOParam("appointRoad") int appointRoad,
            @DAOParam("appointStatus") int appointStatus);

    @RpcService
    @DAOMethod(sql = "from AppointRecord where (telClinicFlag=0 or telClinicFlag is null) and appointUser=:appointUser and appointRoad=:appointRoad and appointStatus=:appointStatus")
    public abstract List<AppointRecord> findAppointRecordByAppointUserAndAppointStatus(
            @DAOParam("appointUser") String appointUser,
            @DAOParam("appointRoad") int appointRoad,
            @DAOParam("appointStatus") int appointStatus);

    @DAOMethod(sql = "from AppointRecord where telClinicFlag=:telClinicFlag and appointUser=:appointUser and appointRoad=:appointRoad and appointStatus=:appointStatus")
    public abstract List<AppointRecord> findCloudAppointRecordByAppointUserAndAppointStatus(
            @DAOParam("telClinicFlag") Integer telClinicFlag,
            @DAOParam("appointUser") String appointUser,
            @DAOParam("appointRoad") int appointRoad,
            @DAOParam("appointStatus") int appointStatus);

    @DAOMethod(sql = "select count(*) from Transfer a,AppointRecord b WHERE a.transferId = b.transferId AND ( a.requestDoctor = :doctorId OR a.confirmDoctor = :doctorId ) AND a.transferStatus = 2 AND a.transferResult = 1 AND a.payflag = 1 AND a.isAdd = 1 AND a.requestDoctor IS NOT NULL AND ( b.appointUser = :doctorId OR  b.doctorId = :doctorId )  AND b.appointRoad = 6 AND b.appointStatus = 0")
    public abstract long getSuccessTransferNum(@DAOParam("doctorId") Integer doctorId);

    @DAOMethod
    @RpcService
    public abstract AppointRecord getByTransferId(int transferId);

    /**
     * 根据转诊单号获取预约记录(云门诊转诊接收，会往预约记录里面插入两条云门诊预约记录)
     *
     * @param transferId 转诊单号
     * @return 返回预约记录
     * @author zhangx
     * @date 2015-10-20下午7:30:22
     */
    @DAOMethod
    @RpcService
    public abstract List<AppointRecord> findByTransferId(int transferId);

    /**
     * 根据云诊室序号获取预约记录
     *
     * @param telClinicId 云诊室序号
     * @return 预约记录列表
     * @author zhangx
     * @date 2015-10-21上午11:10:16
     */
    @DAOMethod
    @RpcService
    public abstract List<AppointRecord> findByTelClinicId(String telClinicId);

    @DAOMethod
    @RpcService
    public abstract AppointRecord getByOrganAppointIdAndOrganId(
            String organAppointId, Integer organId);

    @RpcService
    public void updateAppointRecord(final AppointRecord app) {

        AppointRecord ar = getByOrganAppointIdAndOrganId(
                app.getOrganAppointId(), app.getOrganId());
        if (ar == null) {
//                    logger.error("can not find the AppointInHosprecord by OrganAppointId:"
//                            + app.getOrganAppointId());
            throw new DAOException(
                    "can not find the record by OrganId:"
                            + app.getOrganId());
        }
        ar.setOrganAppointId(app.getOrganAppointId());
        ar.setRegisterDate(app.getRegisterDate());
        ar.setRegisterUser(app.getRegisterUser());
        ar.setRegisterName(app.getRegisterName());
        ar.setRegisterDate(app.getRegisterDate());
        ar.setAppointStatus(1);
        update(ar);


    }


    /**
     * 预约申请列表-app端
     *
     * @param appointUser 预约提交人编号
     * @param flag        标志--0：全部，1：待就诊，2：已就诊
     * @param mark        标志-0未就诊1已完成
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @return Hashtable<String, List<AppointRecordAndPatientAndDoctor>>
     * @author luf
     */
    @RpcService
    public Hashtable<String, List<AppointRecordBean>> queryRequestAppointList(
            String appointUser, int flag, int mark, int start, int limit) {
        return this.queryRequestAppointListWithGetPath(appointUser, flag, mark, start, limit, 0);
    }

    /**
     * 预约申请列表-pc端
     *
     * @param appointUser 预约提交人编号
     * @param flag        标志--0：全部，1：待就诊，2：已就诊
     * @param mark        标志-0未就诊1已完成
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @return Hashtable<String, List<AppointRecordAndPatientAndDoctor>>
     * @author luf
     */
    @RpcService
    public Hashtable<String, List<AppointRecordBean>> queryRequestAppointListDiff(
            String appointUser, int flag, int mark, int start, int limit) {
        return this.queryRequestAppointListWithGetPath(appointUser, flag, mark, start, limit, 1);
    }

    /**
     * 预约申请列表
     *
     * @param appointUser 预约提交人编号
     * @param flag        标志--0：全部，1：待就诊，2：已就诊
     * @param mark        标志-0未就诊1已完成
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @param getPath     获取途径-0app端，1pc端
     * @return Hashtable<String, List<AppointRecordAndPatientAndDoctor>>
     * @author luf
     */
    public Hashtable<String, List<AppointRecordBean>> queryRequestAppointListWithGetPath(
            String appointUser, int flag, int mark, int start, int limit, int getPath) {
        logger.info("预约申请列表  eh.bus.dao.AppointRecordDAO ===> queryRequestAppointList <== "
                + "appointUser="
                + appointUser
                + ";flag="
                + flag
                + ";mark="
                + mark + ";start=" + start + "limit=" + limit);
        if (flag == 0) {
            return this.queryRequestAppointListAll(appointUser, mark, start,
                    limit, getPath);
        }
        Hashtable<String, List<AppointRecordBean>> table = new Hashtable<String, List<AppointRecordBean>>();
        List<AppointRecordBean> list = new ArrayList<AppointRecordBean>();
        List<AppointRecord> rs = new ArrayList<AppointRecord>();
        String listName = "";
        if (flag == 1) {
            table.put("completed", list);
            listName = "unfinished";
        } else {
            table.put("unfinished", list);
            listName = "completed";
        }
        rs = this.findAsByAppointUserUnAppoint(appointUser, flag, start, limit);
        list = convertFromAppointRecordListToAnd(rs, 0);
        table.put(listName, list);
        return table;
    }

    /**
     * 供 queryRequestAppointList 调用
     *
     * @param appointUser 预约提交人编号
     * @param flag        标志--1：待就诊，2：已就诊
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @return List<AppointRecord>
     * @author luf
     * @desc 原来查询(appointStatus=0 or appointStatus=1 or appointStatus=9) 换为 (appointStatus!=10 and appointStatus!=3)
     * 医生同步患者端 患者可支付但不可取消预约，解决患者支付完成后医生端 我的申请列表数据消失问题 houxr 20170303
     */
    public List<AppointRecord> findAsByAppointUserUnAppoint(
            final String appointUser, final int flag, final int start,
            final int limit) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "from AppointRecord where appointUser=:appointUser and "
                                + "(telClinicFlag=0 or telClinicFlag is null  or (telClinicFlag = 1 and clinicObject = 2) or (telClinicFlag = 2 and clinicObject = 2)) "
                                + "and ((appointStatus!=10 and appointStatus!=3 and appointStatus!=2) ");
                if (flag == 1) {
                    hql.append("and endTime>now()) order by workDate asc,startTime asc");
                } else {
                    hql.append("and endTime<=now()) order by workDate desc,startTime desc");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("appointUser", appointUser);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                @SuppressWarnings("unchecked")
                List<AppointRecord> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 预约申请列表(全部)
     *
     * @param appointUser 预约提交人编号
     * @param mark        标志-0未就诊1已完成
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @param getPath     获取途径-0app端，1pc端
     * @return Hashtable<String, List<AppointRecordAndPatientAndDoctor>>
     * @author luf
     */
    public Hashtable<String, List<AppointRecordBean>> queryRequestAppointListAll(
            String appointUser, int mark, int start, int limit, int getPath) {
        Hashtable<String, List<AppointRecordBean>> table = new Hashtable<String, List<AppointRecordBean>>();
        List<AppointRecordBean> list = new ArrayList<AppointRecordBean>();
        List<AppointRecord> rs = new ArrayList<AppointRecord>();
        String listName = "";
        if (mark == 0) {
            if (getPath == 0) {
                rs = this.findAsByAppointUserUn(appointUser, start, limit);
            } else {
                rs = this.findAsByAppointUserUnWithClinic(appointUser, start, limit);
            }
            listName = "unfinished";
            if (rs == null) {
                rs = new ArrayList<AppointRecord>();
            }
            if (rs.size() < limit) {
                List<AppointRecord> appointRecords = new ArrayList<AppointRecord>();
                if (getPath == 0) {
                    appointRecords = this.findAsByAppointUserHad(appointUser, 0, limit - rs.size());
                } else {
                    appointRecords = this.findAsByAppointUserHadWithClinic(appointUser, 0, limit - rs.size());
                }
                list = convertFromAppointRecordListToAnd(appointRecords, 0);
                table.put("completed", list);
            }
        } else {
            if (getPath == 0) {
                rs = this.findAsByAppointUserHad(appointUser, start, limit);
            } else {
                rs = this.findAsByAppointUserHadWithClinic(appointUser, start, limit);
            }
            listName = "completed";
            table.put("unfinished", list);
        }
        if (rs == null) {
            rs = new ArrayList<AppointRecord>();
        }
        list = convertFromAppointRecordListToAnd(rs, 0);
        table.put(listName, list);
        return table;
    }

    /**
     * 我的申请未就诊列表（供 预约申请列表 调用）
     *
     * @param appointUser 预约提交人编号
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @return List<AppointRecord>
     * @author luf
     * @desc 原来查询(appointStatus=0 or appointStatus=1 or appointStatus=9) 换为 (appointStatus!=10 and appointStatus!=3)
     * 医生同步患者端 患者可支付但不可取消预约，解决患者支付完成后医生端 我的申请列表数据消失问题 houxr 20170303
     */
    @DAOMethod(sql = "from AppointRecord where appointUser=:appointUser and "
            + "(telClinicFlag=0 or telClinicFlag is null  or (telClinicFlag = 1 and clinicObject = 2) or (telClinicFlag = 2 and clinicObject = 2)) "
            + "and ((appointStatus!=10 and appointStatus!=3 and appointStatus!=2) and "
            + "endTime>now()) order by appointDate desc")
    public abstract List<AppointRecord> findAsByAppointUserUn(
            @DAOParam("appointUser") String appointUser,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 我的申请已完成列表（供 预约申请列表 调用）
     *
     * @param appointUser 预约提交人编号
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @return List<AppointRecord>
     * @author luf
     * @desc 原来查询(appointStatus=0 or appointStatus=1 or appointStatus=9) 换为 (appointStatus!=10 and appointStatus!=3)
     * 医生同步患者端 患者可支付但不可取消预约，解决患者支付完成后医生端 我的申请列表数据消失问题 houxr 20170303
     */
    @DAOMethod(sql = "from AppointRecord where appointUser=:appointUser and "
            + "(telClinicFlag=0 or telClinicFlag is null  or (telClinicFlag = 1 and clinicObject = 2) or (telClinicFlag = 2 and clinicObject = 2)) "
            + "and ((appointStatus=2 or appointStatus=3) or "
            + "((appointStatus!=10 and appointStatus!=3) and "
            + "endTime<=now())) order by appointDate desc")
    public abstract List<AppointRecord> findAsByAppointUserHad(
            @DAOParam("appointUser") String appointUser,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 我的申请未就诊列表（供 预约申请列表 调用）-pc端
     *
     * @param appointUser 预约提交人编号
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @return List<AppointRecord>
     * @author luf
     * @desc 原来查询(appointStatus=0 or appointStatus=1 or appointStatus=9)
     * 医生同步患者端 患者可支付但不可取消预约，解决患者支付完成后医生端 我的申请列表数据消失问题 houxr 20170303
     */
    @DAOMethod(sql = "from AppointRecord where appointUser=:appointUser and "
            + "(telClinicFlag=0 or telClinicFlag is null  or (telClinicFlag = 1 and clinicObject = 2) or (telClinicFlag = 2 and clinicObject = 2)) "
            + "and ((IFNULL(telClinicFlag,0)=0 and (appointStatus!=10 and appointStatus!=3 and appointStatus!=2) and endTime>now()) "
            + "or (IFNULL(telClinicFlag,0)>0 and clinicStatus<2)) order by appointDate desc")
    public abstract List<AppointRecord> findAsByAppointUserUnWithClinic(
            @DAOParam("appointUser") String appointUser,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 我的申请已完成列表（供 预约申请列表 调用）-pc端
     *
     * @param appointUser 预约提交人编号
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @return List<AppointRecord>
     * @author luf
     * @desc 原来查询(appointStatus=0 or appointStatus=1 or appointStatus=9)
     * 医生同步患者端 患者可支付但不可取消预约，解决患者支付完成后医生端 我的申请列表数据消失问题 houxr 20170303
     */
    @DAOMethod(sql = "from AppointRecord where appointUser=:appointUser and "
            + "(telClinicFlag=0 or telClinicFlag is null  or (telClinicFlag = 1 and clinicObject = 2) or (telClinicFlag = 2 and clinicObject = 2)) "
            + "and ((IFNULL(telClinicFlag,0)=0 and ((appointStatus=2 or appointStatus=3) or ((appointStatus!=10 and appointStatus!=3) and "
            + "endTime<=now()))) or (IFNULL(telClinicFlag,0)>0 and clinicStatus>=2)) order by appointDate desc")
    public abstract List<AppointRecord> findAsByAppointUserHadWithClinic(
            @DAOParam("appointUser") String appointUser,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 循环遍历预约记录返回添加病人信息
     *
     * @param rs   预约记录列表
     * @param from 调用来源--0我的预约申请 1我的预约列表
     * @return List<AppointRecordAndPatientAndDoctor>
     * @author luf
     */
    public List<AppointRecordBean> convertFromAppointRecordListToAnd(
            List<AppointRecord> rs, int from) {
        List<AppointRecordBean> list = new ArrayList<AppointRecordBean>();
        for (AppointRecord ar : rs) {
            AppointRecordBean apd = new AppointRecordBean();
            ar = convertAppointRecordForRequestList(ar);
            Patient patient = new Patient();
            if (from == 0) {
                patient = convertPatientForAppointRecord(ar.getMpiid(),
                        ar.getAppointUser());
            }
            if (from == 1) {
                patient = convertPatientForAppointRecord(ar.getMpiid(), ar
                        .getDoctorId().toString());
            }
            apd.setAppointRecord(ar);
            apd.setPatient(patient);
            list.add(apd);
        }
        return list;
    }

    /**
     * 过滤病人信息
     *
     * @param mpiId
     * @param appointUser
     * @return
     */
    public Patient convertPatientForAppointRecord(String mpiId, String appointUser) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.get(mpiId);
        Patient p = new Patient();
        p.setPatientName(patient.getPatientName());
        p.setPatientSex(patient.getPatientSex());
        p.setBirthday(patient.getBirthday());
        p.setPhoto(patient.getPhoto());
        p.setPatientType(patient.getPatientType());
        if (!StringUtils.isEmpty(appointUser) && appointUser.length() < 32) {
            RelationPatientDAO dao = DAOFactory.getDAO(RelationPatientDAO.class);
            RelationDoctor relationDoctor = dao.getByMpiidAndDoctorId(mpiId, Integer.valueOf(appointUser));
            Integer relationDoctorId = 0;
            Boolean relationFlag = false;
            Boolean signFlag = false;
            List<String> labelNames = new ArrayList<String>();
            if (relationDoctor != null) {
                relationDoctorId = relationDoctor.getRelationDoctorId();
                relationFlag = true;
                if (relationDoctor.getFamilyDoctorFlag()) {
                    signFlag = true;
                }
                RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
                labelNames = labelDAO.findLabelNamesByRPId(relationDoctorId);
            }
            p.setRelationPatientId(relationDoctorId);
            p.setRelationFlag(relationFlag);
            p.setSignFlag(signFlag);
            p.setLabelNames(labelNames);
        }
        return p;
    }

    /**
     * 过滤预约记录并进行时间转换
     *
     * @param appointRecord 预约记录
     * @return AppointRecord
     * @author luf
     */
    public AppointRecord convertAppointRecordForRequestList(
            AppointRecord appointRecord) {
        AppointRecord record = new AppointRecord();
        Date appointDate = appointRecord.getAppointDate();
        String requestDate = DateConversion
                .convertRequestDateForBuss(appointDate);
        record = appointRecord;
        record.setRequestDate(requestDate);
        return record;
    }

    /**
     * 我的预约列表
     *
     * @param doctorId 医生内码
     * @param flag     标志--0：全部，1：今日就诊，2：明日就诊，3：7天内就诊，4：7天后就诊
     * @param mark     标记--0未就诊1已完成
     * @param start    分页起始位置
     * @param limit    每页限制条数
     * @return Hashtable<String, List<AppointRecordBean>>
     * @author luf
     */
    @RpcService
    public Hashtable<String, List<AppointRecordBean>> queryAppointRecordList(
            int doctorId, int flag, int mark, int start, int limit) {
        Hashtable<String, List<AppointRecordBean>> table = new Hashtable<String, List<AppointRecordBean>>();
//        List<AppointRecordBean> list = new ArrayList<AppointRecordBean>();
        Date workDate = new Date();
        switch (flag) {
            case 1:
                workDate = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
                break;
            case 2:
                workDate = DateConversion.getDaysAgo(-1);
                break;
            case 3:
            case 4:
                workDate = DateConversion.getDaysAgo(-7);
                break;
            default:
                break;
        }
        List<AppointRecord> rs = new ArrayList<AppointRecord>();
        List<AppointRecord> appointRecords = new ArrayList<AppointRecord>();
        if (flag <= 1 && mark == 0) {
            rs = findAsByDoctorIdAndMark(doctorId, mark, flag, workDate, start, limit);
            if (rs == null) {
                rs = new ArrayList<AppointRecord>();
            }
            if (rs.size() < limit) {
                appointRecords = findAsByDoctorIdAndMark(doctorId, 1, flag, workDate, 0, limit - rs.size());
            }
        }
        if (flag > 1) {
            mark = 0;
            rs = findAsByDoctorIdAndMark(doctorId, mark, flag, workDate, start, limit);
        }
        List<AppointRecordBean> list = convertFromAppointRecordListToAnd(rs, 1);
        table.put("unfinished", list);
        if (mark == 1) {
            appointRecords = findAsByDoctorIdAndMark(doctorId, mark, flag, workDate, start, limit);
        }
        list = convertFromAppointRecordListToAnd(appointRecords, 1);
        table.put("completed", list);
        return table;
    }

    /**
     * 供 我的预约列表 调用
     *
     * @param doctorId 医生内码
     * @param mark     标记--0未就诊1已完成
     * @param flag     标志--0：全部，1：今日就诊，2：明日就诊，3：7天内就诊，4：7天后就诊
     * @param workDate 就诊日期
     * @param start    分页起始位置
     * @param limit    每页限制条数
     * @return List<AppointRecord>
     * @author luf
     * 修改云门诊筛选条件（取消待支付，增加已退款） zhangsl 2017-04-20 18:02:42
     */
    public List<AppointRecord> findAsByDoctorIdAndMark(final int doctorId,
                                                       final int mark, final int flag, final Date workDate,
                                                       final int start, final int limit) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) {
                StringBuilder hql = new StringBuilder(
                        "from AppointRecord where doctorId=:doctorId and ((ifNUll(telClinicFlag,0)=0 and appointStatus in (0,1,4,5)) or (ifNull(telClinicFlag,0)<>0 and appointStatus in(0,1,5,6))) ");
                switch (mark) {
                    case 0:
                        hql.append("and endTime>=now() ");
                        break;
                    case 1:
                        hql.append("and endTime<=now() ");
                    default:
                        break;
                }
                switch (flag) {
                    case 1:
                    case 2:
                        hql.append("and workDate=:workDate ");
                        break;
                    case 3:
                        hql.append("and workDate<=:workDate and workDate>=:workDateNow ");
                        break;
                    case 4:
                        hql.append("and workDate>:workDate ");
                    default:
                        break;
                }
                hql.append("order by workDate desc,startTime desc");
                Query q = ss.createQuery(hql.toString());
                if (flag != 0) {
                    q.setParameter("workDate", workDate);
                }
                if (flag == 3) {
                    q.setParameter("workDateNow", DateConversion.getFormatDate(
                            new Date(), "yyyy-MM-dd"));
                }
                q.setParameter("doctorId", doctorId);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<AppointRecord> as = q.list();
                setResult(as);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 预约取消服务（同时取消相应的转诊）
     *
     * @param appointRecordId 预约记录主键
     * @param cancelUser      取消人Id
     * @param cancelName      取消人姓名
     * @param cancelResean    取消原因
     * @return Boolean
     * @author luf
     * @desc 2016.03.23 解决预约模块中远程转诊取消转诊单不能取消的问题
     */
    @RpcService
    public Boolean cancelAppointAndTransfer(final int appointRecordId,
                                            final String cancelUser, final String cancelName,
                                            final String cancelResean) {
        logger.info("cancelAppointAndTransfer-->appointRecordId:" + appointRecordId
                + ",cancelUser:" + cancelUser + ",cancelName:" + cancelName
                + ",cancelResean:" + cancelResean);
        final AppointRecord appoint = this
                .getByAppointRecordId(appointRecordId);
        Boolean cancelFlag = true;

        // 取消普通预约
        if (appoint.getTelClinicFlag() == null
                || appoint.getTelClinicFlag() == 0) {
            cancelFlag = cancel(appointRecordId, cancelUser, cancelName,
                    cancelResean);
        }
        // 取消云门诊
        if (appoint.getTelClinicFlag() != null
                && appoint.getTelClinicFlag() == 1) {
            AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    List<AppointRecord> list = findByTelClinicId(appoint
                            .getTelClinicId());
                    
                    for (AppointRecord appointRecord : list) {
                        // 判断预约记录是否已经取消
                        if (appointRecord.getAppointStatus() != null
                                && appointRecord.getAppointStatus() == 2) {
                            continue;
                        }

                        if (appointRecord.getAppointStatus() != null
                                && appointRecord.getAppointStatus() == 0) {
                            doCancelAppoint(appointRecord.getAppointRecordId(),
                                    cancelUser, cancelName, cancelResean); 
                            // 出诊方预约记录信息发送到his
                            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                    		HisServiceConfig cfg=hisServiceConfigDao.getByOrganId(appointRecord.getOrganId());
                            if (null != cfg && cfg.getCloudClinicNeedToHis() != null && cfg.getCloudClinicNeedToHis() == 1
                            		&& appointRecord.getClinicObject() != null && appointRecord.getClinicObject() == 2) {
                            	cancelHisAppoint(appointRecord.getAppointRecordId(), cancelResean, appointRecord.getWorkDate());                                           
                            }                         
                        }
                    }                    
                    setResult(true);                    
                }
            };
            HibernateSessionTemplate.instance().executeTrans(action);            
            cancelFlag = action.getResult();
            // 取消有号转诊
            if (cancelFlag) {
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer = transferDAO.get(appoint.getTransferId());
                if (transfer != null) {
                    // 取消人是医生
                    if (cancelUser.length() < 32 && cancelUser.length() > 0) {
                        Integer doctorId = Integer.valueOf(cancelUser);
                        Doctor d = DAOFactory.getDAO(DoctorDAO.class).get(doctorId);
                        transfer.setCancelDoctor(doctorId);
                        transfer.setCancelOrgan(d.getOrgan());
                        transfer.setCancelDepart(d.getDepartment());
                    }
                    transfer.setCancelCause(cancelResean);
                    transfer.setTransferStatus(9);
                    transfer.setCancelTime(DateConversion
                            .convertFromDateToTsp(new Date()));
                    transferDAO.update(transfer);
                }
            }
        }
        /*try {
            AppContextHolder.getBean("followUpdateService", FollowUpdateService.class).deleteByAppointRecordId(appointRecordId);
        } catch (Exception e) {
            logger.debug("after cancelAppoint deleteByAppointRecordId faild and appointRecordId is [{}]", appointRecordId);
        }*/
        return cancelFlag;
    }

    /**
     * @param @param  organId 医院代码
     * @param @param  doctorId 医生编号 //输入为空，搜索全部
     * @param @param  mpiid 患者主索引 //输入为空，搜索全部
     * @param @param  startTime 开始时间
     * @param @param  endTime 结束时间
     * @param @return
     * @return List<AppointRecord>
     * @Description: TODO 根据五个参数查找（号源已停诊的）所对应的预约记录
     * @author Zhongzx
     * @Date 2015-12-4下午4:07:10
     * @date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public List<AppointRecord> findAppointRecordByFive(final Integer organId,
                                                       final Date startTime, final Date endTime, final Integer doctorId,
                                                       final String mpiid) {
        if (null == organId) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "select d from AppointRecord d,AppointSource s where "
                                + "d.appointSourceId = s.appointSourceId and s.stopFlag=1"
                                + " and d.organId=:organId "
                                + " and d.workDate<=:endTime and d.workDate>=:startTime");
                if (null != doctorId) {
                    hql.append(" and d.doctorId=:doctorId");
                }
                if (!StringUtils.isEmpty(mpiid)) {
                    hql.append(" and d.mpiid=:mpiid");
                }
                hql.append(" order by d.workDate desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                q.setParameter("endTime", endTime);
                q.setParameter("startTime", startTime);
                if (null != doctorId) {
                    q.setParameter("doctorId", doctorId);
                }
                if (!StringUtils.isEmpty(mpiid)) {
                    q.setParameter("mpiid", mpiid);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 查询患者全部预约、转诊数据列表[包括医生帮患者预约的数据同步患者端显示]服务-患者端
     *
     * @param mpiId 患者主键
     * @param start 起始位置
     * @param limit 分页
     * @return
     * @date 2017-02-14 13:09:38
     */
    @RpcService
    public List<Map<String, Object>> findAllPatientAppointTransferList(final String mpiId, final int start, final int limit) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<Object[]> resultList = findAppointRecordAndTransfer(mpiId, start, limit);
        List<Map<String, Object>> mapList = new ArrayList<>();
        AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        if (resultList.size() != 0) {
            for (Object[] objects : resultList) {
                Map<String, Object> map = new HashMap<>();
                if (ObjectUtils.nullSafeEquals((String) objects[0], "1")) {//预约挂号
                    Integer appointRecordId = (Integer) objects[1];
                    AppointRecord appointRecord = appointDao.getByAppointRecordId(appointRecordId);
                    AppointRecord ar = new AppointRecord();
                    ar.setAppointRecordId(appointRecord.getAppointRecordId());
                    ar.setConfirmClinicAddr(appointRecord.getConfirmClinicAddr());// 就诊地点
                    ar.setStartTime(appointRecord.getStartTime());// 就诊时间
                    ar.setAppointStatus(appointRecord.getAppointStatus());// 预约状态
                    ar.setOrganId(appointRecord.getOrganId());// 就诊医院
                    //上海6院不显示评价
                    if (!HisServiceConfigDAO.isShangHai6(appointRecord.getOrganId())) {
                        ar.setEvaStatus(appointRecord.getEvaStatus());//评价状态
                    }
                    ar.setDoctorId(appointRecord.getDoctorId());
                    ar.setClinicPrice(appointRecord.getClinicPrice());
                    ar.setAppointRoad(appointRecord.getAppointRoad());
                    ar.setRequestDate(DateConversion.convertRequestDateForBuss(appointRecord.getAppointDate()));
                    ar.setRecordType(appointRecord.getRecordType());
                    ar.setWorkDate(appointRecord.getWorkDate());
                    ar.setWorkType(appointRecord.getWorkType());
                    ar.setAppointUser(appointRecord.getAppointUser());
                    map.put("appointRecord", ar);// 放入预约记录

                    Doctor doctor = doctorDAO.getByDoctorId(appointRecord.getDoctorId());
                    Doctor doc = new Doctor();
                    doc.setDoctorId(doctor.getDoctorId());
                    doc.setName(doctor.getName());
                    doc.setPhoto(doctor.getPhoto());
                    doc.setProfession(doctor.getProfession());// 专科
                    doc.setProTitle(doctor.getProTitle());// 职称
                    doc.setGender(doctor.getGender());
                    doc.setProTitleImage(doctor.getProTitleImage());
                    doc.setOrgan(doctor.getOrgan());
                    doc.setTeams(doctor.getTeams());
                    map.put("doctor", doc);
                    mapList.add(map);
                }
                if (ObjectUtils.nullSafeEquals((String) objects[0], "2")) {//特需预约 加号转诊 住院转诊
                    OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                    Integer transferId = (Integer) objects[1];
                    Transfer tran = transferDAO.getById(transferId);
                    Transfer tr = new Transfer();
                    tr.setTransferId(tran.getTransferId());
                    tr.setTransferPrice(tran.getTransferPrice() == null ? 0.00 : tran.getTransferPrice());//医生设置的转诊费用
                    tr.setClinicPrice(tran.getClinicPrice() == null ? 0.00 : tran.getClinicPrice());//诊疗费用
                    tr.setTransferResult(tran.getTransferResult());
                    tr.setTransferStatus(tran.getTransferStatus());
                    tr.setEvaStatus(tran.getEvaStatus());//评价状态
                    tr.setConfirmClinicTime(tran.getConfirmClinicTime());
                    tr.setConfirmClinicAddr(StringUtils.isEmpty(tran.getConfirmClinicAddr()) ?
                            (tran.getConfirmOrgan() == null ? tran.getConfirmClinicAddr() : organDAO.getNameById(tran.getConfirmOrgan())) : tran.getConfirmClinicAddr());
                    tr.setPatientRequire(tran.getPatientRequire());
                    tr.setTargetDoctor(tran.getTargetDoctor());
                    tr.setTargetOrgan(tran.getTargetOrgan());
                    tr.setTransferResultType(tran.getTransferResultType());//标记是否是住院转诊记录
                    tr.setRequestTimeString(DateConversion.convertRequestDateForBuss(tran.getRequestTime()));
                    //门诊转诊 支付后 状态添加
                    AppointRecord ar = appointDao.getByTransferId(tran.getTransferId());
                    if (ar != null && tr.getTransferStatus().intValue() == 2) {
                        int status = ar.getAppointStatus().intValue();
                        if (status == 4 || status == 5) {
                            tr.setAppointRecordStatus(ar.getAppointStatus());
                            try {
                                String statusName = DictionaryController.instance().get("eh.bus.dictionary.AppointStatus").getText(ar.getAppointStatus());
                                tr.setAppointRecordStatusName(statusName);
                            } catch (ControllerException e) {
                               logger.error("findAllPatientAppointTransferList-->"+e);
                            }
                        }
                    }
                    map.put("transfer", tr);


                    // 审核前：目标医生信息 审核后：接收确认医生信息
                    Doctor doctor = doctorDAO.getByDoctorId(tran.getRequestMpi() != null ? tran.getTargetDoctor() : tran.getConfirmDoctor());
                    Doctor doc = new Doctor();
                    doc.setDoctorId(doctor.getDoctorId());
                    doc.setName(doctor.getName());
                    doc.setPhoto(doctor.getPhoto());
                    doc.setProfession(doctor.getProfession());// 专科
                    doc.setProTitle(doctor.getProTitle());// 职称
                    doc.setGender(doctor.getGender());
                    doc.setProTitleImage(doctor.getProTitleImage());
                    doc.setOrgan(doctor.getOrgan());
                    doc.setTeams(doctor.getTeams());
                    map.put("doctor", doc);
                    mapList.add(map);
                }

            }
        }
        return mapList;
    }

    /***
     * 查询所有患者 预约挂号和特需预约记录 -患者端查询列表
     * (包括医生帮患者预约:预约挂号下面[门诊有号预约 门诊有号转诊)
     * (特需预约下面[门诊加号转诊 住院转诊)
     * @param mpiId
     * @param start
     * @param limit
     * @return
     * @description 使用hibernate 原生SQL查询
     */
    @RpcService
    public List<Object[]> findAppointRecordAndTransfer(final String mpiId, final int start, final int limit) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "SELECT type,bizId" +
                                " FROM (" +
                                "  (SELECT AppointRecordID as bizId, mpiId, AppointDate as appointTime, '1' as type " +
                                "   FROM bus_AppointRecord WHERE " +
                                "  ( recordType = 0 OR ( recordType = 1 AND appointStatus != 9 ) )" +//当天医院确认中不显示 条件提前
                                "  AND (cancelUser = '系统' or cancelResean <> '超时未支付,系统自动取消' or cancelResean is null or cancelResean='')" +//当天超时未支付不显示
                                "  AND (   (appointUser=:mpiid " +
                                "    AND appointRoad = 5  " +
                                "   ) " +
                                "   OR (mpiId =:mpiid AND ifNULL(telClinicFlag,0) = 0 " +
                                "   AND (ifNUll(transferId,0) = 0 OR transferId in ( SELECT transferId FROM bus_Transfer where IFNULL(IsAdd,0)=0 and transferType<>3)))" +
                                "   ) )" +
                                " UNION " +
                                "   (SELECT TransferID as bizId,mpiId,RequestTime as appointTime,'2' as type " +
                                "     FROM bus_Transfer WHERE (requestMpi = :mpiid and payflag != 0) " +//患者端特需预约
                                "      or(mpiId = :mpiid and refuseflag is null " +//不是医生拒绝和系统自动拒绝的
                                "         and (transferStatus <> 9 or transferId in ( SELECT transferId FROM bus_AppointRecord where appointStatus=6 and mpiId = :mpiid ) )" +// 医生未取消
                                "         and IsAdd=1 " +//加号转诊
                                "         and transferResult=1 ) " +//加号转诊已接收
                                "   )" +
                                " ) as tab ");
                hql.append(" ORDER BY appointTime DESC ");

                Query q = ss.createSQLQuery(hql.toString());
                q.setParameter("mpiid", mpiId);
                q.setMaxResults(limit);
                q.setFirstResult(start);
                List<Object[]> result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * @param mpiId
     * @param start
     * @param limit
     * @return List<Map<String,Object>>
     * @function 患者预约挂号列表
     * @author zhangjr
     * @date 2015-12-15
     */
    @RpcService
    public List<Map<String, Object>> findPatAppointRecordList(
            final String mpiId, final int start, final int limit) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) {
                // TODO there has a pit!!
                StringBuilder hql = new StringBuilder("from AppointRecord ");//患者端普通预约
                hql.append(" WHERE (recordType = 0 OR (recordType=1 AND appointStatus!=9)) "); //当天医院确认中不显示 条件提前
                hql.append(" AND (cancelUser = '系统' or cancelResean <> '超时未支付,系统自动取消' or cancelResean is null or cancelResean='')  ");
                hql.append(" AND ( (appointUser=:mpiid AND appointRoad = 5 ) ");
                hql.append("  OR (mpiId =:mpiid  AND ifNull(telClinicFlag,0) = 0 ");
                hql.append("  AND ( ifNUll(transferId,0) = 0 OR transferId in ( SELECT transferId FROM Transfer where IFNULL(IsAdd,0)=0 and transferType<>3) ) ) ");
                hql.append(" ) ");
                hql.append(" order by appointDate desc ");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiid", mpiId);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<AppointRecord> as = q.list();
                setResult(as);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        List<Map<String, Object>> mapList = new ArrayList<>();
        for (AppointRecord appointRecord : action.getResult()) {
            Map<String, Object> map = new HashMap<>();
            AppointRecord ar = new AppointRecord();
            ar.setAppointRecordId(appointRecord.getAppointRecordId());
            ar.setConfirmClinicAddr(appointRecord.getConfirmClinicAddr());// 就诊地点
            ar.setStartTime(appointRecord.getStartTime());// 就诊时间
            ar.setAppointStatus(appointRecord.getAppointStatus());// 预约状态
            ar.setEvaStatus(appointRecord.getEvaStatus());//评价状态
            ar.setOrganId(appointRecord.getOrganId());// 就诊医院
            ar.setDoctorId(appointRecord.getDoctorId());
            ar.setClinicPrice(appointRecord.getClinicPrice());
            ar.setAppointRoad(appointRecord.getAppointRoad());
            ar.setRequestDate(DateConversion.convertRequestDateForBuss(appointRecord.getAppointDate()));
            ar.setRecordType(appointRecord.getRecordType());
            ar.setWorkDate(appointRecord.getWorkDate());
            ar.setWorkType(appointRecord.getWorkType());
            map.put("appointRecord", ar);// 放入预约记录

            Doctor doctor = doctorDAO.getByDoctorId(appointRecord.getDoctorId());
            Doctor doc = new Doctor();
            doc.setDoctorId(doctor.getDoctorId());
            doc.setName(doctor.getName());
            doc.setPhoto(doctor.getPhoto());
            doc.setProfession(doctor.getProfession());// 专科
            doc.setProTitle(doctor.getProTitle());// 职称
            doc.setGender(doctor.getGender());
            doc.setProTitleImage(doctor.getProTitleImage());
            doc.setOrgan(doctor.getOrgan());
            doc.setTeams(doctor.getTeams());
            map.put("doctor", doc);
            mapList.add(map);
        }
        return mapList;
    }

    /**
     * @param appointRecordId
     * @return Map
     * @function 患者挂号预约详情
     * @author zhangjr
     * @date 2015-12-16
     */
    @RpcService
    public Map<String, Object> getPatAppointRecordById(final int appointRecordId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        AppointRecord ar = getByAppointRecordId(appointRecordId);
        Map<String, Object> map = new HashMap<>();
//        SeeADoctorOrgan sadOrgan = null;
//        try {
//            sadOrgan = SeeADoctorController.instance().get(String.valueOf(ar.getOrganId()));
//        } catch (ControllerException e) {
//            logger.error(LocalStringUtil.format("getPatAppointRecordById get organ"));
//        }
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig hisServiceConfig = hisServiceConfigDAO.getByOrganId(ar.getOrganId());
        Integer callnum = hisServiceConfig.getCallNum();
        ar.setConnectCallNumberSystem(false);//zhangsl wx2.8有评价状态时不显示叫号系统
        if (callnum != null && callnum.intValue() == 1 && ObjectUtils.nullSafeEquals(0, ar.getEvaStatus())) {
            if (0 == ar.getAppointStatus() || 1 == ar.getAppointStatus() || 4 == ar.getAppointStatus() || 5 == ar.getAppointStatus()) {
                ar.setConnectCallNumberSystem(true);
            }
        }

        //获取评价信息
        //上海6院取消评价 2017-06-29 chenq
        if (!HisServiceConfigDAO.isShangHai6(ar.getOrganId())) {
            if (ObjectUtils.nullSafeEquals(2, ar.getEvaStatus())) {
                UserSevice userSevice = AppContextHolder.getBean("eh.userSevice", UserSevice.class);
                String mpiId = ar.getAppointUser().length() == 32 ? ar.getAppointUser() : ar.getMpiid();
                int requestUrtId = userSevice.getUrtIdByUserId(patientDAO.get(mpiId).getLoginId(), "patient");
                EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
                ar.setFeedbackId(evaDao.findEvaByServiceAndUser(ar.getDoctorId(), EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD,
                        String.valueOf(ar.getAppointRecordId()), requestUrtId, EvaluationConstant.EVALUATION_USERTYPE_PATIENT).get(0).getFeedbackId());
            }
        } else {
            ar.setEvaStatus(null);
        }
        //医生给患者预约标记
        if (!ObjectUtils.isEmpty(ar.getAppointUser()) && ar.getAppointUser().length() != 32) {
            ar.setStatusName("doc");
            //医生给患者有号转诊 获取 初步诊断和病情摘要
            if (!ObjectUtils.nullSafeEquals(ar.getTransferId(), 0)) {
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                Transfer tr = transferDAO.getById(ar.getTransferId());
                //map.put("transfer", tr);
                ar.setIsAdd(tr.getIsAdd());//是否加号转诊预约-0:false有号，1true加号
                ar.setDiagianName(tr.getDiagianName());//初步诊断
                ar.setPatientCondition(tr.getPatientCondition());//病情摘要

                //电子病历相关信息
                List<Otherdoc> cdrOtherdocs = DAOFactory.getDAO(CdrOtherdocDAO.class)
                        .findByClinicTypeAndClinicId(1, ar.getTransferId());
                map.put("cdrOtherdocs", cdrOtherdocs);
            }
        }
        // 预约记录
        map.put("appointRecord", ar);


        // 医生信息
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(ar.getDoctorId());
        Doctor doc = new Doctor();
        doc.setDoctorId(doctor.getDoctorId());
        doc.setName(doctor.getName());
        doc.setPhoto(doctor.getPhoto());
        doc.setProfession(doctor.getProfession());// 专科
        doc.setProTitle(doctor.getProTitle());// 职称
        doc.setGender(doctor.getGender());
        doc.setProTitleImage(doctor.getProTitleImage());
        doc.setOrgan(doctor.getOrgan());
        doc.setTeams(doctor.getTeams());//是否团队医生
        doc.setVirtualDoctor(doctor.getVirtualDoctor());
        map.put("doctor", doc);

        // 病人信息
        Patient patient = patientDAO.getByMpiId(ar.getMpiid());
        Patient pat = new Patient();
        pat.setMpiId(patient.getMpiId());
        pat.setPatientSex(patient.getPatientSex());
        pat.setPatientName(patient.getPatientName());
        pat.setAge(patient.getBirthday() == null ? 0 : DateConversion.getAge(patient.getBirthday()));
        pat.setPatientType(patient.getPatientType());
        map.put("patient", pat);

        //获取申请医生[医生给患者申请的预约挂号]
        if (!ObjectUtils.isEmpty(ar.getAppointUser()) && ar.getAppointUser().length() != 32) {
            Doctor reqDoctor = doctorDAO.getByDoctorId(Integer.valueOf(ar.getAppointUser()));
            Doctor reqDoc = new Doctor();
            reqDoc.setDoctorId(reqDoctor.getDoctorId());
            reqDoc.setName(reqDoctor.getName());
            reqDoc.setPhoto(reqDoctor.getPhoto());
            reqDoc.setProfession(reqDoctor.getProfession());// 专科
            reqDoc.setProTitle(reqDoctor.getProTitle());// 职称
            reqDoc.setGender(reqDoctor.getGender());
            reqDoc.setProTitleImage(reqDoctor.getProTitleImage());
            reqDoc.setOrgan(reqDoctor.getOrgan());
            reqDoc.setTeams(reqDoctor.getTeams());//是否团队医生
            map.put("requestDoctor", reqDoc);
        }
        return map;
    }

    /**
     * @param mpiId 病人索引
     * @param date  就诊日期
     * @return List<AppointRecord>
     * @throws
     * @Class eh.bus.dao.AppointRecordDAO.java
     * @Title: findAppointRecords
     * @Description: TODO 根据病人索引 和就诊日期查询 预约记录（妇保）
     * @author Zhongzx
     * @Date 2016-1-8下午4:13:25
     */
    @RpcService
    public List<AppointRecord> findAppointRecords(final String mpiId,
                                                  final Date date) {
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is needed");
        }
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select new eh.entity.bus.AppointRecord(a.appointRecordId,a.patientName,a.appointSourceId,a.appointDepartId,a.appointDepartName,a.doctorId,a.workDate,a.workType,a.sourceType,a.startTime,a.endTime,a.orderNum,a.appointStatus) from AppointRecord a where a.mpiid=:mpiId");
                if (date != null) {
                    hql.append(" and a.workDate=:date");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", mpiId);
                if (date != null) {
                    q.setParameter("date", date);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * @param @param  mpiId
     * @param @param  sourceId
     * @param @return
     * @return AppointRecord
     * @throws
     * @Class eh.bus.dao.AppointRecordDAO.java
     * @Title: addAppointRecordByMpiIdAndSourceId
     * @Description: TODO 根据患者主键和号源主键添加预约记录
     * @author AngryKitty
     * @Date 2016-1-8下午2:43:39
     */
    @RpcService
    public AppointRecord addAppointRecordByMpiIdAndSourceId(final String mpiId,
                                                            final Integer sourceId) {
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }
        if (sourceId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "sourceId is required");
        }
        AbstractHibernateStatelessResultAction<AppointRecord> action = new AbstractHibernateStatelessResultAction<AppointRecord>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                AppointRecord record = new AppointRecord();
                DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
                AppointRecordDAO recordDao = DAOFactory
                        .getDAO(AppointRecordDAO.class);
                AppointSourceDAO sourceDao = DAOFactory
                        .getDAO(AppointSourceDAO.class);
                AppointSource source = sourceDao.getById(sourceId);
                int userNum = source.getUsedNum();
                int sourceNum = source.getSourceNum();
                int orderNum = source.getOrderNum();
                int doctorId = source.getDoctorId();
                if ((userNum - sourceNum) >= 0) {
                    throw new DAOException(600, "【" + sourceId + "】号源已被使用！");
                }
                PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
                if (!patientDao.exist(mpiId)) {
                    throw new DAOException(404, "patient[" + mpiId
                            + "] not exist");
                }
                Patient patient = patientDao.getByMpiId(mpiId);
                record.setMpiid(mpiId);
                record.setPatientName(patient.getPatientName());
                record.setCertId(patient.getCardId());
                record.setLinkTel(patient.getMobile());
                try {
                    record.setDeviceId(SessionItemManager.instance().checkClientAndGet());
                } catch (Exception e) {
                    logger.info(LocalStringUtil.format("addAppointRecordByMpiIdAndSourceId get deviceId from session exception! errorMessage[{}]", e.getMessage()));
                }
                record.setOrganAppointId(source.getOrganSchedulingId());
                record.setAppointSourceId(source.getAppointSourceId());
                record.setOrganId(source.getOrganId());
                record.setAppointDepartId(source.getAppointDepartCode());
                record.setAppointDepartName(source.getAppointDepartName());
                record.setDoctorId(doctorId);
                record.setWorkDate(source.getWorkDate());
                record.setWorkType(source.getWorkType());
                record.setSourceType(source.getSourceType());
                record.setStartTime(source.getStartTime());
                record.setEndTime(source.getEndTime());
                record.setOrderNum(source.getOrderNum());
                record.setAppointRoad(1);// 默认预约途径都是现场预约
                record.setAppointStatus(0);// 默认预约状态为预约成功
                record.setAppointDate(new Date());
                record.setAppointUser(mpiId);// 预约提交人
                record.setAppointName(patient.getPatientName());// 预约提交人姓名
                record.setConfirmClinicAddr(source.getClinicAddr());
                record.setClinicPrice(source.getPrice());
                record.setTransferId(0);
                record.setSourceLevel(source.getSourceLevel());

                if ((sourceNum - userNum) > 1) {// 若号源只剩下一个就不用更新orderNum
                    orderNum += 1;
                }

                sourceDao.updateUsedNum(userNum + 1, orderNum,
                        source.getAppointSourceId());// 更新号源表
                Integer cloudClinic = source.getCloudClinic();
                if ((cloudClinic == null || cloudClinic == 0) && DateConversion.getDaysBetween(record.getWorkDate(), new Date()) == 0) {
                    record.setRecordType(1);
                } else {
                    record.setRecordType(0);
                }
                record.setEvaStatus(0);//评价状态 zhangsl 2017-02-13 19:28:49
                recordDao.save(record);// 保存预约记录
                List<DoctorDateSource> doctorAllSource = sourceDao
                        .totalByDoctorDate(doctorId, source.getSourceType());
                long doctorSumSource = 0;
                for (int i = 0; i < doctorAllSource.size(); i++) {
                    doctorSumSource = doctorSumSource
                            + doctorAllSource.get(i).getSourceNum();
                }
                if (doctorSumSource > 0) {
                    doctorDao.updateHaveAppointByDoctorId(doctorId, 1);
                } else {
                    logger.info("更新成无号源:doctorId:" + doctorId);
                    doctorDao.updateHaveAppointByDoctorId(doctorId, 0);
                }
                setResult(record);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * @param @param  recordId
     * @param @return
     * @return boolean
     * @throws
     * @Class eh.bus.dao.AppointRecordDAO.java
     * @Title: cancelAppointRecordByRecordId
     * @Description: TODO
     * @author AngryKitty
     * @Date 2016-1-8下午2:52:23
     */
    @RpcService
    public Boolean cancelAppointRecordByRecordId(final int recordId,
                                                 final String cancelUser, final String cancelName,
                                                 final String cancelResean) {
        if (!this.exist(recordId)) {
            throw new DAOException(404, "record[" + recordId + "] not exist");
        }
        final AppointRecord record = this.get(recordId);

        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                // 预约单状态不为0不能被取消
                if (record.getAppointStatus() > 0) {
                    throw new DAOException(600, "该预约单【" + recordId + "】不可被取消!");
                }

                AppointRecordDAO recordDao = DAOFactory
                        .getDAO(AppointRecordDAO.class);
                DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
                recordDao.updateCancel(recordId, new Date(), cancelUser,
                        cancelName, cancelResean);
                AppointSourceDAO sourceDao = DAOFactory
                        .getDAO(AppointSourceDAO.class);
                AppointSource source = sourceDao.get(record
                        .getAppointSourceId());
                sourceDao.updateUsedNum(source.getUsedNum() - 1,
                        source.getOrderNum(), source.getAppointSourceId());// 更新号源数据
                doctorDao.updateHaveAppointByDoctorId(source.getDoctorId(), 1);
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * @param @param appointRecordId
     * @param @param registerUser
     * @param @param registerName
     * @return void
     * @throws
     * @Class eh.bus.dao.AppointRecordDAO.java
     * @Title: registeRecord
     * @Description: TODO 确认预约单（挂号）
     * @author AngryKitty
     * @Date 2016-1-8下午3:35:00
     */
    @RpcService
    @DAOMethod(sql = "update AppointRecord set  appointStatus=2,registerDate=current_timestamp(), registerUser=:registerUser, registerName=:registerName, appointStatus=1 where appointRecordId=:appointRecordId")
    public abstract void updateRegisteRecord(
            @DAOParam("appointRecordId") int appointRecordId,
            @DAOParam("registerUser") String registerUser,
            @DAOParam("registerName") String registerName);

    /**
     * 添加医生账户收入
     *
     * @param ar
     * @author zhangx
     * @date 2016-1-18 下午5:27:27
     */
    public void addIncome(AppointRecord ar) {
        TransferDAO transferdao = DAOFactory.getDAO(TransferDAO.class);
        DoctorAccountDAO doctoraccountdao = DAOFactory.getDAO(DoctorAccountDAO.class);

        String appointUser = ar.getAppointUser();
        Integer appointRoad = ar.getAppointRoad();

        // 医生申请的预约
        if (appointRoad == 5 && appointUser.length() < 32) {

            Integer telClnic = ar.getTelClinicFlag()==null?Integer.valueOf(0):ar.getTelClinicFlag();
            if (telClnic == null || telClnic == 0) {
                logger.info("普通预约成功,为预约申请医生增加账户金额,申请医生id:" + appointUser);
                doctoraccountdao.addDoctorIncome(Integer.parseInt(appointUser), 6,
                        ar.getAppointRecordId(), 0);
            }

            //预约远程云门诊
            if (telClnic != null && telClnic == 1) {
                logger.info("预约远程云门诊成功,为预约申请医生增加账户金额,申请医生id:" + appointUser);
                doctoraccountdao.addDoctorIncome(Integer.parseInt(appointUser), 23,
                        ar.getAppointRecordId(), 0);
            }

        }

        // 患者申请的预约
        if (appointRoad == 5 && appointUser.length() == 32) {

        }

        // 转诊、特需预约
        if (appointRoad == 6) {

            // 医生申请的转诊，增加医生奖励收入
            int transId = ar.getTransferId();
            transferdao.addTransferIncome(transId);
        }
    }

    /**
     * 添加预约云门诊奖励(就诊结束给予接诊方/出诊方奖励)
     */
    public void addCloudIncome(List<AppointRecord> ars) {
        DoctorAccountDAO doctoraccountdao = DAOFactory
                .getDAO(DoctorAccountDAO.class);

        for (AppointRecord record : ars) {
            Integer telClnic = record.getTelClinicFlag()==null?0:record.getTelClinicFlag();
            Integer doctorId = record.getDoctorId();
            Integer clickObject = record.getClinicObject();//1接诊方；2出诊方
            Integer appointId = record.getAppointRecordId();

            //预约远程云门诊
            if (telClnic != null && telClnic == 1) {
                //接诊方
                if (clickObject == 1) {
                    logger.info("预约远程云门诊就诊结束,为接诊方医生增加账户金额,接诊方医生医生id:" + doctorId);
                    doctoraccountdao.addDoctorIncome(doctorId, 27, appointId, 0);
                }

                //出诊方
                if (clickObject == 2) {
                    logger.info("预约远程云门诊就诊结束,为出诊方医生增加账户金额,出诊方医生id:" + doctorId);
                    doctoraccountdao.addDoctorIncome(doctorId, 28, appointId, 0);
                }
            }

        }
    }

    /**
     * 预约取消，扣除奖励
     *
     * @param ar
     * @author zhangx
     * @date 2016-1-18 下午5:55:09
     */
    public void subtractIncome(final AppointRecord ar) {
        DoctorAccountDAO doctoraccountdao = DAOFactory
                .getDAO(DoctorAccountDAO.class);
        TransferDAO transferdao = DAOFactory.getDAO(TransferDAO.class);

        if (ar.getAppointRoad() == 5) {
            String appointUser = ar.getAppointUser();

            if (!StringUtils.isEmpty(appointUser) && appointUser.length() < 32) {
                Integer telClnic = ar.getTelClinicFlag()==null?Integer.valueOf(0):ar.getTelClinicFlag();

                //普通预约
                if (telClnic == null || telClnic == 0) {
                    logger.info("平台内部预约取消时,且是医生申请的普通预约，则给预约申请医生增加账户负收入,doctorId:"
                            + ar.getAppointUser());
                    doctoraccountdao.addDoctorIncome(
                            Integer.parseInt(ar.getAppointUser()), 7,
                            ar.getAppointRecordId(), 0);
                }

                //预约远程云门诊
                if (telClnic!=null && telClnic == 1) {
                    logger.info("平台内部预约取消时,且是医生申请的远程云门诊预约，则给预约申请医生增加账户负收入,doctorId:"
                            + ar.getAppointUser());
                    doctoraccountdao.addDoctorIncome(
                            Integer.parseInt(ar.getAppointUser()), 24,
                            ar.getAppointRecordId(), 0);
                }

            }

        } else {

            Transfer t = transferdao.getById(ar.getTransferId());
            Integer requestDocId = t.getRequestDoctor();

            if (requestDocId != null) {
                logger.info("平台内部预约取消时,且是医生申请的转诊，则给转诊申请医生增加账户负收入,doctorId:"
                        + requestDocId);
                doctoraccountdao.addDoctorIncome(requestDocId, 7,
                        ar.getAppointRecordId(), 0);
            }

        }
    }

    /**
     * 校验患者已经预约记录 AppointRecordWriteDAO移入
     */
    public void checkAppointRecordsBeforSave(AppointRecord o) {
        if (o.getTelClinicFlag() == null) {
            o.setTelClinicFlag(0);
        }
//        董大说去掉
//        if (o.getTelClinicFlag() != 2) {
//            if (!(o.getAppointRoad() == 6 && o.getAppointUser().length() == 32)) {
//                checkAppointList(o);
//            }
//        }
    }

    /**
     * 校验患者已经预约记录
     *
     * @param o
     * @desc 1.获取同一个患者同一天同一个科室或医师的预约记录</br> 2.同一病人同一个就诊日不能预约2次以上</br>3.预约记录>=2 且
     * 要添加的预约记录为普通门诊/未添加过的云门诊记录
     * @desc 2015-02-15 该预约记录条数限制只针对医生端的预约，医生端发起的转诊接收，患者端的预约
     */
    private void checkAppointList(AppointRecord o) {

        AppointRecordDAO appointRecordDAO = DAOFactory
                .getDAO(AppointRecordDAO.class);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String strWorkDate = sdf.format(o.getWorkDate());
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date workDate = null;
        try {
            workDate = df.parse(strWorkDate);
        } catch (ParseException e1) {
            throw new DAOException(e1);
        }
        // 蒋旭辉和王宁武 预约不限制
        if (o.getMpiid().equals("2c9081814cc5cb8a014ccf86ae3d0000")
                || o.getMpiid().equals("2c9081814cc3ad35014cc3e0361f0000")) {
            return;
        }

        // 获取同一个患者同一天同一个科室或医师的预约记录
        List<AppointRecord> list = appointRecordDAO
                .findByMpiIdAndWorkDateAndOrganId(o.getMpiid(), o.getOrganId(),
                        o.getAppointDepartId(), o.getDoctorId(), workDate);
        String doctorName = "";
        String organName = "";
        String workType = "";
        //根据纳里健康2.6版本 把预约转诊一天一次改为一天三次
        if (list.size() >= 3) {
            try {
                doctorName = DictionaryController.instance()
                        .get("eh.base.dictionary.Doctor")
                        .getText(list.get(0).getDoctorId());
                // 医院名称
                organName = DictionaryController.instance()
                        .get("eh.base.dictionary.Organ")
                        .getText(list.get(0).getOrganId());

                workType = DictionaryController.instance()
                        .get("eh.bus.dictionary.WorkType")
                        .getText(list.get(0).getWorkType());
            } catch (ControllerException e) {
                throw new DAOException(e);
            }

            // 就诊时间
            String WorkDate = sdf.format(list.get(0).getWorkDate());
            if (list.get(0).getAppointRoad() == 5 && list.get(0).getOrderNum() > 0) {
                throw new DAOException(602, "您已预约了" + doctorName + "医师("
                        + list.get(0).getAppointDepartName() + "|" + organName
                        + ")的" + WorkDate + workType + "第"
                        + list.get(0).getOrderNum() + "个就诊号，请勿重复预约！");
            } else {
                throw new DAOException(602, "您已预约了" + doctorName + "医师("
                        + list.get(0).getAppointDepartName() + "|" + organName
                        + ")的" + WorkDate + workType + "就诊号，请勿重复预约！");
            }

        }

        // 同一病人同一个就诊日不能预约2次以上 根据纳里健康2.6更改为同一病人同一个就诊日不能预约3次以上
        List<AppointRecord> list2 = appointRecordDAO.findByMpiIdAndWorkDate(
                o.getMpiid(), workDate, workDate);

        // 预约记录>=2 且 要添加的预约记录为普通门诊/未添加过的云门诊记录
        if (list2.size() >= 3 && !canAdd(o, list2)) {
            String msg = "您已预约了 " + sdf.format(workDate) + " ";
            String msgDoctorName = "";
            String msgOrganName = "";
            for (int i = 0; i < list2.size(); i++) {
                try {
                    int docId = list2.get(i).getDoctorId();
                    msgDoctorName = DictionaryController.instance()
                            .get("eh.base.dictionary.Doctor").getText(docId);
                    // 医院名称
                    int organId = list2.get(i).getOrganId();
                    msgOrganName = DictionaryController.instance()
                            .get("eh.base.dictionary.Organ").getText(organId);

                } catch (ControllerException e) {
                    throw new DAOException(e);
                }

                msg = msg + msgDoctorName + "医师(" + msgOrganName + ")、";
            }

            msg = msg.substring(0, msg.length() - 1) + "的就诊，不能再进行预约";
            throw new DAOException(602, msg);
        }

        // 同一个病人7天内预约不可超过3次   根据纳里健康2.6更改同一个病人7天内预约不可超过5次
        Date startDate = Context.instance().get("date.getDatetimeOfLastWeek",
                Date.class);
        Date endDate = Context.instance().get("date.getToday", Date.class);

        List<AppointRecord> list3 = appointRecordDAO.findByMpiIdAndAppointDate(
                o.getMpiid(), startDate, endDate);

        if (list3.size() >= 5 && !canAdd(o, list3)) {
            String msg = "您已预约了 ";
            String msgDoctorName = "";
            String msgOrganName = "";
            for (int i = 0; i < list3.size(); i++) {
                try {
                    int docId = list3.get(i).getDoctorId();
                    msgDoctorName = DictionaryController.instance()
                            .get("eh.base.dictionary.Doctor").getText(docId);
                    // 医院名称
                    int organId = list3.get(i).getOrganId();
                    msgOrganName = DictionaryController.instance()
                            .get("eh.base.dictionary.Organ").getText(organId);

                } catch (ControllerException e) {
                    throw new DAOException(e);
                }

                msg = msg + sdf.format(list3.get(i).getWorkDate())
                        + msgDoctorName + "医师(" + msgOrganName + ")、";
            }

            msg = msg.substring(0, msg.length() - 1) + "的就诊，不能再进行预约";
            throw new DAOException(602, msg);
        }
    }

    /**
     * 查询预约记录 是否 和目标列表中的云门诊预约记录相匹配</br>
     * <p>
     * 云门诊预约记录 且telClinicId相同--匹配</br>
     *
     * @param o    要添加的预约记录
     * @param list 已经存在的符合条件的预约记录
     * @return true:匹配；false:不匹配
     * @author zhangx
     * @date 2015-10-20下午7:53:44
     */
    private boolean canAdd(AppointRecord o, List<AppointRecord> list) {
        Boolean b = false;
        if (o.getTelClinicFlag() != null && o.getTelClinicFlag() == 1) {
            for (AppointRecord appointRecord : list) {
                if (appointRecord.getTelClinicFlag() != null
                        && appointRecord.getTelClinicFlag() == 1
                        && o.getTelClinicId().equals(
                        appointRecord.getTelClinicId())) {
                    b = true;
                }
            }
        }

        return b;
    }

    /**
     * 根据 排班id 查找 预约记录
     *
     * @param organSchedulingId
     * @return List<AppointRecord>
     * @author houxr
     * @date 2016-4-21 上午9:29:35
     */
    @DAOMethod(sql = "from AppointRecord where organSchedulingId=:organSchedulingId and workDate>CURDATE()")
    public abstract List<AppointRecord> findAppointRecordByOrganSchedulingId(
            @DAOParam("organSchedulingId") String organSchedulingId);

    /**
     * 根据号源appointSourceId获取预约记录
     *
     * @param appointSourceID
     * @return List<AppointRecord>
     * @author houxr
     * @date 2016-4-21 上午9:29:35
     */
    @DAOMethod(sql = "from AppointRecord where appointSourceID=:appointSourceID")
    public abstract List<AppointRecord> findAppointRecordByAppointSourceId(
            @DAOParam("appointSourceID") Integer appointSourceID);

    @DAOMethod(sql = "from AppointRecord where appointSourceID=:appointSourceID and appointStatus in(0,1,4,5) and startTime>NOW()")
    public abstract List<AppointRecord> findSuccessAppointRecordByAppointSourceId(
            @DAOParam("appointSourceID") Integer appointSourceID);


    /**
     * 供transfer.findTransferList使用
     *
     * @param clinicObject
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "select transferId from AppointRecord where clinicObject=:clinicObject and doctorId=:doctorId")
    public abstract List<Integer> findTransferIdByClinicObjectAndDoctorId(
            @DAOParam("clinicObject") Integer clinicObject, @DAOParam("doctorId") Integer doctorId);


    /**
     * 预约停诊短信通知申请者(患者预约的号源)
     *
     * @param appointRecordId
     * @author houxr
     * @date 2016-4-21 上午9:29:35
     */
    @RpcService
    public void sendSmsForSourceStopToAppointUser(Integer appointRecordId,Integer organId) {
       /* AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord appointRecord = dao.getByAppointRecordId(appointRecordId);
        SmsInfo info = new SmsInfo();
        info.setBusId(appointRecord.getAppointRecordId());
        info.setBusType("appointRecordStop");// 业务类型
        info.setSmsType("smsForSourceStopToAppointUser");
        info.setStatus(0);
        info.setOrganId(0);// 短信服务对应的机构， 0代表通用机构
        AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
        exe.execute();*/
        SmsInfo info = new SmsInfo();
        info.setBusId(appointRecordId);
        info.setBusType("SourceStopToApply");
        info.setSmsType("SourceStopToApply");
        info.setStatus(0);
        info.setOrganId(organId==null?0:organId);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    /**
     * 预约停诊短信通知申请医生和患者
     *
     * @author houxr
     * @date 2016-4-22 上午9:29:35
     */
    @RpcService
    public void sendSmsForSourceStopToApplyDoctorAndPatient(Integer appointRecordId,Integer organId) {
      /*  AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord appointRecord = dao.getByAppointRecordId(appointRecordId);
        SmsInfo info = new SmsInfo();
        info.setBusId(appointRecord.getAppointRecordId());
        info.setBusType("appointRecordStop");// 业务类型
        info.setSmsType("smsForSourceStopToApplyDoctorAndPatient");
        info.setStatus(0);
        info.setOrganId(0);// 短信服务对应的机构， 0代表通用机构
        AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
        exe.execute();*/
        //转ons
        SmsInfo info = new SmsInfo();
        info.setBusId(appointRecordId);
        info.setBusType("SourceStopToApplyAndPatient");
        info.setSmsType("SourceStopToApplyAndPatient");
        info.setStatus(0);
        info.setOrganId(organId==null?0:organId);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);

    }

    /**
     * 云门诊预约停诊短信通知申请医生和患者
     *
     * @author zhangsl
     * @date 2017-04-18 16:06:47
     */
    public void sendSmsForOrderPayCloundClinicSourceStop(Integer appointRecordId) {
        AppointRecordDAO appointRecordDAO=DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord ar=appointRecordDAO.getByTelClinicIdAndClinicObject(//取出诊方的记录
                appointRecordDAO.getByAppointRecordId(appointRecordId).getTelClinicId(),2);
        SmsInfo info = new SmsInfo();
        info.setBusId(ar.getAppointRecordId());
        info.setBusType("orderPayAppCancelMsg");
        info.setSmsType("orderPayAppCancelMsg");
        info.setExtendValue(AppointConstant.APPCANCEL_SOURCESTOP);
        info.setOrganId(0);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    /**
     * 运营平台预约信息统计查询
     *
     * @param timeType      申请类型:1按申请日期 2按就诊日期
     * @param startTime     转诊开始时间
     * @param endTime       转诊结束时间
     * @param telClinicFlag 是否云门诊标志 0全部 1非云门诊[线下0] 2云门诊(全部云门诊in[1 2] 预约云门诊1 实时云门诊2)
     * @param mpiId         患者主键
     * @param ar            预约信息
     * @param appointOragns 预约申请机构（集合）
     * @param organIds      预约目标机构（集合）
     * @param start         分页，开始条目数
     * @param limit         页数
     * @return List<AppointRecordAndDoctors>
     * @author houxr
     * @date 2016-5-25
     */
    @RpcService
    public QueryResult<AppointRecordAndDoctors> findAppointRecordAndDoctorsForOP(
            final Integer timeType, final Date startTime, final Date endTime,
            final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
            final List<Integer> appointOragns, final List<Integer> organIds,
            final int start, final int limit) {

        this.validateOptionForStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        HibernateStatelessResultAction<QueryResult<AppointRecordAndDoctors>> action = new AbstractHibernateStatelessResultAction<QueryResult<AppointRecordAndDoctors>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                Date sTime = DateConversion.firstSecondsOfDay(startTime);
                Date eTime = DateConversion.lastSecondsOfDay(endTime);
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                if (1 == timeType) {
                    countQuery.setParameter("startTime", sTime);
                    countQuery.setParameter("endTime", eTime);

                } else if (2 == timeType) {
                    countQuery.setParameter("startTime", startTime);
                    countQuery.setParameter("endTime", endTime);
                }
                total = ((Long) countQuery.uniqueResult()).intValue();//获取总条数*/
                hql.append(" order by a.appointDate desc ");
                Query query = ss.createQuery("select new eh.entity.bus.AppointRecordAndDoctors(a) " + hql.toString());
                if (1 == timeType) {
                    query.setParameter("startTime", sTime);
                    query.setParameter("endTime", eTime);

                } else if (2 == timeType) {
                    query.setParameter("startTime", startTime);
                    query.setParameter("endTime", endTime);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);

                List<AppointRecordAndDoctors> tfList = query.list();
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

                for (AppointRecordAndDoctors appointRecordAndDoctors : tfList) {
                    // 申请电话
                    String requestId = appointRecordAndDoctors.getAppointrecord().getAppointUser();
                    if (requestId.length() > 11) {
                        Patient pt = patientDAO.getByMpiId(requestId);
                        if (!StringUtils.isEmpty(pt.getMobile())) {
                            appointRecordAndDoctors.setRequestTel(pt.getMobile());
                        }
                    } else {
                        int requestDocId = Integer.parseInt(requestId);
                        Doctor reqDoctor = doctorDAO.getByDoctorId(requestDocId);
                        if (!StringUtils.isEmpty(reqDoctor.getMobile())) {
                            appointRecordAndDoctors.setRequestTel(reqDoctor.getMobile());
                        }
                    }
                    // 目标医生电话
                    int targerDocId = appointRecordAndDoctors.getAppointrecord().getDoctorId();
                    Doctor targetDoctor = doctorDAO.getByDoctorId(targerDocId);
                    if (!StringUtils.isEmpty(targetDoctor.getMobile())) {
                        appointRecordAndDoctors.setTargerDocTel(targetDoctor.getMobile());
                    }
                }
                QueryResult<AppointRecordAndDoctors> qResult = new QueryResult<AppointRecordAndDoctors>(
                        total, query.getFirstResult(), query.getMaxResults(), tfList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return (QueryResult<AppointRecordAndDoctors>) action.getResult();
    }


    /**
     * 根据状态统计
     *
     * @param timeType      申请类型:1按申请日期 2按就诊日期
     * @param startTime     转诊开始时间
     * @param endTime       转诊结束时间
     * @param telClinicFlag 是否云门诊标志 0全部 1非云门诊[线下0] 2云门诊(全部云门诊in[1 2] 预约云门诊1 实时云门诊2)
     * @param mpiId         患者主键
     * @param ar            预约信息
     * @param appointOragns 预约申请机构（集合）
     * @param organIds      预约目标机构（集合）
     * @param start         分页，开始条目数
     * @param limit         页数
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByStatus(final Integer timeType, final Date startTime, final Date endTime,
                                                          final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                                          final List<Integer> appointOragns, final List<Integer> organIds,
                                                          final int start, final int limit) {
        this.validateOptionForStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.appointStatus ");
                Query query = ss.createQuery("select a.appointStatus, count(a.appointRecordId) as count " + hql.toString());
                Date sTime = DateConversion.firstSecondsOfDay(startTime);
                Date eTime = DateConversion.lastSecondsOfDay(endTime);
                if (1 == timeType) {
                    query.setParameter("startTime", sTime);
                    query.setParameter("endTime", eTime);
                } else if (2 == timeType) {
                    query.setParameter("startTime", startTime);
                    query.setParameter("endTime", endTime);
                }
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            String status = hps[0].toString();
                            String statusName = DictionaryController.instance()
                                    .get("eh.bus.dictionary.AppointStatus").getText(status);
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


    private void validateOptionForStatistics(final Integer timeType, final Date startTime, final Date endTime,
                                             final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                             final List<Integer> appointOragns, final List<Integer> organIds,
                                             final int start, final int limit) {

        if (timeType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "筛选方式不能为空");
        }
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        if (telClinicFlag == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "是否云门诊标志不能为空");
        }
    }

    /**
     * 根据预约方式统计
     *
     * @param timeType      申请类型:1按申请日期 2按就诊日期
     * @param startTime     转诊开始时间
     * @param endTime       转诊结束时间
     * @param telClinicFlag 是否云门诊标志 0全部 1非云门诊[线下0] 2云门诊(全部云门诊in[1 2] 预约云门诊1 实时云门诊2)
     * @param mpiId         患者主键
     * @param ar            预约信息
     * @param appointOragns 预约申请机构（集合）
     * @param organIds      预约目标机构（集合）
     * @param start         分页，开始条目数
     * @param limit         页数
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByAppointRoad(final Integer timeType, final Date startTime, final Date endTime,
                                                               final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                                               final List<Integer> appointOragns, final List<Integer> organIds,
                                                               final int start, final int limit) {
        this.validateOptionForStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.appointRoad ");
                Query query = ss.createQuery("select a.appointRoad, count(a.appointRecordId) as count " + hql.toString());
                Date sTime = DateConversion.firstSecondsOfDay(startTime);
                Date eTime = DateConversion.lastSecondsOfDay(endTime);
                if (1 == timeType) {
                    query.setParameter("startTime", sTime);
                    query.setParameter("endTime", eTime);
                } else if (2 == timeType) {
                    query.setParameter("startTime", startTime);
                    query.setParameter("endTime", endTime);
                }
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            String status = hps[0].toString();
                            String statusName = DictionaryController.instance()
                                    .get("eh.bus.dictionary.AppointRoad").getText(status);
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
     * 根据申请机构统计
     *
     * @param timeType      申请类型:1按申请日期 2按就诊日期
     * @param startTime     转诊开始时间
     * @param endTime       转诊结束时间
     * @param telClinicFlag 是否云门诊标志 0全部 1非云门诊[线下0] 2云门诊(全部云门诊in[1 2] 预约云门诊1 实时云门诊2)
     * @param mpiId         患者主键
     * @param ar            预约信息
     * @param appointOragns 预约申请机构（集合）
     * @param organIds      预约目标机构（集合）
     * @param start         分页，开始条目数
     * @param limit         页数
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByRequestOrgan(final Integer timeType, final Date startTime, final Date endTime,
                                                                final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                                                final List<Integer> appointOragns, final List<Integer> organIds,
                                                                final int start, final int limit) {
        this.validateOptionForStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.appointOragn ");
                Query query = ss.createQuery("select a.appointOragn, count(a.appointRecordId) as count " + hql.toString());
                Date sTime = DateConversion.firstSecondsOfDay(startTime);
                Date eTime = DateConversion.lastSecondsOfDay(endTime);
                if (1 == timeType) {
                    query.setParameter("startTime", sTime);
                    query.setParameter("endTime", eTime);
                } else if (2 == timeType) {
                    query.setParameter("startTime", startTime);
                    query.setParameter("endTime", endTime);
                }
                List<Object[]> tfList = query.list();
                HashMap<Integer, Integer> mapStatistics = new HashMap<Integer, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            Integer consultOrganId = Integer.parseInt(hps[0].toString());
                            mapStatistics.put(consultOrganId, Integer.parseInt(hps[1].toString()));
                        } else {
                            if (hps[1] != null && Integer.parseInt(hps[1].toString()) > 0) {
                                Integer emptyCount = 0;
                                if (mapStatistics.get(0) != null) {
                                    emptyCount += Integer.parseInt(mapStatistics.get(0).toString());
                                } else {
                                    emptyCount = Integer.parseInt(hps[1].toString());
                                }
                                mapStatistics.put(0, emptyCount);
                            }
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
     * 根据目标机构统计
     *
     * @param timeType      申请类型:1按申请日期 2按就诊日期
     * @param startTime     转诊开始时间
     * @param endTime       转诊结束时间
     * @param telClinicFlag 是否云门诊标志 0全部 1非云门诊[线下0] 2云门诊(全部云门诊in[1 2] 预约云门诊1 实时云门诊2)
     * @param mpiId         患者主键
     * @param ar            预约信息
     * @param appointOragns 预约申请机构（集合）
     * @param organIds      预约目标机构（集合）
     * @param start         分页，开始条目数
     * @param limit         页数
     * @return HashMap<String, Integer>
     * @author andywang
     * @date 2016-11-30
     */
    public HashMap<String, Integer> getStatisticsByTargetOrgan(final Integer timeType, final Date startTime, final Date endTime,
                                                               final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
                                                               final List<Integer> appointOragns, final List<Integer> organIds,
                                                               final int start, final int limit) {
        this.validateOptionForStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        final StringBuilder preparedHql = this.generateHQLforStatistics(timeType, startTime, endTime, telClinicFlag, mpiId, ar, appointOragns, organIds, start, limit);
        HibernateStatelessResultAction<HashMap<Integer, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<Integer, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = preparedHql;
                hql.append(" group by a.organId ");
                Query query = ss.createQuery("select a.organId, count(a.appointRecordId) as count " + hql.toString());
                Date sTime = DateConversion.firstSecondsOfDay(startTime);
                Date eTime = DateConversion.lastSecondsOfDay(endTime);
                if (1 == timeType) {
                    query.setParameter("startTime", sTime);
                    query.setParameter("endTime", eTime);
                } else if (2 == timeType) {
                    query.setParameter("startTime", startTime);
                    query.setParameter("endTime", endTime);
                }
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


    private StringBuilder generateHQLforStatistics(
            final Integer timeType, final Date startTime, final Date endTime,
            final Integer telClinicFlag, final String mpiId, final AppointRecord ar,
            final List<Integer> appointOragns, final List<Integer> organIds,
            final int start, final int limit) {

        StringBuilder hql = new StringBuilder(
                "from AppointRecord a " +
                        " where 1=1 ");

        //申请查询类型:1按预约申请日期 2按就诊日期
        if (1 == timeType) {
            if (startTime != null && endTime != null ) {
                hql.append(" and a.appointDate between :startTime and :endTime");
            }

        } else if (2 == timeType) {
            if (startTime != null) {
                hql.append(" and a.workDate>=:startTime");
            }
            if (endTime != null) {
                hql.append(" and a.workDate<=:endTime");
            }
        }

        // 是否云门诊标记
        if (telClinicFlag != null) {
            if (telClinicFlag == 1) {//非云门诊0 或null
                hql.append(" and a.telClinicFlag not in (1,2) ");
            } else if (telClinicFlag == 2) {//全部云门诊
                hql.append(" and a.telClinicFlag in (1,2) ");
            } else if (telClinicFlag == 3) {//预约云门诊
                hql.append(" and a.telClinicFlag=1 ");
            } else if (telClinicFlag == 4) {//在线云门诊[实时云门诊]
                hql.append(" and a.telClinicFlag=2 ");
            } else {//全部
                hql.append("");
            }
        }

        // 添加申请机构条件
        if (appointOragns != null && appointOragns.size() > 0) {
            boolean flag = true;
            for (Integer i : appointOragns) {
                if (i != null) {
                    if (flag) {
                        hql.append(" and  a.appointOragn in (");
                        flag = false;
                    }
                    hql.append("'" + i + "',");
                }
            }
            if (!flag) {
                hql = new StringBuilder(hql.substring(0, hql.length() - 1) + ") ");
            }
        }

        // 添加目标机构条件
        if (organIds != null && organIds.size() > 0) {
            boolean flag = true;
            for (Integer i : organIds) {
                if (i != null) {
                    if (flag) {
                        hql.append(" and a.organId in (");
                        flag = false;
                    }
                    hql.append(i + ",");
                }
            }
            if (!flag) {
                hql = new StringBuilder(hql.substring(0, hql.length() - 1) + ") ");
            }
        }
        // 患者主键
        if (!StringUtils.isEmpty(mpiId)) {
            hql.append(" and a.mpiid= '" + mpiId + "'");
        }
        if (ar != null) {
            // 添加申请医生条件
            if (ar.getAppointUser() != null
                    && !StringUtils.isEmpty(ar.getAppointUser())) {
                hql.append(" and a.appointUser='" + ar.getAppointUser() + "'");
            }

            // 添加目标医生条件
            if (ar.getDoctorId() != null) {
                hql.append(" and a.doctorId=" + ar.getDoctorId());
            }

            // 添加预约单状态
            if (ar.getAppointStatus() != null) {
                hql.append(" and a.appointStatus=" + ar.getAppointStatus());
            }

            // 添加预约方式
            if (ar.getAppointRoad() != null) {
                hql.append(" and a.appointRoad=" + ar.getAppointRoad());
            }
        }
        return hql;
    }

    @DAOMethod(sql = "From AppointRecord where mpiid=:mpiid and appointUser=:appointUser and doctorId=:doctorId " +
            "and DATE_FORMAT(appointDate,'%y-%m-%d')=DATE_FORMAT(:appointDate,'%y-%m-%d') " +
//            "and transferId=0 and appointStatus<>2") 2 6 7  已取消 已退款 退款失败
            "and transferId=0 and appointStatus not in (2,6,7)")
    public abstract List<AppointRecord> findHasAppointByFour(
            @DAOParam("mpiid") String mpiId, @DAOParam("appointUser") String reMpiId,
            @DAOParam("doctorId") int doctorId, @DAOParam("appointDate") Date appointDate);

    //当天挂号未付款预约记录
    @DAOMethod(sql = "From AppointRecord where mpiid=:mpiid and appointUser=:appointUser and doctorId=:doctorId " +
            "and DATE_FORMAT(appointDate,'%y-%m-%d')=DATE_FORMAT(:appointDate,'%y-%m-%d') " +
            "and recordType=1 and payFlag=0 and transferId=0 and appointStatus not in (2,6,7)")//已取消 已退款 退款失败
    public abstract List<AppointRecord> findHasAppointToday(
            @DAOParam("mpiid") String mpiId, @DAOParam("appointUser") String reMpiId,
            @DAOParam("doctorId") int doctorId, @DAOParam("appointDate") Date appointDate);

    //当天挂号并付款的预约记录
    @DAOMethod(sql = "From AppointRecord where mpiid=:mpiid and appointUser=:appointUser and doctorId=:doctorId " +
            "and DATE_FORMAT(appointDate,'%y-%m-%d')=DATE_FORMAT(:appointDate,'%y-%m-%d') " +
            "and recordType=1 and payFlag=1 and transferId=0 and appointStatus not in (2,6,7)")//已取消 已退款 退款失败
    public abstract List<AppointRecord> findHasAppointTodayAndPayed(
            @DAOParam("mpiid") String mpiId, @DAOParam("appointUser") String reMpiId,
            @DAOParam("doctorId") int doctorId, @DAOParam("appointDate") Date appointDate);

    /**
     * 获取已约号源冲突数
     * <p>
     * 供transferdao-requestTransferClinic/this-addAppointRecordForCloudClinic调用
     *
     * @param inDoctor 接诊医生内码
     * @param start    预约开始时间
     * @param endT     预约结束时间
     * @return Long
     * @author luf 2016-6-21
     */
    public Long countTimeConflict(final int inDoctor, final Date start, final Date endT) {
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "select count(*) from AppointRecord where doctorId=:inDoctor and " +
                        "((startTime>:start and startTime<:endT)or(endTime>:start and endTime<:endT)) and appointStatus<>2";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("inDoctor", inDoctor);
                q.setParameter("start", start);
                q.setParameter("endT", endT);
                setResult((Long) q.uniqueResult());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据主键查询单条预约记录(返回预约记录对象+病人对象+目标医生对象+申请医生对象+对方医生对象)
     *
     * @param appointRecordId
     * @return
     * @author LF
     * @date 2016-7-20 luf:拷贝原 getFullAppointRecordById 添加排队信息出现按
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     * @date 2017-2-20 luf：所有预约相关视频状态只判断是否有设备在线
     */
    @RpcService
    public AppointRecordAndPatientAndDoctor getFullAppointRecordByIdWithQueue(
            int appointRecordId, int doctorId) {
        AppointService service = AppContextHolder.getBean("appointService", AppointService.class);
        String platformNgari = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ALL;
        return service.getPlatformFullAppointRecordByIdWithQueue(appointRecordId, doctorId, platformNgari);
    }

    /**
     * 根据telClinicId获取clinicStatus
     *
     * @param telClinicId
     * @return
     */
    public Integer getClinicStatusByTelClinicId(String telClinicId) {
        if (StringUtils.isEmpty(telClinicId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "telClinicId is required!");
        }
        List<AppointRecord> appointRecords = this.findByTelClinicId(telClinicId);
        if (null == appointRecords || appointRecords.isEmpty()) {
            return null;
        }
        AppointRecord ar = appointRecords.get(0);
        if (null == ar || null == ar.getClinicStatus()) {
            return null;
        }
        return ar.getClinicStatus();
    }


    /**
     * 查询有对接医院叫号系统的当天就诊的未提醒过的就诊号不为0预约单信息
     *
     * @param organIds
     * @return
     */
    @DAOMethod(sql = "FROM AppointRecord WHERE appointStatus in(0,1,4,5) AND startTime > :startPoint AND startTime < :endPoint AND workDate = CURDATE() AND orderNum > 0 AND (fiveNumberRemindFlag IS NULL OR fiveNumberRemindFlag=0) AND organID IN (:organIds)")
    public abstract List<AppointRecord> findTodaysAppointRecordWithNumAndNotRemind(
            @DAOParam("organIds") List<Integer> organIds,
            @DAOParam("startPoint") Date startPoint,
            @DAOParam("endPoint") Date endPoint);

    /**
     * 将排队叫号剩余5个提醒标志位改为“已提醒”
     *
     * @param appointRecordId
     */
    @RpcService
    @DAOMethod(sql = "UPDATE AppointRecord SET fiveNumberRemindFlag=1 WHERE appointRecordId=:appointRecordId")
    public abstract void updateAppointRecordFiveNumberRemindFlagTrue(
            @DAOParam("appointRecordId") Integer appointRecordId);

    /**
     * 查询预约等待时间大于一天（自然天）的记录
     *
     * @return
     */
    @DAOMethod(sql = "FROM AppointRecord WHERE appointStatus=0 AND TO_DAYS(WorkDate)-TO_DAYS(AppointDate)-1>0 AND TO_DAYS(WorkDate)-TO_DAYS(CURDATE())=1 AND (oneDayRemindFlag IS NULL OR oneDayRemindFlag=0) AND ((appointRoad=5 AND telClinicFlag=0) OR (appointRoad=6 AND telClinicFlag=0))")
    public abstract List<AppointRecord> findAppointListWaitTimeBigThanOneDay();

    /**
     * 将还有1天（自然天）就诊提醒标志位改为“已提醒”
     *
     * @param appointRecordId
     */
    @RpcService
    @DAOMethod(sql = "UPDATE AppointRecord SET oneDayRemindFlag=1 WHERE appointRecordId=:appointRecordId")
    public abstract void updateAppointRecordOneDayLeftRemindFlagTrue(
            @DAOParam("appointRecordId") Integer appointRecordId);

    /**
     * 处理过期的云门诊就诊状态
     */
    public void updateClinicStatusToEnd() {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                // 视频过的云门诊被置为已就诊
                String hql = new String("update AppointRecord set clinicStatus=2 where DATE_FORMAT(workDate,'%y-%m-%d')<DATE_FORMAT(NOW(),'%y-%m-%d') and clinicStatus=1");
                Query q = statelessSession.createQuery(hql);
                q.executeUpdate();
                // 未视频的云门诊置为爽约
                String hql1 = new String("update AppointRecord set clinicStatus=3 where DATE_FORMAT(workDate,'%y-%m-%d')<DATE_FORMAT(NOW(),'%y-%m-%d') and clinicStatus=0");
                Query q1 = statelessSession.createQuery(hql1);
                q1.executeUpdate();
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        action.getResult();
    }

    /**
     * 获取某日待就诊远程门诊服务(供appointService.findTodayUnDoRecordListByPlatform调用)
     * 查询条件：就诊时间为当天，预约成功的且就诊状态为未完成的，远程云门诊(预约界面预约的有号源的远程门诊+转诊界面的有号远程门诊转诊)，
     * 排序条件：
     * 出诊方今日待办排序说明：（前>后）
     * 1、已加入排队的患者 >  未加入排队的患者；
     * 2、已加入排队的患者：先加入的 > 后加入的；
     * 3、未加入排队的患者：预约就诊时间早的 > 晚的
     *
     * @param doctorId 医生id
     * @param now      今日时间
     * @param platform 视频流平台 值见CloudClinicSetConstant.java
     * @return
     */
    public List<AppointRecord> findTodayOutAppointList(final Integer doctorId, final Date now, final String platform) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                // 视频过的云门诊被置为已就诊
                String sql = "select a.* from " + "bus_AppointRecord" + " a LEFT JOIN " + " bus_CloudClinicSet " + "  b ON a.oppdoctor = b.doctorId AND b.platform =:platform LEFT JOIN " + "bus_cloudclinicqueue " + " q ON a.TelClinicID = q.TelClinicID AND q.QueueStatus = 0 AND DATE_FORMAT(createDate, '%y-%m-%d') = DATE_FORMAT(now(), '%y-%m-%d') WHERE a.doctorId =:doctorId AND date(a.workDate) = date(:date) AND a.appointStatus = 0 AND a.telClinicFlag = 1 AND a.ClinicObject = 2 AND( a.clinicStatus <=2 OR a.clinicStatus IS NULL) ORDER BY (a.clinicStatus <2 or  a.clinicStatus IS NULL) desc, b.onLineStatus DESC, (q.LastModify IS NOT NULL) DESC, q.LastModify, a.StartTime ";
                Query q = statelessSession.createSQLQuery(sql).addEntity("a", AppointRecord.class);
                q.setParameter("platform", platform);
                q.setParameter("date", now);
                q.setParameter("doctorId", doctorId);
                setResult(q.list());

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public List<AppointRecord> findTodayInAppointList(final Integer doctorId, final Date now) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "FROM AppointRecord a WHERE a.doctorId =:doctorId AND date(a.workDate) = date(:now) AND a.appointStatus = 0 AND a.telClinicFlag = 1 and a.clinicObject=1 AND( a.clinicStatus <=2 OR a.clinicStatus IS NULL)";

                Query q = statelessSession.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                q.setParameter("now", now);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @RpcService
    @DAOMethod(sql = "from AppointRecord where appointSourceId in :appointSourceId and (appointStatus=0 or appointStatus=4 or appointStatus=5)")
    public abstract List<AppointRecord> findByAppointSourceIdHasAppoint(@DAOParam("appointSourceId")List<Integer> appointSourceId);

    @DAOMethod
    @RpcService
    public abstract AppointRecord getByAppointSourceId(Integer appointSourceId);

    /**
     * 获取医生小结
     *
     * @param telClinicId
     * @param doctorId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select summary from AppointRecord where telClinicId=:telClinicId and doctorId=:doctorId")
    public abstract String getSummary(@DAOParam("telClinicId") String telClinicId, @DAOParam("doctorId") int doctorId);

    /**
     * 添加医生小结
     *
     * @param telClinicId
     * @param doctorId
     * @param summary
     * @author zhangsl
     * @Date 2017-01-10 11:02:44
     * app3.8消息改造
     */
    @RpcService
    public void addSummary(String telClinicId, int doctorId, String summary) throws ControllerException {
        if (StringUtils.isEmpty(telClinicId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "telClinicId is required!");
        }
        if (StringUtils.isEmpty(summary)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "summary is required!");
        }
        String s = this.getSummary(telClinicId, doctorId);
        if (!StringUtils.isEmpty(s)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "summary is not null");
        }
        this.updateSummary(telClinicId, doctorId, summary);
        //给下级医生发送系统消息
        AppointRecord appointRecord = this.getByTelClinicIdAndClinicObject(telClinicId, 2);
//      DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
//      String doctorName = doctorDAO.getNameById(doctorId);
//      String patientName = appointRecord.getPatientName();
//      String oppDocTel = doctorDAO.getMobileByDoctorId(appointRecord.getOppdoctor());
//      String msg = doctorName + "医生已为" + patientName + "患者备注门诊小结，请查看。";
//      String title = "系统提醒";

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2Ons(appointRecord.getAppointRecordId(), appointRecord.getOrganId(), "CloudClinicSummaryMsg", "CloudClinicSummaryMsg", null);
        logger.info("appointRecord[{}] add summary send msg to CloudClinicSummaryMsg success", appointRecord.getAppointRecordId());
//      SessionDetailService detailService = new SessionDetailService();
//      detailService.addSysTextMsgSummaryToReqDoc(appointRecord.getAppointRecordId(), oppDocTel, title, msg, false, true);
    }

    @DAOMethod(sql = "update AppointRecord set summary=:summary where telClinicId=:telClinicId and doctorId=:doctorId")
    public abstract void updateSummary(@DAOParam("telClinicId") String telClinicId,
                                       @DAOParam("doctorId") int doctorId, @DAOParam("summary") String summary);

    @DAOMethod(sql = "update AppointRecord set appointStatus=4 where appointRecordId =:appointRecordId")
    public abstract void updateExeRegisteAppointStatus(@DAOParam("appointRecordId") int appointRecordId);


    /**
     * 定时器  更新当天未支付超时的挂号记录 并且释放当天挂号的号源
     */
    @RpcService
    public void updateNoPayAppointList(int time) {
        Date date_end = DateConversion.getDateAftMinute(new Date(),-time);
        Date date_start = DateConversion.getDateAftMinute(new Date(),-time*3);

        HisServiceConfigDAO configDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        List<Integer> organs = configDAO.findByCanAppoint();
        List<AppointRecord> list = findRecordsOfTodayByOrganIds(date_start,date_end, organs);
        for (AppointRecord r : list) {
            Date appointDate = r.getAppointDate();
            logger.info("更新当天未支付记录：" + r.getAppointRecordId());
            updateStatus(2, "超时未支付,系统自动取消", r.getAppointRecordId());
            try {
                AppContextHolder.getBean("followUpdateService", FollowUpdateService.class).deleteByAppointRecordId(r.getAppointRecordId());
            } catch (Exception e) {
                logger.info("after updateNoPayAppointList deleteByAppointRecordId faild and appointRecordId is [{}]", r.getAppointRecordId());
            }
            AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
            AppointSource appointSource = appointSourceDAO.getByAppointSourceId(r.getAppointSourceId());
            if (appointSource.getUsedNum() > 0) {
                int usedNum = appointSource.getUsedNum() - 1;
                appointSourceDAO.updateUsedNumByAppointSourceId(usedNum, r.getAppointSourceId());
            }
            cancelAppoint(r.getAppointRecordId(),"系统", "系统", "超时未支付,系统自动取消");
        }
    }

    /**
     * 查询当天未支付超时的挂号记录
     */
    public List<AppointRecord> findRecordsOfToday(final Date start,final Date end, final Integer organId) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                Date time = new Date();
                String hql = "FROM AppointRecord a WHERE a.organId =:organId AND " +
                        "appointDate>=:start and appointDate <=:end AND a.appointStatus = 9 AND a.recordType=1  and (payFlag <> 1 or payFlag is null)";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("organId", organId);
                q.setParameter("start", start);
                q.setParameter("end", end);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
    
    public List<AppointRecord> findRecordsOfTodayByOrganIds(final Date start,final Date end, final List<Integer> organIds) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {            	
            	StringBuffer sb = new StringBuffer();
                if(organIds != null){
                	for(Integer organId : organIds){
                		sb.append(organId+",");
                	}	
                }
                String hql = "FROM AppointRecord a WHERE a.organId in ("+sb.toString().substring(0, sb.toString().length()-1)+") AND " +
                        "appointDate>=:start and appointDate <=:end AND a.appointStatus = 9 AND a.recordType=1  and (payFlag <> 1 or payFlag is null)";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("start", start);
                q.setParameter("end", end);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    public List<AppointRecord> findRecordsOfTodayAndPayTime(final int payTimeNow, final Integer organId) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                Date time = new Date();
                Date endTime = DateConversion.getDateAftMinute(time, payTimeNow);
                Date startTime = DateConversion.getDateAftMinute(time, payTimeNow*2);
                String hql = "FROM AppointRecord a WHERE a.organId =:organId AND " +
                        "a.appointDate >= :startTime AND a.appointDate <= :endTime AND (a.appointStatus = 0 or a.appointStatus = 4)  AND a.recordType=0 AND (a.telClinicFlag=0 or a.telClinicFlag is null)  AND length(a.appointUser)=32 and (a.appointSourceId is not null AND a.appointSourceId <> 0) AND (payFlag <> 1 or payFlag is null) AND clinicPrice>0 ";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("organId", organId);
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                List<AppointRecord> list=q.list();
                        setResult(list);
            }

        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 那我就按现在的需求改了，患者端预约的预约单，预约成功(预约成功未支付)后，这些预约单中，预约机构支持支付，
     * 且指定时间内未支付，取消，退款，支持支付的机构中指定时间，如果运营平台配置，则按配置来，如果未配置，默认15分钟
     */
    @RpcService
    public void updateTodayPayTime() {
        HisServiceConfigDAO configDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        List<HisServiceConfig> hisConfigs = configDAO.findHisConfigByCanPayAndPayCancelTime();
        for (HisServiceConfig hisServiceConfig : hisConfigs) {
            int payTime = hisServiceConfig.getPayTime();
            int organId = hisServiceConfig.getOrganid();
            int advanceTime=-payTime;
            List<AppointRecord> list = findRecordsOfTodayAndPayTime(advanceTime, organId);
            for (AppointRecord r : list) {
                logger.info("更新预约未支付记录：" + r.getAppointRecordId());
                cancel(r.getAppointRecordId(), "系统", "系统", "超时未支付,系统自动取消");
                /*try {
                    AppContextHolder.getBean("followUpdateService", FollowUpdateService.class).deleteByAppointRecordId(r.getAppointRecordId());
                } catch (Exception e) {
                    logger.info("after updateTodayPayTime deleteByAppointRecordId faild and appointRecordId is [{}]", r.getAppointRecordId());
                }*/
            }
        }

    }

    @DAOMethod(sql = "update AppointRecord set appointStatus=:appointStatus, cancelResean=:cancelResean where appointRecordId=:appointRecordId ")
    public abstract void updateStatus(@DAOParam("appointStatus") Integer appointStatus, @DAOParam("cancelResean") String cancelResean, @DAOParam("appointRecordId") int appointRecordId);

    @DAOMethod(sql = "update AppointRecord set appointStatus=:appointStatus where appointRecordId=:appointRecordId ")
    public abstract void updateStatusById(@DAOParam("appointStatus") Integer appointStatus, @DAOParam("appointRecordId") int appointRecordId);

    /**
     * 查询医生固定时间内未提醒的就诊云门诊预约记录
     *
     * @param lastDate 固定时间内
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select count(*),doctorId,startTime,appointRecordId From AppointRecord where telClinicFlag=1 and startTime>=:nowDate and startTime<=:lastDate and (remindFlag<>1 OR remindFlag is NULL) and appointStatus=0 group by doctorId order by startTime asc")
    public abstract List<Object[]> findOutDoctorsNotReminded(@DAOParam("nowDate") Date nowDate, @DAOParam("lastDate") Date lastDate);

    @RpcService
    @DAOMethod
    public abstract void updateRemindFlagByAppointRecordId(boolean remindFlag, int appointRecordId);

    /**
     * 预约支付时取消支付处理-->待支付
     * 当天挂号不处理，还是医院确认中。
     */
    @RpcService
    public void payFaild(int appointRecordId) {
        AppointRecord ar = get(appointRecordId);

        if (ar == null) {
            return;
//            throw  new DAOException(609, "预约记录不存在");
        }
        if (ar.getRecordType() != null && ar.getRecordType().intValue() == 1) {
            return;
        }
        int status = ar.getAppointStatus().intValue();
        if(status==0){
            ar.setAppointStatus(4);
            ar.setPayFlag(0);
            update(ar);
        }

    }

    public void cancelOverTimeNoPayOrder(Date deadTime) {
        List<AppointRecord> arList = findTimeOverNoPayOrder(deadTime);
        if (ValidateUtil.notBlankList(arList)) {
            for (AppointRecord ar : arList) {
                try {
                    ar.setCancelResean(PayConstant.OVER_TIME_AUTO_CANCEL_TEXT);
                    ar.setAppointStatus(2);
                    update(ar);
                    if (ValidateUtil.notNullAndZeroInteger(ar.getCouponId()) && ar.getCouponId() != -1) {
                        CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                        couponService.unlockCouponById(ar.getCouponId());
                    }
                } catch (Exception e) {
                    logger.error("cancelOverTimeNoPayOrder error, busId[{}], errorMessage[{}], stackTrace[{}]", ar.getAppointRecordId(), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                }
            }
        }
    }

    // 2017-05-15 luf：去除云门诊预约查询，添加 and (telClinicFlag=0 or telClinicFlag is null)
    @DAOMethod(sql = "from AppointRecord where appointStatus=4 AND payFlag = 0 AND appointDate < :deadTime and (telClinicFlag=0 or telClinicFlag is null)")
    public abstract List<AppointRecord> findTimeOverNoPayOrder(@DAOParam("deadTime") Date deadTime);

    @DAOMethod(sql = "from AppointRecord where appointStatus=4 AND payFlag = 0 AND workDate < :today and telClinicFlag>0")
    public abstract List<AppointRecord> findTimeOverNoPayCloud(@DAOParam("today") Date today);


    /**
     * 更新his返回数据服务(更新【机构号源编号】、【预约状态】)
     */
    public void updatePreParam(final String patientID, final String mzID, final String regId, final Double clinicPrice,final String regReceipt,final Integer appointRecordId) {
        HibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "update AppointRecord set ";

                if (clinicPrice == null) {
                    hql += (" clinicPrice= " + 0);
                } else {
                    hql += (" clinicPrice= " + clinicPrice.doubleValue());
                }
                if (!StringUtils.isEmpty(regId)) {
                    hql += (" ,regId= '" + regId + "'");
                }
                if (!StringUtils.isEmpty(patientID)) {
                    hql += (" ,clinicId= '" + patientID + "'");
                }
                if (!StringUtils.isEmpty(mzID)) {
                    hql += (" ,clinicMzID= '" + mzID + "'");
                }
                if (!StringUtils.isEmpty(regReceipt)) {
                    hql += (" ,regReceipt= '" + regReceipt + "'");
                }
                hql += " where appointRecordId=" + appointRecordId.intValue();
                Query q = statelessSession.createQuery(hql);
                q.executeUpdate();
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }

    /**
     * 按日期将预约成功或挂号成功的预约单更新为待评价状态(患者在平台有帐号)
     * 筛选条件：
     * [0预约成功,1挂号成功,4待支付,5已支付]
     * [普通预约记录，普通有号转诊]
     * [医生预约的且患者有账户,患者用户申请的且有账户]
     * [就诊时间为指定时间]
     * [未评价]
     *
     * @author zhangsl 2017-02-13 16:38:43
     */
    @DAOMethod(sql = "update AppointRecord set evaStatus=1 where evaStatus=0 and date(workDate)=date(:workDate) and ((CHAR_LENGTH(appointUser)=32 and appointUser in(select mpiId from Patient where loginId<>'')) or (CHAR_LENGTH(appointUser)<>32 and mpiid in(select mpiId from Patient where loginId<>''))) and (appointStatus=0 or appointStatus=1 or appointStatus=4 or appointStatus=5) and ifNull(cancelResean,'')='' and ifnull(telClinicFlag,0)=0 and (ifNUll(transferId,0) = 0 OR transferId in ( SELECT transferId FROM Transfer where IFNULL(IsAdd,0)=0 and transferType<>3))")
    abstract public void updateAppointEvaStatusByWorkDate(@DAOParam("workDate") Date workDate);

    /**
     * 按就诊日查询待评价状态预约单
     *
     * @author zhangsl 2017-02-14 16:38:43
     */
    @DAOMethod(sql = "From AppointRecord where workDate=:workDate and evaStatus=1", limit = 0)
    abstract public List<AppointRecord> findNeedEvaAppointByWorkDate(@DAOParam("workDate") Date workDate);

    /**
     * 根据预约单id更新评价状态为已评价
     *
     * @author zhangsl 2017-02-14 16:38:43
     */
    public Integer updateAppointEvaStatusById(final Integer appointRecordId) {
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("update AppointRecord set evaStatus=2 where appointRecordId=:appointRecordId and evaStatus=1");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("appointRecordId", appointRecordId);
                Integer count = q.executeUpdate();
                setResult(count);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 按就诊日查询未评价的待评价预约单
     *
     * @author zhangsl 2017-02-15 14:11:01
     */
    @DAOMethod(sql = "select appointRecordId From AppointRecord where endTime<:senvenDate and endTime>:startDate and evaStatus=1", limit = 0)
    abstract public List<Integer> findOverTimeNeedEvaAppointByWorkDate(@DAOParam("senvenDate") Date senvenDate,
                                                                       @DAOParam("startDate") Date startDate);

    public int updateToRefundSuccessForOffline(final String outTradeNo, final int offlineRefund) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "UPDATE AppointRecord SET payFlag=3, appointStatus=6, offlineRefund=:offlineRefund WHERE outTradeNo=:outTradeNo AND payFlag=1";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("outTradeNo", outTradeNo);
                q.setParameter("offlineRefund", offlineRefund);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public int updateForOffline(final String outTradeNo, final int offlineRefund) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "UPDATE AppointRecord SET offlineRefund=:offlineRefund WHERE outTradeNo=:outTradeNo AND payFlag=1";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("outTradeNo", outTradeNo);
                q.setParameter("offlineRefund", offlineRefund);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @DAOMethod
    public abstract AppointRecord getByTradeNo(String tradeNo);


    @DAOMethod(sql="update AppointRecord set orderNum=:orderNum where appointRecordId=:appointRecordId")
    public abstract void updateOrderNumByAppointRecordId(@DAOParam("orderNum") Integer orderNum,@DAOParam("appointRecordId") Integer appointRecordId);

    /**
     * 获取当前医生的云门诊申请记录，包括自己是接诊方和申请医生的记录
     * zhangsl 2017-03-20 14:49:10
     */
    @DAOMethod(sql = "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) " +
            "from AppointRecord a,Patient b where ifNull(a.telClinicFlag,0)<>0 and a.clinicObject=1 and (a.appointUser=concat(:doctorId,'') or (a.doctorId=:doctorId and a.appointStatus in (0,4,5,6) )) and b.mpiId=a.mpiid " +
            "order by case when appointStatus=4 and clinicStatus=0 then appointStatus end desc,case when appointStatus=4 and clinicStatus=0 then appointDate end,case when clinicStatus=0 and (appointStatus=0 or appointStatus=5) then clinicStatus end desc,startTime desc")
    public abstract List<AppointRecordAndPatient> findRequestClinicRecordList(@DAOParam("doctorId") int doctorId,
                                                                              @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

    /**
     * 获取当前医生的云门诊接收记录
     * zhangsl 2017-03-20 14:49:10
     * 修改云门诊筛选条件（取消待支付，增加已退款） zhangsl 2017-04-20 18:02:42
     */
    @DAOMethod(sql = "select new eh.entity.bus.AppointRecordAndPatient(a,b.patientSex,b.birthday,b.photo,b.patientType) " +
            "from AppointRecord a,Patient b where ifNull(a.telClinicFlag,0)<>0 and a.doctorId=:doctorId and a.clinicObject=2 and appointStatus in(0,1,5,6) and b.mpiId=a.mpiid " +
            "order by case when clinicStatus=0 and appointStatus in(0,1,5) then clinicStatus end desc,startTime desc")
    public abstract List<AppointRecordAndPatient> findReceiveClinicRecordList(@DAOParam("doctorId") int doctorId,
                                                                              @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

    /**
     * 获取医生被约的待就诊的云门诊信息
     *
     * @return doctorId:医生内码,count(doctorId):该医生被约数量,min(startTime):最近的就诊时间
     */
    @RpcService
    @DAOMethod(sql = "select doctorId,count(doctorId),min(startTime) from AppointRecord where startTime>now() and telClinicFlag=1 and clinicStatus=0 and appointStatus in(0,1,5) and clinicObject=2 group by doctorId", limit = 0)
    public abstract List<Object[]> findDocClinicOrderInfo();

    public List<Object[]> findAllUnClinicToday(final int doctorId, final List<String> telIds) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer notInTelId = new StringBuffer();
                for (String telId : telIds) {
                    notInTelId.append(" and a.telClinicId<>").append(telId);
                }
                StringBuilder hql = new StringBuilder("select d,a From AppointRecord a,Doctor d where a.oppdoctor=d.doctorId " +
                        "and DATE_FORMAT(a.workDate,'%y-%m-%d')=DATE_FORMAT(NOW(),'%y-%m-%d') and a.doctorId=:doctorId " +
                        "and a.clinicObject=1 and a.clinicStatus<=1").append(" and a.appointStatus=0").append(notInTelId).append(" order by a.clinicStatus,a.startTime");
                Query query = statelessSession.createQuery(hql.toString());
                query.setParameter("doctorId", doctorId);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 就诊确认更新服务
     * @param request
     * @return
     */
    public HisResponse updateTreatmentConfirm(final AppointTreatmentRequest request){
        HisResponse hisResponse = new HisResponse();
        final AppointRecord appointRecord = getByOrganAppointIdAndOrganId(request.getOrganAppointId(),request.getOrganId());
        if (null == appointRecord){
            throw new DAOException("can not find the record by appointRecordId: " + JSONUtils.toString(request));
        }

        logger.info("更新就诊确认信息");

        try{
            Date registerDate = request.getRegisterDate();
            String registerUser = request.getRegisterUser();
            String registerName = request.getRegisterName();
            if (registerDate != null){
                appointRecord.setRegisterDate(registerDate);
            }
            if (StringUtils.isNotEmpty(registerUser)){
                appointRecord.setRegisterUser(registerUser);
            }
            if (StringUtils.isNotEmpty(registerName)){
                appointRecord.setRegisterName(registerName);
            }
            appointRecord.setAppointStatus(10);
            update(appointRecord);

            hisResponse.setMsg("更新就诊确认成功");
            hisResponse.setMsgCode("200");
        }catch (Exception e){
            logger.error("更新就诊确认失败"+e.getMessage());
            hisResponse.setMsg("更新就诊确认失败");
            hisResponse.setMsgCode("-1");
        }
        return hisResponse;
    }

    /**
     * 获取当前医生的待支付云门诊申请记录数
     * zhangsl 2017-03-20 14:49:10
     */
    @DAOMethod(sql = "select count(*) from AppointRecord where ifNull(telClinicFlag,0)<>0 and clinicObject=1 and appointUser=concat(:doctorId,'') and appointStatus=4 and appointDate>:overTime")
    public abstract Long getCloudClinicOrderPayNum(@DAOParam("doctorId") int doctorId,
                                                                              @DAOParam("overTime") Date overTime);

    /**
     * 获取当前超时未支付的云门诊申请记录
     * zhangsl 2017-03-20 14:49:10
     */
    @DAOMethod(sql = "from AppointRecord where telClinicFlag<>0 and clinicObject=1 and appointStatus=4 and appointDate<=:overTime",limit =0)
    public abstract List<AppointRecord> findCloudClinicTimeOut(@DAOParam("overTime") Date overTime);

    @DAOMethod(sql = "update AppointRecord set tradeNo=:tradeNo,paymentDate=:paymentDate,payFlag=:payFlag where outTradeNo=:outTradeNo")
    public abstract void updatePayMessageByOutTradeNo(@DAOParam("tradeNo") String tradeNo, @DAOParam("paymentDate") Date paymentDate, @DAOParam("payFlag") Integer payFlag, @DAOParam("outTradeNo") String outTradeNo);

    @DAOMethod(sql = "update AppointRecord set qrCode=:qrCode where telClinicId=:telClinicId")
    public abstract void updateQrCodeCloud(@DAOParam("qrCode") String qrCode, @DAOParam("telClinicId") String telClinicId);

    @DAOMethod(sql = "update AppointRecord set payFlag=:payFlag,appointStatus=:appointStatus where telClinicId=:telClinicId and payFlag=1")
    public abstract void updatePayFlagAndStatusCloud(@DAOParam("payFlag") Integer payFlag,@DAOParam("appointStatus")Integer appointStatus, @DAOParam("telClinicId") String telClinicId);

    @DAOMethod(sql = "update AppointRecord set appointStatus=2,cancelResean=:cancelResean where telClinicId=:telClinicId")
    public abstract void updateCancelStatusCloud(@DAOParam("cancelResean")String cancelResean,@DAOParam("telClinicId") String telClinicId);
    
    @DAOMethod(sql = "From AppointRecord where appointDate<=:appointDate and payFlag in(1,4) and appointStatus in(2,7) and telClinicFlag in(1,2) and clinicObject=2 and outTradeNo is not null")
    public abstract List<AppointRecord> findAllNeedRefundCloud(@DAOParam("appointDate") Date nowDate);


    public  int updateRecordAfterPay( final String tradeNo, final Integer payFlag,  final Date paymentDate,  final String outTradeNo, final Integer appointRecordId){

        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "update AppointRecord set tradeNo=:tradeNo,payFlag=:payFlag,paymentDate=:paymentDate, outTradeNo=:outTradeNo " +
                        "where appointRecordId=:appointRecordId and payFlag=0";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("tradeNo", tradeNo);
                q.setParameter("payFlag", payFlag);
                q.setParameter("paymentDate", paymentDate);
                q.setParameter("outTradeNo", outTradeNo);
                q.setParameter("appointRecordId", appointRecordId);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();

    }

    /**
     * 根据id获取预约状态
     * @param id
     * @return
     */
    @DAOMethod(sql = "select appointStatus from AppointRecord where appointRecordId = :id")
    public abstract Integer getAppointStatusById(@DAOParam("id") Integer id);

    @DAOMethod(sql = "from AppointRecord where organId = :organid and mpiid =:mpiid ")
    public abstract List<AppointRecord> findbyOrganIdAndMpiid(@DAOParam("organid") Integer organid,@DAOParam("mpiid")  String mpiid);
    /**
     * 预约取消发送消息
     * @param ar
     */
    private  void sendSmsForAppointmentCancel(AppointRecord ar){
        String busType="";
        int payFlay=ar.getPayFlag()==null?0:ar.getPayFlag().intValue();
        int recordType=ar.getRecordType()==null?0:ar.getRecordType().intValue();
        if (ar.getAppointUser().length() == 32) {
            //患者预约
            //2017-5-26 22:43:00 wx3.2 消息模块优化，将预约成功推送消息拆分成-预约成功不支付+预约成功已支付+挂号成功
            if(recordType==1){
                //挂号成功
                logger.info("患者挂号成功后不能取消推送消息appointRecordId:[{}]",ar.getAppointRecordId());
            }else{
                if(payFlay==0){
                    //预约成功未支付
                    busType="PatAppointUnPayCancel";
                }else if(payFlay>=1 && payFlay<=4){
                    //预约成功已支付
                    busType="PatAppointPayCancel";
                }
            }
            logger.info("患者预约挂号成功后取消推送消息busType:[{}],appointRecordId:[{}]", busType, ar.getAppointRecordId());
        } else {
            //医生预约

            int appointRoad=ar.getAppointRoad()==null? AppointRoadConstant.NORMAL_APPOINTMENT:ar.getAppointRoad().intValue();
            int transferId=ar.getTransferId()==null?0:ar.getTransferId().intValue();

            if(appointRoad==AppointRoadConstant.NORMAL_APPOINTMENT){
                if(transferId>0 && payFlay==0){
                    //有号转诊成功-未支付-取消
                    busType="DocHasSourceTransferUnPayCancel";
                }else if(transferId>0 && payFlay>=1 && payFlay<=4){
                    //有号转诊成功-已支付-取消
                    busType="DocHasSourceTransferAndPayCancel";
                }else if(transferId==0 && payFlay==0){
                    //医生预约成功-未支付-取消
                    busType="DocAppointUnPayCancel";
                }else if(transferId==0 && payFlay>=1 && payFlay<=4){
                    //医生预约成功-已支付-取消
                    busType="DocAppointPayCancel";
                }
            }else{
                if(payFlay==0){
                    //加号转诊成功未支付-取消
                    busType="DocTransferConfirmUnPayCancel";
                }else if(payFlay>=1 && payFlay<=4){
                    //加号转诊成功已支付-取消
                    busType="DocTransferConfirmAndPayCancel";
                }

            }

            logger.info("医生预约成功后取消推送消息busType:[{}],appointRecordId:[{}]", busType, ar.getAppointRecordId());
        }

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2Ons(ar.getAppointRecordId(), ar.getOrganId(), busType, busType, ar.getDeviceId());

    }


    @DAOMethod(sql = "from AppointRecord where regId = :regId order by paymentDate desc")
    public abstract List<AppointRecord> findByRegId(@DAOParam("regId") String regId);

    @DAOMethod(sql = "from AppointRecord where tradeNo = :tradeNo order by paymentDate desc")
    public abstract List<AppointRecord> findByTradeNo(@DAOParam("tradeNo") String tradeNo);
    public List<AppointRecord> findNDaysgAppointRecords(final Integer days, final ArrayList<Integer> status, final List<Integer> organIds, final Long seconds) {

        AbstractHibernateStatelessResultAction<List<AppointRecord>> action = new AbstractHibernateStatelessResultAction<List<AppointRecord>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {

                StringBuffer findAppointRecordsSql = new StringBuffer(" from AppointRecord where 1=1");

                if (days != null) {
                    findAppointRecordsSql.append(" and DATEDIFF( :now,appointDate)<= :days");
                }
                if (seconds != null) {
                    findAppointRecordsSql.append(" and TIMESTAMPDIFF(SECOND,appointDate, :now) >= :seconds");
                }

                if (CollectionUtils.isNotEmpty(status)) {
                    findAppointRecordsSql.append(" and appointStatus in :status");
                }
                if (CollectionUtils.isNotEmpty(organIds)) {
                    findAppointRecordsSql.append(" and organId in :organIds");
                }
                findAppointRecordsSql.append(" order by AppointDate desc");

                Query sqlQuery = statelessSession.createQuery(findAppointRecordsSql.toString());

                sqlQuery.setParameter("now", new java.util.Date());

                if (days != null) {
                    sqlQuery.setParameter("days", days.intValue());
                }
                if (seconds != null) {
                    sqlQuery.setParameter("seconds", seconds.longValue());
                }
                if (CollectionUtils.isNotEmpty(status)) {
                    sqlQuery.setParameterList("status", status);
                }
                if (CollectionUtils.isNotEmpty(organIds)) {
                    sqlQuery.setParameterList("organIds", organIds);
                }

                setResult(sqlQuery.list());

            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }
}
