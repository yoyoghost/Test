package eh.op.dao;

import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.bus.CRMUser;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * Created by houxr on 2016/6/14.
 * CRM用户管理相关查询
 */
public class CrmUserDAO extends HibernateSupportDelegateDAO<CRMUser> {

    private static final Log logger = LogFactory.getLog(CrmUserDAO.class);

    /**
     * 根据条件查询CRM用户
     *
     * @param patientName 客户姓名
     * @param idNumber    身份证号
     * @param patientSex  性别
     * @param timeType    查询类型：1注册时间 2上次访问时间
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param start       分页起始
     * @param limit       每页显示条数
     * @return
     */
    @RpcService
    public QueryResult<CRMUser> queryListCRMUser(final String patientName, final String idNumber, final String patientSex,
                                                 final Integer minAge, final Integer maxAge, final String wxSub, final String requestMode, final Integer timeType, final Date startTime, final Date endTime,
                                                 final Integer start, final Integer limit) {
        if (startTime == null) {
//            logger.error("统计开始时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
//            logger.error("统计结束时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        HibernateStatelessResultAction<QueryResult<CRMUser>> action = new AbstractHibernateStatelessResultAction<QueryResult<CRMUser>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                int total = 0;
                StringBuilder hql = new StringBuilder("FROM UserRoleTokenEntity a,User b,Patient c " +
                        " where a.roleId='patient' and a.userId=b.id " +
                        " and a.userId = c.loginId and c.idcard is not null"
                );
                if (timeType == 1) {//用户创建时间
                    hql.append(" and DATE(c.createDate)>=DATE(:startTime) and DATE(c.createDate)<=DATE(:endTime) ");
                }
                if (timeType == 2) {//上次登录时间
                    hql.append(" and DATE(a.lastLoginTime)>=DATE(:startTime) and DATE(a.lastLoginTime)<=DATE(:endTime) ");
                }
                if (!StringUtils.isEmpty(patientName)) {
                    hql.append(" and c.patientName like :patientName ");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and c.idcard like :idNumber ");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    hql.append(" and c.patientSex = :patientSex ");
                }
                if(maxAge>=0) {
                    hql.append(" and DATE(c.birthday)>DATE(:startBirthDay)");
                }
                if(minAge>=0) {
                    hql.append(" and DATE(c.birthday)<=DATE(:endBirthDay)");
                }

                if (StringUtils.equals(requestMode, "c")) {
                    //所有咨询 select * from bus_consult where mpiid=c.mpiid
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId )");
                }
                if (StringUtils.equals(requestMode, "c1")) {
                    //电话咨询 and c.requesMode=1
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId and requestMode=1)");
                }

