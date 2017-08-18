package eh.mpi.dao;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import eh.entity.mpi.FollowPlanTrigger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zhangyq on 2017/4/24.
 */
public abstract class FollowPlanTriggerDAO extends HibernateSupportDelegateDAO<FollowPlanTrigger> {

    public FollowPlanTriggerDAO() {
        super();
        this.setEntityName(FollowPlanTrigger.class.getName());
        this.setKeyField("triggerId");
    }

    @DAOMethod(sql = "select a from FollowPlanTrigger a where triggerEvent=:triggerEvent  and targetDoctor =:targetDoctor and status = 1 order by triggerId desc")
    public abstract List<FollowPlanTrigger> findByDoctorAndEvent(@DAOParam("targetDoctor") Integer targetDoctor,@DAOParam("triggerEvent")String triggerEvent);

    public List<FollowPlanTrigger> findTriggers(final FollowPlanTrigger trigger,final int start,final int limit) {
        AbstractHibernateStatelessResultAction<List<FollowPlanTrigger>> action = new AbstractHibernateStatelessResultAction<List<FollowPlanTrigger>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder sb = new StringBuilder(" from FollowPlanTrigger where 1=1");
                Map<String,Object> map = new HashMap<String, Object>();
                if (trigger != null) {
                    if (!StringUtils.isEmpty(trigger.getTriggerEvent())) {
                        sb.append(" and triggerEvent =:triggerEvent");
                        map.put("triggerEvent",trigger.getTriggerEvent());
                    }
                    if (!StringUtils.isEmpty(trigger.getTriggerCondition())) {
                        sb.append(" and triggerCondition =:triggerCondition");
                        map.put("triggerCondition",trigger.getTriggerCondition());
                    }
                    if (!StringUtils.isEmpty(trigger.getTipText())) {
                        sb.append(" and tipText like:tipText");
                        map.put("tipText","%"+trigger.getTipText()+"%");
                    }
                    if (trigger.getTargetDoctor() != null) {
                        sb.append(" and targetDoctor =:targetDoctor");
                        map.put("targetDoctor",trigger.getTargetDoctor());
                    }
                    if (trigger.getFollowModuleId() != null) {
                        sb.append(" and followModuleId =:followModuleId");
                        map.put("followModuleId",trigger.getFollowModuleId());
                    }
                }
                sb.append(" order by triggerId desc");
                Query query = ss.createQuery(sb.toString());
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    query.setParameter(entry.getKey(),entry.getValue());
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

}