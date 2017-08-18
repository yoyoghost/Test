package test.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import eh.utils.DateConversion;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.QueryDoctorListDAO;
import eh.entity.base.Doctor;
import junit.framework.TestCase;

public class QueryDoctorListDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("spring.xml");
	}

	public void testQueryDoctorList() throws DAOException {
		int buesType = 0;
		Integer department = 70;
		int organId = 1;
		QueryDoctorListDAO dao = appContext.getBean("queryDoctorListDAO",
				QueryDoctorListDAO.class);
		List<Doctor> list = dao.queryDoctorList(buesType, department, organId);
		if (list != null && list.size() > 0) {
			System.out.println(JSONUtils.toString(list));
		}
	}

	public void testQueryDoctorListWithPage() throws DAOException {
		int buesType = 0;
		Integer department = 70;
		int organId = 1;
		int start = 0;
		QueryDoctorListDAO dao = appContext.getBean("queryDoctorListDAO",
				QueryDoctorListDAO.class);
		List<Doctor> list = dao.queryDoctorListWithPage(buesType, department,
				organId, start);
		if (list != null && list.size() > 0) {
			System.out.println(JSONUtils.toString(list));
		}

		list = dao.queryDoctorListWithPage(buesType, department, organId, 10);
		System.out.println(JSONUtils.toString(list));

		list = dao.queryDoctorListWithPage(buesType, department, organId, 20);
		System.out.println(JSONUtils.toString(list));

		list = dao.queryDoctorListWithPage(buesType, department, organId, 30);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 根据机构和科室查询医生列表
	 * 
	 * eh.base.dao
	 * 
	 * @author luf 2016-2-26
	 * 
	 * @param department
	 * @param organId
	 * @param flag
	 *            标志-0咨询1预约
	 * @return List<HashMap<String,Object>>
	 */
	public void testQueryDoctorListForHealth() {
		QueryDoctorListDAO dao = appContext.getBean("queryDoctorListDAO",
				QueryDoctorListDAO.class);
		List<HashMap<String, Object>> list = dao.queryDoctorListForHealth(70, 1,
				1);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
	}

	public void testQueryDoctorListByProfession() {
		int organId = 1;
		String profession = "02";
		QueryDoctorListDAO dao = appContext.getBean("queryDoctorListDAO",
				QueryDoctorListDAO.class);
		List<HashMap<String, Object>> list = dao.queryDoctorListByProfession(
				organId, profession);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
	}

	public void testEffectiveWorkDates() {
		QueryDoctorListDAO dao = appContext.getBean("queryDoctorListDAO",
				QueryDoctorListDAO.class);
//		List<HashMap<String, Object>> results = dao.effectiveWorkDates(70,1);
		List<HashMap<String, Object>> results = dao.effectiveWorkDatesForHealth(70,1);
		System.out.println(JSONUtils.toString(results));
		System.out.println(results.size());
	}

	public void testEffectiveSourceDoctors() {
		QueryDoctorListDAO dao= appContext.getBean("queryDoctorListDAO",QueryDoctorListDAO.class);
//		List<Doctor> ds = dao.effectiveSourceDoctors(70,1, DateConversion.getCurrentDate("2016-05-19","yyyy-MM-dd"),0,10);
		List<HashMap<String, Object>> ds = dao.effectiveSourceDoctorsForhealth(70,1, /*null*/DateConversion.getCurrentDate("2016-12-21","yyyy-MM-dd"));
		System.out.println(JSONUtils.toString(ds));
		System.out.println(ds.size());
	}

	public void testInDoctorListForRecive() {
		QueryDoctorListDAO dao= appContext.getBean("queryDoctorListDAO",QueryDoctorListDAO.class);
		List<Doctor> ds = dao.inDoctorListForRecive(1,70,1);
		System.out.println(JSONUtils.toString(ds));
	}

	/**
	 * 测试专家解读列表功能中获取医生的列表
	 * @author cuill
	 * Date: 2017年2月15日
	 */
	public void testQueryDoctorListForExpertConsult(){
		QueryDoctorListDAO dao= appContext.getBean("queryDoctorListDAO",QueryDoctorListDAO.class);
		System.out.println( JSONUtils.toString(dao.queryDoctorListForExpertConsult("02", 0, 10)));
	}

	/**
	 * 测试专家解读列表科室的排序
	 * @author cuill
	 * Date: 2017年2月15日
	 */
	public void testFindDepartmentList(){
		QueryDoctorListDAO dao= appContext.getBean("queryDoctorListDAO",QueryDoctorListDAO.class);
		List<Object[]> results = dao.findProfessionList();
		for(int i = 0; i<results.size(); i++) {
			Object[] obj = results.get(i);
			System.out.println((String) obj[0]);
			System.out.println((long) obj[1]);
		}
	}
}
