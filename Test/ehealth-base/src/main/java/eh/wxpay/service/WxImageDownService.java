package eh.wxpay.service;

import ctd.account.UserRoleToken;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.upload.exception.FileRegistryException;
import ctd.mvc.upload.exception.FileRepositoryException;
import ctd.mvc.weixin.support.HttpClientUtils;
import ctd.mvc.weixin.support.HttpStreamResponse;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.wxpay.util.RandomStringGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class WxImageDownService {
	private static final Log logger = LogFactory.getLog(WxImageDownService.class);
	@SuppressWarnings({"rawtypes" })
	@RpcService
	public Map<String, Object> wxImageUpload(String accessToken, String mediaId) throws DAOException{
		String url = "http://file.api.weixin.qq.com/cgi-bin/media/get?access_token="
				+ accessToken + "&media_id=" + mediaId;
		logger.info("url = " + url);
		FileOutputStream fileOutputStream = null;
		InputStream is = null;
		File file = null;
		try {
			HttpStreamResponse res = HttpClientUtils.doGetAsInputStream(url);
			// 获取文件大小
			Long size = res.getContentLength();
			// 获取文件类型
			String contentType = res.getContentType();	
			logger.info("contentType = " + contentType);
			// 获取输入字节流
			is = res.getInputStream();
			if(contentType.equals("text/plain")){//下载图片出错
				String result = getStreamString(is);
				HashMap map = JSONUtils.parse(result, HashMap.class);
				Integer errcode = (Integer) map.get("errcode");
				String errmsg = (String) map.get("errmsg");
				if(errcode != null){
					throw new DAOException(errcode,errmsg);
				}else{
					throw new DAOException(42000,"access_token may have been invalid ");
				}
			}
			UserRoleToken urt = UserRoleToken.getCurrent();
			String userId = urt==null?null:urt.getUserId();
			String tenantId = urt==null?null:urt.getTenantId();
			String manageUnit = urt==null?null:urt.getManageUnit();
			FileMetaRecord metaRecord = new FileMetaRecord();
			Date nowDate = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			// 生成文件名
			String fileName = sdf.format(nowDate) + RandomStringGenerator.getRandomStringByLength(4)+".jpg";
			metaRecord.setFileName(fileName);
			metaRecord.setContentType(contentType);
			metaRecord.setTenantId(tenantId);
			metaRecord.setManageUnit(manageUnit);
			metaRecord.setMode(31);
			metaRecord.setOwner(userId);
			metaRecord.setCatalog("cdr-img");
			metaRecord.setFileSize(size);
			metaRecord.setUploadTime(nowDate);
			metaRecord.setLastModify(nowDate);
			//先生成本地图片
			file = new File(fileName);
			byte[] data = new byte[1024];
			int len = 0;
			fileOutputStream = new FileOutputStream(file);
			while ((len = is.read(data)) != -1) {
				fileOutputStream.write(data, 0, len);
			}
			logger.info("upload start ...... ");
			FileService.instance().upload(metaRecord, file);
			logger.info("upload end ...... ");
			file.delete();//删除本地图片
			Integer fileId = metaRecord.getFileId();
			Map<String, Object> map = new HashMap<>();
			map.put("fileId", fileId);
			map.put("fileName", fileName);
			logger.info("返回fileId:" + fileId==null?"":String.valueOf(fileId));
			return map;
		} catch (FileRepositoryException e) {
			logger.error("wxImageUpload FileRepositoryException"+e.getMessage());
		} catch (FileRegistryException e) {
			logger.error("wxImageUpload FileRegistryException"+e.getMessage());
		} catch (IOException e) {
			logger.error("wxImageUpload IOException"+e.getMessage());
		}finally{
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					logger.error("wxImageUpload :"+e.getMessage());
				}
			}
			if (fileOutputStream != null) {
				try {
					fileOutputStream.close();
				} catch (IOException e) {
					logger.error("wxImageUpload :"+e.getMessage());
				}
			}
		}
		return null;
	}
	/*
	 * 将输入流转为字符串
	 */
	private String getStreamString(InputStream tInputStream){
		if (tInputStream != null){
			try{
				BufferedReader tBufferedReader = new BufferedReader(new InputStreamReader(tInputStream));
				StringBuffer tStringBuffer = new StringBuffer();
				String sTempOneLine = new String("");
				while ((sTempOneLine = tBufferedReader.readLine()) != null){
					tStringBuffer.append(sTempOneLine);
				}
				return tStringBuffer.toString();
			}catch (Exception ex){
					logger.error(ex);
			}
		}
		return null;
	}
}
