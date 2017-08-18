package eh.upload;

import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileServiceListener;
import ctd.net.rpc.util.ServiceAdapter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 头像上传事件
 * @author 
 *
 */
public class PatientImageUploadListener implements FileServiceListener {
	private static final Logger logger = LoggerFactory
			.getLogger(PatientImageUploadListener.class);
	@Override
	public String getCatalog() {
		
		return "patient-avatar";
	}

	@Override
	public void onFileDelete(FileMetaRecord arg0) {

	}

	@Override
	public void onFileLoad(FileMetaRecord arg0) {

	}

	@Override
	public void onFileUpload(FileMetaRecord arg0) {
		String mpiId=(String) arg0.getProperty("mpiId") ;
		if(StringUtils.isEmpty(mpiId)){
			logger.error("missing mpiId by fileId[{}] name {}", arg0.getFileId(), arg0.getFileName());
			return;
		}
		try {	//更新病人头像
			ServiceAdapter.invoke("eh.patient", "updatePhoto", arg0.getFileId(), mpiId);
			logger.info("patient[{}] photo[{}] updated:", mpiId, arg0.getFileId());
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

}
