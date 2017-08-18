package eh.op.service;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.OrderDao;
import eh.bus.his.service.AppointTodayBillService;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Order;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.his.PaymentResultRequest;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.wxpay.service.NgariRefundService;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jianghc
 * @create 2017-07-20 13:47
 **/
public class OrderOpService {
    @RpcService
    public Map<String, Object> getOrderBill(Integer type, String code) {
        if (type == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "type is require");
        }
        if (StringUtils.isEmpty(code)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "code is require");
        }
        OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);
        Order order = null;
        switch (type) {
            case 0:
                order = orderDao.getByOutTradeNo(code);
                break;
            case 1:
                order = orderDao.getByTradeNo(code);
                break;
            default:
                return null;
        }
        if (order == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<String, Object>();
        BeanUtils.map(order, map);
        String busType = order.getBusType();
        BusTypeEnum bt = BusTypeEnum.fromCode(busType);
        map.put("busTypeName", bt.getName());
        AppointTodayBillService appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);
        PaymentResultRequest resultRequest = new PaymentResultRequest();
        resultRequest.setBusinessID(order.getBusId() + "");
        resultRequest.setBusinessType(bt.getId());
        try {
            HisResponse response = appointTodayBillService.queryPaymentResult(resultRequest);
            if(response!=null&&response.getMsgCode().equals("200")){
                map.put("hisStatus","已支付");
            }else {
                map.put("hisStatus","未支付");
            }
        }catch (Exception e){
            throw new DAOException("获取his状态失败");
        }

        switch (bt){
            case APPOINT:
            case APPOINTPAY:
                AppointRecord appointRecord = DAOFactory.getDAO(AppointRecordDAO.class).getByAppointRecordId(order.getBusId());
                if (appointRecord!=null){
                    map.put("patientName",appointRecord.getPatientName());
                }
                break;
            default:
        }
        return map;
    }

    @RpcService
    public void refundOrderBill(String outTradeNo){
        OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);
        Order order = orderDao.getByOutTradeNo(outTradeNo);
        NgariRefundService refundService = AppContextHolder.getBean("ngariRefundService", NgariRefundService.class);
        refundService.refund(order.getBusId(),order.getBusType());
        BusActionLogService.recordBusinessLog("单边账退款",order.getId()+"","Order","商户订号【"+outTradeNo+"】,退款："+order.getPrice());
    }
}
