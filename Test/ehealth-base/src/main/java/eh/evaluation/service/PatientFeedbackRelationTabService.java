package eh.evaluation.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.entity.base.PatientFeedback;
import eh.entity.evaluation.PatientFeedbackRelationTab;
import eh.evaluation.constant.EvaluationConstant;
import eh.evaluation.dao.PatientFeedbackRelationTabDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Administrator on 2017/5/12.
 */
public class PatientFeedbackRelationTabService {

    private static final Logger logger = LoggerFactory.getLogger(PatientFeedbackRelationTabService.class);


    /**
     * 增加一条评价的标签
     *
     * @param patientFeedback                评价记录的对象
     * @param patientFeedbackRelationTabList 评价关联的标签
     * @return
     * @author cuill
     */
    @RpcService
    public void addFeedbackRelationTabByFeedback(PatientFeedback patientFeedback, List<PatientFeedbackRelationTab> patientFeedbackRelationTabList) {
        logger.info("增加的评价主键为:[{}]", patientFeedback.getFeedbackId());
        PatientFeedbackRelationTabDAO patientFeedbackRelationTabDAO = DAOFactory.getDAO(PatientFeedbackRelationTabDAO.class);
        if (ObjectUtils.isEmpty(patientFeedbackRelationTabList)) {
            return;
        }
        Iterator<PatientFeedbackRelationTab> patientFeedbackRelationTabIterator = patientFeedbackRelationTabList.iterator();
        PatientFeedbackRelationTab patientFeedbackRelationTab = null;
        while (patientFeedbackRelationTabIterator.hasNext()) {
            patientFeedbackRelationTab = patientFeedbackRelationTabIterator.next();
            patientFeedbackRelationTab = this.wrapFeedbackRelationTabByFeedback(patientFeedback, patientFeedbackRelationTab);
            patientFeedbackRelationTabDAO.save(patientFeedbackRelationTab);
        }
    }

    /**
     * 根据评价的主键来获取对应的标签列表
     *
     * @param feedbackId 评价的主键
     * @return
     * @author cuill
     * @date 2017/6/1
     */
    @RpcService
    public List<PatientFeedbackRelationTab> patientFeedbackRelationTabList(Integer feedbackId) {
        PatientFeedbackRelationTabDAO feedbackRelationTabDAO = DAOFactory.getDAO(PatientFeedbackRelationTabDAO.class);
        List<PatientFeedbackRelationTab> feedbackRelationTabsList = feedbackRelationTabDAO.queryFeedbackRelationTabByFeedbackID(feedbackId);
        return this.wrapFeedbackRelationTabListBySelf(feedbackRelationTabsList);
    }


    /**
     * 当评价被删除的时候,对应的关联标签应该也要被删除
     *
     * @param feedbackId 要删除的评价主键
     * @date 2017/7/5
     * @author cuill
     */
    @RpcService
    public void delFeedbackRelationTabByFeedbackId(Integer feedbackId) {

        logger.info("delFeedbackRelationTabByFeedbackId删除的评价主键为:[{}]", feedbackId);
        PatientFeedbackRelationTabDAO feedbackRelationTabDAO = DAOFactory.getDAO(PatientFeedbackRelationTabDAO.class);
        PatientFeedbackCountService feedbackCountService = AppContextHolder.getBean(
                "eh.patientFeedbackCountService", PatientFeedbackCountService.class);

        List<PatientFeedbackRelationTab> feedbackRelationTabList = feedbackRelationTabDAO.queryFeedbackRelationTabByFeedbackID(feedbackId);

        for (PatientFeedbackRelationTab feedbackRelationTab : feedbackRelationTabList) {
            feedbackCountService.delPatientFeedbackByFeedbackTab(feedbackRelationTab);
            feedbackRelationTab.setStatus(0);
            feedbackRelationTabDAO.update(feedbackRelationTab);
        }
    }

    /**
     * 包装feedbackRelationTab对象,只返回标签图腾
     *
     * @param feedbackRelationTab 数据库获取的改对象,有很多不必要的值
     * @return
     * @author cuill
     * @date 2017/6/1
     */
    private PatientFeedbackRelationTab wrapFeedbackRelationTabBySelf(PatientFeedbackRelationTab feedbackRelationTab) {
        PatientFeedbackRelationTab returnFeedbackRelationTab = new PatientFeedbackRelationTab();
        if (!StringUtils.isEmpty(feedbackRelationTab.getTabTotem())) {
            returnFeedbackRelationTab.setTabTotem(feedbackRelationTab.getTabTotem());
        }
        return returnFeedbackRelationTab;
    }

    /**
     * 包装包装feedbackRelationTab对象数组,只返回带有标签图腾的feedbackRelationTab对象
     *
     * @param feedbackRelationTabList 需要包装的标签数组
     * @return
     * @author cuill
     * @date 2017/6/1
     */
    private List<PatientFeedbackRelationTab> wrapFeedbackRelationTabListBySelf(List<PatientFeedbackRelationTab> feedbackRelationTabList) {
        List<PatientFeedbackRelationTab> returnFeedbackRelationTabsList = new ArrayList<>();
        PatientFeedbackRelationTab returnFeedbackRelationTab;
        for (PatientFeedbackRelationTab feedbackRelationTab : feedbackRelationTabList) {
            returnFeedbackRelationTab = this.wrapFeedbackRelationTabBySelf(feedbackRelationTab);
            returnFeedbackRelationTabsList.add(returnFeedbackRelationTab);
        }
        return returnFeedbackRelationTabsList;
    }

    /**
     * 包装PatientFeedbackRelationTab对象 ->evaluationTypeId,evaluationType,feedbackId
     *
     * @param patientFeedback            评价记录的对象
     * @param patientFeedbackRelationTab 评价关联的标签
     * @return
     * @author cuill
     */
    private PatientFeedbackRelationTab wrapFeedbackRelationTabByFeedback(PatientFeedback patientFeedback, PatientFeedbackRelationTab patientFeedbackRelationTab) {
        if (!StringUtils.isEmpty(patientFeedback.getEvaluationType())) {
            patientFeedbackRelationTab.setEvaluationType(patientFeedback.getEvaluationType());
        }
        if (!StringUtils.isEmpty(patientFeedback.getEvaluationTypeId())) {
            patientFeedbackRelationTab.setEvaluationTypeId(patientFeedback.getEvaluationTypeId());
        }
        if (!StringUtils.isEmpty(patientFeedback.getFeedbackId())) {
            patientFeedbackRelationTab.setPatientFeedbackId(patientFeedback.getFeedbackId());
        }
        patientFeedbackRelationTab.setStatus(EvaluationConstant.EVALUATION_RELATION_TAB_STATUS_NOT_DEL);
        return patientFeedbackRelationTab;
    }
}
