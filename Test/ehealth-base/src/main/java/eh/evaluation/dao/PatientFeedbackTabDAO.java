package eh.evaluation.dao;

import ctd.dictionary.DictionaryItem;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import eh.entity.evaluation.PatientFeedbackTab;

import java.util.List;

/**
 * Created by cuill on 2017/5/11
 */
public abstract class PatientFeedbackTabDAO
        extends HibernateSupportDelegateDAO<PatientFeedbackTab>
        implements DBDictionaryItemLoader<PatientFeedbackTab> {

//    private static final Logger logger = LoggerFactory.getLogger(PatientFeedbackTabDAO.class);

    public PatientFeedbackTabDAO() {
        super();
        this.setEntityName(PatientFeedbackTab.class.getName());
        this.setKeyField("evaluationTabId");
    }

    /**
     * 根据标签的id获取标签
     *
     * @param id 评价标签的主键
     * @return
     */
    @DAOMethod
    public abstract PatientFeedbackTab getById(int id);

    /**
     * 根据评价的星级获取标签数组,为医生评价获取
     *
     * @param evaluationLevel 评价的星级
     * @param serviceType     业务类型,3-> 咨询 4->预约
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @DAOMethod(sql = "from PatientFeedbackTab where evaluationLevel =:evaluationLevel and tabStatus = 1  " +
            "And (generalRate = 5 or generalRate =:serviceType) ")
    public abstract List<PatientFeedbackTab> queryFeedbackTabByLevelAndTypeForDoctor(
            @DAOParam("evaluationLevel") Integer evaluationLevel,
            @DAOParam("serviceType") Integer serviceType
    );


    /**
     * 根据评价的星级获取标签数组,为医院评价获取
     *
     * @param evaluationLevel 评价的星级
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @DAOMethod(sql = "from PatientFeedbackTab where evaluationLevel =:evaluationLevel And tabStatus = 1 And generalRate = 6")
    public abstract List<PatientFeedbackTab> queryFeedbackTabByLevelForOrgan(@DAOParam("evaluationLevel") Integer evaluationLevel);


    /**
     * @param start
     * @param limit
     * @author cuill
     * @Data 2017/5/24
     */
    @Override
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(tabTotem,tabDesc) from PatientFeedbackTab order by evaluationLevel")
    public abstract List<DictionaryItem> findAllDictionaryItem(
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     *
     * @param key
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @Override
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(tabTotem,tabDesc) from PatientFeedbackTab where tabTotem=:tabTotem")
    public abstract DictionaryItem getDictionaryItem(@DAOParam("tabTotem") Object key);

    /**
     * 根据评价的标签的描述和等级获取评价标签
     *
     * @param tabDesc         评价描述
     * @param evaluationLevel 星级
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @DAOMethod(sql = "from PatientFeedbackTab where tabDesc =:tabDesc " +
            "And evaluationLevel =:evaluationLevel And tabStatus = 1 And generalRate <> 6")
    public abstract List<PatientFeedbackTab> queryFeedbackByTabDescAndLevel(@DAOParam("tabDesc") String tabDesc,
                                                                            @DAOParam("evaluationLevel") Integer evaluationLevel);

    /**
     * 根据评价的标签的描述获取评价标签
     *
     * @param tabDesc 评价描述
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @DAOMethod(sql = "from PatientFeedbackTab where tabDesc =:tabDesc And tabStatus = 1 And generalRate <>6")
    public abstract List<PatientFeedbackTab> queryFeedbackByTabDesc(@DAOParam("tabDesc") String tabDesc);

    /**
     * 根据评价的标签的描述和等级获取评价标签
     *
     * @param tabDesc         评价描述
     * @param evaluationLevel 星级
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @DAOMethod(sql = "from PatientFeedbackTab where tabDesc =:tabDesc " +
            "And evaluationLevel =:evaluationLevel And tabStatus = 1 And generalRate = 6")
    public abstract List<PatientFeedbackTab> queryFeedbackByTabDescAndLevelForOrgan(@DAOParam("tabDesc") String tabDesc,
                                                                                    @DAOParam("evaluationLevel") Integer evaluationLevel);

    /**
     * 根据评价的标签的描述获取评价标签
     *
     * @param tabDesc 评价描述
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @DAOMethod(sql = "from PatientFeedbackTab where tabDesc =:tabDesc And tabStatus = 1 And generalRate =6")
    public abstract List<PatientFeedbackTab> queryFeedbackByTabDescForOrgan(@DAOParam("tabDesc") String tabDesc);

    /**
     * /**
     * 获取评价标签的图腾为医生.并且以后面的两位排序
     *
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @DAOMethod(sql = "SELECT SUBSTRING(tabTotem, 2, 2) FROM PatientFeedbackTab Where generalRate <>6 ORDER BY SUBSTRING(tabTotem, 2, 2)")
    public abstract List<String> queryFeedbackByTabTotemNumberForDoctor();

    /**
     * /**
     * 获取评价标签的图腾为机构所用.并且以后面的两位排序
     *
     * @return
     * @author cuill
     * @Data 2017/5/24
     */
    @DAOMethod(sql = "SELECT SUBSTRING(tabTotem, 2, 2) FROM PatientFeedbackTab Where generalRate =6 ORDER BY SUBSTRING(tabTotem, 2, 2)")
    public abstract List<String> queryFeedbackByTabTotemNumberForOrgan();


}
