package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.mpi.FollowPlanTriggerRule;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-06-06 13:39
 **/
public abstract class FollowPlanTriggerRuleDAO extends HibernateSupportDelegateDAO<FollowPlanTriggerRule> {

    public FollowPlanTriggerRuleDAO() {
        super();
        setEntityName(FollowPlanTriggerRule.class.getName());
        setKeyField("ruleId");
    }

    @DAOMethod(limit = 0)
    public abstract List<FollowPlanTriggerRule> findByTriggerId(Integer triggerId);

    @DAOMethod(sql = "delete from FollowPlanTriggerRule where triggerId=:triggerId")
    public abstract void deleteByTriggerId(@DAOParam("triggerId") Integer triggerId);
}
