package eh.autodiagnosis.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import eh.autodiagnosis.dao.DiseaseConfigDao;
import eh.entity.autodiagnosis.DiseaseConfig;
import eh.entity.autodiagnosis.vo.BodyPartVo;
import eh.entity.autodiagnosis.vo.DiagnosisResult;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by shenhj on 2017/5/16.
 */
public class IntelligenceGuidance {
    private static final Log logger = LogFactory.getLog(IntelligenceGuidance.class);

    public static List<BodyPartVo> bodyPartVoList = Lists.newLinkedList();//身体部位对应症状列表,男
    public static ConcurrentMap<Integer, Integer> diseaseScoreTotal = Maps.newConcurrentMap();//疾病对应症状数
    public static ConcurrentMap<Integer,Set<Integer>> symptomWithDisease = Maps.newConcurrentMap();//症状对应疾病列表
    public static ConcurrentMap<Integer,DiseaseConfig> diseaseMap = Maps.newConcurrentMap();//疾病列表

    public static List<DiagnosisResult> getDiseaseScore(List<Integer> symptomIds, int gender) throws DAOException {
        HashMap<Integer,Integer> tempDiseaseScoreMap = Maps.newHashMap();//疾病对应它的出现次数
        for (Integer id:symptomIds){
            Set<Integer> diseaseIds = symptomWithDisease.get(id);
            if(CollectionUtils.isNotEmpty(diseaseIds)){
                for (Integer d : diseaseIds) {
                    DiseaseConfig diseaseConfig = diseaseMap.get(d);
                    if(diseaseConfig==null){
                        diseaseConfig = DAOFactory.getDAO(DiseaseConfigDao.class).get(d);
                        diseaseMap.put(d,diseaseConfig);
                    }
                    if(diseaseConfig==null||((byte)gender != diseaseConfig.getGender()&&diseaseConfig.getGender()!=(byte)3)){
                        continue;
                    }

                    if(tempDiseaseScoreMap.containsKey(d)){
                        tempDiseaseScoreMap.put(d,tempDiseaseScoreMap.get(d)+1);
                    }else{
                        tempDiseaseScoreMap.put(d,1);
                    }
                }
            }
        }
        List<DiagnosisResult> resultList = Lists.newArrayList();
        for (Integer diseaseId:tempDiseaseScoreMap.keySet()) {
            Integer denominator = tempDiseaseScoreMap.get(diseaseId);
            Integer molecule = diseaseScoreTotal.get(diseaseId);
            if(molecule==null||molecule<1){
                continue;
            }
            logger.info("disease::"+denominator+"::"+molecule);
            NumberFormat.getPercentInstance();
            DiagnosisResult diagnosisResult = new DiagnosisResult();
            diagnosisResult.setDisease(diseaseMap.get(diseaseId).getDisease());
            diagnosisResult.setDiseaseId(diseaseId);
            if(denominator>molecule){
                diagnosisResult.setPercent(100);
            }else{
//                NumberFormat numberFormat = NumberFormat.getNumberInstance();
                diagnosisResult.setPercent(denominator*100/molecule);
            }
            resultList.add(diagnosisResult);

        }
        Collections.sort(resultList);
        return resultList;
    }

//    private static <K, V extends Comparable<? super V>> LinkedHashMap<K, V> sortByValue(Map<K, V> map) {
//        List<Map.Entry<K, V>> list = new ArrayList(map.entrySet());
//        Collections.sort(list,new Comparator<Map.Entry<K, V>>() {
//            //降序排序2-1
//            public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
//                return o2.getValue().compareTo(o1.getValue());
//            }
//        });
//        LinkedHashMap<K,V> result = new LinkedHashMap();//重新赋值
//        for (Map.Entry<K, V> e: list) {
//            result.put(e.getKey(),e.getValue());
//        }
//        return result;
//    }
//
//    public static void main(String[] args) {
//        diseaseScoreTotal.put("感冒",5);
//        diseaseScoreTotal.put("肺炎",3);
//        diseaseScoreTotal.put("骨折",4);
//        symptomWithDisease.put("头痛",Sets.<String>newHashSet("感冒","肺炎","其他","aaa"));
//        symptomWithDisease.put("咳嗽",Sets.<String>newHashSet("感冒","肺炎","bbb"));
//        symptomWithDisease.put("发烧",Sets.<String>newHashSet("感冒","肺炎","ccc"));
//        symptomWithDisease.put("肿痛",Sets.<String>newHashSet("未知"));
//        symptomWithDisease.put("不能走路",Sets.<String>newHashSet("骨折","肺炎"));
//        symptomWithDisease.put("肺部有问题",Sets.<String>newHashSet("dddd","肺炎"));
//        Map<String,Integer> resultMap = getDiseaseScore(Lists.newArrayList("咳嗽","头痛","aaadd"));
//        for (String str:resultMap.keySet()) {
//            System.out.println(str+"::"+resultMap.get(str));
//        }
//    }
}
