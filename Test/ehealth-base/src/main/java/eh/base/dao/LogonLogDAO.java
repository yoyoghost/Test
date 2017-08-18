package eh.base.dao;

import ctd.account.UserRoleToken;
import ctd.account.user.UserRoleTokenEntity;
import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Publisher;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.base.service.BusActionLogService;
import eh.bus.service.consult.OnsConfig;
import eh.entity.base.Doctor;
import eh.entity.base.LogonLog;
import eh.utils.DateConversion;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.*;

public abstract class LogonLogDAO extends HibernateSupportDelegateDAO<LogonLog> {
    public static final Logger logger = Logger.getLogger(LogonLogDAO.class);
    public LogonLogDAO() {
        super();
        setEntityName(LogonLog.class.getName());
        setKeyField("id");
    }


    @DAOMethod
    public abstract LogonLog getByUserIdAndLoginTime(String userId,Date loginTime);


    @RpcService
    public void saveLog(LogonLog log) {
        this.save(log);
    }

    public QueryResult<LogonLog> queryLogonLog(final String roleId,final Integer organId, final Integer urt, final Date bDate, final Date eDate, final int start, final int limit) {
        if (bDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "bDate is require");
        }
        if (eDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "eDate is require");
        }

        HibernateStatelessResultAction<QueryResult<LogonLog>> action = new AbstractHibernateStatelessResultAction<QueryResult<LogonLog>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer(" from LogonLog where roleId =:roleId and loginTime>=:bDate and loginTime<=:eDate ");
                if (organId != null) {
                    hql.append(" and organ = ").append(organId);
                }
                if (urt != null) {
                    hql.append(" and urt=").append(urt);
                }
                if(roleId.equals(SystemConstant.ROLES_DOCTOR)){
                    UserRoleToken userRoleToken = UserRoleToken.getCurrent();
                    if(userRoleToken==null){
                        throw new DAOException("UserRoleToken is require");
                    }
                    String mu = userRoleToken.getManageUnit();
                    if(!"eh".equals(mu)){
                    hql.append(" and manageUnit like '").append(mu).append("%'");
                    }
                }

                //获取后一天日期
                Date endDate =DateConversion.getDateAftXDays(eDate,1);

                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                countQuery.setParameter("roleId",roleId);
                countQuery.setParameter("bDate",bDate);
                countQuery.setParameter("eDate",endDate);
                long total = (long) countQuery.uniqueResult();//获取总条数
                Query query = ss.createQuery(hql.toString() + " order by loginTime DESC");
                query.setParameter("roleId",roleId);
                query.setParameter("bDate",bDate);
                query.setParameter("eDate",endDate);
                query.setMaxResults(limit);
                query.setFirstResult(start);
                List<LogonLog> list = query.list();
                if (list == null) {
                    list = new ArrayList<LogonLog>();
                }
                setResult(new QueryResult<LogonLog>(total, start, limit, list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
    @RpcService
    public void recordLogonLog(UserRoleToken urt){
        Publisher publisher = MQHelper.getMqPublisher();
        Map<String, Object> data = new HashMap<String,Object>();
        data.put("type", BusActionLogService.LOGON_LOG);
        data.put("urt",urt);
        try {
            publisher.publish(OnsConfig.logTopic, data);
        }catch (Exception e){
            logger.error("登录日志记录失败:"+urt.getId());
        }

    }



    public void saveLongLogByUserRoleTokenAndDate(UserRoleToken urt){
        if(urt==null){
            logger.error(" logonLog error:urt is null");
        }
        this.getByUserIdAndLoginTime(urt.getUserId(),urt.getLastLoginTime());
        LogonLog log = new LogonLog();
        log.setUserId(urt.getUserId());
        log.setLoginTime(urt.getLastLoginTime());
        log.setIpAddress(urt.getLastIPAddress());
        log.setUserName(urt.getUserName());
        log.setUrt(urt.getId());
        try{
            UserRoleTokenEntity dUrt = (UserRoleTokenEntity) urt;
            log.setUserAgent(dUrt.getLastUserAgent());
            log.setRoleId(dUrt.getRoleId());
            log.setTenantId(dUrt.getTenantId());
            log.setManageUnit(dUrt.getManageUnit());
            if(SystemConstant.ROLES_DOCTOR.equals(dUrt.getRoleId())){
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                Doctor doctor  = doctorDAO.getByMobile(urt  .getUserId());
                if(doctor!=null){
                    log.setOrgan(doctor.getOrgan());
                }
            }
        }catch (Exception e){
            logger.error("login error："+e.getMessage());
        }
        this.save(log);
    }




}
