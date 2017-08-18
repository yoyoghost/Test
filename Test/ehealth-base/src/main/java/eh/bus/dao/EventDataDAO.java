package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.EmploymentDAO;
import eh.bus.service.AppointService;
import eh.entity.bus.AppointSource;
import eh.entity.bus.EventData;
import eh.entity.bus.HisAppointRecord;
import eh.task.executor.EventDateExecutor;
import eh.utils.DateConversion;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class EventDataDAO extends
		HibernateSupportDelegateDAO<EventData> {
	private static final Log logger = LogFactory.getLog(AppointRecordDAO.class);

	public EventDataDAO() {
		super();
		this.setEntityName(EventData.class.getName());
		this.setKeyField("id");
	}

	@RpcService
	public void saveEventDate(EventData eventDate) {
		save(eventDate);
	}

	@RpcService
	public void saveEventDatas(List<HashMap<String, Object>> tmpList,
			String messageId, String queryDate) throws DAOException {
		EventDateExecutor event = new EventDateExecutor();
		event.execute(tmpList, messageId,
				queryDate);
	}

	@SuppressWarnings("unchecked")
	@RpcService
	public void saveEventData(final HashMap<String, Object> map,
			final String messageId, final String queryDate) throws DAOException {
		try {
			EventData eventData = new EventData();
			eventData.seteventType((String) map.get("eventType"));
			eventData.setContent(map.get("content") == null ? "null" : map.get(
					"content").toString());
			eventData.setMessageId(messageId);
			eventData.setQueryTime(queryDate);
			eventData.setCreateTime(new Date());
			save(eventData);

		} catch (Exception e) {
			logger.error(e.getMessage());
		}

	}

	/**
	 * 排班删除事件处理
	 * 
	 * @author hyj
	 * @param contentMap
	 */
	@SuppressWarnings("unchecked")
	public void schedulingDelete(HashMap<String, Object> contentMap) {
		HashMap<String, Object> organSchedulingMap = null;
		organSchedulingMap = (HashMap<String, Object>) contentMap
				.get("OrganScheduling");
		int organId = Integer.parseInt(organSchedulingMap.get("OrganId")
				.toString());
		String organSchedulingId = organSchedulingMap.get("OrganSchedulingId")
				.toString();
		AppointSourceDAO appointSourceDAO = DAOFactory
				.getDAO(AppointSourceDAO.class);
		appointSourceDAO.updateStopFlagForSchedulingDelete(organId,
				organSchedulingId, 1);
	}

	/**
	 * 排班开诊或排班停诊事件处理
	 * 
	 * @author hyj
	 * @param contentMap
	 */
	@SuppressWarnings("unchecked")
	public void schedulingOpenOrStop(HashMap<String, Object> contentMap,
			int stopFlag) {
		HashMap<String, Object> organSchedulingMap = null;
		organSchedulingMap = (HashMap<String, Object>) contentMap
				.get("OrganScheduling");
		int organId = Integer.parseInt(organSchedulingMap.get("OrganId")
				.toString());
		String organSchedulingId = organSchedulingMap.get("OrganSchedulingId")
				.toString();
		int workType = Integer.parseInt(organSchedulingMap.get("WorkType")
				.toString());
		AppointSourceDAO appointSourceDAO = DAOFactory
				.getDAO(AppointSourceDAO.class);
		appointSourceDAO.updateStopFlagForSchedulingOpenOrStopAndSendSMS(organId,
				organSchedulingId, stopFlag, workType);
	}

	/**
	 * 排班新增或号源新增事件处理
	 * 
	 * @author hyj
	 * @param schedulingList
	 * @param sourceList
	 */
	@SuppressWarnings("unchecked")
	public void schedulingAddOrSourceAdd(
			List<HashMap<String, Object>> schedulingList,
			List<HashMap<String, Object>> sourceList) {
		HashMap<String, Object> organSchedulingMap = null;
		HashMap<String, Object> organSourceMap = null;
		AppointSource as = new AppointSource();

		AppointSourceDAO appointSourceDAO = DAOFactory
				.getDAO(AppointSourceDAO.class);
		for (HashMap<String, Object> sourMap : sourceList) {
			organSourceMap = (HashMap<String, Object>) ((HashMap<String, Object>) sourMap
					.get("content")).get("OrganSourceInfo");
			String sourOrganSchedulingId = organSourceMap.get(
					"OrganSchedulingId").toString();
			if (sourOrganSchedulingId.equals("13295")) {
			}
			if (schedulingList.size() > 0) {
				for (HashMap<String, Object> scheMap : schedulingList) {
					organSchedulingMap = (HashMap<String, Object>) ((HashMap<String, Object>) scheMap
							.get("content")).get("OrganScheduling");
					String scheOrganSchedulingId = organSchedulingMap.get(
							"OrganSchedulingId").toString();
					if (scheOrganSchedulingId.equals(sourOrganSchedulingId)) {
						// 创建排班数据
						as = creatSchedulingData(organSchedulingMap);
						// 创建号源数据
						as = creatSourceData(organSourceMap, as);
						appointSourceDAO.saveAppointSource(as);
						break;
					} else {
						int organId = Integer.parseInt(organSchedulingMap.get(
								"OrganId").toString());
						List<AppointSource> list = appointSourceDAO
								.findByOrganIdAndOrganSchedulingId(organId,
										sourOrganSchedulingId);
						if (list.size() > 0) {
							AppointSource a = list.get(0);
							Calendar c = Calendar.getInstance();
							c.setTime(a.getStartTime());
							int worktype = 0;
							if (c.get(Calendar.HOUR_OF_DAY) < 12) {
								worktype = 1;
								// source.setWorkType(1);
							} else {
								worktype = 2;
								// source.setWorkType(2);
							}
							a.setWorkType(worktype);
							// 创建已存在排班数据
							as = creatExistSchedulingData(a);
							// 创建号源数据
							as = creatSourceData(organSourceMap, as);
							appointSourceDAO.saveAppointSource(as);
							break;
						}
					}
				}
			} else {
				int organId = Integer.parseInt(organSourceMap.get("OrganId")
						.toString());
				List<AppointSource> list = appointSourceDAO
						.findByOrganIdAndOrganSchedulingId(organId,
								sourOrganSchedulingId);
				if (list.size() > 0) {
					AppointSource a = list.get(0);
					Calendar c = Calendar.getInstance();
					c.setTime(a.getStartTime());
					int worktype = 0;
					if (c.get(Calendar.HOUR_OF_DAY) < 12) {
						worktype = 1;
						// source.setWorkType(1);
					} else {
						worktype = 2;
						// source.setWorkType(2);
					}
					a.setWorkType(worktype);
					// 创建已存在排班数据
					as = creatExistSchedulingData(a);
					// 创建号源数据
					as = creatSourceData(organSourceMap, as);
					appointSourceDAO.saveAppointSource(as);
				}

			}

		}
	}

	/**
	 * 创建排班数据
	 * 
	 * @author hyj
	 * @param organSchedulingMap
	 * @return
	 */
	private AppointSource creatSchedulingData(
			HashMap<String, Object> organSchedulingMap) {
		AppointSource as = new AppointSource();
		as.setOrganId(Integer.parseInt(organSchedulingMap.get("OrganId")
				.toString()));
		as.setOrganSchedulingId(organSchedulingMap.get("OrganSchedulingId")
				.toString());
		as.setWorkType(Integer.parseInt(organSchedulingMap.get("WorkType")
				.toString()));

		String jobNumber = organSchedulingMap.get("DoctorID").toString();
		EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
		// 第一执业点
		int doctorID = employmentDAO.getDoctorIdByJobNumberAndOrganId(
				jobNumber, as.getOrganId());
		if (doctorID == 0) {
			logger.info("该排班医生未在平台注册,不保存该排班,jobnumber:" + jobNumber
					+ ",doctorName:"
					+ organSchedulingMap.get("DoctorName").toString());
			return null;
		}
		as.setDoctorId(doctorID);
		as.setAppointDepartCode(organSchedulingMap.get("DepartCode").toString());
		as.setAppointDepartName(organSchedulingMap.get("DepartName").toString());
		as.setSourceType(Integer.parseInt(organSchedulingMap.get("SourceType")
				.toString()));
		as.setSourceLevel(Integer.parseInt(organSchedulingMap
				.get("SourceLevel").toString()));
		as.setPrice(Double.parseDouble(organSchedulingMap.get("Price")
				.toString()));
		as.setWorkDate(DateConversion.getDateByWeekday(Integer
				.parseInt(organSchedulingMap.get("WeekID").toString())));
		return as;
	}

	/**
	 * 创建已存在排班数据
	 * 
	 * @author hyj
	 * @param as
	 * @return
	 */
	private AppointSource creatExistSchedulingData(AppointSource as) {
		AppointSource target = new AppointSource();
		target.setOrganId(as.getOrganId());
		target.setOrganSchedulingId(as.getOrganSchedulingId());
		target.setWorkType(as.getWorkType());// 不对

		target.setDoctorId(as.getDoctorId());
		target.setAppointDepartCode(as.getAppointDepartCode());
		target.setAppointDepartName(as.getAppointDepartName());
		target.setSourceType(as.getSourceType());
		target.setSourceLevel(as.getSourceLevel());
		target.setPrice(as.getPrice());
		target.setWorkDate(as.getWorkDate());
		return target;
	}

	/**
	 * 创建号源数据
	 * 
	 * @param organSourceMap
	 * @param as
	 * @return
	 */
	private AppointSource creatSourceData(
			HashMap<String, Object> organSourceMap, AppointSource as) {
		as.setOriginalSourceId(organSourceMap.get("OrganSourceId").toString());
		String startTimePoint = organSourceMap.get("StartTime").toString();
		String endTimePoint = organSourceMap.get("EndTime").toString();
		as.setStartTime(DateConversion.getDateByTimePoint(as.getWorkDate(),
				startTimePoint));
		as.setEndTime(DateConversion.getDateByTimePoint(as.getWorkDate(),
				endTimePoint));
		as.setOrderNum(Integer.parseInt(organSourceMap.get("OrderNum")
				.toString()));
		as.setSourceNum(Integer.parseInt(organSourceMap.get("SourceNum")
				.toString()));
		as.setUsedNum(0);
		as.setCreateDate(new Date());
		as.setStopFlag(0);
		String organSourceId = getOrganSourceID(as);
		as.setOrganSourceId(organSourceId);
		return as;
	}

	/**
	 * 转化OrganSourceID
	 * 
	 * @param source
	 * @return
	 */
	private String getOrganSourceID(AppointSource source) {
		Calendar c = Calendar.getInstance();
		c.setTime(source.getStartTime());
		int worktype = 0;
		if (c.get(Calendar.HOUR_OF_DAY) < 12) {
			worktype = 1;
			// source.setWorkType(1);
		} else {
			worktype = 2;
			// source.setWorkType(2);
		}
		String dateStr = getDate(source.getWorkDate(), "yyyyMMdd");
		String organSourceID = dateStr + "|" + source.getOrganSchedulingId()
				+ "|" + worktype + "|" + source.getOrderNum();
		return organSourceID;
	}

	/**
	 * 获取系统当期年月日(精确到天)，格式：yyyyMMdd
	 * 
	 * @return
	 */
	public static String getDate(Date date, String format) {
		DateFormat df = new SimpleDateFormat(format);
		return df.format(date);
	}

	/**
	 * 排班删除事件处理
	 * 
	 * @author hyj
	 * @param contentMap
	 */
	@SuppressWarnings("unchecked")
	public void sourceDelete(HashMap<String, Object> contentMap) {
		HashMap<String, Object> organSchedulingMap = null;
		organSchedulingMap = (HashMap<String, Object>) contentMap
				.get("OrganScheduling");
		int organId = Integer.parseInt(organSchedulingMap.get("OrganId")
				.toString());
		String OrganSourceId = organSchedulingMap.get("OrganSourceId")
				.toString();
		AppointSourceDAO appointSourceDAO = DAOFactory
				.getDAO(AppointSourceDAO.class);
		appointSourceDAO.updateStopFlagForSourceDelete(organId, OrganSourceId,
				1);
	}

	/**
	 * 排班修改事件处理
	 * 
	 * @author hyj
	 * @param contentMap
	 */
	@SuppressWarnings("unchecked")
	public void schedulingModify(HashMap<String, Object> contentMap) {
		HashMap<String, Object> organSchedulingMap = null;
		organSchedulingMap = (HashMap<String, Object>) contentMap
				.get("OrganScheduling");
		int organId = Integer.parseInt(organSchedulingMap.get("OrganId")
				.toString());
		String organSchedulingID = organSchedulingMap.get("OrganSchedulingId")
				.toString();
		AppointSourceDAO appointSourceDAO = DAOFactory
				.getDAO(AppointSourceDAO.class);
		String active1 = organSchedulingMap.get("Active1").toString();
		String active2 = organSchedulingMap.get("Active2").toString();
		if (active1.equals("2")) {// 上午停诊
			appointSourceDAO.updateStopFlagForHisFailed(organId,
					organSchedulingID, 1);
			logger.error("更新成上午停诊：" + organSchedulingMap);
		}
		if (active2.equals("2")) {// 下午停诊
			appointSourceDAO.updateStopFlagForHisFailed(organId,
					organSchedulingID, 2);
			logger.error("更新成上午停诊：" + organSchedulingMap);
		}
		if (active1.equals("1")) {// 上午开诊
			appointSourceDAO.updateStopFlagForSchedulingOpenOrStop(organId,
					organSchedulingID, 0, 1);
			logger.error("更新成上午开诊：" + organSchedulingMap);
		}
		if (active2.equals("1")) {// 下午开诊
			appointSourceDAO.updateStopFlagForSchedulingOpenOrStop(organId,
					organSchedulingID, 0, 2);
			logger.error("更新成下午开诊：" + organSchedulingMap);
		}

	}

	/**
	 * 转化OrganSourceID
	 * APPOINTMENT事件 startTime 为空 workType先用医院传过来的workType 但是省中会出现workType 不准的情况
	 * @param source
	 * @return
	 */
	private String getOrganSourceIDWithWorkType(AppointSource source) {
		String organSourceID = "";
		try {
			Integer workType = source.getWorkType();
			Date workDate = source.getWorkDate();
			String dateStr = "";
			if (null != workDate) {
				dateStr = DateConversion.getDateFormatter(workDate, "yyyyMMdd");
			}
			organSourceID = dateStr + "|" + source.getOrganSchedulingId()
					+ "|" + workType + "|" + source.getOrderNum();
		}catch (Exception e){
			logger.error("AppointSource:"+ JSONUtils.toString(source)+"=====转化OrganSourceID出错:" + e.getMessage());
		}
		return organSourceID;
	}

	private Integer getWorkType(Date date){
		Calendar c = Calendar.getInstance();
		if(null == date){
			return null;
		}
		c.setTime(date);
		Integer worktype = 0;
		if (c.get(Calendar.HOUR_OF_DAY) < 12) {
			worktype = 1;
			// source.setWorkType(1);
		} else {
			worktype = 2;
			// source.setWorkType(2);
		}
		return worktype;
	}

	// 预约记录
	@SuppressWarnings("unchecked")
	public void appointment(HashMap<String, Object> contentMap, String type) {
		AppointService ap = new AppointService();
		Map<String, Object> recordInfo = (Map<String, Object>) contentMap
				.get("AppointRecordInfo");

		String OrganAppointID = (String)(recordInfo.get("OrganAppointID"));
		String OrganId = (String)(recordInfo.get("OrganId"));
		Integer organId = Integer.valueOf(OrganId);
		HisAppointRecord app = new HisAppointRecord();

		String OperateDate = (String) (recordInfo.get("OperateDate"));
		String DepartCode = (String) (recordInfo.get("DepartCode"));
		String DepartName = (String) (recordInfo.get("DepartName"));
		String PatientType = (String) (recordInfo.get("PatientType"));
		String WorkDate = (String) (recordInfo.get("WorkDate"));
		String PatientName = (String) (recordInfo.get("PatientName"));
		String OrganSourceID = (String) (recordInfo.get("OrganSourceID"));
		String OrganSchdulingID = (String) (recordInfo.get("OrganSchdulingID"));
		String OrderNum = (String) (recordInfo.get("OrderNum"));
		String Mobile = (String) (recordInfo.get("Mobile"));
		String DoctorID = (String) (recordInfo.get("DoctorID"));
		String EndTime = (String) (recordInfo.get("EndTime"));
		String StartTime = (String) (recordInfo.get("StartTime"));
		String WorkType = (String) (recordInfo.get("WorkType"));
		String CredentialsType = (String) (recordInfo.get("CredentialsType"));

		Integer orderNum = null;
		if(!StringUtils.isEmpty(OrderNum)){
			orderNum = Integer.parseInt(OrderNum);
		}
		Date workDate = getCurrentDate(WorkDate, "yyyy-MM-dd");
		//省中WorkDate有值 邵逸夫StartTime有值
		if (null == workDate) {
			workDate = getCurrentDate(StartTime, "yyyy-MM-dd");
		}
		app.setAppointDepartId(DepartCode);
		app.setAppointDepartName(DepartName);
		app.setPatientType(PatientType);
		app.setWorkDate(workDate);
		app.setPatientName(PatientName);
		app.setOrganSchedulingId(OrganSchdulingID);
		app.setOrderNum(orderNum);
		if (!StringUtils.isEmpty(DoctorID) && !DoctorID.contains("*")) {
			app.setDoctorId(Integer.parseInt(DoctorID));
		}
		app.setEndTime(getCurrentDate(EndTime, "hh:mm"));
		app.setStartTime(getCurrentDate(StartTime, "hh:mm"));
		Integer workType = null;
		if (StringUtils.isNotEmpty(WorkType)) {
			workType = Integer.parseInt(WorkType);
		}
		//邵逸夫WorkType 没值
		if (null == workType) {
			Date startTime = getCurrentDate(StartTime, "yyyy-MM-dd HH:mm:ss");
			workType = getWorkType(startTime);
		}
		/**
		 * OrganSourceId 是我们平台自己拼接的
		 */
		AppointSource source = new AppointSource();
		source.setWorkType(workType);
		source.setOrganSchedulingId(OrganSchdulingID);
		source.setOrderNum(orderNum);
		source.setWorkDate(workDate);

		app.setOrganSourceId(getOrganSourceIDWithWorkType(source));
		app.setWorkType(workType);
		app.setOrganId(organId);
		app.setOrganAppointId(OrganAppointID);
		app.setType(type);// //1 释放的号源 2已用的号源
		app.setNumber(1);
		ap.updateSource(app);
		logger.info("预约"+("1".equals(type)?"取消":"")+"事件处理完成");
	}

	private Date getCurrentDate(String dateStr, String format) {
		Date currentTime = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat(format);
		try {
			currentTime = formatter.parse(dateStr);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			return null;
		}
		return currentTime;
	}

}
