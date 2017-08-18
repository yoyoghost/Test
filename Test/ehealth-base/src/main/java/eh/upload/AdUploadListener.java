package eh.upload;

import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileServiceListener;
import ctd.net.rpc.util.ServiceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 头像上传事件
 * @author LF
 *
 */
public class AdUploadListener implements FileServiceListener {
	private static final Logger log = LoggerFactory.getLogger(AdUploadListener.class);

	@Override
	public String getCatalog() {
		
		return "ad-avatar";
	}

	@Override
	public void onFileDelete(FileMetaRecord arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFileLoad(FileMetaRecord arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onFileUpload(FileMetaRecord arg0) {
		Object id = arg0.getProperty("id");
		if (id == null){
			log.error("missing id by fileId[{}]", arg0.getFileId());
			return;
		}

		Map<String, Object> properties = arg0.getProperties();
		if (properties == null){
			log.error("missing properties by fileId[{}]", arg0.getFileId());
			return;
		}
		//测试
		StringBuilder builder = new StringBuilder("http://121.41.93.220:8080/ehealth-base/upload/");
		//正式
		//StringBuilder builder = new StringBuilder("http://ehealth.easygroup.net.cn/ehealth-base/upload/");
		try{
			id=Integer.parseInt(String.valueOf(id));
			if (properties.containsKey("photo")){	// 首页照片
				ServiceAdapter.invoke("eh.ad", "updatePhotoById", builder.append(arg0.getFileId()).toString(), id);
				log.info("doctor[{}] photo updated by {}", id, arg0.getFileId());
			}else if (properties.containsKey("content")){	// 网页
				ServiceAdapter.invoke("eh.ad", "updateContentById", builder.append(arg0.getFileId()).toString(), id);
				log.info("doctor[{}] content updated by {}", id, arg0.getFileId());
			}else {
				log.error("missing properties by fileId[{}]", arg0.getFileId());
			}
		}catch (Exception e){
			log.error(e.getMessage());
		}
	}

}
