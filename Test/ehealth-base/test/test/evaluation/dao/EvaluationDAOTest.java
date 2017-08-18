package test.evaluation.dao;

import com.alibaba.fastjson.JSON;
import ctd.persistence.DAOFactory;
import eh.base.dao.DepartmentDAO;
import eh.entity.base.PatientFeedback;
import eh.evaluation.dao.EvaluationDAO;
import junit.framework.TestCase;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by zhangsl on 2016/11/11.
 */
public class EvaluationDAOTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }


}