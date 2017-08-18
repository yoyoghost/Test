package eh.evaluation.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.evaluation.PatientFeedbackCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * Created by cuill on 2017/5/18.
 */
public abstract class PatientFeedbackCountDAO
        extends HibernateSupportDelegateDAO<PatientFeedbackCount> {

    private static final Logger logger = LoggerFactory.getLogger(PatientFeedbackCountDAO.class);

    public PatientFeedbackCountDAO() {
        super();
        this.setEntityName(PatientFeedbackCount.class.getName());
        this.setKeyField("id");
    }

    /**
     * 根据评价类型和评价类型对应的主键还有评价标签的图腾来获取该图腾对应的评价数量
     *
     * @param evaluationType   评价类型现在分为机构和医生
     * @param evaluationTypeId 对应评价类型的主键
     * @param tabTotem         评价的图腾
     * @return 一条统计评价标签的数据
     * @author cuill
     * @date 2017/6/1
     */
    @DAOMethod(sql = "from PatientFeedbackCount where status = 1 And evaluationType =:evaluationType and evaluationTypeId =:evaluationTypeId " +
            "and tabTotem=:tabTotem")
    public abstract PatientFeedbackCount getFeedbackCountByTypeAndIdAndTabTotem(
            @DAOParam("evaluationType") Integer evaluationType,
            @DAOParam("evaluationTypeId") Integer evaluationTypeId,
            @DAOParam("tabTotem") String tabTotem);


    /**
     * 根据评价类型和评价类型对应的主键来获取该图腾对应的评价数量
     *
     * @param evaluationType   评价类型现在分为机构和医生
     * @param evaluationTypeId 对应评价类型的主键
     * @return 统计评价标签的数据
     * @author cuill
     * @date 2017/6/2
     */
    @DAOMethod(sql = "from PatientFeedbackCount where status = 1 And evaluationType =:evaluationType and evaluationTypeId =:evaluationTypeId and evaluationCount > 0 order by evaluationCount desc ")
    public abstract List<PatientFeedbackCount> queryFeedbackCountByEvaluationTypeAndId(
            @DAOParam("evaluationType") Integer evaluationType,
            @DAOParam("evaluationTypeId") Integer evaluationTypeId);


    /**
     * 更新评价标签的数量,将有效评价标签置0,供定时器使用
     *
     * @param evaluationType   评价类型现在分为机构和医生
     * @param evaluationTypeId 对应评价类型的主键
     * @author cuill
     * @date 2017/6/9
     */
    @DAOMethod(sql = "UPDATE PatientFeedbackCount SET evaluationCount = 0 where evaluationType =:evaluationType and evaluationTypeId =:evaluationTypeId And status = 1 ")
    public abstract void updateFeedbackCountToInitByTypeAndIdForSchedule(@DAOParam("evaluationType") Integer evaluationType,
                                                                         @DAOParam("evaluationTypeId") Integer evaluationTypeId);


}
