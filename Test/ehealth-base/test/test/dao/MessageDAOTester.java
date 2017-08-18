package test.dao;

import eh.utils.DateConversion;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.entity.msg.Message;
import eh.msg.dao.MessageDAO;

import java.util.Date;

public class MessageDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static MessageDAO dao;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        dao = appContext.getBean("messageDAO", MessageDAO.class);
    }

    /**
     * 新增数据
     *
     * @author ZX
     * @date 2015-6-26  下午2:01:23
     */
    public void testAddMessage() {
        Message msg = new Message();
        msg.setMessageId("666666");
        msg.setFromUser("1");
        msg.setToUser("ss");
        msg.setMessageTime(98l);
        msg.setIsAcked(1);
        msg.setIsDelivered(1);
        msg.setIsRead(1);
        msg.setIsGroup(1);
        msg.setMessageType("text");
        System.out.println(JSONUtils.toString(msg));
        dao.addMessage(msg);
    }

    /**
     * 根据messageId查询
     *
     * @author ZX
     * @date 2015-6-26  下午2:07:22
     */
//    public void testGetByMessageId() {
//        String msgId = "666666";
//        Message msg = dao.getByMessageId(msgId);
//        System.out.println(JSONUtils.toString(msg));
//    }

    /**
     * 每条数据获取类型进行保存
     *
     * @return
     * @author LF
     */
    public void testSaveChatMessage() {
//		String endTime = DateConversion.getCurrentDate("2016-01-01 00:00:00",
//				"yyyy-MM-dd HH:mm:ss").toString();
        Date startTime = DateConversion.getCurrentDate("2016-01-01 00:00:00",
                "yyyy-MM-dd HH:mm:ss");
        int limit = 100;
        dao.saveChatMessage(startTime, limit);
    }

    public void testSaveChatMessageAfterPull() {
        dao.saveChatMessageAfterPull();
    }

    public void testGetMaxTimeFromMessage() {
        Long time = dao.getMaxTimeFromMessage();
        System.out.println(time);
    }
}
