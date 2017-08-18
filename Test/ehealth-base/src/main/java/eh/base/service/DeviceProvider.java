package eh.base.service;

import ctd.mvc.controller.util.ClientProvider;
import ctd.persistence.DAOFactory;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DeviceDAO;
import eh.entity.base.Device;

import java.util.Map;

public class DeviceProvider implements ClientProvider<Device>{

    @RpcService
    @Override
    public Device join(Map<String, Object> client) {
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);
        return deviceDAO.reportDevice(BeanUtils.map(client, Device.class));
    }

    @RpcService
    @Override
    public void leave(Integer clientId) {
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);
        deviceDAO.offLine(clientId);
    }

    @RpcService
    @Override
    public void update(Map<String, Object> client) {
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);
        Integer clientId = (Integer) client.get("id");
        String userId = (String) client.get("userId");
        Integer urt = (Integer) client.get("urt");
        String accessToken = (String) client.get("accesstoken");
        deviceDAO.updateDeviceByUserInfo(clientId, userId, urt, accessToken);
    }

    @RpcService
    @Override
    public Device exists(Integer clientId) {
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);
        Device device = deviceDAO.get(clientId);
        return device;
    }
}
