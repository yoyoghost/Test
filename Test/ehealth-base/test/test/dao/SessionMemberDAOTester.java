package test.dao;

import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import eh.msg.dao.SessionMemberDAO;

public class SessionMemberDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    public void testGetUnRead() {
        Integer sessionId = 7;
        Integer memberType = 1;
        Integer memberId = 36;
        SessionMemberDAO dao = appContext.getBean("sessionMemberDAO", SessionMemberDAO.class);
//		Integer count = dao.getUnRead(sessionId, memberType, memberId);
//		System.out.println(count);
    }

    /**
     * 获取一个用户未读取的消息条数服务
     *
     * @return
     * @author LF
     */
    public void testGetUnRead2() {
        Integer memberType = 1;
        Integer memberId = 17;
        SessionMemberDAO dao = appContext.getBean("sessionMemberDAO", SessionMemberDAO.class);
        Integer count = dao.getUnRead(memberType, memberId);
        System.out.println(count);
    }

    public void testGetUnReadWithFlag() {
        Integer memberType = 1;
        Integer memberId = 17;
        int flag = 2;
        SessionMemberDAO dao = appContext.getBean("sessionMemberDAO", SessionMemberDAO.class);
        int count = dao.getUnReadWithFlag(memberType, memberId, flag);
        System.out.println(count);
    }
}
