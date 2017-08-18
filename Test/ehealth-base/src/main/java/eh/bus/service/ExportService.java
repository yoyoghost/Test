package eh.bus.service;

import ctd.account.UserRoleToken;
import ctd.dictionary.DictionaryController;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorAccountDetailDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.MeetClinicRecordDAO;
import eh.bus.dao.TransferDAO;
import eh.entity.base.DoctorAndAccountAndDetail;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.MeetClinicAndResult;
import eh.entity.bus.Transfer;
import eh.util.BusExportExcel;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

public class ExportService {

	public static final Logger log = Logger.getLogger(ExportService.class);

	private static String fileName = "医师提现记录.xls";
	private static String title[] = { "序号", "医院", "医生编号", "医生姓名", "医生联系电话",
			"支付方式", "支付宝账号", "充值手机号", "开户行", "银行卡号", "提现金额", "提现申请日期",
			"要求最后打款日期" };
	private static Integer width[] = { 10, 40, 10, 10, 15, 15, 25, 15, 15, 25,
			15, 20, 20 };

	@SuppressWarnings("deprecation")
	@RpcService
	public int exportAccountDetail() {
		DoctorAccountDetailDAO dao = DAOFactory
				.getDAO(DoctorAccountDetailDAO.class);
		List<DoctorAndAccountAndDetail> datas = dao.findRecordByPayStatus(2);
		if (datas == null || datas.size() < 1) {
			return 0;
		}
		WritableWorkbook book = null;
		File xmlFile = new File(fileName);
		try {

			book = Workbook.createWorkbook(xmlFile);
			WritableSheet sheet = book.createSheet("sheet1", 0);
			for (int i = 0; i < title.length; i++) {
				sheet.addCell(new Label(i, 0, title[i]));
				sheet.setColumnView(i, width[i]);

			}
			for (int j = 0; j < datas.size(); j++) {
				DoctorAndAccountAndDetail data = datas.get(j);
				sheet.addCell(new Label(0, j + 1, data.getDoctoraccountdetail()
						.getAccountDetailId().toString()));
				Integer organ = data.getDoctor().getOrgan();
				if (organ != null) {
					String organText = DictionaryController.instance()
							.get("eh.base.dictionary.Organ").getText(organ);
					sheet.addCell(new Label(1, j + 1, organText));
				}
				sheet.addCell(new Label(2, j + 1, data.getDoctor()
						.getDoctorId().toString()));
				sheet.addCell(new Label(3, j + 1, data.getDoctor().getName()));
				sheet.addCell(new Label(4, j + 1, data.getDoctor().getMobile()));

				String payMode = data.getDoctoraccountdetail().getPayMode();
				if (payMode != null) {
					String payModeText = DictionaryController.instance()
							.get("eh.base.dictionary.PayMode").getText(payMode);
					sheet.addCell(new Label(5, j + 1, payModeText));
				}
				sheet.addCell(new Label(6, j + 1, data.getDoctoraccount()
						.getAlipayId().toString()));
				sheet.addCell(new Label(7, j + 1, data.getDoctoraccount()
						.getPayMobile()));
				sheet.addCell(new Label(8, j + 1, data.getDoctoraccountdetail()
						.getBankName()));
				sheet.addCell(new Label(9, j + 1, data.getDoctoraccountdetail()
						.getCardNo()));
				sheet.addCell(new Label(10, j + 1, data
						.getDoctoraccountdetail().getMoney().toString()));
				sheet.addCell(new Label(11, j + 1, data
						.getDoctoraccountdetail().getCreateDate()
						.toLocaleString()));
				sheet.addCell(new Label(12, j + 1, data.getLastDate()
						.toLocaleString()));
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
			meta.setCatalog("other-doc");
			meta.setContentType("application/vnd.ms-excel");
			meta.setFileName(fileName);
			meta.setFileSize(xmlFile.length());
			FileService.instance().upload(meta, is);
			xmlFile.delete();
			return meta.getFileId();
		} catch (Exception e) {
			log.error("exportAccountDetail() error : "+e);
		}
		return 0;
	}

	/**
	 * 根据提现记录单号下载提现记录
	 * 
	 * @author ZX
	 * @date 2015-8-11 下午2:34:23
	 * @param billId
	 * @return
	 */
	@SuppressWarnings("deprecation")
	@RpcService
	public int exportAccountDetailByBillId(String billId) {
		DoctorAccountDetailDAO dao = DAOFactory
				.getDAO(DoctorAccountDetailDAO.class);

		String exportName = "提现记录单-" + billId + ".xls";

		List<DoctorAndAccountAndDetail> datas = dao.findRecordByBillId(billId);
		if (datas == null || datas.size() < 1) {
			return 0;
		}
		WritableWorkbook book = null;
		File xmlFile = new File(exportName);
		try {

			book = Workbook.createWorkbook(xmlFile);
			WritableSheet sheet = book.createSheet("sheet1", 0);
			for (int i = 0; i < title.length; i++) {
				sheet.addCell(new Label(i, 0, title[i]));
				sheet.setColumnView(i, width[i]);

			}
			for (int j = 0; j < datas.size(); j++) {
				DoctorAndAccountAndDetail data = datas.get(j);
				sheet.addCell(new Label(0, j + 1, data.getDoctoraccountdetail()
						.getAccountDetailId().toString()));
				Integer organ = data.getDoctor().getOrgan();
				if (organ != null) {
					String organText = DictionaryController.instance()
							.get("eh.base.dictionary.Organ").getText(organ);
					sheet.addCell(new Label(1, j + 1, organText));
				}
				sheet.addCell(new Label(2, j + 1, data.getDoctor()
						.getDoctorId().toString()));
				sheet.addCell(new Label(3, j + 1, data.getDoctor().getName()));
				sheet.addCell(new Label(4, j + 1, data.getDoctor().getMobile()));

				String payMode = data.getDoctoraccountdetail().getPayMode();
				if (payMode != null) {
					String payModeText = DictionaryController.instance()
							.get("eh.base.dictionary.PayMode").getText(payMode);
					sheet.addCell(new Label(5, j + 1, payModeText));
				}
				sheet.addCell(new Label(6, j + 1, data.getDoctoraccount()
						.getAlipayId() == null ? "" : data.getDoctoraccount()
						.getAlipayId().toString()));
				sheet.addCell(new Label(7, j + 1, data.getDoctoraccount()
						.getPayMobile()));
				sheet.addCell(new Label(8, j + 1, data.getDoctoraccountdetail()
						.getBankName()));
				sheet.addCell(new Label(9, j + 1, data.getDoctoraccountdetail()
						.getCardNo()));
				sheet.addCell(new Label(10, j + 1, data
						.getDoctoraccountdetail().getMoney().toString()));
				sheet.addCell(new Label(11, j + 1, data
						.getDoctoraccountdetail().getCreateDate()
						.toLocaleString()));
				sheet.addCell(new Label(12, j + 1, data.getLastDate()
						.toLocaleString()));
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
			meta.setCatalog("other-doc");
			// meta.setCatalog("cdr-img");
			meta.setContentType("application/vnd.ms-excel");
			meta.setFileName(exportName);
			meta.setFileSize(xmlFile.length());
			FileService.instance().upload(meta, is);
			xmlFile.delete();
			return meta.getFileId();
		} catch (Exception e) {
			log.error("exportAccountDetailByBillId() error : "+e);
		}
		return 0;
	}

	/**
	 * 导出会诊业务数据到Excel
	 * 
	 * @author LF
	 * @param startTime
	 * @param endTime
	 * @return
	 */
	@RpcService
	public int MeetExcelExport(Date startTime, Date endTime) {
		MeetClinicRecordDAO clinicRecordDAO = DAOFactory
				.getDAO(MeetClinicRecordDAO.class);
		List<MeetClinicAndResult> andResults = clinicRecordDAO.exportExcelMeet(
				startTime, endTime);
		if (andResults == null || andResults.size() <= 0) {
			return 0;
		}
		return BusExportExcel.exportBussExcel(andResults, 2);
	}

	/**
	 * 
	 * Title: 按时间导出转诊业务数据 
	 * Description:根据开始时间和结束时间查询转诊业务数据，按转诊申请时间降序排列 导出成excel
	 * 
	 * @author AngryKitty
	 * @date 2015-8-24
	 * @param startTime
	 *            --开始时间
	 * @param endTime
	 *            --结束时间
	 * @return int
	 */
	@RpcService
	public int exportTransferByStrartTimeAndEndTime(final Date startTime,
			final Date endTime) {
		TransferDAO dao = DAOFactory.getDAO(TransferDAO.class);
		List<Transfer> list = dao.findByStartTimeAndEndTime(startTime, endTime);
		if (list == null || list.size() <= 0) {
			return 0;
		}
		return BusExportExcel.exportBussExcel(list, 1);

	}

	/**
	 * 根据开始时间和结束时间查询预约业务数据，导出成excel
	 * 
	 * @author Qichengjian
	 * @param startTime
	 * @param endTime
	 * @return
	 */
	@RpcService
	public int exportAppointByStartTimeAndEndTime(final Date startTime,
			final Date endTime){
		AppointRecordDAO dao =DAOFactory.getDAO(AppointRecordDAO.class );
		List<AppointRecord> list = dao.findByStartTimeAndEndTime(startTime, endTime);
		if(list == null ||list.size()<=0){
			return 0;
		}
		return BusExportExcel.exportBussExcel(list,4);
	}
}
