package eh.wxpay.bus.callback.support;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by Administrator on 2017/5/18 0018.
 */
public class PayResult implements Serializable{
    /**
     * 业务主键id，做业务处理时应以此字段为条件查询业务单信息
     */
    private Integer busId;
    /**
     * 支付的云平台订单号
     */
    private String outTradeNo;
    /**
     * 云平台订单号对应的第三方交易流水号
     */
    private String tradeNo;
    /**
     * 订单支付时间
     */
    private Date paymentDate;

    public String getOutTradeNo() {
        return outTradeNo;
    }

    public void setOutTradeNo(String outTradeNo) {
        this.outTradeNo = outTradeNo;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public Date getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(Date paymentDate) {
        this.paymentDate = paymentDate;
    }

    public Integer getBusId() {
        return busId;
    }

    public void setBusId(Integer busId) {
        this.busId = busId;
    }
}
