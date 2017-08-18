package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.base.dao.DoctorRelationDoctorDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorRelationDoctor;

public class DoctorRelationDoctorDAOTester extends TestCase
{
	private static ClassPathXmlApplicationContext appContext;
	private static DoctorRelationDoctorDAO dao;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("doctorRelationDoctorDAO", DoctorRelationDoctorDAO.class);
	}
	
	/**
	 * 关注医生添加服务 (先判断该医生是否关注过，未关注过，则添加关注)
	 * @author ZX
	 * @date 2015-6-12  下午7:08:04
	 */
	public void testAddRelationDoctor(){
		int doctorId=1178;
		int relationDoctorId=1198;
		
		dao.addRelationDoctor(doctorId, relationDoctorId);
	}
	
	/**
	 * 判断医生是否关注服务
	 * @author ZX
	 * @date 2015-6-12  下午7:09:10
	 */
	public void testGetRelationDoctorFlag(){
		int doctorId=1178;
		int relationDoctorId=1198;
		boolean b=dao.getRelationDoctorFlag(doctorId, relationDoctorId);
		System.out.println(b);
	}
	
	/**
	 * 根据被关注医生主键，医生主键查询
	 * @author ZX
	 * @date 2015-6-12  下午7:10:32
	 */
	public void testGetByDoctorIdAndRelationDoctorId(){
		int doctorId=1178;
		int relationDoctorId=1182;
		DoctorRelationDoctor re=dao.getByDoctorIdAndRelationDoctorId(doctorId, relationDoctorId);
		System.out.println(JSONUtils.toString(re));
	}
	
	/**
	 *  取消关注
	 * @author ZX
	 * @date 2015-6-12  下午7:11:28
	 */
	public void testDelByDoctorIdAndRelationDoctorId(){
		int doctorId=1178;
		int relationDoctorId=1182;
		dao.delByDoctorIdAndRelationDoctorId(doctorId, relationDoctorId);
	}
	
	/**
	 * 查询病人关注医生列表(分页)
	 * @author ZX
	 * @date 2015-6-12  下午7:13:24
	 */
	public void testfindRelationDoctorListStartAndLimit(){
		int doctorId=1178;
		int start=0;
		int limit=100;
		List<Doctor> list =dao.findRelationDoctorListStartAndLimit(doctorId, start, limit);
		System.out.println(JSONUtils.toString(list));
	}
	
	/**
	 * 查询病人关注医生列表(分页)
	 * @author ZX
	 * @date 2015-6-12  下午7:14:12
	 */
	public void testFindRelationDoctorListStart(){
		int doctorId=1178;
		int start=0;
		List<Doctor> list =dao.findRelationDoctorListStart(doctorId, start);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
		
		int start2=2;
		List<Doctor> list2 =dao.findRelationDoctorListStart(doctorId, start2);
		System.out.println(JSONUtils.toString(list2));
		System.out.println(list2.size());
	}
	
	public void testFindRelationDoctorListStartAndLimitBus() {
		int doctorId = 1178;
		int start = 0;
		int busId = 2;
		List<Doctor> list = dao.findRelationDoctorListStartAndLimitBus(doctorId, busId, start, 10);
		System.out.println(JSONUtils.toString(list));
	}
	
	/**
	 * 查询被关注医生列表(分页)
	 * 
	 * @author luf
	 * @param relationDoctorId
	 *            被关注医生
	 * @param start
	 *            分页起始位置
	 * @return List<Doctor>
	 */
	public void testFindDoctorListByRelationStart() {
		int relationDoctorId = 40;
		int start = 0;
		List<Doctor> list = dao.findDoctorListByRelationStart(relationDoctorId, start);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
	}
}
