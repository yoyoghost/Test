package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.entity.bus.AppointDepart;
import eh.entity.bus.AppointSource;
import eh.entity.bus.TempSchedule;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 
 * 临时排班
 * 
 * @author <a href="mailto:jianghc@easygroup.net.cn">jianghc</a>
 */
public abstract class TempScheduleDAO extends
		HibernateSupportDelegateDAO<TempSchedule> {


	public TempScheduleDAO() {
		super();
		this.setEntityName(TempSchedule.class.getName());
		this.setKeyField("scheduleId");
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.TempScheduleDAO.java
	 * 
	 * @Title: findTempsByWorkDateAndDoctorID
	 * 
	 * @Description: TODO 查询某个医生某天是否有临时排班记录
	 * 
	 * @param @return
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-12-1下午4:18:41
	 * 
	 * @return List<TempSchedule>
	 * 
	 * @throws
	 */
	@RpcService
	@DAOMethod(sql = " from TempSchedule where workDate =:workDate and doctorId=:doctorId and flag =1")
	public abstract List<TempSchedule> findTempsByWorkDateAndDoctorID(
			@DAOParam("workDate") Date workDate,
			@DAOParam("doctorId") Integer doctorId);

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.TempScheduleDAO.java
	 * 
	 * @Title: findTempsByDoctorIDAndFlag
	 * 
	 * @Description: TODO
	 * 
	 * @param @param doctorId
	 * @param @param flag
	 * @param @return
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-12-3下午1:02:27
	 * 
	 * @return List<TempSchedule>
	 * 
	 * @throws
	 */
	@RpcService
	@DAOMethod(sql = " from TempSchedule where  doctorId=:doctorId and flag =:flag")
	public abstract List<TempSchedule> findTempsByDoctorIDAndFlag(
			@DAOParam("doctorId") Integer doctorId,
			@DAOParam("flag") Boolean flag);
	
	/**
	 * 
	*
	* @Class eh.bus.dao.TempScheduleDAO.java
	*
	* @Title: findTempSchedsByDoctorIdAndFlag
	
	* @Description: TODO   两条件查找临时排班（增加挂号科室名称）
	
	* @param doctorId    医生代码
	* @param flag           临时排班状态
	* @return    
	
	* @author Zhongzx
	
	* @Date 2016-1-27下午5:29:37 
	
	* @return List<TempSchedule>   
	
	* @throws
	 */
	@RpcService
	public List<TempSchedule> findTempSchedsByDoctorIdAndFlag(Integer doctorId,Boolean flag){
		List<TempSchedule> temps = this.findTempsByDoctorIDAndFlag(doctorId, flag);
		AppointDepartDAO departDAO = DAOFactory.getDAO(AppointDepartDAO.class);
		for(TempSchedule t:temps){
			AppointDepart depart = departDAO.getByOrganIDAndAppointDepartCode(
					t.getOrganId(), t.getAppointDepart());
			if (depart != null) {
				t.setAppointDepartName(depart.getAppointDepartName());
			}
		}
		return temps;
	}
	/**
	 * 
	 * 
	 * @Class eh.bus.dao.TempScheduleDAO.java
	 * 
	 * @Title: findTempsByDoctorIDAndFlag
	 * 
	 * @Description: TODO
	 * 
	 * @param @param doctorId
	 * @param @param flag
	 * @param @return
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-12-3下午1:02:27
	 * 
	 * @return List<TempSchedule>
	 * 
	 * @throws
	 */
	@RpcService
	@DAOMethod(sql = " from TempSchedule where  doctorId=:doctorId")
	public abstract List<TempSchedule> findTempsByDoctorID(
			@DAOParam("doctorId") Integer doctorId);

	/**
	 * 
	 * 
	 * @Description: TODO 根据workDate 和 startTime ， endTime 查询在某日某时间段内有交集的临时排班
	 * 
	 * @author Zhongzx
	 * 
	 * @param @param workDate 排班日期
	 * @param @param doctorId 医生代码
	 * @param @param startTime 号源起始时间
	 * @param @param endTime 号源结束时间
	 * @param @return
	 * 
	 * @Date 2015-12-2下午5:27:29
	 * 
	 * @return List<TempSchedule> 临时排班列表
	 */
	@RpcService
	@DAOMethod(sql = " from TempSchedule where workDate =:workDate and doctorId=:doctorId "
			+ "and ((startTime<=:startTime and endTime>:startTime) or (endTime>=:endTime and startTime<:endTime) or (startTime>:startTime and endTime<:endTime))")
	public abstract List<TempSchedule> findByWorkDateAndTimeAndDoctorId(
			@DAOParam("workDate") Date workDate,
			@DAOParam("doctorId") Integer doctorId,
			@DAOParam("startTime") Date startTime,
			@DAOParam("endTime") Date endTime);
	/**
	 * 
	*
	* @Description: TODO  查找某天的临时排班
	
	* @author Zhongzx
	
	* @param @param doctorId   医生编码
	* @param @param workDate  排班日期
	* @param @return    
	
	* @Date 2015-12-11下午2:00:06 
	
	* @return List<TempSchedule>   
	 */
	@RpcService
	@DAOMethod
	public abstract List<TempSchedule> findByDoctorIdAndWorkDate(Integer doctorId,Date workDate);
	
	/**
	 * 
	*
	* @Description: TODO   查询某天  工作/休息  的排班
	
	* @author Zhongzx
	
	* @param @param doctor    医生编码
	* @param @param workDate   排班日期
	* @param @param workFlag    工作标志
	* @param @return    
	
	* @Date 2015-12-11下午2:04:14 
	
	* @return List<TempSchedule>
	 */
	@RpcService
	@DAOMethod(sql = "from TempSchedule where doctorId=:doctorId and workDate=:workDate and workFlag=:workFlag")
	public abstract List<TempSchedule> findRestTemp(
			@DAOParam("doctorId")Integer doctorId,
			@DAOParam("workDate")Date workDate,
			@DAOParam("workFlag")Integer workFlag);
	
	
	/**
	 * 
	 * 
	 * @Description: TODO 增加单条临时排班
	 * 
	 * @author Zhongzx
	 * 
	 * @param @param t 临时排班对象
	 * @param @return
	 * 
	 * @Date 2015-12-2下午3:10:53
	 * 
	 * @return Integer 排班序号
	 */
	@RpcService
	public Integer addOneTempSchedule(TempSchedule t) {
		if (t == null || t.getDoctorId() == null || t.getDepartId() == null
				|| t.getWorkDate() == null || t.getOrganId() == null
				|| t.getWorkFlag() == null) {
			throw new DAOException(609, "排班信息不能为空或出错");
		}
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		List<AppointSource> alist = dao.findByWorkDateAndDoctorId(
				t.getWorkDate(), t.getDoctorId());
		// 如果当天有正常的号源，不可插入临时排班
		if (alist != null && alist.size() != 0) {
			throw new DAOException(609, "当天已有正常号源");
		}
		// 如果是工作，下面字段不可为空
		if (t.getWorkFlag() == 0) {
			if (t.getSourceNum() == null || t.getSourceNum() <= 0
					|| t.getSourceType() == null || t.getClinicType() == null
					|| t.getTelMedFlag() == null || t.getTelMedType() == null
					|| t.getWorkType() == null || t.getEndTime() == null
					|| t.getStartTime() == null
					|| t.getEndTime().before(t.getStartTime())
					|| StringUtils.isEmpty(t.getWordAddr())
					|| StringUtils.isEmpty(t.getAppointDepart())) {
				throw new DAOException(609, "排班信息不能为空或出错");
			}
			List<TempSchedule> rtemp = this.findRestTemp(t.getDoctorId(), t.getWorkDate(), 1);
			if(rtemp!=null&&rtemp.size()>0){
				throw new DAOException(609, "当天已有休息的临时排班");
			}
			// 把startTime 和 endTime 的 年月日设为1990-01-01
			Date ymd = DateConversion
					.getCurrentDate("1990-01-01", "yyyy-MM-dd");
			String startTime = DateConversion.getDateFormatter(
					t.getStartTime(), "HH:mm:ss");
			String endTime = DateConversion.getDateFormatter(t.getEndTime(),
					"HH:mm:ss");
			Date s = DateConversion.getDateByTimePoint(ymd, startTime);
			Date e = DateConversion.getDateByTimePoint(ymd, endTime);
			t.setStartTime(s);
			t.setEndTime(e);
			List<TempSchedule> tlist = this.findByWorkDateAndTimeAndDoctorId(
					t.getWorkDate(), t.getDoctorId(), t.getStartTime(),
					t.getEndTime());
			// 如果有时间交集的临时排班，不可插入临时排班
			if (tlist != null && tlist.size() != 0) {
				throw new DAOException(609, "与当天其他临时排班有时间交集");
			}
		}
		//如果是休息，要判断当天有没有临时排班（不管有没有生效）
		if(t.getWorkFlag()==1){
			List<TempSchedule> tlist = this.findByDoctorIdAndWorkDate(t.getDoctorId(), t.getWorkDate());
			if(tlist!=null&&tlist.size()>0){
				throw new DAOException(609,"当天有其他临时排班");
			}
		}
		t.setFlag(false);
		TempSchedule ts = save(t);
		if (ts == null || ts.getScheduleId() == null) {
			throw new DAOException(609, "插入不成功");
		}
		return ts.getScheduleId();
	}

	/**
	 * 
	 * 
	 * @Description: TODO 修改单条临时排班
	 * 
	 * @author Zhongzx
	 * 
	 * @param @param t
	 * @param @return
	 * 
	 * @Date 2015-12-2下午3:34:15
	 * 
	 * @return boolean true 修改成功
	 */
	@RpcService
	public boolean updateOneTempSchedule(TempSchedule t) {
		if (t == null || t.getDoctorId() == null || t.getDepartId() == null
				|| t.getWorkDate() == null || t.getScheduleId() == null
				|| t.getOrganId() == null || t.getWorkFlag() == null) {
			throw new DAOException(609, "排班信息不能为空或出错");
		}
		// 如果当天的排班已生效，不可修改
		Integer id = t.getScheduleId();
		TempSchedule ts = get(id);
		if (ts == null || ts.getFlag()) {
			return false;
		}
		AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
		List<AppointSource> alist = dao.findByWorkDateAndDoctorId(
				t.getWorkDate(), t.getDoctorId());
		// 如果当天有正常的号源，不可插入临时排班
		if (alist != null && alist.size() != 0) {
			throw new DAOException(609, "当天已有正常号源");
		}
		// 如果是工作，下面字段不可为空
		if (t.getWorkFlag() == 0) {
			if (t.getSourceNum() == null || t.getSourceNum() <= 0
					|| t.getSourceType() == null || t.getClinicType() == null
					|| t.getTelMedFlag() == null || t.getTelMedType() == null
					|| t.getWorkType() == null || t.getEndTime() == null
					|| t.getStartTime() == null
					|| t.getEndTime().before(t.getStartTime())
					|| StringUtils.isEmpty(t.getWordAddr())
					|| StringUtils.isEmpty(t.getAppointDepart())) {
				throw new DAOException(609, "排班信息不能为空或出错");
			}
			// 把startTime 和 endTime 的 年月日设为1990-01-01
			Date ymd = DateConversion
					.getCurrentDate("1990-01-01", "yyyy-MM-dd");
			String startTime = DateConversion.getDateFormatter(
					t.getStartTime(), "HH:mm:ss");
			String endTime = DateConversion.getDateFormatter(t.getEndTime(),
					"HH:mm:ss");
			Date s = DateConversion.getDateByTimePoint(ymd, startTime);
			Date e = DateConversion.getDateByTimePoint(ymd, endTime);
			t.setStartTime(s);
			t.setEndTime(e);
			List<TempSchedule> tlist = this.findByWorkDateAndTimeAndDoctorId(
					t.getWorkDate(), t.getDoctorId(), t.getStartTime(),
					t.getEndTime());
			// 如果有时间交集的临时排班，个数大于1或者个数等于1但不是要修改的临时排班，不可修改临时排班
			if ((tlist != null && tlist.size() > 1)
					|| (tlist != null && tlist.size() == 1 && tlist.get(0)
							.getScheduleId().intValue() != t.getScheduleId().intValue())) {
				throw new DAOException(609,"与当天其他临时排班有时间交集");
			}
		}
		// 如果改为休息，要判断当天有没有临时排班（不管有没有生效）个数大于1 或者等于1但不是要修改的临时排班，不可修改
		if (t.getWorkFlag() == 1) {
			List<TempSchedule> tlist = this.findByDoctorIdAndWorkDate(
					t.getDoctorId(), t.getWorkDate());
			if ((tlist != null && tlist.size() > 1)
					|| (tlist != null && tlist.size() == 1 && tlist.get(0)
							.getScheduleId().intValue() != t.getScheduleId().intValue())) {
				throw new DAOException(609, "当天有其他临时排班");
			}
		}
        BeanUtils.map(t, ts);
		update(ts);
		return true;
	}

	/**
	 * 
	 * 
	 * @Description: TODO 删除单条临时排班
	 * 
	 * @author Zhongzx
	 * 
	 * @param @param ids 排班序号
	 * @param @return
	 * 
	 * @Date 2015-12-2下午3:55:39
	 * 
	 * @return boolean true 删除成功
	 */
	@RpcService
	public boolean deleteOneTempSchedule(Integer id) {
		if (id == null) {
			return false;
		}
		TempSchedule t = get(id);
		// 如果当天的排班已生效，不可删除
		if (t == null || t.getFlag()) {
			return false;
		}
		remove(id);
		return true;
	}
	
	/**
	 * 
	*
	* @Description: TODO  查询所有未生效/已生效  工作/休息的临时排班
	
	* @author Zhongzx
	
	* @param @param flag   未生效/已生效 标志
	* @param @param workFlag  工作休息标志
	* @param @return    
	
	* @Date 2015-12-3下午1:59:54 
	
	* @return List<TempSchedule> 临时排班列表
	 */
	@RpcService
	@DAOMethod
	public abstract List<TempSchedule> findByFlagAndWorkFlag(boolean flag,Integer workFlag);
	
}
