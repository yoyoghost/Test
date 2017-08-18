package eh.evaluation.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.entity.evaluation.PatientFeedbackTab;
import eh.evaluation.constant.EvaluationConstant;
import eh.evaluation.constant.EvaluationLevelTextEnum;
import eh.evaluation.dao.PatientFeedbackTabDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.*;


/**
 * Created by Administrator on 2017/5/11.
 */
public class PatientFeedbackTabService {

    /**
     * 定义打印日志对象
     */
    private static final Logger logger = LoggerFactory.getLogger(EvaluationService.class);

    /**
     * 获取的是评价标签列表
     *
     * @param serviceType 业务类别 3->咨询, 4->预约 其他的返回医院评价的标签列表
     * @return List<Map<Integer, Object>>
     * @author cuill
     */
    @RpcService
    public Map<String, Object> findEvaluationTabByServiceType(String serviceType) {

        Map<String, Object> evaluationTabMap = new HashMap<>();
        PatientFeedbackTabDAO patientFeedbackTabDAO = DAOFactory.getDAO(PatientFeedbackTabDAO.class);

        /**
         *  定义装载星级对应评价文案 如{key:1, text: "非常不满意"}
         */
        List<Map<String, Object>> evaLevelsMapList = new ArrayList<>();
        Map<String, Object> evaLevelsMap;

        // 定义装载标签tab的数组
        List<List<PatientFeedbackTab>> allFeedbackTabList = new ArrayList<>();
        List<PatientFeedbackTab> levelFeedbackTabList;
        List<PatientFeedbackTab> patientFeedbackTabsList;

        for (int j = 1; j <= EvaluationLevelTextEnum.values().length; j++) {
            evaLevelsMap = new HashMap<>();
            //如果是咨询或者预约的评价标签列表,需要传入serviceType,医院的评价标签列表不需要传入serviceType
            if (EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT.equals(serviceType)
                    || EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD.equals(serviceType)) {
                patientFeedbackTabsList = patientFeedbackTabDAO
                        .queryFeedbackTabByLevelAndTypeForDoctor(j, Integer.parseInt(serviceType));
            } else {
                patientFeedbackTabsList = patientFeedbackTabDAO.queryFeedbackTabByLevelForOrgan(j);
            }

            //包装从数据读取的评价标签值,包装出前端需要的数据
            levelFeedbackTabList = this.wrapFeedbackTabList(patientFeedbackTabsList);
            allFeedbackTabList.add(levelFeedbackTabList);

            //获取每个星级对应的文本,eg: 一星 ->非常不满意
            EvaluationLevelTextEnum evaluationLevelTextEnum = EvaluationLevelTextEnum.findByEvaluationLevel((j));
            evaLevelsMap.put("key", j);
            evaLevelsMap.put("text", evaluationLevelTextEnum.getEvaluationLevelText());
            evaLevelsMapList.add(evaLevelsMap);
        }
        evaluationTabMap.put("evaLevels", evaLevelsMapList);
        evaluationTabMap.put("labels", allFeedbackTabList);
        return evaluationTabMap;
    }


    /**
     * 根据serviceType获取的是评价标签列表
     *
     * @param serviceType 业务类别 1->转诊 3->咨询, 4->预约
     * @return 评价标签列表的键值对
     * @author cuill
     * @date 2017/5/24
     */
    @RpcService
    public Map<String, Map<String, Object>> queryEvaluationTabByServiceTypes(String serviceType) {
        Map<String, Map<String, Object>> evaluationTabMap = new HashMap<>();
        if (serviceType.equals(EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT)) {
            evaluationTabMap.put("doctorFeedbackTab", this.findEvaluationTabByServiceType(serviceType));
        }
        //转诊和预约的标签是一样的
        if (serviceType.equals(EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER)) {
            serviceType = EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD;
        }
        //预约评价现在包括对医生和医院的评价,所以需要两组评价标签 @date 2017/5/24
        if (serviceType.equals(EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD)) {
            evaluationTabMap.put("doctorFeedbackTab", this.findEvaluationTabByServiceType(serviceType));
            evaluationTabMap.put("organFeedbackTab", this.findEvaluationTabByServiceType(null));
        }
        return evaluationTabMap;
    }