                if (StringUtils.equals(requestMode, "c2")) {
                    //图文咨询 and c.requesMode=2
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId and requestMode=2)");
                }

                if (!StringUtils.isEmpty(wxSub)) {
                    hql.append(" and exists ( from OAuthWeixinMP where 1=1 and userId=b.id and appId=:wxSub )");
                }

                Query countQuery=ss.createQuery("select count(*) "+ hql.toString());
                if (!StringUtils.isEmpty(patientName)) {
                    countQuery.setString("patientName", "%" + patientName + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    countQuery.setString("idNumber", "%" + idNumber + "%");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    countQuery.setString("patientSex", patientSex);
                }
                if (maxAge >= 0) {
                    final Date startBirthDay = DateConversion.getBirthdayFromAge(maxAge > 0 ? maxAge + 1 : 0);
                    countQuery.setDate("startBirthDay", startBirthDay);
                }
                if (minAge >= 0) {
                    final Date endBirthDay = DateConversion.getBirthdayFromAge(minAge);
                    countQuery.setDate("endBirthDay", endBirthDay);
                }
                if (!StringUtils.isEmpty(wxSub)) {
                    countQuery.setString("wxSub", wxSub);
                }
                countQuery.setDate("startTime", startTime);
                countQuery.setDate("endTime", endTime);
                total=((Long)countQuery.uniqueResult()).intValue();//获取总条数

                Query query = ss.createQuery("select new eh.entity.bus.CRMUser(c.mpiId,c.patientName,b.id,c.idcard,c.birthday,c.patientSex,c.createDate,a.lastLoginTime) "+hql.toString());
                if (!StringUtils.isEmpty(patientName)) {
                    query.setString("patientName", "%" + patientName + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    query.setString("idNumber", "%" + idNumber + "%");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    query.setString("patientSex", patientSex);
                }
                if(maxAge>=0) {
                    final Date startBirthDay = DateConversion.getBirthdayFromAge(maxAge>0?maxAge+1:0);
                    query.setDate("startBirthDay", startBirthDay);
                }
                if(minAge>=0) {
                    final Date endBirthDay = DateConversion.getBirthdayFromAge(minAge);
                    query.setDate("endBirthDay", endBirthDay);
                }
                if (!StringUtils.isEmpty(wxSub)) {
                    query.setString("wxSub", wxSub);
                }
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<CRMUser> resList = query.list();
                int age = -1; //获取年龄
                for (int i = 0; i < resList.size(); i++) {
                    CRMUser crmUser = resList.get(i);
                    if (crmUser.getBirthday() != null) {
                        age = DateConversion.getAge(crmUser.getBirthday());
                    }
                    resList.get(i).setAge(age);
                }
                QueryResult<CRMUser> qResult = new QueryResult<CRMUser>(
                        total, query.getFirstResult(), query.getMaxResults(), resList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<CRMUser>) action.getResult();
    }


    @RpcService
    public List<Object[]> countCRMUserBySex(final String patientName, final String idNumber, final String patientSex,
                                            final Integer minAge,final Integer maxAge,
                                            final String wxSub, final String requestMode, final Integer timeType, final Date startTime, final Date endTime
    ) {
        if (startTime == null) {
//            logger.error("统计开始时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
//            logger.error("统计结束时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select c.patientSex,count(*) " +
                                "FROM UserRoleTokenEntity a,User b,Patient c " +
                                " where a.roleId='patient' and a.userId=b.id " +
                                " and a.userId = c.loginId and c.idcard is not null"
                );
                if (timeType == 1) {//用户创建时间
                    hql.append(" and DATE(c.createDate)>=DATE(:startTime) and DATE(c.createDate)<=DATE(:endTime) ");
                }
                if (timeType == 2) {//上次登录时间
                    hql.append(" and DATE(a.lastLoginTime)>=DATE(:startTime) and DATE(a.lastLoginTime)<=DATE(:endTime) ");
                }
                if (!StringUtils.isEmpty(patientName)) {
                    hql.append(" and c.patientName like :patientName ");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and c.idcard like :idNumber ");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    hql.append(" and c.patientSex = :patientSex ");
                }
                if(maxAge>=0) {
                    hql.append(" and DATE(c.birthday)>DATE(:startBirthDay)");
                }
                if(minAge>=0) {
                    hql.append(" and DATE(c.birthday)<=DATE(:endBirthDay)");
                }
                if (StringUtils.equals(requestMode, "c")) {
                    //所有咨询 select * from bus_consult where mpiid=c.mpiid
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId )");
                }
                if (StringUtils.equals(requestMode, "c1")) {
                    //电话咨询 and c.requesMode=1
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId and requestMode=1)");
                }

                if (StringUtils.equals(requestMode, "c2")) {
                    //图文咨询 and c.requesMode=2
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId and requestMode=2)");
                }

                if (!StringUtils.isEmpty(wxSub)) {
                    hql.append(" and exists ( from OAuthWeixinMP where 1=1 and userId=b.id and appId=:wxSub )");
                }

                Query query = ss.createQuery(hql.toString()+" group by patientSex");
                if (!StringUtils.isEmpty(patientName)) {
                    query.setString("patientName", "%" + patientName + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    query.setString("idNumber", "%" + idNumber + "%");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    query.setString("patientSex", patientSex);
                }
                if(maxAge>=0) {
                    final Date startBirthDay = DateConversion.getBirthdayFromAge(maxAge>0?maxAge+1:0);
                    query.setDate("startBirthDay", startBirthDay);
                }
                if(minAge>=0) {
                    final Date endBirthDay = DateConversion.getBirthdayFromAge(minAge);
                    query.setDate("endBirthDay", endBirthDay);
                }
                if (!StringUtils.isEmpty(wxSub)) {
                    query.setString("wxSub", wxSub);
                }
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> resList = query.list();//[["1",14],["2",8]]
                setResult(resList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @RpcService
    public List<Object[]> countConsultCRMUser(final String patientName, final String idNumber, final String patientSex,
                                              final Integer minAge,final Integer maxAge,
                                              final String wxSub, final String requestMode, final Integer timeType, final Date startTime, final Date endTime
    ) {
        if (startTime == null) {
//            logger.error("统计开始时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
//            logger.error("统计结束时间不能为空");
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select c.mpiId " +
                                "FROM UserRoleTokenEntity a,User b,Patient c " +
                                " where a.roleId='patient' and a.userId=b.id " +
                                " and a.userId = c.loginId and c.idcard is not null"
                );
                if (timeType == 1) {//用户创建时间
                    hql.append(" and DATE(c.createDate)>=DATE(:startTime) and DATE(c.createDate)<=DATE(:endTime) ");
                }
                if (timeType == 2) {//上次登录时间
                    hql.append(" and DATE(a.lastLoginTime)>=DATE(:startTime) and DATE(a.lastLoginTime)<=DATE(:endTime) ");
                }
                if (!StringUtils.isEmpty(patientName)) {
                    hql.append(" and c.patientName like :patientName ");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and c.idcard like :idNumber ");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    hql.append(" and c.patientSex = :patientSex ");
                }
                if(maxAge>=0) {
                    hql.append(" and DATE(c.birthday)>DATE(:startBirthDay)");
                }
                if(minAge>=0) {
                    hql.append(" and DATE(c.birthday)<=DATE(:endBirthDay)");
                }
                //所有咨询
                if (StringUtils.equals(requestMode, "c")) {
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId )");
                }
                //电话咨询
                if (StringUtils.equals(requestMode, "c1")) {
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId and requestMode=1)");
                }
                //图文咨询
                if (StringUtils.equals(requestMode, "c2")) {
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId and requestMode=2)");
                }
                //关注微信公众号
                if (!StringUtils.isEmpty(wxSub)) {
                    hql.append(" and exists ( from OAuthWeixinMP where 1=1 and userId=b.id and appId=:wxSub )");
                }

                Query query = ss.createQuery("select requestMode, count(distinct mpiid)," +
                        " count(*), sum(consultCost) from Consult where mpiid in ("
                        + hql.toString()+") group by requestMode");
                if (!StringUtils.isEmpty(patientName)) {
                    query.setString("patientName", "%" + patientName + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    query.setString("idNumber", "%" + idNumber + "%");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    query.setString("patientSex", patientSex);
                }
                if(maxAge>=0) {
                    final Date startBirthDay = DateConversion.getBirthdayFromAge(maxAge>0?maxAge+1:0);
                    query.setDate("startBirthDay", startBirthDay);
                }
                if(minAge>=0) {
                    final Date endBirthDay = DateConversion.getBirthdayFromAge(minAge);
                    query.setDate("endBirthDay", endBirthDay);
                }
                if (!StringUtils.isEmpty(wxSub)) {
                    query.setString("wxSub", wxSub);
                }
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                List<Object[]> resList = query.list();
                setResult(resList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @RpcService
    public Long countPayedCRMUser(final String patientName, final String idNumber, final String patientSex,
                                  final Integer minAge, final Integer maxAge,
                                  final String wxSub, final String requestMode,
                                  final Integer timeType, final Date startTime, final Date endTime) {
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
                        "select count(*) " +
                                "FROM UserRoleTokenEntity a,User b,Patient c " +
                                " where a.roleId='patient' and a.userId=b.id " +
                                " and a.userId = c.loginId and c.idcard is not null"
                );
                if (timeType == 1) {//用户创建时间
                    hql.append(" and DATE(c.createDate)>=DATE(:startTime) and DATE(c.createDate)<=DATE(:endTime) ");
                }
                if (timeType == 2) {//上次登录时间
                    hql.append(" and DATE(a.lastLoginTime)>=DATE(:startTime) and DATE(a.lastLoginTime)<=DATE(:endTime) ");
                }
                if (!StringUtils.isEmpty(patientName)) {
                    hql.append(" and c.patientName like :patientName ");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and c.idcard like :idNumber ");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    hql.append(" and c.patientSex = :patientSex ");
                }
                if(maxAge>=0) {
                    hql.append(" and DATE(c.birthday)>DATE(:startBirthDay)");
                }
                if(minAge>=0) {
                    hql.append(" and DATE(c.birthday)<=DATE(:endBirthDay)");
                }
                if (StringUtils.equals(requestMode, "c")) {
                    //所有咨询 select * from bus_consult where mpiid=c.mpiid
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId )");
                }
                if (StringUtils.equals(requestMode, "c1")) {
                    //电话咨询 and c.requesMode=1
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId and requestMode=1)");
                }

                if (StringUtils.equals(requestMode, "c2")) {
                    //图文咨询 and c.requesMode=2
                    hql.append(" and exists ( from Consult where 1=1 and mpiid=c.mpiId and requestMode=2)");
                }

                if (!StringUtils.isEmpty(wxSub)) {
                    hql.append(" and exists ( from OAuthWeixinMP where 1=1 and userId=b.id and appId=:wxSub )");
                }

                Query query = ss.createQuery(hql.toString()+" and exists( from Consult where mpiid=c.mpiId and consultCost>0)");
                if (!StringUtils.isEmpty(patientName)) {
                    query.setString("patientName", "%" + patientName + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    query.setString("idNumber", "%" + idNumber + "%");
                }
                if (!StringUtils.isEmpty(patientSex)) {
                    query.setString("patientSex", patientSex);
                }
                if(maxAge>=0) {
                    final Date startBirthDay = DateConversion.getBirthdayFromAge(maxAge>0?maxAge+1:0);
                    query.setDate("startBirthDay", startBirthDay);
                }
                if(minAge>=0) {
                    final Date endBirthDay = DateConversion.getBirthdayFromAge(minAge);
                    query.setDate("endBirthDay", endBirthDay);
                }
                if (!StringUtils.isEmpty(wxSub)) {
                    query.setString("wxSub", wxSub);
                }
                query.setDate("startTime", startTime);
                query.setDate("endTime", endTime);
                Long count = (Long) query.uniqueResult();
                setResult(count);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

}
