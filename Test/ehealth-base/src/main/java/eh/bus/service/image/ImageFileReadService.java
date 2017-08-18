package eh.bus.service.image;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.bus.dao.AppointRecordDAO;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.entity.bus.AppointRecord;
import eh.entity.cdr.Otherdoc;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.task.ExecutorRegister;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 根据业务记录查询图像信息，返回base64
 */
public class ImageFileReadService {

	public static final Logger log = Logger.getLogger(ImageFileReadService.class);

	private static ExecutorService exec = ExecutorRegister.register(Executors
			.newScheduledThreadPool(5));

	/**
	 * 根据预约记录查询转诊的图像信息
	 * */
	@RpcService
	public HisResponse<ArrayList<String>> getImageInfoByBusID(
			int appointRecordId) {
		HisResponse res = new HisResponse();
		AppointRecordDAO appointRecordDAO = DAOFactory
				.getDAO(AppointRecordDAO.class);
		AppointRecord record = appointRecordDAO.get(appointRecordId);
		if (record == null) {
			res.setMsgCode("-1");
			res.setMsg("记录不存在");
			return res;
		}
		Integer transferId = record.getTransferId();
		CdrOtherdocDAO cdrOtherdocDAO = DAOFactory.getDAO(CdrOtherdocDAO.class);
		List<Otherdoc> images = cdrOtherdocDAO.findByClinicTypeAndClinicId(1,
				transferId);
		if (images != null && images.size() > 0) {
			res.setMsgCode("200");
			res.setMsg("查询图像信息成功");
			ArrayList<String> list = getCompressList(images);
			res.setData(list);
			return res;
		} else {
			res.setMsgCode("-1");
			res.setMsg("没有图像信息");
			return res;
		}
	}

	public ArrayList<String> getCompressList(List<Otherdoc> images) {
		ArrayList<Future<String>> results = new ArrayList<Future<String>>();
		ArrayList<String> list = new ArrayList<>();
		for (Otherdoc doc : images) {
			results.add(exec.submit(new ImageCompressUtilTask(doc
					.getDocContent())));
		}
		try {
			for (Future<String> fs : results) {
				list.add(fs.get());
			}
		} catch (InterruptedException e) {
			log.error("getCompressList() error : "+e);
			return null;
		} catch (ExecutionException e) {
			log.error("getCompressList() error : "+e);
			return null;
		}
		return list;
	}

}
