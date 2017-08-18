package eh.base.dao;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.base.constant.*;
import eh.bus.constant.SearchConstant;
import eh.bus.dao.AppointDepartDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.dao.SearchContentDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.OrganConfig;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.SearchContent;
import eh.utils.DateConversion;
import eh.utils.ValidateUtil;
import org.apache.commons.lang.BooleanUtils;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.StatelessSession;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.*;

public abstract class QueryDoctorListDAO extends
        HibernateSupportDelegateDAO<Doctor> {

    private Logger logger = LoggerFactory.getLogger(QueryDoctorListDAO.class);

    public QueryDoctorListDAO() {
        super();
        this.setEntityName(Doctor.class.getName());
        this.setKeyField("doctorId");
    }

    /**
     * 转诊会诊医生列表查询服务
     *
     * @param buesType   1转诊，2会诊,0咨询预约
     * @param department
     * @param organId
     * @return
     * @throws DAOException
     */
    @RpcService
    public List<Doctor> queryDoctorList(final int bussType,
                                        final Integer department, final int organId) throws DAOException {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                if (bussType == 1) {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e,ConsultSet c WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId AND d.doctorId=c.doctorId and c.transferStatus=1 and d.status=1 order by d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    List<Doctor> list1 = q.list();
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);
                    }
                    setResult(list);
                } else if (bussType == 2) {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e,ConsultSet c WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId AND d.doctorId=c.doctorId and c.meetClinicStatus=1 and d.status=1 order by d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    List<Doctor> list1 = q.list();
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);
                    }
                    setResult(list);
                } else {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId and d.status=1 order by d.haveAppoint desc,d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    List<Doctor> list1 = q.list();
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);
                    }
                    setResult(list);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 转诊会诊医生列表查询服务(分页)
     *
     * @param bussType   1转诊，2会诊,0咨询预约
     * @param department
     * @param organId
     * @return
     * @throws DAOException
     */
    @RpcService
    public List<Doctor> queryDoctorListWithPage(final int bussType,
                                                final Integer department, final int organId, final int start)
            throws DAOException {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                if (bussType == BussTypeConstant.TRANSFER) {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e,ConsultSet c WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId AND d.doctorId=c.doctorId and c.transferStatus=1 and d.status=1  order by d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    q.setMaxResults(10);
                    q.setFirstResult(start);
                    List<Doctor> list1 = q.list();
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);

                    }
                    setResult(list);
                } else if (bussType == BussTypeConstant.MEETCLINIC) {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e,ConsultSet c WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId AND d.doctorId=c.doctorId and c.meetClinicStatus=1 and d.status=1 order by d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    q.setMaxResults(10);
                    q.setFirstResult(start);
                    List<Doctor> list1 = q.list();
                    DoctorTabDAO doctorTabDAO=DAOFactory.getDAO(DoctorTabDAO.class);
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        //zhangsl 2017-05-26 15:25:51会诊中心标记新增
                        d.setMeetCenter(doctorTabDAO.getMeetTypeByDoctorId(d.getDoctorId()));
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);
                    }
                    setResult(list);
                } else {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId  and d.status=1 order by d.haveAppoint desc,d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    q.setMaxResults(10);
                    q.setFirstResult(start);
                    List<Doctor> list1 = q.list();
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);
                    }
                    setResult(list);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 支付宝医生列表获取
     *
     * @param appointDepartCode
     * @param organId
     * @return
     * @throws DAOException
     */
    @RpcService
    public List<Doctor> queryDoctorListAlipay(final String appointDepartCode,
                                              final int organId) throws DAOException {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String appointDepartCodeV = appointDepartCode;
                String hql = new String(
                        "SELECT DISTINCT d from Doctor d WHERE d.doctorId IN(SELECT DISTINCT doctorId from AppointSource where (appointDepartCode=:appointDepartCode or appointDepartCode=:appointDepartCodeV) and organId=:organId) and d.status=1 and (d.teams=0 or d.teams is null) order by d.orderNum");
                Query q = ss.createQuery(hql);
                q.setParameter("appointDepartCode", appointDepartCode);
                if (appointDepartCode.indexOf("A") != -1) {// 将名医号源和专家号源一同返回
                    appointDepartCodeV = appointDepartCode.replace("A", "VA");
                } else {
                    appointDepartCodeV = appointDepartCode + "V";
                }
                q.setString("appointDepartCodeV", appointDepartCodeV);
                q.setParameter("organId", organId);
                list = q.list();
                setResult(list);

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据机构和科室查询医生列表
     * <p>
     * eh.base.dao
     *
     * @param department
     * @param organId
     * @param flag       标志-0咨询1预约
     * @return List<HashMap<String,Object>>
     * @author luf 2016-2-26
     */
    @RpcService
    public List<HashMap<String, Object>> queryDoctorListForHealth(
            int department, int organId, int flag) {
        List<Doctor> doctors = queryDoctorList2(department, organId, flag);
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor : doctors) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            if (ValidateUtil.isNotTrue(doctor.getTeams()) && ValidateUtil.blankString(doctor.getGender())) {
                doctor.setGender("1");
            }
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctorId);
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 咨询/预约医生列表
     * <p>
     * eh.base.dao
     *
     * @param bussType
     * @param department
     * @param organId
     * @param flag       标志-0咨询1预约
     * @return List<Doctor>
     * @throws DAOException
     * @author luf 2016-2-26
     */
    public List<Doctor> queryDoctorList2(final Integer department,
                                         final int organId, final int flag) throws DAOException {
        List<Doctor> ds = new ArrayList<Doctor>();
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer(
                        "SELECT DISTINCT d from Doctor d,Employment e,ConsultSet c WHERE d.doctorId=e.doctorId "
                                + "AND e.department=:department AND e.organId=:organId and d.status=1");
                if (flag == 0) {
                    //2016-6-2 luf:放开团队医生限制，添加or d.teams=1
                    //2016-7-15 luf:业务设置为null是排序出错bug修复，添加IFNULL(,0)
                    hql.append(" and (((d.idNumber is not null and d.idNumber<>:empty)or d.teams=1) and d.doctorId=c.doctorId) order by (IFNULL(c.onLineStatus,0)+IFNULL(c.appointStatus,0)) DESC,d.goodRating DESC");
                } else {
                    // 2016-3-5 luf:患者端预约中按照机构、科室找医生需要添加普通挂号科室医生，添加and d.name
                    // like \"%普通%\"
                    //2016-6-2 luf：放开团队医生限制，添加or d.teams=1
                    //2016-7-15 luf:业务设置为null是排序出错bug修复，添加IFNULL(,0)
                    //2016-7-22 luf:更改排序优先级，将有号的置顶，然后排虚拟医生
                    hql.append(" and ((d.idNumber is not null and d.idNumber<>:empty and d.doctorId=c.doctorId)or d.teams=1 or ((d.idNumber is null or d.idNumber=:empty) and d.generalDoctor=1)) "
                            + "order by d.haveAppoint DESC,d.virtualDoctor DESC,(IFNULL(d.haveAppoint,0)+IFNULL(c.patientTransferStatus,0)) DESC,c.patientTransferStatus DESC,d.goodRating DESC");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("department", department);
                q.setParameter("organId", organId);
                q.setParameter("empty", "");
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        ds = action.getResult();
        List<Doctor> results = new ArrayList<Doctor>();
        if (ds == null || ds.size() == 0) {
            return results;
        }
        for (Doctor d : ds) {
            d.setDepartment(department);
            d.setOrgan(organId);
            results.add(d);
        }
        return results;
    }

    /**
     * 健康端医生列表
     *
     * @param organ      机构
     * @param profession 专科
     * @return List<HashMap<String, Object>>
     * @author luf
     */
    @RpcService
    public List<HashMap<String, Object>> queryDoctorListByProfession(
            final int organ, final String profession) {
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "from Doctor WHERE profession like :profession AND organ=:organ and status=1 order by haveAppoint desc,orderNum";
                Query q = ss.createQuery(hql);
                q.setParameter("profession", profession + "%");
                q.setParameter("organ", organ);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> ds = action.getResult();
        for (Doctor d : ds) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = d.getDoctorId();
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctorId);
            result.put("doctor", d);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 转诊会诊医生列表查询服务（原queryDoctorList）-原生
     * <p>
     * 针对转诊部分添加或有号源限制
     * <p>
     * eh.base.dao
     *
     * @param bussType   1转诊，2会诊,3咨询,4预约
     * @param department
     * @param organId
     * @return
     * @throws DAOException List<Doctor>
     * @author luf 2016-3-10
     */
    @RpcService
    public List<Doctor> queryDoctorListTransferWithApp(final int bussType,
                                                       final Integer department, final int organId) throws DAOException {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                if (bussType == 1) {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e,ConsultSet c WHERE "
                                    + "d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId "
                                    + "AND d.doctorId=c.doctorId and (c.transferStatus=1 or d.haveAppoint=1) "
                                    + "and d.status=1 order by d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    List<Doctor> list1 = q.list();
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);
                    }
                    setResult(list);
                } else if (bussType == 2) {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e,ConsultSet c WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId AND d.doctorId=c.doctorId and c.meetClinicStatus=1 and d.status=1 order by d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    List<Doctor> list1 = q.list();
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);
                    }
                    setResult(list);
                } else {
                    String hql = new String(
                            "SELECT DISTINCT d from Doctor d,Employment e WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId and d.status=1 order by d.haveAppoint desc,d.orderNum");
                    Query q = ss.createQuery(hql);
                    q.setParameter("department", department);
                    q.setParameter("organId", organId);
                    List<Doctor> list1 = q.list();
                    for (int i = 0; i < list1.size(); i++) {
                        Doctor d = list1.get(i);
                        d.setDepartment(department);
                        d.setOrgan(organId);
                        list.add(d);
                    }
                    setResult(list);
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> docList = action.getResult();
        List<Doctor> targets = new ArrayList<Doctor>();
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        DoctorTabDAO doctorTabDAO=DAOFactory.getDAO(DoctorTabDAO.class);
        for (Doctor doctor : docList) {
            Integer docId = doctor.getDoctorId();
            ConsultSet cs = csDao.get(docId);
            Integer isOpen = 0;
            if(bussType==BussTypeConstant.MEETCLINIC){//zhangsl 2017-05-26 15:25:51会诊中心标记新增
                doctor.setMeetCenter(doctorTabDAO.getMeetTypeByDoctorId(docId));
            }
            if (cs != null) {
                switch (bussType) {
                    case BussTypeConstant.TRANSFER:
                        isOpen = cs.getTransferStatus();
                        break;
                    case BussTypeConstant.MEETCLINIC:
                        isOpen = cs.getMeetClinicStatus();
                        break;
                    case BussTypeConstant.CONSULT:
                    case BussTypeConstant.APPOINTMENT:
                        if ((cs.getOnLineStatus() != null && cs.getOnLineStatus() == 1) || (cs.getAppointStatus() != null && cs.getAppointStatus() == 1)) {
                            isOpen = 1;
                        }
                }
            }
            doctor.setIsOpen(isOpen);
            targets.add(doctor);
        }
        return targets;
    }

    /**
     * 预约/转诊医生列表日期栏服务
     *
     * @param department
     * @param organId
     * @return List<HashMap<String, Object>>
     */
    @RpcService
    public List<HashMap<String, Object>> effectiveWorkDates(final int department, final int organId) {
        HibernateStatelessResultAction<List<Date>> action = new AbstractHibernateStatelessResultAction<List<Date>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                HisServiceConfigDAO hscDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                Boolean can = hscDao.isServiceEnable(organId, ServiceType.CANAPPOINTTODAY);
                //2016-6-6 luf:不显示接诊方号源日期， 添加 AND s.cloudClinicType<>2
                String hql = "SELECT DISTINCT s.workDate from Doctor d,Employment e,AppointSource s WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId and d.status=1 AND s.doctorId=d.doctorId AND s.sourceNum-s.usedNum>0 AND s.startTime>:startTime AND s.stopFlag=0 AND (s.cloudClinicType<>2 or (s.cloudClinicType is NULL)) GROUP BY s.workDate ORDER BY s.workDate";
                Query q = ss.createQuery(hql);
                if (can) {
                    String date = DateConversion.getDateFormatter(new Date(), "yyyy-MM-dd");
//                    q.setParameter("startTime", DateConversion.getCurrentDate(date + " 12:00:00", "yyyy-MM-dd HH:mm:ss"));
                    // 当天号源均为可约
                    q.setParameter("startTime", DateConversion.getCurrentDate(date + " 00:00:00", "yyyy-MM-dd HH:mm:ss"));
                } else {
                    q.setParameter("startTime", DateConversion.getFormatDate(DateConversion.getDaysAgo(-1), "yyyy-MM-dd"));
                }
                q.setParameter("department", department);
                q.setParameter("organId", organId);
                q.setMaxResults(30);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Date> dates = action.getResult();
        List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
        if (dates == null || dates.size() <= 0) {
            return results;
        }
        for (Date d : dates) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            String week = DateConversion.getWeekOfDate(d);
            map.put("date", d);
            map.put("week", week.replace("星期", "周"));
            results.add(map);
        }
        return results;
    }

    /**
     * 预约/转诊医生列表服务
     *
     * @param department
     * @param organId
     * @param date
     * @param start
     * @param limit
     * @return List<Doctor>
     */
    @RpcService
    public List<Doctor> effectiveSourceDoctors(final int department, final int organId, final Date date, final int start, final int limit) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "SELECT DISTINCT d from Doctor d,Employment e,AppointSource s WHERE d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId and d.status=1 AND s.doctorId=d.doctorId AND s.sourceNum-s.usedNum>0 AND ((s.startTime>NOW() and (s.cloudClinic=0 or s.cloudClinic is null)) or s.cloudClinic=1) AND s.stopFlag=0 AND s.workDate=:workDate ORDER BY d.haveAppoint desc,d.orderNum";
                Query q = ss.createQuery(hql);
                q.setParameter("department", department);
                q.setParameter("organId", organId);
                q.setParameter("workDate", date);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> ds = action.getResult();
        List<Doctor> result = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor d : ds) {
            Doctor doctor = new Doctor();
            doctor.setDoctorId(d.getDoctorId());
            doctor.setName(d.getName());
            doctor.setGender(d.getGender());
            doctor.setProfession(d.getProfession());
            doctor.setDomain(d.getDomain());
            doctor.setProTitle(d.getProTitle());
            doctor.setPhoto(d.getPhoto());
            doctor.setOrgan(d.getOrgan());
            doctor.setHaveAppoint(d.getHaveAppoint());
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            result.add(doctor);
        }
        return result;
    }

    /**
     * 接诊医生列表
     *
     * @param bussType   业务类型-1转诊，2会诊,3咨询,4预约
     * @param department 科室编码
     * @param organId    机构内码
     * @return List<Doctor>
     * @author luf 2016-6-20
     */
    @RpcService
    public List<Doctor> inDoctorListForRecive(int bussType, final Integer department, final int organId) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String hql = "select d from Doctor d,Employment e where (d.teams=0 OR (d.teams is NULL)) AND d.idNumber>0 AND (d.virtualDoctor=0 OR (d.virtualDoctor IS NULL)) AND " +
                        "d.doctorId=e.doctorId AND e.department=:department AND e.organId=:organId AND d.status=1 order by d.orderNum";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("department", department);
                q.setParameter("organId", organId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> ds = action.getResult();
        List<Doctor> results = new ArrayList<Doctor>();
        if (null == ds || ds.isEmpty()) {
            return results;
        }
        for (Doctor d : ds) {
            d.setOrgan(organId);
            d.setDepartment(department);
            results.add(d);
        }
        return results;
    }

    /**
     * 预约/挂号医生列表日期栏服务
     *
     * @param department
     * @param organId
     * @return List<HashMap<String, Object>>
     */
    @RpcService
    public List<HashMap<String, Object>> effectiveWorkDatesForHealth(final int department, final int organId) {
        final List<String> appointDepartCodes = DAOFactory.getDAO(AppointDepartDAO.class).findByOrganIdAndDepartId(organId, department);
        if (appointDepartCodes.isEmpty()) {
            logger.warn(organId + "机构编号,科室编号:" + department + " 不匹配");
            return new ArrayList<HashMap<String, Object>>();
        }

        HibernateStatelessResultAction<List<Date>> action = new AbstractHibernateStatelessResultAction<List<Date>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                HisServiceConfigDAO hscDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
                OrganConfig organConfig = organConfigDAO.getByOrganId(organId);
                Boolean can = hscDao.isCanAppoint(organId);
                StringBuffer hql = new StringBuffer("SELECT DISTINCT asd.workDate FROM ( SELECT s.workDate,s.stopFlag from bus_appointsource s left join base_doctor d " +
                        "on s.doctorId=d.doctorId where ((d.idNumber is not null and d.idNumber<>:empty) or d.teams=1 or ((d.idNumber is null or d.idNumber=:empty) and d.generalDoctor=1)) " +
                        "AND s.appointDepartCode in :appointDepartCodes " +
                        "and s.organId=:organId and d.status=1 " +
                        "AND s.endTime>=:endTime  AND (s.cloudClinic=0 or (s.cloudClinic is NULL)) "
                );
                // 设置展示全部号源，日期需要排除
                if (!organConfig.getDisplaySourceUsedDoctor()) {
                    hql.append(" AND s.sourceNum-s.usedNum>0 ");
                }
                hql.append(" ORDER BY s.workDate ) asd GROUP BY asd.workdate HAVING BIT_AND(asd.stopflag)=0"); //不是全部停诊
                Query q = ss.createSQLQuery(hql.toString());
                q.setParameter("empty", "");
                if (can) {
                    Date date = new Date();
                    q.setParameter("endTime", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                } else {
                    q.setParameter("endTime", DateConversion.getFormatDate(DateConversion.getDaysAgo(-1), "yyyy-MM-dd"));
                }
                q.setParameterList("appointDepartCodes", appointDepartCodes);
                q.setParameter("organId", organId);
                q.setMaxResults(30);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Date> dates = action.getResult();

        // 当天不全是停诊 就显示出来当天日期
        addTodayDate(dates, organId, appointDepartCodes);

        List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
        if (dates == null || dates.size() <= 0) {
            return results;
        }
        for (Date d : dates) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            String week = DateConversion.getWeekOfDate(d);
            map.put("date", d);
            map.put("week", week.replace("星期", "周"));
            results.add(map);
        }
        return results;
    }

    private void addTodayDate(List<Date> dates, final int organId, final List<String> appointDepartCodes) {
        if (dates != null && dates.size() > 0) {
            Date firstDate = dates.get(0);
            if (firstDate.compareTo(LocalDate.now().toDate()) == 0) {
                //现有日期已经包含当天
            } else {
                //现有日期不包含当天，判断当天的是不是全部停诊
                AbstractHibernateStatelessResultAction<BigInteger> abstractHibernateStatelessResultAction = new AbstractHibernateStatelessResultAction<BigInteger>() {
                    @Override
                    public void execute(StatelessSession ss) throws Exception {
                        String hql = "SELECT BIT_AND(asd.stopflag) FROM ( SELECT s.workDate,s.stopFlag from bus_appointsource s left join base_doctor d " +
                                "on s.doctorId=d.doctorId where ((d.idNumber is not null and d.idNumber<>:empty) or d.teams=1 or ((d.idNumber is null or d.idNumber=:empty) and d.generalDoctor=1)) " +
                                "AND s.appointDepartCode in :appointDepartCodes " +
                                "and s.organId=:organId and d.status=1 " +
                                " AND (s.cloudClinic=0 or (s.cloudClinic is NULL)) " +
                                //" AND s.sourceNum-s.usedNum>0 " +
                                "AND s.workDate=:today  ORDER BY s.workDate ) asd GROUP BY asd.workdate"; //是全部停诊,当天不显示


                        SQLQuery sqlQuery = ss.createSQLQuery(hql);
                        sqlQuery.setParameterList("appointDepartCodes", appointDepartCodes);
                        sqlQuery.setParameter("empty", "");
                        sqlQuery.setParameter("organId", organId);
                        sqlQuery.setParameter("today", DateConversion.getFormatDate(new Date(), "yyyy-MM-dd"));


                        setResult((BigInteger) sqlQuery.uniqueResult());

                    }
                };


                HibernateSessionTemplate.instance().executeReadOnly(abstractHibernateStatelessResultAction);
                BigInteger result = abstractHibernateStatelessResultAction.getResult();

                //dates.add(0,LocalDate.now().toDate());
                // 今天有号的情况下，不全部停诊，就显示今天
                if (result != null && result.intValue() != 1) {
                    dates.add(0, LocalDate.now().toDate());
                }

            }
        }
    }


    /**
     * 预约/挂号某一医生列表日期栏服务
     *
     * @param department
     * @param organId
     * @return List<HashMap<String, Object>>
     */
    @RpcService
    public List<HashMap<String, Object>> effectiveWorkDatesForHealthWithDoctorId(final int department, final int organId, final Integer doctorId) {
        HibernateStatelessResultAction<List<Date>> action = new AbstractHibernateStatelessResultAction<List<Date>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                HisServiceConfigDAO hscDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                Boolean can = hscDao.isCanAppoint(organId);
                //2017-4-21 11:57:09 zhangx 9541 【预约挂号】-【选择科室】通过机构选择科室进入选择医生列表页面，
                // 页面的日期栏要跟入口科室号源显示一致,且只针对指定机构做处理
                StringBuffer hqlbuf = new StringBuffer("SELECT DISTINCT s.workDate from Doctor d,Employment e,AppointSource s,AppointDepart a " +
                        "WHERE d.doctorId=e.doctorId and s.doctorId=d.doctorId and a.organId=s.organId " +
                        "AND a.departId=e.department and a.cancleFlag=0 " +
                        "AND e.department=:department AND e.organId=:organId  and d.status=1 AND s.sourceNum-s.usedNum>0 " +
                        "AND s.endTime>=:endTime AND s.stopFlag=0 AND (s.cloudClinic=0 or (s.cloudClinic is NULL)) " +
                        "and ((d.idNumber is not null and d.idNumber<>:empty) or d.teams=1 or ((d.idNumber is null or d.idNumber=:empty) and d.generalDoctor=1)) ");

//                if (OrganConstant.ORGAN_GXFB_XZ == organId || OrganConstant.ORGAN_GXFB_XY == organId || OrganConstant.ORGAN_GZSRMYY == organId) {
                hqlbuf.append("and s.appointDepartCode in(SELECT appointDepartCode FROM AppointDepart WHERE organId = :organId AND departId = :department) ");
//                }
                if(null != doctorId){
                       hqlbuf.append("and(e.doctorId=:doctorId)");
                }

                hqlbuf.append("GROUP BY s.workDate ORDER BY s.workDate");

                /*String hql = "SELECT DISTINCT s.workDate from ConsultSet c,Doctor d,Employment e,AppointSource s WHERE c.doctorId=d.doctorId and d.doctorId=e.doctorId " +
                        "AND e.department=:department AND e.organId=:organId and d.status=1 AND s.doctorId=d.doctorId AND s.sourceNum-s.usedNum>0 " +
                        "AND s.stopFlag=0" + "and ((d.idNumber is not null and d.idNumber<>:empty and d.doctorId=c.doctorId)or d.teams=1 or ((d.idNumber is null or d.idNumber=:empty) and d.generalDoctor=1)) " +
                        "GROUP BY s.workDate ORDER BY s.workDate";*/
                Query q = ss.createQuery(hqlbuf.toString());
                q.setParameter("empty", "");
                if (can) {
                    Date date = new Date();
                    q.setParameter("endTime", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                } else {
                    q.setParameter("endTime", DateConversion.getFormatDate(DateConversion.getDaysAgo(-1), "yyyy-MM-dd"));
                }
                q.setParameter("department", department);
                q.setParameter("organId", organId);
                if(doctorId!=null){
                    q.setParameter("doctorId",doctorId);
                }
                q.setMaxResults(30);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Date> dates = action.getResult();
        List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
        if (dates == null || dates.size() <= 0) {
            return results;
        }
        for (Date d : dates) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            String week = DateConversion.getWeekOfDate(d);
            map.put("date", d);
            map.put("week", week.replace("星期", "周"));
            results.add(map);
        }
        return results;
    }

    /**
     * 预约/挂号医生列表服务
     *
     * @param department
     * @param organId
     * @param date
     * @return List<Doctor>
     */
    @RpcService
    public List<HashMap<String, Object>> effectiveSourceDoctorsForhealth(final int department, final int organId, final Date date) {
        //TODO 挂号科室后期作缓存
        final List<String> appointDepartCodes = DAOFactory.getDAO(AppointDepartDAO.class).findByOrganIdAndDepartId(organId, department);
        if(appointDepartCodes.isEmpty()){
            logger.warn(organId + "机构编号,科室编号:" + department + " 不匹配");
            return new ArrayList<HashMap<String, Object>>();
        }

        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        final OrganConfig organConfig = organConfigDAO.get(organId);

        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                HisServiceConfigDAO hscDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                Boolean can = hscDao.isCanAppoint(organId);


             /*   StringBuffer hql = new StringBuffer("SELECT d,sum(s.sourceNum-s.usedNum) as haveSource from Doctor d, AppointSource s");
                hql.append(" where d.doctorId = s.doctorId AND ")
                        .append(" ((d.idNumber is not null and d.idNumber<>:empty ) or d.teams=1 or ((d.idNumber is null or d.idNumber=:empty) and d.generalDoctor=1)) ")
                        .append(" and s.organId=:organId and d.status=1 and s.appointDepartCode in :appointDepartCodes ")
                        .append(" and s.endTime>=:endTime and ( s.cloudClinic=0 or s.cloudClinic is null) AND s.stopFlag=0");
                if (date != null) {
                    hql.append(" AND s.sourceNum-s.usedNum>0  AND s.workDate=:workDate  ");
                }
                hql.append(" group by d.doctorId");*/

                //replaced by chenq 2017-4-27 增加医生的一条数据：是否有当日号源

                Date today = LocalDate.now().toDate();


                StringBuffer hql = new StringBuffer("SELECT d.*,s.sourceNum,s.usedNum,s.workDate,s.endTime,s.startTime,s.stopFlag from bus_appointsource s left join base_doctor d ");
                hql.append(" on d.doctorId = s.doctorId where ")
                        .append(" ((d.idNumber is not null and d.idNumber<>:empty ) or d.teams=1 or ((d.idNumber is null or d.idNumber=:empty) and d.generalDoctor=1)) ")
                        .append(" and s.organId=:organId and d.status=1 and s.appointDepartCode in :appointDepartCodes ")
                        .append(" and s.endTime>=:endTime and ( s.cloudClinic=0 or s.cloudClinic is null)");
                if (date != null) {
                    hql.append("  AND s.workDate=:workDate   ");
                }
                 if (date != null && date.compareTo(today)==0) {

                } if (date != null && date.compareTo(today)>0) {
                    if (!organConfig.getDisplaySourceUsedDoctor()) {
                        hql.append(" AND s.sourceNum-s.usedNum>0   ");
                    }
                }


                hql.append(" order by ");
                //机构自定义科室医生排序功能开启 DoctorOrderType=1
                if (organConfig != null && organConfig.getDoctorOrderType() != null && organConfig.getDoctorOrderType() == 1) {
                    hql.append(" d.orderNumEmp DESC,");
                }
                hql.append(" d.haveAppoint DESC,d.virtualDoctor DESC,IFNULL(d.haveAppoint,0) DESC,d.goodRating DESC");
                //Query q = ss.createQuery(hql.toString());
                Query q = ss.createSQLQuery(hql.toString()).addEntity("d", Doctor.class).addScalar("sourceNum").addScalar("usedNum").addScalar("workDate").addScalar("endTime").addScalar("startTime").addScalar("stopFlag");
                q.setParameterList("appointDepartCodes", appointDepartCodes);
                if (date != null) {
                    q.setParameter("workDate", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                    if (can) {
                        Date date = new Date();
                        q.setParameter("endTime", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                    } else {
                        q.setParameter("endTime", DateConversion.getFormatDate(DateConversion.getDaysAgo(-1), "yyyy-MM-dd"));
                    }
                }
                q.setParameter("organId", organId);
                q.setParameter("empty", "");
                if (can) {
                    Date date = new Date();
                    q.setParameter("endTime", DateConversion.getFormatDate(date, "yyyy-MM-dd"));
                } else {
                    q.setParameter("endTime", DateConversion.getFormatDate(DateConversion.getDaysAgo(-1), "yyyy-MM-dd"));
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        //List<Object[]> doctors = action.getResult();

        //List<Doctor> appointSources = action.getResult();
        List<Object[]> appointSources = action.getResult();

        Map<Doctor, Integer[]> doctorMap = aggregateAppointSource(appointSources);

        //chenq 如果没开启机机构医生排班功能：按照医生号源数目排序
        if (organConfig == null || organConfig.getDoctorOrderType() == null || organConfig.getDoctorOrderType() == 0) {
            doctorMap = sortByAppointSourceNum(doctorMap);
        }

        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();

        for (Iterator it = doctorMap.keySet().iterator(); it.hasNext(); ) {
            Doctor doctor = (Doctor) it.next();
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctor.getDoctorId());

            Integer doctorTotalAppointSource = doctorMap.get(doctor)[0];
            Integer doctorTodayTotalAppointSource = doctorMap.get(doctor)[1];
            Integer stopFlag = doctorMap.get(doctor)[2];

            Integer appointSourceFlag = null;
            if (doctorTotalAppointSource >= 1) {
                doctor.setHaveAppoint(1);
                appointSourceFlag = AppointSourceFlagEnum.CanAppoint.getValue();

            } else {
                doctor.setHaveAppoint(0);
                appointSourceFlag = AppointSourceFlagEnum.AppointFull.getValue();
            }
            if (doctorTodayTotalAppointSource >= 1) {
                doctor.setHaveTodayAppointSource(true);
            } else {
                doctor.setHaveTodayAppointSource(false);
            }
            doctor.setStopFlag(BooleanUtils.toBoolean(stopFlag));

            if (BooleanUtils.toBoolean(stopFlag)) {
                // doctor.setAppointSourceFlag(AppointSourceFlagEnum.DoctorStopFlag.getValue());
                appointSourceFlag = AppointSourceFlagEnum.DoctorStopFlag.getValue();
            }

            doctor.setAppointSourceFlag(appointSourceFlag);
            /*try {
                doctor.setAppointSourceFlagText(DictionaryController.instance()
                        .get( "eh.base.dictionary.AppointSourceFlag")
                        .getText(appointSourceFlag));
            } catch (ControllerException e) {
                logger.error("appointSourceFlag can not mapping text:"+appointSourceFlag);
            }*/


            if (ValidateUtil.isNotTrue(doctor.getTeams()) && ValidateUtil.blankString(doctor.getGender())) {
                doctor.setGender("1");
            }

            HashMap<String, Object> result = new HashMap<String, Object>();
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }


        /*if (doctors != null && !doctors.isEmpty()) {
            for (Object[] doctor_source : doctors) {
                HashMap<String, Object> result = new HashMap<String, Object>();

                Doctor doctor = (Doctor) doctor_source[0];
                int sourceNum = Integer.parseInt(doctor_source[1].toString());
                if(sourceNum>=1){
                    doctor.setHaveAppoint(1);
                }else{
                    doctor.setHaveAppoint(0);
                }

                //如果输入日期为当天,有号源说明，有当日号源
                if (date!=null && LocalDate.now().toDate().equals(date)) {
                    if(sourceNum>=1){
                        doctor.setHaveTodayAppointSource(true);
                    }else{
                        doctor.setHaveTodayAppointSource(false);
                    }
                }else if (date ==null){

                }

                if (ValidateUtil.isNotTrue(doctor.getTeams()) && ValidateUtil.blankString(doctor.getGender())) {
                    doctor.setGender("1");
                }
                ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
                ConsultSet consultSet = dao.get(doctor.getDoctorId());
                result.put("doctor", doctor);
                result.put("consultSet", consultSet);
                targets.add(result);
            }
        }*/
        return targets;
    }

    /**
     * 按照医生剩余号源排序
     * @param doctorMap
     * @return
     */
    private Map<Doctor, Integer[]> sortByAppointSourceNum(Map<Doctor, Integer[]> doctorMap) {

        HashMultimap<Integer,Map.Entry> doctorSourceMap = HashMultimap.create();
        ArrayList<Integer> nums = Lists.newArrayList();
        for (Map.Entry<Doctor, Integer[]> doctorEntry : doctorMap.entrySet()) {
            Integer[] doctorSources = doctorEntry.getValue();
            Integer doctorSourceNum = doctorSources[0];

            doctorSourceMap.put(doctorSourceNum,doctorEntry);
            nums.add(doctorSourceNum);
        }

        //按照医生的号源数目排序 由高到低
        Collections.sort(nums, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o2-o1;
            }
        });

        LinkedHashMap<Doctor, Integer[]> doctorsSotedByLeftAppointSource = Maps.newLinkedHashMap();

        for (Integer num : nums) {
            Set<Map.Entry> entries = doctorSourceMap.get(num);
            for (Map.Entry entry : entries) {
                Doctor key = (Doctor) entry.getKey();
                Integer[] values = (Integer[]) entry.getValue();
                doctorsSotedByLeftAppointSource.put(key, values);
            }
        }


        return doctorsSotedByLeftAppointSource;
    }

    //手动group by doctor，目的是为了doctor增加 是否有当日预约号源标识HaveTodayAppointSource
    private Map<Doctor, Integer[]> aggregateAppointSource(List<Object[]> doctors) {
        //保证医生顺序不变
        Map<Doctor, Integer[]> table = Maps.newLinkedHashMap();
        Date today = LocalDate.now().toDate();
        Date now = LocalDateTime.now().toDate();

        for (Object[] rowData : doctors) {
            Object doctorColumn = rowData[0];

            if (doctorColumn != null) {
                Doctor doctor = (Doctor) doctorColumn;
                //医生id -- 医生号源总数 --当日号源数目
                Integer doctorId = doctor.getDoctorId();

                Integer sourceNum = null;
                Integer usedNum = null;
                Date workDate = null;
                Date appointSourceEndTime=null;//号源的结束日期
                Date appointSourceStartTime=null;//号源的开始日期
                Integer stopFlag = null;


                sourceNum = dbIntegerValueConvert(rowData[1]);
                usedNum = dbIntegerValueConvert(rowData[2]);


                if (rowData[3] != null) {
                    workDate = new java.util.Date(((java.sql.Date) rowData[3]).getTime());
                }
                //号源结束时间段
                if (rowData[4] != null) {
                    appointSourceEndTime = new Date(((Timestamp) rowData[4]).getTime());
                }
                // /号源开始时间段
                if (rowData[5] != null) {
                    appointSourceStartTime = new Date(((Timestamp) rowData[5]).getTime());
                }
                if (rowData[6] != null) {
                    stopFlag = rowData[6]!=null?(Integer) rowData[6]:0;
                    //stopFlag = BooleanUtils.toBoolean(((Integer) rowData[6]));
                }

                //当号源endTime大于当前时间 并没有被停诊才算有效号源
                int leftAppointSource =0;
                if (appointSourceEndTime!=null && appointSourceEndTime.after(now) &&  !BooleanUtils.toBoolean(stopFlag)) {
                    leftAppointSource = (sourceNum - usedNum)>0?(sourceNum - usedNum):0;
                }
                int todayAppointCount = 0;
                //计算当前号源：  工作日期等于当天 And 号源的工作结束日期还没过期
                if (workDate != null && today.equals(workDate)) {
                    //当日号源还可以挂号
                    if (appointSourceEndTime!=null && appointSourceEndTime.after(now) &&  !BooleanUtils.toBoolean(stopFlag)){
                        todayAppointCount = leftAppointSource;
                    }

                }

                if (table.get(doctor) == null) {
                    Integer[] number = new Integer[3];
                    number[0] = leftAppointSource;
                    number[1] = todayAppointCount;
                    number[2] = stopFlag;
                    table.put(doctor, number);
                } else {
                    table.get(doctor)[0] += leftAppointSource;
                    table.get(doctor)[1] += todayAppointCount;
                    //医生号源信息，停诊标识全部是1(才是医生的停诊)
                    table.get(doctor)[2] = BooleanUtils.toInteger(BooleanUtils.toBoolean(table.get(doctor)[2]) && BooleanUtils.toBoolean(stopFlag));
                }
            }
        }
        return table;
    }

    private Integer dbIntegerValueConvert(Object rowDatum) {
        if (rowDatum != null) {
            return ((Integer) rowDatum);
        } else {
            return 0;
        }
    }


    /**
     * 根据专科id获取专家解读的列表,并且按照职称由高到低依次排序
     * ,再根据咨询量由多到少排序,最后根据评分由高到低排序.只包含个人医生
     *
     * @param profession 大专科
     * @param start      起始页
     * @param limit      每页限制条数
     * @return List<Doctor>
     * @author cuill
     */
    public List<Doctor> queryDoctorListForExpertConsult(final String profession, final int start,
                                                        final int limit) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {

                StringBuffer sql = new StringBuffer("SELECT d.* FROM base_doctor d LEFT JOIN bus_consultset c ON d.DoctorId = c.DoctorID " +
                        "LEFT JOIN( SELECT ConsultDoctor doctorId, COUNT(*) number FROM bus_consult GROUP BY ConsultDoctor) e ON d.DoctorId = e.doctorId" +
                        " WHERE d.IDNumber IS NOT NULL AND d.IDNumber<>:empty AND d.Teams = 0 AND c.ProfessorConsultStatus = 1 " +
                        "AND SUBSTRING(d.profession, 1, 2) =:profession ");

                //讲个性化中限制的医院加上去
                StringBuffer resultSql = addPersonalityOrganCondition(sql);
                resultSql.append(" ORDER BY d.proTitle ASC, d.rating DESC, e.number DESC");

                Query query = statelessSession.createSQLQuery(resultSql.toString()).addEntity("d", Doctor.class);
                query.setParameter("empty", "");
                if (!StringUtils.isEmpty(profession)) {
                    query.setParameter("profession", profession);
                } else {
                    //2017年2月14日 @cuill 第一次进来以后可能没有传递科室号,由于全科医学为默认第一位.所以此处传全科医学
                    query.setParameter("profession", ProfessionConstant.PRODESSOR_GENERALPRACTICE);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取专科号为"03A"(也就是神经科)专科的专家解读的列表,并且按照职称由高到低依次排序
     * ,再根据咨询量由多到少排序,最后根据评分由高到低排序.只包含个人医生
     *
     * @param profession 大专科
     * @param start      起始页
     * @param limit      每页限制条数
     * @return List<Doctor>
     * @author cuill
     */
    public List<Doctor> queryDoctorListForExpertConsultAndNeurology(final String profession, final int start,
                                                                    final int limit) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {


                StringBuffer sql = new StringBuffer("SELECT d.* FROM base_doctor d LEFT JOIN bus_consultset c ON d.DoctorId = c.DoctorID " +
                        "LEFT JOIN( SELECT ConsultDoctor doctorId, COUNT(*) number FROM bus_consult GROUP BY ConsultDoctor) e ON d.DoctorId = e.doctorId" +
                        " WHERE d.IDNumber IS NOT NULL AND d.IDNumber<>:empty AND d.Teams = 0 AND c.ProfessorConsultStatus = 1 " +
                        "AND d.profession = '03A' ");

                //讲个性化中限制的医院加上去
                StringBuffer resultSql = addPersonalityOrganCondition(sql);
                resultSql.append(" ORDER BY d.proTitle ASC, d.rating DESC, e.number DESC");
                Query query = statelessSession.createSQLQuery(resultSql.toString()).addEntity("d", Doctor.class);
                query.setParameter("empty", "");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据bae_doctor和bus_consultSet这两张表来判断大科室里面开通专家解读的医生数量.
     * 返回的结果为按照科室医生的数量从多到少排序,如果全科科室有医生的话,全科科室排在第一位.
     *
     * @return List<String>
     * @Author cuill 2017年2月15日
     */
    public List<Object[]> findProfessionList() {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("SELECT SUBSTRING(d.profession, 1, 2) AS profession, count(*) as number " +
                        " FROM Doctor d, ConsultSet c WHERE d.teams = 0 AND d.idNumber IS NOT NULL " +
                        "AND d.idNumber <>:empty AND d.doctorId = c.doctorId AND d.profession != '03A' AND c.professorConsultStatus = 1 ");

                StringBuffer resultHql = addPersonalityOrganCondition(hql);
                resultHql.append(" GROUP BY SUBSTRING(d.profession, 1, 2)ORDER BY number DESC ");
                Query query = statelessSession.createQuery(resultHql.toString());
                query.setParameter("empty", "");
                List<Object[]> professionList = query.list();

                //当科室号为神经科的医生数量
                StringBuffer neuroHql = new StringBuffer("SELECT d.profession, count(*) as number " +
                        " FROM Doctor d, ConsultSet c WHERE d.teams = 0 AND d.idNumber IS NOT NULL " +
                        "AND d.idNumber <>:empty AND d.doctorId = c.doctorId AND  d.profession = '03A' AND c.professorConsultStatus = 1 ");
                StringBuffer resultNeuroHql = addPersonalityOrganCondition(neuroHql);
                resultNeuroHql.append(" GROUP BY SUBSTRING(d.profession, 1, 2)ORDER BY number DESC");
                Query neuroQuery = statelessSession.createQuery(resultNeuroHql.toString());
                neuroQuery.setParameter("empty", "");

                List<Object[]> specialProfessionList = neuroQuery.list();

                List<Object[]> result = new ArrayList<Object[]>();
                result.addAll(professionList);
                result.addAll(specialProfessionList);
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 只显示当前机构号,避免个性化显示了错误的信息,对sql语句进行拼接.
     *
     * @param sql 传进来的sql语句
     * @return 包装后的sql语句
     * @author cuill
     * @Date 2017年3月3日
     */
    private StringBuffer addPersonalityOrganCondition(StringBuffer sql) {

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Integer> organs = organDAO.findOrgansByUnitForHealth();
        if (organs != null && organs.size() > 0) {
            sql.append(" and (");
            for (Integer i : organs) {
                sql.append("d.organ = ").append(i).append(" or ");
            }
            sql = new StringBuffer(sql.substring(0, sql.length() - 3)).append(")");
        }
        return sql;
    }

    /**
     * 获取根据机构id获取机构对应的功能白名单医生列表
     * @param organId 机构id，若不填，则获取所有机构的
     * @param whiteListType whiteListType.dic
     * @param start
     * @param limit
     * @return
     */
    public List<Doctor> findWhiteDoctorListByOrganId(final List<Integer> organIds, final Integer whiteListType, final Integer start, final Integer limit){
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("select d from Doctor d ,DoctorWhiteList w" +
                        " where d.doctorId=w.doctorId and w.type=:whiteListType and d.organ in (:organIds)");
                hql.append(" ORDER BY w.id");

                Query query = statelessSession.createQuery(hql.toString());
                query.setParameterList("organIds", organIds);
                query.setParameter("whiteListType", whiteListType);
                query.setMaxResults(limit);
                query.setFirstResult(start);

                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }

    /**
     * 按条件查询有云诊室排班的医生列表-sql
     *
     * @param doctorName
     * @param searchOrganId
     * @param start
     * @param limit
     * @param doctorId
     * @param organs
     * @return
     */
    public List<Doctor> searchDoctorWithClinic(final String doctorName, final Integer searchOrganId, final int start, final int limit, final int doctorId, final List<Integer> organs) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "SELECT DISTINCT d FROM Doctor d WHERE d.doctorId<>:doctorId ");
                if (org.apache.commons.lang3.StringUtils.isNotBlank(doctorName)) {
                    hql.append("AND d.name like :doctorName ");
                }
                if (searchOrganId == 0) {
                    hql.append("AND d.organ in(:organs) ");
                } else {
                    hql.append("AND d.organ=:searchOrganId ");
                }

                hql.append("AND ((SELECT COUNT(*) FROM AppointSource s WHERE s.doctorId=d.doctorId AND s.cloudClinic=1 AND s.cloudClinicType=1 AND s.workDate>NOW() AND (s.sourceNum-s.usedNum)>0 AND s.stopFlag=0)>0 OR " +
                        "((SELECT COUNT(*) FROM AppointSource ap WHERE ap.doctorId=d.doctorId AND ap.cloudClinic=1 AND ap.cloudClinicType=1 AND " +
                        "ap.workDate=DATE(NOW()) AND ap.workType=1 AND (ap.sourceNum-ap.usedNum)>0 AND ap.startTime>:startTime AND ap.stopFlag=0)>0 AND " +
                        "(SELECT COUNT(*) FROM AppointSource app WHERE app.doctorId=d.doctorId AND app.cloudClinic=1 AND app.cloudClinicType=1 AND " +
                        "app.workDate=DATE(NOW()) AND app.workType=1 AND app.usedNum>0 AND app.stopFlag=0)>0) OR " +
                        "((SELECT COUNT(*) FROM AppointSource aps WHERE aps.doctorId=d.doctorId AND aps.cloudClinic=1 AND aps.cloudClinicType=1 AND " +
                        "aps.workDate=DATE(NOW()) AND aps.workType=2 AND (aps.sourceNum-aps.usedNum)>0 AND aps.startTime>:startTime AND aps.stopFlag=0)>0 AND " +
                        "(SELECT COUNT(*) FROM AppointSource apps WHERE apps.doctorId=d.doctorId AND apps.cloudClinic=1 AND apps.cloudClinicType=1 AND apps.workDate=DATE(NOW()) AND apps.workType=2 AND apps.usedNum>0 AND apps.stopFlag=0)>0)) " +
                        "ORDER BY LENGTH(TRIM(d.proTitle)) DESC,d.proTitle,d.rating DESC");
                Query q = ss.createQuery(hql.toString());
                if (searchOrganId == 0) {
                    q.setParameterList("organs", organs);
                } else {
                    q.setParameter("searchOrganId", searchOrganId);
                }
                q.setParameter("doctorId", doctorId);
                //DATE_ADD(NOW(),INTERVAL 1 DAY_HOUR)
                q.setParameter("startTime", DateConversion.getDateAftHour(new Date(), 1));
                if (org.apache.commons.lang3.StringUtils.isNotBlank(doctorName)) {
                    q.setParameter("doctorName", "%" + doctorName + "%");
                    SearchContentDAO searchContentDAO = DAOFactory.getDAO(SearchContentDAO.class);
                    SearchContent searchContent = new SearchContent();
                    searchContent.setDoctorId(doctorId);
                    searchContent.setContent(doctorName);
                    searchContent.setBussType(SearchConstant.SEARCHTYPE_YCYS); //远程医生名称
                    searchContentDAO.addSearchContent(searchContent, 1);
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
     * 按机构科室或姓名查找会诊中心-sql
     *
     * @param doctorName
     * @param searchOrganId
     * @param departmentId
     * @param start
     * @param limit
     * @param doctorId
     * @param organs
     * @return
     */
    public List<Doctor> searchDoctorsForMeetCenter(final String doctorName, final Integer searchOrganId, final Integer departmentId, final int start, final int limit, final int doctorId, final List<Integer> organs) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "SELECT DISTINCT d FROM Doctor d,DoctorTab dt,Employment e,ConsultSet c " +
                                "WHERE dt.paramType=:paramType and dt.paramValue=:paramValue and d.doctorId=dt.doctorId and d.doctorId<>:doctorId and d.status=1 " +
                                "and e.doctorId=d.doctorId ");
                if (org.apache.commons.lang3.StringUtils.isNotBlank(doctorName)) {
                    hql.append("AND d.name like :doctorName ");
                }
                if (searchOrganId == 0) {
                    hql.append("AND e.organId in(:organs) ");
                } else {
                    hql.append("AND e.organId=:searchOrganId ");
                    if (departmentId != 0) {
                        hql.append("AND e.department=:department ");
                    }
                }
                hql.append("and d.doctorId=c.doctorId and c.meetClinicStatus=1");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("paramType", DoctorTabConstant.ParamType_MEETCENTER);
                q.setParameter("paramValue", DoctorTabConstant.ParamValue_TRUE);
                if (searchOrganId == 0) {
                    q.setParameterList("organs", organs);
                } else {
                    q.setParameter("searchOrganId", searchOrganId);
                    if (departmentId != 0) {
                        q.setParameter("department", departmentId);
                    }
                }
                if (org.apache.commons.lang3.StringUtils.isNotBlank(doctorName)) {
                    q.setParameter("doctorName", "%" + doctorName + "%");
                    SearchContentDAO searchContentDAO = DAOFactory.getDAO(SearchContentDAO.class);
                    SearchContent searchContent = new SearchContent();
                    searchContent.setDoctorId(doctorId);
                    searchContent.setContent(doctorName);
                    searchContent.setBussType(SearchConstant.SEARCHTYPE_HZZXYS); //会诊中心医生名称
                    searchContentDAO.addSearchContent(searchContent, 1);
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
     * 按条件查询有云诊室排班的医生列表-sql
     *
     * @param doctorName
     * @param searchOrganId
     * @param departmentId
     * @param workDate
     * @param start
     * @param limit
     * @param organs
     * @param doctorId
     * @param now
     * @param todayOrgans
     * @return
     */
    public List<Doctor> searchDoctorsForClinic(final String doctorName, final Integer searchOrganId, final Integer departmentId, final Date workDate, final int start, final int limit, final List<Integer> organs, final int doctorId, final Date now, final List<Integer> todayOrgans) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer(
                        "SELECT DISTINCT d FROM Doctor d,Employment e WHERE d.doctorId<>:doctorId and d.status=1 and e.doctorId=d.doctorId ");
                if (org.apache.commons.lang3.StringUtils.isNotBlank(doctorName)) {
                    hql.append("AND d.name like :doctorName ");
                }
                if (searchOrganId == 0) {
                    hql.append("AND e.organId in(:organs) ");
                } else {
                    hql.append("AND e.organId=:searchOrganId ");
                    if (departmentId != 0) {
                        hql.append("AND e.department=:department ");
                    }
                }
                hql.append("AND exists(FROM AppointSource s WHERE s.doctorId=d.doctorId AND s.cloudClinic=1 AND s.cloudClinicType=1 AND s.stopFlag=0 ");
                if (searchOrganId == 0) {
                    hql.append("AND s.organId in(:organs) ");
                } else {
                    hql.append("AND s.organId=:searchOrganId ");
                }
                if (workDate != null && workDate.after(now)) {//查询非当天号源
                    hql.append("AND s.workDate=DATE(:workDate) AND (s.sourceNum-s.usedNum)>0) ");
                } else {
                    if (todayOrgans != null && !todayOrgans.isEmpty()) {//有当天号源可约机构
                        if (workDate == null) {//查询全部日期（当天号源+非当天号源）
                            hql.append("AND ((s.workDate>NOW() AND (s.sourceNum-s.usedNum)>0) OR (s.workDate=DATE(NOW()) AND s.organId in(:todayOrgans) and sourceNum>=usedNum ))) ");
                        } else if (DateConversion.isSameDay(workDate, now)) {//查询当天号源
                            hql.append("AND s.workDate=DATE(:workDate) AND s.organId in(:todayOrgans) and sourceNum>=usedNum ) ");
                        }
                    } else {//无当天可约机构直接查询非当天号源
                        hql.append("AND s.workDate>NOW() AND (s.sourceNum-s.usedNum)>0) ");
                    }
                }
                hql.append(" ORDER BY LENGTH(TRIM(d.proTitle)) DESC,d.proTitle,d.rating DESC");
                Query q = ss.createQuery(hql.toString());
                if (searchOrganId == 0) {
                    q.setParameterList("organs", organs);
                } else {
                    q.setParameter("searchOrganId", searchOrganId);
                    if (departmentId != 0) {
                        q.setParameter("department", departmentId);
                    }
                }
                if (workDate != null) {
                    q.setParameter("workDate", workDate);
                }
                if (todayOrgans != null && !todayOrgans.isEmpty() && (workDate == null || DateConversion.isSameDay(workDate, now))) {
                    q.setParameterList("todayOrgans", todayOrgans);
                }
                if (org.apache.commons.lang3.StringUtils.isNotBlank(doctorName)) {
                    q.setParameter("doctorName", "%" + doctorName + "%");
                    SearchContentDAO searchContentDAO = DAOFactory.getDAO(SearchContentDAO.class);
                    SearchContent searchContent = new SearchContent();
                    searchContent.setDoctorId(doctorId);
                    searchContent.setContent(doctorName);
                    searchContent.setBussType(SearchConstant.SEARCHTYPE_YCYS); //远程医生名称
                    searchContentDAO.addSearchContent(searchContent, 1);
                }
                q.setParameter("doctorId", doctorId);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
}
