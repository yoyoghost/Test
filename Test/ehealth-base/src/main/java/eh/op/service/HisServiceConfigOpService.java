package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.HisServiceConfigDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.RpcHisServiceName;
import eh.op.dao.RpcHisServiceNameDAO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Created by houxr on 2016/5/30.
 */
public class HisServiceConfigOpService {
    private static final Log logger = LogFactory.getLog(HisServiceConfigOpService.class);

    /**
     * 根据organId查询HisServiceConfig
     *
     * @param organId
     * @return
     */
    @RpcService
    public HisServiceConfig getByOrganId(Integer organId) {
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        if (null == organId) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is null!");
        }else {
            HisServiceConfig hisServiceConfig = hisServiceConfigDAO.getByOrganId(organId);
            return hisServiceConfig;
        }
    }

    /**
     * His Config维护
     *
     * @param hisServiceConfig
     * @return
     */
    @RpcService
    public HisServiceConfig addOrUpdateHisServiceConfig(HisServiceConfig hisServiceConfig) {
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        return hisServiceConfigDAO.addOrUpdateHisServiceConfig(hisServiceConfig);
    }

    /**
     * 获取所有RpcHisServiceName
     *
     * @param
     * @return
     */
    @RpcService
    public List<RpcHisServiceName> findAllRpcHisServiceName() {
        RpcHisServiceNameDAO rdao = DAOFactory.getDAO(RpcHisServiceNameDAO.class);
        return rdao.findAllRpcServiceName();
    }


}
