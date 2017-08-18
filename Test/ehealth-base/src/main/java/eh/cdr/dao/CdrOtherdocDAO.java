package eh.cdr.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.DoctorDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.TransferDAO;
import eh.cdr.constant.DocClassConstant;
import eh.cdr.constant.DocIndexConstant;
import eh.cdr.constant.OtherdocConstant;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Consult;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.Transfer;
import eh.entity.cdr.DocIndex;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.List;

public abstract class CdrOtherdocDAO extends
		HibernateSupportDelegateDAO<Otherdoc> {
	private static final Log logger = LogFactory.getLog(CdrOtherdocDAO.class);

	public CdrOtherdocDAO() {
		super();
		this.setEntityName(Otherdoc.class.getName());
		this.setKeyField("otherDocId");
	}

	@RpcService
	@DAOMethod
	public abstract List<Otherdoc> findByClinicIdAndMpiid(Integer clinicId,
			String mpiId);

	/**
	 * 根据业务类型和业务主键号获取业务相关资料列表
	 * 
	 * @author zhangx
	 * @date 2015-11-3 下午8:21:16
	 * @param clinicType
	 *            业务类型(1转诊；2会诊；3咨询；4其他)
	 * @param clinicId
	 *            业务主键号
	 * @return 业务相关资料列表
	 */
	@RpcService
	@DAOMethod
	public abstract List<Otherdoc> findByClinicTypeAndClinicId(
			Integer clinicType, Integer clinicId);

	/**
	 * 根据图片ID获取图片类型
	 * 
	 * @author LF
	 * @param docContent
	 * @return
	 */
	@RpcService
	@DAOMethod
	public abstract List<Otherdoc> findByDocContent(Integer docContent);

	/**
	 * 添加文档资料数据</br>
	 * 
	 * 嵌在业务申请中，如果需要单独使用，则考虑是否需要进行事务包装
	 * 
	 * @author zhangx
	 * @date 2015-11-3 下午3:43:34
	 * @param clinicType
	 *            业务类型(1转诊；2会诊；3咨询；4其他;5在线云门诊;6医生自主上传;7患者注册上传病历
	 * @param clinicId
	 *            业务序号
	 * @param otherDocs
	 *            文档资料列表
	 */
	public void saveOtherDocList(int clinicType, int clinicId,
			List<Otherdoc> otherDocs) {

		TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
		DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		DepartmentDAO deptDAO = DAOFactory.getDAO(DepartmentDAO.class);
		DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
		MeetClinicDAO clinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
		ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
		PatientDAO patDAO = DAOFactory.getDAO(PatientDAO.class);
		AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);

		for (int i = 0; i < otherDocs.size(); i++) {
			Otherdoc cdrOtherdoc = otherDocs.get(i);
			checkLegality(cdrOtherdoc);

			// 其他资料--赋值
			Date createDate = new Date();
			cdrOtherdoc.setCreateDate(createDate);
			cdrOtherdoc.setClinicId(clinicId);
			cdrOtherdoc.setClinicType(clinicType);

			// 电子病历文档索引--赋值
			DocIndex docIndex = new DocIndex();
			docIndex.setClinicId(clinicId);
			docIndex.setDocType(cdrOtherdoc.getDocType());
			docIndex.setCreateDate(createDate);
			docIndex.setDocClass(OtherdocConstant.DOC_FORMAT_PDF.equals(cdrOtherdoc.getDocFormat())?
					DocClassConstant.PDF_DOC:DocClassConstant.OTHER_DOC);// 未说明，直接定死
			docIndex.setDocFlag(DocIndexConstant.DOC_FLAG_CANNOT_DEL);
			docIndex.setDocStatus(DocIndexConstant.DOC_STATUS_NO_DEL);
			docIndex.setLastModify(new Date());

			String docTypeText;
			try {
				docTypeText = DictionaryController.instance()
						.get("eh.cdr.dictionary.DocType")
						.getText(cdrOtherdoc.getDocType());
			} catch (ControllerException e) {
				logger.error(e.getMessage());
				continue;
			}

			docIndex.setDocTitle(docTypeText);
			docIndex.setDocSummary(docTypeText);
			docIndex.setDoctypeName(docTypeText);

			switch (clinicType) {
				case 1:// 转诊
					Transfer tran = transferDAO.getById(clinicId);
					if (tran != null) {
						cdrOtherdoc.setMpiid(tran.getMpiId());
						docIndex.setMpiid(tran.getMpiId());

						// 医生申请的转诊单
						if (StringUtils.isEmpty(tran.getRequestMpi())) {
							docIndex.setCreateOrgan(tran.getRequestOrgan());
							docIndex.setCreateDepart(tran.getRequestDepart());
							docIndex.setCreateDoctor(tran.getRequestDoctor());

							Doctor dcotor = doctorDAO.getByDoctorId(tran
									.getRequestDoctor());
							docIndex.setDoctorName(dcotor.getName());

							Department dept = deptDAO.getById(tran
									.getRequestDepart());
							docIndex.setDepartName(dept.getName());
						} else {
							// 患者申请的转诊单
							Patient reqPat = patDAO
									.getByMpiId(tran.getRequestMpi());
							if (reqPat != null) {
								docIndex.setDoctorName(reqPat.getPatientName());
							}
							docIndex.setDepartName("");
						}
					}
					break;
				case 2:// 会诊
					MeetClinic meetClinic = clinicDAO.getByMeetClinicId(clinicId);
					if (meetClinic != null) {
						cdrOtherdoc.setMpiid(meetClinic.getMpiid());
						docIndex.setMpiid(meetClinic.getMpiid());
						docIndex.setCreateOrgan(meetClinic.getRequestOrgan());
						docIndex.setCreateDepart(meetClinic.getRequestDepart());
						docIndex.setCreateDoctor(meetClinic.getRequestDoctor());

						Doctor dcotor = doctorDAO.getByDoctorId(meetClinic
								.getRequestDoctor());
						docIndex.setDoctorName(dcotor.getName());

						Department dept = deptDAO.getById(meetClinic
								.getRequestDepart());
						docIndex.setDepartName(dept.getName());
					}
					break;

				case 3:// 咨询
					Consult consult = consultDAO.getById(clinicId);
					if (consult != null) {
						cdrOtherdoc.setMpiid(consult.getMpiid());
						docIndex.setMpiid(consult.getMpiid());

						Patient p = patDAO.getByMpiId(consult.getRequestMpi());
						if (p != null) {
							docIndex.setDoctorName(p.getPatientName());
						}

						docIndex.setDepartName("");
					}
					break;
				case 5:// 在线云门诊(接诊方，出诊方都能穿照片，因此传入ID为接诊方，出诊方预约记录ID,)
					AppointRecord record = appointDao
							.getByAppointRecordId(clinicId);
					String mpiId = record.getMpiid();
					cdrOtherdoc.setMpiid(mpiId);
					docIndex.setMpiid(mpiId);

					Integer doctorId = record.getDoctorId();
					Integer deptId = Integer.parseInt(record.getAppointDepartId());

					docIndex.setCreateOrgan(record.getOrganId());
					docIndex.setCreateDepart(deptId);
					docIndex.setCreateDoctor(doctorId);

					Doctor dcotor = doctorDAO.getByDoctorId(doctorId);
					docIndex.setDoctorName(dcotor.getName());

					Department dept = deptDAO.getById(deptId);
					docIndex.setDepartName(dept.getName());
					break;
				case 7:
				case 9:
					docIndex.setMpiid(cdrOtherdoc.getMpiid());
					docIndex.setDoctorName("");
					docIndex.setDepartName("");
					break;
				default:
					break;
			}

			// 保存其他资料
			Otherdoc otherdoc = DAOFactory.getDAO(CdrOtherdocDAO.class).save(
					cdrOtherdoc);
			if (otherdoc.getOtherDocId() == null) {
				logger.error("saveOtherDocFailed:"
						+ JSONUtils.toString(otherdoc));
			}
			// 获取主键值
			Integer otherDocId = cdrOtherdoc.getOtherDocId();
			docIndex.setDocId(otherDocId);

			// 保存电子病历文档索引
			DocIndex docIndex2 = indexDAO.save(docIndex);

			if (docIndex2 == null) {
				logger.error("saveDocIndexFailed: docIndex2 = null");
			}
		}

	}
	
	private void checkLegality(Otherdoc cdrOtherdoc) {
		if (StringUtils.isEmpty(cdrOtherdoc.getDocName())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"docName is required");
		}
		if (cdrOtherdoc.getDocContent() == null) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"docContent is required");
		}
		if (StringUtils.isEmpty(cdrOtherdoc.getDocType())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"docType is required");
		}
		if (StringUtils.isEmpty(cdrOtherdoc.getDocFormat())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"docFormat is required");
		}
	}

}
