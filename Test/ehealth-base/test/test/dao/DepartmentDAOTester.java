package test.dao;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.alibaba.fastjson.JSON;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.google.common.eventbus.Subscribe;

import ctd.persistence.event.support.AbstractDAOEventLisenter;
import ctd.persistence.event.support.CreateDAOEvent;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.DepartmentDAO;
import eh.entity.base.Department;

public class DepartmentDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("spring.xml");
	}

	@SuppressWarnings("unused")
	public void testCreate() throws DAOException {
		DepartmentDAO dao = appContext.getBean("departmentDAO",
				DepartmentDAO.class);

		dao.addEventListener(new AbstractDAOEventLisenter() {
			@Override
			@Subscribe
			public void onCreate(CreateDAOEvent e) {
				System.out.println(e.getTarget() + "," + e.getTargetId());
			}
		});

		int nmr = ThreadLocalRandom.current().nextInt(10000, 99999);
		// Integer organId, String code, String professionCode
		Department dept = new Department(1, "38A", "1502");
		dept.setName("精神卫生科XS");

		dept.setParentDept(1);
		dept.setOrderNum(1);
		dept.setInpatientEnable(1);
		dept.setStatus(0);
		dept.setClinicEnable(1);
		dao.save(dept);
		System.out.println("save done");
	}

	public List<Department> testGetByOrganIdAndProfessionCode() {
		DepartmentDAO dao = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		List<Department> depts = dao.findByOrganIdAndProfessionCode(1, "50");
		System.out.println(JSONUtils.toString(depts));
		return depts;
	}

	public Department testGetByCode() {
		DepartmentDAO dao = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		Department dept = dao.getByCode("04");
		System.out.println(JSONUtils.toString(dept));
		return dept;
	}

	public void testGetById() {
		int deptId = 70;
		DepartmentDAO dpd = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		Department d = dpd.getById(deptId);
		System.out.println(JSONUtils.toString(d));
	}

	public void testQueryDepartment() throws DAOException {
		Integer organId = 1;
		String professionCode = "2";

		DepartmentDAO dpd = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		List<Department> dp = dpd.findDepartment(organId, professionCode);
		System.out.println(JSONUtils.toString(dp));
	}

	/**
	 * 医院专科类别查询服务
	 * 
	 * @throws DAOException
	 */
	public void testFindProfessionCode() throws DAOException {
		int organ = 1;
		int deptType = 1;
		DepartmentDAO dpd = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		List<Object> list = dpd.findValidProfession(organ, deptType);
		System.out.println(JSONUtils.toString(list.size()));
		for (int i = 0; i < list.size(); i++) {
			System.out.println(JSONUtils.toString(list.get(i)));
		}

	}

	/**
	 * 医院有效科室查询服务
	 * 
	 * @throws DAOException
	 */
	public void testFindValidDepartment() throws DAOException {
		int organId = 1;
		String professionCode = "03";
		int bussType = 0;
		DepartmentDAO dpd = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		List<Department> list = dpd.findValidDepartment(organId,
				professionCode, bussType);
		System.out.println(list.size());
		for (Department d : list) {
			System.out.println(JSONUtils.toString(d));
		}
	}

	/**
	 * 科室新增服务
	 * 
	 * @author hyj
	 */
	public void testAddDepartment() {
		Department dept = new Department(8, "38A", "1502");
		dept.setName("精神卫生科XS");

		dept.setParentDept(1);
		dept.setOrderNum(1);
		dept.setInpatientEnable(1);
		dept.setStatus(0);
		dept.setClinicEnable(1);
		DepartmentDAO dpd = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		Department d = dpd.addDepartment(dept);
		System.out.println(JSONUtils.toString(d));
	}

	/**
	 * 医院科室列表分页查询服务
	 * 
	 * @author hyj
	 * @throws DAOException
	 */
	public void testFindDepartmentWithPage() throws DAOException {
		Integer organId = 1;
		String professionCode = "03";
		DepartmentDAO dpd = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		List<Department> dp = dpd.findDepartmentWithPage(organId,
				professionCode, 10);
		System.out.println(dp.size());
		for (Department d : dp) {
			System.out.println(JSONUtils.toString(d));
		}

	}

	public void testGetByCodeAndOrgan() {
		DepartmentDAO dao = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		System.out.println(JSONUtils.toString(dao.getByCodeAndOrgan("32",
				Integer.valueOf("1"))));
	}

	public void testGetDeptByProfessionIdAndOrgan() {
		DepartmentDAO dao = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
		System.out.println(JSONUtils.toString(dao
				.getDeptByProfessionIdAndOrgan("0308", 1)));
	}

	/**
	 * 医院有效科室查询服务
	 * 
	 * @throws DAOException
	 */
	public void testFindValidDepartmentTransferWithApp() throws DAOException {
		int organId = 1;
		String professionCode = "291";
		int bussType = 1;
		DepartmentDAO dpd = appContext.getBean("departmentDAO",
				DepartmentDAO.class);
//		List<Department> list = dpd.findValidDepartment(organId,
//				professionCode, bussType);
		List<Department> list = dpd.findValidDepartmentTransferWithApp(organId,
				professionCode, bussType);
		System.out.println(list.size());
		for (Department d : list) {
			System.out.println(JSONUtils.toString(d));
		}
	}

	public void testFindValidDepartmentByProfessionCode()throws DAOException{
		DepartmentDAO dpd=appContext.getBean("departmentDAO",DepartmentDAO.class);
		String professionCode="03";
		String addr="3301";
		String organIdStr="1";
		List<Object> list=dpd.findValidDepartmentByProfessionCode(addr,organIdStr,professionCode);
		System.out.println(JSON.toJSONString(list));
	}

	public void testFindHotProfession()throws DAOException{
		DepartmentDAO dpd=appContext.getBean("departmentDAO",DepartmentDAO.class);
		List<Object> list=dpd.findHotProfession();
		System.out.println(JSON.toJSONString(list));
	}
}
