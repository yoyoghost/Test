package eh.op.tonglihr;

/**
 * tonglihr 德科注册医生 数据对接
 * Created by houxr on 2016/8/26.
 */
public class UserInfo {

    private String name;
    private String id_number;
    private String mobile_number;
    private String province;
    private String city;
    private String address;
    private String alipay_id;
    private String card_no; //银行卡号
    private String bank_code;// 开户银行
    private String card_name;//持卡人姓名
    private String bank_branch;// 开户网点
    private String pay_mobile;// 提现手机号
    private Integer employee_number;// 员工号对应医生内码doctorId

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId_number() {
        return id_number;
    }

    public void setId_number(String id_number) {
        this.id_number = id_number;
    }

    public String getMobile_number() {
        return mobile_number;
    }

    public void setMobile_number(String mobile_number) {
        this.mobile_number = mobile_number;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAlipay_id() {
        return alipay_id;
    }

    public void setAlipay_id(String alipay_id) {
        this.alipay_id = alipay_id;
    }

    public String getCard_no() {
        return card_no;
    }

    public void setCard_no(String card_no) {
        this.card_no = card_no;
    }

    public String getBank_code() {
        return bank_code;
    }

    public void setBank_code(String bank_code) {
        this.bank_code = bank_code;
    }

    public String getCard_name() {
        return card_name;
    }

    public void setCard_name(String card_name) {
        this.card_name = card_name;
    }

    public String getBank_branch() {
        return bank_branch;
    }

    public void setBank_branch(String bank_branch) {
        this.bank_branch = bank_branch;
    }

    public String getPay_mobile() {
        return pay_mobile;
    }

    public void setPay_mobile(String pay_mobile) {
        this.pay_mobile = pay_mobile;
    }

    public Integer getEmployee_number() {
        return employee_number;
    }

    public void setEmployee_number(Integer employee_number) {
        this.employee_number = employee_number;
    }
}

