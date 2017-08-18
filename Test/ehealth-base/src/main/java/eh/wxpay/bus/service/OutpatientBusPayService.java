package eh.wxpay.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.dao.OutpatientDAO;
import eh.bus.service.payment.QueryOutpatient;
import eh.entity.bus.Order;
import eh.entity.bus.Outpatient;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.SimpleBusObject;
import eh.utils.LocalStringUtil;
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
public class OutpatientBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(OutpatientBusPayService.class);
    private static OutpatientDAO outpatientDAO;

    public OutpatientBusPayService() {
        outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {

    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        Outpatient outpatient = outpatientDAO.getById(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(outpatient.getTotalFee());
        simpleBusObject.setActualPrice(outpatient.getTotalFee());
        simpleBusObject.setMpiId(outpatient.getMpiId());
        simpleBusObject.setOrganId(outpatient.getOrganId());
        simpleBusObject.setOutTradeNo(outpatient.getOutTradeNo());
        simpleBusObject.setPayFlag(outpatient.getPayflag());
        simpleBusObject.setBusObject(outpatient);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {

    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        Outpatient outpatient = outpatientDAO.getById(busId);
        outpatient.setPayflag(PayConstant.PAY_FLAG_PAY_SUCCESS);
        outpatient.setOutTradeNo(outTradeNo);
        outpatientDAO.update(outpatient);
        //如果是0元，直接調用結算
        int busIdValue = Integer.valueOf(busId);
        QueryOutpatient queryOutpatient = AppContextHolder.getBean("queryOutpatient", QueryOutpatient.class);
        Boolean falg= queryOutpatient.settlementOutPatientToHis(outpatient);
        //返回结算标识
        if(falg==true){
            map.put("saFlag", 1);
        }else{
            map.put("saFlag", 0);
        }
    }

    @Override
    public void onOrder(Order order) {
        Outpatient outpatient = outpatientDAO.getById(order.getBusId());
        outpatient.setPayOrganId(order.getPayOrganId());
        outpatient.setOutTradeNo(order.getOutTradeNo());
        outpatient.setPayWay(order.getPayway());
        outpatient.setUpdateTime(new Date());
        outpatientDAO.update(outpatient);
    }
}
