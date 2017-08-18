package eh.wxpay.service;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.WxConfigDAO;
import eh.entity.base.WxConfig;
import eh.util.HttpHelper;
import org.apache.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class WxApiService {

	public static final Logger log = Logger.getLogger(WxApiService.class);

	/**
	 * 纳里健康appid
	 */
//	private static final String appid = "wx870abf50c6bc6da3";
	private static final String appid = "wxbc513b2a88553f93";//测试
	/**
	 * 纳里健康appsecret
	 */
//	private static final String secret = "1b99e65d11726f8057727e0e30aae103";
	private static final String secret = "8ad95fd1a8428fc9484c57384bce8c15";//测试

	/**
	 * 1、为了保密appsecrect，第三方需要一个access_token获取和刷新的中控服务器。
	 * 而其他业务逻辑服务器所使用的access_token均来自于该中控服务器，不应该各自去 刷新，否则会造成access_token覆盖而影响业务；
	 * 2、目前access_token的有效期通过返回的expire_in来传达，目前是7200秒之内的值。
	 * 中控服务器需要根据这个有效时间提前去刷新新access_token。在刷新过程中，中控服务器对
	 * 外输出的依然是老access_token，此时公众平台后台会保证在刷新短时间内，
	 * 新老access_token都可用，这保证了第三方业务的平滑过渡；
	 * 3、access_token的有效时间可能会在未来有调整，所以中控服务器不仅需要内部定时主动刷新
	 * ，还需要提供被动刷新access_token的接口，这样便于业务服务器在API调用获知access_token
	 * 已超时的情况下，可以触发access_token的刷新流程。 这里的access_token
	 * 跟获取用户信息用的access_token是不一样的，公众号可以获取 到一个网页授权特有的接口调用凭证（网页授权access_token），
	 * 通过网页授权access_token可以进行授权后接口调用，如获取用户基本信息；
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String getAccessToken() {
		WxConfigDAO dao = DAOFactory.getDAO(WxConfigDAO.class);
		WxConfig cf = dao.getByAppid(appid);// 数据库默认有配置该appid对应一条记录
		if (cf == null)
			return "";
		if (cf.getTokenExpireTime() < getTimestamp()) {
			String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
					+ appid + "&secret=" + secret + "";
			// String
			// tokenStr="{\"access_token\":\"o3oVQxYWo8qXwULtAE0XdQ6Uz4mGLrUOGNRdKF6ckf57nxBe80qD3umURt1qUcRVoKkHTgKB03_0TP_2ZvvPFQQZ-sBE7ckBWpX8dk0eaOYIXUfADAODA\",\"expires_in\":7200}";
			String tokenStr = HttpHelper.getHttpResult(url);
			HashMap<String, Object> map = JSONUtils.parse(tokenStr,
					HashMap.class);
			String token = (String) map.get("access_token");
			Integer expirein = (Integer) map.get("expires_in");
			cf.setAccessToken(token);
			cf.setTokenExpireIn(expirein);
			cf.setTokenExpireTime(getTimestamp() + expirein - 200);
			cf.setTokenModifyDate(new Date());
			dao.update(cf);
			return token;
		} else {
			return cf.getAccessToken();
		}
	}

	/**
	 * jsapi_ticket是公众号用于调用微信JS接口的临时票据。正常情况下，jsapi_ticket
	 * 的有效期为7200秒，通过access_token来获取。由于获取jsapi_ticket的api调用次数
	 * 非常有限，频繁刷新jsapi_ticket会导致api调用受限，影响自身业务，开发者必须在 自己的服务全局缓存jsapi_ticket 。
	 * 
	 * @param
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@RpcService
	public String getJsapiTicket() {
		WxConfigDAO dao = DAOFactory.getDAO(WxConfigDAO.class);
		WxConfig cf = dao.getByAppid(appid);// 数据库默认有配置该appid对应一条记录
		if (cf == null)
			return "";
		if (cf.getTicketExpireTime() < getTimestamp()) {
			String accessToken = getAccessToken();
			String url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token="
					+ accessToken + "&type=jsapi";
			String ticketStr = HttpHelper.getHttpResult(url);
			HashMap<String, Object> map = JSONUtils.parse(ticketStr,
					HashMap.class);
			String ticket = (String) map.get("ticket");
			Integer expirein = (Integer) map.get("expires_in");
			cf.setJsapiTicket(ticket);
			cf.setTicketExpireIn(expirein);
			cf.setTicketExpireTime(getTimestamp() + expirein - 200);
			cf.setTicketModifyDate(new Date());
			dao.update(cf);
			return ticket;
		} else {
			return cf.getJsapiTicket();
		}

	}

	@RpcService
	public Map<String, String> getWxConfig(String url) {

		Map<String, String> ret = new HashMap<String, String>();
		String nonce_str = create_nonce_str();
		String timestamp = create_timestamp();
		String string1;
		String signature = "";
		String jsapi_ticket = getJsapiTicket();
		// 注意这里参数名必须全部小写，且必须有序
		string1 = "jsapi_ticket=" + jsapi_ticket + "&noncestr=" + nonce_str
				+ "&timestamp=" + timestamp + "&url=" + url;
		log.info("wxConfig:"+string1);

		try {
			MessageDigest crypt = MessageDigest.getInstance("SHA-1");
			crypt.reset();
			crypt.update(string1.getBytes("UTF-8"));
			signature = byteToHex(crypt.digest());
		} catch (NoSuchAlgorithmException e) {
			log.error("getWxConfig NoSuchAlgorithmException"+e.getMessage());
		} catch (UnsupportedEncodingException e) {
			log.error("getWxConfig UnsupportedEncodingException"+e.getMessage());
		}
		ret.put("appId", appid);
		ret.put("url", url);
		ret.put("jsapi_ticket", jsapi_ticket);
		ret.put("nonceStr", nonce_str);
		ret.put("timestamp", timestamp);
		ret.put("signature", signature);

		return ret;

	}

	
	
	
	
	/**
	 * 获取开发者测试公众号token
	 * @author zhangx
	 * @date 2016-1-10 下午8:33:35
	 * @param appId
	 * @param secret
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String getDevelopAccessToken(String appId,String secret) {
		WxConfigDAO dao = DAOFactory.getDAO(WxConfigDAO.class);
		WxConfig cf = dao.getByAppid(appId);// 数据库默认有配置该appid对应一条记录
		if (cf == null)
			return "";
		if (cf.getTokenExpireTime() < getTimestamp()) {
			String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
					+ appId + "&secret=" + secret + "";
			// String
			// tokenStr="{\"access_token\":\"o3oVQxYWo8qXwULtAE0XdQ6Uz4mGLrUOGNRdKF6ckf57nxBe80qD3umURt1qUcRVoKkHTgKB03_0TP_2ZvvPFQQZ-sBE7ckBWpX8dk0eaOYIXUfADAODA\",\"expires_in\":7200}";
			String tokenStr = HttpHelper.getHttpResult(url);
			HashMap<String, Object> map = JSONUtils.parse(tokenStr,
					HashMap.class);
			String token = (String) map.get("access_token");
			Integer expirein = (Integer) map.get("expires_in");
			cf.setAccessToken(token);
			cf.setTokenExpireIn(expirein);
			cf.setTokenExpireTime(getTimestamp() + expirein - 200);
			cf.setTokenModifyDate(new Date());
			dao.update(cf);
			return token;
		} else {
			return cf.getAccessToken();
		}
	}

	/**
	 * 获取开发者测试公众号票据
	 * 
	 * @desc jsapi_ticket是公众号用于调用微信JS接口的临时票据。正常情况下，jsapi_ticket
	 * 的有效期为7200秒，通过access_token来获取。由于获取jsapi_ticket的api调用次数
	 * 非常有限，频繁刷新jsapi_ticket会导致api调用受限，影响自身业务，开发者必须在 自己的服务全局缓存jsapi_ticket 。
	 * 
	 * @param accessToken
	 * @return
	 */
	@SuppressWarnings("unchecked")
	@RpcService
	public String getDevelopJsapiTicket(String appId,String secret) {
		WxConfigDAO dao = DAOFactory.getDAO(WxConfigDAO.class);
		WxConfig cf = dao.getByAppid(appId);// 数据库默认有配置该appid对应一条记录
		if (cf == null)
			return "";
		if (cf.getTicketExpireTime() < getTimestamp()) {
			String accessToken = getDevelopAccessToken(appId,secret);
			String url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token="
					+ accessToken + "&type=jsapi";
			String ticketStr = HttpHelper.getHttpResult(url);
			HashMap<String, Object> map = JSONUtils.parse(ticketStr,
					HashMap.class);
			String ticket = (String) map.get("ticket");
			Integer expirein = (Integer) map.get("expires_in");
			cf.setJsapiTicket(ticket);
			cf.setTicketExpireIn(expirein);
			cf.setTicketExpireTime(getTimestamp() + expirein - 200);
			cf.setTicketModifyDate(new Date());
			dao.update(cf);
			return ticket;
		} else {
			return cf.getJsapiTicket();
		}

	}

	
	/**
	 * 获取开发者测试公众号配置
	 * 
	 * @author zhangx
	 * @date 2016-1-9 下午3:42:18
	 * @param appId
	 *            开发者测试的测试公众号appId
	 * @param url
	 * @return
	 */
	@RpcService
	public Map<String, String> getDevelopWxConfig(String appId,String secret, String url) {
		Map<String, String> ret = new HashMap<String, String>();
		String nonce_str = create_nonce_str();
		String timestamp = create_timestamp();
		String string1;
		String signature = "";
		String jsapi_ticket = getDevelopJsapiTicket(appId,secret);
		// 注意这里参数名必须全部小写，且必须有序
		string1 = "jsapi_ticket=" + jsapi_ticket + "&noncestr=" + nonce_str
				+ "&timestamp=" + timestamp + "&url=" + url;
		log.info("developWxConfig:"+string1);

		try {
			MessageDigest crypt = MessageDigest.getInstance("SHA-1");
			crypt.reset();
			crypt.update(string1.getBytes("UTF-8"));
			signature = byteToHex(crypt.digest());
		} catch (NoSuchAlgorithmException e) {
			log.error("getDevelopWxConfig NoSuchAlgorithmException"+e.getMessage());
		} catch (UnsupportedEncodingException e) {
			log.error("getDevelopWxConfig UnsupportedEncodingException"+e.getMessage());
		}
		ret.put("appId", appId);
		ret.put("url", url);
		ret.put("jsapi_ticket", jsapi_ticket);
		ret.put("nonceStr", nonce_str);
		ret.put("timestamp", timestamp);
		ret.put("signature", signature);

		return ret;
	}
	

	private static String byteToHex(final byte[] hash) {
		Formatter formatter = new Formatter();
		for (byte b : hash) {
			formatter.format("%02x", b);
		}
		String result = formatter.toString();
		formatter.close();
		return result;
	}

	private static String create_nonce_str() {
		return UUID.randomUUID().toString();
	}

	private static String create_timestamp() {
		return Long.toString(System.currentTimeMillis() / 1000);
	}

	private static int getTimestamp() {
		return (int) (System.currentTimeMillis() / 1000);
	}
}
