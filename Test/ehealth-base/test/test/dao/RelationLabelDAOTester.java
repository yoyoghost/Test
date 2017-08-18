package test.dao;

import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import ctd.util.JSONUtils;
import eh.base.dao.RelationLabelDAO;
import eh.entity.mpi.RelationDoctor;

public class RelationLabelDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static RelationLabelDAO dao;

	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("relationLabelDAO", RelationLabelDAO.class);
	}
	
	/**
	 * 查询标签列表服务
	 * @author LF
	 * @param doctorId
	 * @return
	 */
	public void testFindRelationLabelByDoctorId() {
		Integer doctorId = 40;
//		Integer doctorId = 1182;
		System.out.println(JSONUtils.toString(dao.findRelationLabelByDoctorId(doctorId)));
	}
	
	public void testArray() {
		List<String> list = new ArrayList<String>();
		System.out.println(JSONUtils.toString(list));
		System.out.println(JSONUtils.toString(list.size()<=0));
	}
	
	/**
	 * 添加某个关注的标签服务
	 * @author LF
	 * @return
	 */
	public void testAddRelationLabel() {
		Integer relationPatientId = 13040;
		List<String> labelNames = new ArrayList<String>();
		labelNames.add("关注d");
		labelNames.add("关注e病");
		labelNames.add("注d病人");
		System.out.println(dao.addRelationLabel(relationPatientId, labelNames));
	}
	
	/**
	 * 删除标签
	 * @author LF
	 * @return
	 */
	public void testDeleteRelationLabel() {
		RelationDoctor relationPatient = new RelationDoctor();
		relationPatient.setMpiId("2c9081824cc3552a014cc3a9a0120002");
		relationPatient.setDoctorId(40);
		String labelName = "";
		System.out.println(dao.deleteRelationLabel(relationPatient, labelName));
	}

	/**
	 * 删除标签
	 * @author LF
	 * @return
	 */
	public void testDeleteRelationLabelById() {
		Integer relationPatientId=1;
		String labelName = "关";
		System.out.println(dao.deleteRelationLabelById(relationPatientId, labelName));
	}
	
	/**
	 * 根据关注ID查询标签列表（供添加某个关注的标签服务调用）
	 * @author LF
	 * @param relationPatientId
	 * @return
	 */
	public void testFindByRelationPatientId() {
		Integer relationPatientId = 3;
		System.out.println(JSONUtils.toString(dao.findByRelationPatientId(relationPatientId)));
	}

	/**
	 * 根据关注病人编号删除多个标签
	 * @param relationPatientId
	 */
	public void testDeleteByRelationPationtId() {
		Integer relationPatientId = 3;
		dao.deleteByRelationPatientId(relationPatientId);
	}
	
	/**
	 * 获取所有标签（供 RelationDoctorDAO findPatientByNamOrIdCOrMob 使用）
	 * 
	 * @author luf
	 * @return List<Integer>
	 */
	public void testFindAllRelationPatientId() {
		int doctorId = 40;
		List<Integer> is = dao.findAllRelationPatientId(doctorId);
		System.out.println(JSONUtils.toString(is));
		System.out.println(is.size());
	}
	
	public void testFindLabelNamesByRPId() {
		Integer relationPatientId = 13040;
		List<String> ls = dao.findLabelNamesByRPId(relationPatientId);
		System.out.println(JSONUtils.toString(ls));
	}
	
	public void testContainObjectL() {
		List<Object[]> is1 = new ArrayList<Object[]>();
		List<String> is2 = new ArrayList<String>();
		Object[] e = {"好友",1};
		Object[] e1 = {"好",2};
		Object[] e2 = {"友",3};
		is1.add(e);
		is1.add(e1);
		is1.add(e2);
		is2.add("好友");
		is2.add("好碰");
		for(String i:is2) {
			System.out.println(is1.contains(i));
		}
	}
}
