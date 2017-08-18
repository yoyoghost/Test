package eh.wxpay.bus.callback;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.bus.dao.OutpatientDAO;
import eh.bus.service.payment.QueryOutpatient;
import eh.cdr.service.RecipeService;
import eh.entity.bus.Outpatient;
import eh.utils.ValidateUtil;
import eh.wxpay.bus.callback.support.PayCallback;
import eh.wxpay.bus.callback.support.PayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 命名规则： BusTypeEnum的code值 + PayCallback 后缀
 */
@Component
@Scope("prototype")
public class OutpatientPayCallback implements PayCallback<PayResult> {
    private static final Logger logger = LoggerFactory.getLogger(OutpatientPayCallback.class);

    @Override
    public boolean handle(PayResult payResult) throws Exception {
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        String tradeNo = payResult.getTradeNo();
        Date paymentDate = payResult.getPaymentDate();

        OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
        Outpatient outpatient = outpatientDAO.getById(busId);
        if (outpatient == null) {
            logger.error("doBusinessAfterOrderSuccess busObject not exists, busId[{}]", busId);
            return false;
        }
        Integer payflag = outpatient.getPayflag();
        if (payflag != null && payflag >= 1) {//已处理
            logger.info("doBusinessAfterOrderSuccess payflag has been set true, busId[{}]", busId);
            return true;
        }
        outpatient.setPayflag(1);
        outpatient.setPaymentDate(paymentDate);
        outpatient.setTradeNo(tradeNo);
        outpatient.setOutTradeNo(outTradeNo);
        outpatientDAO.update(outpatient);
        if (ValidateUtil.notBlankString(outpatient.getRecipeIds())) {
            // 更新对应的处方单改为已支付
            //TODO 门诊缴费暂不处理
            String[] recipeIds = outpatient.getRecipeIds().split(QueryOutpatient.HIS_RECIPEID_SPLIT_CHAR);
            for (String recipeId : recipeIds) {
                RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);
//                    recipeService.finishRecipePay(Integer.valueOf(recipeId), 1, outpatient.getMpiId());
            }
        }
        QueryOutpatient queryOutpatient = AppContextHolder.getBean("queryOutpatient", QueryOutpatient.class);
        queryOutpatient.settlementOutPatientToHis(outpatient);
        return true;
    }
}
