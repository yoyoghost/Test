package eh.cdr.service;

import com.google.common.collect.ImmutableMap;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.AuditPrescriptionOrganDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganConfigDAO;
import eh.bus.dao.SearchContentDAO;
import eh.cdr.constant.OrderStatusConstant;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.*;
import eh.cdr.drugsenterprise.RemoteDrugEnterpriseService;
import eh.entity.base.Doctor;
import eh.entity.bus.SearchContent;
import eh.entity.cdr.*;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;
import eh.utils.MapValueUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;

/**
 * Created by zhongzx on 2016/5/20 0020.
 * 药师审核平台的服务
 */
public class RecipeCheckService {

    private static final Log logger = LogFactory.getLog(RecipeCheckService.class);
    /**
     * zhongzx
     * 根据flag查询处方列表   for phone
     * flag 0-待审核 1-审核通过 2-审核未通过 3-全部
     * @param doctorId
     * @param flag
     * @param start  每页起始数 首页从0开始
     * @param limit  每页限制条数
     * @return
     */
    @RpcService
    public List<Map<String,Object>> findRecipeListWithPage(int doctorId, int flag, int start, int limit){

        RecipeDAO rDao = DAOFactory.getDAO(RecipeDAO.class);
        PatientDAO pDao = DAOFactory.getDAO(PatientDAO.class);
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        AuditPrescriptionOrganDAO auditDao = DAOFactory.getDAO(AuditPrescriptionOrganDAO.class);

        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        if(null == doctor){
            logger.error("doctor is null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId = " + doctorId + " 找不到该医生");
        }
        Set<Integer> organs = new HashSet<>();
        //organs.add(doctor.getOrgan());
        List<Integer> organIds = auditDao.findOrganIdsByDoctorId(doctorId);
        if(null == organIds || organIds.size() == 0){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该药师没有配置能审核的机构");
        }
        organs.addAll(organIds);

        //分页开始位置在后台计算
        //int start = startPage*limit;
        //nextPage 是下一页
        //int nextPage = ++startPage;
        //int nextStart = nextPage*limit;
        List<Recipe> list = rDao.findRecipeByFlag(organs,flag,start,limit);
        //List<Recipe> nextList = rDao.findRecipeByFlag(organs,flag,nextStart,limit);

        List<Map<String,Object>> mapList = new ArrayList<>();
        /*Map<String, Object> countMap = new HashMap();
        //flag 1-有下一页 0-没有下一页
        if (null != nextList && nextList.size() > 0) {
            countMap.put("flag", 1);
        } else {
            countMap.put("flag", 0);
            nextPage = --startPage;
        }
        countMap.put("nextPage", nextPage);
        mapList.add(countMap);*/
        //只返回要用到的字段
        if(null != list && list.size() > 0) {
            for (Recipe r : list) {
                Map<String, Object> map = new HashMap<>();
                //组装需要的处方数据
                Recipe recipe = new Recipe();
                recipe.setRecipeId(r.getRecipeId());
                recipe.setSignDate(r.getSignDate());
                recipe.setDoctor(r.getDoctor());
                recipe.setDepart(r.getDepart());
                recipe.setOrganDiseaseName(r.getOrganDiseaseName());
                recipe.setChecker(r.getChecker());
                //组装需要的患者数据
                Patient p = pDao.get(r.getMpiid());
                Patient patient = new Patient();
                patient.setPatientName(p.getPatientName());
                patient.setPatientSex(p.getPatientSex());
                Date birthDay = p.getBirthday();
                if (null != birthDay) {
                    patient.setAge(DateConversion.getAge(birthDay));
                }
                //显示一条详情数据
                List<Recipedetail> details = detailDAO.findByRecipeId(r.getRecipeId());
                Recipedetail detail = null;
                if(null != details && details.size() > 0) {
                    detail = details.get(0);
                }
                //checkResult 0:未审核 1:通过 2:不通过
                Integer checkResult = getCheckResult(r);

                Date signDate = r.getSignDate();
                String dateString = "";
                if (null != signDate) {
                    dateString = DateConversion.getDateFormatter(signDate, "yyyy-MM-dd HH:mm");
                }
                map.put("dateString", dateString);
                map.put("recipe", recipe);
                map.put("patient", patient);
                map.put("check", checkResult);
                map.put("detail", detail);
                mapList.add(map);
            }
        }
        return mapList;
    }

