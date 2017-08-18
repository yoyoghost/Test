package eh.op.dao;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.wx.WeChatAutoMsg;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jianghc
 * @create 2017-02-21 13:41
 **/
public abstract class WeChatAutoMsgDAO extends HibernateSupportDelegateDAO<WeChatAutoMsg> {

    public WeChatAutoMsgDAO() {
        super();
        setEntityName(WeChatAutoMsg.class.getName());
        setKeyField("Id");
    }

    @DAOMethod
    public abstract WeChatAutoMsg getByConfigIdAndReplyType(Integer configId,Integer replyType);

    @DAOMethod(sql = " from WeChatAutoMsg where replyType in(5,6) and configId =:configId and replyParam=:replyParam")
    public abstract List<WeChatAutoMsg> findByConfigIdAndReplyParam(@DAOParam("configId") Integer configId,@DAOParam("replyParam") String replyParam);


    public QueryResult<WeChatAutoMsg> queryWeChatAutoMsgs(final Integer configId,final Boolean keyFlag,final String keyWord, final Integer start, final Integer  limit){

        if(configId==null){
            throw new DAOException(DAOException.VALUE_NEEDED," configId is require");
        }
        if(keyFlag==null){
            throw new DAOException(DAOException.VALUE_NEEDED," keyFlag is require");
        }

        HibernateStatelessResultAction<QueryResult<WeChatAutoMsg>> action = new AbstractHibernateStatelessResultAction<QueryResult<WeChatAutoMsg>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        " from WeChatAutoMsg where configId=").append(configId);
                if (keyFlag){
                    hql.append(" And replyType in(5,6) ");
                }
                if(!StringUtils.isEmpty(keyWord)){
                    hql.append(" And replyParam like'%").append(keyWord).append("%'");
                }
                Query countQuery = ss.createQuery(" select count (*) "+hql.toString());
                Long total = (Long) countQuery.uniqueResult();
                Query query = ss.createQuery(hql.toString()+" order by id desc");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<WeChatAutoMsg> list = query.list();
                if(list==null){
                    list = new ArrayList<WeChatAutoMsg>();
                }
                setResult(new QueryResult<WeChatAutoMsg>(total,start,limit,list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public WeChatAutoMsg getKeyWordReply(final Integer configId,final String keyWord){
        if(configId==null){
            throw new DAOException(DAOException.VALUE_NEEDED," configId is require");
        }
        if(keyWord==null||StringUtils.isEmpty(keyWord)){
            throw new DAOException(DAOException.VALUE_NEEDED," keyWord is require");
        }
        HibernateStatelessResultAction<WeChatAutoMsg> action = new AbstractHibernateStatelessResultAction<WeChatAutoMsg>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        " from WeChatAutoMsg where replyType in(5,6) And configId=").append(configId)
                        .append(" And ((replyType=5 and replyParam='").append(keyWord.trim()).append("')Or(replyType=6 and '").append(keyWord.trim()).append("' like CONCAT('%',replyParam,'%'))) order by id desc ");
                Query query = ss.createQuery(hql.toString());
                query.setFirstResult(0);
                query.setMaxResults(1);
                List<WeChatAutoMsg> list = query.list();
                if(list==null||list.size()<=0){
                    setResult(null);
                }else {
                    setResult(list.get(0));
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }





}