    /**
     * 新增一个评价标签,注意转诊和预约默认用一种标签
     *
     * @param evaluationType 评价标签的服务对象 1 ->医生 2 ->医院
     * @param serviceType    评价的标签的服务类型 3 ->咨询 4 ->预约 5 ->咨询和预约共用 6->医院特有
     * @param tabDesc        标签的描述
     * @param level          新增标签的等级
     * @author cuill
     * @date 2017/6/7
     */
    @RpcService
    public void addFeedbackTab(Integer evaluationType, Integer serviceType, String tabDesc, Integer level) {

        logger.info("新增的评价标签evaluationType为:[{}], serviceType为:[{}]," +
                "tabDesc为:[{}],level为:[{}]", evaluationType, serviceType, tabDesc, level);
        PatientFeedbackTabDAO feedbackTabDAO = DAOFactory.getDAO(PatientFeedbackTabDAO.class);

        PatientFeedbackTab feedbackTab = this.getFeedbackTabByTabDescOrLevel(evaluationType, tabDesc, level);

        //如果没有这个等级对应标签, ->相同标签,相同等级
        if (StringUtils.isEmpty(feedbackTab)) {

            //此处获取的是相同标签,不同的等级,医院和医生的评价标签不同的判断
            PatientFeedbackTab feedbackTabForOther = this.getFeedbackTabByTabDescOrLevel(evaluationType, tabDesc, null);

            //当数据库中没有此标签的时候
            if (StringUtils.isEmpty(feedbackTabForOther)) {
                List<String> tabTotemList = null;
                // 获取标签图腾后面的数字,别且获取的是按照从小到大的顺序,医生和机构的评价图腾是独立的
                if (evaluationType.equals(EvaluationConstant.EVALUATION_TYPE_DOCTOR)) {
                    tabTotemList = feedbackTabDAO.queryFeedbackByTabTotemNumberForDoctor();
                } else if (evaluationType.equals(EvaluationConstant.EVALUATION_TYPE_ORGAN)) {
                    tabTotemList = feedbackTabDAO.queryFeedbackByTabTotemNumberForOrgan();
                }
                int tabTotemNumber = 1;
                if (!ObjectUtils.isEmpty(tabTotemList)) {
                    tabTotemNumber = this.dealStringToIntForTabTotem(tabTotemList.get(tabTotemList.size() - 1)) + 1;
                }
                //包装即将要插入的标签图腾
                String tabTotem = wrapTabTotemByNumberAndEvaluationType(evaluationType, tabTotemNumber);

                //包装要插入的标签的对象
                PatientFeedbackTab patientFeedbackTab = wrapFeedbackTabForSave(evaluationType, serviceType, tabDesc, level, tabTotem);
                feedbackTabDAO.save(patientFeedbackTab);
            } else {
                //如果数据库有这个评价描述,获取这个评价图腾,相同标签,不同等级
                PatientFeedbackTab patientFeedbackTab = wrapFeedbackTabForSave(evaluationType,
                        serviceType, tabDesc, level, feedbackTabForOther.getTabTotem());
                feedbackTabDAO.save(patientFeedbackTab);
            }
            //咨询和预约可以共用个评价标签记录
        } else if (!StringUtils.isEmpty(feedbackTab) && evaluationType == EvaluationConstant.EVALUATION_TYPE_DOCTOR) {
            Integer returnServiceType = feedbackTab.getGeneralRate();
            if (returnServiceType.equals(serviceType)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该标签您已经添加过了");
            } else {
                feedbackTab.setGeneralRate(EvaluationConstant.EVALUATION_TAB_GENERAL_RATE_CONSULT_AND_APPOINT);
                feedbackTabDAO.update(feedbackTab);
            }
        } else {  //对于医院的标签现在还不能和医生的标签共用
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该标签您已经添加过了");
        }
    }

    /**
     * 获取标签后面的数字,个位数的话将前面的0去掉
     *
     * @param tabTotem 图腾的后两位数字
     * @return
     * @date 2017/6/8
     * @author cuill
     */
    private int dealStringToIntForTabTotem(String tabTotem) {
        int indexPost = Integer.parseInt(tabTotem.substring(0, 1));
        if (indexPost == 0) {
            return Integer.parseInt(tabTotem.substring(1, 2));
        } else {
            return Integer.parseInt(tabTotem);
        }
    }

