package eh.util;

import ctd.account.UserRoleToken;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Consult;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.bus.Transfer;
import eh.mpi.dao.PatientDAO;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BusExportExcel {
	public static final Logger log = Logger.getLogger(BusExportExcel.class);

	private static String fileName1 = "转诊业务";
	private static String fileName2 = "会诊业务";
	private static String fileName3 = "咨询业务";
	private static String fileName4 = "预约业务";
	private static String nowDate = new String();
	private static String Suffix = ".xls";
	private static String[] title = { "申请时间", "业务类型", "申请机构", "申请医生", "目标机构",
			"目标医生", "病人姓名", "状态", "预约方式" };
	private static int[] width = { 20, 9, 30, 9, 30, 32, 8, 12, 18 };

	/**
	 * 导出业务数据（Excel）
	 * 
	 * @author LF
	 * @param list
	 * @param busId
	 *            1.转诊，2.会诊，3.咨询，4.预约(咨询没写)
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@RpcService
	public static int exportBussExcel(List list, int busId) {
		log.info("This is exportBussExcel in eh.util.BusExportExcel.");
		if (list == null || list.size() <= 0) {
			log.error("list is required!");
			return 0;
		}
		if (busId <= 0 || busId > 4) {
			log.error("busId not within range!");
			return 0;
		}
		List<Transfer> transfers = new ArrayList<Transfer>();
		List<MeetClinicAndResult> andResults = new ArrayList<MeetClinicAndResult>();
		List<Consult> consults = new ArrayList<Consult>();
		List<AppointRecord> appointRecords = new ArrayList<AppointRecord>();
		StringBuffer fileName = new StringBuffer();
		try {
			switch (busId) {
			case 1:
				transfers = list;
				fileName.append(fileName1);
				break;
			case 2:
				andResults = list;
				fileName.append(fileName2);
				break;
			case 3:
				consults = list;
				fileName.append(fileName3);
				break;
			case 4:
				appointRecords = list;
				fileName.append(fileName4);
				break;
			default:
				break;
			}
		} catch (Exception e) {
			log.error("Type conversion fail!"+e);
		}
		if (transfers.size() <= 0 && andResults.size() <= 0
				&& consults.size() <= 0 && appointRecords.size() <= 0) {
			log.error("=============data lose==========");
			return 0;
		}
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		nowDate = sdf.format(new Date());
		fileName.append(nowDate);
		fileName.append(Suffix);
		WritableWorkbook book = null;
		File xmlFile = new File(fileName.toString());
		try {
			book = Workbook.createWorkbook(xmlFile);
			WritableSheet sheet = book.createSheet("sheet1", 0);
			for (int i = 0; i < title.length; i++) {
				sheet.addCell(new Label(i, 0, title[i]));
				sheet.setColumnView(i, width[i]);
			}
			Dictionary dictionaryOrgan = DictionaryController.instance().get(
					"eh.base.dictionary.Organ");
			Dictionary dictionaryDoctor = DictionaryController.instance().get(
					"eh.base.dictionary.Doctor");
			Dictionary dictionaryTranStatus = DictionaryController.instance()
					.get("eh.bus.dictionary.TransferStatus");
			Dictionary dictionaryMeetStatus = DictionaryController.instance()
					.get("eh.bus.dictionary.ExeStatus");
			Dictionary dictionaryAppStatus = DictionaryController.instance()
					.get("eh.bus.dictionary.AppointStatus");
			Dictionary dictionaryAppRoad = DictionaryController.instance().get(
					"eh.bus.dictionary.AppointRoad");
			PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
			for (int i = 0; i < list.size(); i++) {
				String requestTime = null;
				String BussType = null;
				String requestOrgan = null;
				String requestDoctor = null;
				String targetOrgan = null;
				String targetDoctor = null;
				String patientName = null;
				String status = null;
				String appointRoad = null;
				if (transfers.size() > 0) {
					Transfer transfer = transfers.get(i);
					requestTime = transfer.getRequestTime().toString();
					BussType = fileName1;
					requestOrgan = dictionaryOrgan.getText(transfer
							.getRequestOrgan());
					requestDoctor = dictionaryDoctor.getText(transfer
							.getRequestDoctor());
					targetOrgan = dictionaryOrgan.getText(transfer
							.getTargetOrgan());
					targetDoctor = dictionaryDoctor.getText(transfer
							.getTargetDoctor());
					patientName = (patientDAO.get(transfer.getMpiId()))
							.getPatientName();
					status = dictionaryTranStatus.getText(transfer
							.getTransferStatus());
				}
				if (andResults.size() > 0) {
					MeetClinicAndResult andResult = andResults.get(i);
					requestTime = andResult.getMc().getRequestTime().toString();
					BussType = fileName2;
					requestOrgan = dictionaryOrgan.getText(andResult.getMc()
							.getRequestOrgan());
					requestDoctor = dictionaryDoctor.getText(andResult.getMc()
							.getRequestDoctor());
					targetOrgan = dictionaryOrgan.getText(andResult.getMr()
							.getTargetOrgan());
					targetDoctor = dictionaryDoctor.getText(andResult.getMr()
							.getTargetDoctor());
					patientName = (patientDAO.get(andResult.getMc().getMpiid()))
							.getPatientName();
					status = dictionaryMeetStatus.getText(andResult.getMr()
							.getExeStatus());
				}
				if (appointRecords.size() > 0) {
					AppointRecord appointRecord = appointRecords.get(i);
					requestTime = appointRecord.getAppointDate().toString();
					BussType = fileName4;
					requestOrgan = dictionaryOrgan.getText(appointRecord
							.getAppointOragn());
					requestDoctor = dictionaryDoctor.getText(appointRecord
							.getAppointUser());
					targetOrgan = dictionaryOrgan.getText(appointRecord
							.getOrganId());
					targetDoctor = dictionaryDoctor.getText(appointRecord
							.getDoctorId());
					patientName = appointRecord.getPatientName();
					status = dictionaryAppStatus.getText(appointRecord
							.getAppointStatus());
					appointRoad = dictionaryAppRoad.getText(appointRecord
							.getAppointRoad());
					sheet.addCell(new Label(8, i + 1, appointRoad));
				}
				sheet.addCell(new Label(0, i + 1, requestTime));
				sheet.addCell(new Label(1, i + 1, BussType));
				sheet.addCell(new Label(2, i + 1, requestOrgan));
				sheet.addCell(new Label(3, i + 1, requestDoctor));
				sheet.addCell(new Label(4, i + 1, targetOrgan));
				sheet.addCell(new Label(5, i + 1, targetDoctor));
				sheet.addCell(new Label(6, i + 1, patientName));
				sheet.addCell(new Label(7, i + 1, status));
			}
			book.write();
			book.close();
			FileMetaRecord meta = new FileMetaRecord();
			InputStream is = new FileInputStream(xmlFile);
			UserRoleToken token = UserRoleToken.getCurrent();
			meta.setManageUnit(token.getManageUnit());
			meta.setOwner(token.getUserId());
			meta.setLastModify(new Date());
			meta.setUploadTime(new Date());
			// meta.setCatalog("cdr-img");//测试、开发
			meta.setCatalog("other-doc");// 正式
			meta.setContentType("application/vnd.ms-excel");
			meta.setFileName(fileName.toString());
			meta.setFileSize(xmlFile.length());
			FileService.instance().upload(meta, is);
			xmlFile.delete();
			return meta.getFileId();
			// return 1;
		} catch (Exception e) {
			log.error(e);
		}
		return 0;
	}
}
