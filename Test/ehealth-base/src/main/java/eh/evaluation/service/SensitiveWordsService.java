package eh.evaluation.service;

import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Publisher;
import ctd.net.broadcast.Subscriber;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.bus.service.consult.OnsConfig;
import eh.entity.base.PatientFeedback;
import eh.entity.evaluation.SensitiveWords;
import eh.entity.evaluation.TmpsensitiveMsgBodyMQ;
import eh.entity.mindgift.MindGift;
import eh.evaluation.constant.EvaluationConstant;
import eh.evaluation.dao.EvaluationDAO;
import eh.evaluation.dao.SensitiveWordsDAO;
import eh.mindgift.dao.MindGiftDAO;
import eh.mindgift.service.MindGiftMsgService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @version 1.0
 * @Description: 敏感词过滤.
 * @Author : fql
 */
public class SensitiveWordsService {
    private static final Log logger = LogFactory.getLog(SensitiveWordsService.class);
    @SuppressWarnings("rawtypes")
    private Map sensitiveWordMap = null;

    /**
     * 对外方法入口  敏感词替换成4个*
     *
     * @param content
     * @return
     */
    @RpcService
    public String replaceWords(String content) {
        String result = "";
        sensitiveWordMap = initKeyWord();//获取敏感词库
        if (StringUtils.isEmpty(content)) {
            logger.info("评论内容为空，无需过滤");
            result = "评论内容为空，无需过滤";
        } else {
            logger.info("待过滤内容：" + content);
            logger.info("待检测语句字数：" + content.length());
            Set<String> set = getSensitiveWord(content, 1);
            logger.info("语句中包含敏感词的个数为：" + set.size() + "。包含：" + set);
            SensitiveWordsDAO dao = DAOFactory.getDAO(SensitiveWordsDAO.class);
            List<SensitiveWords> swList = null;
            SensitiveWords sw = null;
            int rate = 0;
            for (String word : set) {
                swList = dao.findBySensitiveWords(word);
                if (swList.size() > 0) {
                    sw = swList.get(0);
                    rate = sw.getRate();
                    dao.updateRateByWords(rate + 1, word);
                    Pattern p = Pattern.compile(word, Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(content);
                    String s1 = m.replaceAll(sw.getReplaceWord());//把敏感词替换为敏感替换词
                    content = s1;
                }
            }
            result = content;
            logger.info("敏感词过滤后为：" + result);
        }

        return result;
    }

    /**
     * 敏感词汇对外接口调用，向ONS队列放消息，发布队列主题
     *
     * @param feedbackId
     * @return
     */
    @RpcService
    public boolean sensitiveMsgDeal(int feedbackId) {
        final Publisher publisher = MQHelper.getMqPublisher();
        final TmpsensitiveMsgBodyMQ tMsg = new TmpsensitiveMsgBodyMQ();
        tMsg.setBusId(feedbackId);
        tMsg.setBusType(BussTypeConstant.EVALUATION);
        tMsg.setCreateTime(new Date());
        publisher.publish(OnsConfig.sensitiveTopic, tMsg);
        return true;
    }

    /**
     * 订阅消息
     */
    @PostConstruct
    public void sensitiveMsgConsumer() {
        if (!OnsConfig.onsSwitch) {
            logger.info("the onsSwitch is set off, consumer not subscribe.");
            return;
        }
        Subscriber subscriber = MQHelper.getMqSubscriber();
        subscriber.attach(OnsConfig.sensitiveTopic, new ctd.net.broadcast.Observer<TmpsensitiveMsgBodyMQ>() {
            @Override
            public void onMessage(TmpsensitiveMsgBodyMQ Tmsg) {

                logger.info("get sensitive ONS Msg："+ JSONUtils.toString(Tmsg));

                Integer busType = Tmsg.getBusType();
                Integer busId=Tmsg.getBusId();

                if(busType==null){
                    busType=BussTypeConstant.EVALUATION;
                    busId=Tmsg.getFeedbackId();
                }

                //评价
               if(BussTypeConstant.EVALUATION==busType.intValue()){
                   doEvaluation(busId);

                   //心意
               }else if(BussTypeConstant.MINDGIFT==busType.intValue()){
                   doMindGift(busId);
               }

            }
        });
    }

    /**
     * 2017-4-20 21:01:18 zhangx 健康3.0-3星及3星以下的评价，先通过运营平台人工审核，审核通过后再放开；
     * 修改后效果：针对3星及3星以下的评价，患者提交后能在聊天页面，业务单的评价页面，查看评价
     * 以下操作在运营平台审核通过以后才可执行： 计算医生的评分，健康端医生主页的评价，医生的系统消息页面关于评价的系统消息，个人中心的患者评价页面
     */
    private void doEvaluation(int feedbackId){
            logger.info("feedbackId:" + feedbackId + "进入到三星以下运营平台审核");
            String filtText = "";
            EvaluationDAO dao = DAOFactory.getDAO(EvaluationDAO.class);
            EvaluationService evaService = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);

            PatientFeedback pf = dao.getById(feedbackId);
            if (null != pf) {
                String evaText=pf.getEvaText();

                //过滤敏感词，更新敏感词处理过的敏感词
                if (StringUtils.isEmpty(evaText)) {
                    logger.info("feedbackId:" + feedbackId + "内容为空，无需过滤");
                } else {
                    filtText = replaceWords(evaText);//替换评价内容
                    logger.info("feedbackId:" + feedbackId + "过滤成功");
                }
                dao.updateFiltTextByFeedbackId(filtText,feedbackId);

                //如果3星以上的，直接审核成功
                double evaValue = pf.getEvaValue().doubleValue();
                if (evaValue > EvaluationConstant.EVALUATION_EVAVALUE_THREE.doubleValue()) {
                    logger.info("feedbackId:" + feedbackId + "评分为" + evaValue + "，直接审核成功");
                    evaService.countEvaValueForONS(pf);//更新过滤内容、状态
                }

            }
    }

