package eh.wxpay.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.bus.dao.*;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeOrderDAO;
import eh.cdr.service.RecipeLogService;
import eh.cdr.service.RecipeOrderService;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.cdr.RecipeOrder;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2016/11/16 0016.
 */
public abstract class RefundService
{
    private static final Logger log =
        LoggerFactory.getLogger(RefundService.class);

    /**
     * 退款
     *
     * @param busId
     * @param busType
     * @return
     */
    public abstract Map<String, Object> refund(Integer busId, String busType);

    /**
     * 退款查询
     *
     * @param busId
     * @param busType
     * @return
     */
    public abstract Map<String, Object> refundQuery(Integer busId,
        String busType);

    /**
     * 根据退款查询结果更新业务表支付状态
     *
     * @return void
     * @author zhangjr
     * @date 2015-12-23
     */
    public void updatePayflag(String applyno, int targetPayflag)
    {
        OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);
        Order payOrder = orderDao.getByOutTradeNo(applyno);
        String busType = payOrder.getBusType();
        log.info(
            "更新业务表支付状态，传入参数商户订单号:" + applyno + ",payflag:" + targetPayflag);
        if (busType.equals(BusTypeEnum.CONSULT.getCode()))
        {// 咨询
            ConsultDAO dao = DAOFactory.getDAO(ConsultDAO.class);
            Consult c = dao.getByOutTradeNo(applyno);
            Integer payflag = c.getPayflag();
            if (payflag != null && payflag != targetPayflag)
            {
                c.setPayflag(targetPayflag);
                dao.updateSinglePayFlagByOutTradeNo(targetPayflag, applyno);
            }
        }
        else if (busType.equals(BusTypeEnum.TRANSFER.getCode()))
        {// 转诊
            TransferDAO dao = DAOFactory.getDAO(TransferDAO.class);
            Transfer t = dao.getByOutTradeNo(applyno);
            Integer payflag = t.getPayflag();
            if (payflag != null && payflag != targetPayflag)
            {
                t.setPayflag(targetPayflag);
                dao.update(t);
            }
        }
        else if (busType.equals(BusTypeEnum.RECIPE.getCode()))
        {
            // 处方
            RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
            RecipeOrder order = orderDAO.getByOutTradeNo(applyno);
            RecipeOrderService orderService = AppContextHolder.getBean(
                "eh.recipeOrderService",
                RecipeOrderService.class);
            orderService.finishOrderPay(order.getOrderCode(),
                targetPayflag,
                RecipeConstant.PAYMODE_ONLINE);
            StringBuilder memo = new StringBuilder("订单 ");
            switch (targetPayflag)
            {
                case 2:
                    memo.append("退款中");
                    break;
                case 3:
                    memo.append("退款成功");
                    break;
                case 4:
                    memo.append("退款失败");
                    break;
                default:
                    memo.append("支付 未知状态，payflag:" + targetPayflag);
                    break;
            }
            Integer _bussId = order.getOrderId();
            if (StringUtils.isNotEmpty(order.getRecipeIdList()))
            {
                List<Integer> recipeIdList =
                    JSONUtils.parse(order.getRecipeIdList(), List.class);
                if (CollectionUtils.isNotEmpty(recipeIdList))
                {
                    _bussId = recipeIdList.get(0);
                }
            }
            RecipeLogService.saveRecipeLog(_bussId,
                RecipeStatusConstant.UNKNOW,
                RecipeStatusConstant.UNKNOW,
                memo.toString());
        }
        else if (busType.equals(BusTypeEnum.SIGN.getCode()))
        {// 签约
            // 签约退款
            SignRecordDAO signRecordDAO =
                DAOFactory.getDAO(SignRecordDAO.class);
            SignRecord signRecord = signRecordDAO.getByOutTradeNo(applyno);
            if (signRecord != null)
            {
                //修改签约记录的状态信息
                signRecord.setPayFlag(targetPayflag);
                signRecordDAO.update(signRecord);
                log.info("修改签约记录的 支付标识: [outTradeNo=" + applyno + ",payFlag=" +
                    targetPayflag + "]");
            }
            else
            {
                log.info("[outTradeNo=" + applyno + "]对应的签约记录的签约记录为空不做修改操作");
            }
        }
        else if (busType.equals(BusTypeEnum.APPOINT.getCode()))
        {
            // 挂号
            AppointRecordDAO appointRecordDAO =
                DAOFactory.getDAO(AppointRecordDAO.class);
            AppointRecord appointRecord =
                appointRecordDAO.getByOutTradeNo(applyno);
            Integer busId = appointRecord.getAppointRecordId();
            switch (targetPayflag)
            {
                case 2:
//                  退款中
                    appointRecordDAO.updateAppointStatusAndPayFlagByAppointRecordId(
                        8,
                        2,
                        busId);
                    break;
                case 3:
//                  退款成功  取消业务
                    appointRecordDAO.doCancelAppoint(busId,
                        "system",
                        "system",
                        "线下退款");
                    appointRecordDAO.updateAppointStatusAndPayFlagByAppointRecordId(
                        6,
                        3,
                        busId);
                    break;
                case 4:
//                  退款失败
                    appointRecord.setAppointStatus(7);
                    appointRecord.setPayFlag(4);
                    appointRecord.setCancelResean("退款失败");
                    appointRecordDAO.update(appointRecord);
                    appointRecordDAO.sendSMS_PayRefundFail(busId);
                    break;
                default:
//                  支付 未知状态
                    break;
            }
        }
        else if (busType.equals(BusTypeEnum.APPOINTCLOUD.getCode()))
        {
            //预约云门诊
            AppointRecordDAO appointRecordDAO =
                DAOFactory.getDAO(AppointRecordDAO.class);
            Map<String, Object> attrMap = new HashMap<>();
            attrMap.put("payFlag", targetPayflag);
            switch (targetPayflag)
            {
                case 2:
                    appointRecordDAO.updateSinglePayFlagByOutTradeNo(
                        targetPayflag,
                        8,
                        applyno);
                    break;
                case 3:
                    appointRecordDAO.updateSinglePayFlagByOutTradeNo(
                        targetPayflag,
                        6,
                        applyno);
                    break;
                case 4:
                    appointRecordDAO.updateSinglePayFlagByOutTradeNo(
                        targetPayflag,
                        7,
                        applyno);
                    break;
            }
        }
        else if (busType.equals(BusTypeEnum.PREPAY.getCode()))
        {
            // 门诊缴费、住院预交
            PayBusinessDAO payBusinessDAO =
                DAOFactory.getDAO(PayBusinessDAO.class);
            PayBusiness payBusiness = payBusinessDAO.getByOutTradeNo(applyno);
            Map<String, Object> attrMap = new HashMap<>();
            attrMap.put("payFlag", targetPayflag);
            payBusinessDAO.updateSinglePayFlagByOutTradeNo(targetPayflag,
                applyno);
        }
        orderDao.updateOrderWithOutTradeNoForRefundResult(targetPayflag,
            applyno);
    }
}
