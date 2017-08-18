package test.dao;

import java.util.HashMap;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.entity.mpi.FamilyMember;
import eh.entity.mpi.FamilyMemberAndPatient;
import eh.entity.mpi.Patient;
import eh.mpi.dao.FamilyMemberDAO;

public class FamilyMemberDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	public void testCreate() throws DAOException{
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO", FamilyMemberDAO.class);
		FamilyMember r = new FamilyMember();
		r.setMpiid("4028811f4bce1c7f014bce1cc5fa0000");
		r.setMemberMpi("402881f04bc9baec014bc9bbb0c40000");
		r.setRelation("哥哥");
//		r.setMemberId(13);
		dao.save(r);
	}
	
	/**
	 * 家庭成员查询服务
	 * @throws DAOException
	 */
	public void testFindFamilyMemberAndPatientByMpiid() throws DAOException {
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO", FamilyMemberDAO.class);
		
		String mpiid = "4028811f4bce1c7f014bce1cc5fa0000";
		
		List<FamilyMemberAndPatient> d = dao.findFamilyMemberAndPatientByMpiid(mpiid);
		for(int i=0;0<d.size();i++){
			System.out.println(JSONUtils.toString(d.get(i)));
		}
		
	}
	
	/**
	 * 家庭成员增加服务
	 * @throws DAOException
	 */
	public void testAddFamilyMember() throws DAOException{
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO", FamilyMemberDAO.class);
		FamilyMember r = new FamilyMember();
		r.setMpiid("2c9081814cd4ca2d014cd4ddd6c90000");
		r.setMemberMpi("2c9081814cd4ca2d014cd4ddd6c90000");
		r.setRelation("4");
		dao.addFamilyMember(r);
	}
	
	/**
	 * 删除一个家庭成员服务
	 * @throws DAOException
	 */
	public void testDelFamilyMember()throws DAOException{
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO",FamilyMemberDAO.class);
	    FamilyMember r =new FamilyMember();
	    r.setMpiid("4028811f4bce1c7f014bce1cc5fa0000");
		r.setMemberMpi("402881f04bc9baec014bc9bbb0c40000");
		dao.delFamilyMember(r);
	}
	
	/**
	 * 根据mpiId和memberMpi查询家庭成员
	 */
	public void testFindByMpiIdAndMemberMpi(){
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO",FamilyMemberDAO.class);
		String mpiId="4028811f4bce1c7f014bce1cc5fa0000";
		String memberMpi = "402881f04bc9baec014bc9bbb0c40000";
		FamilyMember familyMember = dao.getByMpiIdAndMemberMpi(mpiId, memberMpi);
		System.out.println(JSONUtils.toString(familyMember));
	}
	
	/**
	 * 家庭成员关系字典
	 * @author ZX
	 * @date 2015-8-7  上午11:06:54
	 */
	public void testGetRelation(){
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO",FamilyMemberDAO.class);
		System.out.println(JSONUtils.toString(dao.getRelation()));
	}
	
	/**
	 * 家庭成员列表(包括自己)
	 * 
	 * @author luf
	 * @param mpiId
	 *            当前患者主索引
	 * @return List<Patient>
	 */
	public void testFamilyMemberList() {
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO",FamilyMemberDAO.class);
		String mpiId="8a287a564efd9e45014f05fd905f0001";
		List<HashMap<String,Object>> list = dao.familyMemberList(mpiId);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
	}
	
	public void testFindByMpiid() {
		String mpiId="2c9081814cd4ca2d014cd4ddd6c90000";
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO",FamilyMemberDAO.class);
		List<FamilyMember> members = dao.findByMpiid(mpiId);
		System.out.println(JSONUtils.toString(members));
	}
	
	public void testDelFamilyMemberByMemberId() {
		FamilyMemberDAO dao = appContext.getBean("familyMemberDAO",FamilyMemberDAO.class);
		dao.delFamilyMemberByMemberId(35);
	}
}
