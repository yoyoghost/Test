package test.evaluation.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import eh.base.constant.ErrorCode;
import eh.entity.evaluation.PatientFeedbackTab;
import eh.evaluation.constant.EvaluationConstant;
import eh.evaluation.dao.PatientFeedbackTabDAO;
import eh.evaluation.service.EvaluationService;
import eh.evaluation.service.PatientFeedbackTabService;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Created by Administrator on 2016/11/11.
 */
public class EvaluationServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("spring.xml");
    }

    public void testEvaluationConsultForHealth() throws Exception {
        EvaluationService service = appContext.getBean("eh.evaluationService", EvaluationService.class);
        String evaText = "测试评价内容";
        Double evaValue = 9.8;
        Integer consultId = 367;
        System.out.println(service.evaluationConsultForHealth(consultId, evaValue, evaText));
    }


    public void testAddFeedbackTab() {

        PatientFeedbackTabService feedbackTabService = appContext.getBean(
                "eh.patientFeedbackTabService", PatientFeedbackTabService.class);
//        feedbackTabService.addFeedbackTab(1, 4, "全部", 1);
        //预约的评价标签为医生
//        String[][] tabDescList = {{"态度很差","没有耐心","没有帮助","不专业"},
//                {"态度较差","没有耐心","没有帮助","不专业"},
//                {"态度温和","讲解细致","有责任心","意见有帮助"},
//                {"态度温和","讲解细致","十分敬业","意见比较有帮助"},
//                {"热心亲切", "讲解细致", "十分敬业", "妙手回春"}};

//        咨询的评价标签为医生
//        String[][] tabDescList = {{"态度很差", "没有耐心", "不专业", "没有帮助"},
//                {"回复较慢", "态度较差", "没有耐心", "没有帮助"},
//                {"回复速度及时", "回答专业", "讲解细致", "意见有帮助"},
//                {"回复速度及时", "回答比较专业", "讲解细致", "意见比较有帮助"},
//                {"回复速度很快", "回答很专业", "讲解细致", "妙手回春"}};

        //医院的评价标签
        String[][] tabDescList = {{"就诊环境很差", "等待时间长", "工作人员很差", "服务设施很差"},
                {"就诊环境较差", "等待时间一般", "工作人员一般", "服务设施较差"},
                {"就诊环境一般", "等待时间一般", "工作人员一般", "服务设施一般"},
                {"就诊环境很好", "就诊很方便", "工作人员比较好", "服务设施比较好"},
                {"就诊环境很好", "就诊很方便", "工作人员很好", "服务设施很好"}};

        for (int i = 0; i < (tabDescList.length); i++) {
            for (int j = 0; j < (tabDescList[i].length); j++) {
                feedbackTabService.addFeedbackTab(2, 4, tabDescList[i][j], (i + 1));
            }
        }
    }

}