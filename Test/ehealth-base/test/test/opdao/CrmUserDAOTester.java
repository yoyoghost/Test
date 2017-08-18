package test.opdao;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.entity.bus.CRMUser;
import eh.op.dao.CrmUserDAO;
import junit.framework.TestCase;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by houxr on 2016/6/17.
 */
public class CrmUserDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    private static CrmUserDAO crmUserDao = appContext.getBean("crmUserDAO", CrmUserDAO.class);


    public void testQueryListCRMUserForOp() {
        String startString = "2015-01-01";
        Date startTime = new StringToDate().convert(startString);
        String endString = "2016-12-31";
        Date endTime = new StringToDate().convert(endString);
        QueryResult<CRMUser> crmUserList = crmUserDao.queryListCRMUser(null, null, null, 20, 25, null, "c", 1, startTime, endTime, 0, 20);
        System.out.println("==总计:" + crmUserList.getItems().size() + "\n" + JSONUtils.toString(crmUserList) + "\n\n");
    }

    public void testQueryCRMUserForSex() {
        String startString = "2015-01-01";
        Date startTime = new StringToDate().convert(startString);
        String endString = "2016-12-31";
        Date endTime = new StringToDate().convert(endString);
        List<Object[]> result = crmUserDao.countCRMUserBySex(null, null, null, 20, 25, null, "c", 1, startTime, endTime);
        System.out.println("============" + JSONUtils.toString(result) + "\n");
        Map<String, Long> resultMap = new HashedMap();
        if (result.size() != 0) {
            for (Object[] objects : result) {
                if (StringUtils.equals((String) objects[0], "1")) {
                    resultMap.put("male", (Long) objects[1]);
                }
                if (StringUtils.equals((String) objects[0], "2")) {
                    resultMap.put("female", (Long) objects[1]);
                }
            }
        } else {
            resultMap.put("male", 0L);
            resultMap.put("female", 0L);
        }
        System.out.println("============" + JSONUtils.toString(resultMap) + "\n");
    }

    public void testQueryCRMUserForPayed() {
        String startString = "2015-01-01";
        Date startTime = new StringToDate().convert(startString);
        String endString = "2016-12-31";
        Date endTime = new StringToDate().convert(endString);
        Long a = crmUserDao.countPayedCRMUser(null, null, null, 20, 25, null, "c", 1, startTime, endTime);
        System.out.println("==总计:" + a + "\n");
    }

    public void testQueryCRMUserForConsult() {
        String startString = "2015-01-01";
        Date startTime = new StringToDate().convert(startString);
        String endString = "2016-12-31";
        Date endTime = new StringToDate().convert(endString);
        List<Object[]> countCount = crmUserDao.countConsultCRMUser(null, null, null, 20, 45, null, "c", 1, startTime, endTime);
        System.out.println("=====原值=======" + JSONUtils.toString(countCount) + "\n");
        Map<String, Object> resultMap = new HashedMap();
        Long telConsultCount = 0L;
        Double telConsultAmount = 0.0d;
        Long factTelConsultCount = 0L;

        Long msgConsultCount = 0L;
        Double msgConsultAmount = 0.0d;
        Long factMsgConsultCount = 0L;
        if (countCount.size() != 0) {//[[1,62,335,5798.1],[2,20,158,13.0]]
            if (countCount.size() != 0) { //返回值:[[1,62(参与量),335(电话咨询量),5798.1(电话咨询总价)],[2,20(参与量),158(图文咨询量),13.0(图文咨询总价)]]
                for (Object[] objects : countCount) {
                    if ((Integer)objects[0] == 1) {
                        telConsultCount = (Long) objects[1];
                        factTelConsultCount = (Long) objects[2];
                        telConsultAmount = (Double) objects[3];
                        resultMap.put("telConsultCount", telConsultCount);
                        resultMap.put("factTelConsultCount", factTelConsultCount);
                        resultMap.put("telConsultAmount", telConsultAmount);
                    }
                    if ((Integer) objects[0] == 2) {
                        msgConsultCount = (Long) objects[1];
                        factMsgConsultCount = (Long) objects[2];
                        msgConsultAmount = (Double) objects[3];
                        resultMap.put("msgConsultCount", msgConsultCount);
                        resultMap.put("factMsgConsultCount", factMsgConsultCount);
                        resultMap.put("msgConsultAmount", msgConsultAmount);
                    }
                }
            } else {
                resultMap.put("telConsultCount", telConsultCount);
                resultMap.put("factTelConsultCount", factTelConsultCount);
                resultMap.put("telConsultAmount", telConsultAmount);
                resultMap.put("msgConsultCount", msgConsultCount);
                resultMap.put("factMsgConsultCount", factMsgConsultCount);
                resultMap.put("msgConsultAmount", msgConsultAmount);
            }
            System.out.println("============" + JSONUtils.toString(resultMap) + "\n");
        }
    }
}
