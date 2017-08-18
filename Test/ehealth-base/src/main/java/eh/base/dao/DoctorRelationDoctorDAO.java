package eh.base.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.base.DoctorRelationDoctor;
import eh.entity.base.Employment;
import eh.entity.bus.ConsultSet;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 医生关注医生
 */
public abstract class DoctorRelationDoctorDAO extends
        HibernateSupportDelegateDAO<DoctorRelationDoctor> {

    public static final Logger log = Logger
            .getLogger(DoctorRelationDoctor.class);

    public DoctorRelationDoctorDAO() {
        super();
        setEntityName(DoctorRelationDoctor.class.getName());
        setKeyField("doctorRelationId");
    }

    /**
     * 关注医生A的医生总数
     *
     * @param doctorId 医生A的doctorId
     * @return
     * @author zhangx
     * @date 2015-12-10 上午11:04:05
     */
    @RpcService
    @DAOMethod(sql = "select count(*) from DoctorRelationDoctor where relationDoctorId=:doctorId ")
    public abstract long getDoctorRelationDoctorNum(
            @DAOParam("doctorId") int doctorId);

    /**
     * 医生A关注的医生总数
     *
     * @param doctorId 医生A的doctorId
     * @return
     * @author zhangsl
     * @date 2016-12-12 14:27:26
     * 剔除无效的被关注医生医生
     */
    @RpcService
    @DAOMethod(sql = "select count(*) from DoctorRelationDoctor r,Doctor d where r.doctorId=:doctorId and r.relationDoctorId=d.doctorId and d.status=1")
    public abstract long getDoctorRelationNum(
            @DAOParam("doctorId") int doctorId);

    /**
     * 关注医生添加服务 (先判断该医生是否关注过，未关注过，则添加关注)
     *
     * @param doctorId
     * @param relationDoctorId
     * @author ZX
     * @date 2015-6-12 下午6:51:57
     */
    @RpcService
    public void addRelationDoctor(int doctorId, int relationDoctorId) {
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);

        // 被关注医生
        Doctor relationDoctor = doctorDao.getByDoctorId(relationDoctorId);

        // 判断被关注医生是否团队医生
        int groupDoctorFlag = 0;
        if (relationDoctor.getTeams() != null
                && relationDoctor.getTeams() == true) {
            groupDoctorFlag = 1;
        }
        if (groupDoctorFlag == 1) {
            DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<DoctorGroup> groups = groupDAO.findByDoctorId(
                    relationDoctorId);
            Boolean isIn = false;
            for (DoctorGroup dg : groups) {
                Integer memberId = dg.getMemberId();
                if (doctorId == memberId) {
                    isIn = true;
                    break;
                }
            }
            if (isIn) {
                throw new DAOException(ErrorCode.SERVICE_ERROR,"不能关注自己所在的团队");
            }
        }

        DoctorRelationDoctor relation = new DoctorRelationDoctor();
        relation.setDoctorId(doctorId);
        relation.setRelationDoctorId(relationDoctorId);
        relation.setGroupDoctorFlag(groupDoctorFlag);
        relation.setRelationDate(new Date());

        // 判断是否已经关注过
        if (!this.getRelationDoctorFlag(doctorId, relationDoctorId)) {
            this.save(relation);
            log.info("医生【" + doctorId + "】关注医生【" + relationDoctorId + "】");
        }
    }

    /**
     * 判断医生是否关注服务 (根据病人主键，医生主键查询是否存在记录，是则返回true，否返回false)
     *
     * @param mpiId
     * @param doctorId
     * @return True关注 False没关注
     * @date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public boolean getRelationDoctorFlag(Integer doctorId,
                                         Integer relationDoctorId) {
        if (doctorId == null || doctorId == 0) {
            return false;
        }

        if (relationDoctorId == null || relationDoctorId == 0) {
            return false;
        }

        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);

        // 判断这个医生是否存在
        if (!doctorDao.exist(doctorId)) {
            throw new DAOException(600, "不存在医生[" + doctorId + "]");
        }
        if (!doctorDao.exist(relationDoctorId)) {
            throw new DAOException(600, "不存在医生[" + relationDoctorId + "]");
        }

        // 根据被关注医生主键，医生主键查询是否存在记录，是则返回true，否返回false
        DoctorRelationDoctor relation = this.getByDoctorIdAndRelationDoctorId(
                doctorId, relationDoctorId);
        if (relation != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 根据被关注医生主键，医生主键查询
     *
     * @param mpiId    病人主键
     * @param doctorId 医生主键
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract DoctorRelationDoctor getByDoctorIdAndRelationDoctorId(
            Integer doctorId, Integer relationDoctorId);

    /**
     * 取消关注
     *
     * @param mpiId
     * @param doctorId
     * @author ZX
     * @date 2015-6-12 下午6:44:03
     */
    @RpcService
    public void delByDoctorIdAndRelationDoctorId(Integer doctorId,
                                                 Integer relationDoctorId) {
        // 判断是否已经关注过
        if (this.getRelationDoctorFlag(doctorId, relationDoctorId)) {
            DoctorRelationDoctor relation = this
                    .getByDoctorIdAndRelationDoctorId(doctorId,
                            relationDoctorId);
            remove(relation.getDoctorRelationId());
            log.info("医生【" + doctorId + "】取消关注医生【" + relationDoctorId + "】");
        }
    }

    /**
     * 查询医生关注医生列表(分页)
     */
    @RpcService
    @DAOMethod(sql = "select distinct(relationDoctorId)  from DoctorRelationDoctor where doctorId=:doctorId")
    public abstract List<Integer> findRelationDoctorId(
            @DAOParam("doctorId") int doctorId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 查询医生关注医生列表(分页)
     */
    // @RpcService
    // public List<Doctor> findRelationDoctorListStartAndLimit(int doctorId,
    // int start, int limit) {
    // DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
    // List<Integer> ids = this.findRelationDoctorId(doctorId, start, limit);
    //
    // if (ids.size() > 0) {
    // return doctorDao.findEffectiveDocByDoctorIdIn(ids);
    // } else {
    // return new ArrayList<Doctor>();
    // }
    // }

    /**
     * 查询医生关注医生列表(分页)
     */
    @RpcService
    public List<Doctor> findRelationDoctorListStartAndLimit(final int doctorId,
                                                            final int start, final int limit) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "select d from Doctor d,DoctorRelationDoctor r where "
                        + "r.doctorId=:doctorId and d.doctorId=r.relationDoctorId and d.status=1";
                Query q = ss.createQuery(hql);
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
     * 查询医生关注医生的数量（总数）
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "select count(*) from Doctor d,DoctorRelationDoctor r where r.doctorId=:doctorId and d.doctorId=r.relationDoctorId and d.status=1")
    public abstract Long getFollowingDoctorNum(@DAOParam("doctorId") Integer doctorId);

    /**
     * 查询医生关注医生列表(分页,业务筛选) [华姐]
     *
     * @param doctorId 申请医生
     * @param busId    业务类型 1转诊 2会诊 3咨询 4预约
     * @param start    起始位置
     * @param limit    限制条数
     * @return List<Doctor>
     * <p>
     * 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     * 修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     * 增加按orderNum排序</br>
     * @author LF
     */
    @RpcService
    public List<Doctor> findRelationDoctorListStartAndLimitBus(
            final int doctorId, final int busId, final int start,
            final int limit) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        UnitOpauthorizeDAO dao = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        List<Integer> oList = dao.findByBussId(busId);
        if (oList == null) {
            oList = new ArrayList<Integer>();

        }
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        oList.add(eSelf.getOrganId());
        StringBuilder sb = new StringBuilder(" and(");
        for (Integer o : oList) {
            sb.append(" e.organId=").append(o).append(" OR");
        }
        final String strUO = sb.substring(0, sb.length() - 2) + ")";

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> result = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select distinct d from DoctorRelationDoctor op, Doctor d, Organ o, Employment e, ConsultSet c where op.relationDoctorId = d.doctorId and op.doctorId =:doctorId and o.organId = e.organId and d.doctorId = e.doctorId and d.doctorId = c.doctorId and d.status = 1 ");

                hql.append(strUO);
                switch (busId) {
                    case 1:
                        hql.append(" and c.transferStatus=1");
                        break;
                    case 2:
                        hql.append(" and c.meetClinicStatus=1");
                        break;
                    case 3:
                        hql.append(" and (c.onLineStatus=1 or c.appointStatus=1)");
                        break;
                    case 4:
                        hql.append(" and d.teams<>1");
                        break;
                    default:
                        hql.append(" ");
                        break;
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);

                q.setMaxResults(limit);
                q.setFirstResult(start);

                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();

        if (docList==null || docList.size()==0) {
            return new ArrayList<Doctor>();
        }

        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        DoctorTabDAO doctorTabDAO=DAOFactory.getDAO(DoctorTabDAO.class);
        for (Doctor doctor : docList) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            if(busId==BussTypeConstant.MEETCLINIC){//zhangsl 2017-05-26 15:25:51会诊中心标记新增
                doctor.setMeetCenter(doctorTabDAO.getMeetTypeByDoctorId(doctor.getDoctorId()));
            }
            targets.add(doctor);
        }
        return targets;
    }

    /**
     * 查询医生关注医生列表(分页)
     */
    @RpcService
    public List<Doctor> findRelationDoctorListStart(int doctorId, int start) {
        return findRelationDoctorListStartAndLimit(doctorId, start, 10);
    }

    /**
     * 查询医生关注医生列表(分页) [华哥]
     *
     * @param doctorId
     * @param busId
     * @param start
     * @return
     * @author LF
     */
    @RpcService
    public List<Doctor> findRelationDoctorListStartBus(int doctorId, int busId,
                                                       int start) {
        return findRelationDoctorListStartAndLimitBus(doctorId, busId, start,
                10);
    }

    /**
     * 查询被关注医生列表(分页)
     *
     * @param relationDoctorId 被关注医生
     * @param start            分也起始位置
     * @param limit            每页限制条数
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public List<Doctor> findDoctorListByRelationStartAndLimit(
            final int relationDoctorId, final int start, final int limit) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "select d from Doctor d,DoctorRelationDoctor r where "
                        + "r.relationDoctorId=:doctorId and d.doctorId=r.doctorId and d.status=1";
                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", relationDoctorId);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询被关注医生列表(分页)
     *
     * @param relationDoctorId 被关注医生
     * @param start            分也起始位置
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public List<Doctor> findDoctorListByRelationStart(int relationDoctorId,
                                                      int start) {
        return findDoctorListByRelationStartAndLimit(relationDoctorId, start,
                10);
    }

    /**
     * 查询医生关注医生列表(原findRelationDoctorListStartAndLimitBus)-原生
     *
     * @param doctorId 申请医生
     * @param start    起始位置
     * @return List<Doctor>
     * @author LF
     */
    @RpcService
    public List<Doctor> findRelationDoctorListWithEmp(final int doctorId,
                                                      final int start) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> result = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select distinct d from DoctorRelationDoctor r,Doctor d where "
                                + "r.relationDoctorId = d.doctorId and r.doctorId =:doctorId and d.status = 1");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setMaxResults(10);
                q.setFirstResult(start);

                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if (docList==null || docList.size()==0) {
            return new ArrayList<Doctor>();
        }
        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor doctor : docList) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            targets.add(doctor);
        }
        return targets;
    }

    /**
     * 查询医生关注医生列表(原findRelationDoctorListWithEmp，findRelationDoctorListStartAndLimitBus)-原生
     *
     * @author luf
     * @date 2016-5-6
     * @param doctorId 申请医生
     * @param start    起始位置
     * @param busType 业务类型-1转诊2会诊3咨询、预约
     * @return List<Doctor>
     */
    /**
     * @param doctorId
     * @param start
     * @return
     */
    @RpcService
    public List<Doctor> findRelationDoctorListWithIsOpen(final int doctorId,
                                                         final int start, final int busType) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> result = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select distinct d from DoctorRelationDoctor r,Doctor d where "
                                + "r.relationDoctorId = d.doctorId and r.doctorId =:doctorId and d.status = 1");
                if (busType == 4) {
                    hql.append(" and d.teams<>1");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setMaxResults(10);
                q.setFirstResult(start);

                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if (docList==null || docList.size()==0) {
            return new ArrayList<Doctor>();
        }
        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        DoctorTabDAO doctorTabDAO=DAOFactory.getDAO(DoctorTabDAO.class);
        for (Doctor doctor : docList) {
            Integer docId = doctor.getDoctorId();
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(docId);
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            if(busType==BussTypeConstant.MEETCLINIC){//zhangsl 2017-05-26 15:25:51会诊中心标记新增
                doctor.setMeetCenter(doctorTabDAO.getMeetTypeByDoctorId(docId));
            }
            ConsultSet cs = csDao.get(docId);
            Integer isOpen = 0;
            if (cs != null) {
                switch (busType) {
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
                        break;
                    default:

                }
            }
            doctor.setIsOpen(isOpen);
            targets.add(doctor);
        }
        return targets;
    }
}
