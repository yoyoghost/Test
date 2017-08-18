package test.dao;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.context.Context;
import ctd.util.converter.support.StringToDate;
import eh.entity.base.Doctor;
import eh.entity.base.RelationLabel;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.mpi.RelationDoctorAndDoctor;
import eh.entity.mpi.SignPatient;
import eh.mpi.dao.RelationDoctorDAO;

public class RelationDoctorDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static RelationDoctorDAO relationDoctorDAO;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		relationDoctorDAO = appContext.getBean("relationDoctorDAO",
				RelationDoctorDAO.class);
	}

	public void testFindAllPatientByNameLikeLimitStatic() {
		System.out.println(JSONUtils.toString(relationDoctorDAO
				.findAllPatientByNameLikeLimitStatic(40, "张", 0)));
	}

	/**
	 * 测试
	 */
	public void testGetSignByMpiAndDocAndType(){
		String MpiId = "40283f814cda7aaf014cda7ac8200000";
		Integer doctorId = 1185;
		RelationDoctor relationDoctor = relationDoctorDAO.getSignByMpiAndDocAndType(MpiId, doctorId, 0);
		System.out.print(JSONUtils.toString(relationDoctor));
	}

	/**
	 * 测试插入数据
	 * 
	 * @throws DAOException
	 */
	public void testCreate() throws DAOException {

		int n4 = ThreadLocalRandom.current().nextInt(1, 99999);

		RelationDoctor relation = new RelationDoctor();
		relation.setDoctorId(14);
		relation.setMpiId("402881834b6d0cfc014b6d0d04f10000");
		if (n4 % 2 == 1) {
			relation.setFamilyDoctorFlag(true);
		} else {
			relation.setFamilyDoctorFlag(false);
		}

		relationDoctorDAO.save(relation);
	}

	/**
	 * 病人关注医生添加服务
	 * 
	 * @throws DAOException
	 */
	public void testAddRelationDoctor() throws DAOException {
		// int n4 = ThreadLocalRandom.current().nextInt(1, 99999);

		RelationDoctor relation = new RelationDoctor();
		relation.setDoctorId(2236);
		relation.setMpiId("2c9081814f45a880014f51184db800a8");
		relation.setObtainType(1);
		relation.setFamilyDoctorFlag(false);
		try {
			System.out.println(relationDoctorDAO.addRelationDoctor(relation));
		} catch (DAOException e) {

		}
	}

	/**
	 * 根据病人主键，医生主键查询
	 * 
	 * @throws DAOException
	 */
	public void testFindByMpiIdAndDoctorId() throws DAOException {
		String mpiId = "40283f814cda7ddd014cda7fc1ef000e";
		Integer doctorId = 1187;
		List<RelationDoctor> relation = relationDoctorDAO
				.findByMpiIdAndDoctorId(mpiId, doctorId);
		assertNotNull(relation);
		System.out.println(JSONUtils.toString(relation));
		System.out.println(JSONUtils.toString(relationDoctorDAO
				.getByMpiIdAndDoctorIdAndRelationType(mpiId, doctorId)));
	}

	/**
	 * 判断医生是否关注服务(正常情况)
	 * 
	 * @throws DAOException
	 */
	public void testGetRelationDoctorFlag() throws DAOException {
		String mpiId = "2c9081814cc3ad35014cc3e0361f0000";
		Integer doctorId = 40;
		boolean b = relationDoctorDAO.getRelationDoctorFlag(mpiId, doctorId);
		System.out.println(b);
	}

	/**
	 * 判断医生是否关注服务(传入doctorId为空)
	 * 
	 * @throws DAOException
	 */
	public void testGetRelationDoctorFlagFalse() throws DAOException {
		String mpiId = "402881834b6d0cfc014b6d0d04f10000";
		Integer doctorId = null;
		boolean b = relationDoctorDAO.getRelationDoctorFlag(mpiId, doctorId);
		assertFalse(b);
	}

	/**
	 * 判断医生是否关注服务(传入mpiId为空)
	 * 
	 * @throws DAOException
	 */
	public void testGetRelationDoctorFlagFalse2() throws DAOException {
		String mpiId = "";
		Integer doctorId = 13;
		boolean b = relationDoctorDAO.getRelationDoctorFlag(mpiId, doctorId);
		assertFalse(b);
	}

	/**
	 * 判断医生是否关注服务(传入mpiId为null)
	 * 
	 * @throws DAOException
	 */
	public void testGetRelationDoctorFlagFalse3() throws DAOException {
		String mpiId = null;
		Integer doctorId = 13;
		boolean b = relationDoctorDAO.getRelationDoctorFlag(mpiId, doctorId);
		assertFalse(b);
	}

	/**
	 * 删除关系
	 * 
	 * @throws DAOException
	 */
	public void testRemove() throws DAOException {
		relationDoctorDAO.remove(2);
	}

	/**
	 * 测试取消关注，若为家庭医生，不能取消关注
	 * 
	 * @throws DAOException
	 */
	public void testDeleteByRelationDoctorId() throws DAOException {
		try {
			String mpiId = "8a287a564d286405014d289ac6c00000";
			Integer doctorId = 40;
			System.out.println(relationDoctorDAO.deleteByMpiIdAndDoctorId(
					mpiId, doctorId));
		} catch (DAOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 判断是否医生签约病人服务(不是签约病人 familyDoctorFlag=false)
	 * 
	 * @throws DAOException
	 */
	public void testGetSignFlag() throws DAOException {
		try {
			String mpiId = "2c9081814cc3ad35014cc3e0361f0000";
			Integer doctorId = 40;
			boolean b = relationDoctorDAO.getSignFlag(mpiId, doctorId);
			System.out.println(b);
		} catch (DAOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 判断是否医生签约病人服务(当前日期在结束签约日期之后)
	 * 
	 * @throws DAOException
	 */
	public void testGetSignFlag2() throws DAOException {
		try {
			String mpiId = "402881834b71a24f014b71a254020000";
			Integer doctorId = 14;
			boolean b = relationDoctorDAO.getSignFlag(mpiId, doctorId);
			assertFalse(b);
		} catch (DAOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 判断是否医生签约病人服务(当前日期在开始签约日期之前)
	 * 
	 * @throws DAOException
	 */
	public void testGetSignFlag3() throws DAOException {
		try {
			String mpiId = "402881834b7172a4014b7172acb90000";
			Integer doctorId = 14;
			boolean b = relationDoctorDAO.getSignFlag(mpiId, doctorId);
			assertFalse(b);
		} catch (DAOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 判断是否医生签约病人服务(当前日期在 签约日期之间)
	 * 
	 * @throws DAOException
	 */
	public void testGetSignFlag4() throws DAOException {
		try {
			String mpiId = "402881834b6d0cfc014b6d0d04f10000";
			Integer doctorId = 14;
			boolean b = relationDoctorDAO.getSignFlag(mpiId, doctorId);
			assertTrue(b);
		} catch (DAOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 查询关注医生服务
	 * 
	 * @throws DAOException
	 */
	public void testFindRelationDoctorList() throws DAOException {
		String mpiId = "2c9081824cc3552a014cc3a9a0120002";
		List<Doctor> list = relationDoctorDAO.findRelationDoctorList(mpiId);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 查询签约医生服务
	 * 
	 * @throws DAOException
	 */
	public void testFindFamilyDoctorList() throws DAOException {
		String mpiId = "2c9081814cc3ad35014cc3e0361f0000";
		List<RelationDoctorAndDoctor> list = relationDoctorDAO
				.findFamilyDoctorList(mpiId);
		System.out.println(JSONUtils.toString(list.size()));
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 增加签约医生服务
	 * 
	 * @throws DAOException
	 */
	public void testAddFamilyDoctor() throws DAOException {
		try {
			String mpiId = "2c9081814cc3ad35014cc54fca420003";
			Integer doctorId = 40;
			Date relationDate = new Date();
			Date startDate = new StringToDate().convert("2016-01-01 11:49:46");
			Date endDate = new StringToDate().convert("2016-12-31 11:49:44");
			relationDoctorDAO.addFamilyDoctor(mpiId, doctorId, relationDate,
					startDate, endDate);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 获取签约病人列表的服务测试
	 * 
	 * @throws DAOException
	 */
	public void testFindSignPatient() throws DAOException {
		int doctorId = 1185;
		List<Patient> list = relationDoctorDAO.findSignPatient(doctorId);
		for (int i = 0; i < list.size(); i++) {
			System.out.println(list.size() + JSONUtils.toString(list.get(i)));
		}
	}

	/**
	 * 分页获取签约病人列表的服务测试
	 * 
	 * @throws DAOException
	 */
	public void testFindSignPatientByPage() throws DAOException {
		int doctorId = 40;
		List<Patient> list = relationDoctorDAO.findSignPatientWithPage(
				doctorId, 0);
		System.out.println(JSONUtils.toString(list));
		List<Patient> list2 = relationDoctorDAO.findSignPatientWithPage(
				doctorId, list.size());
		System.out.println(JSONUtils.toString(list2));
	}

	/**
	 * 分页获取签约病人列表的服务测试
	 * 
	 * @throws DAOException
	 */
	public void testFindSignPatientByStartAndLimit() throws DAOException {
		int doctorId = 1185;
		List<Patient> list = relationDoctorDAO.findSignPatientByStartAndLimit(
				doctorId, 0, 3);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按姓名模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByNameLike() {
		int doctorId = 1185;
		String patientName = "%丰%";
		List<Patient> list = relationDoctorDAO.findSignPatientByNameLike(
				doctorId, patientName, 0, 3);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按姓名模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByNameByStartAndLimit() {
		int doctorId = 1185;
		String patientName = "丰";
		List<Patient> list = relationDoctorDAO
				.findSignPatientByNameByStartAndLimit(doctorId, patientName, 0,
						3);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按姓名模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByNameWithPage() {
		int doctorId = 1185;
		String patientName = "丰";
		List<Patient> list = relationDoctorDAO.findSignPatientByNameWithPage(
				doctorId, patientName, 0);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按手机号模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByMobileLike() {
		int doctorId = 1185;
		String mobile = "%189580084%";
		List<Patient> list = relationDoctorDAO.findSignPatientByMobileLike(
				doctorId, mobile, 0, 3);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按手机号模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByMobileByStartAndLimit() {
		int doctorId = 1185;
		String mobile = "189580084";
		List<Patient> list = relationDoctorDAO
				.findSignPatientByMobileByStartAndLimit(doctorId, mobile, 0, 3);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按手机号模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByMobileWithPage() {
		int doctorId = 1185;
		String mobile = "189580084";
		List<Patient> list = relationDoctorDAO.findSignPatientByMobileWithPage(
				doctorId, mobile, 0);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按手机号模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByIdcardLike() {
		int doctorId = 1185;
		String idcard = "%330727%";
		List<Patient> list = relationDoctorDAO.findSignPatientByIdcardLike(
				doctorId, idcard, 0, 3);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按手机号模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByIdcardByStartAndLimit() {
		int doctorId = 1185;
		String idcard = "330727";
		List<Patient> list = relationDoctorDAO
				.findSignPatientByIdcardByStartAndLimit(doctorId, idcard, 0, 3);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按手机号模糊查询签约病人(分页)
	 * 
	 * @author ZX
	 * @date 2015-5-8 上午9:51:33
	 */
	public void testFindSignPatientByIdcardWithPage() {
		int doctorId = 1185;
		String idcard = "330727";
		List<Patient> list = relationDoctorDAO.findSignPatientByIdcardWithPage(
				doctorId, idcard, 0);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 保存签约病人信息
	 * 
	 * @author hyj
	 */
	public void testSaveSignPatient() {
		SignPatient s = new SignPatient();
		s.setPatientName("陈和凤");
		s.setCertId("330104193203182723");
		s.setCardId("2400852");
		s.setCardOrgan("470211136");
		s.setCardType("1");
		s.setDoctorId("1021");
		s.setOrganId("470211136");
		Date relationDate = new StringToDate().convert("2015-01-02 13:53:08");
		s.setRelationDate(relationDate);
		Date startDate = new StringToDate().convert("2015-01-01 00:00:00");
		s.setStartDate(startDate);
		Date endDate = new StringToDate().convert("2015-12-31 00:00:00");
		s.setEndDate(endDate);
		relationDoctorDAO.saveSignPatient(s);

	}

	public void testfindSignPatientByDoctorIdAndMpi() {
		String mpiid = "402881924f44d121014f44d1d6a70000";
		int doctorId = 1467;
		List<RelationDoctor> list = relationDoctorDAO
				.findSignPatientByDoctorIdAndMpi(mpiid, doctorId);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 签约剩余时间
	 * 
	 * @author luf
	 * @param endDate
	 *            签约结束时间
	 * @return String
	 */
	public void testRemainingRelationTime() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date endDate = null;
		try {
			endDate = sdf.parse("2016-09-16 09:00:00");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String remainningTime = relationDoctorDAO
				.remainingRelationTime(endDate);
		System.out.println(remainningTime);
	}

	public void testDateReductDate() {
		Date date = Context.instance().get("date.getToday", Date.class);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date endDate = null;
		try {
			endDate = sdf.parse("2015-10-10 19:20:00");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		long remainingTime = endDate.getTime() - date.getTime();// 微秒级别
		long day = remainingTime / (1000 * 60 * 60 * 24);
		long month = day / 30;
		long year = month / 12;
		long mday = day % 30;
		System.out.println(remainingTime);
		System.out.println("day=" + day);
		System.out.println("month=" + month);
		System.out.println("year=" + year);
		System.out.println(mday);
		System.out.println(date.getTime() - endDate.getTime());
		System.out.println("year=" + remainingTime
				/ (1000 * 60 * 60 * 24 * 30 * 12));
		System.out.println("month=" + remainingTime
				/ (1000 * 60 * 60 * 24 * 30));
		System.out.println("day=" + remainingTime / (1000 * 60 * 60 * 24));
	}

	/**
	 * 三页搜索条服务（姓名，身份证，手机号）
	 * 
	 * @author luf
	 * @param name
	 *            患者姓名 --可空
	 * @param idCard
	 *            患者身份证 --可空
	 * @param mobile
	 *            患者电话 --可空
	 * @param doctorId
	 *            当前医生内码 --不可空
	 * @param searchType
	 *            搜索条所在页面 --1全部患者 2签约患者 3非签约且非标签患者
	 * @param start
	 *            页面初始位置 --首页从0开始
	 * @param limit
	 *            每页限制值 --不可空
	 * @return List<Patient>
	 */
	public void testFindPatientByNamOrIdCOrMob() {
		String name = "";
		String idCard = "";
		String mobile = "";
		int pageNow = 2;
		int start = 0;
		int limit = 10;
		int doctor = 40;
		List<Patient> ps = relationDoctorDAO.findPatientByNamOrIdCOrMob(name,
				idCard, mobile, doctor, pageNow, start, limit);
		System.out.println(JSONUtils.toString(ps));
	}

	/**
	 * 三页搜索条服务（姓名）
	 * 
	 * @author luf
	 * @param name
	 *            患者姓名 --可空
	 * @param doctorId
	 *            当前医生内码 --不可空
	 * @param searchType
	 *            搜索条所在页面 --0全部患者 1签约患者 2非签约且非标签患者
	 * @param start
	 *            页面初始位置 --首页从0开始
	 * @return List<Patient>
	 */
	public void testFindThreePagesByName() {
		String name = "张";
		int doctorId = 1919;
		int searchType = 0;
		int start = 0;
		List<Patient> ps = relationDoctorDAO.findThreePagesByName(name,
				doctorId, searchType, start);
		System.out.println(JSONUtils.toString(ps));
	}

	/**
	 * Title: 根据医生查询所有患者测试用例
	 * 
	 * @author zhangjr
	 * @date 2015-9-25
	 * @param doctorId
	 *            医生主键
	 * @param start
	 *            页面初始位置 --首页从0开始
	 * @return void
	 */
	public void testFindAllPatientByDoctorIdWithPage() {
		Integer doctorId = 40;
		int start = 0;
		List<Patient> list = relationDoctorDAO
				.findAllPatientByDoctorIdWithPage(doctorId, start);
		System.out.println(JSONUtils.toString(list));
		String patientName = "";
		String remainDates = "";
		String isSign = "";
		String labelNames = "";
		List<RelationLabel> rlList = null;
		for (Patient pr : list) {
			labelNames = "";
			patientName = pr.getPatientName();
			remainDates = pr.getRemainDates();
			isSign = pr.getSignFlag() ? "签约" : "关注";
			rlList = pr.getLabels();
			if (rlList != null) {
				for (int i = 0; i < rlList.size(); i++) {
					labelNames += rlList.get(i).getLabelName() + ",";
				}
			}
			System.out.println(patientName + ":" + isSign + ":" + remainDates
					+ ":" + labelNames);
		}
	}

	/**
	 * Title: 根据医生查询所有签约患者测试用例
	 * 
	 * @author zhangjr
	 * @date 2015-9-25
	 * @param doctorId
	 *            医生主键
	 * @param start
	 *            页面初始位置 --首页从0开始
	 * @return void
	 */
	public void testFindSignPatientByDoctorIdWithPage() {
		Integer doctorId = 40;
		int start = 0;
		List<Patient> list = relationDoctorDAO
				.findSignPatientByDoctorIdWithPage(doctorId, start);
		String patientName = "";
		String remainDates = "";
		String isSign = "";
		String labelNames = "";
		List<RelationLabel> rlList = null;
		for (Patient pr : list) {
			labelNames = "";
			patientName = pr.getPatientName();
			remainDates = pr.getRemainDates();
			isSign = pr.getSignFlag() ? "签约" : "关注";
			rlList = pr.getLabels();
			if (rlList != null) {
				for (int i = 0; i < rlList.size(); i++) {
					labelNames += rlList.get(i).getLabelName() + ",";
				}
			}
			System.out.println(patientName + ":" + isSign + ":" + remainDates
					+ ":" + labelNames);
		}
	}

	/**
	 * Title: 根据医生查询未分组患者测试用例
	 * 
	 * @author zhangjr
	 * @date 2015-9-25
	 * @param doctorId
	 *            医生主键
	 * @param start
	 *            页面初始位置 --首页从0开始
	 * @return void
	 */
	public void testFindNoGroupPatientWidthPage() {
		Integer doctorId = 40;
		int start = 0;
		List<Patient> list = relationDoctorDAO.findNoGroupPatientWidthPage(
				doctorId, start);
		String patientName = "";
		for (Patient pr : list) {
			patientName = pr.getPatientName();
			System.out.println(patientName);
		}
	}

	/**
	 * Title: 根据医生查询指定标签患者测试用例
	 * 
	 * @author zhangjr
	 * @date 2015-9-25
	 * @param doctorId
	 *            医生主键
	 * @param labelName
	 *            标签名称
	 * @param start
	 *            页面初始位置 --首页从0开始
	 * @return void
	 */
	public void testFindPatientByLabelAndDoctorIdWidthPage() {
		Integer doctorId = 40;
		int start = 0;
		List<Patient> list = relationDoctorDAO
				.findPatientByLabelAndDoctorIdWidthPage(doctorId, "方法", start);
		String patientName = "";
		String remainDates = "";
		String isSign = "";
		String labelNames = "";
		List<RelationLabel> rlList = null;
		for (Patient pr : list) {
			labelNames = "";
			patientName = pr.getPatientName();
			remainDates = pr.getRemainDates();
			isSign = pr.getSignFlag() ? "签约" : "关注";
			rlList = pr.getLabels();
			if (rlList != null) {
				for (int i = 0; i < rlList.size(); i++) {
					labelNames += rlList.get(i).getLabelName() + ",";
				}
			}
			System.out.println(patientName + ":" + isSign + ":" + remainDates
					+ ":" + labelNames);
		}
	}

	/*
	 * public static void main(String[] args){
	 * testFindAllPatientByDoctorIdWithPage(); }
	 */

	public void testFindRelationDoctorId() {
		String mpiId = "8a287a564d286405014d289ac6c00000";
		System.out.println(JSONUtils.toString(relationDoctorDAO
				.findRelationDoctorId(mpiId)));
	}

	/**
	 * 根据关注ID查病人详细信息
	 * 
	 * @author luf
	 * @param relationDoctorId
	 *            医生关注内码
	 * @return Patient
	 */
	public void testGetPatientByRelationId() {
		Integer relationDoctorId = 1;
		Patient p = relationDoctorDAO.getPatientByRelationId(relationDoctorId);
		System.out.println(JSONUtils.toString(p));
	}

	public void testCleanDataOfThisTAble() {
		relationDoctorDAO.cleanDataOfThisTAble();
	}

	/**
	 * 全部患者
	 * 
	 * @author zhangx
	 * @date 2015-10-30 下午3:43:53
	 */
	public void testFindByDoctorId() {
		List<String> list = relationDoctorDAO.findByDoctorId(1919);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 未分组患者
	 * 
	 * @author zhangx
	 * @date 2015-10-30 下午3:44:00
	 */
	public void testFindNoGroupByDoctorId() {
		List<String> list = relationDoctorDAO.findNoGroupByDoctorId(1919);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 签约患者
	 * 
	 * @author zhangx
	 * @date 2015-10-30 下午3:46:22
	 */
	public void testFindSignPatientByDoctorId() {
		List<String> list = relationDoctorDAO.findSignPatientByDoctorId(1919);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 指定分组患者
	 * 
	 * @author zhangx
	 * @date 2015-10-30 下午3:50:06
	 */
	public void testFindByDoctorIdAndLabel() {
		List<String> list = relationDoctorDAO.findByDoctorIdAndLabel(1919,
				"蛇经病");
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 医生和病人对应的所有关注信息查询服务
	 * 
	 * @author luf
	 * @param doctorId
	 *            医生内码
	 * @param mpiId
	 *            主索引
	 * @return Patient
	 */
	public void testGetPatientAndRelationByDocAndMpi() {
		int doctorId = 40;
		String mpiId = "2c9081824cc3552a014cc3a9a0120002";
		Patient patient = relationDoctorDAO.getPatientAndRelationByDocAndMpi(
				doctorId, mpiId);
		System.out.println(JSONUtils.toString(patient));
	}

	/**
	 * 按姓名，身份证，手机号三个其中的一个搜索医生关注的患者中符合条件的患者
	 * 
	 * @author zhangx
	 * @date 2015-11-25 下午4:10:15
	 */
	public void testfindAttentionPatients() {
		Integer doctorId = 1182;
		List<Patient> patients = relationDoctorDAO.findAttentionPatients(
				doctorId, "%张%", "%331081%", "%187%", 0l, 20l);
		System.out.println(JSONUtils.toString(patients));
	}

	public void testfindRelationPatientByDoctorIdWithPage() {
		Integer doctorId = 1182;
		List<Patient> patients = relationDoctorDAO
				.findRelationPatientByDoctorIdWithPage(doctorId, 0, "desc");
		System.out.println(JSONUtils.toString(patients));
	}

	/**
	 * 查询病人关注医生列表(健康端)
	 * 
	 * eh.mpi.dao
	 * 
	 * @author luf 2016-2-26
	 * 
	 * @param mpiId
	 *            主索引ID
	 * @param flag
	 *            标志-0咨询1预约
	 * @return List<HashMap<String,Object>>
	 */
	public void testFindRelationDoctorListForHealth() {
		String mpiId = "2c9081814cc3ad35014cc3e0361f0000";// 黄伊瑾
		List<HashMap<String, Object>> list = relationDoctorDAO
				.findRelationDoctorListForHealth(mpiId, 0);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
	}

	/**
	 * 获取医生关注病人信息
	 * 
	 * @author luf
	 * @param mpiId
	 *            主索引
	 * @param doctorId
	 *            医生内码
	 * @return Patient
	 */
	public void testGetPatientRelation() {
		String mpiId = "2c9081824cc3552a014cc3a9a0120002";
		int doctorId = 40;
		Patient p = relationDoctorDAO.getPatientRelation(mpiId, doctorId);
		System.out.println(JSONUtils.toString(p));
	}

	public void testFindDoctorsByMpi() {
		String mpiId = "2c9081814cc3ad35014cc3e0361f0000";
		System.out.println(JSONUtils.toString(relationDoctorDAO
				.findDoctorsByMpi(mpiId)));
	}

	public void testSignOrRelationDoctors() {
		List<HashMap<String,Object>> ds = relationDoctorDAO.signOrRelationDoctors("402881924cc355a3014cc355ad3a0000",0,10,0);
//		List<HashMap<String,Object>> ds = relationDoctorDAO.signOrRelationDoctors("402881924cc355a3014cc355ad3a0000",1,4,2);
		System.out.println(JSONUtils.toString(ds));
	}

	public void testGetAllSignNumByUnit() {
		List<Integer> is = new ArrayList<Integer>();
		is.add(1);
		Long num = relationDoctorDAO.getAllSignNumByUnit("402881924cc355a3014cc355ad3a0000",is);
		System.out.println("关注数=================================================="+num);
	}
}
