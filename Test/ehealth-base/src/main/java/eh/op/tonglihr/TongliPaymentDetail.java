package eh.op.tonglihr;

import java.math.BigDecimal;

/**
 * 德科批量提款给医生发款明细 已结 德科打款 成功后返回的支付信息
 * Created by houxr on 2016/10/15.
 */
public class TongliPaymentDetail {

    private String mobile_number;//医生手机号 不能重复 13812341256
    private String account_bank_code;//收款人开户行 中国建设银行 支付宝
    private String account_bank_name;//收款人账户名 张三
    private String account_bank_no;//收款人账号 8391234256 或者 支付宝账号
    private String withdraw_order_id;//提款单号 数字 1234256
    private String bank_trans_id;//银行处理结果id  11223344556678
    private BigDecimal payment_amount;//发款金额 以元为单位，可有2位小数 20.13
    private BigDecimal processing_fee;//手续费金额 以元为单位，可有2位小数 0.13
    private String detail_status;//银行处理结果
    private String payment_channel;//支付渠道:bocomm 银行卡 alipay支付宝
    private String payment_detail_time;//德科具体支付时间 2016-10-21 13:51:56
    private String payment_detail_status;//德科支付成功状态

    public String getMobile_number() {
        return mobile_number;
    }

    public void setMobile_number(String mobile_number) {
        this.mobile_number = mobile_number;
    }

    public String getAccount_bank_code() {
        return account_bank_code;
    }

    public void setAccount_bank_code(String account_bank_code) {
        this.account_bank_code = account_bank_code;
    }

    public String getAccount_bank_name() {
        return account_bank_name;
    }

    public void setAccount_bank_name(String account_bank_name) {
        this.account_bank_name = account_bank_name;
    }

    public String getAccount_bank_no() {
        return account_bank_no;
    }

    public void setAccount_bank_no(String account_bank_no) {
        this.account_bank_no = account_bank_no;
    }

    public String getWithdraw_order_id() {
        return withdraw_order_id;
    }

    public void setWithdraw_order_id(String withdraw_order_id) {
        this.withdraw_order_id = withdraw_order_id;
    }

    public BigDecimal getPayment_amount() {
        return payment_amount;
    }

    public void setPayment_amount(BigDecimal payment_amount) {
        payment_amount = payment_amount.setScale(2, 2);
        this.payment_amount = payment_amount;
    }

    public String getBank_trans_id() {
        return bank_trans_id;
    }

    public void setBank_trans_id(String bank_trans_id) {
        this.bank_trans_id = bank_trans_id;
    }

    public BigDecimal getProcessing_fee() {
        return processing_fee;
    }

    public void setProcessing_fee(BigDecimal processing_fee) {
        processing_fee = processing_fee.setScale(2, 2);
        this.processing_fee = processing_fee;
    }

    public String getDetail_status() {
        return detail_status;
    }

    public void setDetail_status(String detail_status) {
        this.detail_status = detail_status;
    }

    public String getPayment_channel() {
        return payment_channel;
    }

    public void setPayment_channel(String payment_channel) {
        this.payment_channel = payment_channel;
    }

    public String getPayment_detail_time() {
        return payment_detail_time;
    }

    public void setPayment_detail_time(String payment_detail_time) {
        this.payment_detail_time = payment_detail_time;
    }

    public String getPayment_detail_status() {
        return payment_detail_status;
    }

    public void setPayment_detail_status(String payment_detail_status) {
        this.payment_detail_status = payment_detail_status;
    }
}