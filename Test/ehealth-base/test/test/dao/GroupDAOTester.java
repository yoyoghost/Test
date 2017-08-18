package test.dao;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ctd.util.JSONUtils;
import eh.entity.msg.Group;
import eh.msg.dao.GroupDAO;

public class GroupDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static GroupDAO dao;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("groupDAO", GroupDAO.class);
	}

	/**
	 * 创建群组
	 * 
	 * @author ZX
	 * @date 2015-6-11 下午4:13:24
	 */
	public void testCreatMeetClinckGroup() {
		int meetClinicId = 271;
		dao.creatMeetClinckGroup(meetClinicId);
	}

	/**
	 * 关闭群组
	 * 
	 * @author ZX
	 * @date 2015-6-11 下午4:13:50
	 */
	public void testCloseMeetClinckGroup() {
		int meetClinicId = 271;
		dao.closeMeetClinckGroup(meetClinicId);
	}

	/**
	 * 根据业务号获取群组信息
	 * 
	 * @author ZX
	 * @date 2015-6-11 下午5:42:26
	 */
	public void testGetByBussTypeAndBussId() {
		int meetClinicId = 271;
		Group group = dao.getByBussTypeAndBussId(2, meetClinicId);
		System.out.println(JSONUtils.toString(group));
	}

	/**
	 * 更新状态
	 * 
	 * @author ZX
	 * @date 2015-6-11 下午6:00:14
	 */
	public void testUpdateStatusByGroupId() {
		dao.updateStatusByGroupId(0, "143416434477400");
	}

	/**
	 * 获取一个用户参与的所有群组
	 * 
	 * @author LF
	 * @return
	 */
	public void testGetAllChatgroupsOfOne() {
		// String userName = "doctor_17";
		// String password = "doctor123";
		// Easemob.login(userName, password);

		System.out.println(dao.getAllChatgroupsOfOne(17).toString());
	}

	/**
	 * 获取群组中的所有成员
	 * 
	 * @author ZX
	 * @date 2015-6-15 下午4:55:51
	 */
	public void testGetAllMemberssByGroupId() {
		Integer bussType = 2;
		Integer bussId = 2039;

		ObjectNode ob = dao.getAllMemberssByGroupId(bussType, bussId);
		System.out.println(JSONUtils.toString(ob));
	}

	/**
	 * 群组加人[单个]
	 * 
	 * @author ZX
	 * @date 2015-6-15 下午5:14:45
	 */
	public void testAddUserToGroup() {
		Integer bussType = 2;
		Integer bussId = 2035;
		Integer doctorId = 4728;
		ObjectNode ob = dao.addUserToGroup(bussType, bussId, doctorId);
		System.out.println(JSONUtils.toString(ob));
	}

	/**
	 * 群组减人
	 * 
	 * @author ZX
	 * @date 2015-6-15 下午5:18:23
	 */
	public void testDeleteUserFromGroup() {
		Integer bussType = 2;
		Integer bussId = 271;
		Integer doctorId = 1180;
		ObjectNode ob = dao.deleteUserFromGroup(bussType, bussId, doctorId);
		System.out.println(JSONUtils.toString(ob));
	}

	/**
	 * 关闭群组
	 * 
	 * @author ZX
	 * @date 2015-6-17 下午8:08:57
	 */
	public void testCloseIMGroup() {
		String groupId = "143454161645329334522";
		ObjectNode ob = dao.closeIMGroup(groupId);
		System.out.println(JSONUtils.toString(ob));
	}

	public void testSplit() {
		String nick = "dev_meetclinic_1";
		String[] nickArray = nick.split("_");
		System.out.println(JSONUtils.toString(nickArray));
	}
}
