package eh.base.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.AliPayConfigDAO;
import eh.entity.base.AliPayConfig;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * @author jianghc
 * @create 2016-11-25 15:03
 **/
public class AliPayConfigService {
    private static final Logger log = Logger.getLogger(AliPayConfigService.class);
    AliPayConfigDAO aliPayConfigDAO;

    public AliPayConfigService() {
        aliPayConfigDAO = DAOFactory.getDAO(AliPayConfigDAO.class);
    }

    @RpcService
    public QueryResult<AliPayConfig> queryAllConfigs(final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<AliPayConfig>> action = new AbstractHibernateStatelessResultAction<QueryResult<AliPayConfig>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                int total = 0;
                StringBuilder hql = new StringBuilder("from AliPayConfig where 1=1");
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                total = ((Long) countQuery.uniqueResult()).intValue();//获取总条数
                hql.append(" order by id ");
                Query query = ss.createQuery(hql.toString());
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<AliPayConfig> list = query.list();
                QueryResult<AliPayConfig> qResult = new QueryResult<AliPayConfig>(
                        total, query.getFirstResult(), query.getMaxResults(), list);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @RpcService
    public AliPayConfig createOneConfig(AliPayConfig config) {
        if (config == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " alipayConfig is require");
        }
        if (config.getAppID() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " appid is require");
        }
        config.setId(null);
        if (aliPayConfigDAO.getByAppID(config.getAppID()) != null) {
            throw new DAOException(" this appid is exist");
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        if(urt==null){
            return null;
        }
        config.setCreator(urt.getId()+"");
        config.setCreateDate(new Date());
        config.setUpdater(urt.getId()+"");
        config.setLastModify(new Date());

        AliPayConfig target = aliPayConfigDAO.save(config);

        BusActionLogService.recordBusinessLog("支付宝账号管理", target.getId().toString(), "AliPayConfig",
                "新增账号：" + target.getPaymentName());
        return target;
    }

    @RpcService
    public void deleteOneConfig(Integer configId) {
        if (configId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " configId is require");
        }
        AliPayConfig config = aliPayConfigDAO.getById(configId);
        if (config == null) {
            throw new DAOException(" configId is not exist");
        }

        aliPayConfigDAO.deleteOneById(configId);
        BusActionLogService.recordBusinessLog("支付宝账号管理", config.getId().toString(), "AliPayConfig",
                "删除账号：" + config.getPaymentName());

    }

    @RpcService
    public AliPayConfig updateOneConfig(AliPayConfig config) {
        if (config == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " alipayConfig is require");
        }
        if (config.getId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " id is require");
        }
        if (config.getAppID() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " appid is require");
        }
        AliPayConfig target = aliPayConfigDAO.getById(config.getId());
        BeanUtils.map(config, target);

        UserRoleToken urt = UserRoleToken.getCurrent();
        if(urt==null){
            return null;
        }
        target.setUpdater(urt.getId()+"");
        target.setLastModify(new Date());


        target = aliPayConfigDAO.update(target);
        BusActionLogService.recordBusinessLog("支付宝账号管理", target.getId().toString(), "AliPayConfig",
                "更新账号：" + target.getPaymentName());

        return target;
    }


}
