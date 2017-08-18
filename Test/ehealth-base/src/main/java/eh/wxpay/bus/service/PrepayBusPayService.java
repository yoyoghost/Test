package eh.wxpay.bus.service;

import ctd.persistence.DAOFactory;
import eh.bus.dao.PayBusinessDAO;
import eh.entity.bus.Order;
import eh.entity.bus.PayBusiness;
import eh.entity.bus.pay.SimpleBusObject;
import eh.wxpay.bus.service.support.AbstractBusPayService;
import eh.wxpay.bus.service.support.BusPayService;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;


@Component
@Scope("prototype")
public class PrepayBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(PrepayBusPayService.class);
    private static PayBusinessDAO payBusinessDAO;

    public PrepayBusPayService() {
        payBusinessDAO = DAOFactory.getDAO(PayBusinessDAO.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {

    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        PayBusiness payBusiness = payBusinessDAO.getById(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(payBusiness.getTotalFee());
        simpleBusObject.setActualPrice(payBusiness.getTotalFee());
        simpleBusObject.setMpiId(payBusiness.getMPIID());
        simpleBusObject.setOrganId(payBusiness.getOrganId());
        simpleBusObject.setOutTradeNo(payBusiness.getOutTradeNo());
        simpleBusObject.setPayFlag(payBusiness.getPayflag());
        simpleBusObject.setBusObject(payBusiness);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {

    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        PayBusiness payBusiness = payBusinessDAO.getById(busId);
        payBusiness.setPayflag(PayConstant.PAY_FLAG_PAY_SUCCESS);
        payBusiness.setOutTradeNo(outTradeNo);
        payBusinessDAO.update(payBusiness);
    }

    @Override
    public void onOrder(Order order) {
        PayBusiness payBusiness = payBusinessDAO.getById(order.getBusId());
        payBusiness.setPayOrganId(order.getPayOrganId());
        payBusiness.setOutTradeNo(order.getOutTradeNo());
        payBusiness.setPayWay(order.getPayway());
        payBusiness.setUpdateTime(new Date());
        payBusinessDAO.update(payBusiness);

    }
}
