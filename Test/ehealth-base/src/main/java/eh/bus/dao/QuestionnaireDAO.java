package eh.bus.dao;

import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.Questionnaire;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by liuya on 2017/3/31.
 */
public abstract class QuestionnaireDAO extends
        HibernateSupportDelegateDAO<Questionnaire> {
    private static final Logger logger = LoggerFactory.getLogger(QuestionnaireDAO.class);

    public QuestionnaireDAO() {
        super();
        this.setEntityName(Questionnaire.class.getName());
        this.setKeyField("questionnaireId");
    }



}
