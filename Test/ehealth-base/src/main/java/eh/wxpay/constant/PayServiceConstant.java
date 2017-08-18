package eh.wxpay.constant;

import eh.wxpay.util.PayUtil;

/**
 * Created by Administrator on 2016/11/9 0009.
 */
public enum PayServiceConstant {
    WEIXIN(0, "v3.0"){
        /**
         *  统一下单url
         */
        private static final String URL_PAY_APPLY = "/easypay-base/wxpay/commonPay.do";
        /**
         * 订单查询url
         */
        private static final String URL_PAY_QUERY = "/easypay-base/wxpay/commonPayQuery.do";
        /**
         * 退款申请url
         */
        private static final String URL_REFUND_APPLY = "/easypay-base/wxpay/refund.do";
        /**
         * 退款查询 url
         */
        private static final String URL_REFUND_QUERY = "/easypay-base/wxpay/refundQuery.do";
        /**
         * 微信统一下单支付  service
         */
        private static final String SERVICE_COMMON_PAY = "wx.commonpay";

        /**
         * 微信订单查询 service
         */
        private static final String SERVICE_COMMON_PAY_QUERY = "wx.payquery";

        /**
         * 微信退款申请 service
         */
        private static final String SERVICE_REFUND = "wx.refund";

        /**
         * 微信退款查询 service
         */
        private static final String SERVICE_REFUND_QUERY = "wx.refundquery";

        @Override
        public String getPayApplyServiceName() {
            return SERVICE_COMMON_PAY;
        }

        @Override
        public String getPayApplyUrl() {
            return PayUtil.getDomain() + URL_PAY_APPLY;
        }

        @Override
        public String getPayQueryServiceName() {
            return SERVICE_COMMON_PAY_QUERY;
        }

        @Override
        public String getpayQueryUrl() {
            return PayUtil.getDomain() + URL_PAY_QUERY;
        }

        @Override
        public String getRefundApplyServiceName() {
            return SERVICE_REFUND;
        }

        @Override
        public String getRefundApplyUrl() {
            return PayUtil.getDomain() + URL_REFUND_APPLY;
        }

        @Override
        public String getRefundQueryServiceName() {
            return SERVICE_REFUND_QUERY;
        }

        @Override
        public String getRefundQueryUrl() {
            return PayUtil.getDomain() + URL_REFUND_QUERY;
        }

    },
    ALIPAY(1, "3.0"){

        /**
         * 支付宝业务通用
         */
        private static final String URL_COMMON = "/easypay-base/pay/gateway.do";
        /**
         * 统一下单支付  service
         */
        private static final String SERVICE_COMMON_PAY = "alipay.trade.precreate";

        /**
         * 支付宝订单查询 service， 退款订单查询也是同一个
         */
        private static final String SERVICE_COMMON_PAY_QUERY = "alipay.acquire.query";

        /**
         * 微信退款申请 service
         */
        private static final String SERVICE_REFUND = "alipay.trade.refund";

        @Override
        public String getPayApplyServiceName() {
            return SERVICE_COMMON_PAY;
        }

        @Override
        public String getPayApplyUrl() {
            return PayUtil.getDomain() + URL_COMMON;
        }

        @Override
        public String getPayQueryServiceName() {
            return SERVICE_COMMON_PAY_QUERY;
        }

        @Override
        public String getpayQueryUrl() {
            return PayUtil.getDomain() + URL_COMMON;
        }

        @Override
        public String getRefundApplyServiceName() {
            return SERVICE_REFUND;
        }

        @Override
        public String getRefundApplyUrl() {
            return PayUtil.getDomain() + URL_COMMON;
        }

        @Override
        public String getRefundQueryServiceName() {
            return SERVICE_COMMON_PAY_QUERY;
        }

        @Override
        public String getRefundQueryUrl() {
            return PayUtil.getDomain() + URL_COMMON;
        }

    };

    private int id;
    private String version;

    PayServiceConstant(int id, String version){
        this.id = id;
        this.version = version;
    }

    public int getId(){
        return this.id;
    }

    public String getVersion(){
        return this.version;
    }

    public abstract String getPayApplyServiceName();

    public abstract String getPayApplyUrl();

    public abstract String getPayQueryServiceName();

    public abstract String getpayQueryUrl();

    public abstract String getRefundApplyServiceName();

    public abstract String getRefundApplyUrl();

    public abstract String getRefundQueryServiceName();

    public abstract String getRefundQueryUrl();

}
