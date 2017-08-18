package test.dao;

import ctd.util.JSONUtils;
import eh.bus.dao.CloudClinicQueueDAO;
import eh.entity.bus.CloudClinicQueue;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.HashMap;
import java.util.List;

public class CloudClinicQueueDAOTester extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    private static CloudClinicQueueDAO dao = appContext.getBean("cloudClinicQueueDAO", CloudClinicQueueDAO.class);

    public void testFindMyAndOutQueues() {
        System.out.println(JSONUtils.toString(dao.findMyAndOutQueues(1180)));
    }

    public void testQueuingProgress() {
        System.out.println(JSONUtils.toString(dao.queuingProgress(1178)));
    }

    public void testChangeQueueByIdWithFlag() {
        dao.changeQueueByIdWithFlag(1, 1);
    }

    public void testFindByThree() {
        List<CloudClinicQueue> queues = dao.findByThree(1180, 1178, "2c9081814f45a880014f51184db800a8");
        System.out.println(JSONUtils.toString(queues));
    }

    public void testLineUp() {
        CloudClinicQueue queue = new CloudClinicQueue();
        queue.setClinicType(1);
        queue.setPatientMobile("13173655489");
        queue.setMpiId("2c9081824f45a0ee014f5164cb6a004f");
        queue.setPatientName("王超");
        queue.setRequestDoctor(9536);
        queue.setRequestOrgan(6);
        queue.setTargetDoctor(9537);
        queue.setTargetOrgan(1000017);
        dao.lineUp(queue);
    }

    public void testfindMyAndOutQueuesWithPatient(){
        HashMap<String, Object> map=dao.findMyAndOutQueuesWithPatient(9722);
        System.out.println(JSONUtils.toString(map));
    }
}
