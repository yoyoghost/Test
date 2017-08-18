package eh.wxpay.bus.service;

import com.alibaba.fastjson.JSONObject;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.bus.service.consult.RequestConsultService;
import eh.coupon.constant.CouponConstant;
import eh.entity.bus.Consult;
import eh.entity.bus.Order;
import eh.entity.bus.pay.ConfirmOrder;
import eh.entity.bus.pay.SimpleBusObject;
import eh.entity.coupon.CouponParam;
import eh.utils.DateConversion;
import eh.utils.ValidateUtil;
import eh.wxpay.bus.service.support.AbstractBusPayService;
import eh.wxpay.bus.service.support.BusPayService;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Component
@Scope("prototype")
public class ConsultBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(ConsultBusPayService.class);
    private ConsultDAO consultDao;

    public ConsultBusPayService() {
        consultDao = DAOFactory.getDAO(ConsultDAO.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {
        Date bf25Hour = DateConversion.getDateBFtHour(deadTime, 1);
        consultDao.cancelOverTimeNoPayOrder(bf25Hour, deadTime);
    }

    @Override
    public ConfirmOrder obtainConfirmOrder(String busType, Integer busId, Map<String, String> extInfo) {
        ConfirmOrder confirmOrder = super.obtainConfirmOrder(busType, busId, extInfo);
        Consult consult = consultDao.getById(busId);
        try {
            confirmOrder.setBusTypeName(DictionaryController.instance().get("eh.bus.dictionary.RequestMode").getText(consult.getRequestMode()));
        } catch (ControllerException e) {
            log.error("obtainConfirmOrder error when set BusTypeName with busType[{}], busId[{}], errorMessge[{}], stackTrace[{}]", busType, busId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return confirmOrder;
    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        Consult consult = consultDao.getById(busId);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(consult.getConsultCost());
        simpleBusObject.setActualPrice(consult.getActualPrice() == null ? consult.getConsultCost() : consult.getActualPrice());
        simpleBusObject.setCouponId(consult.getCouponId());
        simpleBusObject.setCouponName(consult.getCouponName());
        simpleBusObject.setMpiId(consult.getRequestMpi());
        simpleBusObject.setOrganId(consult.getConsultOrgan());
        simpleBusObject.setOutTradeNo(consult.getOutTradeNo());
        simpleBusObject.setPayFlag(consult.getPayflag());
        simpleBusObject.setBusObject(consult);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {
        Consult consult = consultDao.getById(busId);
        if (consult.getCouponId() == null) {
            CouponParam couponParam = new CouponParam();
            couponParam.setCouponId(couponId);
            couponParam.setOrderAmount(BigDecimal.valueOf(consult.getConsultCost()));
            couponParam.setBusType(CouponConstant.COUPON_BUSTYPE_CONSULT);
//                    couponParam.setSubType((consult.getRequestMode() == ConsultConstant.CONSULT_TYPE_GRAPHIC
//                            || consult.getRequestMode() == ConsultConstant.CONSULT_TYPE_RECIPE
//                            || consult.getRequestMode() == ConsultConstant.CONSULT_TYPE_PROFESSOR) ? CouponConstant.COUPON_SUBTYPE_CONSULT_ONLINE : CouponConstant.COUPON_SUBTYPE_CONSULT_APPOINT);
            if (consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_RECIPE)) {
                couponParam.setSubType(CouponConstant.COUPON_SUBTYPE_CONSULT_RECIPE);
            } else if (consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_PROFESSOR)) {
                couponParam.setSubType(CouponConstant.COUPON_SUBTYPE_CONSULT_PROFESSOR);
            } else if (consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_GRAPHIC)) {
                couponParam.setSubType(CouponConstant.COUPON_SUBTYPE_CONSULT_ONLINE);
            } else {
                couponParam.setSubType(CouponConstant.COUPON_SUBTYPE_CONSULT_APPOINT);
            }
            consult.setCouponId(couponId);
            fetchAndLockCoupon(busId, this.getClass().getSimpleName(), couponId, consult, couponParam);
            consultDao.update(consult);
        }
    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        Consult consult = consultDao.getById(busId);
        consult.setPayflag(PayConstant.PAY_FLAG_PAY_SUCCESS);
        consult.setConsultStatus(0);
        consult.setOutTradeNo(outTradeNo);
        if (ConsultConstant.CONSULT_TYPE_PROFESSOR == consult.getRequestMode() ||
                ConsultConstant.CONSULT_TYPE_RECIPE == consult.getRequestMode() ||
                ConsultConstant.CONSULT_TYPE_GRAPHIC == consult.getRequestMode()) {
            if (ValidateUtil.isTrue(consult.getTeams())) {
                consult.setStatus(1);
            } else {
                consult.setStatus(3);
            }
        } else {
            consult.setStatus(2);
        }
        consultDao.update(consult);
        RequestConsultService requestConsultService = AppContextHolder.getBean("requestConsultService", RequestConsultService.class);
        requestConsultService.doAfterPaySuccess(busId);
    }

    @Override
    public void onOrder(Order order) {
        Consult consult = consultDao.getById(order.getBusId());
        consult.setPayOrganId(order.getPayOrganId());
        consult.setOutTradeNo(order.getOutTradeNo());
        consult.setPayWay(order.getPayway());
        consultDao.update(consult);
    }
}
