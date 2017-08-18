package eh.wxpay.util;

import eh.wxpay.constant.WxConstant;

public class PayUtil {
	public static String organId;

	public static String domain;

	public static String notify_domain;

	private static String dabaiServiceHost;

	private static String dabaiAccessToken;

	private static String dabaiPartnerCode;

	public static String getOrganId() {
		return organId;
	}

	public static void setOrganId(String organId) {
		PayUtil.organId = organId;
	}

	public static String getDomain() {
		return domain;
	}

	public static void setDomain(String domain) {
		PayUtil.domain = domain;
	}

	public static String getNotify_domain() {
		return notify_domain;
	}

	public static void setNotify_domain(String notify_domain) {
		PayUtil.notify_domain = notify_domain;
	}

	/**
	 * 获取支付平台->云平台 支付异步通知url
	 */
	public static String getNotifyUrl() {
		return notify_domain + "/wxpay/payNotify.do";
	}

	/**
	 * 获取支付平台支付统一下单url
	 */
	public static String getWxPayUrl() {
		return domain + WxConstant.Url.WXPAY_URL;
	}
	
	/**
	 * 获取支付平台订单查询url
	 */
	public static String getWxPayQueryUrl() {
		return domain + WxConstant.Url.WXPAY_QUERY_URL;
	}
	
	/**
	 * 获取支付平台退款url
	 */
	public static String getWxRefundUrl() {
		return domain + WxConstant.Url.REFUND_URL;
	}
	
	/**
	 * 获取支付平台
	 */
	public static String getWxRefundQueryUrl(){
		return domain + WxConstant.Url.REFUND_QUERY_URL;
	}

	public static String getDabaiServiceHost() {
		return dabaiServiceHost;
	}

	public static void setDabaiServiceHost(String dabaiServiceHost) {
		PayUtil.dabaiServiceHost = dabaiServiceHost;
	}

	public static String getDabaiAccessToken() {
		return dabaiAccessToken;
	}

	public static void setDabaiAccessToken(String dabaiAccessToken) {
		PayUtil.dabaiAccessToken = dabaiAccessToken;
	}

	public static String getDabaiPartnerCode() {
		return dabaiPartnerCode;
	}

	public static void setDabaiPartnerCode(String dabaiPartnerCode) {
		PayUtil.dabaiPartnerCode = dabaiPartnerCode;
	}
}
