package eh.op.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.bus.InternalSystemMessage;
import eh.utils.DateConversion;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * Created by andywang on 2017/6/16.
 */
public abstract class InternalSystemMessageDAO extends HibernateSupportDelegateDAO<InternalSystemMessage> {

    public static final Logger log = Logger.getLogger(InternalSystemMessageDAO.class);

    public InternalSystemMessageDAO(){
        super();
        this.setEntityName(InternalSystemMessage.class.getName());
        this.setKeyField("id");
    }

    public Integer addMessage(Integer visibleTo, Integer emergency, String title, String message, Date startTime, Date endTime){
        InternalSystemMessage msg = new InternalSystemMessage();
        msg.setEmergency(emergency);//系统警告
        msg.setVisibleTo(visibleTo);//平台管理员可见
        msg.setTitle(title);
        msg.setMessage(message);
        Date now = new Date();
        msg.setStartTime(startTime);
        msg.setEndTime(endTime);
        msg.setCreateTime(now);
        msg.setLastUpdateTime(now);
        msg.setStatus(0);
        return this.save(msg).getId();
    }


    public Integer addUrgentMessage( String title, String message, String serviceName, Integer defaultDays){
        if (title == null || message == null)
        {
            return 0;
        }
        if (defaultDays == null)
        {
            defaultDays = 7;
        }
        if (this.isActiveServiceMessageExist(serviceName, 9))
        {
            return 0;
        }
        InternalSystemMessage msg = new InternalSystemMessage();
        msg.setEmergency(9);//系统警告
        msg.setVisibleTo(-1);//平台管理员可见
        msg.setTitle(title);
        msg.setServiceName(serviceName);
        msg.setMessage(message);
        Date now = new Date();
        Date endtime = DateConversion.getDateAftXDays(new Date(), defaultDays);
        msg.setStartTime(now);
        msg.setEndTime(endtime);
        msg.setCreateTime(now);
        msg.setLastUpdateTime(now);
        msg.setStatus(0);
        return this.save(msg).getId();
    }

    @DAOMethod(sql = " from InternalSystemMessage where emergency =:emergency and Status=:status and visibleTo = -1")
    public abstract List<InternalSystemMessage> findActivePlatformMeesageByEmergency(@DAOParam("emergency") Integer emergency,@DAOParam("status") Integer status);

    public List<InternalSystemMessage> findActivePlatformUrgentMessage(){
        return this.findActivePlatformMeesageByEmergency(9,0);
    }

    public List<InternalSystemMessage> findHiddenPlatformUrgentMessage(){
        return this.findActivePlatformMeesageByEmergency(9,1);
    }


    public Long getActiveCountByServiceNameAndEmergency( final String serviceName,final Integer emergency) {
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select count(*) from InternalSystemMessage where status = 0 and serviceName=:serviceName and emergency=:emergency");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("serviceName", serviceName);
                query.setParameter("emergency", emergency);
                long num = (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public Boolean checkActiveStatusById(Integer id){
        if (id == null)
        {
            return false;
        }
        InternalSystemMessage message = this.get(id);
        if(message != null)
        {
            if (message.getStatus() == 0 )
            {
                return true;
            }
        }
        return false;
    }

    public InternalSystemMessage hideMessageById(final Integer id){
        if (id == null)
        {
            return null;
        }
        InternalSystemMessage message = this.get(id);
        if(message != null)
        {
            final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql = new StringBuilder("update InternalSystemMessage set status =1  where id=:id and status=0");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("id", id);
                    Integer count = q.executeUpdate();
                    setResult(count);
                }
            };
            HibernateSessionTemplate.instance().execute(action);
            Integer count = action.getResult();
            log.info(" Message: hideMessageById"  +  " ID: " + id  + " Affect Count: " + count);
        }
        return message;
    }

    private Boolean isActiveServiceMessageExist(String serviceName, Integer emergency)
    {
        Long activeCount = this.getActiveCountByServiceNameAndEmergency(serviceName,emergency);
        if (activeCount != null && activeCount.intValue() > 0)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

}
