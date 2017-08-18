package test.dao;

import ctd.util.JSONUtils;
import eh.base.dao.ChemistDAO;
import eh.base.service.ChemistService;
import eh.entity.base.Chemist;
import eh.utils.DateConversion;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by zhongzx on 2016/6/29 0029.
 * 药师相关服务测试类
 */
public class ChemistDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    static {
        appContext = new ClassPathXmlApplicationContext("classpath:spring.xml");
    }
    private ChemistService service = appContext.getBean("chemistService", ChemistService.class);

    public void testAdd(){
        Chemist c = new Chemist();
        c.setName("王超");
        c.setStatus(1);
        c.setOrgan(1);
        c.setGender(1);
        c.setMobile("13020008002");
        c.setIdNumber("370405198703210236");
        c.setBirthDay(DateConversion.getCurrentDate("1987-03-21 00:00:00", "yyyy-MM-dd HH:mm:ss"));

        service.addChemist(c);
    }

    public void testCreateUser(){
        service.createChemistUser(2);
    }

    public void testGet(){
        ChemistDAO dao = appContext.getBean("chemistDAO", ChemistDAO.class);
        System.out.println(JSONUtils.toString(dao.findByMobile("",1)));
    }
}
