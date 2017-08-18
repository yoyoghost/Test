package eh.op.tonglihr;

import java.math.BigDecimal;
import java.util.List;

/**
 * 对接德科医生提现详情信息
 * Created by houxr on 2016/10/15.
 */
public class WithdrawInfo {

    private String payment_batch_id;//发款批次 数字:333,
    private String funding_bank_code;//转款的开户行[企业用于向德科转款的开户行名称] 中信银行
    private String funding_account_no;//转款的账号[企业用于向德科转款的账号] 财务打款给德科的 公司中信账号
    private BigDecimal funding_amount;//转款金额[数字,以元为单位,可有2位小数] 100.13
    private String funding_date;//转款日期 字符格式：YYYY-MM-DD, 2016-10-08
    private Integer payment_count;//发款总笔数 数字 2
    private Integer success_count;//发款成功总笔数 数字 2
    private BigDecimal success_total;//发款成功总金额 [数字,以元为单位,可有2位小数] 100.13
    private Integer failure_count;//发款失败总笔数 数字 2
    private BigDecimal failure_total;//发款失败总金额 [数字,以元为单位,可有2位小数] 100.13
    private BigDecimal payment_total;//发款总金额[数字,以元为单位,可有2位小数] 100.13
    private BigDecimal processing_fee_total;//发款总笔数 数字 2
    private List<TongliPaymentDetail> payment_detail;//发款明细
    private List<TongliPaymentDetail> payment_details;//德科打款结果明细
    private String reply_url;//发款结果通知的url https://api.ngarihealth.com/payment
    private String payment_status;//德科返回提现结果

    public String getPayment_batch_id() {
        return payment_batch_id;
    }

    public void setPayment_batch_id(String payment_batch_id) {
        this.payment_batch_id = payment_batch_id;
    }

    public String getFunding_bank_code() {
        return funding_bank_code;
    }

    public void setFunding_bank_code(String funding_bank_code) {
        this.funding_bank_code = funding_bank_code;
    }

    public String getFunding_account_no() {
        return funding_account_no;
    }

    public void setFunding_account_no(String funding_account_no) {
        this.funding_account_no = funding_account_no;
    }

    public BigDecimal getFunding_amount() {
        return funding_amount;
    }

    public void setFunding_amount(BigDecimal funding_amount) {
        funding_amount = funding_amount.setScale(2, 2);
        this.funding_amount = funding_amount;
    }

    public String getFunding_date() {
        return funding_date;
    }

    public void setFunding_date(String funding_date) {
        this.funding_date = funding_date;
    }

    public Integer getPayment_count() {
        return payment_count;
    }

    public void setPayment_count(Integer payment_count) {
        this.payment_count = payment_count;
    }

    public Integer getSuccess_count() {
        return success_count;
    }

    public void setSuccess_count(Integer success_count) {
        this.success_count = success_count;
    }

    public BigDecimal getSuccess_total() {
        return success_total;
    }

    public void setSuccess_total(BigDecimal success_total) {
        success_total = success_total.setScale(2, 2);
        this.success_total = success_total;
    }

    public Integer getFailure_count() {
        return failure_count;
    }

    public void setFailure_count(Integer failure_count) {
        this.failure_count = failure_count;
    }

    public BigDecimal getFailure_total() {
        return failure_total;
    }

    public void setFailure_total(BigDecimal failure_total) {
        failure_total = failure_total.setScale(2, 2);
        this.failure_total = failure_total;
    }

    public BigDecimal getProcessing_fee_total() {
        return processing_fee_total;
    }

    public void setProcessing_fee_total(BigDecimal processing_fee_total) {
        this.processing_fee_total = processing_fee_total;
    }

    public BigDecimal getPayment_total() {
        return payment_total;
    }

    public void setPayment_total(BigDecimal payment_total) {
        payment_total = payment_total.setScale(2, 2);
        this.payment_total = payment_total;
    }

    public List<TongliPaymentDetail> getPayment_details() {
        return payment_details;
    }

    public void setPayment_details(List<TongliPaymentDetail> payment_details) {
        this.payment_details = payment_details;
    }

    public List<TongliPaymentDetail> getPayment_detail() {
        return payment_detail;
    }

    public void setPayment_detail(List<TongliPaymentDetail> payment_detail) {
        this.payment_detail = payment_detail;
    }

    public String getReply_url() {
        return reply_url;
    }

    public void setReply_url(String reply_url) {
        this.reply_url = reply_url;
    }

    public String getPayment_status() {
        return payment_status;
    }

    public void setPayment_status(String payment_status) {
        this.payment_status = payment_status;
    }
}


