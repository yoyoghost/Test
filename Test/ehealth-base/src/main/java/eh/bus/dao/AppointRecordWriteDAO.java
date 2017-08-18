package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportWriteDAO;
import eh.entity.bus.AppointRecord;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;

public abstract class AppointRecordWriteDAO extends
		HibernateSupportWriteDAO<AppointRecord> {

	public AppointRecordWriteDAO() {
		setEntityName(AppointRecord.class.getName());
		setKeyField("appointRecordId");
	}

	@Override
	protected void beforeSave(AppointRecord o) throws DAOException {
		AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
		PatientDAO PatientDAO = DAOFactory.getDAO(PatientDAO.class);
		Patient p = PatientDAO.getByMpiId(o.getMpiid());
		o.setCertId(p.getRawIdcard());
		appointRecordDAO.checkAppointRecordsBeforSave(o);
	}

	/**
	 * 查询预约记录 是否 和目标列表中的云门诊预约记录相匹配</br>
	 * 
	 * 云门诊预约记录 且telClinicId相同--匹配</br>
	 * 
	 * @author zhangx
	 * @date 2015-10-20下午7:53:44
	 * @param o
	 *            要添加的预约记录
	 * @param list
	 *            已经存在的符合条件的预约记录
	 * @return true:匹配；false:不匹配
	 *//*
	private boolean canAdd(AppointRecord o, List<AppointRecord> list) {
		Boolean b = false;
		if (o.getTelClinicFlag() != null && o.getTelClinicFlag() == 1) {
			for (AppointRecord appointRecord : list) {
				if (appointRecord.getTelClinicFlag() != null
						&& appointRecord.getTelClinicFlag() == 1
						&& o.getTelClinicId().equals(
								appointRecord.getTelClinicId())) {
					b = true;
				}
			}
		}

		return b;
	}

	*//**
	 * 校验患者已经预约记录
	 * 
	 * @desc 1.获取同一个患者同一天同一个科室或医师的预约记录</br> 2.同一病人同一个就诊日不能预约2次以上</br>3.预约记录>=2 且
	 *       要添加的预约记录为普通门诊/未添加过的云门诊记录
	 * 
	 * @param o
	 * 
	 * @desc 2015-02-15 该预约记录条数限制只针对医生端的预约，医生端发起的转诊接收，患者端的预约
	 *//*
	public void checkAppointList(AppointRecord o) {

		AppointRecordDAO appointRecordDAO = DAOFactory
				.getDAO(AppointRecordDAO.class);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String strWorkDate = sdf.format(o.getWorkDate());
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		Date workDate = null;
		try {
			workDate = df.parse(strWorkDate);
		} catch (ParseException e1) {
			throw new DAOException(e1);
		}
		// 蒋旭辉和王宁武 预约不限制
		if (o.getMpiid().equals("2c9081814cc5cb8a014ccf86ae3d0000")
				|| o.getMpiid().equals("2c9081814cc3ad35014cc3e0361f0000")) {
			return;
		}

		// 获取同一个患者同一天同一个科室或医师的预约记录
		List<AppointRecord> list = appointRecordDAO
				.findByMpiIdAndWorkDateAndOrganId(o.getMpiid(), o.getOrganId(),
						o.getAppointDepartId(), o.getDoctorId(), workDate);
		String doctorName = "";
		String organName = "";
		String workType = "";
		if (list.size() != 0) {
			try {
				doctorName = DictionaryController.instance()
						.get("eh.base.dictionary.Doctor")
						.getText(list.get(0).getDoctorId());
				// 医院名称
				organName = DictionaryController.instance()
						.get("eh.base.dictionary.Organ")
						.getText(list.get(0).getOrganId());

				workType = DictionaryController.instance()
						.get("eh.bus.dictionary.WorkType")
						.getText(list.get(0).getWorkType());
			} catch (ControllerException e) {
				throw new DAOException(e);
			}

			// 就诊时间
			String WorkDate = sdf.format(list.get(0).getWorkDate());
			if (list.get(0).getAppointRoad() == 5) {
				throw new DAOException(602, "您已预约了" + doctorName + "医师("
						+ list.get(0).getAppointDepartName() + "|" + organName
						+ ")的" + WorkDate + workType + "第"
						+ list.get(0).getOrderNum() + "个就诊号，请勿重复预约！");
			} else {
				throw new DAOException(602, "您已预约了" + doctorName + "医师("
						+ list.get(0).getAppointDepartName() + "|" + organName
						+ ")的" + WorkDate + workType + "就诊号，请勿重复预约！");
			}

		}

		// 同一病人同一个就诊日不能预约2次以上
		List<AppointRecord> list2 = appointRecordDAO.findByMpiIdAndWorkDate(
				o.getMpiid(), workDate, workDate);

		// 预约记录>=2 且 要添加的预约记录为普通门诊/未添加过的云门诊记录
		if (list2.size() >= 2 && !canAdd(o, list2)) {
			String msg = "您已预约了 " + sdf.format(workDate) + " ";
			String msgDoctorName = "";
			String msgOrganName = "";
			for (int i = 0; i < list2.size(); i++) {
				try {
					int docId = list2.get(i).getDoctorId();
					msgDoctorName = DictionaryController.instance()
							.get("eh.base.dictionary.Doctor").getText(docId);
					// 医院名称
					int organId = list2.get(i).getOrganId();
					msgOrganName = DictionaryController.instance()
							.get("eh.base.dictionary.Organ").getText(organId);

				} catch (ControllerException e) {
					throw new DAOException(e);
				}

				msg = msg + msgDoctorName + "医师(" + msgOrganName + ")、";
			}

			msg = msg.substring(0, msg.length() - 1) + "的就诊，不能再进行预约";
			throw new DAOException(602, msg);
		}

		// 同一个病人7天内预约不可超过3次
		Date startDate = Context.instance().get("date.getDatetimeOfLastWeek",
				Date.class);
		Date endDate = Context.instance().get("date.getToday", Date.class);

		List<AppointRecord> list3 = appointRecordDAO.findByMpiIdAndAppointDate(
				o.getMpiid(), startDate, endDate);

		if (list3.size() >= 3 && !canAdd(o, list3)) {
			String msg = "您已预约了 ";
			String msgDoctorName = "";
			String msgOrganName = "";
			for (int i = 0; i < list3.size(); i++) {
				try {
					int docId = list3.get(i).getDoctorId();
					msgDoctorName = DictionaryController.instance()
							.get("eh.base.dictionary.Doctor").getText(docId);
					// 医院名称
					int organId = list3.get(i).getOrganId();
					msgOrganName = DictionaryController.instance()
							.get("eh.base.dictionary.Organ").getText(organId);

				} catch (ControllerException e) {
					throw new DAOException(e);
				}

				msg = msg + sdf.format(list3.get(i).getWorkDate())
						+ msgDoctorName + "医师(" + msgOrganName + ")、";
			}

			msg = msg.substring(0, msg.length() - 1) + "的就诊，不能再进行预约";
			throw new DAOException(602, msg);
		}
	}*/
}