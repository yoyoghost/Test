package eh.evaluation.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.evaluation.PatientFeedbackCount;
import eh.entity.evaluation.PatientFeedbackRelationTab;
import eh.evaluation.constant.EvaluationConstant;
import eh.evaluation.dao.PatientFeedbackCountDAO;
import eh.evaluation.dao.PatientFeedbackRelationTabDAO;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Administrator on 2017/5/18.
 */
public class PatientFeedbackCountService {

    private static final Logger logger = LoggerFactory.getLogger(PatientFeedbackCountService.class);


    /**
     * 当评价审核通过以后,增加统计标签数量的接口
     *
     * @param feedbackId 评价记录的主键
     * @author cuill
     * @date 2017/6/1
     */
    @RpcService
    public void addFeedbackRelationCountByFeedbackId(Integer feedbackId) {

        PatientFeedbackRelationTabDAO feedbackRelationTabDAO = DAOFactory.getDAO(PatientFeedbackRelationTabDAO.class);
        List<PatientFeedbackRelationTab> feedbackRelationTabList = feedbackRelationTabDAO.queryFeedbackRelationTabByFeedbackID(feedbackId);
        Iterator<PatientFeedbackRelationTab> relationTabIterator = feedbackRelationTabList.iterator();
        while (relationTabIterator.hasNext()) {
            PatientFeedbackRelationTab feedbackRelationTab = relationTabIterator.next();
            this.addOrUpdatePatientFeedbackByFeedbackTab(feedbackRelationTab);
        }

    }

    /**
     * 根据评价类型和评价类型的主键获取评价标签和对应的数量
     *
     * @param evaluationType 评价业务类型 用户类型: 1是医生，2是机构
     * @param evaluationId   评价类型对应的主键
     * @return
     * @author cuill
     * @date 2017/6/2
     */
    @RpcService
    public List<PatientFeedbackCount> queryFeedbackCountByEvaluationTypeAndIdNotHaveAll(Integer evaluationType, Integer evaluationId) {
        PatientFeedbackCountDAO feedbackCountDAO = DAOFactory.getDAO(PatientFeedbackCountDAO.class);
        List<PatientFeedbackCount> feedbackCountList = feedbackCountDAO.queryFeedbackCountByEvaluationTypeAndId(evaluationType, evaluationId);
        return this.wrapFeedbackCountListBySelf(feedbackCountList);
    }

    /**
     * 根据评价类型和评价类型的主键获取评价标签和对应的数量
     *
     * @param evaluationType 评价业务类型 用户类型: 1是医生，2是机构
     * @param evaluationId   评价类型对应的主键
     * @return
     * @author cuill
     * @date 2017/6/2
     */
    @RpcService
    public List<PatientFeedbackCount> queryFeedbackCountByEvaluationTypeAndId(Integer evaluationType, Integer evaluationId) {
        PatientFeedbackCountDAO feedbackCountDAO = DAOFactory.getDAO(PatientFeedbackCountDAO.class);
        List<PatientFeedbackCount> feedbackCountList = feedbackCountDAO.queryFeedbackCountByEvaluationTypeAndId(evaluationType, evaluationId);
        //如果获取的标签不为空,size大于0,增加"全部"标签,并且放在第一个位置
        if (!ObjectUtils.isEmpty(feedbackCountList)) {
            PatientFeedbackCount feedbackCount = new PatientFeedbackCount();
            feedbackCount.setTabTotem(EvaluationConstant.EVALUATION_TAB_ALL);
            feedbackCountList.add(0, feedbackCount);
        }
        return this.wrapFeedbackCountListBySelf(feedbackCountList);
    }

    /**
     * 统计医生的标签对应的评价数量
     */
    @RpcService
    public void countFeedbackTabCountForSchedule() {
        PatientFeedbackRelationTabDAO feedbackRelationTabDAO = DAOFactory.getDAO(PatientFeedbackRelationTabDAO.class);

        // 获取评价关联表中医生的列表
        List<Integer> doctorIdList = feedbackRelationTabDAO.queryEvaluationTypeIdByEvaluationType(EvaluationConstant.EVALUATION_TYPE_DOCTOR);
        for (Integer doctorId : doctorIdList) {
            this.updateFeedbackTabCountByDoctorId(doctorId);
        }
    }

