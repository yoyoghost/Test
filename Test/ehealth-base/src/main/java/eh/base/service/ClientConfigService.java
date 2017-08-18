package eh.base.service;

import com.alibaba.druid.util.StringUtils;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.ClientConfigDAO;
import eh.entity.base.ClientConfig;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-18 14:33
 **/
public class ClientConfigService {


    @RpcService
    public QueryResult<ClientConfig> queryByType(Integer type, int start, int limit) {
        return DAOFactory.getDAO(ClientConfigDAO.class).queryByType(type, start, limit);
    }

    @RpcService
    public ClientConfig getById(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is require");
        }
        return DAOFactory.getDAO(ClientConfigDAO.class).get(id);
    }


    @RpcService
    public ClientConfig saveOrUpdateClient(ClientConfig config) {
        if (config == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "ClientConfig is require");
        }
        Integer type = config.getType();
        String typeText = null;
        if (type == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "ClientConfig.type is require");
        }
        try {
            typeText = DictionaryController.instance().get("eh.base.dictionary.ClientConfigType").getText(type);
        } catch (ControllerException e) {
            throw new DAOException(DAOException.VALUE_NEEDED, "ClientConfig.type is not exist");
        }
        if (StringUtils.isEmpty(typeText)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "ClientConfig.type is not exist");
        }
        Integer clientId = config.getClientId();
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "ClientConfig.clientId is require");
        }
        ClientConfigDAO clientConfigDAO = DAOFactory.getDAO(ClientConfigDAO.class);
        ClientConfig old = clientConfigDAO.getByTypeAndClientId(type, clientId);
        if (old == null) {//create
            config.setId(null);
            config = clientConfigDAO.save(config);
            BusActionLogService.recordBusinessLog("客户端管理", config.getId() + "", "ClientConfig", "新增客户端【" + config.getClientName() + "】，类型为：" + typeText);
        } else {//update
            config.setId(old.getId());
            config.setCreateTime(old.getCreateTime());
            config = clientConfigDAO.update(config);
            BusActionLogService.recordBusinessLog("客户端管理", config.getId() + "", "ClientConfig", "更新客户端【" + config.getClientName() + "】，类型为：" + typeText);
        }
        return config;

    }

    @RpcService
    public void deleteClient(Integer configId) {
        if (configId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configId is require");
        }
        ClientConfigDAO clientConfigDAO = DAOFactory.getDAO(ClientConfigDAO.class);
        ClientConfig old = clientConfigDAO.get(configId);
        if (old == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configId is not exist");
        }
        BusActionLogService.recordBusinessLog("客户端管理", old.getId() + "", "ClientConfig", "更新客户端【" + old.getClientName() + "】" + configId);
        clientConfigDAO.remove(configId);
    }

    @RpcService
    public List<ClientConfig> findAllClientByType(){
        return DAOFactory.getDAO(ClientConfigDAO.class).findAllClientByType();
    }


}
