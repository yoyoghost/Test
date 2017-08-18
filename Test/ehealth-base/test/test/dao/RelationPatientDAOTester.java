package test.dao;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.RelationPatientDAO;
import eh.entity.mpi.RelationDoctor;

public class RelationPatientDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static RelationPatientDAO dao;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("relationPatientDAO", RelationPatientDAO.class);
	}
	
	public void testCreate() throws DAOException{
		RelationDoctor r = new RelationDoctor();
		r.setDoctorId(40);
		r.setMpiId("98093821094321kjk");
		
		dao.save(r);
		
		System.out.println(r.getRelationDoctorId());
	}
	
	/**
	 * 查询医生关注的病人列表(全部关注病人)
	 * @return
	 */
	public void testFindByDoctorIdAndRelationType() {
		Integer doctorId = 40;
		Integer relationType = 2;
		long start = 0;
		List<RelationDoctor> relationDoctors = dao.findByDoctorIdAndRelationType(doctorId, relationType, start);
		System.out.println(JSONUtils.toString(relationDoctors));
	}
	
	/**
	 * 根据mpiId和doctorId查询
	 * @return
	 */
	public void testGetByMpiidAndDoctorId() {
		String mpiId = "2c9081814cc3ad35014cc3e0361f0000";
		Integer doctorId = 40;
		System.out.println(JSONUtils.toString(dao.getByMpiidAndDoctorId(mpiId, doctorId)));
	}
	
	/**
	 * 添加关注
	 * @author ZX
	 * @date 2015-6-7  下午6:14:40
	 */
	public void testAddRelationPatient(){
		RelationDoctor relationDoctor = new RelationDoctor();
		String mpiId="2c9081824cc3552a014cc3a9a0120002";//黄伊瑾
		int doctorId=40;
		relationDoctor.setMpiId(mpiId);
		relationDoctor.setDoctorId(doctorId);
		System.out.println(dao.addRelationPatient(relationDoctor));
	}
	
	/**
	 * 关注病人(添加标签)
	 * @author LF
	 * @return
	 */
	public void testFocuseOnThePatient() {
		RelationDoctor relationPatient = new RelationDoctor();
		String mpiid="2c9081814cd4e87d014cd5ba70180000";//王宁武
		int doctorId=40;
		relationPatient.setMpiId(mpiid);
		relationPatient.setDoctorId(doctorId);
		relationPatient.setNote("lalalalala,../");
		List<String> labelNames = new ArrayList<String>();
		labelNames.add("好友");
		labelNames.add("朋友");
//		System.out.println(dao.focuseOnThePatient(relationPatient, null));
		System.out.println(dao.focuseOnThePatient(relationPatient, labelNames));
	}
	
	/**
	 * 取消关注
	 * @author ZX
	 * @date 2015-6-7  下午6:14:48
	 */
	public void testDelRelationPatient(){
		String mpiid="2c9081814cc3ad35014cc3e0361f0000";//黄伊瑾
		int doctorId=40;
		Boolean b = dao.delRelationPatient(mpiid, doctorId);
		System.out.println(b);
	}
	
	/**
	 * 判断是否关注
	 * @author ZX
	 * @date 2015-6-7  下午6:14:56
	 */
	public void testIsRelationPatient(){
		String mpiid="8a287a564d286405014d289ac6c00000";//黄伊瑾
		int doctorId=40;
		System.out.println(dao.isRelationPatient(mpiid, doctorId));
	}
	
	/**
	 * 关注病人查询服务（供前台调用）
	 * @author LF
	 * @return
	 */
	public void testFindRelationPatientAndLabels() {
		Integer doctorId = 40;
		String labelName = "";
//		String labelName = "";
		int start = 0;
		System.out.println(JSONUtils.toString(dao.findRelationPatientAndLabels(doctorId, labelName, start)));
	}
	
	/**
	 * 获取医生和病人的过期签约记录（供其它调用）
	 * @author luf
	 */
	public void testGetOffSignByMpiAndDoc() {
		String mpiId="8a287a564d286405014d289ac6c00000";
		Integer doctorId = 40;
		System.out.println(JSONUtils.toString(dao.getOffSignByMpiAndDoc(mpiId, doctorId)));
	}
}
