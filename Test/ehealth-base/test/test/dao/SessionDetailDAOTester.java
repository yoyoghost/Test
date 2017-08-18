package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.entity.msg.SessionDetail;
import eh.msg.dao.SessionDetailDAO;

public class SessionDetailDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static SessionDetailDAO dao;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("sessionDetailDAO", SessionDetailDAO.class);
	}
	
	public void testFindSessionDetail(){
		Integer sessionId = 7;
		Integer memberType = 1;
		Integer memberId = 36;
//		List<SessionDetail> sessionDetails = dao.findSessionDetail(sessionId, memberType, memberId);
//		System.out.println(JSONUtils.toString(sessionDetails));
	}
	
	/**
	 * 会话明细查询服务
	 * @author ZX
	 * @date 2015-4-12  下午2:07:22
	 */
	public void testQuerySessionDetail(){
		Integer sessionId = 2891;
		Integer memberType = 1;
		Integer memberId = 1198;
		int page=1;
		List<SessionDetail> sessionDetails = dao.querySessionDetail(sessionId, memberType, memberId, page);
		System.out.println(JSONUtils.toString(sessionDetails));
	}
	
	/**
	 * 新增系统消息服务
	 * @author ZX
	 * @date 2015-4-9 下午5:34:37
	 * @param publisherId 消息订阅号（1系统提醒……）
	 * @param message 消息内容（消息体），格式相见下面的说明。
	 * @param memberType 接收者类型（1医生 2患者），如果是统一会话号类消息则可为0
	 * @param urtid 接收者编号（医生或患者的UrtID），如果是统一会话号类消息则可为0
	 */
	public void testAddSysMessage(){
		int publisherId=1;
		String message="测试会诊消息订阅";
		int memberType=1;
		int urtid=36;
//		int sessionId=dao.addSysMessage(publisherId,message, memberType, urtid);
		
//		System.out.println(sessionId);
	}
	
	/**
	 * 根据用户id新增系统消息服务
	 * @author ZX
	 * @date 2015-4-10 下午2:06:12
	 * @param publisherId  消息订阅号（1系统提醒……）
	 * @param message  消息内容（消息体），格式相见下面的说明。
	 * @param memberType 接收者类型（1医生 2患者），如果是统一会话号类消息则可为0
	 * @param managerUnit 
	 * @param userId  用户id
	 * @return
	 */
	public void testAddSysMessageByUserId(){
		int publisherId=1;
		String message="测试";
		int memberType=1;
		String mangerUnit="eh";
		String userId="13858043673";
		int sessionId=dao.addSysMessageByUserId(message, memberType, mangerUnit,userId);
		
		System.out.println(sessionId);
	}
	
	/**
	 * 新增欢迎信息
	 * @author ZX
	 * @date 2015-4-24  下午5:36:24
	 */
	public void testSddWelcomeMessage(){
		int memberType=1;
		String tel="18768177768";
		dao.addWelcomeMessage( memberType,tel);
	}

}
