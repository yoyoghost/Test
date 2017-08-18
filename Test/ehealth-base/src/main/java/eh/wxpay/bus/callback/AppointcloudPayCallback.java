package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.OrderDao;
import eh.bus.his.service.AppointTodayBillService;
import eh.bus.service.appointrecord.RequestAppointService;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.AppointmentResponse;
import eh.entity.bus.Order;
import eh.entity.bus.pay.BusTypeEnum;
import eh.unifiedpay.service.UnifiedRefundService;
import eh.wxpay.bus.callback.support.PayCallback;
import eh.wxpay.bus.callback.support.PayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 命名规则： BusTypeEnum的code值 + PayCallback 后缀
 */
@Component
@Scope("prototype")
public class AppointcloudPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(AppointcloudPayCallback.class);
    private AppointTodayBillService appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        String tradeNo = payResult.getTradeNo();
        Date paymentDate = payResult.getPaymentDate();

        AppointRecordDAO appointRecordDAOCloud = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecord> appointRecords = appointRecordDAOCloud.findByOutTradeNo(outTradeNo);
        if (appointRecords == null || appointRecords.isEmpty()) {
            OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);
            Order order = orderDao.getByTradeNo(tradeNo);
            if (order!=null && BusTypeEnum.APPOINTCLOUD.getCode().equals(order.getBusType())) {
                //支付成功的单子调用退款接口，并更新预约状态和支付状态
                UnifiedRefundService refundService = AppDomainContext.getBean("eh.unifiedRefundService", UnifiedRefundService.class);
                refundService.refundByOrder(busId, BusTypeEnum.APPOINTCLOUD.getCode(), order);
            }
            logger.error("doBusinessAfterOrderSuccess busObject[appointCloud] not exists, outTradeNo[{}]", outTradeNo);
            return false;
        }
        //不对预约单状态为取消的单子做处理，后期作为错误数据处理
        Integer payflag = appointRecords.get(0).getPayFlag();
        if (payflag != null && payflag.equals(1)) {
            logger.info("doBusinessAfterOrderSuccess payflag has been set true, outTradeNo[{}]", outTradeNo);
            return true;
        }
        if (payflag != null && payflag > 1) {
            logger.error("doBusinessAfterOrderSuccess this cloudclinic record was cancel, outTradeNo[{}]", outTradeNo);
            return false;
        }
        appointRecordDAOCloud.updatePayMessageByOutTradeNo(tradeNo, paymentDate, 1, outTradeNo);
        for (AppointRecord appointRecordCloud : appointRecords) {
            AppointmentResponse res = new AppointmentResponse();
            res.setId(appointRecordCloud.getAppointRecordId().toString());
            res.setAppointID("");
            res.setClinicArea(appointRecordCloud.getConfirmClinicAddr());
            res.setOrderNum(appointRecordCloud.getOrderNum());
            appointRecordDAOCloud.updateAppointId4TransferNottohis(res);
        }
        //云门诊 支付成功调用his结算接口
        boolean retFlag = false;
        RequestAppointService requestAppointService = AppContextHolder.getBean("requestAppointService", RequestAppointService.class);
        AppointRecord appointRecord = requestAppointService.isCloudClinicAppointNeedToHis(appointRecords);
        if(appointRecord != null){
        	appointRecord.setPayFlag(1); // updatePayMessageByOutTradeNo已置payflag为1，为不在此查数据库
        	appointRecord.setTradeNo(tradeNo); // updatePayMessageByOutTradeNo已置，为不在此查数据库
        	appointRecord.setPaymentDate(paymentDate); // updatePayMessageByOutTradeNo，为不在此查数据库
            Map<String, Object> resMap = appointTodayBillService.settleRegBill(appointRecord);
            logger.info("updateBusinessPayStatus.APPOINTCLOUD,msg=", resMap);
            if (resMap != null && "success".equals(resMap.get("code"))) {
            	retFlag = true;
            }
        }
        if(retFlag){
        	 for (AppointRecord appointRecordCloud : appointRecords) {
        		 if(appointRecordCloud.getClinicObject() != null && appointRecordCloud.getClinicObject() == 1 ){ 
        			 AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        			 appointRecordCloud.setAppointStatus(5);
             		 appointRecordDAO.update(appointRecordCloud);
        		 }
        	 }
        }
        return retFlag;
    }
}
