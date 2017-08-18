package test.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import ctd.persistence.DAOFactory;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.DoctorGroupDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.base.DoctorGroupAndDoctor;
import eh.entity.base.Employment;

public class DoctorGroupDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testCreate() throws DAOException{
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO", DoctorGroupDAO.class);
		
		
		int nmr = ThreadLocalRandom.current().nextInt(10000);
		int n4 = ThreadLocalRandom.current().nextInt(1000,9999);
		
		DoctorGroup r = new DoctorGroup();
		r.setDoctorGroupId(n4);
		r.setDoctorId(14+nmr);
		r.setLeader(0);
		r.setMemberId(13);
		dao.save(r);
	}
	
	public void testFindByMemberId() throws DAOException {
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO",DoctorGroupDAO.class);
		
		int doctorId = 1;
		
		List<DoctorGroup> d = dao.findByMemberId(doctorId);
		System.out.println(JSONUtils.toString(d));
	}
	
	/**
	 * 根据医生团队内码查询医生团队测试
	 */
	public void testFindByDoctorGroupId(){
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO",DoctorGroupDAO.class);
		int doctorGroupId=5;
		List<DoctorGroup> d = dao.findByDoctorGroupId(doctorGroupId);
		System.out.println(JSONUtils.toString(d));
	}
	
	/**
	 * 新增团队成员测试
	 */
	public void testSaveDoctorGroup(){
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO",DoctorGroupDAO.class);
		int doctorId=2146;
		int memberId=1195;
		dao.saveDoctorGroup(doctorId,memberId);

	}
	
	/**
	 * 根据医生团队内码和医生内码删除医生团队测试
	 */
	public void testDeleteByDoctorGroupIdAndDoctorId(){
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO",DoctorGroupDAO.class);
		int doctorGroupId=100;
		int doctorId=9842;
		dao.deleteByDoctorGroupIdAndDoctorId(doctorGroupId, doctorId);
	}
	
	/**
	 * 新增团队医生基本信息服务
	 * @author hyj
	 */
	public void testAddGroupDoctor(){
		Doctor r = new Doctor();
		r.setName("俞建萍团队");
		r.setUserType(1);
		r.setOrgan(1);
		r.setProfession("02");
		Employment e = new Employment();
		e.setJobNumber("9003");
		e.setOrganId(1);
		e.setDepartment(70);
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO",DoctorGroupDAO.class);
		Doctor d=dao.addGroupDoctor(r, e);
		System.out.println(JSONUtils.toString(d));
	}
	
	/**
	 * 获取团队医生所有成员详细信息
	 * @author ZX
	 * @date 2015-6-16  下午1:23:51
	 */
	public void testGetDoctorInfoByDoctorId(){
		int doctorId=1561;
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO", DoctorGroupDAO.class);
		List<DoctorGroupAndDoctor> list=dao.getDoctorInfoByDoctorId(doctorId,0,10);
		for(DoctorGroupAndDoctor d:list){
			System.out.println(JSONUtils.toString(d));
		}
		
	}
	
	/**
	 * 获取团队医生所有成员详细信息--给前端调用默认一页10条记录
	 * @author hyj
	 */
	public void testGetDoctorGroupAndDoctorByDoctorId(){
		int doctorId=1918;
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO", DoctorGroupDAO.class);
		//List<DoctorGroupAndDoctor> list=dao.getDoctorGroupAndDoctorByDoctorId(doctorId, 0);
		Map<String, Object> map = dao.getDoctorGroupAndDoctorAndTotalByDoctorId(doctorId, 0);
		List<DoctorGroupAndDoctor> list=(List<DoctorGroupAndDoctor>) map.get("group");
		int total = (int) map.get("total");
		System.out.println(">>>>"+total);
		for(DoctorGroupAndDoctor d:list){
			System.out.println(JSONUtils.toString(d));
		}
		
	}
	
	/**
	 * 获取医生所属团队列表信息服务
	 * @author hyj
	 */
	public void testGetDoctorGroupAndDoctorByMemberId(){
		int memberId=1177;
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO", DoctorGroupDAO.class);
		List<DoctorGroupAndDoctor> list=dao.getDoctorGroupAndDoctorByMemberId(memberId, 0);
		for(DoctorGroupAndDoctor d:list){
			System.out.println(JSONUtils.toString(d));
		}
	}
	
	/**
	 * 删除团队成员
	 * @author ZX
	 * @date 2015-6-16  下午3:42:26
	 */
	public void testDelMember(){
		int doctorId=5;
		int memberId=46;
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO", DoctorGroupDAO.class);
		dao.delMember(doctorId, memberId);
	}
	
	/**
	 * 设置团队组长
	 *  @author yaozh
	 */
	public void testUpdateMemberToLeader(){
		int doctorId=1561;
		int memberId=1182;
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO", DoctorGroupDAO.class);
		dao.updateMemberToLeader(doctorId, memberId);
	}
	
	public void testGetByDoctorIdAndLeader(){
		int doctorId=1561;
		int leader=1;
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO", DoctorGroupDAO.class);
		DoctorGroup d=dao.getByDoctorIdAndLeader(doctorId, leader);
		System.out.println(JSONUtils.toString(d));
	}
	
	/**
	 * 修改团队角色
	 */
	public void testUpdateGroupRole(){
		int doctorId=1561;
		int memberId=1178;
		int leader=1;
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO",DoctorGroupDAO.class);
		dao.updateGroupRole(doctorId, memberId, leader);
	}

	public void testFindMembersAndDoctor() {
		DoctorGroupDAO dao = appContext.getBean("doctorGroupDAO",DoctorGroupDAO.class);
		Map<String,Object> map = dao.findMembersAndDoctor(3849,0);
		System.out.println(JSONUtils.toString(map));
	}

	/**
	 * 运营平台 三条件新增团队医生
	 * @date 2016-05-05 16:20:50
	 * @author houxr
	 */
	public void testSaveDoctorGroupByGroupRole(){
		DoctorGroupDAO dao=appContext.getBean("doctorGroupDAO",DoctorGroupDAO.class);
		int doctorId=39;
		int memberId=4741;
		int leader=1;
		dao.saveDoctorGroupByGroupRole(doctorId,memberId,leader);

	}

	public void testfindMembersByDoctorIdForHealth(){
		Integer docId=5;
		DoctorGroupDAO dao= DAOFactory.getDAO(DoctorGroupDAO.class);
		List<Object[]> list=dao.findMembersByDoctorIdForHealth(docId, 0,4);
		System.out.println(JSONUtils.toString(list));
	}

	public void testFindDoctorIdsByMemberIdAndMode() {
		DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
		System.out.println(dao.findDoctorIdsByMemberIdAndMode(1182,null));
	}

	public void testFindMemberIdsByDoctorId() {
		DoctorGroupDAO dao = DAOFactory.getDAO(DoctorGroupDAO.class);
		System.out.println(dao.findMemberIdsByDoctorId(7444));
	}
}
