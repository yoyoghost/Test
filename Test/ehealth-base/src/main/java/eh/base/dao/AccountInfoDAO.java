package eh.base.dao;


import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.base.AccountInfo;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;


public abstract  class AccountInfoDAO extends HibernateSupportDelegateDAO<AccountInfo> {

    public AccountInfoDAO() {
        super();
        setEntityName(AccountInfo.class.getName());
        setKeyField("msgId");
    }

    @Override
    public AccountInfo save(AccountInfo info){
        Date now=new Date();
        info.setCreateTime(now);
        info.setLastModify(now);
        info.setMsgStatus(0);
        return super.save(info);
    }


    /**
     * 状态更新成已处理
     * @param msgId
     * @return
     */
    public int updateStatusToOver(final Integer msgId){
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query q = ss.createQuery("update AccountInfo set lastModify=:lastModify, msgStatus=1 where msgId=:msgId and msgStatus !=1");
                q.setInteger("msgId", msgId);
                q.setParameter("lastModify",new Date());
                setResult(q.executeUpdate());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


}
