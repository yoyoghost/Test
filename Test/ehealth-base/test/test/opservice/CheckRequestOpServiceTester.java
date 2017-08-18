package test.opservice;


import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.JSONUtils;
import eh.bus.dao.CheckRequestDAO;
import eh.entity.base.OrganCheckItem;
import eh.entity.bus.CheckRequest;
import eh.op.service.CheckRequestOpService;
import eh.utils.DateConversion;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;
import java.util.List;

/**
 * Created by houxr on 2016/5/25.
 * 医技检查相关服务测试
 */
public class CheckRequestOpServiceTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static CheckRequestOpService service;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service = appContext.getBean("checkRequestOpService", CheckRequestOpService.class);
    }

    public void testqueryCheckOrganItemByOrganId() {
        List<OrganCheckItem> list = service.findOrganCheckItemByOrganId(1);
        System.out.println(JSONUtils.toString(list));
    }

    public void testQueryCheckListForOP() {
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckRequest checkRequest = checkRequestDAO.get(49);
        final Date startDate= DateConversion.getCurrentDate("2016-03-01","yyyy-MM-dd");
        final Date endDate=DateConversion.getCurrentDate("2016-03-31","yyyy-MM-dd");
        QueryResult<CheckRequest> queryCheckListForOP = service.queryCheckListForOP(2, startDate, endDate, checkRequest, 0, 10);
        System.out.println(JSONUtils.toString(queryCheckListForOP));
    }
}
