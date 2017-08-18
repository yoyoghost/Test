package eh.autodiagnosis.service;

import com.google.common.base.Function;
import com.google.common.collect.*;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.autodiagnosis.dao.BodyPartConfigDao;
import eh.autodiagnosis.dao.DiseaseConfigDao;
import eh.autodiagnosis.dao.SymptomConfigDao;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.entity.autodiagnosis.BodyPartConfig;
import eh.entity.autodiagnosis.DiseaseConfig;
import eh.entity.autodiagnosis.SymptomConfig;
import eh.entity.autodiagnosis.vo.BodyPartVo;
import eh.entity.autodiagnosis.vo.DiagnosisResult;
import eh.entity.autodiagnosis.vo.SymptomVo;
import eh.entity.base.Doctor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.annotation.Scheduled;
import sun.jvmstat.perfdata.monitor.PerfStringVariableMonitor;

import java.util.*;

/**
 * Created by shenhj on 2017/5/16.
 */
@RpcBean(value = "autodiagnosisService")
public class AutodiagnosisService implements ApplicationListener<ContextRefreshedEvent> {
    private static final Log logger = LogFactory.getLog(AutodiagnosisService.class);

    @RpcService
    public List<DiagnosisResult> getDiseaseScore(List<Integer> symptomIds, int gender) {
        assertSymptoms(symptomIds);
        List<DiagnosisResult> list = IntelligenceGuidance.getDiseaseScore(symptomIds,gender);
        return list;
    }

    @RpcService
    public List<BodyPartVo> findAllSymptoms() {
        if(CollectionUtils.isEmpty(IntelligenceGuidance.bodyPartVoList)){
            syncBodyPartList();
        }
        return IntelligenceGuidance.bodyPartVoList;
    }

    @RpcService
    public Map<String,Object> getRecommendDoctorsWithPages(String area, int gender, List<Integer> diseaseIds,int start,int limit) throws ControllerException {
        assertRecommendParam(area,gender,start,limit);
        if(CollectionUtils.isEmpty(diseaseIds)){
            return null;
        }
        if(area.length()==6){
            area=area.substring(0,4);
        }
        List<Doctor> list = getAutodiagnosisDoctors(diseaseIds,area);
        Map<String, List<Doctor>> map = new HashMap<>();
        Map<Integer,Doctor> uniqueMap = Maps.newHashMap();
        List<Doctor> totalDoctors = Lists.newArrayList();
        for (Doctor doctor : list) {
            if (!uniqueMap.containsKey(doctor.getDoctorId())){
                uniqueMap.put(doctor.getDoctorId(),doctor);
            }else{
                continue;
            }
            String profession = doctor.getProfession();
            if (map.containsKey(profession)) {
                map.get(profession).add(doctor);
            } else {
                List<Doctor> ds = new ArrayList<>();
                ds.add(doctor);
                map.put(profession, ds);
            }
        }
        int i = 1;
        //最多8个科室
        for (String profession:map.keySet()) {
            if(i>8){
                break;
            }
            totalDoctors.addAll(map.get(profession));
            i++;
        }
        Map<String, Object> result = new HashMap<>();
        if(totalDoctors.size()<start){
            result.put("doctors", Lists.newArrayList());
        }else if(totalDoctors.size()<start+limit){
            if(start==30&&totalDoctors.size()>32){
                result.put("doctors", totalDoctors.subList(start,32));
            }else{
                result.put("doctors", totalDoctors.subList(start,totalDoctors.size()));
            }
        }else{
            result.put("doctors", totalDoctors.subList(start,start+limit));
        }
        result.put("addrArea", area);
        result.put("addrAreaText", DictionaryController.instance()
                .get("eh.base.dictionary.AddrArea")
                .getText(area));
        return result;
    }

    private List<Doctor> findDoctorsByDiseaseIds(List<Integer> diseaseIds, String area) {
        List<Doctor> list = Lists.newArrayListWithCapacity(5);
        diseaseIds = removeDuplicate(diseaseIds);
        List<String> professions = DAOFactory.getDAO(DiseaseConfigDao.class).findDepartNos(diseaseIds);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        for (String profession : professions) {
            String[] proArr = profession.split(",");
            for (String pro: proArr){
                HashMap<String, Object> os = doctorDAO.getDoctorListWithWhile(area, profession, 4);
                if (os == null || os.get("list") == null) {
                    continue;
                }else{
                    list.addAll((List<Doctor>) os.get("list"));
                }
            }
        }
        return list;
    }

