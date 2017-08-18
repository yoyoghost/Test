package eh.op.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.wx.WXConfig;
import eh.entity.wx.WxAppProps;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

/**
 * @author jianghc
 * @create 2016-11-24 19:00
 **/
public abstract class WxAppPropsDAO extends HibernateSupportDelegateDAO<WxAppProps> {
    public WxAppPropsDAO(){
        super();
        this.setEntityName(WxAppProps.class.getName());
        this.setKeyField("id");
    }


    @RpcService
    @DAOMethod(limit = 0)
    public abstract List<WxAppProps> findByConfigId(Integer configId);

    @DAOMethod
    public abstract WxAppProps getByConfigIdAndPropName(Integer configId,String propName);

    @DAOMethod(sql = " delete from WxAppProps where configId=:configId")
    public abstract void deleteByConfigId(@DAOParam("configId") Integer configId);

    @DAOMethod(sql = " delete from WxAppProps where id=:id")
    public abstract void deleteById(@DAOParam("id") Integer id);

    /**
     * 公众号是否可以心意
     * @param configId
     * @return true可以 false 不可以
     */
    public boolean canMindGift(Integer configId){
        WxAppProps wxAppProps = this.getByConfigIdAndPropName(configId,"canMindGift");
        if(wxAppProps==null){
            return false;
        }
        return wxAppProps.getPropValue().equals("1")?true:false;
    }

    /**
     * 如果数据库有这个不需要显示评价的记录,则医生主页不显示评价
     *
     * @param configId wx_config表对应的主键
     * @return true可以 false 不可以
     * @athour cuill
     * @date 2017/6/15
     */
    public boolean canShowEvaluationForWx(Integer configId) {
        return this.getByConfigIdAndPropName(configId, "notShowEvaluation") == null ? true : false;
    }

    public QueryResult<WXConfig> queryWxConfigForMindGift(boolean canMindGift, final int start, final int limit){
        StringBuffer sb = new StringBuffer(" from WXConfig o where 1=1 and o.id");
        if(!canMindGift){
            sb.append(" not");
        }
        sb.append(" in(select e.configId from WxAppProps e where e.propName='canMindGift' and e.configId =o.id )");

        final String hql = sb.toString();
        HibernateStatelessResultAction<QueryResult<WXConfig>> action = new AbstractHibernateStatelessResultAction<QueryResult<WXConfig>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = ss.createQuery("SELECT count(*) " + hql);
                long total = (long) query.uniqueResult();//获取总条数
                query = ss.createQuery(hql + " order by id");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<WXConfig>(total, start, limit, query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }




}