    private void doMindGift(Integer mindId){
        String filtText = "";
        MindGiftDAO dao = DAOFactory.getDAO(MindGiftDAO.class);
        MindGift mind = dao.get(mindId);
        if (null != mind) {
            String mindText=mind.getMindText();

            if (StringUtils.isEmpty(mindText)) {
                logger.info("MindGift[" + mindId + "]心意内容为空，无需过滤");
            } else {
                filtText = replaceWords(mindText);//替换评价内容
                logger.info("MindGift[" + mindId + "]过滤后为["+filtText+"]");
            }

            //更新敏感词数据
            MindGiftMsgService.doAfterSensitiveWords(mindId,filtText);
        }else{
            logger.info("查不到业务单MindGift[" + mindId + "]");
        }
    }


    /**
     * 构造函数，初始化敏感词库
     */
    public SensitiveWordsService() {
        //sensitiveWordMap = initKeyWord();
    }

    /**
     * 判断文字是否包含敏感字符
     *
     * @param txt       文字
     * @param matchType 匹配规则&nbsp;1：最小匹配规则，2：最大匹配规则
     * @return 若包含返回true，否则返回false
     * @author fql
     * @version 1.0
     */
    public boolean isContaintSensitiveWord(String txt, int matchType) {
        boolean flag = false;
        for (int i = 0; i < txt.length(); i++) {
            int matchFlag = this.checkSensitiveWord(txt, i, matchType); //判断是否包含敏感字符
            if (matchFlag > 0) {    //大于0存在，返回true
                flag = true;
            }
        }
        return flag;
    }

    /**
     * 获取文字中的敏感词
     *
     * @param txt       文字
     * @param matchType 匹配规则&nbsp;1：最小匹配规则，2：最大匹配规则
     * @return
     * @author fql
     * @version 1.0
     */
    public Set<String> getSensitiveWord(String txt, int matchType) {
        Set<String> sensitiveWordList = new HashSet<String>();
        for (int i = 0; i < txt.length(); i++) {
            int length = checkSensitiveWord(txt, i, matchType);    //判断是否包含敏感字符
            if (length > 0) {    //存在,加入list中
                sensitiveWordList.add(txt.substring(i, i + length));
                i = i + length - 1;    //减1的原因，是因为for会自增
            }
        }

        return sensitiveWordList;
    }

    /**
     * 替换敏感字字符
     *
     * @param txt
     * @param matchType
     * @param replaceChar 替换字符，默认*
     * @author fql
     * @version 1.0
     */
    public String replaceSensitiveWord(String txt, int matchType, String replaceChar) {
        String resultTxt = txt;
        Set<String> set = getSensitiveWord(txt, matchType);     //获取所有的敏感词
        Iterator<String> iterator = set.iterator();
        String word = null;
        String replaceString = null;
        while (iterator.hasNext()) {
            word = iterator.next();
            replaceString = getReplaceChars(replaceChar, word.length());
            resultTxt = resultTxt.replaceAll(word, replaceString);
        }

        return resultTxt;
    }

    /**
     * 获取替换字符串
     *
     * @param replaceChar
     * @param length
     * @return
     * @author fql
     * @version 1.0
     */
    private String getReplaceChars(String replaceChar, int length) {
        String resultReplace = replaceChar;
        for (int i = 1; i < length; i++) {
            resultReplace += replaceChar;
        }

        return resultReplace;
    }

