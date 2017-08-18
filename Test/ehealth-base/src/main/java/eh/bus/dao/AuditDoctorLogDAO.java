package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.EmploymentDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.bus.AuditDoctorLog;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by houxr on 2016/5/27.
 * 医生审核
 */
public abstract class AuditDoctorLogDAO extends HibernateSupportDelegateDAO<AuditDoctorLog> {

    public AuditDoctorLogDAO() {
        super();
        this.setEntityName(AuditDoctorLog.class.getName());
        this.setKeyField("auditDoctorLogId");
    }

    @RpcService
    @DAOMethod
    public abstract AuditDoctorLog getByAuditDoctorLogId(int auditDoctorLogId);

    @RpcService
    @DAOMethod
    public abstract List<AuditDoctorLog> findAuditDoctorLogByDoctorId(int doctorId);

    /**
     * 查询医生信息和医生执业信息服务
     *
     * @param name     医生姓名
     * @param idNumber 身份证号码
     * @param organ    机构
     * @param start    分页起始
     * @param limit    条数
     * @return docList 医生列表
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> queryDoctorAndEmploymentForOP(final String name,
                                                      final String idNumber, final Integer organ,
                                                      final String profession, final Integer department,
                                                      final Integer start, final Integer limit) {

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select DISTINCT a from Doctor a,Employment b "
                                + "where a.doctorId=b.doctorId");
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and a.name like :name");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and a.idNumber like :idNumber");
                }
                if (organ != null) {
                    hql.append(" and b.organId=:organ");
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and a.profession like :profession");
                }
                if (department != null) {
                    hql.append(" and b.department=:department");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(name)) {
                    q.setString("name", "%" + name + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    q.setParameter("idNumber", "%" + idNumber + "%");
                }
                if (organ != null) {
                    q.setParameter("organ", organ);
                }
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (department != null) {
                    q.setParameter("department", department);
                }
                if (start != null) {
                    q.setFirstResult(start);
                    q.setMaxResults(limit);
                }
                List<Doctor> doctors = q.list();
                setResult(doctors);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> docs = action.getResult();
        if (docs == null || docs.size() <= 0) {
            return new ArrayList<Doctor>();
        }
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        for (int i = 0; i < docs.size(); i++) {
            Doctor d = docs.get(i);
            List<Employment> employments = dao.findByDoctorIdAndOrganId(d.getDoctorId(), d.getOrgan());
            if (employments != null && employments.size() > 0) {
                docs.get(i).setEmployments(employments);
            }
        }
        return docs;
    }

    /**
     * 审核中
     *
     * @return
     */
    @RpcService
    @DAOMethod(sql = "SELECT count(*) FROM Doctor d WHERE d.status = 2")
    public abstract Long getCountAuditDoctorStatusTwo();

    /**
     * 再次审核
     *
     * @return
     */
    @RpcService
    @DAOMethod(sql = "SELECT count(*) FROM Doctor d WHERE d.status = 2 AND d.doctorId IN (SELECT a.doctorId FROM AuditDoctorLog a)")
    public abstract Long getCountAuditDoctorStatusTwoAndAgain();

