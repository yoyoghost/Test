package eh.wxpay.util;

import ctd.mvc.weixin.support.HttpStreamResponse;
import ctd.util.JSONUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class UrlPost {
	private static final Log logger = LogFactory.getLog(UrlPost.class);
	/**
	 * @function httppost请求
	 * @author zhangjr
	 * @param url
	 * @param map
	 * @date 2015-12-14
	 * @return String
	 */
	public static String getHttpPostResult(String url, Map<String, String> map,String operation) {
		logger.info(operation + "请求参数:"+ JSONUtils.toString(map));
		String result = "";
		try {
			HttpParams httpParams = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParams, 5000); // 设置连接超时为5秒
			HttpClient client = new DefaultHttpClient(httpParams); // 生成一个http客户端发送请求对象
			HttpPost httpPost = new HttpPost(url); // 设定请求方式
			List<BasicNameValuePair> pairList = new ArrayList<BasicNameValuePair>();
			for (Map.Entry<String, String> entry : map.entrySet()) {
				pairList.add(new BasicNameValuePair(entry.getKey(), entry
						.getValue()));
			}

			if (pairList != null && pairList.size() != 0) {
				// 把键值对进行编码操作并放入HttpEntity对象中
				httpPost.setEntity(new UrlEncodedFormEntity(pairList, "utf-8"));
			}
			HttpResponse httpResponse = client.execute(httpPost); // 发送请求并等待响应

			// 判断网络连接是否成功
			if (httpResponse.getStatusLine().getStatusCode() != 200) {
				logger.info("支付请求时，网络出现错误异常！");
				return "<xml><msg>网络异常</msg></xml>";
			}

			HttpEntity entity = httpResponse.getEntity(); // 获取响应里面的内容
			// 得到服务气端发回的响应的内容（都在一个字符串里面）
			result = EntityUtils.toString(entity);
			logger.info(operation+"返回结果串:" + result);
		} catch (Exception e) {
			logger.info(e.getMessage());
		}
		return result;
	}
	
	 public static HttpStreamResponse doGetAsInputStream(String url) throws IOException {
		 HttpParams httpParams = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParams, 5000); // 设置连接超时为5秒
			HttpClient client = new DefaultHttpClient(httpParams); // 生成一个http客户端发送请求对象
	        HttpGet get = new HttpGet(url);
	        try {
	            HttpResponse response = client.execute(get);
	            int statusCode = response.getStatusLine().getStatusCode();
	            if(statusCode < 300){
	                HttpEntity entity = response.getEntity();
	                return new HttpStreamResponse(entity.getContentType().getValue(),entity.getContentLength(),entity.getContent());
	            }
	            else{
	                throw new IOException("http get[" + url + "] failed,statuCode [" + statusCode + "].");
	            }
	        }
	        catch (Exception e){
	            if(!get.isAborted()) {
	                get.abort();
	            }
	            throw new IOException(e);
	        }
	    }
	 
	 public static HttpStreamResponse doGetInputStream(String url){
		 try {
				URL urlGet = new URL(url);
				HttpURLConnection http = (HttpURLConnection) urlGet.openConnection();
				http.setRequestMethod("GET"); // 必须是get方式请求
				http.setRequestProperty("Content-Type",	"application/x-www-form-urlencoded");
				http.setDoOutput(true);
				http.setDoInput(true);
				http.connect();
				// 获取文件转化为byte流
				InputStream is = http.getInputStream();
				// 获取文件类型
				String contentType = http.getContentType();
				// 获取文件大小
				Integer size = http.getContentLength();
				return new HttpStreamResponse(contentType,size,is);
			} catch (Exception e) {
			 logger.error("doGetInputStream:"+e.getMessage());
			}
		 return null;
	 }
}