    /**
     * zhongzx
     * 根据flag查询处方列表   for pc pc大小界面 和 手机大小界面 调用接口不同
     * flag 0-待审核 1-审核通过 2-审核未通过 3-全部
     * @param doctorId
     * @param flag
     * @param start  开始位置 从0开始
     * @param limit  每页限制条数
     * @return
     */
    @RpcService
    public List<Map<String,Object>> findRecipeListWithPageForPC(int doctorId, int flag, int start, int limit){

        RecipeDAO rDao = DAOFactory.getDAO(RecipeDAO.class);
        PatientDAO pDao = DAOFactory.getDAO(PatientDAO.class);
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        AuditPrescriptionOrganDAO auditDao = DAOFactory.getDAO(AuditPrescriptionOrganDAO.class);

        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        if(null == doctor){
            logger.error("doctor is null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId = " + doctorId + " 找不到该医生");
        }
        Set<Integer> organs = new HashSet<>();
        //organs.add(doctor.getOrgan());
        List<Integer> organIds = auditDao.findOrganIdsByDoctorId(doctorId);
        if(null == organIds || organIds.size() == 0){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该药师没有配置能审核的机构");
        }
        organs.addAll(organIds);
        List<Recipe> list = rDao.findRecipeByFlag(organs,flag,start,limit);
        Long count = rDao.getRecipeCountByFlag(organs,flag);

        List<Map<String,Object>> mapList = new ArrayList<>();
        Map<String, Object> countMap = new HashMap();
        countMap.put("start", start);
        countMap.put("limit", limit);
        countMap.put("count", count);
        mapList.add(countMap);
        //只返回要用到的字段
        if(null != list && list.size() > 0) {
            for (Recipe r : list) {
                Map<String, Object> map = new HashMap<>();
                //组装需要的处方数据
                Recipe recipe = new Recipe();
                recipe.setRecipeId(r.getRecipeId());
                recipe.setSignDate(r.getSignDate());
                recipe.setDoctor(r.getDoctor());
                recipe.setDepart(r.getDepart());
                recipe.setOrganDiseaseName(r.getOrganDiseaseName());
                recipe.setChecker(r.getChecker());
                //组装需要的患者数据
                Patient p = pDao.get(r.getMpiid());
                Patient patient = new Patient();
                patient.setPatientName(p.getPatientName());
                patient.setPatientSex(p.getPatientSex());
                Date birthDay = p.getBirthday();
                if (null != birthDay) {
                    patient.setAge(DateConversion.getAge(birthDay));
                }
                //显示一条详情数据
                List<Recipedetail> details = detailDAO.findByRecipeId(r.getRecipeId());
                Recipedetail detail = null;
                if(null != details && details.size() > 0) {
                    detail = details.get(0);
                }
                //checkResult 0:未审核 1:通过 2:不通过
                Integer checkResult = getCheckResult(r);

                Date signDate = r.getSignDate();
                String dateString = "";
                if (null != signDate) {
                    dateString = DateConversion.getDateFormatter(signDate, "yyyy-MM-dd HH:mm");
                }
                map.put("dateString", dateString);
                map.put("recipe", recipe);
                map.put("patient", patient);
                map.put("check", checkResult);
                map.put("detail", detail);
                mapList.add(map);
            }
        }
        return mapList;
    }

    /**
     * 审核平台 获取处方单详情
     * @param recipeId
     * @return
     */
    @RpcService
    public Map<String,Object> findRecipeAndDetailsAndCheckById(int recipeId){
        RecipeDAO rDao = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);

        //取recipe需要的字段
        Recipe recipe = rDao.getByRecipeId(recipeId);
        if(null == recipe){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipe is null!");
        }
        Integer doctorId = recipe.getDoctor();
        Recipe r = new Recipe();
        r.setRecipeId(recipe.getRecipeId());
        r.setRecipeType(recipe.getRecipeType());
        r.setDoctor(doctorId);
        r.setOrganDiseaseName(recipe.getOrganDiseaseName());
        r.setClinicOrgan(recipe.getClinicOrgan());
        r.setSignDate(recipe.getSignDate());
        r.setDepart(recipe.getDepart());
        r.setCreateDate(recipe.getCreateDate());
        r.setCheckDateYs(recipe.getCheckDateYs());
        r.setChecker(recipe.getChecker());
        r.setCheckFailMemo(recipe.getCheckFailMemo());
        r.setCheckOrgan(recipe.getCheckOrgan());
        //诊断备注
        r.setMemo(recipe.getMemo());
        //处方号新加
        r.setRecipeCode(recipe.getRecipeCode());
        //新增配送详细地址
        String address = recipeService.getCompleteAddress(recipeId);
        r.setAddress1(address);
        //新增患者在医院的病历号
        r.setPatientID(recipe.getPatientID());
        //补充说明
        r.setSupplementaryMemo(recipe.getSupplementaryMemo());
        //处方状态
        r.setStatus(recipe.getStatus());
        try {
            String showTip = DictionaryController.instance().get("eh.cdr.dictionary.RecipeStatus").getText(recipe.getStatus());
            r.setShowTip(showTip);
        } catch (ControllerException e) {
            e.printStackTrace();
        }
        //取医生的手机号
        Doctor doctor = doctorDAO.get(doctorId);
        if(null == doctor){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctor is null!");
        }
        Doctor doc = new Doctor();
        doc.setMobile(doctor.getMobile());

