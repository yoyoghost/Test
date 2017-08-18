package eh.wxpay.constant;

/**
 * Created by Administrator on 2016/10/11 0011.
 *
 * 07 支付宝wap支付，
 * 08 支付宝web支付
 * 31 支付宝代扣支付
 * 32 支付宝扫一扫支付
 * 33 扫描支付宝条码支付
 * 34 支付宝APP支付
 * 40 微信wap支付
 * 41 微信app支付
 * 97 虚拟账户
 * 98 市民卡支付
 * 99 POS消费
 */
public enum PayWayEnum {
    /*UNKNOW("-1", "未知", "", "", null, "未知"),

    //支付宝支付
    ALIPAY_WAP("07", "支付宝wap支付", "WAP", "", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_WEB("08", "支付宝web支付", "WEB", "", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_JSAPI("09", "支付宝JSAPI支付", "JSAPI", "", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_WITHHOLD("31", "支付宝代扣支付", "", "", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_QR_CODE("32", "支付宝扫码（二维码）支付", "QR", "", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_AUTH_CODE("33", "支付宝条码、声波支付", "AUTH", "", PayServiceConstant.ALIPAY, "支付宝"),
    ALIPAY_APP("34", "支付宝APP支付", "APP", "", PayServiceConstant.ALIPAY, "支付宝"),

    //微信支付
    WEIXIN_WAP("40", "微信wap支付", "JSAPI", "WEB", PayServiceConstant.WEIXIN, "微信"),
    WEIXIN_APP("41", "微信app支付", "APP", "APP", PayServiceConstant.WEIXIN, "微信"),
    WEIXIN_WEB("41", "微信web支付", "WEB", "", PayServiceConstant.WEIXIN, "微信"),
    WEIXIN_QR_CODE("41", "微信扫码支付", "QR", "", PayServiceConstant.WEIXIN, "微信"),
    WEIXIN_AUTH_CODE("41", "微信条码支付", "AUTH", "", PayServiceConstant.WEIXIN, "微信"),

    //招商银行一网通支付
    CMB_WAP("50", "一网通wap支付", "WAP", "", null, "一网通"),

    //其他支付
    VIRTUAL_ACCOUNT("97", "虚拟账户", "", "", null, "其他"),
    CITIZEN_CARD("98", "市民卡支付", "", "", null, "其他"),
    POS("99", "POS消费", "", "", null, "银联");

    *//**
     * 对应支付平台pay_way字段值
     *//*
    private String code;
    private String name;
    *//**
     * 交易类型，目前已知两种（用于微信支付）：JSAPI、APP
     *//*
    private String tradeType;
    *//**
     * 终端设备号（门店或收银设备ID），注意：PC网页或公众号内支付请传"WEB"
     *//*
    private String deviceInfo;

    private PayServiceConstant payServiceConstant;
    private String payTypeName;

    PayWayEnum(String code, String name, String tradeType, String deviceInfo, PayServiceConstant payServiceConstant, String payTypeName){
        this.code = code;
        this.name = name;
        this.tradeType = tradeType;
        this.deviceInfo = deviceInfo;
        this.payServiceConstant = payServiceConstant;
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

    public String getDeviceInfo(){
        return this.deviceInfo;
    }

    public String getTradeType(){
        return this.tradeType;
    }

    public String getCode(){
        return this.code;
    }

    public PayServiceConstant getPayServiceConstant() {
        return this.payServiceConstant;
    }

    public String getPayTypeName(){
        return payTypeName;
    }*/

}
