package test.dao;

import ctd.util.JSONUtils;
import eh.base.dao.BussDescriptionDAO;
import eh.entity.base.BussDescription;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

/**
 * Created by Administrator on 2016/11/10.
 */
public class BussDescriptionDAOTester extends TestCase{

    private static ClassPathXmlApplicationContext appContext;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }
    private static BussDescriptionDAO dao = appContext.getBean("bussDescriptionDAO",BussDescriptionDAO.class);



    public void testFindByAll(){
        List<BussDescription> bd = dao.findByAll();
        System.out.println(JSONUtils.toString(bd));
    }

    public void testGetByOrganId(){
        int organId = 1;
        BussDescription bd = dao.getByOrganId(organId);
        System.out.println(JSONUtils.toString(bd));
    }

    public void testQueryDescriptionForWX(){
        int organId = 1;
//        String des = dao.queryDescriptionForWX(organId);
//        System.out.println(des);
    }
}
