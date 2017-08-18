package test.dao;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import eh.entity.cdr.Recipe;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.OperationRecordsDAO;
import eh.bus.dao.TransferDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Consult;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import eh.entity.bus.Transfer;
import eh.entity.mpi.Patient;

public class OperationRecordsDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static OperationRecordsDAO dao;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("operationRecordsDAO",
				OperationRecordsDAO.class);
	}

	/**
	 * 获取常用病人MPIID(前10条)
	 * 
	 * @author ZX
	 * @date 2015-7-6 上午10:51:36
	 */
	public void testFindMpiIdByRequestDoctor() {
		int requestDocId = 1182;
		int start = 0;
		List<String> list = dao.findMpiIdByRequestDoctor(requestDocId, start);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 获取常用病人信息
	 * 
	 * @author ZX
	 * @date 2015-7-6 上午10:52:45
	 */
	public void testFindUsedPatients() {
		int requestDocId = 1176;
		List<Patient> list = dao.findUsedPatients(requestDocId);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 根据姓名检索常用病人
	 * 
	 * @author ZX
	 * @date 2015-7-6 上午10:55:25
	 */
	public void testFindMpiIdByRequestDoctorLike() {
		int requestDocId = 1180;
		int start = 0;
		String Name = "%张%";
		List<String> list = dao.findMpiIdByRequestDoctorLike(requestDocId,
				Name, start);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 根据姓名检索常用病人
	 * 
	 * @author ZX
	 * @date 2015-7-6 上午10:57:10
	 */
	public void testFindPatientByRequestDoctorLike() {
		int requestDocId = 1182;
		String Name = "王";
		int start = 0;
		List<Patient> list = dao.findPatientByRequestDoctorLike(requestDocId,
				Name, start);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 获取常用医生列表
	 * 
	 * @author ZX
	 * @date 2015-7-6 下午12:00:37
	 */
	public void testFindDocIdByMpiId() {
		String mpiId = "2c9081814cd4ca2d014cd4ddd6c90000";
		int start = 0;
		List<Integer> list = dao.findDocIdByMpiId(mpiId, start);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 获取常用医生列表
	 * 
	 * @author ZX
	 * @date 2015-7-6 下午12:02:03
	 */
	public void testFindDocByMpiId() {
		String mpiId = "2c9081814cd4e87d014cd5ba70180000";
		int start = 0;
		List<Doctor> list = dao.findDocByMpiId(mpiId, start);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 获取常用医生列表(筛选出医生设置打开的医生列表)
	 * 
	 * @author ZX
	 * @date 2015-7-6 下午12:02:03
	 */
	public void testFindDocByMpiIdWithServiceType() {
		String mpiId = "2c9081814cd4ca2d014cd4ddd6c90000";
		int start = 0;
		int bussType = 3;// 1:转诊；2：会诊；3：咨询; 4:预约
		List<Doctor> list = dao.findDocByMpiIdWithServiceType(mpiId, start,
				bussType);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 保存转诊记录日志
	 * 
	 * @author ZX
	 * @date 2015-7-3 下午5:31:03
	 */
	public void testSaveOperationRecordsForTransfer() {
		int i = 664;
		TransferDAO bdd = appContext.getBean("transferDAO", TransferDAO.class);
		Transfer trans = bdd.get(i);
		//患者申请的转诊赋值
//		trans.setRequestOrgan(null);
//		trans.setRequestDepart(null);
//		trans.setRequestDoctor(null);
//		trans.setRequestMpi(trans.getMpiId());
		dao.saveOperationRecordsForTransfer(trans);
	}

	public void testSaveOperationRecordsForMeetClinic() {
		int meetClinicId = 248;
		MeetClinicDAO dao1 = appContext.getBean("meetClinicDAO",
				MeetClinicDAO.class);
		List<MeetClinicResult> list = dao1.findByMeetClinicId(meetClinicId);
		MeetClinic mc = dao1.getByMeetClinicId(meetClinicId);
		dao.saveOperationRecordsForMeetClinic(mc, list);
	}

	/**
	 * 保存咨询记录日志
	 * 
	 * @author ZX
	 * @date 2015-7-6 下午12:29:05
	 */
	public void testSaveOperationRecordsForConsult() {
		ConsultDAO dao1 = appContext.getBean("consultDAO", ConsultDAO.class);
		for (int i = 580; i < 602; i++) {
			Consult consult = dao1.getById(i);
			dao.saveOperationRecordsForConsult(consult);
		}
	}

	/**
	 * 保存咨询记录日志
	 * 
	 * @author ZX
	 * @date 2015-7-6 下午3:30:27
	 */
	public void testSaveOperationRecordsForAppoint() {
		AppointRecordDAO dao1 = appContext.getBean("appointRecordDAO",
				AppointRecordDAO.class);
			AppointRecord ar = dao1.getByAppointRecordId(704);
			//患者申请
			ar.setAppointUser(ar.getMpiid());
			ar.setAppointOragn(null);
			dao.saveOperationRecordsForAppoint(ar);
	}

	public void testfindDocIdByRequestDoctor() {
		List<Integer> list = dao.findDocIdByRequestDoctor(1182, 0);
		System.out.println(JSONUtils.toString(list));
	}

	public void testfindDocByRequestDocIdWithServiceType() {
		List<Doctor> list = dao
				.findDocByRequestDocIdWithServiceType(1182, 0, 2);
		System.out.println(JSONUtils.toString(list));
	}

	// //获取最近使用但未被关注的病人
	// public void testFindUnRelaPatientsPage(){
	// List<Patient> patients =dao.findUnRelaPatientsPage(40);
	// for(Patient patient : patients){
	// System.out.println(JSONUtils.toString(patient));
	// }
	// }

	/**
	 * 获取常用病人信息(分页)-不分是否关注
	 * 
	 * @author zhangx
	 * @date 2015-10-22 下午4:03:09
	 */
	public void testFindUsedPatientsPage() {
		int requestDoctor = 1182;
		int start = 0;
		List<Patient> ps = dao.findUsedPatientsPage(requestDoctor, start);
		System.out.println(JSONUtils.toString(ps));
	}

	/**
	 * 获取未关注的常用患者
	 * 
	 * @author zhangx
	 * @date 2015-10-22 下午4:02:57
	 */
	public void testfindUnRelationMpiIdByRequestDoctor() {
		int requestDocId = 1182;
		int start = 0;
		List<String> list = dao.findUnRelationMpiIdByRequestDoctor(
				requestDocId, start);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 获取未关注的常用患者(分页)
	 * 
	 * @author zhangx
	 * @date 2015-10-22 下午4:02:48
	 */
	public void testfindUnRelationUsedPatientsPage() {
		int requestDocId = 1182;
		int start = 0;
		List<Patient> list = dao.findUnRelationUsedPatientsPage(requestDocId,
				start);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 根据姓名检索常用病人
	 * 
	 * @author ZX
	 * @date 2015-7-6 上午10:57:10
	 */
	public void testfindUnRelationPatientByRequestDoctorLike() {
		int requestDocId = 1182;
		String Name = "王";
		int start = 0;
		List<Patient> list = dao.findUnRelationPatientByRequestDoctorLike(
				requestDocId, Name, start);
		System.out.println(JSONUtils.toString(list));
	}

	/**
	 * 按姓名，身份证，手机号三个其中的一个搜索医生的历史患者中符合条件的患者
	 * 
	 * @author zhangx
	 * @date 2015-11-25 下午4:10:15
	 */
	public void testfindAttentionPatients() {
		Integer doctorId = 1182;
		List<Patient> patients = dao.findHistoryPatients(doctorId, "%张%",
				"%331081%", "%187%", 0l, 20l);
		System.out.println(JSONUtils.toString(patients));
	}

	/**
	 * 历史医生列表(健康端)
	 * 
	 * @author luf
	 * @param mpiId
	 *            主索引
	 * @return List<Doctor>
	 */
	public void testFindDocByMpiIdForHealth() {
		String mpiId = "2c9081834f312ae4014f313662cc0000";// 王超
		List<HashMap<String, Object>> list = dao.findDocByMpiIdForHealth(mpiId);
		System.out.println(JSONUtils.toString(list));
		System.out.println(list.size());
	}

	public void testSaveOperationRecordsForRecipe() {
		Recipe recipe = new Recipe();
		recipe.setRecipeId(0);
		recipe.setMpiid("297e42715189b65d015189b712e60000");
		recipe.setDoctor(40);
		recipe.setCreateDate(new Date());
		dao.saveOperationRecordsForRecipe(recipe);
	}
}