    /**
     * 检查文字中是否包含敏感字符，检查规则如下：<br>
     *
     * @param txt
     * @param beginIndex
     * @param matchType
     * @author fql
     * @return，如果存在，则返回敏感词字符的长度，不存在返回0
     * @version 1.0
     */
    @SuppressWarnings({"rawtypes"})
    public int checkSensitiveWord(String txt, int beginIndex, int matchType) {
        boolean flag = false;    //敏感词结束标识位：用于敏感词只有1位的情况
        int matchFlag = 0;     //匹配标识数默认为0
        char word = 0;
        Map nowMap = sensitiveWordMap;
        for (int i = beginIndex; i < txt.length(); i++) {
            word = Character.toLowerCase(txt.charAt(i));//统一转换为小写
            if (nowMap != null) {
                nowMap = (Map) nowMap.get(word);//获取指定key
            }
            if (nowMap != null) {     //存在，则判断是否为最后一个
                matchFlag++;     //找到相应key，匹配标识+1
                if ("1".equals(nowMap.get("isEnd"))) {       //如果为最后一个匹配规则,结束循环，返回匹配标识数
                    flag = true;       //结束标志位为true
                    if (EvaluationConstant.minMatchTYpe == matchType) {    //最小规则，直接返回,最大规则还需继续查找
                        break;
                    }
                }
            } else {     //不存在，直接返回
                break;
            }
        }
        if (matchFlag < 2 || !flag) {        //长度必须大于等于1，为词
            matchFlag = 0;
        }
        return matchFlag;
    }

    @SuppressWarnings("rawtypes")
    public Map initKeyWord() {
        try {
            // 读取敏感词库
            Set<String> keyWordSet = readSensitiveWordFile();
            // 将敏感词库加入到HashMap中
            if (keyWordSet != null) {
                addSensitiveWordToHashMap(keyWordSet);
            }
            // spring获取application，然后application.setAttribute("sensitiveWordMap",sensitiveWordMap);
        } catch (Exception e) {
            //e.printStackTrace();
            logger.info("敏感词初始化异常!!!" + e);
        }
        return sensitiveWordMap;
    }

    /**
     * 读取敏感词库，将敏感词放入HashSet中，构建一个DFA算法模型：<br>
     *
     * @param keyWordSet 敏感词库
     * @author fql
     * @version 1.0
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addSensitiveWordToHashMap(Set<String> keyWordSet) {
        sensitiveWordMap = new HashMap(keyWordSet.size()); // 初始化敏感词容器，减少扩容操作
        String key = null;
        Map nowMap = null;
        Map<String, String> newWorMap = null;
        // 迭代keyWordSet
        Iterator<String> iterator = keyWordSet.iterator();
        while (iterator.hasNext()) {
            key = iterator.next(); // 关键字
            nowMap = sensitiveWordMap;
            for (int i = 0; i < key.length(); i++) {
                char keyChar = Character.toLowerCase(key.charAt(i)); // 转换成char型,统一转换为小写
                Object wordMap = null;
                if (nowMap != null) {
                    wordMap = nowMap.get(keyChar); // 获取
                }
                if (wordMap != null) { // 如果存在该key，直接赋值
                    nowMap = (Map) wordMap;
                } else { // 不存在则，则构建一个map，同时将isEnd设置为0，因为他不是最后一个
                    newWorMap = new HashMap<String, String>();
                    newWorMap.put("isEnd", "0"); // 不是最后一个
                    nowMap.put(keyChar, newWorMap);
                    nowMap = newWorMap;
                }

                if (i == key.length() - 1) {
                    nowMap.put("isEnd", "1"); // 最后一个
                }
            }
        }
    }

    /**
     * 读取敏感词库中的内容，将内容添加到set集合中
     *
     * @return
     * @throws Exception
     * @author fql
     * @version 1.0
     */
    @SuppressWarnings("resource")
    private Set<String> readSensitiveWordFile() {
        Set<String> set = null;
        SensitiveWordsDAO dao = DAOFactory.getDAO(SensitiveWordsDAO.class);
        List<String> allWords = dao.findAllWords();
        if (allWords.size() > 0) {
            set = new HashSet<>(allWords);
            for (int i = 0; i < allWords.size(); i++) {
                set.add(allWords.get(i));
            }
        } else {
            logger.info("数据库敏感词库不存在");
        }
        return set;
    }

    /**
     * 定时检索ONS处理失败的记录
     *
     * 2017-4-20 21:01:18 zhangx 健康3.0-3星及3星以下的评价，先通过运营平台人工审核，审核通过后再放开；
     * 修改后效果：针对3星及3星以下的评价，患者提交后能在聊天页面，业务单的评价页面，查看评价
     * 以下操作在运营平台审核通过以后才可执行： 计算医生的评分，健康端医生主页的评价，医生的系统消息页面关于评价的系统消息，
     * 个人中心的患者评价页面
     */
    @RpcService
    public void sensitiveWordsDealSchedule() {
        EvaluationDAO eDao = DAOFactory.getDAO(EvaluationDAO.class);
        List<Integer> pList = eDao.findOnsFailedList();//获取ONS未处理的记录
        EvaluationService evaService = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        if (pList.size() > 0) {
            String filtText = "";
            for (Integer feedbackId : pList) {
                logger.info("定时处理ons失败的相关数据-"+feedbackId);
                doEvaluation(feedbackId);
            }
        }
    }
}
