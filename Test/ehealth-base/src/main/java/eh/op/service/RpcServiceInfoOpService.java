package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.entity.bus.RpcServiceInfo;
import eh.op.dao.RpcServiceInfoDao;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Created by andywang on 2016/11/17.
 */
public class RpcServiceInfoOpService {

    @RpcService
    public RpcServiceInfo addRpcServiceInfo(Integer organId, String organName, String serviceName, String url)
    {
        if (organId == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "Organ is required!");
        }
        if (StringUtils.isEmpty(organName)) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "Organ is required!");
        }
        if (StringUtils.isEmpty(serviceName)) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "ServiceName is required!");
        }
        if (StringUtils.isEmpty(url)) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "URL is required!");
        }
        RpcServiceInfo rpc = new RpcServiceInfo();
        rpc.setOrganId(organId);
        rpc.setOrganName(organName);
        rpc.setUrl(url);
        rpc.setServiceName(serviceName);
        RpcServiceInfoDao rpcServiceInfoDao = DAOFactory.getDAO(RpcServiceInfoDao.class);
        return rpcServiceInfoDao.addRpcServiceInfo(rpc);
    }

    @RpcService
    public RpcServiceInfo updateRpcServiceInfo(Integer id, Integer organId, String organName, String serviceName, String url)
    {
        if (id == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "RPCServiceInfo Id is required!");
        }
        if (organId == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "Organ is required!");
        }
        if (StringUtils.isEmpty(organName)) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "Organ is required!");
        }
        if (StringUtils.isEmpty(serviceName)) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "ServiceName is required!");
        }
        if (StringUtils.isEmpty(url)) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "URL is required!");
        }
        RpcServiceInfo rpc;
        RpcServiceInfoDao rpcServiceInfoDao = DAOFactory.getDAO(RpcServiceInfoDao.class);
        rpc = rpcServiceInfoDao.getById(id);
        if (rpc == null)
        {
            throw  new DAOException(DAOException.VALUE_NEEDED, "Cannot find RpcServiceInfoBId(" + id + ")!");
        }
        rpc.setOrganId(organId);
        rpc.setOrganName(organName);
        rpc.setUrl(url);
        rpc.setServiceName(serviceName);
        rpcServiceInfoDao.updateRpcServiceInfo(rpc);


        return rpc;
    }

    @RpcService
    public void addOrUpdateRpcServiceInfoByList(List<RpcServiceInfo> list)
    {
        if(list == null)
        {
            throw  new DAOException(DAOException.VALUE_NEEDED, "RpcServiceInfo列表为空!");
        }
        RpcServiceInfoDao rpcServiceInfoDao = DAOFactory.getDAO(RpcServiceInfoDao.class);
        rpcServiceInfoDao.addOrUpdateRpcServiceInfoByList(list);
    }

    @RpcService
    public List<RpcServiceInfo> findRpcServiceInfoByOrganWithHPrepend(final Integer organId) {
        if (organId == null || organId == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        RpcServiceInfoDao rpcServiceInfoDao = DAOFactory.getDAO(RpcServiceInfoDao.class);
        return rpcServiceInfoDao.findRpcServiceInfoByOrganWithHPrepend(organId);
    }

    @RpcService
    public  RpcServiceInfo getById(Integer id)
    {
        if (id == null || id == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "id is required!");
        }
        RpcServiceInfoDao rpcServiceInfoDao = DAOFactory.getDAO(RpcServiceInfoDao.class);
        return rpcServiceInfoDao.getById(id);
    }

    @RpcService
    public  void deleteRpcServiceInfoById(Integer id)
    {
        if (id == null || id == 0) {
            throw  new DAOException(DAOException.VALUE_NEEDED, "id is required!");
        }
        RpcServiceInfoDao rpcServiceInfoDao = DAOFactory.getDAO(RpcServiceInfoDao.class);
        rpcServiceInfoDao.deleteRpcServiceInfoById(id);
    }

    @RpcService
    public QueryResult<RpcServiceInfo> queryRpcServiceInfoByStartAndLimit(final Integer organId, final int start, final int limit) {
        RpcServiceInfoDao rpcServiceInfoDao = DAOFactory.getDAO(RpcServiceInfoDao.class);
        return rpcServiceInfoDao.queryRpcServiceInfoByStartAndLimit(organId, start, limit);
    }
}