        //取patient需要的字段
        Patient patient = patientDAO.get(recipe.getMpiid());
        if(null == patient){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "patient is null!");
        }
        Patient p = new Patient();
        p.setPatientName(patient.getPatientName());
        p.setPatientSex(patient.getPatientSex());
        p.setAge(null == patient.getBirthday() ? 0 : DateConversion.getAge(patient.getBirthday()));
        p.setPatientType(patient.getPatientType());
        //加上手机号 和 身份证信息（脱敏）
        p.setMobile(patient.getMobile());
        p.setIdcard(hideIdCard(patient.getIdcard()));
        p.setMpiId(patient.getMpiId());
        //返回map对象
        Map<String,Object> map = new HashMap<>();

        List<Recipedetail> details = detailDAO.findByRecipeId(recipeId);
        //获取审核不通过详情
        List<Map<String, Object>> mapList = getCheckNotPassDetail(recipeId);
        map.put("reasonAndDetails", mapList);

        //开方日期 yyyy-MM-dd HH:mm 格式化
        Date signDate = recipe.getSignDate();
        String dateString = "";
        if (null != signDate) {
            dateString = DateConversion.getDateFormatter(signDate, "yyyy-MM-dd HH:mm");
        }
        //处方供应商
        Integer enterpriseId = recipe.getEnterpriseId();
        DrugsEnterprise e = new DrugsEnterprise();
        if (enterpriseId != null){
            DrugsEnterprise drugsEnterprise = drugsEnterpriseDAO.get(enterpriseId);
            e.setName(drugsEnterprise.getName());
            e.setPayModeSupport(drugsEnterprise.getPayModeSupport());
        }
        map.put("dateString", dateString);
        map.put("recipe", r);
        map.put("patient", p);
        map.put("doctor", doc);
        map.put("details", details);
        map.put("drugsEnterprise",e);
        return map;
    }

    /**
     * 获取不通过详情
     * @param recipeId
     * @return
     */
    public List<Map<String, Object>> getCheckNotPassDetail(Integer recipeId){
        RecipeCheckDAO recipeCheckDAO = DAOFactory.getDAO(RecipeCheckDAO.class);
        RecipeCheckDetailDAO checkDetailDAO = DAOFactory.getDAO(RecipeCheckDetailDAO.class);
        RecipeCheck recipeCheck = recipeCheckDAO.getByRecipeIdAndCheckStatus(recipeId);
        if(null != recipeCheck){
            //审核不通过 查询审核详情记录
            List<RecipeCheckDetail> checkDetails = checkDetailDAO.findByCheckId(recipeCheck.getCheckId());
            if(null != checkDetails) {
                List<Map<String, Object>> mapList = new ArrayList<>();

                for(RecipeCheckDetail checkDetail : checkDetails){
                    Map<String, Object> checkMap = new HashMap<>();
                    String recipeDetailIds = checkDetail.getRecipeDetailIds();
                    String reasonIds = checkDetail.getReasonIds();
                    List<Integer> detailIdList;
                    List<Integer> reasonIdList;
                    if(StringUtils.isNotEmpty(recipeDetailIds)){
                        detailIdList = JSONUtils.parse(recipeDetailIds, List.class);
                        checkMap.put("checkNotPassDetails", getRecipeDetailList(detailIdList));
                    }
                    if(StringUtils.isNotEmpty(reasonIds)){
                        reasonIdList =JSONUtils.parse(reasonIds, List.class);
                        checkMap.put("reason", getReasonDicList(reasonIdList));
                    }
                    mapList.add(checkMap);
                }

                return mapList;
            }
        }
        return null;
    }

    /**
     * 获取审核结果
     * @param recipe
     * checkResult 0:未审核 1:通过 2:不通过 3:二次签名
     * @return
     */
    private Integer getCheckResult(Recipe recipe){
        Integer checkResult = 0;
        Integer status = recipe.getStatus();
        if(RecipeStatusConstant.READY_CHECK_YS == status){
            checkResult = 0;
        }else {
            if(StringUtils.isNotEmpty(recipe.getSupplementaryMemo())){
                checkResult = 3;
            }else {
                RecipeCheckDAO recipeCheckDAO = DAOFactory.getDAO(RecipeCheckDAO.class);
                List<RecipeCheck> recipeCheckList = recipeCheckDAO.findByRecipeId(recipe.getRecipeId());
                //找不到审核记录 就用以前根据状态判断的方法判断
                if (CollectionUtils.isEmpty(recipeCheckList)) {
                    if (status == RecipeStatusConstant.IN_SEND ||
                            status == RecipeStatusConstant.WAIT_SEND ||
                            status == RecipeStatusConstant.FINISH ||
                            status == RecipeStatusConstant.CHECK_PASS_YS) {
                        checkResult = 1;
                    }
                    if (status == RecipeStatusConstant.CHECK_NOT_PASS_YS) {
                        checkResult = 2;
                    }
                } else {
                    RecipeCheck recipeCheck = recipeCheckList.get(0);
                    if (1 == recipeCheck.getCheckStatus()) {
                        checkResult = 1;
                    } else {
                        checkResult = 2;
                    }

                }
            }
        }

        return checkResult;
    }

    /**
     * 获取地址字典文本
     * @param area
     * @return
     */
    private static String getAddressDic(String area){
        if(StringUtils.isNotEmpty(area)){
            try {
                return DictionaryController.instance().get("eh.base.dictionary.AddrArea").getText(area);
            } catch (ControllerException e) {
                logger.error("获取地址数据类型失败*****area:"+area);
            }
        }
        return "";
    }

    /**
     * 获取原因文本
     * @param reList
     * @return
     */
    private List<String> getReasonDicList(List<Integer> reList){
        List<String> reasonList = new ArrayList<>();
        try {
            Dictionary dictionary = DictionaryController.instance().get("eh.cdr.dictionary.Reason");
            if(null != reList) {
                for (Integer key : reList) {
                    String reason = dictionary.getText(key);
                    if (StringUtils.isNotEmpty(reason)) {
                        reasonList.add(reason);
                    }
                }
            }
        } catch (ControllerException e) {
            logger.error("获取审核不通过原因字典文本出错reasonIds:" + JSONUtils.toString(reList));
        }
        return reasonList;
    }

    /**
     * 根据序号列表 获取详情列表
     * @param ids
     * @return
     */
    private List<Recipedetail> getRecipeDetailList(List<Integer> ids){
        List<Recipedetail> recipedetailList = new ArrayList<>();
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        if(null != ids){
            for(Integer id : ids){
                Recipedetail recipedetail = recipeDetailDAO.getByRecipeDetailId(id);
                if(null != recipedetail) {
                    recipedetailList.add(recipedetail);
                }
            }
        }
        return recipedetailList;
    }

    /**
     * zhongzx
     * 保存药师审核平台审核结果
     * @param paramMap
     * 包含以下属性
     * int         recipeId 处方ID
     * int        checkOrgan  检查机构
     * int        checker    检查人员
     * int        result  1:审核通过 0-通过失败
     * String     failMemo 备注
     * @return boolean
     */
    @RpcService
    public Map<String, Object> saveCheckResult(Map<String, Object> paramMap){
        Integer recipeId = MapValueUtil.getInteger(paramMap, "recipeId");
        Integer result = MapValueUtil.getInteger(paramMap, "result");
        if(null == recipeId || null == result){
            throw new DAOException(DAOException.VALUE_NEEDED, "params are needed");
        }

        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);

        //审核处方单（药师相关数据处理）
        boolean rs = recipeService.reviewRecipe(paramMap);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        //审核成功往药厂发消息
        if(1 == result){
            recipeService.afterCheckPassYs(recipe);
        }else{
            Boolean secondsignflag = organConfigDAO.getEnableSecondsignByOrganId(recipe.getClinicOrgan());
            //不支持二次签名的机构直接执行后续操作
            if(null == secondsignflag || false == secondsignflag) {
                recipeService.afterCheckNotPassYs(recipe);
            }
        }

        Map<String, Object> resMap = new HashMap<>();
        resMap.put("result", rs);
        resMap.put("recipeId", recipeId);
        //把审核结果再返回前端 0:未审核 1:通过 2:不通过
        resMap.put("check", (1 == result)?1:2);
        return resMap;
    }

    //脱敏身份证号
    private String hideIdCard(String idCard) {
        if(StringUtils.isEmpty(idCard)){
            return "";
        }
        //显示前1-3位
        String str1 = idCard.substring(0, 3);
        //显示后15-18位
        String str2 = idCard.substring(14, 18);
        idCard = str1 + "***********" + str2;
        return idCard;
    }

    /**
     * chuwei
     * 前端页面调用该接口查询是否存在待审核的处方单
     * @param organ 审核机构
     * @return
     */
    @RpcService
    public boolean existUncheckedRecipe(int organ){
        RecipeDAO rDao = DAOFactory.getDAO(RecipeDAO.class);
        boolean bResult = rDao.checkIsExistUncheckedRecipe(organ);
        return bResult;
    }

    /**
     * 药师搜索方法 开方医生 审方医生 患者姓名 患者patientId
     * @author zhongzx
     * @param doctorId
     * @param searchString 搜索内容
     * @param searchFlag 0-开方医生 1-审方医生 2-患者姓名 3-病历号
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<Map<String, Object>> searchRecipeForChecker(Integer doctorId, String searchString, Integer searchFlag, Integer start, Integer limit){
        if(null == doctorId){
            logger.error("doctorId is null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId = null 找不到该医生");
        }
        RecipeCheckDAO recipeCheckDAO = DAOFactory.getDAO(RecipeCheckDAO.class);
        AuditPrescriptionOrganDAO auditDao = DAOFactory.getDAO(AuditPrescriptionOrganDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        /**
         * 保存患者搜索记录
         */
        if (!StringUtils.isEmpty(searchString)) {
            SearchContentDAO contentDAO = DAOFactory.getDAO(SearchContentDAO.class);
            SearchContent content = new SearchContent();
            content.setDoctorId(doctorId);
            content.setContent(searchString);
            content.setBussType(4);
            contentDAO.addSearchContent(content, 1);
        }

        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        if(null == doctor){
            logger.error("doctor is null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId = " + doctorId + " 找不到该医生");
        }
        Set<Integer> organs = new HashSet<>();
        List<Integer> organIds = auditDao.findOrganIdsByDoctorId(doctorId);
        if(null == organIds || organIds.size() == 0){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该药师没有配置能审核的机构");
        }
        //organs.add(doctor.getOrgan());
        organs.addAll(organIds);

        List<Recipe> recipeList = recipeCheckDAO.searchRecipe(organs, searchString, searchFlag, start, limit);
        List<Map<String,Object>> mapList = new ArrayList<>();

        if(null != recipeList && recipeList.size() > 0) {
            for (Recipe r : recipeList) {
                Map<String, Object> map = new HashMap<>();
                //组装需要的处方数据
                Recipe recipe = new Recipe();
                recipe.setRecipeId(r.getRecipeId());
                recipe.setSignDate(r.getSignDate());
                recipe.setDoctor(r.getDoctor());
                recipe.setDepart(r.getDepart());
                recipe.setOrganDiseaseName(r.getOrganDiseaseName());
                recipe.setChecker(r.getChecker());
                //组装需要的患者数据
                Patient p = patientDAO.get(r.getMpiid());
                Patient patient = new Patient();
                patient.setPatientName(p.getPatientName());
                patient.setPatientSex(p.getPatientSex());
                Date birthDay = p.getBirthday();
                if (null != birthDay) {
                    patient.setAge(DateConversion.getAge(birthDay));
                }
                //显示一条详情数据
                List<Recipedetail> details = detailDAO.findByRecipeId(r.getRecipeId());
                Recipedetail detail = null;
                if(null != details && details.size() > 0) {
                    detail = details.get(0);
                }
                //checkResult 0:未审核 1:通过 2:不通过
                Integer checkResult = getCheckResult(r);

                Date signDate = r.getSignDate();
                String dateString = "";
                if (null != signDate) {
                    dateString = DateConversion.getDateFormatter(signDate, "yyyy-MM-dd HH:mm");
                }
                map.put("dateString", dateString);
                map.put("recipe", recipe);
                map.put("patient", patient);
                map.put("check", checkResult);
                map.put("detail", detail);
                mapList.add(map);
            }
        }
        return mapList;
    }

}
