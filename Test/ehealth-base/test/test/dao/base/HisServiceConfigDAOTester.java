package test.dao.base;

import ctd.persistence.DAOFactory;
import eh.base.constant.ServiceType;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.bus.AppointSource;
import eh.utils.DateConversion;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class HisServiceConfigDAOTester extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("spring.xml");
    }

    public void testIsServiceEnable() {
        HisServiceConfigDAO dao = appContext.getBean("serviceConfigDAO",HisServiceConfigDAO.class);
//        assertTrue(dao.isServiceEnable(1, ServiceType.MEDFILING));
        boolean res = dao.isServiceEnable(1, ServiceType.TRANSFER);
        System.out.print(res);

        AppointSourceDAO sourceDao = DAOFactory.getDAO(AppointSourceDAO.class);
//        Integer sourceId = a.getAppointSourceId();
        AppointSource source = sourceDao.getById(1507274);
        Integer fromFlag = source.getFromFlag();
        if (fromFlag == null || fromFlag.intValue() == 0) {
//            return true;
            System.out.print(11);
        }
//        return false;
    }

    public void testGetConfirmTimeLimit() {
        HisServiceConfigDAO dao = appContext.getBean("serviceConfigDAO", HisServiceConfigDAO.class);
        System.out.println(dao.getConfirmTimeLimit(1));
    }

    public void testIsOverConfirmTimeLimit() {
        HisServiceConfigDAO dao = appContext.getBean("serviceConfigDAO", HisServiceConfigDAO.class);
        System.out.println(dao.isOverConfirmTimeLimit(1, 1, DateConversion.getCurrentDate("2016-8-14", "yyyy-MM-dd")));
    }
    public void testIsCanappointAndCanFIle(){
        HisServiceConfigDAO dao = appContext.getBean("serviceConfigDAO", HisServiceConfigDAO.class);

        dao.canAppointAndCanFile(1,"2c9081814cc3ad35014cc54fca420003");
    }
}
