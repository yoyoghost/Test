package eh.wxpay.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.his.service.AppointTodayBillService;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Order;
import eh.entity.bus.pay.SimpleBusObject;
import eh.wxpay.bus.service.support.AbstractBusPayService;
import eh.wxpay.bus.service.support.BusPayService;
import eh.wxpay.constant.PayConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@Scope("prototype")
public class AppointcloudBusPayService extends AbstractBusPayService implements BusPayService {
    private static final Logger log = LoggerFactory.getLogger(AppointcloudBusPayService.class);
    private AppointRecordDAO appointRecordCloudDAO;
    private static AppointTodayBillService appointTodayBillService;

    public AppointcloudBusPayService() {
        appointRecordCloudDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);
    }

    @Override
    public void doCancelForUnPayOrder(Date deadTime) {

    }

    @Override
    public SimpleBusObject getSimpleBusObject(Integer busId) {
        List<AppointRecord> appointRecords = appointRecordCloudDAO.findByTelClinicId(appointRecordCloudDAO.getByAppointRecordId(busId).getTelClinicId());
        AppointRecord appointRecord = appointRecords.get(0);
        SimpleBusObject simpleBusObject = new SimpleBusObject();
        simpleBusObject.setBusId(busId);
        simpleBusObject.setPrice(appointRecord.getClinicPrice());
        simpleBusObject.setActualPrice(appointRecord.getActualPrice() == null ? appointRecord.getClinicPrice() : appointRecord.getActualPrice());
        simpleBusObject.setCouponId(appointRecord.getCouponId());
        simpleBusObject.setCouponName(appointRecord.getCouponName());
        simpleBusObject.setMpiId(appointRecord.getMpiid());
        simpleBusObject.setOrganId(appointRecord.getOrganId());
        simpleBusObject.setOutTradeNo(appointRecord.getOutTradeNo());
        simpleBusObject.setPayFlag(appointRecord.getPayFlag());
        simpleBusObject.setBusObject(appointRecord);
        return simpleBusObject;
    }

    @Override
    public void handleCoupon(Integer busId, Integer couponId) {

    }

    @Override
    public void handleBusWhenNoNeedPay(Integer busId, String outTradeNo,Map<String,Object> map) {
        List<AppointRecord> appointRecords = appointRecordCloudDAO.findByTelClinicId(appointRecordCloudDAO.getByAppointRecordId(busId).getTelClinicId());
        for (AppointRecord p : appointRecords) {
            p.setPayFlag(PayConstant.PAY_FLAG_PAY_SUCCESS);
            p.setAppointStatus(0);
            p.setOutTradeNo(outTradeNo);
            appointRecordCloudDAO.update(p);
            appointTodayBillService.settleRegBill(p);
        }
    }

    @Override
    public void onOrder(Order order) {
        List<AppointRecord> appointRecords = appointRecordCloudDAO.findByTelClinicId(appointRecordCloudDAO.getByAppointRecordId(order.getBusId()).getTelClinicId());
        for (AppointRecord p : appointRecords) {
            p.setPayOrganId(order.getPayOrganId());
            p.setOutTradeNo(order.getOutTradeNo());
            p.setPayWay(order.getPayway());
            appointRecordCloudDAO.update(p);
        }
    }

}
