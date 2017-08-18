package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.json.JSONObject;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import ctd.util.JSONUtils;
import eh.entity.msg.SessionAndMember;
import eh.msg.dao.SessionDAO;

public class SessionDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static SessionDAO dao;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao = appContext.getBean("sessionDAO", SessionDAO.class);
    }

    public void testUpdateSession() {

    }

    /**
     * 会话列表查询服务
     *
     * @author LF
     */
    public void testQuerySessionList() {
        Integer memberType = 1;
        Integer memberId = 17;
        List<SessionAndMember> list = dao.querySessionList(memberType, memberId);
        //app端
        System.out.println(JSONUtils.toString(list));

        //pc端
        //System.out.println(JSONUtils.toString(dao.queryPcSessionList(memberType, memberId)));
    }

    public void testQuerySessions() {
        Integer memberType = 1;
        Integer memberId = 17;
        Integer flag = 0;//查询类型--0所有会话 1原生app端 2pc端
        List<SessionAndMember> list = dao.querySessions(memberType, memberId, flag);
        System.out.println(JSONUtils.toString(list));
    }
}
