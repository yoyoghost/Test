package test.dao.mpi;

import com.alibaba.druid.support.json.JSONUtils;
import ctd.controller.exception.ControllerException;
import eh.mpi.constant.SignInitiatorConstant;
import eh.mpi.dao.SignPatientLabelDAO;
import junit.framework.TestCase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dingding on 2016/10/10.
 */
public class SignPatientLabelDAOTester extends TestCase{

    private static final Log logger = LogFactory.getLog(SignPatientLabelDAOTester.class);

    private static final ClassPathXmlApplicationContext appContext = new ClassPathXmlApplicationContext("test/spring.xml");

    private SignPatientLabelDAO dao = appContext.getBean(SignPatientLabelDAO.class);



    /**
     * 测试医生发起上门签约的时候或者患者端申请签约的时候，插入签约记录居民类型关系表数据
     */
    public void testSignResidentType() throws ControllerException {
        Integer signRecordId = 1000;
        Integer doctorId = 1182;
        String MpiId = "2c9081814cc3ad35014cc4d1b6140002";
        List<String> residentType = new ArrayList<>();
        residentType.add("2");
        residentType.add("3");
        residentType.add("5");
        boolean flag = dao.saveSignResidentType(signRecordId, doctorId, MpiId, residentType, SignInitiatorConstant.DROP_IN_SIGN);
        logger.info(flag);
    }

    /**
     * 测试根据签约记录表主键查询患者标签
     */
    public void testFindSplLabelBySignRecordId(){
        Integer signRecordId = 272;
        List<Integer> list = dao.findSplLabelBySignRecordId(signRecordId);
        logger.info(JSONUtils.toJSONString(list));
    }
}
