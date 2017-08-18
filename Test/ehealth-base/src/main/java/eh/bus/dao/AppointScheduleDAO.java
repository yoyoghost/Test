package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.Organ;
import eh.entity.bus.AppointDepart;
import eh.entity.bus.AppointSchedule;
import eh.entity.bus.SourceAllot;
import eh.op.auth.service.SecurityService;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.text.Collator;
import java.util.*;

public abstract class AppointScheduleDAO extends
		HibernateSupportDelegateDAO<AppointSchedule> {
	private static final Logger log = Logger.getLogger(AppointSourceDAO.class);

	public AppointScheduleDAO() {
		super();
		this.setEntityName(AppointSchedule.class.getName());
		this.setKeyField("scheduleId");
	}

	/**
	 * 排班列表查询
	 * 
	 * @author luf
	 * @param organId
	 *            机构编号
	 * @param profession
	 *            专科代码
	 * @param department
	 *            科室代码
	 * @param name
	 *            医生姓名
	 * @param start
	 *            分页起始位置 --从0开始
	 * @return List<Doctor> 医生列表，内含排班列表
	 */
	@RpcService
	public List<Doctor> findDoctorAndScheduleByThree(int organId,
			String profession, int department, String name, int start) {
		log.info("========>>>>>> AppointScheduleDAO  排班列表查询  <<<<<=======  "
				+ "  findDoctorAndScheduleByThree  >>>>>" + "organId="
				+ organId + ";profession=" + profession + ";department="
				+ department + ";start=" + start);
		List<Doctor> docs = this.findDocsByFour(organId, department,
				profession, name, start);
		List<Doctor> target = new ArrayList<Doctor>();
		for (Doctor d : docs) {
			List<AppointSchedule> appointSchedules = this.findSchedByThreeOld(
					d.getDoctorId(), organId);
			d.setAppointSchedules(appointSchedules);
			target.add(d);
		}
		return target;
	}

	/**
	 * 三条件查询医生排班列表服务
	 * 
	 * @author luf
	 * @param doctorId
	 *            医生内码
	 * @param organId
	 *            机构编码
	 * @return List<AppointSchedule> 排班列表（时间正序）
	 */
	@RpcService
	@DAOMethod(sql = "From AppointSchedule where doctorId=:doctorId and "
			+ "organId=:organId order by week,startTime")
	public abstract List<AppointSchedule> findSchedByThreeOld(
			@DAOParam("doctorId") int doctorId, @DAOParam("organId") int organId);

	/**
	 * 三条件查询医生排班列表服务(加挂号科室名称)
	 * 
	 * @author luf
	 * @param doctorId
	 *            医生内码
	 * @param organId
	 *            机构编码
	 * @return List<AppointSchedule> 排班列表（时间正序）
	 */
	@RpcService
	public List<AppointSchedule> findSchedByThree(int doctorId, int organId) {
		List<AppointSchedule> schedules = this.findSchedByThreeOld(doctorId,
				organId);
		for (AppointSchedule as : schedules) {
			AppointDepartDAO departDAO = DAOFactory
					.getDAO(AppointDepartDAO.class);
			AppointDepart depart = departDAO.getByOrganIDAndAppointDepartCode(
					as.getOrganId(), as.getAppointDepart());
			if (depart != null) {
				as.setAppointDepartName(depart.getAppointDepartName());
			}
		}
		return schedules;
	}

	/**
	 * 四条件搜索医生列表服务
	 * 
	 * @author luf
	 * @param organId
	 *            机构编号
	 * @param department
	 *            科室代码
	 * @param profession
	 *            专科代码
	 * @param name
	 *            医生姓名 --可空
	 * @param start
	 *            分页起始位置 --可空（空则不分页），不为空时从0开始
	 * @return List<Doctor> 医生列表
	 */
	@RpcService
	public List<Doctor> findDocsByFour(final int organId, final int department,
			final String profession, final String name, final Integer start) {
		log.info("===== AppointScheduleDAO 四条件搜索医生列表服务 ===== findDocsByFour >>>"
				+ "organId="
				+ organId
				+ ";department="
				+ department
				+ ";profession="
				+ profession
				+ ";name="
				+ name
				+ ";start="
				+ start);
		if (StringUtils.isEmpty(profession)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"profession is required!");
		}
		HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws DAOException {
				StringBuilder hql = new StringBuilder(
						"SELECT d FROM Employment e,Doctor d WHERE "
								+ "e.organId=:organId AND e.department=:department AND "
								+ "d.profession=:profession AND e.doctorId=d.doctorId and d.status=1");
				if (!StringUtils.isEmpty(name)) {
					hql.append(" and d.name like :name");
				}
				Query q = ss.createQuery(hql.toString());
				q.setParameter("organId", organId);
				q.setParameter("department", department);
				q.setParameter("profession", profession);
				if (!StringUtils.isEmpty(name)) {
					q.setParameter("name", "%" + name + "%");
				}
				if (start != null) {
					q.setFirstResult(start);
					q.setMaxResults(10);
				}
				setResult(q.list());
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 停/复班服务
	 * 
	 * @author luf
	 * @param ids
	 *            需修改的排班序号列表
	 * @param useFlag
	 *            启用标志 --0正常 1停班
	 * @return Integer 更新成功的条数
	 */
	@RpcService
	public Integer updateUseFlagStopOrNot(List<Integer> ids, int useFlag) {
		log.info("停/复班服务 AppointScheduleDAO====> updateUseFlagStopOrNot <==="
				+ "ids:" + JSONUtils.toString(ids) + ";useFlag:" + useFlag);

		int count = 0;
		String doctorMessage = null;
		for (Integer id : ids) {
			if (id == null) {
				continue;
			}
			AppointSchedule ad;
			ad = get(id);
			if (ad == null) {
				continue;
			}
			ad.setUseFlag(useFlag);
			if (doctorMessage == null)
			{
				doctorMessage = this.getDoctorInfoBySchedule(ad);
			}
			update(ad);
			count++;
		}
		BusActionLogService.recordBusinessLog("医生排班", JSONUtils.toString(ids), "AppointSchedule",
				doctorMessage + "的排班"+JSONUtils.toString(ids)+ (useFlag == 1 ? "停班" : "取消停班"));
		return count;
	}

	private String getDoctorInfoBySchedule(AppointSchedule a)
	{
		String doctorMessage = null;
		if (a != null)
		{
			Doctor doctor = null;
			Organ organ = null;
			DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
			doctor = doctorDao.getByDoctorId(a.getDoctorId());
			OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
			organ = organDao.getByOrganId(a.getOrganId());
			String deptName = null;
			if ( doctor != null)
			{
				EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
				Employment e =  employmentDAO.getDeptNameByDoctorIdAndOrganId(doctor.getDoctorId(), doctor.getOrgan());
				if (e != null)
				{
					deptName = e.getDeptName();
				}
			}
			if (organ != null &&  doctor != null)
			{
				if (deptName != null)
				{
					doctorMessage = organ.getShortName() + " " +  deptName + " "+ doctor.getName();
				}
				else
				{
					doctorMessage = organ.getShortName() + " " + doctor.getName();
				}
			}
		}
		return doctorMessage;
	}

	/**
	 * 修改单条排班信息
	 * 
	 * @author luf
	 * @param a
	 *            需修改的排版信息组成的对象
	 * @return Boolean 是否修改成功
	 */
	@RpcService
	public Boolean updateOneSchedule(AppointSchedule a) {
		log.info("修改单条排班信息 AppointScheduleDAO====> updateOneSchedule <==="
				+ "a:" + JSONUtils.toString(a));

		if (a == null || a.getScheduleId() == null) {
			return false;
		}
		Integer id = a.getScheduleId();
		AppointSchedule ad = get(id);
		if (ad == null) {
			return false;
		}
        BeanUtils.map(a, ad);

		Date ymd = DateConversion.getCurrentDate("1990-01-01", "yyyy-MM-dd");
		if (ad.getStartTime() != null) {
			String startTime = DateConversion.getDateFormatter(
					ad.getStartTime(), "HH:mm:ss");
			Date s = DateConversion.getDateByTimePoint(ymd, startTime);
			ad.setStartTime(s);
		}
		if (ad.getEndTime() != null) {
			String endTime = DateConversion.getDateFormatter(ad.getEndTime(),
					"HH:mm:ss");
			Date e = DateConversion.getDateByTimePoint(ymd, endTime);
			ad.setEndTime(e);
		}
		update(ad);
		String doctorMessage = this.getDoctorInfoBySchedule(ad);
		BusActionLogService.recordBusinessLog("医生排班",a.getScheduleId().toString(),"CheckSchedule",
				doctorMessage + "的排班["+a.getScheduleId()+"]被更新");
		return true;
	}

	/**
	 * 删除单条/多条排班
	 * 
	 * @author luf
	 * @param ids
	 @RpcService
	 *            需删除的排班序号列表
	 * @return int 成功删除的条数
	 */
	@RpcService
	public int deleteOneOrMoreSchedule(List<Integer> ids) {
		log.info("删除单条/多条排班 AppointScheduleDAO====> deleteOneOrMoreSchedule <==="
				+ "ids:" + JSONUtils.toString(ids));
		int count = 0;
		String doctorMessage = null;
		for (Integer id : ids) {
			if (id == null) {
				continue;
			}
			AppointSchedule a = get(id);
			if (a == null) {
				continue;
			}
			if (doctorMessage == null)
			{
				doctorMessage = this.getDoctorInfoBySchedule(a);
			}
			remove(id);
			count++;
		}
		BusActionLogService.recordBusinessLog("医生排班",JSONUtils.toString(ids),"AppointSchedule",
				doctorMessage + "的排班"+JSONUtils.toString(ids)+"被删除");
		return count;
	}

	/**
	 * 新增排班信息
	 * 
	 * @author luf
	 * @param a
	 *            新增的排班信息组成的对象
	 * @return Integer 新增成功返回排班序号
	 * @throws DAOException
	 */
	@RpcService
	public Integer addOneSchedule(AppointSchedule a) throws DAOException {
		log.info("新增排班信息 <============ addOneSchedule ===========> AppointSchedule a:"
				+ JSONUtils.toString(a));
		if (a == null || a.getDoctorId() == null || a.getDepartId() == null
				|| a.getOrganId() == null || a.getSourceNum() == null
				|| a.getSourceNum() <= 0 || a.getSourceType() == null
				|| a.getClinicType() == null || a.getTelMedFlag() == null
				|| a.getTelMedType() == null || a.getMaxRegDays() == null
				|| a.getMaxRegDays() <= 0 || a.getWeek() == null
				|| a.getWorkType() == null || a.getEndTime() == null
				|| a.getStartTime() == null || a.getUseFlag() == null
				|| a.getEndTime().before(a.getStartTime())
				|| StringUtils.isEmpty(a.getWorkAddr())
				|| StringUtils.isEmpty(a.getAppointDepart())) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"one or more parameter is required!");
		}
		Date ymd = DateConversion.getCurrentDate("1990-01-01", "yyyy-MM-dd");
		String startTime = DateConversion.getDateFormatter(a.getStartTime(),
				"HH:mm:ss");
		String endTime = DateConversion.getDateFormatter(a.getEndTime(),
				"HH:mm:ss");
		Date s = DateConversion.getDateByTimePoint(ymd, startTime);
		Date e = DateConversion.getDateByTimePoint(ymd, endTime);
		a.setStartTime(s);
		a.setEndTime(e);
		AppointSchedule ad = save(a);
		String doctorMessage = this.getDoctorInfoBySchedule(a);
		if (ad == null) {
			return null;
		}
		BusActionLogService.recordBusinessLog("医生排班", ad.getScheduleId().toString(), "AppointSchedule",
				doctorMessage + "新增排班[" + ad.getScheduleId().toString() + "]");
		return ad.getScheduleId();
	}

	/**
	 * 获取所有有效/无效排班列表
	 * 
	 * @author luf
	 * @param useFlag
	 *            启用标志 0正常，1停班
	 * @return List<AppointSchedule> 排班列表
	 */
	@RpcService
	public List<AppointSchedule> findAllEffectiveSchedule(final int useFlag) {
		HibernateStatelessResultAction<List<AppointSchedule>> action = new AbstractHibernateStatelessResultAction<List<AppointSchedule>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws DAOException {
				String hql = "From AppointSchedule where useFlag=:useFlag";
				Query q = ss.createQuery(hql);
				q.setParameter("useFlag", useFlag);
				List<AppointSchedule> as = q.list();
				setResult(as);
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 三条件查询时间段
	 * 
	 * @author luf
	 * @param now
	 *            入参当前日期
	 * @param max
	 *            最大预约天数
	 * @param week
	 *            周几
	 * @return List<Date> 时间段 --0从排班到号源的时间，1被生成的号源所在时间
	 */
	@RpcService
	public List<Date> findTimeSlotByThree(Date now, int max, int week) {
		week++;// 周日1..周六7
		if (week == 8) {
			week = 1;
		}
		Calendar c = Calendar.getInstance();
		c.setTime(now);
		c.add(Calendar.DAY_OF_WEEK, 1);// 不包括今天
		int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);// 周日1..周六7
		while (dayOfWeek != week) {
			c.add(Calendar.DAY_OF_WEEK, 1);
			dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
			if (dayOfWeek == week) {
				break;
			}
		}
		Date lastDate = new Date(c.getTimeInMillis());
		long dateTime = lastDate.getTime() - max * 1000L * 60 * 60 * 24;
		long nowTime = now.getTime();
		Date insertDate = now;
		if (dateTime > nowTime) {
			insertDate = new Date(dateTime);
		}
		List<Date> ds = new ArrayList<Date>();
		ds.add(insertDate);// 从排班到号源的时间
		ds.add(lastDate);// 被生成的号源所在时间
		return ds;
	}

	/**
	 * 日期调用服务（测试用）
	 * 
	 * @author luf
	 */
	public void testTimeC() {
//		Date startTime = new Date();
//		Date date = DateConversion.getFormatDate(startTime, "yyyy-MM-dd");
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.AppointScheduleDAO.java
	 * 
	 * @Title: findDoctorAndScheduleByThreeAndLimit
	 * 
	 * @Description: TODO 按条件查询医生及排班信息
	 * 
	 * @param @param organId
	 * @param @param profession
	 * @param @param department
	 * @param @param name
	 * @param @param start
	 * @param @param limit
	 * @param @return
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-11-30下午3:06:38
	 * 
	 * @return List<Doctor>
	 * 
	 * @throws
	 */

	@RpcService
	public List<Doctor> findDoctorAndScheduleByThreeAndLimit(int organId,
			String profession, int department, String name, int start,
			Integer limit) {
		log.info("========>>>>>> AppointScheduleDAO  排班列表查询  <<<<<=======  "
				+ "  findDoctorAndScheduleByThree  >>>>>" + "organId="
				+ organId + ";profession=" + profession + ";department="
				+ department + ";start=" + start);
		AppointDepartDAO appDept = DAOFactory.getDAO(AppointDepartDAO.class);

		List<Doctor> docs = this.findDocsByFourByLimit(organId, department,
				profession, name, start, limit);
		List<Doctor> target = new ArrayList<Doctor>();
		for (Doctor d : docs) {
			List<AppointSchedule> appointSchedules = this.findSchedByThreeOld(
					d.getDoctorId(), organId);
			if (appointSchedules != null) {
				for (AppointSchedule schedule : appointSchedules) {
					AppointDepart dept = appDept
							.getByOrganIDAndAppointDepartCode(
									schedule.getOrganId(),
									schedule.getAppointDepart());// 获取挂号科室名称
					if (dept != null) {
						schedule.setAppointDepartName(dept
								.getAppointDepartName());
					}
				}
			}
			d.setAppointSchedules(appointSchedules);
			target.add(d);
		}
		return target;
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.AppointScheduleDAO.java
	 * 
	 * @Title: findDoctorAndScheduleByThreeZ
	 * 
	 * @Description: TODO 修改了程序中查找医生的方法
	 * 
	 * @param organId
	 *            机构代码
	 * @param profession
	 *            专科代码
	 * @param department
	 *            科室代码
	 * @param start
	 * @param limit
	 * @return
	 * 
	 * @author Zhongzx
	 * 
	 * @Date 2015-12-28下午4:39:57
	 * 
	 * @return List<Doctor>
	 * 
	 * @throws
	 */
	@RpcService
	public List<Doctor> findDoctorAndScheduleByThreeZ(Integer organId,
			String profession, Integer department) {
		log.info("========>>>>>> AppointScheduleDAO  排班列表查询  <<<<<=======  "
				+ "  findDoctorAndScheduleByThreeZ  >>>>>" + "organId="
				+ organId + ";profession=" + profession + ";department="
				+ department);
		AppointDepartDAO appDept = DAOFactory.getDAO(AppointDepartDAO.class);
		DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
		List<Doctor> docs = docDao.findDoctorByThree(organId, profession,
				department);
		List<Doctor> target = new ArrayList<Doctor>();
		for (Doctor d : docs) {
			List<AppointSchedule> appointSchedules = this.findSchedByThreeOld(
					d.getDoctorId(), organId);
			if (appointSchedules != null) {
				for (AppointSchedule schedule : appointSchedules) {
					AppointDepart dept = appDept
							.getByOrganIDAndAppointDepartCode(
									schedule.getOrganId(),
									schedule.getAppointDepart());// 获取挂号科室名称
					if (dept != null) {
						schedule.setAppointDepartName(dept
								.getAppointDepartName());
					}
				}
			}
			d.setAppointSchedules(appointSchedules);
			target.add(d);
		}
		return target;
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.AppointScheduleDAO.java
	 * 
	 * @Title: findDocsByFourByLimit
	 * 
	 * @Description: TODO 查询符合条件的医生列表（提供给妇保）
	 * 
	 * @param organId
	 *            机构编号
	 * @param department
	 *            科室代码
	 * @param profession
	 *            专科代码
	 * @param name
	 *            医生姓名 --可空
	 * @param start
	 *            分页起始位置 --可空（空则不分页），不为空时从0开始
	 * @param @param limit
	 * @param @return
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-11-30下午2:59:34
	 * 
	 * @return List<Doctor>
	 * 
	 * @throws
	 */
	@RpcService
	public List<Doctor> findDocsByFourByLimit(final int organId,
			final int department, final String profession, final String name,
			final Integer start, final Integer limit) {
		log.info("===== AppointScheduleDAO 四条件搜索医生列表服务 ===== findDocsByFour >>>"
				+ "organId="
				+ organId
				+ ";department="
				+ department
				+ ";profession="
				+ profession
				+ ";name="
				+ name
				+ ";start="
				+ start);
		if (StringUtils.isEmpty(profession)) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"profession is required!");
		}
		HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws DAOException {
				StringBuilder hql = new StringBuilder(
						"SELECT d FROM Employment e,Doctor d WHERE "
								+ "e.organId=:organId AND e.department=:department AND "
								+ "d.profession=:profession AND e.doctorId=d.doctorId and d.status=1");
				if (!StringUtils.isEmpty(name)) {
					hql.append(" and d.name like :name");
				}
				Query q = ss.createQuery(hql.toString());
				q.setParameter("organId", organId);
				q.setParameter("department", department);
				q.setParameter("profession", profession);
				if (!StringUtils.isEmpty(name)) {
					q.setParameter("name", "%" + name + "%");
				}
				if (start != null) {
					q.setFirstResult(start);
				}
				if (limit != null) {
					q.setMaxResults(limit);
				}

				setResult(q.list());
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.AppointScheduleDAO.java
	 * 
	 * @Title: findDocsByFourByLimitZ 修改自findDocsByFourByLimit
	 * 
	 * @Description: TODO 四条件查询医生 科室、专科、姓名三个条件都可选填
	 * 
	 * @param organId
	 *            机构编号
	 * @param department
	 *            科室代码 可空
	 * @param profession
	 *            专科代码 可空
	 * @param name
	 *            医生姓名 可空
	 * @param start
	 *            分页起始位置 空则不分页
	 * @param limit
	 * @return
	 * 
	 * @author Zhongzx
	 * 
	 * @Date 2016-1-21上午11:06:36
	 * 
	 * @return List<Doctor>
	 * 
	 * @throws
	 */
	@RpcService
	public List<Doctor> findDocsByFourByLimitZ(final Integer organId,
			final Integer department, final String profession,
			final String name, final Integer start, final Integer limit) {
		log.info("===== AppointScheduleDAO 四条件搜索医生列表服务 ===== findDocsByFour >>>"
				+ "organId="
				+ organId
				+ ";department="
				+ department
				+ ";profession="
				+ profession
				+ ";name="
				+ name
				+ ";start="
				+ start);
		if (null == organId) {
			throw new DAOException(DAOException.VALUE_NEEDED,
					"organId is required!");
		}
		HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws DAOException {
				StringBuilder hql = new StringBuilder(
						"SELECT d FROM Employment e,Doctor d WHERE "
								+ "e.organId=:organId AND e.doctorId=d.doctorId and d.status=1");
				if (null != department) {
					hql.append(" and e.department=:department");
				}
				if (!StringUtils.isEmpty(profession)) {
					hql.append(" and d.profession=:profession");
				}
				if (!StringUtils.isEmpty(name)) {
					hql.append(" and d.name like :name");
				}
				Query q = ss.createQuery(hql.toString());
				q.setParameter("organId", organId);
				if (null != department) {
					q.setParameter("department", department);
				}
				if (!StringUtils.isEmpty(profession)) {
					q.setParameter("profession", profession);
				}
				if (!StringUtils.isEmpty(name)) {
					q.setParameter("name", "%" + name + "%");
				}
				if (start != null) {
					q.setFirstResult(start);
				}
				if (limit != null) {
					q.setMaxResults(limit);
				}
				setResult(q.list());
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		List<Doctor> list = action.getResult();
		final Collator collator = Collator.getInstance(java.util.Locale.CHINA); // collator
																				// 实现本地语言排序
		// 实现本地语言排序
		Collections.sort(list, new Comparator<Doctor>() {
			@Override
			public int compare(Doctor d1, Doctor d2) {
				// 正序
				return collator.compare(d1.getName(), d2.getName());
			}
		});
		return list;
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.AppointScheduleDAO.java
	 * 
	 * @Title: addOneScheduleAndSourceAllot
	 * 
	 * @Description: TODO增加排班及号源生成规则
	 * 
	 * @param @param a
	 * @param @return
	 * @param @throws DAOException
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-11-30下午4:48:27
	 * 
	 * @return Integer
	 * 
	 * @throws
	 */
	@RpcService
	public Integer addOneScheduleAndSourceAllot(AppointSchedule a,
			List<SourceAllot> allots) throws DAOException {
		if (a == null || a.getDoctorId() == null || a.getDepartId() == null
				|| a.getOrganId() == null || a.getSourceNum() == null
				|| a.getSourceNum() <= 0 || a.getSourceType() == null
				|| a.getClinicType() == null || a.getTelMedFlag() == null
				|| a.getTelMedType() == null || a.getMaxRegDays() == null
				|| a.getMaxRegDays() <= 0 || a.getWeek() == null
				|| a.getWorkType() == null || a.getEndTime() == null
				|| a.getStartTime() == null || a.getUseFlag() == null
				|| a.getEndTime().before(a.getStartTime())
				|| StringUtils.isEmpty(a.getWorkAddr())
				|| StringUtils.isEmpty(a.getAppointDepart()) || allots == null
				|| allots.size() <= 0) {
			return null;
		}
		Date ymd = DateConversion.getCurrentDate("1990-01-01", "yyyy-MM-dd");
		String startTime = DateConversion.getDateFormatter(a.getStartTime(),
				"HH:mm:ss");
		String endTime = DateConversion.getDateFormatter(a.getEndTime(),
				"HH:mm:ss");
		Date s = DateConversion.getDateByTimePoint(ymd, startTime);
		Date e = DateConversion.getDateByTimePoint(ymd, endTime);
		a.setStartTime(s);
		a.setEndTime(e);
		AppointSchedule ad = save(a);
		if (ad == null || ad.getScheduleId() == null) {
			return null;
		}
		// 保存号源生成规则
		SourceAllotDAO allotDao = DAOFactory.getDAO(SourceAllotDAO.class);
		for (SourceAllot allot : allots) {
			allot.setScheduleId(ad.getScheduleId());
			allotDao.save(allot);
		}

		return ad.getScheduleId();
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.AppointScheduleDAO.java
	 * 
	 * @Title: updateOneScheduleAndSourceAllot
	 * 
	 * @Description: TODO修改单条排班记录以及对应的号源生成规则
	 * 
	 * @param @param a
	 * @param @param allots
	 * @param @return
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-11-30下午4:56:29
	 * 
	 * @return Boolean
	 * 
	 * @throws
	 */
	@RpcService
	public Boolean updateOneScheduleAndSourceAllot(AppointSchedule a,
			List<SourceAllot> saveAllots, List<SourceAllot> delAllots) {
		if (!this.updateOneSchedule(a)) {// 更新排班表
			return false;
		}
		// 更新号源
		SourceAllotDAO allotDao = DAOFactory.getDAO(SourceAllotDAO.class);
		int scheduleId = a.getScheduleId();
		for (SourceAllot saveAllot : saveAllots) {
			if (saveAllot.getAllotId() == null) {
				saveAllot.setScheduleId(scheduleId);
				allotDao.save(saveAllot);
			} else {
				saveAllot.setScheduleId(scheduleId);
				allotDao.update(saveAllot);
			}
		}
		for (SourceAllot delAllot : delAllots) {
			if (delAllot.getAllotId() != null) {
				allotDao.deleteByAllotId(delAllot.getAllotId());
			}
		}
		return true;
	}

	/**
	 * 
	 * 
	 * @Class eh.bus.dao.AppointScheduleDAO.java
	 * 
	 * @Title: deleteOneOrMoreSchedule
	 * 
	 * @Description: TODO删除排班记录
	 * 
	 * @param @param ids
	 * @param @return
	 * 
	 * @author AngryKitty
	 * 
	 * @Date 2015-12-1上午10:17:23
	 * 
	 * @return int
	 * 
	 * @throws
	 */
	@RpcService
	public int deleteOneOrMoreScheduleAndAllot(List<Integer> ids) {
		log.info("删除单条/多条排班 AppointScheduleDAO====> deleteOneOrMoreSchedule <==="
				+ "ids:" + JSONUtils.toString(ids));
		int count = 0;
		for (Integer id : ids) {
			if (id == null) {
				continue;
			}
			AppointSchedule a = get(id);
			if (a == null) {
				continue;
			}
			remove(id);
			// 删除号源生成规则
			SourceAllotDAO allotDao = DAOFactory.getDAO(SourceAllotDAO.class);
			allotDao.deleteByScheduleId(id);
			count++;
		}
		return count;
	}

	@RpcService
	@DAOMethod
	public abstract List<AppointSchedule> findByDoctorId(Integer doctorId);

	/**
	 * 查询排班医生列表接口(添加范围)
	 * 
	 * eh.bus.dao
	 * 
	 * @author luf 2016-3-4
	 * 
	 * @param organId
	 *            机构内码
	 * @param department
	 *            科室代码
	 * @param name
	 *            医生姓名
	 * @param range
	 *            范围- 0只查无排班医生，1只查有排班医生，-1查询全部医生
	 * @param start
	 *            分页起始位置
	 * @return List<Doctor>
	 */
	@RpcService
	public List<Doctor> findDoctorAndScheduleWithRange(int organId,
			Integer department, String name, int range, int start) {
		log.info("========>>>>>> AppointScheduleDAO  查询排班医生列表接口(添加范围)  <<<<<=======  "
				+ "  findDoctorAndScheduleWithRange  >>>>>"
				+ "organId="
				+ organId + ";department=" + department + ";start=" + start);
		List<Doctor> docs = this.findDocsWithRange(organId, department, name,
				range, start);
		List<Doctor> target = new ArrayList<Doctor>();
		for (Doctor d : docs) {
			List<AppointSchedule> appointSchedules = this.findSchedByThreeOld(
					d.getDoctorId(), organId);
			d.setAppointSchedules(appointSchedules);
			target.add(d);
		}
		return target;
	}


	/**
	 * 查询排班医生列表接口(添加范围)
	 *
	 * eh.bus.dao
	 *
	 *
	 * 运营平台（权限改造）
	 *
	 *
	 */
	@RpcService
	public List<Doctor> findDoctorAndScheduleWithRangeForOp(int organId,
													   Integer department, String name, int range, int start) {
		Set<Integer> o = new HashSet<Integer>();
		o.add(organId);
		if (!SecurityService.isAuthoritiedOrgan(o)) {
			return null;
		}
		List<Doctor> docs = this.findDocsWithRange(organId, department, name,
				range, start);
		List<Doctor> target = new ArrayList<Doctor>();
		for (Doctor d : docs) {
			List<AppointSchedule> appointSchedules = this.findSchedByThreeOld(
					d.getDoctorId(), organId);
			d.setAppointSchedules(appointSchedules);
			target.add(d);
		}
		return target;
	}

	/**
	 * 三条件查询医生排班列表服务(加挂号科室名称)
	 *
	 * 运营平台（权限改造）
	 *
	 */
	@RpcService
	public List<AppointSchedule> findSchedByThreeForOp(int doctorId, int organId) {
		Set<Integer> o = new HashSet<Integer>();
		o.add(organId);
		if(!SecurityService.isAuthoritiedOrgan(o)){
			return null;
		}
		EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
		List<Employment> ems = employmentDAO.findByDoctorId(doctorId);
		if(ems==null||ems.isEmpty()){
			throw new DAOException("职业点信息缺失");
		}
		o = new HashSet<Integer>();
		for(Employment e:ems){
			o.add(e.getOrganId());
		}
		if(!SecurityService.isAuthoritiedOrgan(o)){
			return null;
		}
		return this.findSchedByThree(doctorId,organId);
	}

	/**
	 * 供 查询排班医生列表接口(添加范围) 调用
	 * 
	 * eh.bus.dao
	 * 
	 * @author luf 2016-3-4
	 * 
	 * @param organId
	 *            机构内码
	 * @param department
	 *            科室代码
	 * @param name
	 *            医生姓名
	 * @param range
	 *            范围- 0只查无排班医生，1只查有排班医生，-1查询全部医生
	 * @param start
	 *            分页起始位置
	 * @return List<Doctor>
	 */
	public List<Doctor> findDocsWithRange(final int organId,
			final Integer department, final String name, final int range,
			final int start) {
		HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws DAOException {
				StringBuilder hql = new StringBuilder(
						"SELECT distinct d FROM Employment e,Doctor d WHERE e.organId=:organId AND e.doctorId=d.doctorId and d.status=1");
				if (department != null) {
					hql.append(" AND e.department=:department");
				}
				if (!StringUtils.isEmpty(name)) {
					hql.append(" and d.name like :name");
				}
				switch (range) {
				case 0:
					hql.append(" and (select count(*) from AppointSchedule s where s.doctorId=d.doctorId)<=0");
					break;
				case 1:
					hql.append(" and (select count(*) from AppointSchedule s where s.doctorId=d.doctorId)>0");
					break;
				default:
					hql.append("");
				}
				Query q = ss.createQuery(hql.toString());
				q.setParameter("organId", organId);
				if (department != null) {
					q.setParameter("department", department);
				}
				if (!StringUtils.isEmpty(name)) {
					q.setParameter("name", "%" + name + "%");
				}
				q.setFirstResult(start);
				q.setMaxResults(10);
				setResult(q.list());
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	/**
	 * @param
	 * @param
	 * @return
	 * 取医生排班信息
	 */
	@RpcService
	public List<AppointSchedule> getScheduling(final String organID,  final String startTime, final String endTime){
//		List<AppointSchedule> las = Lists.newArrayList();
		log.info( " 查询医生排班服务"+ organID);
		HibernateStatelessResultAction<List<AppointSchedule>> action = new AbstractHibernateStatelessResultAction<List<AppointSchedule>>() {
			@SuppressWarnings("unchecked")
			public void execute(StatelessSession ss) throws DAOException {
				//StringBuilder sql = new StringBuilder( " FROM AppointSchedule WHERE OrganID="+organID+" AND ((DATE_FORMAT(StartTime,'%Y-%m-%d') <= '"+startTime+"'  AND DATE_FORMAT(EndTime,'%Y-%m-%d') >='"+endTime+"' and DATE_FORMAT(EndTime,'%Y-%m-%d') < '"+endTime+"') OR (DATE_FORMAT(StartTime,'%Y-%m-%d') >= '"+startTime+"' AND DATE_FORMAT(EndTime,'%Y-%m-%d') <='"+endTime+"') OR(DATE_FORMAT(StartTime,'%Y-%m-%d') > '"+startTime+"' and DATE_FORMAT(StartTime,'%Y-%m-%d') <= '"+startTime+"' AND DATE_FORMAT(EndTime,'%Y-%m-%d') >='"+endTime+"'))" );
				StringBuilder sql = new StringBuilder( "FROM AppointSchedule WHERE CONCAT(StartTime, EndTime) >= "+startTime+"  AND CONCAT(StartTime, EndTime) <= "+endTime);

				Query q = ss.createQuery(sql.toString());
			/*	q.setParameter("organID",organID);
				q.setParameter("startTime",startTime);
				q.setParameter("endTime",endTime);*/
				setResult(q.list());
			}
		};
		HibernateSessionTemplate.instance().executeReadOnly(action);
		return action.getResult();
	}

	@DAOMethod(limit = 0,sql = " select scheduleId from AppointSchedule where useFlag = 0 and telMedFlag =0 and organId=:organId")
	public abstract List<Integer> findScheduleIdByOrganId(@DAOParam("organId") Integer organId);





}
