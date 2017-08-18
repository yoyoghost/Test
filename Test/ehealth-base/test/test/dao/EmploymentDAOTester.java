package test.dao;

import com.alibaba.fastjson.JSON;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.EmploymentDAO;
import eh.entity.base.Employment;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.List;

public class EmploymentDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static EmploymentDAO dao;
	
	static{
		appContext = new ClassPathXmlApplicationContext("spring.xml");
		dao =appContext.getBean("employmentDAO", EmploymentDAO.class);
	}

    public void testFindEffEmpWithDrug(){
        System.out.println(JSONUtils.toString(dao.findEffEmpWithDrug(9138)));
    }

	
	public void testDeleteById() {
		int id = 2078;
//		dao.deleteById(id);
		System.out.println(dao.deleteEm(id));
	}
	
	public void testCreate() throws DAOException{
		
		Employment r = new Employment();
		r.setDoctorId(1);
		r.setOrganId(3);
		dao.save(r);
		
		System.out.println(r.getEmploymentId());
	}
	
	public void testFindByDoctorId() throws DAOException{
		List<Employment> rs = dao.findByDoctorId(336);
		System.out.println(JSONUtils.toString(rs));
		assertEquals(2,rs.size());
	}
	
	public void testFindEmByDoctorId() {
		List<Employment> list = dao.findEmByDoctorId(1178);
		System.out.println(list.size());
		System.out.println(JSONUtils.toString(list));
	}
	
	public void testDAOFactory() throws DAOException{
		EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
	
		assertTrue(dao.exist(7));
	}
	
	public void testGetByDoctorIdAndOrganId(){
		Employment r = dao.getDeptNameByDoctorIdAndOrganId(38, 4);
//		assertNotNull(r);
		System.out.println(JSONUtils.toString(r));
		
//		dao.updateJobNumberByDoctorIdAndOrganId("401", 1, 2);
	}

	public void testUpdateJobnumberByOrganIdAndName(){
		dao.updateJobnumberByOrganIdAndName("111111", 1, "王宁武");
	}
	public void testFindAllByOrganId(){
		/*List<Employment> list=dao.findByOrganId(1,1);
		System.out.println(list.size());
		System.out.println(JSONUtils.toString(list));
		list=dao.findByOrganId(1,10);
		System.out.println(list.size());
		System.out.println(JSONUtils.toString(list));*/
	}
	public void testFindAllEmp(){
		List<Employment> list=dao.findAllEmp(1,1,10);
		System.out.println(list.size());
		System.out.println(JSONUtils.toString(list));
	    list=dao.findAllEmp(1,10,10);
		System.out.println(list.size());
		System.out.println(JSONUtils.toString(list));
	}
	/*public void testFindAll(){
		List<Employment> list= dao.findAll(1);
		System.out.println(list.size());
	}*/

	
	/**
	 * 根据医生工号，机构编码查询医生内部主键
	 */
	public void testGetDoctorIdByJobNumberAndOrganId(){
		String jobNumber="30130";
		int organId=1;
		int doctorId = dao.getDoctorIdByJobNumberAndOrganId(jobNumber,organId);
		System.out.println(JSONUtils.toString(doctorId));
		
	}
	
	/**
	 * 根据医生工号，机构编码查询医生内部主键

	 */
	public void testFindJobNumberByDoctorIdAndOrganId(){
		int doctorId=40;
		int organId=2;
		List<String> jobNumber = dao.findJobNumberByDoctorIdAndOrganId(doctorId,organId);
		System.out.println(JSONUtils.toString(jobNumber));
		
	}
	
	/**
	 * 医生执业信息新增服务
	 * @author hyj
	 */
	public void testAddEmployment(){
		Employment r = new Employment();
		r.setDoctorId(1178);
		r.setJobNumber("9003");
		r.setOrganId(1);
		r.setDepartment(70);
		r.setClinicRoomAddr1("门诊一楼内科");
		r.setClinicRoomAddr2("住院楼七楼内科");
		r.setClinicRoomAddr3("住院楼六楼");
		r.setClinicPrice((double) 114);
		System.out.println(JSONUtils.toString(dao.addEmployment(r)));
	}
	
	public void testgetPrimaryEmpByDoctorId(){
		Employment e = dao.getPrimaryEmpByDoctorId(1182);
		System.out.println(JSONUtils.toString(e));
	}

	public void testFindByDoctorIdAndOrganId() {
		int doctorId = 1178;
		int organId = 1;
		System.out.println(JSONUtils.toString(dao.findByDoctorIdAndOrganId(doctorId, organId)));
	}
	
	public void testMapContainsKey() {
		HashMap<String, Integer> map = new HashMap<String,Integer>();
		map.put("9001", 1177);
		map.put("9003", 1178);
		System.out.println(JSONUtils.toString(map));
		System.out.println(map.containsKey("9001"));
		System.out.println(map.containsKey("9002"));
	}
	
	public void testFindEmploymentList(){
		Integer doctorId = 1377;
		System.out.println(JSON.toJSONString(dao.findEmploymentList(doctorId)));
	}
	
	public void testFindUnEmptyEmploymentList(){
		Integer doctorId = 1377;
		System.out.println(JSON.toJSONString(dao.findUnEmptyEmploymentList(doctorId)));
	}
	public void testGetPrimaryEmployment(){
		Integer doctorId = 1377;
		System.out.println(JSON.toJSONString(dao.getPrimaryEmployment(doctorId)));
	}
	
	/**
	 * 查询一个医生的职业机构列表，第一职业点排在最前面，非第一职业点升序排序
	 * @author zhangx
	 * @date 2015-11-16 下午8:23:26
	 */
	public void testFindByDoctorIdOrderBy(){
		Integer doctorId = 1377;
		System.out.println(JSON.toJSONString(dao.findByDoctorIdOrderBy(doctorId)));
	}
	
	
	public void testUpdateEmployment(){
		Employment e=dao.getById(9671);
		e.setClinicRoomAddr1("");
		e.setSourceLevel1(null);
		e.setClinicRoomAddr2("");
		e.setSourceLevel2(null);
		e.setClinicRoomAddr3("");
		e.setSourceLevel3(null);
		Employment re=dao.updateEmploymentForOP(e);
		System.out.println(JSONUtils.toString(re));
	}

}
