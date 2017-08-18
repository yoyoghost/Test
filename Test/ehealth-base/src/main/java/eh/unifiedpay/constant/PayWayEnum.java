package eh.unifiedpay.constant;

/**
 * Created by IntelliJ IDEA.
 * Description: payWay in base ,payWay desc ,payWay in platform ,service ,payType ,payType desc
 * User: xiangyf
 * Date: 2017-04-24 11:18.
 */
public enum PayWayEnum {
    UNKNOW("-1", "未知", "", null, "未知"),

    //支付宝支付
    ALIPAY_WAP("07", "支付宝wap支付", "WAP", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_WEB("08", "支付宝web支付", "WEB",  PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_JSAPI("09", "支付宝JSAPI支付", "JSAPI", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_WITHHOLD("31", "支付宝代扣支付", "", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_QR_CODE("32", "支付宝扫码（二维码）支付", "QR", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_AUTH_CODE("33", "支付宝条码、声波支付", "AUTH", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_APP("34", "支付宝APP支付", "APP", PayServiceConstant.ALIPAY, "支付宝"),

    //微信支付
    WEIXIN_WAP("40", "微信wap支付", "JSAPI",  PayServiceConstant.WXPAY, "微信"),
    WEIXIN_APP("41", "微信app支付", "APP",  PayServiceConstant.WXPAY, "微信"),
    WEIXIN_WEB("42", "微信web支付", "WEB", PayServiceConstant.WXPAY, "微信"),
    WEIXIN_QR_CODE("43", "微信扫码支付", "QR", PayServiceConstant.WXPAY, "微信"),
    WEIXIN_AUTH_CODE("44", "微信条码支付", "AUTH",PayServiceConstant.WXPAY, "微信"),

    //招商银行一网通支付
    CMB_WAP("50", "一网通wap支付", "WAP",  PayServiceConstant.CMBPAY, "一网通"),

    //其他支付
    VIRTUAL_ACCOUNT("97", "虚拟账户", "", null, "其他"),
    CITIZEN_CARD("98", "市民卡支付", "", null, "其他"),
    POS("99", "POS消费", "",  null, "银联");

    /**
     * 对应支付平台pay_way字段值
     */
    private String code;
    private String name;

    private String payWay;

    private Integer payType;

    private String payTypeName;

    /**
     * @param code
     * @param name
     * @param payWay
     * @param payType
     * @param payTypeName
     */
    PayWayEnum(String code, String name, String payWay, Integer payType, String payTypeName){
        this.code = code;
        this.name = name;
        this.payWay = payWay;
        this.payType = payType;
        this.payTypeName = payTypeName;
    }

    public static PayWayEnum fromCode(String code){
        if(code==null || code.trim().equals("")){
            return null;
        }
        for(PayWayEnum e : PayWayEnum.values()){
            if(e.getCode().equalsIgnoreCase(code)){
                return e;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPayWay() {
        return payWay;
    }

    public Integer getPayType() {
        return payType;
    }

    public String getPayTypeName() {
        return payTypeName;
    }
}
