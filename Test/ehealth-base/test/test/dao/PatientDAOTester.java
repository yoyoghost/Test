package test.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import eh.entity.mpi.PatientType;
import eh.mpi.dao.PatientTypeDAO;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.exception.DAOException;
import ctd.schema.exception.ValidateException;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.util.ChinaIDNumberUtil;

public class PatientDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static PatientDAO dao;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("patientDAO", PatientDAO.class);
	}

	/*
	 * public void testGetHisToUpdate() { Patient patient = new Patient();
	 * List<HealthCard> healthCards = new ArrayList<HealthCard>(); HealthCard
	 * healthCard = new HealthCard(); healthCard.setCardType("1");
	 * healthCard.setCardId("81399"); healthCard.setCardOrgan(470211128);
	 * healthCards.add(healthCard); patient.setHealthCards(healthCards);
	 * patient.setPatientName("唐国儿");
	 * System.out.println(JSONUtils.toString(dao.getHisToUpdate(patient))); }
	 */

	public void testCreate() throws DAOException, ValidateException {

		Patient p = new Patient();
		p.setPatientSex("1");
		p.setMobile("13018923990");
		p.setPatientName("丁东");
		p.setIdcard("42082119911111353X");
		p.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber("42082119911111353X"));
		 dao.save(p);
	}

	public void testUpdateBussAndAppointPatientName() {
		// String mpiId = "8a287a564d26f753014d281d6c530000";
		// String patientName = "卢芳";
		// dao.updateBussAndAppointPatientName(mpiId, patientName);
	}

	private Patient createRanddomOne() {
		int nmr = ThreadLocalRandom.current().nextInt(10000);
		int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

		Patient p = new Patient();

		p.setPatientName("张肖" + nmr);
		p.setPatientSex("2");
		p.setBirthday(new Date());
		p.setIdcard("33108119920702" + n4);

		return p;
	}

	/**
	 * 根据【mpiId】获取主索引信息服务
	 * 
	 * @throws DAOException
	 */
	public void testGetByMpiId() throws DAOException {

		String mpiId = "2c9081824ce5089d014ce61823030000";

		Patient p = dao.getByMpiId(mpiId);

		System.out.println(JSONUtils.toString(p));
	}

	public void testGetByIdCard() throws DAOException {
		String idCard = "339011197609025490";
		Patient p = dao.getByIdCard(idCard);
		System.out.println(JSONUtils.toString(p));
	}

	public void testGetOrUpdateExist() {
		Patient p = new Patient();
		p.setIdcard("330187199003024536");
		p.setHomeArea("3301");

		Patient find = dao.getOrUpdate(p);
		System.out.println(JSONUtils.toString(find));
	}

	public void testGetOrUpdateNewCard() {
		Patient p = new Patient();
		p.setIdcard("330184199305152620");
		p.setBirthPlace("330191");

		List<HealthCard> cards = new ArrayList<>();
		HealthCard card = new HealthCard();
		card.setCardOrgan(2);
		card.setCardType("1");
		card.setCardId("1234567");
		cards.add(card);

		p.setHealthCards(cards);
		Patient find = dao.getOrUpdate(p);
		System.out.println(JSONUtils.toString(find));
	}

	public void testGetOrUpdateOnlyCard() {
		Patient p = new Patient();

		List<HealthCard> cards = new ArrayList<>();
		HealthCard card = new HealthCard();
		card.setCardOrgan(2);
		card.setCardType("1");
		card.setCardId("1234567");
		cards.add(card);

		p.setHealthCards(cards);
		Patient find = dao.getOrUpdate(p);
		System.out.println(JSONUtils.toString(find));

	}

	public void testGetOrUpdateNewPatient() {
		Patient p = new Patient();
		p.setIdcard("332603198002205437");
		p.setPatientName("王晓东");
		p.setBirthday(StringToDate.toDate("1980-02-20"));
		Patient find = dao.getOrUpdate(p);
		System.out.println(JSONUtils.toString(find));
	}

	public void testGetPatientType() {
		try {
			Map<Integer, String> map = dao.getPatientType();
			for (int i = 1; i <= map.size(); i++) {
				System.out.println(map.get(i));
			}
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 创建患者用户
	 * 
	 * @author ZX
	 * @date 2015-4-15 上午11:17:46
	 */
	public void testCreatePatientUser() {
		Patient p = new Patient();
		p.setPatientName("周梦一");
		p.setMobile("18768177770");
		p.setPatientSex("2");
		p.setIdcard("331081199207026745");
		p.setBirthday(new Date());
		Patient px = dao.createPatientUser(p, "888888");
		System.out.println(JSONUtils.toString(px));
	}

	/**
	 * 获取患者数量
	 * 
	 * @author ZX
	 * @date 2015-4-21 下午1:13:27
	 */
	public void testGetAllPatientNum() {
		long num = dao.getAllPatientNum();
		System.out.println(num);
	}

	/**
	 * 获取 指定时间内患者数量
	 * 
	 * @author ZX
	 * @date 2015-5-21 下午3:54:22
	 */
	public void testGetPatientNumFromTo() {
		Date startTime = new StringToDate().convert("2015-01-01");
		Date endTime = new StringToDate().convert("2016-01-01");
		long num = dao.getPatientNumFromTo(startTime, endTime);
		System.out.println(num);
	}

	/**
	 * 获取当月新增的患者数
	 * 
	 * @author ZX
	 * @date 2015-5-21 下午4:25:01
	 */
	public void testGetPatientNumByMonth() {
		long num = dao.getPatientNumByMonth();
		System.out.println(num);
	}

	/**
	 * 获取昨天新增的患者数
	 * 
	 * @author ZX
	 * @date 2015-5-21 下午4:25:01
	 */
	public void testGetPatientNumByYestoday() {
		long num = dao.getPatientNumByYesterday();
		System.out.println(num);
	}

	/**
	 * 获取指定时间内登录过的用户数
	 * 
	 * @author ZX
	 * @date 2015-5-21 下午6:09:15
	 */
	public void testGetActiveNum() {
		Date startTime = new StringToDate().convert("2015-01-01");
		Date endTime = new StringToDate().convert("2016-01-01");
		long num = dao.getActiveNum(startTime, endTime);
		System.out.println(num);
	}

	/**
	 * 获取三天内的活跃用户数
	 * 
	 * @author ZX
	 * @date 2015-5-21 下午6:30:44
	 */
	public void testGetActivePatientNum() {
		long num = dao.getActivePatientNum();
		System.out.println(num);
	}

	public void testGetByLoginId() {
		System.out.println(JSONUtils.toString(dao.getByLoginId("13735891715")));
	}

	/**
	 * 获取病人列表
	 * 
	 * @author ZX
	 * @date 2015-7-3 下午4:05:20
	 */
	public void testFindByMpiIdIn() {
		List<String> list = new ArrayList<String>();
		list.add("2c9081814cc3ad35014cc3e0361f0000");
		list.add("2c9081814cd4ca2d014cd4ddd6c90000");
		List<Patient> pl = dao.findByMpiIdIn(list);
		System.out.println(JSONUtils.toString(pl));
	}

	/**
	 * 将属地区域code转化成对应的text值
	 * 
	 * @author hyj
	 */
	public void testGetAddrAreaTextByCode() {
		DictionaryItem d = dao.getAddrAreaTextByCode("33");
		System.out.println(JSONUtils.toString(d));
	}

	public void testFindByMobileOrNameOrIdCard() {
		String mobile = "";
		String patientName = "";
		String idcard = "";
		List<Patient> patients = dao.findByMobileOrNameOrIdCard(mobile,
				patientName, idcard);
		System.out.println(patients.size());
		System.out.println(JSONUtils.toString(patients));
	}

	public void testGetMobileByMpiId() {
		System.out.println(dao
				.getMobileByMpiId("2c9081824cc3552a014cc3a9a0120002"));
	}

	public void testGetByIdCardAddHealthCards() {
		System.out.println(JSONUtils.toString(dao
				.getByIdCardAddHealthCards("33108119920702674X")));
	}
	
	public void testSearchPatientByDoctorId(){
	Patient p = new Patient(),p2= new Patient();
/*		List<Patient> list1= new ArrayList<Patient>(),list2= new ArrayList<Patient>();
		p.setMpiId("0011");
		p.setPatientName("姜海川");
		list1.add(p);
		p2.setMpiId("0011");
		p2.setPatientName("姜海川");
		list2.add(p2);
		
		System.out.println("list1>>"+JSONUtils.toString(list1));
		System.out.println("list2>>"+JSONUtils.toString(list2));
		list1.addAll(list2);
		Set<Patient > set = new HashSet<>();
		set.addAll(list1);
		
		System.out.println("----------------------------------------");
		System.out.println("list1>>"+JSONUtils.toString(list1));
		System.out.println("set>>"+set.size());*/	
		
		System.out.println(JSONUtils.toString(dao.searchPatientByDoctorId(1919, "姜", "姜", "姜")));
		}

	public void testUpdatePatientUserInfo(){
		Patient pateint = dao.getByMpiId("402881b94ff3caf0014ff3d460110000");
		pateint.setFullHomeArea("浙江省 杭州市 淳安县");
		dao.updatePatientUserInfo(pateint);
	}

	public void testPatientTypeDic() {
		PatientTypeDAO typeDAO = DAOFactory.getDAO(PatientTypeDAO.class);
		PatientType patientType = typeDAO.get("1");
		System.out.println(JSONUtils.toString(patientType));
		Patient patient = dao.get("2c9081814cc3ad35014cc470838f0001");
		System.out.println(JSONUtils.toString(patient));
	}

	public void testUpdatePatientTypeByMpiId() {
		dao.updatePatientTypeByMpiId("1","2c9081814d689a20014d6b6c4ad80001");
	}
}
