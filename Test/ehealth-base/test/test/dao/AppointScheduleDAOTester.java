package test.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.bus.dao.AppointScheduleDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointSchedule;
import eh.utils.DateConversion;

public class AppointScheduleDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static AppointScheduleDAO dao;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext
				.getBean("appointScheduleDAO", AppointScheduleDAO.class);
	}

	public void testTimeC() {
		dao.testTimeC();
	}

	/**
	 * 排班列表查询
	 * 
	 * @author luf
	 */
	public void testFindDoctorAndScheduleByThree() {
		System.out.println(JSONUtils.toString(dao.findByDoctorId(1180)));
		/*
		 * int organId = 1; String profession = "02"; int department = 70; int
		 * start = 0; String name = "王宁"; List<Doctor> ds =
		 * dao.findDoctorAndScheduleByThree(organId, profession, department,
		 * name, start); System.out.println(JSONUtils.toString(ds));
		 * System.out.println(ds.get(0).getAppointSchedules().size());
		 * System.out.println(ds.size());
		 */
	}

	/**
	 * 三条件查询医生排班列表（供 排班列表查询）
	 * 
	 * @author luf
	 */
	public void testfindSchedByThreeOld() {
		int organId = 1;
		List<AppointSchedule> ds = dao.findSchedByThreeOld(1177, organId);
		System.out.println(JSONUtils.toString(ds));
		List<AppointSchedule> dss = dao.findSchedByThree(1177, organId);
		System.out.println(JSONUtils.toString(dss));
	}

	/**
	 * 四条件搜索医生列表服务
	 * 
	 * @author luf
	 */
	public void testFindDocsByFour() {
		int organId = 1;
		int department = 70;
		String profession = "02";
		String name = "周";
		Integer start = 0;
		List<Doctor> ds = dao.findDocsByFour(organId, department, profession,
				name, start);
		System.out.println(JSONUtils.toString(ds));
		System.out.println(ds.size());
	}

	/**
	 * 停/复班服务
	 * 
	 * @author luf
	 */
	public void testUpdateUseFlagStopOrNot() {
		List<Integer> is = new ArrayList<Integer>();
		is.add(1);
		is.add(null);
		is.add(-1);
		is.add(0);
		is.add(19);
		int useFlag = 1;
		Integer i = dao.updateUseFlagStopOrNot(is, useFlag);
		System.out.println(i);
	}

	/**
	 * 修改单条排班信息
	 * 
	 * @author luf
	 */
	public void testUpdateOneSchedule() {
		AppointSchedule a = new AppointSchedule();
		a.setScheduleId(1);
		// a.setDoctorId(40);
		// a.setDepartId(70);
		// a.setOrganId(1);
		a.setSourceNum(3);
		System.out.println(JSONUtils.toString(a));
		Boolean b = dao.updateOneSchedule(a);
		System.out.println(b);
	}

	/**
	 * 删除单条/多条排班
	 * 
	 * @author luf
	 */
	public void testDeleteOneOrMoreSchedule() {
		List<Integer> is = new ArrayList<Integer>();
		is.add(18);
		is.add(19);
		is.add(null);
		is.add(-1);
		is.add(0);
		System.out.println(JSONUtils.toString(is));
		int i = dao.deleteOneOrMoreSchedule(is);
		System.out.println(i);
	}

	/**
	 * 新增排班信息
	 * 
	 * @author luf
	 */
	public void testAddOneSchedule() {
		AppointSchedule a = new AppointSchedule();
		a.setAppointDepart("54");
		a.setClinicType(1);
		a.setDepartId(70);
		a.setDoctorId(40);
		a.setEndTime(DateConversion.getCurrentDate("2015-10-22 12:12:00",
				"yyyy-MM-dd HH:mm:ss"));
		a.setStartTime(DateConversion.getCurrentDate("2015-10-22 12:12:00",
				"yyyy-MM-dd HH:mm:ss"));
		// a.setLastGenDate(null);
		a.setMaxRegDays(8);
		a.setOrganId(1);
		// a.setScheduleId(1);
		a.setSourceNum(5);
		a.setSourceType(1);
		a.setTelMedFlag(0);
		a.setTelMedType(0);
		a.setUseFlag(0);
		a.setWeek(7);
		a.setAppointDepart("36A");
		a.setWorkAddr("sadgsdfdfsgr");
		a.setWorkType(0);
		System.out.println(JSONUtils.toString(a));
		Integer i = dao.addOneSchedule(a);
		System.out.println(i);
	}

	/**
	 * 获取所有有效/无效排班列表
	 * 
	 * @author luf
	 */
	public void testFindAllEffectiveSchedule() {
		int useFlag = 0;
		List<AppointSchedule> as = dao.findAllEffectiveSchedule(useFlag);
		System.out.println(JSONUtils.toString(as));
		System.out.println(as.size());
	}

	/**
	 * 三条件查询时间段
	 * 
	 * @author luf
	 */
	public void testFindTimeSlotByThree() {
		Date now = DateConversion.getCurrentDate("2015-10-25", "yyyy-MM-dd");
		int max = 8;
		int week = 7;
		List<Date> ds = dao.findTimeSlotByThree(now, max, week);
		System.out.println(JSONUtils.toString(ds));
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
	public void testFindDocsWithRange() {
		int organId = 1;
		Integer department = null;
		String name = "华";
		int range = 1;
		int start = 0;
		System.out.println(JSONUtils.toString(dao.findDocsWithRange(organId,
				department, name, range, start)));
	}

	public void testFindDoctorAndScheduleWithRange() {
		int organId = 1;
		Integer department = null;
		String name = "振";
		int range = 1;
		int start = 0;
		System.out.println(JSONUtils.toString(dao
				.findDoctorAndScheduleWithRange(organId, department, name,
						range, start)));
	}
}
