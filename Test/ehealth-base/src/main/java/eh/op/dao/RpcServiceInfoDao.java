package eh.op.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.base.dao.BusActionLogDAO;
import eh.base.dao.OrganDAO;
import eh.entity.bus.RpcServiceInfo;
import eh.util.RpcServiceInfoStore;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by andywang on 2016/11/17.
 */
public abstract class RpcServiceInfoDao  extends HibernateSupportDelegateDAO<RpcServiceInfo>{
    private static final Log logger = LogFactory.getLog(RpcServiceInfoDao.class);

    public RpcServiceInfoDao() {
        super();
        this.setEntityName(RpcServiceInfo.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod
    public abstract RpcServiceInfo getByServiceName(String serviceName);

    public void addOrUpdateRpcServiceInfoByList( List<RpcServiceInfo> list)
    {
        if (list == null)
        {
            return;
        }
        Iterator iteValidate = list.iterator();
        while ( iteValidate.hasNext())
        {
            RpcServiceInfo info = (RpcServiceInfo) iteValidate.next();
            this.validateEntity(info);
        }
        try{
            Iterator ite = list.iterator();
            while ( ite.hasNext())
            {
                RpcServiceInfo info = (RpcServiceInfo) ite.next();
                if(info.getId() != null && info.getId() >0)
                {
                    this.updateRpcServiceInfo(info);
                }
                else
                {
                    this.addRpcServiceInfo(info);
                }
            }
        }
        catch (DAOException de)
        {
            logger.error(de.getMessage());
            throw de;
        }
        catch (Exception e)
        {
            logger.error(e.getMessage());
            throw  new DAOException(DAOException.EVAL_FALIED, "批量添加RPC服务信息报错!");
        }
        BusActionLogDAO ldao = DAOFactory.getDAO(BusActionLogDAO.class);
        ldao.recordLog("His服务配置", "","RpcServiceInfo", "添加或者修改了" + list.size() + "个RpcServiceInfo");
    }

    private void validateEntity(RpcServiceInfo rpc)
    {
        if(rpc == null )
        {
            throw  new DAOException(DAOException.VALUE_NEEDED, "RpcServiceInfo Object is Null!");
        }
        if (StringUtils.isEmpty(rpc.getOrganName()) ||rpc.getOrganId() == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "Organ is required!");
        }
        if (StringUtils.isEmpty(rpc.getServiceName())) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "ServiceName is required!");
        }
    }

    public RpcServiceInfo addRpcServiceInfo(RpcServiceInfo rpc) {
        this.validateEntity(rpc);
        RpcServiceInfo rpcTemp = this.getByServiceName(rpc.getServiceName());
        if (rpcTemp != null) {
            throw new DAOException(DAOException.VALIDATE_FALIED, rpc.getServiceName() + " 服务名不能重复，请检查应用程序域ID!");
        }
        try {
            super.save(rpc);
            RpcServiceInfoStore.updateRpcServiceCacheByEntity(rpc);
            if (rpc.getId() > 0) {
                BusActionLogDAO ldao = DAOFactory.getDAO(BusActionLogDAO.class);
                ldao.recordLog("His服务配置", "","RpcServiceInfo", "为" + rpc.getOrganName() + "添加RpcServiceInfo[ServiceName: " + rpc.getServiceName() + " Url:" + rpc.getUrl() + "]");
                return rpc;
            }
        }
        catch (Exception e) {
            throw new DAOException(DAOException.VALIDATE_FALIED, "ServiceName: " + rpc.getServiceName() +  " URL:" +  rpc.getUrl() + "添加失败" );
        }
        return null;
    }

    public RpcServiceInfo updateRpcServiceInfo(RpcServiceInfo rpc)
    {
        if(rpc == null )
        {
            throw  new DAOException(DAOException.VALUE_NEEDED, "RpcServiceInfo Object is Null!");
        }
        if (StringUtils.isEmpty(rpc.getOrganName()) ||rpc.getOrganId() == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "Organ is required!");
        }
        if (StringUtils.isEmpty(rpc.getServiceName())) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "ServiceName is required!");
        }
        super.update(rpc);
        RpcServiceInfoStore.updateRpcServiceCacheByEntity(rpc);
        if (rpc.getId() > 0) {
            BusActionLogDAO ldao = DAOFactory.getDAO(BusActionLogDAO.class);
            ldao.recordLog("His服务配置", "","RpcServiceInfo", "为" + rpc.getOrganName() + "修改RpcServiceInfo[ServiceName: " + rpc.getServiceName() + " Url:" + rpc.getUrl() + "]");
            return rpc;
        }
        return null;
    }


    @DAOMethod()
    public abstract RpcServiceInfo getById(Integer id);


    public void deleteRpcServiceInfoById(final Integer id)
    {
        if (id == null)
        {
            return;
        }
        RpcServiceInfo temp = this.getById(id);
        if(temp == null)
        {
            return;
        }
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder sql = new StringBuilder("delete from RpcServiceInfo where id=:id");
                Query q = ss.createSQLQuery(sql.toString());
                q.setParameter("id",id);
                int flag = q.executeUpdate();
                setResult(flag == 1);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        RpcServiceInfoStore.updateRpcServiceCacheByEntity(temp);
        BusActionLogDAO ldao = DAOFactory.getDAO(BusActionLogDAO.class);
        ldao.recordLog("His服务配置", "","RpcServiceInfo",  temp.getOrganName() + "的pcServiceInfo被删除 [ServiceName: " + temp.getServiceName() + " Url:" + temp.getUrl() + "]");
    }

    public void deleteRpcServiceInfoByOrganId(final Integer organId)
    {
        if (organId == null)
        {
            return;
        }
        RpcServiceInfo temp = this.getById(organId);
        if(temp == null)
        {
            return;
        }
        List<RpcServiceInfo> list = this.findRpcServiceInfoByOrgan(organId);
        Iterator ite = list.iterator();
        while (ite.hasNext())
        {
            RpcServiceInfo info = (RpcServiceInfo)ite.next();
            RpcServiceInfoStore.updateRpcServiceCacheByEntity(info);
        }
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder sql = new StringBuilder("delete from RpcServiceInfo where organId=:organId");
                Query q = ss.createSQLQuery(sql.toString());
                q.setParameter("organId",organId);
                int flag = q.executeUpdate();
                setResult(flag == 1);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        BusActionLogDAO ldao = DAOFactory.getDAO(BusActionLogDAO.class);
        ldao.recordLog("His服务配置", "","RpcServiceInfo",  temp.getOrganName() + "的pcServiceInfo被删除");
    }

    public List<RpcServiceInfo> findRpcServiceInfoByOrgan(final Integer organId) {

        if (organId == null || organId == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        final OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        if (organDAO == null) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "organ not exist!");
        }
        HibernateStatelessResultAction<List<RpcServiceInfo>> action = new AbstractHibernateStatelessResultAction<List<RpcServiceInfo>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "from RpcServiceInfo b where b.organId=:organId  ");
                hql.append(" ORDER BY b.organId,b.serviceName ");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("organId", organId);
                List<RpcServiceInfo> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<RpcServiceInfo>) action.getResult();
    }

    public List<RpcServiceInfo> findRpcServiceInfoByOrganWithHPrepend(final Integer organId) {

        if (organId == null || organId == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        final OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        if (organDAO == null) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "organ not exist!");
        }
        HibernateStatelessResultAction<List<RpcServiceInfo>> action = new AbstractHibernateStatelessResultAction<List<RpcServiceInfo>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "from RpcServiceInfo b where b.organId=:organId and serviceName like 'h%'");
                hql.append(" ORDER BY b.organId,b.serviceName ");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("organId", organId);
                List<RpcServiceInfo> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<RpcServiceInfo>) action.getResult();
    }

    public QueryResult<RpcServiceInfo> queryRpcServiceInfoByStartAndLimit(final Integer organId, final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<RpcServiceInfo>> action = new AbstractHibernateStatelessResultAction<QueryResult<RpcServiceInfo>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                long total = 0;
                StringBuilder hql = new StringBuilder("FROM RpcServiceInfo w WHERE 1=1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (organId != null && organId >0) {
                    hql.append(" and w.organId=:organId ");
                    params.put("organId", organId);
                }

                Query query = ss.createQuery("SELECT count(*) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                query = ss.createQuery("SELECT w " + hql.toString());
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<RpcServiceInfo>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
}