    private void assertRecommendParam(String area, int gender, int start, int limit) {
        assertRecommendParam(area,gender);
        if((start+limit)>40||start<0||limit<0){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "最多推荐32个医生");
        }
    }


    private void assertRecommendParam(String area, int gender) {
        if(StringUtils.isBlank(area)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "区域不能为空");
        }
        if(gender!=1 && gender!=2){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "性别类型不支持");
        }

    }


    @Scheduled(cron = "0 0 1 * * ?")
    public void syncAutodiagnosisConfig(){
        syncBodyPartList();
        syncDiseaseInfo();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        syncAutodiagnosisConfig();
    }

    private void assertSymptoms(List<Integer> symptomIds) throws DAOException {
        if (CollectionUtils.isEmpty(symptomIds)) {
            if (CollectionUtils.isEmpty(symptomIds)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "请选择对应症状");
            }
        }
    }

    private void syncBodyPartList() {
        BodyPartConfigDao bodyPathsConfigDao = DAOFactory.getDAO(BodyPartConfigDao.class);
        SymptomConfigDao symptomConfigDao = DAOFactory.getDAO(SymptomConfigDao.class);
        List<BodyPartConfig> bodyPathsConfigs;
        bodyPathsConfigs = bodyPathsConfigDao.findBodyPartConfigs();
        if(CollectionUtils.isEmpty(bodyPathsConfigs)){
        }
        List<BodyPartVo> tempLists = Lists.newLinkedList();
        for (BodyPartConfig config: bodyPathsConfigs) {
            BodyPartVo bodyPartVo = new BodyPartVo();
            List<SymptomConfig> list = symptomConfigDao.findSymptomConfigs();
            bodyPartVo.setPartId(config.getId());
            bodyPartVo.setPart(config.getPart());
            for (SymptomConfig symptomConfig:list) {
                if(symptomConfig.getBodyPartId().equals(config.getId())){
                    SymptomVo symptomVo = new SymptomVo();
                    symptomVo.setGender(symptomConfig.getGender());
                    symptomVo.setSymptom(symptomConfig.getSymptom());
                    symptomVo.setSymptomId(symptomConfig.getId());
                    bodyPartVo.getSymptomList().add(symptomVo);
                }
            }
            tempLists.add(bodyPartVo);
        }
        IntelligenceGuidance.bodyPartVoList = tempLists;
    }

    private void syncDiseaseInfo() {
        DiseaseConfigDao diseaseConfigDao = DAOFactory.getDAO(DiseaseConfigDao.class);
        List<DiseaseConfig> diseaseConfigs = diseaseConfigDao.findDiseaseConfigs();
        if(CollectionUtils.isEmpty(diseaseConfigs)){
            return;
        }
        for (DiseaseConfig disease:diseaseConfigs) {
            String symptoms = disease.getSymptoms();
            if(StringUtils.isNotBlank(symptoms)){
                IntelligenceGuidance.diseaseMap.put(disease.getId(),disease);
                String[] symps = symptoms.split(",");
                IntelligenceGuidance.diseaseScoreTotal.put(disease.getId(),symps.length);
                for (String symp: symps) {
                    Set<Integer> diseaseSet = IntelligenceGuidance.symptomWithDisease.get(Integer.parseInt(symp));
                    if(CollectionUtils.isNotEmpty(diseaseSet)){
                        diseaseSet.add(disease.getId());
                    }else
                        diseaseSet = Sets.newHashSet(disease.getId());
                    IntelligenceGuidance.symptomWithDisease.put(Integer.parseInt(symp),diseaseSet);
                }

            }
        }
    }

    public static <T> List<T> removeDuplicate(List<T> list)
    {
        HashSet<T> hashSet = new HashSet<T>(list);
        list.clear();
        list.addAll(hashSet);
        return list;
    }

    //每个科室最多4个
    public List<Doctor>  getAutodiagnosisDoctors(List<Integer> diseaseIds, String area) {
        List<Doctor> list = Lists.newArrayListWithCapacity(16);
        diseaseIds = removeDuplicate(diseaseIds);
        List<String> professions = DAOFactory.getDAO(DiseaseConfigDao.class).findDepartNos(diseaseIds);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        HashSet<String> proSet = Sets.newHashSet();
        //去重
        for (String profession : professions) {
            String[] proArr = profession.split(",");
            for (String pro: proArr){
                proSet.add(pro);
            }
        }
        for (String pro:proSet) {
            List<Doctor> doctorList = doctorDAO.getDoctorsforAutodiagnosis(area, pro, 4);
            list.addAll(doctorList);
        }
        return list;
    }
}
