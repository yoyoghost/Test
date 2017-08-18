package eh.wxpay.bus.service;

import ctd.persistence.DAOFactory;
import eh.bus.dao.TransferDAO;
import eh.entity.bus.Order;
import eh.entity.bus.Transfer;
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
public class TransferBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(TransferBusPayService.class);
    private static TransferDAO transferDao;

    public TransferBusPayService() {
        transferDao = DAOFactory.getDAO(TransferDAO.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {

    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        Transfer transfer = transferDao.getById(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(transfer.getTransferCost());
        simpleBusObject.setActualPrice(transfer.getTransferCost());
        simpleBusObject.setMpiId(transfer.getRequestMpi());
        simpleBusObject.setOrganId(transfer.getTargetOrgan());
        simpleBusObject.setOutTradeNo(transfer.getOutTradeNo());
        simpleBusObject.setPayFlag(transfer.getPayflag());
        simpleBusObject.setBusObject(transfer);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {

    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        Transfer transfer = transferDao.getById(busId);
        transfer.setPayflag(PayConstant.PAY_FLAG_PAY_SUCCESS);
        transfer.setTransferStatus(0);
        transfer.setOutTradeNo(outTradeNo);
        transferDao.update(transfer);
    }

    @Override
    public void onOrder(Order order) {
        Transfer transfer = transferDao.getById(order.getBusId());
        transfer.setPayOrganId(order.getPayOrganId());
        transfer.setOutTradeNo(order.getOutTradeNo());
        transfer.setPayWay(order.getPayway());
        transferDao.update(transfer);
    }
}