    /**
     * 医生审核日志记录
     *
     * @param auditDoctorLog
     * @return
     * @author houxr
     */
    public AuditDoctorLog addAuditDoctorLog(AuditDoctorLog auditDoctorLog) {
        if (auditDoctorLog.getUserRolesId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userRolesId is required");
        }
        if (StringUtils.isEmpty(auditDoctorLog.getUserName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userName is required");
        }
        if (auditDoctorLog.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }
        auditDoctorLog.setDoctorId(auditDoctorLog.getDoctorId());
        auditDoctorLog.setUserRolesId(auditDoctorLog.getUserRolesId());
        auditDoctorLog.setUserName(auditDoctorLog.getUserName());
        auditDoctorLog.setAuditDate(new Date());
        auditDoctorLog.setTimer(1);//初次次审核
        auditDoctorLog.setReason(auditDoctorLog.getReason());
        auditDoctorLog.setMemo(auditDoctorLog.getMemo());
        return save(auditDoctorLog);
    }

    /**
     * 更新审核记录
     *
     * @param auditDoctorLog
     * @return
     */
    public AuditDoctorLog updateAuditDoctorLogForOp(AuditDoctorLog auditDoctorLog) {
        AuditDoctorLog auditDoc = this.getByAuditDoctorLogId(auditDoctorLog.getAuditDoctorLogId());
        BeanUtils.map(auditDoctorLog, auditDoc);
        update(auditDoc);
        return auditDoc;
    }

    /**
     * 查询医生信息和医生执业信息服务 医生审核状态为 1审核通过，状态正常 9注销
     *
     * @param name
     * @param idNumber
     * @param organ
     * @param profession
     * @param department
     * @param start
     * @param limit
     * @return docList
     * @author houxr
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> queryDoctorAndEmployment(final String name,
                                                 final String idNumber, final Integer organ,
                                                 final String profession, final Integer department,
                                                 final Integer start, final Integer limit) {

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select DISTINCT a from Doctor a,Employment b "
                                + "where a.doctorId=b.doctorId and a.status in (1,9)");
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and a.name like :name");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and a.idNumber like :idNumber");
                }
                if (organ != null) {
                    hql.append(" and b.organId=:organ");
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and a.profession like :profession");
                }
                if (department != null) {
                    hql.append(" and b.department=:department");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(name)) {
                    q.setString("name", "%" + name + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    q.setParameter("idNumber", "%" + idNumber + "%");
                }
                if (organ != null) {
                    q.setParameter("organ", organ);
                }
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (department != null) {
                    q.setParameter("department", department);
                }
                if (start != null) {
                    q.setMaxResults(limit);
                    q.setFirstResult(start);
                }
                List<Doctor> doctors = q.list();

                setResult(doctors);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> docs = action.getResult();
        if (docs == null || docs.size() <= 0) {
            return new ArrayList<Doctor>();
        }

        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        for (int i = 0; i < docs.size(); i++) {
            Doctor d = docs.get(i);
            List<Employment> es = dao.findByDoctorIdAndOrganId(d.getDoctorId(),
                    d.getOrgan());
            if (es != null && es.size() > 0) {
                docs.get(i).setEmployments(es);
            }
        }
        return docs;
    }

    /**
     * 查询产生过审核日志的医生
     *
     * @param status 医生状态
     * @param start 分页开始
     * @param limit 分页限制条数
     * @return
     */
    @DAOMethod(sql = "select d from Doctor d where (select count(*) from AuditDoctorLog l where d.doctorId = l.doctorId)>0 and d.status=:status order by d.createDt desc")
    public abstract QueryResult<Doctor> queryDoctorHasLogByStatus(@DAOParam("status") Integer status,
                                                                  @DAOParam(pageStart = true) int start,
                                                                  @DAOParam(pageLimit = true) int limit);



    public  QueryResult<Doctor> queryDoctorHasLogByStatusAndNameAndIdNumberAndOrgan(final String docName,final String IDNumber,final Integer organ,final Integer status,final int start,final  int limit){
        if(status==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"status is require");
        }
        HibernateStatelessResultAction<QueryResult<Doctor>> action = new AbstractHibernateStatelessResultAction<QueryResult<Doctor>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {

                StringBuffer hql = new StringBuffer(" from Doctor d where (select count(*) from AuditDoctorLog l where d.doctorId = l.doctorId)>0 ");
                hql.append(" And d.status=").append(status);
                if(!StringUtils.isEmpty(docName)){
                    hql.append(" And d.name like'%").append(docName).append("%'");
                }
                if(!StringUtils.isEmpty(IDNumber)){
                    hql.append(" And d.idNumber='").append(IDNumber).append("'");
                }
                if(organ!=null){
                    hql.append(" And d.organ=").append(organ);
                }
                Query query = ss.createQuery("SELECT count(*) " + hql.toString());
                Long total = (long) query.uniqueResult();//获取总条数
                Query q = ss.createQuery("SELECT d "+hql.append(" order by d.createDt desc").toString());
				List<Doctor> list = q.list();
                if(list==null){
                    list = new ArrayList<Doctor>();
                }
                setResult(new QueryResult<Doctor>(total,start,limit,list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
       return action.getResult();

    }





}
