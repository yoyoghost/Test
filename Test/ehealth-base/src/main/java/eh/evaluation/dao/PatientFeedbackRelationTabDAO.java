package eh.evaluation.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.evaluation.PatientFeedbackRelationTab;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;


/**
 * Created by cuill on 2017/5/12.
 */
public abstract class PatientFeedbackRelationTabDAO
        extends HibernateSupportDelegateDAO<PatientFeedbackRelationTab> {

    private static final Logger logger = LoggerFactory.getLogger(PatientFeedbackRelationTab.class);

    public PatientFeedbackRelationTabDAO() {
        super();
        this.setEntityName(PatientFeedbackRelationTab.class.getName());
        this.setKeyField("id");
    }

    /**
     * 根据评价的主键来获取该评价记录的标签
     *
     * @param patientFeedbackId
     * @return
     * @date 2017/6/8
     * @author cuill
     */
    @DAOMethod(sql = "from PatientFeedbackRelationTab where patientFeedbackId =:patientFeedbackId And status = 1")
    public abstract List<PatientFeedbackRelationTab> queryFeedbackRelationTabByFeedbackID(@DAOParam("patientFeedbackId") Integer patientFeedbackId);


    /**
     * 根据评价的主键来获取该评价记录的标签 主要是为了计时器统计用的
     *
     * @return
     * @date 2017/6/8
     * @author cuill
     */
    @DAOMethod(sql = "from PatientFeedbackRelationTab where status = 1")
    public abstract List<PatientFeedbackRelationTab> queryFeedbackRelationTabForValid();

    /**
     * 根据评价类型和评价类型对应的角色的id获取评价关联标签记录
     *
     * @param evaluationType   评价标签的类型
     * @param evaluationTypeId 评价标签对应的角色
     * @author cuill
     */
    @DAOMethod(sql = "select t from PatientFeedbackRelationTab t, PatientFeedback p where p.feedbackId = t.patientFeedbackId and t.evaluationType =:evaluationType and t.evaluationTypeId =:evaluationTypeId and t.status = 1" +
            "and (p.status = 1 OR p.status = 2)")
    public abstract List<PatientFeedbackRelationTab> queryFeedbackRelationTabByEvaluationTypeAndId(@DAOParam("evaluationType") Integer evaluationType,
                                                                                                   @DAOParam("evaluationTypeId") Integer evaluationTypeId);

    /**
     * 根据评价类型获取评价关联标签记录
     *
     * @param evaluationType 评价标签的类型
     * @return
     * @author cuill
     * @date 2017/6/9
     */
    public List<Integer> queryEvaluationTypeIdByEvaluationType(final Integer evaluationType){
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select DISTINCT evaluationTypeId from PatientFeedbackRelationTab " +
                        "where evaluationType =:evaluationType and status = 1");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("evaluationType", evaluationType);
                List<Integer> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
}
