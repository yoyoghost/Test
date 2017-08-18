package eh.upload;

import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileServiceListener;
import ctd.net.rpc.util.ServiceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 机构图片上传事件
 * @author 
 *
 */
public class OrganImageUploadListener implements FileServiceListener {
	private static final Logger log = LoggerFactory.getLogger(OrganImageUploadListener.class);
	@Override
	public String getCatalog() {
		
		return "organ-avatar";
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
		Map<String, Object> properties = arg0.getProperties();
		if (properties == null){
			log.error("missing properties by fileId[{}]", arg0.getFileId());
			return;
		}
		Object organId = properties.get("organId");
		if (organId == null){
			log.error("missing organId by fileId[{}]", arg0.getFileId());
			return;
		}
		try {
			organId = Integer.valueOf(String.valueOf(organId));
			if (properties.containsKey("photo")){	//更新机构图片
				ServiceAdapter.invoke("eh.organ", "updatePhotoByOrganId", arg0.getFileId(), organId);
				log.info("organ[{}] photo updated by {}", organId, arg0.getFileId());
			}else if (properties.containsKey("certImage")){ //更新机构执业证
				ServiceAdapter.invoke("eh.organ", "updateCertImageByOrganId", arg0.getFileId(), organId);
				log.info("organ[{}] certImage updated by {}", organId, arg0.getFileId());
			}else {
				log.error("missing properties by fileId[{}]", arg0.getFileId());
			}
		}catch (Exception e){
			log.error(e.getMessage());
		}
		
	}

}