    /**
     * 根据标签描述,评价等级,标签所对的评价对象 或者标签描述标签所对的评价对象 来获取标签对象
     *
     * @param evaluationType 标签所对的评价对象 1 ->医生 2->医院
     * @param tabDesc        标签描述
     * @param level          星级
     * @return
     * @author cuill
     * @date 2017/6/7
     */
    private PatientFeedbackTab getFeedbackTabByTabDescOrLevel(Integer evaluationType, String tabDesc, Integer level) {
        PatientFeedbackTabDAO feedbackTabDAO = DAOFactory.getDAO(PatientFeedbackTabDAO.class);
        PatientFeedbackTab feedbackTab = null;
        List<PatientFeedbackTab> feedbackTabList;
        if (!StringUtils.isEmpty(level)) {
            if (evaluationType == EvaluationConstant.EVALUATION_TYPE_DOCTOR) {
                feedbackTabList = feedbackTabDAO.queryFeedbackByTabDescAndLevel(tabDesc, level);
                if (!ObjectUtils.isEmpty(feedbackTabList)) {
                    feedbackTab = feedbackTabList.get(0);
                }
            } else if (evaluationType == EvaluationConstant.EVALUATION_TYPE_ORGAN) {
                feedbackTabList = feedbackTabDAO.queryFeedbackByTabDescAndLevelForOrgan(tabDesc, level);
                if (!ObjectUtils.isEmpty(feedbackTabList)) {
                    feedbackTab = feedbackTabList.get(0);
                }
            }
        } else {
            if (evaluationType == EvaluationConstant.EVALUATION_TYPE_DOCTOR) {
                feedbackTabList = feedbackTabDAO.queryFeedbackByTabDesc(tabDesc);
                if (!ObjectUtils.isEmpty(feedbackTabList)) {
                    feedbackTab = feedbackTabList.get(0);
                }
            } else if (evaluationType == EvaluationConstant.EVALUATION_TYPE_ORGAN) {
                feedbackTabList = feedbackTabDAO.queryFeedbackByTabDescForOrgan(tabDesc);
                if (!ObjectUtils.isEmpty(feedbackTabList)) {
                    feedbackTab = feedbackTabList.get(0);
                }
            }
        }
        return feedbackTab;
    }

    /**
     * 根据评价标签的服务对象来包装标签图腾
     *
     * @param evaluationType 评价标签的服务对象 1 ->医生 2 ->医院
     * @param tabTotemNumber 标签图腾后面追加的数字
     * @return eg:"d01"
     * @author cuill
     * @date 2017/6/7
     */
    private String wrapTabTotemByNumberAndEvaluationType(Integer evaluationType, int tabTotemNumber) {
        String tabTotem;
        if (tabTotemNumber < 10) {
            if (evaluationType == EvaluationConstant.EVALUATION_TYPE_DOCTOR) {
                tabTotem = "d0" + tabTotemNumber;
            } else {
                tabTotem = "h0" + tabTotemNumber;
            }
        } else {
            if (evaluationType == EvaluationConstant.EVALUATION_TYPE_DOCTOR) {
                tabTotem = "d" + tabTotemNumber;
            } else {
                tabTotem = "h" + tabTotemNumber;
            }
        }
        return tabTotem;
    }

    /**
     * 包装要插入评价标签表中的对象
     *
     * @param evaluationType 标签所服务的类型,分为机构和医生
     * @param serviceType    服务类型,分为咨询和预约
     * @param tab            标签的描述
     * @param level          评价的星级
     * @param nowTabTom      标签的图腾
     * @return
     * @author cuill
     * @date 2017/6/7
     */
    private PatientFeedbackTab wrapFeedbackTabForSave(int evaluationType, int serviceType, String tab, Integer level, String nowTabTom) {
        PatientFeedbackTab patientFeedbackTab = new PatientFeedbackTab();
        patientFeedbackTab.setTabDesc(tab);
        patientFeedbackTab.setTabTotem(nowTabTom);
        patientFeedbackTab.setTabStatus(1);
        patientFeedbackTab.setEvaluationLevel(level);
        if (evaluationType == EvaluationConstant.EVALUATION_TYPE_ORGAN) {
            patientFeedbackTab.setGeneralRate(EvaluationConstant.EVALUATION_TAB_GENERAL_RATE_ORGAN);
        } else {
            patientFeedbackTab.setGeneralRate(serviceType);
        }
        return patientFeedbackTab;
    }

    /**
     * 包装从数据读取的评价标签值,包装出前端需要的数据
     *
     * @param patientFeedbackTab 数据读取的评价标签值
     * @return 前端需要的patientFeedbackTab数据
     * @author cuill
     * @date 2017/5/25
     */
    private PatientFeedbackTab wrapFeedbackTab(PatientFeedbackTab patientFeedbackTab) {
        PatientFeedbackTab returnFeedbackTab = new PatientFeedbackTab();
        returnFeedbackTab.setTabTotem(patientFeedbackTab.getTabTotem());
        returnFeedbackTab.setTabDesc(patientFeedbackTab.getTabDesc());
        return returnFeedbackTab;
    }

    /**
     * 包装从数据读取的评价标签数组值,包装出前端需要的数据
     *
     * @param feedbackTabList 数据读取的评价标签列表
     * @return 前端需要的patientFeedbackTab数据
     * @author cuill
     * @date 2017/6/6
     */
    private List<PatientFeedbackTab> wrapFeedbackTabList(List<PatientFeedbackTab> feedbackTabList) {
        List<PatientFeedbackTab> returnFeedbackTab = new ArrayList<>();
        for (PatientFeedbackTab feedbackTab : feedbackTabList) {
            returnFeedbackTab.add(this.wrapFeedbackTab(feedbackTab));
        }
        return returnFeedbackTab;
    }

}