    /**
     * 根据医生的主键来更新医生评价的标签对应评价数量
     *
     * @param doctorId 医生的主键
     * @author cuill
     * @date 2017/6/21
     */
    private void updateFeedbackTabCountByDoctorId(final Integer doctorId) {

        logger.info("updateFeedbackTabCountByDoctorId doctorId is [{}]", doctorId);
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                PatientFeedbackRelationTabDAO feedbackRelationTabDAO = DAOFactory.getDAO(PatientFeedbackRelationTabDAO.class);
                PatientFeedbackCountDAO feedbackCountDAO = DAOFactory.getDAO(PatientFeedbackCountDAO.class);

//                逻辑是首先将该医生的评价对应的标签数量置为0,然后获取关联标签列表,计算
                feedbackCountDAO.updateFeedbackCountToInitByTypeAndIdForSchedule(EvaluationConstant.EVALUATION_TYPE_DOCTOR, doctorId);

                List<PatientFeedbackRelationTab> feedbackRelationTabList = feedbackRelationTabDAO.queryFeedbackRelationTabByEvaluationTypeAndId(
                        EvaluationConstant.EVALUATION_TYPE_DOCTOR, doctorId);

                for (PatientFeedbackRelationTab feedbackRelationTab : feedbackRelationTabList) {
                    addOrUpdatePatientFeedbackByFeedbackTab(feedbackRelationTab);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 根据评价关联表更新或者增加评价数量表
     *
     * @param feedbackRelationTab 评价关联标签记录对象
     * @author cuill
     * @date 2017/6/8
     */
    public void addOrUpdatePatientFeedbackByFeedbackTab(PatientFeedbackRelationTab feedbackRelationTab) {

        logger.info("addOrUpdatePatientFeedbackByFeedbackTab增加的评价的主键是:[{}], 评价的图腾是:[{}]",
                feedbackRelationTab.getPatientFeedbackId(), feedbackRelationTab.getTabTotem());

        PatientFeedbackCountDAO feedbackCountDAO = DAOFactory.getDAO(PatientFeedbackCountDAO.class);

        PatientFeedbackCount patientFeedbackCount = feedbackCountDAO.getFeedbackCountByTypeAndIdAndTabTotem(
                feedbackRelationTab.getEvaluationType(), feedbackRelationTab.getEvaluationTypeId(), feedbackRelationTab.getTabTotem());

        //如果数据库有这条标签统计数量记录,则更新记录否则插入一条记录
        if (StringUtils.isEmpty(patientFeedbackCount)) {
            patientFeedbackCount = this.wrapFeedbackCountByFeedbackRelationTab(feedbackRelationTab);
            feedbackCountDAO.save(patientFeedbackCount);
        } else if (StringUtils.isEmpty(patientFeedbackCount.getEvaluationCount())) { //如果有这个标签的数量统计记录,但是数量为空
            patientFeedbackCount.setEvaluationCount(1);
            feedbackCountDAO.update(patientFeedbackCount);
        } else {
            patientFeedbackCount.setEvaluationCount((patientFeedbackCount.getEvaluationCount() + 1));
            feedbackCountDAO.update(patientFeedbackCount);
        }
    }

    /**
     * 根据评价关联表更新或者减少评价数量表
     *
     * @param feedbackRelationTab 评价关联标签记录对象
     * @author cuill
     * @date 2017/7/5
     */
    public void delPatientFeedbackByFeedbackTab(PatientFeedbackRelationTab feedbackRelationTab) {

        logger.info("delPatientFeedbackByFeedbackTab删除的评价的主键是:[{}], 评价的图腾是:[{}]",
                feedbackRelationTab.getPatientFeedbackId(), feedbackRelationTab.getTabTotem());

        PatientFeedbackCountDAO feedbackCountDAO = DAOFactory.getDAO(PatientFeedbackCountDAO.class);

        PatientFeedbackCount patientFeedbackCount = feedbackCountDAO.getFeedbackCountByTypeAndIdAndTabTotem(
                feedbackRelationTab.getEvaluationType(), feedbackRelationTab.getEvaluationTypeId(), feedbackRelationTab.getTabTotem());

        //如果数据库没有该数量统计则返回,如果有该数量统计数量为null的时候改为0,如果数量大于0的话加1
        if (StringUtils.isEmpty(patientFeedbackCount)) {
            return;
        }
        if (StringUtils.isEmpty(patientFeedbackCount.getEvaluationCount())) { //如果有这个标签的数量统计记录,但是数量为空
            patientFeedbackCount.setEvaluationCount(0);
            feedbackCountDAO.update(patientFeedbackCount);
        } else {
            patientFeedbackCount.setEvaluationCount((patientFeedbackCount.getEvaluationCount() - 1));
            feedbackCountDAO.update(patientFeedbackCount);
        }
    }

    /**
     * 根据评价关系表来封装评价的数量统计对象
     *
     * @param feedbackRelationTab 获取的评价关系对象
     * @return 需要的评价关系数量对象
     * @date 2017/6/1
     * @author cuill
     */
    private PatientFeedbackCount wrapFeedbackCountByFeedbackRelationTab(PatientFeedbackRelationTab feedbackRelationTab) {
        PatientFeedbackCount feedbackCount = new PatientFeedbackCount();
        if (!StringUtils.isEmpty(feedbackRelationTab.getTabTotem())) {
            feedbackCount.setTabTotem(feedbackRelationTab.getTabTotem());
        }
        if (!StringUtils.isEmpty(feedbackRelationTab.getEvaluationType())) {
            feedbackCount.setEvaluationType(feedbackRelationTab.getEvaluationType());
        }
        if (!StringUtils.isEmpty(feedbackRelationTab.getEvaluationTypeId())) {
            feedbackCount.setEvaluationTypeId(feedbackRelationTab.getEvaluationTypeId());
        }
        feedbackCount.setEvaluationCount(1);
        feedbackCount.setStatus(EvaluationConstant.EVALUATION_RELATION_COUNT_STATUS_NOT_DEL);
        return feedbackCount;
    }


    /**
     * 包装PatientFeedbackCount对象,只返回前端需要的evaluationTypeId,tabTotem,evaluationCount
     *
     * @param feedbackCount 数据库获取的改对象,有很多不必要的值
     * @return
     * @author cuill
     * @date 2017/6/2
     */
    private PatientFeedbackCount wrapFeedbackCountBySelf(PatientFeedbackCount feedbackCount) {
        PatientFeedbackCount returnFeedbackCount = new PatientFeedbackCount();
        if (!StringUtils.isEmpty(feedbackCount.getEvaluationTypeId())) {
            returnFeedbackCount.setEvaluationTypeId(feedbackCount.getEvaluationTypeId());
        }
        if (!StringUtils.isEmpty(feedbackCount.getTabTotem())) {
            returnFeedbackCount.setTabTotem(feedbackCount.getTabTotem());
        }
        if (!StringUtils.isEmpty(feedbackCount.getEvaluationCount())) {
            returnFeedbackCount.setEvaluationCount(feedbackCount.getEvaluationCount());
        }
        return returnFeedbackCount;
    }


    /**
     * 包装包装PatientFeedbackCount对象数组,只返回前端需要的evaluationTypeId,tabTotem,evaluationCount对象数组
     *
     * @param feedbackCountList 需要包装的标签数量统计数组
     * @return
     * @author cuill
     * @date 2017/6/2
     */
    private List<PatientFeedbackCount> wrapFeedbackCountListBySelf(List<PatientFeedbackCount> feedbackCountList) {

        List<PatientFeedbackCount> returnFeedbackCountList = new ArrayList<>();
        PatientFeedbackCount returnFeedbackCount;
        for (PatientFeedbackCount feedbackCount : feedbackCountList) {
            returnFeedbackCount = this.wrapFeedbackCountBySelf(feedbackCount);
            returnFeedbackCountList.add(returnFeedbackCount);
        }
        return returnFeedbackCountList;
    }
}
