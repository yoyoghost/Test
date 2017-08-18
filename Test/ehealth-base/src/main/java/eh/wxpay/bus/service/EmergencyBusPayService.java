package eh.wxpay.bus.service;

import ctd.persistence.DAOFactory;
import eh.bus.constant.EmergencyConstant;
import eh.bus.dao.EmergencyDao;
import eh.entity.bus.Emergency;
import eh.entity.bus.Order;
import eh.entity.bus.pay.SimpleBusObject;
import eh.wxpay.bus.service.support.AbstractBusPayService;
import eh.wxpay.bus.service.support.BusPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
@Scope("prototype")
public class EmergencyBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(EmergencyBusPayService.class);
    private static EmergencyDao emergencyDao;

    public EmergencyBusPayService() {
        emergencyDao = DAOFactory.getDAO(EmergencyDao.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {

    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        Emergency emergency = emergencyDao.get(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(emergency.getPrice());
        simpleBusObject.setActualPrice(emergency.getActualPrice());
        simpleBusObject.setMpiId(emergency.getRequestMpi());
        simpleBusObject.setOrganId(emergency.getOrganId());
        simpleBusObject.setBusObject(emergency);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {

    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        Emergency emergency = emergencyDao.get(busId);
        emergency.setStatus(EmergencyConstant.EMERGENCY_STATUS_PAID);
        emergency.setUpdateTime(new Date());
        emergencyDao.update(emergency);
    }

    @Override
    public void onOrder(Order order) {
    }
}
