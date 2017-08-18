package eh.cdr.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.mvc.upload.exception.FileRegistryException;
import ctd.mvc.upload.exception.FileRepositoryException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.ConditionOperator;
import eh.base.constant.ErrorCode;
import eh.base.constant.PageConstant;
import eh.base.constant.SystemConstant;
import eh.base.dao.*;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.dao.OperationRecordsDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.bus.service.consult.ConsultMessageService;
import eh.bus.service.payment.DaBaiMedicalInsuranceService;
import eh.cdr.bean.PatientRecipeBean;
import eh.cdr.bean.RecipeResultBean;
import eh.cdr.bean.RecipeTagMsgBean;
import eh.cdr.constant.OrderStatusConstant;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.*;
import eh.cdr.drugsenterprise.CommonRemoteService;
import eh.cdr.drugsenterprise.RemoteDrugEnterpriseService;
import eh.cdr.thread.RecipeBusiThreadPool;
import eh.cdr.thread.UpdateRecipeStatusFromHisCallable;
import eh.controller.PayController;
import eh.entity.base.*;
import eh.entity.bus.Consult;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.OpReturnPayParams;
import eh.entity.cdr.*;
import eh.entity.his.DrugInfo;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.dao.FamilyMemberDAO;
import eh.mpi.dao.PatientDAO;
import eh.remote.IESignService;
import eh.task.executor.WxRefundExecutor;
import eh.unifiedpay.constant.PayWayEnum;
import eh.util.CdrUtil;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.MapValueUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.service.PayWayService;
import eh.wxpay.util.PayUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

import static ctd.persistence.DAOFactory.getDAO;


/**
 * 处方服务类
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/4/27.
 */
public class RecipeService {

    private static final Logger logger = LoggerFactory.getLogger(RecipeService.class);

    private static final String UNSIGN = "unsign";

    private static final String UNCHECK = "uncheck";

    public static final String WX_RECIPE_BUSTYPE = "recipe";

    public static final Integer RECIPE_EXPIRED_DAYS = 3;

    // 二次签名处方审核不通过过期时间
    public static final Integer RECIPE_EXPIRED_SECTION = 30;

    //过期处方查询起始天数
    public static final Integer RECIPE_EXPIRED_SEARCH_DAYS = 13;

    /**
     * 判断医生是否可以处方
     * @param doctorId 医生ID
     * @return  Map<String, Object>
     */
    @RpcService
    public Map<String, Object> openRecipeOrNot(Integer doctorId) {
        Boolean canCreateRecipe = false;
        String tips = "";
        Map<String, Object> map = new HashMap<String, Object>();
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        List<Employment> employmentList =  dao.findEmByDoctorId(doctorId);
        List<Integer> organIdList = new ArrayList<>();
        if(employmentList.size()>0) {
            for (Employment employment : employmentList) {
                organIdList.add(employment.getOrganId());
            }
            OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
            int listNum = organDrugListDAO.getCountByOrganIdAndStatus(organIdList);
            canCreateRecipe= listNum>0;
            if(!canCreateRecipe){
                tips="抱歉，您所在医院暂不支持开处方业务。";
            }
        }

        //能否开医保处方
        boolean medicalFlag = false;
        if(canCreateRecipe){
            ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet set = consultSetDAO.get(doctorId);
            if(null != set && null != set.getMedicarePrescription()){
                medicalFlag = (true == set.getMedicarePrescription())?true:false;
            }
        }

        map.put("result", canCreateRecipe);
        map.put("medicalFlag", medicalFlag);
        map.put("tips",tips);
        return map;

    }

    /**
     * 新的处方列表  pc端仍在使用
     * @param doctorId  医生ID
     * @param start     记录开始下标
     * @param limit     每页限制条数
     * @return list
     */
    @RpcService
    public List<HashMap<String, Object>> findNewRecipeAndPatient(int doctorId, int start, int limit) {
        return findRecipesAndPatientsByDoctor(doctorId, start, PageConstant.getPageLimit(limit), 0);
    }

    /**
     * 历史处方列表 pc端仍在使用
     * @param doctorId  医生ID
     * @param start     记录开始下标
     * @param limit     每页限制条数
     * @return list
     */
    @RpcService
    public List<HashMap<String, Object>> findOldRecipeAndPatient(int doctorId,int start, int limit) {
        return findRecipesAndPatientsByDoctor(doctorId, start, PageConstant.getPageLimit(limit), 1);
    }

    /**
     * 强制删除处方(接收医院处方发送失败时处理)
     * @param recipeId  处方ID
     * @return  boolean
     */
    @RpcService
    public Boolean delRecipeForce(int recipeId) {
        logger.info("delRecipeForce [recipeId:"+recipeId+"]");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        recipeDAO.remove(recipeId);
        return true;
    }

    /**
     * 删除处方
     * @param recipeId  处方ID
     * @return  boolean
     */
    @RpcService
    public Boolean delRecipe(int recipeId) {
        logger.info("delRecipe [recipeId:"+recipeId+"]");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不存在或者已删除");
        }
        if (null == recipe.getStatus() || recipe.getStatus() > RecipeStatusConstant.UNSIGN) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不是新处方或者审核失败的处方，不能删除");
        }

        boolean rs = recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.DELETE, null);

        //记录日志
        RecipeLogService.saveRecipeLog(recipeId,recipe.getStatus(), RecipeStatusConstant.DELETE,"删除处方单");

        return rs;
    }

    /**
     * 撤销处方单
     * @param recipeId   处方ID
     * @return    boolean
     */
    @RpcService
    public Boolean undoRecipe(int recipeId) {
        logger.info("undoRecipe [recipeId：" + recipeId+"]");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不存在或者已删除");
        }
        if (null == recipe.getStatus() || RecipeStatusConstant.UNCHECK != recipe.getStatus()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不是待审核的处方，不能撤销");
        }

        boolean rs = recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.UNSIGN, null);

        //记录日志
        RecipeLogService.saveRecipeLog(recipeId,recipe.getStatus(), RecipeStatusConstant.UNSIGN,"撤销处方单");

        return rs;
    }

    /**
     * 保存处方
     * @param recipe  处方对象
     * @param details 处方详情
     * @return int
     */
    @RpcService
    public Integer saveRecipeData(Recipe recipe, List<Recipedetail> details) {
         return saveRecipeDataImpl(recipe,details,1);
    }

    /**
     * 保存HIS处方
     * @param recipe
     * @param details
     * @return
     */
    public Integer saveRecipeDataForHos(Recipe recipe, List<Recipedetail> details){
        return saveRecipeDataImpl(recipe,details,0);
    }

    /**
     *
     * @param recipe
     * @param details
     * @param flag(recipe的fromflag) 0：HIS处方  1：平台处方
     * @return
     */
    private Integer saveRecipeDataImpl(Recipe recipe, List<Recipedetail> details, Integer flag){
        if(null != recipe && recipe.getRecipeId() != null && recipe.getRecipeId() > 0){
            return updateRecipeAndDetail(recipe, details);
        }

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        validateSaveRecipeData(recipe);
        recipe.setDefaultData();

        if(null == details){
            details = new ArrayList<>(0);
        }
        for (Recipedetail recipeDetail : details) {
            validateRecipeDetailData(recipeDetail, recipe);
        }

        if(1 == flag) {
            boolean isSucc = setDetailsInfo(recipe, details);
            if(!isSucc){
                throw new DAOException(ErrorCode.SERVICE_ERROR, "药品详情数据有误");
            }
        }else if(0 == flag){
            //处方总价未计算
            BigDecimal totalMoney = new BigDecimal(0d);
            for (Recipedetail detail : details) {
                if(null != detail.getDrugCost()){
                    totalMoney = totalMoney.add(detail.getDrugCost());
                }
            }
            recipe.setTotalMoney(totalMoney);
            recipe.setActualPrice(totalMoney);
        }

        Integer recipeId = recipeDAO.updateOrSaveRecipeAndDetail(recipe,details,false);
        recipe.setRecipeId(recipeId);

        //加入历史患者
        OperationRecordsDAO operationRecordsDAO = DAOFactory.getDAO(OperationRecordsDAO.class);
        operationRecordsDAO.saveOperationRecordsForRecipe(recipe);

        RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(),"暂存处方单");
        return recipeId;
    }

    /**
     * 修改处方
     * @param recipe  处方对象
     * @param recipedetails 处方详情
     */
    @RpcService
    public Integer updateRecipeAndDetail(Recipe recipe, List<Recipedetail> recipedetails) {
        if (recipe == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "recipe is required!");
        }
        Integer recipeId = recipe.getRecipeId();
        if (recipeId == null || recipeId <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "recipeId is required!");
        }
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);

        Recipe dbRecipe = recipeDAO.getByRecipeId(recipeId);
        if (null == dbRecipe.getStatus() || dbRecipe.getStatus() > RecipeStatusConstant.UNSIGN) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不是新处方或者审核失败的处方，不能修改");
        }

        int beforeStatus = dbRecipe.getStatus();
        if(null == recipedetails){
            recipedetails = new ArrayList<>(0);
        }
        for (Recipedetail recipeDetail : recipedetails) {
            validateRecipeDetailData(recipeDetail, dbRecipe);
        }
        //由于使用BeanUtils.map，空的字段不会进行copy，要进行手工处理
        if(StringUtils.isEmpty(recipe.getMemo())){
            dbRecipe.setMemo("");
        }
        //复制修改的数据
        BeanUtils.map(recipe, dbRecipe);
        //将原先处方单详情的记录都置为无效 status=0
        recipeDetailDAO.updateDetailInvalidByRecipeId(recipeId);
        //设置药品价格
        boolean isSucc = setDetailsInfo(dbRecipe, recipedetails);
        if(!isSucc){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "药品详情数据有误");
        }
        Integer dbRecipeId = recipeDAO.updateOrSaveRecipeAndDetail(dbRecipe,recipedetails,true);
        //记录日志
        RecipeLogService.saveRecipeLog(dbRecipeId,beforeStatus,beforeStatus,"修改处方单");

        return dbRecipeId;
    }

    /**
     * 保存处方电子病历
     * @param recipe 处方对象
     */
    public void saveRecipeDocIndex(Recipe recipe){
        DocIndexDAO indexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        indexDAO.saveRecipeDocIndex(recipe);
    }

    /**
     * 根据处方ID获取完整地址
     * @param recipeId
     * @return
     */
    @RpcService
    public String getCompleteAddress(Integer recipeId){
        String address = "";
        if(null != recipeId){
            CommonRemoteService commonRemoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

            Recipe recipe = recipeDAO.get(recipeId);
            if(null != recipe){
                if(null != recipe.getAddressId()){
                    StringBuilder sb = new StringBuilder();
                    commonRemoteService.getAddressDic(sb,recipe.getAddress1());
                    commonRemoteService.getAddressDic(sb,recipe.getAddress2());
                    commonRemoteService.getAddressDic(sb,recipe.getAddress3());
                    sb.append(StringUtils.isEmpty(recipe.getAddress4())?"":recipe.getAddress4());
                    address = sb.toString();
                }

                if(StringUtils.isEmpty(address)){
                    RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
                    //从订单获取
                    RecipeOrder order = recipeOrderDAO.getOrderByRecipeId(recipeId);
                    if(null != order && null != order.getAddressID()) {
                        address = commonRemoteService.getCompleteAddress(order);
                    }
                }
            }
        }

        return address;
    }

    /**
     * 审核处方结果(药师平台)
     * @param   paramMap 参数
     * 包含一下属性
     * int         recipeId 处方ID
     * int        checkOrgan  检查机构
     * int        checker    检查人员
     * int        result  1:审核通过 0-通过失败
     * String     failMemo 备注
     * List<Map<String, Object>>     checkList
     */
    public boolean reviewRecipe(Map<String, Object> paramMap) {
        Integer recipeId = MapValueUtil.getInteger(paramMap, "recipeId");
        Integer checkOrgan = MapValueUtil.getInteger(paramMap, "checkOrgan");
        Integer checker = MapValueUtil.getInteger(paramMap, "checker");
        Integer checkFlag = MapValueUtil.getInteger(paramMap, "result");
        //校验数据
        if(null == recipeId || null == checkOrgan || null == checker || null == checkFlag){
            throw new DAOException(DAOException.VALUE_NEEDED, "recipeId or checkOrgan or checker or result is null");
        }
        String memo = MapValueUtil.getString(paramMap, "failMemo");
        Object checkListObj = paramMap.get("checkList");

        List<Map<String, Object>> checkList = null;
        if(null != checkListObj && checkListObj instanceof List){
            checkList = (List<Map<String, Object>>) checkListObj;
        }
        //如果审核不通过 详情审核结果不能为空
        if(0 == checkFlag){
            if(null == checkList || checkList.size() == 0){
                throw new DAOException(DAOException.VALUE_NEEDED, "详情审核结果不能为空");
            }else{
                for (Map<String, Object> map : checkList) {
                    String recipeDetailIds = MapValueUtil.getString(map, "recipeDetailIds");
                    String reasonIds = MapValueUtil.getString(map, "reasonIds");
                    if (StringUtils.isEmpty(recipeDetailIds) || StringUtils.isEmpty(reasonIds)) {
                        throw new DAOException(DAOException.VALUE_NEEDED, "请选择不通过理由以及不合理药品");
                    }
                }
            }
        }
        logger.info("reviewRecipe [recipeId：" + recipeId+",checkFlag: "+checkFlag+"]");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (null == recipe) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不存在或者已删除");
        }
        if (null == recipe.getStatus() || recipe.getStatus() != RecipeStatusConstant.READY_CHECK_YS) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方已被审核");
        }

        int beforeStatus = recipe.getStatus();
        String logMemo = "审核不通过(药师平台):"+memo;
        int recipeStatus = RecipeStatusConstant.CHECK_NOT_PASS_YS;
        if(1 == checkFlag){
            //成功
            recipeStatus = RecipeStatusConstant.CHECK_PASS_YS;
            if(recipe.canMedicalPay()){
                //如果是可医保支付的单子，审核是在用户看到之前，所以审核通过之后变为待处理状态
                recipeStatus = RecipeStatusConstant.CHECK_PASS;
            }
            logMemo = "审核通过(药师平台)";
        }

        Date now = DateTime.now().toDate();
        Map<String,Object> attrMap = new HashMap<>();
        attrMap.put("checkDateYs",now);
        attrMap.put("checkOrgan",checkOrgan);
        attrMap.put("checker",checker);
        attrMap.put("checkFailMemo",(StringUtils.isEmpty(memo))?"":memo);

        //保存审核记录和详情审核记录
        RecipeCheck recipeCheck = new RecipeCheck();
        recipeCheck.setChecker(checker);
        recipeCheck.setRecipeId(recipeId);
        recipeCheck.setCheckOrgan(checkOrgan);
        recipeCheck.setCheckDate(now);
        recipeCheck.setMemo((StringUtils.isEmpty(memo)) ? "" : memo);
        recipeCheck.setCheckStatus(checkFlag);
        List<RecipeCheckDetail> recipeCheckDetails;
        if (0 == checkFlag) {
            recipeCheckDetails = new ArrayList<>();
            for (Map<String, Object> map : checkList) {
                //这里的数组是已字符串的形式传入保存，查询详情时需要解析成数组
                String recipeDetailIds = MapValueUtil.getString(map, "recipeDetailIds");
                String reasonIds = MapValueUtil.getString(map, "reasonIds");
                if (StringUtils.isEmpty(recipeDetailIds) || StringUtils.isEmpty(reasonIds)) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "请选择不通过理由以及不合理药品");
                }
                RecipeCheckDetail recipeCheckDetail = new RecipeCheckDetail();
                recipeCheckDetail.setRecipeDetailIds(recipeDetailIds);
                recipeCheckDetail.setReasonIds(reasonIds);
                recipeCheckDetails.add(recipeCheckDetail);
            }
        } else {
            recipeCheckDetails = null;
        }
        RecipeCheckDAO recipeCheckDAO = DAOFactory.getDAO(RecipeCheckDAO.class);
        recipeCheckDAO.saveRecipeCheckAndDetail(recipeCheck, recipeCheckDetails);

        boolean bl = recipeDAO.updateRecipeInfoByRecipeId(recipeId,recipeStatus,attrMap);
        if(!bl){
            logger.error("reviewRecipe update recipe["+recipeId+"] error!");
            return bl;
        }

        //记录日志
        RecipeLogService.saveRecipeLog(recipeId,beforeStatus,recipeStatus,logMemo);

        if(1 == checkFlag){
            if(null != recipe.getSignFile()){
                //先下载oss服务器上的签名文件
                InputStream is = null;
                BufferedInputStream bis = null;
                ByteArrayOutputStream out = null;
                byte[] byteData = null;
                FileService fileService = AppContextHolder.getBean("fileService", FileService.class);
                try {
                    FileMetaRecord fileMetaRecord = fileService.getRegistry().load(recipe.getSignFile());
                    if(null != fileMetaRecord) {
                        is = fileService.getRepository().readAsStream(fileMetaRecord);
                        bis = new BufferedInputStream(is);
                    }
                    if(null != bis){
                        byte[] byteArray = new byte[1024];
                        int len = 0;
                        out = new ByteArrayOutputStream();
                        while((len=bis.read(byteArray))!=-1){
                            out.write(byteArray, 0, len);
                        }
                        byteData = out.toByteArray();
                    }
                } catch (FileRegistryException e) {
                    logger.error("reviewRecipe download signFile occur FileRegistryException signFileId="+recipe.getSignFile());
                    bl = false;
                } catch (FileRepositoryException e) {
                    logger.error("reviewRecipe download signFile occur FileRepositoryException signFileId="+recipe.getSignFile());
                    bl = false;
                } catch (IOException e) {
                    logger.error("reviewRecipe download signFile occur IOException signFileId="+recipe.getSignFile());
                    bl = false;
                } finally {
                    if(null != bis){try {bis.close();} catch (IOException e) {logger.error("error:"+e);}}
                    if(null != is){try {is.close();} catch (IOException e) {logger.error("error:"+e);}}
                    if(null != out){try {out.close();} catch (IOException e) {logger.error("error:"+e);}}
                }

                //往文件上打签名
                if(bl && !Integer.valueOf(0).equals(checker)){
                    IESignService eSignService = AppContextHolder.getBean("esign.esignService", IESignService.class);
                    //以前是Chemist对象 现在 是Doctor对象 userType=5 是药师
                    //zhongzx
                    DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    Doctor chemist = doctorDAO.getByDoctorId(checker);
                    EsignPerson person = new EsignPerson();
                    person.setName(chemist.getName());
                    person.setIdNumber(chemist.getIdNumber());
                    person.setMobile(chemist.getMobile());
                    if(StringUtils.isEmpty(chemist.getESignId())){
                        //没有在e签宝注册则需要先注册
                        String eSignId = registerEsign(chemist.getESignId(),person);
                        chemist.setESignId(eSignId);
                        doctorDAO.update(chemist);
                    }

                    if(StringUtils.isNotEmpty(chemist.getESignId())){
                        String fileName = "recipe" + System.currentTimeMillis() + ".pdf";

                        Map<String,Object> dataMap = new HashMap<>();
                        dataMap.put("userId",chemist.getESignId());
                        dataMap.put("fileName",fileName);
                        dataMap.put("data",byteData);
                        DoctorExtendDAO doctorExtendDAO = DAOFactory.getDAO(DoctorExtendDAO.class);
                        DoctorExtend doctorExtend = doctorExtendDAO.getByDoctorId(chemist.getDoctorId());
                        if(null != doctorExtend) {
                            dataMap.put("sealData", doctorExtend.getSealData());
                        }

                        //签名
                        byte[] data = null;
                        try {
//                            logger.info("reviewRecipe signForChemist para:"+JSONUtils.toString(dataMap));
                            data = eSignService.signForChemist(dataMap);
                        } catch (Exception e) {
                            logger.error("reviewRecipe signForChemist exception :"+e.getMessage()+",data:"+dataMap.toString());
                        }
                        if(null == data || data.length == 0){
                            logger.error("reviewRecipe sign back data error!");
                            bl = false;
                        }

                        if(bl){
                            //上传阿里云
                            Integer recipeFileId = recipeDAO.uploadRecipeFile(data, fileName);
                            if(null == recipeFileId){
                                logger.error("reviewRecipe upload aliyun error!");
                                bl = false;
                            }

                            if(bl) {
                                Map<String, Object> _attrMap = new HashMap<>();
                                _attrMap.put("chemistSignFile", recipeFileId);
                                bl = recipeDAO.updateRecipeInfoByRecipeId(recipeId, _attrMap);
                            }

                            if (bl) {
                                logger.info("chemist " + chemist.getName() + " sign finish. fileId=" + recipeFileId);
                            }
                        }
                    }else{
                        logger.error("reviewRecipe register esign error recipeId="+recipeId);
                        bl = false;
                    }
                }else{
                    logger.error("reviewRecipe chemist is empty recipeId="+recipeId);
                    bl = false;
                }
            }else{
                logger.error("reviewRecipe signFile is empty recipeId="+recipeId);
                bl = false;
            }

            if(!bl){
                RecipeLogService.saveRecipeLog(recipeId,beforeStatus,recipeStatus,"reviewRecipe 添加药师签名失败");
            }
        }

        return bl;
    }

    /**
     * 药师审核不通过的情况下，医生重新开处方
     * @param recipeId
     * @return
     */
    @RpcService
    public List<Recipedetail> reCreatedRecipe(Integer recipeId){
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Recipe dbRecipe = checkRecipeCommonInfo(recipeId,resultBean);
        if (null == dbRecipe){
            logger.error("reCreatedRecipe 平台无该处方对象. recipeId=[{}] error={}", recipeId, JSONUtils.toString(resultBean));
            return Lists.newArrayList();
        }
        Integer status = dbRecipe.getStatus();
        if (null == status || status != RecipeStatusConstant.CHECK_NOT_PASS_YS) {
            logger.error("reCreatedRecipe 该处方不是审核未通过的处方. recipeId=[{}]", recipeId);
            return Lists.newArrayList();
        }
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        boolean effective = orderDAO.isEffectiveOrder(dbRecipe.getOrderCode(),dbRecipe.getPayMode());
        if(effective) {
            afterCheckNotPassYs(dbRecipe);
        }
        return validateDrugsImpl(dbRecipe);
    }

    /**
     * 重新开具 或这续方时校验 药品数据
     * @param recipeId
     * @return
     */
    @RpcService
    public List<Recipedetail> validateDrugs(Integer recipeId){
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Recipe dbRecipe = checkRecipeCommonInfo(recipeId,resultBean);
        if (null == dbRecipe){
            logger.error("validateDrugs 平台无该处方对象. recipeId=[{}] error={}", recipeId, JSONUtils.toString(resultBean));
            return Lists.newArrayList();
        }
        return validateDrugsImpl(dbRecipe);
    }


    private List<Recipedetail> validateDrugsImpl(Recipe recipe){
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

        Integer organId = recipe.getClinicOrgan();
        Integer recipeId = recipe.getRecipeId();
        List<Recipedetail> backDetailList = new ArrayList<>();
        List<Recipedetail> details = detailDAO.findByRecipeId(recipeId);
        if(CollectionUtils.isEmpty(details)){
            return backDetailList;
        }
        List<Integer> drugIdList = FluentIterable.from(details).transform(new Function<Recipedetail, Integer>() {
            @Override
            public Integer apply(Recipedetail input) {
                return input.getDrugId();
            }
        }).toList();

        //校验当前机构可开具药品是否满足
        List<OrganDrugList> organDrugList = organDrugListDAO.findByOrganIdAndDrugIds(organId,drugIdList);
        if(CollectionUtils.isEmpty(organDrugList)){
            return backDetailList;
        }

        //2边长度一致直接返回
        if(organDrugList.size() == drugIdList.size()){
            return details;
        }

        Map<Integer, Recipedetail> drugIdAndDetailMap = Maps.uniqueIndex(details, new Function<Recipedetail, Integer>() {
            @Override
            public Integer apply(Recipedetail input) {
                return input.getDrugId();
            }
        });

        Recipedetail mapDetail;
        for(OrganDrugList organDrug : organDrugList){
            mapDetail = drugIdAndDetailMap.get(organDrug.getDrugId());
            if(null != mapDetail) {
                backDetailList.add(mapDetail);
            }
        }

        return backDetailList;
    }

    /**
     * 组装生成pdf的参数集合
     * zhongzx
     * @param recipe 处方对象
     * @param details 处方详情
     * @return Map<String, Object>
     */
    private Map<String, Object> createParamMap(Recipe recipe, List<Recipedetail> details, String fileName){
        PatientDAO pdao = DAOFactory.getDAO(PatientDAO.class);
        DrugListDAO dDao = DAOFactory.getDAO(DrugListDAO.class);
        Map<String, Object> paramMap = new HashMap<>();
        try {
            Patient p = pdao.get(recipe.getMpiid());
            if (null == p) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "病人不存在");
            }
            //模板类型，西药模板
            paramMap.put("templateType", "wm");
            //生成pdf文件的入参
            paramMap.put("fileName", fileName);
            paramMap.put("recipeType", recipe.getRecipeType());
            String recipeType = DictionaryController.instance().get("eh.cdr.dictionary.RecipeType").getText(recipe.getRecipeType());
            paramMap.put("title", recipeType + "处方笺");
            paramMap.put("pName", p.getPatientName());
            paramMap.put("pGender", DictionaryController.instance().get("eh.base.dictionary.Gender").getText(p.getPatientSex()));
            paramMap.put("pAge", DateConversion.getAge(p.getBirthday()) + "岁");
            paramMap.put("pType", DictionaryController.instance().get("eh.mpi.dictionary.PatientType").getText(p.getPatientType()));
            paramMap.put("doctor", DictionaryController.instance().get("eh.base.dictionary.Doctor").getText(recipe.getDoctor()));
            String organ = DictionaryController.instance().get("eh.base.dictionary.Organ").getText(recipe.getClinicOrgan());
            String depart = DictionaryController.instance().get("eh.base.dictionary.Depart").getText(recipe.getDepart());
            paramMap.put("organInfo", organ);
            paramMap.put("departInfo", depart);
            paramMap.put("disease", recipe.getOrganDiseaseName());
            paramMap.put("cDate", DateConversion.getDateFormatter(recipe.getSignDate(),"yyyy-MM-dd HH:mm"));
            paramMap.put("diseaseMemo", recipe.getMemo());
            paramMap.put("recipeCode", recipe.getRecipeCode().startsWith("ngari")?"":recipe.getRecipeCode());
            paramMap.put("patientId", recipe.getPatientID());
            paramMap.put("mobile", p.getMobile());
            paramMap.put("label", recipeType + "处方");
            int i = 0;
            List<Integer> drugIds = new ArrayList();
            for (Recipedetail d : details) {
                drugIds.add(d.getDrugId());
            }
            List<DrugList> dlist = dDao.findByDrugIds(drugIds);
            Map<Integer, DrugList> dMap = new HashMap<>();
            for (DrugList d : dlist) {
                dMap.put(d.getDrugId(), d);
            }
            Dictionary usingRateDic = DictionaryController.instance().get("eh.cdr.dictionary.UsingRate");
            Dictionary usePathwaysDic = DictionaryController.instance().get("eh.cdr.dictionary.UsePathways");
            for (Recipedetail d : details) {
                DrugList drug = dMap.get(d.getDrugId());
                String dName = (i+1) + "、" + drug.getDrugName();
                //规格+药品单位
                String dSpec = drug.getDrugSpec() + "/" + drug.getUnit();
                //使用天数
                String useDay = d.getUseDays() + "天";
                //每次剂量+剂量单位
                String uDose = "Sig: " + "每次" + d.getUseDose() + drug.getUseDoseUnit();
                //开药总量+药品单位
                String dTotal = "X" + d.getUseTotalDose() + drug.getUnit();
                //用药频次
                String dRateName = d.getUsingRate() + "(" + usingRateDic.getText(d.getUsingRate()) + ")";
                //用法
                String dWay = d.getUsePathways() + "(" + usePathwaysDic.getText(d.getUsePathways()) + ")";
                paramMap.put("drugInfo" + i, dName + dSpec);
                paramMap.put("dTotal" + i, dTotal);
                paramMap.put("useInfo" + i, uDose + "    " + dRateName + "    " + dWay + "    " + useDay);
                if (!StringUtils.isEmpty(d.getMemo())) {
                    //备注
                    paramMap.put("dMemo" + i, "备注:" + d.getMemo());
                }
                i++;
            }
            logger.info("createParamMap paramMap:"+JSONUtils.toString(paramMap));
        }catch (Exception e){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "createParamMap 组装参数错误:========="+e.getMessage());
        }
        return paramMap;
    }

    /**
     * 中药处方pdf模板
     * @param recipe
     * @param details
     * @param fileName
     * @Author liuya
     * @return
     */
    private Map<String, Object> createParamMapForChineseMedicine(Recipe recipe, List<Recipedetail> details, String fileName){
        PatientDAO pdao = DAOFactory.getDAO(PatientDAO.class);
        DrugListDAO dDao = DAOFactory.getDAO(DrugListDAO.class);
        Map<String, Object> paramMap = new HashMap<>();
        try {
            Patient p = pdao.get(recipe.getMpiid());
            if (null == p) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "病人不存在");
            }
            //模板类型，中药类模板
            paramMap.put("templateType", "tcm");
            //生成pdf文件的入参
            paramMap.put("fileName", fileName);
            paramMap.put("recipeType", recipe.getRecipeType());
            String recipeType = DictionaryController.instance().get("eh.cdr.dictionary.RecipeType").getText(recipe.getRecipeType());
            paramMap.put("title", recipeType + "处方笺");
            paramMap.put("pName", p.getPatientName());
            paramMap.put("pGender", DictionaryController.instance().get("eh.base.dictionary.Gender").getText(p.getPatientSex()));
            paramMap.put("pAge", DateConversion.getAge(p.getBirthday()) + "岁");
            paramMap.put("pType", DictionaryController.instance().get("eh.mpi.dictionary.PatientType").getText(p.getPatientType()));
            paramMap.put("doctor", DictionaryController.instance().get("eh.base.dictionary.Doctor").getText(recipe.getDoctor()));
            String organ = DictionaryController.instance().get("eh.base.dictionary.Organ").getText(recipe.getClinicOrgan());
            String depart = DictionaryController.instance().get("eh.base.dictionary.Depart").getText(recipe.getDepart());
            paramMap.put("organInfo", organ);
            paramMap.put("departInfo", depart);
            paramMap.put("disease", recipe.getOrganDiseaseName());
            paramMap.put("cDate", DateConversion.getDateFormatter(recipe.getSignDate(),"yyyy-MM-dd HH:mm"));
            paramMap.put("diseaseMemo", recipe.getMemo());
            paramMap.put("recipeCode", recipe.getRecipeCode().startsWith("ngari")?"":recipe.getRecipeCode());
            paramMap.put("patientId", recipe.getPatientID());
            paramMap.put("mobile", p.getMobile());
            paramMap.put("label", recipeType + "处方");
            paramMap.put("copyNum", recipe.getCopyNum()+"剂");
            paramMap.put("recipeMemo", recipe.getRecipeMemo());
            int i = 0;
            List<Integer> drugIds = new ArrayList();
            for (Recipedetail d : details) {
                drugIds.add(d.getDrugId());
            }
            List<DrugList> dlist = dDao.findByDrugIds(drugIds);
            Map<Integer, DrugList> dMap = new HashMap<>();
            for (DrugList d : dlist) {
                dMap.put(d.getDrugId(), d);
            }
            for (Recipedetail d : details) {
                DrugList drug = dMap.get(d.getDrugId());
                String dName = drug.getDrugName();
                //开药总量+药品单位
                String dTotal = "";
                //增加判断条件  如果用量小数位为零，则不显示小数点
                if((d.getUseDose()-d.getUseDose().intValue()) == 0d){
                    dTotal = d.getUseDose().intValue() + drug.getUseDoseUnit();
                } else {
                    dTotal = d.getUseDose() + drug.getUseDoseUnit();
                }
                if (!StringUtils.isEmpty(d.getMemo())) {
                    //备注
                    dTotal = dTotal+ "*" + d.getMemo();
                }
                paramMap.put("drugInfo" + i, dName+"¨"+dTotal);
                paramMap.put("tcmUsePathways", d.getUsePathways());
                paramMap.put("tcmUsingRate", d.getUsingRate());
                i++;
            }
            logger.info("createParamMapForChineseMedicine paramMap:"+JSONUtils.toString(paramMap));
        }catch (Exception e){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "createParamMapForChineseMedicine 组装参数错误:========="+e.toString());
        }
        return paramMap;
    }

    /**
     * 生成pdf并签名
     * @param recipeId
     */
    @RpcService
    public void generateRecipePdfAndSign(Integer recipeId){
        if(null == recipeId){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipeId is null");
        }
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        List<Recipedetail> details = detailDAO.findByRecipeId(recipeId);

        //组装生成pdf的参数
        String fileName = "recipe_" + recipeId + ".pdf";
        Map<String, Object> paramMap = Maps.newHashMap();
        recipe.setSignDate(DateTime.now().toDate());
        if(recipe.containTcmType()){
            //中药pdf参数
            paramMap = createParamMapForChineseMedicine(recipe, details, fileName);
        } else {
            paramMap = createParamMap(recipe, details, fileName);
        }

        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doc = doctorDao.getByDoctorId(recipe.getDoctor());
        if(null == doc){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctor is null");
        }
        String strEsignCode = doc.getESignId();
        IESignService eSignService = AppContextHolder.getBean("esign.esignService", IESignService.class);
        // 未在E宝进行注册的医生先进行注册
        if (StringUtils.isEmpty(strEsignCode)) {
            EsignPerson person = new EsignPerson();
            person.setMobile(doc.getMobile());
            person.setName(doc.getName());
            person.setIdNumber(doc.getIdNumber());
            person.setEmail(doc.getEmail());
            try {
                strEsignCode = eSignService.addPerson(person);
            }catch (Exception e){
                logger.error("generateRecipePdfAndSign register esign error！ error=[{}], person=[{}]", "用户E宝注册失败！!", e.getMessage(), JSONUtils.toString(person));
                throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
            }
            if (StringUtils.isEmpty(strEsignCode)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "用户E宝注册失败！账户返回为空");
            }
            doc.setESignId(strEsignCode);
            doctorDao.update(doc);
        }
        DoctorExtendDAO doctorExtendDAO = DAOFactory.getDAO(DoctorExtendDAO.class);
        DoctorExtend doctorExtend = doctorExtendDAO.getByDoctorId(recipe.getDoctor());
        if(null != doctorExtend) {
            paramMap.put("sealData", doctorExtend.getSealData());
        }
        // 未签名处方进行签名
        byte[] data = null;
        try {
            data = eSignService.signRecipePDF(strEsignCode, paramMap);
        }catch (Exception e){
            logger.error("sign pdf fail! error=[{}]", e.getMessage());
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
        //上传阿里云
        String memo = "";
        Integer recipeFileId = recipeDAO.uploadRecipeFileForHisCallBack(data, fileName, doc.getMobile());
        if (null == recipeFileId) {
            memo = "pdf文件流生成pdf文件并上传到阿里云失败！";
            logger.error("recipeFileId is null, pdf文件流生成pdf文件并上传到阿里云失败！");
        } else {
            Map<String, Object> attrMap = Maps.newHashMap();
            attrMap.put("signFile", recipeFileId);
            attrMap.put("signDate", recipe.getSignDate());
            recipeDAO.updateRecipeInfoByRecipeId(recipeId, attrMap);
            memo = "实际生成pdf并签名成功";
            logger.info("generateRecipePdfAndSign upload aliyun finish. fileId:" + recipeFileId + ",recipeId:" + recipe.getRecipeId());
        }
        //日志记录
        RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), memo);
    }

    /**
     * 重试
     * @param recipeId
     */
    @RpcService
    public RecipeResultBean sendNewRecipeToHIS(Integer recipeId){
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);

        Integer status = recipeDAO.getStatusByRecipeId(recipeId);
        if (null == status || status != RecipeStatusConstant.CHECKING_HOS) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方不能重试");
        }

        //HIS消息发送
        RecipeResultBean scanResult = hisService.scanDrugStockByRecipeId(recipeId);
        if(RecipeResultBean.FAIL.equals(scanResult.getCode())){
            resultBean.setCode(scanResult.getCode());
            resultBean.setMsg(scanResult.getError());
            if("1".equals(scanResult.getExtendValue())){
                resultBean.setError(scanResult.getError());
            }
            return resultBean;
        }

        hisService.recipeSendHis(recipeId,null);
        return resultBean;
    }

    /**
     * 发送只能配送处方，当医院库存不足时医生略过库存提醒后调用
     * @param recipe
     * @return
     */
    @RpcService
    public Map<String, Object> sendDistributionRecipe(Recipe recipe, List<Recipedetail> details){
        if(null == recipe){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "传入参数为空");
        }

        recipe.setDistributionFlag(1);
        recipe.setGiveMode(RecipeConstant.GIVEMODE_SEND_TO_HOME);
        return doSignRecipeExt(recipe,details);
    }

    /**
     * 签名服务
     * @param recipe 处方
     * @param details 详情
     * @return  Map<String, Object>
     */
    @RpcService
    public Map<String, Object> doSignRecipe(Recipe recipe, List<Recipedetail> details) {
        PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);

        Map<String,Object> rMap = new HashMap<>();
        Patient patient=patDao.get(recipe.getMpiid());
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (patient==null || StringUtils.isEmpty(patient.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能开处方");
        }
        recipe.setStatus(RecipeStatusConstant.UNSIGN);
        recipe.setSignDate(DateTime.now().toDate());
        Integer recipeId = recipe.getRecipeId();
        //如果是已经暂存过的处方单，要去数据库取状态 判断能不能进行签名操作
        if(null != recipeId && recipeId > 0) {
            Integer status = recipeDAO.getStatusByRecipeId(recipeId);
            if (null == status || status > RecipeStatusConstant.UNSIGN) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "处方单已处理,不能重复签名");
            }

            updateRecipeAndDetail(recipe, details);
        }else{
            recipeId = saveRecipeData(recipe, details);
        }

        //非只能配送处方需要进行医院库存校验
        if(!Integer.valueOf(1).equals(recipe.getDistributionFlag())) {
            //HIS消息发送
            RecipeResultBean scanResult = hisService.scanDrugStockByRecipeId(recipeId);
            if (RecipeResultBean.FAIL.equals(scanResult.getCode())) {
                rMap.put("signResult", false);
                rMap.put("recipeId", recipe.getRecipeId());
                rMap.put("msg", scanResult.getError());
                if("1".equals(scanResult.getExtendValue())) {
                    rMap.put("scanDrugStock", true);
                }
                return rMap;
            }
        }

        //HIS消息发送
        boolean result = hisService.recipeSendHis(recipeId,null);
        rMap.put("signResult",result);
        rMap.put("recipeId",recipeId);

        logger.info("doSignRecipe execute ok! rMap:"+JSONUtils.toString(rMap));
        return rMap;
    }

    /**
     * 新版签名服务
     * @param recipe 处方
     * @param details 详情
     * @paran consultId  咨询单Id
     * @return  Map<String, Object>
     */
    @RpcService
    public Map<String, Object> doSignRecipeExt(Recipe recipe, List<Recipedetail> details) {

        Map<String, Object> rMap = doSignRecipe(recipe, details);
        //获取处方签名结果
        Boolean result = Boolean.parseBoolean(rMap.get("signResult").toString());
        if(result){
            //非可使用省医保的处方立即发送处方卡片，使用省医保的处方需要在药师审核通过后显示
           if(!recipe.canMedicalPay()){
               sendRecipeTagToPatient(recipe,details,rMap);
           }
        }

        logger.info("doSignRecipeExt execute ok! rMap:"+JSONUtils.toString(rMap));
        return rMap;
    }

    /**
     * 处方二次签名
     * @param recipe
     * @return
     */
    @RpcService
    public RecipeResultBean doSecondSignRecipe(Recipe recipe) {
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);

        Recipe dbRecipe = checkRecipeCommonInfo(recipe.getRecipeId(),resultBean);
        if(null == dbRecipe){
            logger.error("validateDrugs 平台无该处方对象. recipeId=[{}] error={}", recipe.getRecipeId(), JSONUtils.toString(resultBean));
            return resultBean;
        }

        Integer status = dbRecipe.getStatus();
        if (null == status || status != RecipeStatusConstant.CHECK_NOT_PASS_YS) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("该处方不是审核未通过状态");
            return resultBean;
        }

        Integer afterStatus = RecipeStatusConstant.CHECK_PASS_YS;
        if(!dbRecipe.canMedicalPay()) {
            RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
            boolean effective = orderDAO.isEffectiveOrder(dbRecipe.getOrderCode(), dbRecipe.getPayMode());
            if (!effective) {
                resultBean.setCode(RecipeResultBean.FAIL);
                resultBean.setMsg("该处方已失效");
                return resultBean;
            }
        }else{
            afterStatus = RecipeStatusConstant.CHECK_PASS;
        }

        recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(),afterStatus,
                ImmutableMap.of("supplementaryMemo", recipe.getSupplementaryMemo()));
        afterCheckPassYs(dbRecipe);
        try {
            //生成pdf并签名
            recipeService.generateRecipePdfAndSign(recipe.getRecipeId());
        } catch (Exception e) {
            logger.error("doSecondSignRecipe 签名失败. recipeId=[{}], error={}", recipe.getRecipeId(), e.getMessage());
        }

        logger.info("doSecondSignRecipe execute ok! ");
        return resultBean;
    }

    /**
     * 处方药师审核通过后处理
     * @param recipe
     * @return
     */
    @RpcService
    public RecipeResultBean afterCheckPassYs(Recipe recipe){
        if(null == recipe){
            return null;
        }
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        RemoteDrugEnterpriseService service = AppContextHolder.getBean("eh.remoteDrugService", RemoteDrugEnterpriseService.class);

        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Integer recipeId = recipe.getRecipeId();
        if(recipe.canMedicalPay()){
            //如果是可医保支付的单子，审核通过之后是变为待处理状态，需要用户支付完成才发往药企
            sendRecipeTagToPatient(recipe,detailDAO.findByRecipeId(recipeId),null);
            //向患者推送处方消息
            RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.CHECK_PASS);
        }else if(RecipeConstant.PAYMODE_COD.equals(recipe.getPayMode()) || RecipeConstant.PAYMODE_TFDS.equals(recipe.getPayMode())){
            //货到付款|药店取药 审核完成，往药企发送审核完成消息
            service.pushCheckResult(recipeId,1);
            Integer status = OrderStatusConstant.READY_SEND;
            //到店取药审核完成是带取药状态
            if(RecipeConstant.PAYMODE_TFDS.equals(recipe.getPayMode())){
                status = OrderStatusConstant.READY_GET_DRUG;
            }
            orderService.updateOrderInfo(recipe.getOrderCode(), ImmutableMap.of("status",status),resultBean);
            //发送患者审核完成消息
            RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.CHECK_PASS_YS);
        }else{
            service.pushSingleRecipeInfo(recipeId);
        }
        RecipeLogService.saveRecipeLog(recipe.getRecipeId(),recipe.getStatus(),recipe.getStatus(),"审核通过处理完成");
        return resultBean;
    }

    /**
     * 药师审核不通过后处理
     * @param recipe
     */
    public void afterCheckNotPassYs(Recipe recipe){
        if(null == recipe){
            return;
        }
        RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        //相应订单处理
        orderService.cancelOrderByRecipeId(recipe.getRecipeId(), OrderStatusConstant.CANCEL_NOT_PASS);
        //根据付款方式提示不同消息
        if (RecipeConstant.PAYMODE_ONLINE.equals(recipe.getPayMode()) && PayConstant.PAY_FLAG_PAY_SUCCESS == recipe.getPayFlag()) {
            //线上支付
            //微信退款
            wxPayRefundForRecipe(2,recipe.getRecipeId(),null);
            RecipeMsgService.batchSendMsg(recipe.getRecipeId(), RecipeStatusConstant.CHECK_NOT_PASSYS_PAYONLINE);
        } else if (RecipeConstant.PAYMODE_COD.equals(recipe.getPayMode()) || RecipeConstant.PAYMODE_TFDS.equals(recipe.getPayMode())) {
            //货到付款 | 药店取药
            RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.CHECK_NOT_PASSYS_REACHPAY);
        }
        //HIS消息发送
        //审核不通过 往his更新状态（已取消）
        hisService.recipeStatusUpdate(recipe.getRecipeId());
        RecipeLogService.saveRecipeLog(recipe.getRecipeId(),recipe.getStatus(),recipe.getStatus(),"审核不通过处理完成");
    }

    /**
     * 往咨询界面发送处方卡片
     * @param recipe
     * @param details
     * @param rMap
     */
    public void sendRecipeTagToPatient(Recipe recipe, List<Recipedetail> details, Map<String, Object> rMap){
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
        ConsultMessageService consultMessageService = AppContextHolder.getBean("consultMessageService",ConsultMessageService.class);

        if(StringUtils.isNotEmpty(recipe.getMpiid()) && null != recipe.getDoctor()){
            //处方的患者编号在咨询单里其实是就诊人编号，不是申请人编号
            List<String> requestMpiIds = consultDAO.findPendingConsultByMpiIdAndDoctor(recipe.getMpiid(),recipe.getDoctor());
            if(CollectionUtils.isNotEmpty(requestMpiIds)){
                //获取诊断疾病名称
                String diseaseName = recipe.getOrganDiseaseName();
                List<String> drugNames =  Lists.newArrayList();
                if(recipe.containTcmType()){
                    for(Recipedetail r : details){
                        drugNames.add(r.getDrugName()+" * "+BigDecimal.valueOf(r.getUseDose()).toBigInteger().toString()+r.getUseDoseUnit());
                    }
                }else{
                    //组装药品名称   药品名+商品名+规格
                    List<Integer> drugIds = Lists.newArrayList();
                    for(Recipedetail r : details){
                        drugIds.add(r.getDrugId());
                    }
                    List<DrugList> drugLists = drugListDAO.findByDrugIds(drugIds);
                    for(DrugList drugList : drugLists){
                        //判断非空
                        String drugName = StringUtils.isEmpty(drugList.getDrugName())? "" : drugList.getDrugName();
                        String saleName = StringUtils.isEmpty(drugList.getSaleName())? "" : drugList.getSaleName();
                        String drugSpec = StringUtils.isEmpty(drugList.getDrugSpec())? "" : drugList.getDrugSpec();

                        //数据库中saleName字段可能包含与drugName相同的字符串,增加判断条件，将这些相同的名字过滤掉
                        StringBuilder drugAndSale = new StringBuilder("");
                        if(StringUtils.isNotEmpty(saleName)){
                            String [] strArray = saleName.split("\\s+");
                            for(String saleName1 : strArray){
                                if(!saleName1.equals(drugName)){
                                    drugAndSale.append(saleName1+" ");
                                }
                            }
                        }
                        drugAndSale.append(drugName+" ");
                        //拼装
                        drugNames.add(drugAndSale+drugSpec);
                    }
                }

                RecipeTagMsgBean recipeTagMsg = new RecipeTagMsgBean();
                recipeTagMsg.setDiseaseName(diseaseName);
                recipeTagMsg.setDrugNames(drugNames);
                if(null != recipe.getRecipeId()){
                    recipeTagMsg.setRecipeId(recipe.getRecipeId());
                }

                for(String requestMpiId : requestMpiIds) {
                    //根据mpi，requestMode 获取当前咨询单consultId
                    Integer consultId = null;
                    List<Integer> consultIds = consultDAO.findApplyingConsultByRequestMpiAndDoctorId(requestMpiId, recipe.getDoctor(), ConsultConstant.CONSULT_TYPE_RECIPE);
                    if (CollectionUtils.isNotEmpty(consultIds)) {
                        consultId = consultIds.get(0);
                    }
                    if(consultId != null){
                        if(null != rMap && null == rMap.get("consultId")) {
                            rMap.put("consultId", consultId);
                        }
                        Consult consult = consultDAO.getById(consultId);
                        if(consult != null){
                            //判断咨询单状态是否为处理中
                            if(consult.getConsultStatus() == ConsultConstant.CONSULT_STATUS_HANDLING){
                                if(StringUtils.isEmpty(consult.getSessionID())){
                                    recipeTagMsg.setSessionID(null);
                                }else {
                                    recipeTagMsg.setSessionID(consult.getSessionID());
                                }
                                logger.info("doSignRecipeExt recipeTagMsg={}",JSONUtils.toString(recipeTagMsg));
                                //将消息存入数据库consult_msg，并发送环信消息
                                consultMessageService.handleRecipeMsg(consult,recipeTagMsg);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 医院处方审核 (当前为自动审核通过)
     * @param recipeId
     * @return HashMap<String,Object>
     */
    @RpcService
    @Deprecated
    public HashMap<String,Object> recipeAutoCheck(Integer recipeId){
        logger.info("recipeAutoCheck get in recipeId="+recipeId);
        HashMap<String,Object> map = new HashMap<>();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        Integer recipeStatus = recipe.getStatus();
        if( RecipeStatusConstant.UNCHECK==recipeStatus){
            int afterStatus = RecipeStatusConstant.CHECK_PASS;
            Map<String,Object> attrMap = new HashMap<>();
            attrMap.put("checkDate",DateTime.now().toDate());
            attrMap.put("checkOrgan",recipe.getClinicOrgan());
            attrMap.put("checker",0);
            attrMap.put("checkFailMemo","");
            recipeDAO.updateRecipeInfoByRecipeId(recipeId,afterStatus,attrMap);

            RecipeLogService.saveRecipeLog(recipeId,recipeStatus,afterStatus,"自动审核通过");

            map.put("code", SystemConstant.SUCCESS);
            map.put("msg","");

            recipe.setStatus(afterStatus);
            //审核失败的话需要发送信息
//            RecipeMsgService.batchSendMsg(recipe,RecipeStatusConstant.CHECK_NOT_PASS);
        }else{
            map.put("code", SystemConstant.FAIL);
            map.put("msg","处方单不是待审核状态，不能进行自动审核");
        }

        //TODO 医院审核系统的对接

        return map;
    }

    /**
     * 状态文字提示（医生端）
     * @param status
     * @param recipe
     * @param effective
     * @return
     */
    public Map<String, String> getTipsByStatus(int status, Recipe recipe, boolean effective){
        String cancelReason = "";
        String tips = "";
        String listTips = "";
        switch (status){
            case RecipeStatusConstant.CHECK_NOT_PASS:
                tips = "审核未通过";
                break;
            case RecipeStatusConstant.UNSIGN:
                tips = "未签名";
                break;
            case RecipeStatusConstant.UNCHECK:
                tips = "待审核";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                tips = "待处理";
                break;
            case RecipeStatusConstant.REVOKE:
                tips = "已取消";
                cancelReason = "由于您已撤销，该处方单已失效";
                break;
            case RecipeStatusConstant.HAVE_PAY:
                tips = "待取药";
                break;
            case RecipeStatusConstant.IN_SEND:
                tips = "配送中";
                break;
            case RecipeStatusConstant.WAIT_SEND:
                tips = "待配送";
                break;
            case RecipeStatusConstant.FINISH:
                tips = "已完成";
                break;
            case RecipeStatusConstant.CHECK_PASS_YS:
                if(StringUtils.isNotEmpty(recipe.getSupplementaryMemo())){
                    tips = "医生再次确认处方";
                }else {
                    tips = "审核通过";
                }
                listTips = "审核通过";
                break;
            case RecipeStatusConstant.READY_CHECK_YS:
                tips = "待审核";
                break;
            case RecipeStatusConstant.HIS_FAIL:
                tips = "已取消";
                cancelReason = "可能由于医院接口异常，处方单已取消，请稍后重试！";
                break;
            case RecipeStatusConstant.NO_DRUG:
                tips = "已取消";
                cancelReason = "由于患者未及时取药，该处方单已失效";
                break;
            case RecipeStatusConstant.NO_PAY:
            case RecipeStatusConstant.NO_OPERATOR:
                tips = "已取消";
                cancelReason = "由于患者未及时支付，该处方单已取消。";
                break;
            case RecipeStatusConstant.CHECK_NOT_PASS_YS:
                if(recipe.canMedicalPay()){
                    tips = "审核未通过";
                }else {
                    if (effective) {
                        tips = "审核未通过";
                    } else {
                        tips = "已取消";
                    }
                }
                break;
            case RecipeStatusConstant.CHECKING_HOS:
                tips = "医院确认中";
                break;
            default:tips = "未知状态"+status;
        }
        if(StringUtils.isEmpty(listTips)){
            listTips = tips;
        }
        Map<String, String> map = new HashMap<>();
        map.put("tips", tips);
        map.put("listTips", listTips);
        map.put("cancelReason", cancelReason);
        return map;
    }

    /**
     * 状态文字提示（患者端）
     * @param recipe
     * @return
     */
    public String getTipsByStatusForPatient(Recipe recipe, RecipeOrder order){
        Integer status = recipe.getStatus();
        Integer payMode = recipe.getPayMode();
        Integer payFlag = recipe.getPayFlag();
        Integer giveMode = recipe.getGiveMode();
        String orderCode = recipe.getOrderCode();
        String tips = "";
        switch (status){
            case RecipeStatusConstant.FINISH:
                tips = "处方单已完结.";
                break;
            case RecipeStatusConstant.HAVE_PAY:
                if(RecipeConstant.GIVEMODE_SEND_TO_HOME.equals(giveMode)){
                    //配送到家
                    tips = "您已支付，药品将尽快为您配送.";
                }else if(RecipeConstant.GIVEMODE_TO_HOS.equals(giveMode)){
                    //医院取药
                    tips = "您已支付，请尽快到院取药.";
                 }
                break;
            case RecipeStatusConstant.NO_OPERATOR:
            case RecipeStatusConstant.NO_PAY:
                tips = "由于您未及时缴费，该处方单已失效，请联系医生.";
                break;
            case RecipeStatusConstant.NO_DRUG:
                tips = "由于您未及时取药，该处方单已失效.";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                if(null == payMode || null == giveMode){
                    tips = "";
                }else if(RecipeConstant.PAYMODE_TO_HOS.equals(payMode) && 0 == payFlag) {
                    tips = "您已选择到院支付，请及时缴费并取药.";
                }

                if(StringUtils.isNotEmpty(orderCode) && null != order && 1 == order.getEffective()){
                    tips = "您已选择配送到家，请及时支付并取药.";
                }

                break;
            case RecipeStatusConstant.READY_CHECK_YS:
                if(RecipeConstant.PAYMODE_ONLINE.equals(payMode)){
                    //在线支付
                    tips = "您已支付，药品将尽快为您配送.";
                }else if(RecipeConstant.PAYMODE_COD.equals(payMode) || RecipeConstant.PAYMODE_TFDS.equals(payMode)){
                    tips = "处方正在审核中.";
                }
                break;
            case RecipeStatusConstant.WAIT_SEND:
            case RecipeStatusConstant.CHECK_PASS_YS:
                if(RecipeConstant.PAYMODE_ONLINE.equals(payMode)){
                    //在线支付
                    tips = "您已支付，药品将尽快为您配送.";
                }else if(RecipeConstant.PAYMODE_COD.equals(payMode)){
                    //货到付款
                    tips = "药品将尽快为您配送.";
                }else if(RecipeConstant.PAYMODE_TFDS.equals(payMode)){
                    tips = "请尽快前往药店取药.";
                }
                break;
            case RecipeStatusConstant.IN_SEND:
                if(RecipeConstant.PAYMODE_ONLINE.equals(payMode)){
                    //在线支付
                    tips = "您已支付，药品正在配送中，请保持手机畅通.";
                }else if(RecipeConstant.PAYMODE_COD.equals(payMode)){
                    //货到付款
                    tips = "药品正在配送中，请保持手机畅通.";
                }
                break;
            case RecipeStatusConstant.CHECK_NOT_PASS_YS:
                tips = "由于未通过审核，该处方单已失效，请联系医生.";
                if(StringUtils.isNotEmpty(orderCode) && null != order && 1 == order.getEffective()){
                    if(RecipeConstant.PAYMODE_ONLINE.equals(payMode)){
                        //在线支付
                        tips = "您已支付，药品将尽快为您配送.";
                    }else if(RecipeConstant.PAYMODE_COD.equals(payMode) || RecipeConstant.PAYMODE_TFDS.equals(payMode)){
                        tips = "处方正在审核中.";
                    }
                }

                break;
            case RecipeStatusConstant.REVOKE:
                tips = "由于医生已撤销，该处方单已失效，请联系医生.";
                break;
            default:tips = "未知状态"+status;

        }
        return tips;
    }


    /**
     * 处方单详情服务
     * @param recipeId 处方ID
     * @return HashMap<String, Object>
     */
    @RpcService
    public HashMap<String, Object> findRecipeAndDetailById(int recipeId) {
        return getRecipeAndDetailByIdImpl(recipeId,true);
    }

    /**
     * 处方撤销方法(供医生端使用)
     * @param recipeId 处方Id
     * @return Map<String, Object>
     *     撤销成功返回 {"result":true,"msg":"处方撤销成功"}
     *     撤销失败返回 {"result":false,"msg":"失败原因"}
     */
    @RpcService
    public Map<String, Object> cancelRecipe(Integer recipeId){
        return cancelRecipeImpl(recipeId, 0, "", "");
    }

    /**
     * 处方撤销方法(供运营平台使用)
     * @param recipeId
     * @param name 操作人员姓名
     * @param message 处方撤销原因
     * @return
     */
    @RpcService
    public Map<String, Object> cancelRecipeForOperator(Integer recipeId, String name, String message){
        return cancelRecipeImpl(recipeId, 1, name, message);
    }

    /**
     * 处方撤销接口区分医生端和运营平台
     * @param recipeId
     * @param flag
     * @return
     */
    public Map<String, Object> cancelRecipeImpl(Integer recipeId, Integer flag, String name, String message){
        logger.info("cancelRecipe [recipeId：" + recipeId+"]");
        //获取订单
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder order = orderDAO.getOrderByRecipeId(recipeId);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);

        //获取处方单
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        Map<String,Object> rMap = new HashMap<>();
        Boolean result = false;
        //医生撤销提醒msg，供医生app端使用
        String msg = "";
        if(null == recipe){
            msg = "该处方单不存在";
            rMap.put("result", result);
            rMap.put("msg", msg);
            return rMap;
        }
        //获取撤销前处方单状态
        Integer beforeStatus = recipe.getStatus();
        //不能撤销的情况:1 患者已支付 2 药师已审核(不管是否通过)
        if(Integer.valueOf(RecipeStatusConstant.REVOKE).equals(recipe.getStatus())){
            msg = "该处方单已撤销，不能进行撤销操作";
        }
        if(!(recipe.getChecker() == null)){
            msg = "该处方单已经过审核，不能进行撤销操作";
        }
        if(recipe.getStatus() == RecipeStatusConstant.UNSIGN){
            msg = "暂存的处方单不能进行撤销";
        }
        if(Integer.valueOf(1).equals(recipe.getPayFlag())){
            msg = "该处方单用户已支付，不能进行撤销操作";
        }
        if(recipe.getStatus() == RecipeStatusConstant.HIS_FAIL
                || recipe.getStatus() == RecipeStatusConstant.NO_DRUG
                || recipe.getStatus() == RecipeStatusConstant.NO_PAY
                || recipe.getStatus() == RecipeStatusConstant.NO_OPERATOR){
            msg = "该处方单已取消，不能进行撤销操作";
        }
        if(Integer.valueOf(1).equals(recipe.getChooseFlag())){
            msg = "患者已选择购药方式，不能进行撤销操作";
        }
        if(1 == flag){
            if(StringUtils.isEmpty(name)){
                msg = "姓名不能为空";
            }
            if(StringUtils.isEmpty(message)){
                msg = "撤销原因不能为空";
            }
        }
        //处方撤销信息，供记录日志使用
        StringBuilder memo = new StringBuilder(msg);
        if(StringUtils.isEmpty(msg)){
            Map<String, Integer> changeAttr = Maps.newHashMap();
            if(!recipe.canMedicalPay()){
                changeAttr.put("chooseFlag", 1);
            }
            result = recipeDAO.updateRecipeInfoByRecipeId(recipeId, RecipeStatusConstant.REVOKE, changeAttr);
            orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO);
            if(result){
                msg = "处方撤销成功";
                //向患者推送处方撤销消息
                if(RecipeStatusConstant.READY_CHECK_YS == recipe.getStatus() && recipe.canMedicalPay()){
                    //医保的处方待审核时患者无法看到处方，不发送撤销消息提示
                } else {
                    RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.REVOKE);
                }
                memo.append(msg);
                //HIS消息发送
                boolean succFlag = hisService.recipeStatusUpdate(recipeId);
                if(succFlag) {
                    memo.append(",HIS推送成功");
                }else{
                    memo.append(",HIS推送失败");
                }
                //处方撤销后将状态设为已撤销，供记录日志使用
                recipe.setStatus(RecipeStatusConstant.REVOKE);
            } else {
                msg = "未知原因，处方撤销失败";
                memo.append(","+msg);
            }
        }

        if(1 == flag){
            memo.append("。"+"撤销人："+name+",撤销原因："+message);
        }
        //记录日志
        RecipeLogService.saveRecipeLog(recipeId, beforeStatus, recipe.getStatus(), memo.toString());
        rMap.put("result",result);
        rMap.put("msg",msg);
        logger.info("cancelRecipe execute ok! rMap:"+JSONUtils.toString(rMap));
        return rMap;
    }

    /**
     *  定时任务:同步HIS医院药品信息
     */
    @RpcService
    public void drugInfoSynTask(Integer organId){
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);
        List<Integer> organIds = new ArrayList<>();
        if(null == organId){
            //查询 base_organconfig 表配置需要同步的机构
            OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
            organIds = organConfigDAO.findOrganIdsByEnableDrugSync();
        }
        else {
            organIds.add(organId);
        }

        List<String> unuseDrugs = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(organIds)) {
            for (int _organId : organIds) {
                int startIndex = 0;//查询起始下标
                boolean finishFlag = true;
                do{
                    unuseDrugs.clear();
                    List<DrugInfo> drugInfoList = hisService.getDrugInfoFromHis(_organId,false,startIndex);
                    if (null != drugInfoList) {
                        //是否有效标志 1-有效 0-无效
                        for (DrugInfo drug : drugInfoList) {
                            if ("0".equals(drug.getUseflag())) {
                                unuseDrugs.add(drug.getDrcode());
                            }
                        }

                        if (CollectionUtils.isNotEmpty(unuseDrugs)) {
                            organDrugListDAO.updateStatusByOrganDrugCode(unuseDrugs, 0);
                        }
                        startIndex += 100;
                        logger.info("drugInfoSynTask organId=[{}] 同步完成. 关闭药品数量[{}], drugCode={}", _organId, unuseDrugs.size(), JSONUtils.toString(unuseDrugs));
                    }else{
                        logger.error("drugInfoSynTask organId=[{}] 药品信息更新结束.", _organId);
                        finishFlag = false;
                    }
                }while (finishFlag);
            }
        } else {
            logger.info("drugInfoSynTask organIds is empty.");
        }
    }

    /**
     * 定时任务:定时取消处方单
     */
    @RpcService
    public void cancelRecipeTask(){
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
        RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);

        List<Integer> statusList = Arrays.asList(RecipeStatusConstant.NO_PAY, RecipeStatusConstant.NO_OPERATOR);
        StringBuilder memo = new StringBuilder();
        RecipeOrder order;
        //设置查询时间段
        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(RECIPE_EXPIRED_DAYS),DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(
                Integer.parseInt(ParamUtils.getParam(ParameterConstant.KEY_RECIPE_CANCEL_DAYS,RECIPE_EXPIRED_SEARCH_DAYS.toString()))),DateConversion.DEFAULT_DATE_TIME);
        for(Integer status : statusList){
            List<Recipe> recipeList = recipeDAO.getRecipeListForCancelRecipe(status,startDt,endDt);
            if(null != recipeList && !recipeList.isEmpty()) {
                logger.info("cancelRecipeTask 状态："+status+"*需要取消的处方单数量："+recipeList.size());
                for(Recipe recipe : recipeList){
                    memo.delete(0,memo.length());
                    int recipeId = recipe.getRecipeId();
                    //相应订单处理
                    order = orderDAO.getOrderByRecipeId(recipeId);
                    orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO);

                    //变更处方状态
                    recipeDAO.updateRecipeInfoByRecipeId(recipeId,status, ImmutableMap.of("chooseFlag",1));
                    RecipeMsgService.batchSendMsg(recipe,status);
                    if(RecipeStatusConstant.NO_PAY == status){
                        memo.append("已取消,超过3天未支付");
                    }else if(RecipeStatusConstant.NO_OPERATOR == status){
                        memo.append("已取消,超过3天未操作");
                    }else{
                        memo.append("未知状态:"+status);
                    }
                    //HIS消息发送
                    boolean succFlag = hisService.recipeStatusUpdate(recipeId);
                    if(succFlag) {
                        memo.append(",HIS推送成功");
                    }else{
                        memo.append(",HIS推送失败");
                    }
                    //保存处方状态变更日志
                    RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.CHECK_PASS,status,memo.toString());
                }
            }
        }

    }

    /**
     * 定时任务:处方单失效提醒
     * 根据处方单失效时间：
     * 如果医生签名确认时间是：9：00-24：00  ，在处方单失效前一天的晚上6点推送；
     * 如果医生签名确认时间是：00-8：59 ，在处方单失效前两天的晚上6点推送；
     *
     */
    @RpcService
    public void remindRecipeTask(){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        //处方失效前一天提醒，但是需要根据签名时间进行推送，故查数据时选择超过一天的数据就可以
        List<Integer> statusList = Arrays.asList(RecipeStatusConstant.PATIENT_NO_OPERATOR, RecipeStatusConstant.PATIENT_NO_PAY,
                RecipeStatusConstant.PATIENT_NODRUG_REMIND);
        Date now = DateTime.now().toDate();
        //设置查询时间段
        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(1),DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(RECIPE_EXPIRED_DAYS),DateConversion.DEFAULT_DATE_TIME);
        for(Integer status : statusList) {
            List<Recipe> recipeList = recipeDAO.getRecipeListForRemind(status,startDt,endDt);
            //筛选数据
            List<Integer> recipeIds = new ArrayList<>(10);
            for(Recipe recipe : recipeList){
                Date signDate = recipe.getSignDate();
                if(null != signDate) {
                   int hour = DateConversion.getHour(signDate);
                    //签名时间在 00-8：59，则进行提醒
                    if(hour>=0 && hour<9){
                        recipeIds.add(recipe.getRecipeId());
                    }else{
                        //如果是在9-24开的药，则判断签名时间与当前时间在2天后
                        int days = DateConversion.getDaysBetween(signDate,now);
                        if(days>=2){
                            recipeIds.add(recipe.getRecipeId());
                        }
                    }
                }
            }

            logger.info("remindRecipeTask 需要提醒用户的处方数量："+recipeIds.size()+",status:"+status);
            if(null != recipeIds && !recipeIds.isEmpty()) {
                //批量更新 处方失效前提醒标志位
                recipeDAO.updateRemindFlagByRecipeId(recipeIds);
                //批量信息推送
                RecipeMsgService.batchSendMsg(recipeIds, status);
            }
        }
    }

    /**
     * 定时任务: 查询过期的药师审核不通过，需要医生二次确认的处方
     * 查询规则: 药师审核不通过时间点的 2天前-1月前这段时间内，医生未处理的处方单
     */
    @RpcService
    public void afterCheckNotPassYsTask()
    {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        String endDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(2),DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(RECIPE_EXPIRED_SECTION),DateConversion.DEFAULT_DATE_TIME);
        //根据条件查询出来的数据都是需要主动退款的
        List<Recipe> list = recipeDAO.findCheckNotPassNeedDealList(startDt,endDt);
        logger.info("do afterCheckNotPassYsTask recipeList.size = " + list.size());
        for (Recipe recipe : list){
            afterCheckNotPassYs(recipe);
        }
    }

    /**
     *  定时任务:从HIS中获取处方单状态
     *  选择了到医院取药方法，需要定时从HIS上获取该处方状态数据
     */
    @RpcService
    public void getRecipeStatusFromHis(){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        //设置查询时间段
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(RECIPE_EXPIRED_DAYS),DateConversion.DEFAULT_DATE_TIME);
        String endDt = DateConversion.getDateFormatter(DateTime.now().toDate(),DateConversion.DEFAULT_DATE_TIME);
        //key为organId,value为recipdeCode集合
        Map<Integer,List<String>> map = new HashMap<>();
        List<Recipe> list = recipeDAO.getRecipeStatusFromHis(startDt,endDt);
        logger.info("getRecipeStatusFromHis 需要从HIS中获取处方单状态处方数量："+JSONUtils.toString(list.size()));

        assembleQueryStatusFromHis(list,map);
        List<UpdateRecipeStatusFromHisCallable> callables = new ArrayList<>(0);
        for(Integer organId : map.keySet()){
            callables.add(new UpdateRecipeStatusFromHisCallable(map.get(organId), organId));
        }

        if(CollectionUtils.isNotEmpty(callables)){
            try {
                new RecipeBusiThreadPool(callables).execute();
            } catch (InterruptedException e) {
                logger.error("getRecipeStatusFromHis 线程池异常");
            }
        }
    }

    /**
     * 定时任务:更新药企token
     *
     */
    @RpcService
    public void updateDrugsEnterpriseToken(){
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        List<Integer> list = drugsEnterpriseDAO.findNeedUpdateIds();
        logger.info("updateDrugsEnterpriseToken 此次更新药企数量:"+((null==list)?0:list.size()));
        RemoteDrugEnterpriseService remoteDrugService = AppContextHolder.getBean("eh.remoteDrugService", RemoteDrugEnterpriseService.class);
        //非空已在方法内部判断
        remoteDrugService.updateAccessToken(list);
    }


    /************************************************患者类接口 START*************************************************/

    /**
     * 健康端获取处方详情
     * @param recipeId 处方ID
     * @return HashMap<String, Object>
     */
    @RpcService
    public Map<String, Object> getPatientRecipeById(int recipeId) {
        return getRecipeAndDetailByIdImpl(recipeId,false);
    }


    /**
     * 获取该处方的购药方式(用于判断这个处方是不是被处理)
     * @param recipeId
     * @param flag 1:表示处方单详情页从到院取药转直接支付的情况判断
     * @return  0未处理  1线上支付 2货到付款 3到院支付
     */
    @RpcService
    public int getRecipePayMode(int recipeId,int flag){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe dbRecipe = recipeDAO.getByRecipeId(recipeId);
        if(null == dbRecipe){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipe not exist!");
        }

        //进行判断该处方单是否已处理，已处理则返回具体购药方式
        if(1 == dbRecipe.getChooseFlag()){
            //如果之前选择的是到院取药且未支付 则可以进行转在线支付的方式
            if(1 == flag && 2 == dbRecipe.getGiveMode() && 3 == dbRecipe.getPayMode()
                    && 0 == dbRecipe.getPayFlag()){
                return 0;
            }
            return dbRecipe.getPayMode();
        }else{
            return 0;
        }

    }

    /**
     * 判断该处方是否支持医院取药
     * @param clinicOrgan  开药机构
     * @return boolean
     */
    @Deprecated
    @RpcService
    public boolean supportTakeMedicine(Integer recipeId, Integer clinicOrgan){
        if(null == recipeId){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipeId is required!");
        }

        if(null == clinicOrgan){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "clinicOrgan is required!");
        }
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        boolean succFlag = false;
        Integer flag = organDAO.getTakeMedicineFlagById(clinicOrgan);
        //是否支持医院取药 0：不支持，1：支持
        if(null != flag && 1 == flag){
            String backInfo = searchRecipeStatusFromHis(recipeId,1);
            if(StringUtils.isNotEmpty(backInfo)){
                succFlag = false;
                throw new DAOException(ErrorCode.SERVICE_ERROR, backInfo);
            }
        }else{
            logger.error("supportTakeMedicine organ["+clinicOrgan+"] not support take medicine!");
        }

        return succFlag;
    }

    /**
     * 扩展配送校验方法
     * @param recipeId
     * @param clinicOrgan
     * @param selectDepId 可能之前选定了某个药企
     * @param payMode
     * @return
     */
    public Integer supportDistributionExt(Integer recipeId, Integer clinicOrgan, Integer selectDepId, Integer payMode){
        Integer backDepId = null;
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Integer flag = organDAO.getTakeMedicineFlagById(clinicOrgan);
        //是否支持医院取药 0：不支持，1：支持
        //该医院不对接HIS的话，则不需要进行该校验
        if(null != flag && 1 == flag) {
            String backInfo = searchRecipeStatusFromHis(recipeId,2);
            if(StringUtils.isNotEmpty(backInfo)){
                throw new DAOException(ErrorCode.SERVICE_ERROR, backInfo);
            }
        }

        //进行药企配送分配，检测药企是否有能力进行该处方的配送
        Integer depId = getDrugsEpsIdByOrganId(recipeId, payMode, selectDepId);
        if (!Integer.valueOf(-1).equals(depId)) {
            if(null != selectDepId && !selectDepId.equals(depId)){
                //说明不是同一家药企配送，无法配送
            }else{
                backDepId = depId;
            }
        }

        return backDepId;
    }

    @RpcService
    public boolean applyMedicalInsurancePayForRecipe(Integer recipeId){
        logger.info("applyMedicalInsurancePayForRecipe start, params: recipeId[{}]", recipeId);
        try{
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            Recipe recipe = recipeDAO.getByRecipeId(recipeId);
            Patient patient = DAOFactory.getDAO(PatientDAO.class).get(recipe.getMpiid());
            String outTradeNo = BusTypeEnum.RECIPE.getApplyNo();
            HisServiceConfig hisServiceConfig = DAOFactory.getDAO(HisServiceConfigDAO.class).getByOrganId(recipe.getClinicOrgan());
            Map<String, Object> httpRequestParam = Maps.newHashMap();
            httpRequestParam.put("mrn", recipe.getPatientID());
            httpRequestParam.put("id_card_no", patient.getIdcard());
            httpRequestParam.put("cfhs", new String[]{recipe.getRecipeCode()});
            httpRequestParam.put("hospital_code", hisServiceConfig.getYkfPlatHospitalCode());
            httpRequestParam.put("partner_trade_no", outTradeNo);
            httpRequestParam.put("callback_url", PayUtil.getNotify_domain() + PayController.DA_BAI_ASYNC_NOTIFY_URL);
            httpRequestParam.put("need_app_notify", "1");
            httpRequestParam.put("is_show_result", "1");
            DaBaiMedicalInsuranceService medicalInsuranceService = AppContextHolder.getBean("medicalInsuranceService", DaBaiMedicalInsuranceService.class);
            DaBaiMedicalInsuranceService.PayResult pr = medicalInsuranceService.applyDaBaiPay(httpRequestParam);

            recipe.setOutTradeNo(outTradeNo);
            if("0".equals(pr.getCode())) {
                String tradeNo = pr.getData().getTrade_no();
                recipe.setTradeNo(tradeNo);
            }
            recipeDAO.update(recipe);
            return true;
        }catch (Exception e){
            logger.error("applyMedicalInsurancePayForRecipe error, recipeId[{}], errorMessage[{}], stackTrace[{}]", recipeId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
//            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
            return false;
        }

    }

    private String getWxAppIdForRecipeFromOps(Integer recipeId, Integer busOrgan){
        try {
            SimpleWxAccount wxAccount = CurrentUserInfo.getSimpleWxAccount();
            if(wxAccount==null){
                return "appPay_"; // app支付时，躲过校验
            }
            PayWayService payWayService = AppContextHolder.getBean("payWayService", PayWayService.class);
            OpReturnPayParams opReturnPayParams = payWayService.fetchPayTargetInfo(wxAccount.getAppId(), busOrgan, PayWayEnum.WEIXIN_WAP.getCode(), BusTypeEnum.RECIPE.getCode(), null);
            logger.info(LocalStringUtil.format("[{}] getWxAppIdForRecipeFromOps with params: busId[{}]; result：opReturnPayParams[{}]", this.getClass().getSimpleName(), recipeId, JSONObject.toJSONString(opReturnPayParams)));
            return opReturnPayParams.getTargetAppId();
        }catch (Exception e) {
            Object obj = ContextUtils.get(Context.RPC_INVOKE_HEADERS);
            logger.error(LocalStringUtil.format("currentRPCInvokeHeaders[{}] getWxAppIdForRecipeFromOps exception with params: busId[{}]; errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(obj), recipeId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        return null;
    }

    /**
     * 根据开方机构分配药企进行配送并入库 （获取某一购药方式最合适的供应商）
     * @param recipeId
     * @param payMode
     * @param selectDepId
     * @return
     */
    public Integer getDrugsEpsIdByOrganId(Integer recipeId, Integer payMode, Integer selectDepId){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        Integer depId = -1;
        if(null != recipe) {
            List<DrugsEnterprise> list = findSupportDepList(Arrays.asList(recipeId),recipe.getClinicOrgan(),
                    payMode,true,selectDepId);
            if(CollectionUtils.isNotEmpty(list)){
                depId = list.get(0).getId();
            }
        }else{
            logger.error("getDrugsEpsIdByOrganId 处方["+recipeId+"]不存在！");
        }

        return depId;
    }

    /**
     * 查询符合条件的药企供应商
     * @param recipeId 处方ID
     * @param organId 开方机构
     * @param payMode  购药方式，为NULL时表示查询所有药企
     * @param sigle true:表示只返回第一个合适的药企，false:表示符合条件的所有药企
     * @param selectDepId  指定某个药企
     * @return
     */
    public List<DrugsEnterprise> findSupportDepList(List<Integer> recipeIdList, int organId, Integer payMode, boolean sigle,
                                                    Integer selectDepId){
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        RemoteDrugEnterpriseService remoteDrugService = AppContextHolder.getBean("eh.remoteDrugService", RemoteDrugEnterpriseService.class);

        List<DrugsEnterprise> backList = new ArrayList<>(5);
        //线上支付能力判断
        boolean onlinePay = true;
        if (null == payMode || RecipeConstant.PAYMODE_ONLINE.equals(payMode)
                || RecipeConstant.PAYMODE_MEDICAL_INSURANCE.equals(payMode)) {
            //只支持线上付款后配送，则需要判断医院是否有付款帐号
            HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
            String wxAccount = getWxAppIdForRecipeFromOps(null, organId);
            //需要判断医院HIS是否开通
            int hisStatus = hisServiceConfigDAO.isHisEnable(organId) ? 1 : 0;
            if (!isCanSupportPayOnline(wxAccount, hisStatus)) {
                logger.error("findSupportDepList 机构[" + organId + "]不支持线上支付！");
                //这里判断payMode=null的情况，是为了筛选供应商提供依据
                if(null == payMode){
                    onlinePay = false;
                }else {
                    return backList;
                }
            }
        }

        for(Integer recipeId : recipeIdList) {
            List<DrugsEnterprise> subDepList = new ArrayList<>(5);
            //检测配送的药品是否按照完整的包装开的药，如 1*20支，开了10支，则不进行选择，数据库里主要是useTotalDose不为小数
            List<Double> totalDoses = recipeDetailDAO.findUseTotalDoseByRecipeId(recipeId);
            if (null != totalDoses && !totalDoses.isEmpty()) {
                for (Double totalDose : totalDoses) {
                    if (null != totalDose) {
                        int itotalDose = (int) totalDose.doubleValue();
                        if (itotalDose != totalDose.doubleValue()) {
                            logger.error("findSupportDepList 不支持非完整包装的计量药品配送. recipeId=[{}], totalDose=[{}]", recipeId, totalDose.doubleValue());
                            break;
                        }
                    } else {
                        logger.error("findSupportDepList 药品计量为null. recipeId=[{}]", recipeId);
                        break;
                    }
                }
            } else {
                logger.error("findSupportDepList 所有药品计量为null. recipeId=[{}]", recipeId);
                break;
            }

            List<Integer> drugIds = recipeDetailDAO.findDrugIdByRecipeId(recipeId);
            if (CollectionUtils.isEmpty(drugIds)) {
                logger.error("findSupportDepList 处方[{}]没有任何药品！", recipeId);
                break;
            }

            List<DrugsEnterprise> drugsEnterpriseList = new ArrayList<>(0);
            if(null != selectDepId){
                DrugsEnterprise drugsEnterprise = drugsEnterpriseDAO.getById(selectDepId);
                if(null != drugsEnterprise){
                    drugsEnterpriseList.add(drugsEnterprise);
                }
            }else {
                if (null != payMode) {
                    List<Integer> payModeSupport = getDepSupportMode(payMode);
                    if (CollectionUtils.isEmpty(payModeSupport)) {
                        logger.error("findSupportDepList 处方[{}]无法匹配配送方式. payMode=[{}]", recipeId, payMode);
                        break;
                    }

                    //筛选出来的数据已经去掉不支持任何方式配送的药企
                    drugsEnterpriseList = drugsEnterpriseDAO.findByOrganIdAndPayModeSupport(organId, payModeSupport);
                    if (CollectionUtils.isEmpty(drugsEnterpriseList)) {
                        logger.error("findSupportDepList 处方[{}]没有任何药企可以进行配送！", recipeId);
                        break;
                    }
                } else {
                    drugsEnterpriseList = drugsEnterpriseDAO.findByOrganId(organId);
                }
            }

            for (DrugsEnterprise dep : drugsEnterpriseList) {
                //根据药企是否能满足所有配送的药品优先
                Integer _depId = dep.getId();
                //不支持在线支付跳过该药企
                if(Integer.valueOf(1).equals(dep.getPayModeSupport()) && !onlinePay){
                     continue;
                }
                //药品匹配成功标识
                boolean succFlag = false;
                Long count = saleDrugListDAO.getCountByOrganIdAndDrugIds(_depId, drugIds);
                if (null != count && count > 0) {
                    if (count == drugIds.size()) {
                        succFlag = true;
                    }
                }

                if (!succFlag) {
                    logger.error("findSupportDepList 存在不支持配送药品. 处方ID=[{}], 药企ID=[{}], 药企名称=[{}], drugIds={}",
                            recipeId, _depId, dep.getName(), JSONUtils.toString(drugIds));
                    continue;
                } else {
                    //通过查询该药企库存，最终确定能否配送
                    succFlag = remoteDrugService.scanStock(recipeId, dep);
                    if (succFlag) {
                        subDepList.add(dep);
                        //只需要查询单供应商就返回
                        if (sigle) {
                            break;
                        }
                    } else {
                        logger.error("findSupportDepList 药企库存查询返回药品无库存. 处方ID=[{}], 药企ID=[{}], 药企名称=[{}]",
                                recipeId, _depId, dep.getName());
                    }
                }
            }

            if(CollectionUtils.isEmpty(subDepList)){
                logger.error("findSupportDepList 该处方无法配送. recipeId=[{}]", recipeId);
                backList.clear();
                break;
            }else{
                //药企求一个交集
                if(CollectionUtils.isEmpty(backList)){
                    backList.addAll(subDepList);
                }else{
                    //交集需要处理
                    backList.retainAll(subDepList);
                }
            }
        }

        return backList;
    }

    public static void main(String[] args) {
        List<Integer> a = new ArrayList<>(Arrays.asList(1,2,5));
        List<Integer> b = new ArrayList<>(Arrays.asList(1,2,4));

        boolean bl = a.retainAll(b);
        System.out.println(bl);
        System.out.println(JSONUtils.toString(a));
        System.out.println(JSONUtils.toString(b));
    }

    /**
     * 配送模式选择
     * @param payMode
     * @return
     */
    private List<Integer> getDepSupportMode(Integer payMode){
        //具体见DrugsEnterprise的payModeSupport字段
        //配送模式支持 0:不支持 1:线上付款 2:货到付款 3:药店取药 8:货到付款和药店取药 9:都支持
        List<Integer> supportMode = new ArrayList<>();
        if(null == payMode){
            return supportMode;
        }

        if(RecipeConstant.PAYMODE_ONLINE.equals(payMode)){
            supportMode.add(RecipeConstant.DEP_SUPPORT_ONLINE);
        }else if(RecipeConstant.PAYMODE_COD.equals(payMode)){
            supportMode.add(RecipeConstant.DEP_SUPPORT_COD);
            supportMode.add(RecipeConstant.DEP_SUPPORT_COD_TFDS);
        }else if(RecipeConstant.PAYMODE_TFDS.equals(payMode)){
            supportMode.add(RecipeConstant.DEP_SUPPORT_TFDS);
            supportMode.add(RecipeConstant.DEP_SUPPORT_COD_TFDS);
        }else if(RecipeConstant.PAYMODE_MEDICAL_INSURANCE.equals(payMode)){
            //医保选用线上支付配送方式
            supportMode.add(RecipeConstant.DEP_SUPPORT_ONLINE);
        }

        if(CollectionUtils.isNotEmpty(supportMode)){
            supportMode.add(RecipeConstant.DEP_SUPPORT_ALL);
        }

        return supportMode;
    }


    /**
     * 手动进行处方退款服务
     * @param recipeId
     * @param operName
     * @param reason
     */
    @RpcService
    public void manualRefundForRecipe(int recipeId, String operName, String reason){
        wxPayRefundForRecipe(4,recipeId,"操作人:["+((StringUtils.isEmpty(operName))?"":operName)+"],理由:["+
                ((StringUtils.isEmpty(reason))?"":reason)+"]");
    }

    /**
     * 退款方法
     * @param flag
     * @param recipeId
     */
    @RpcService
    public void wxPayRefundForRecipe(int flag, int recipeId, String log){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        int status = recipe.getStatus();

        String errorInfo = "退款-";
        switch (flag){
            case 1:
                errorInfo += "HIS线上支付返回：写入his失败";
                RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.PATIENT_HIS_FAIL);
                break;
            case 2:errorInfo += "药师审核不通过";break;
            case 3:errorInfo += "推送药企失败";
                RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.RECIPE_LOW_STOCKS);
                break;
            case 4:errorInfo += log;
                status = RecipeStatusConstant.REVOKE;
                break;
            default:errorInfo += "未知,flag="+flag;

        }

        RecipeLogService.saveRecipeLog(recipeId,recipe.getStatus(),status,errorInfo);

        //相应订单处理
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder order = orderDAO.getByOrderCode(recipe.getOrderCode());
        if(1 == flag){
            orderService.updateOrderInfo(order.getOrderCode(), ImmutableMap.of("status",OrderStatusConstant.READY_PAY), null);
        }else if(3 == flag){
            orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO);
        }else if(4 == flag){
            orderService.cancelOrder(order, OrderStatusConstant.CANCEL_AUTO);
            //处理处方单
            recipeDAO.updateRecipeInfoByRecipeId(recipeId, status, null);
        }

        try {
            //微信退款
            WxRefundExecutor executor = new WxRefundExecutor(order.getOrderId(), RecipeService.WX_RECIPE_BUSTYPE);
            executor.execute();
        } catch (Exception e) {
            logger.error("wxPayRefundForRecipe "+errorInfo+"*****微信退款异常！recipeId["+ recipeId+"],err["+e.getMessage()+"]");
        }

        if(2 == flag || 3 == flag || 4 == flag){
            //HIS消息发送
            RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);
            hisService.recipeRefund(recipeId);
        }

    }

    /**
     * 更新机构相应药品价格
     * @param organId 机构ID
     * @param priceMap 药品价格，key:药品id，value:价格
     */
    public void updateDrugPrice(Integer organId, Map<Integer,BigDecimal> priceMap){
       if(null != organId && null != priceMap && !priceMap.isEmpty()){
           OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

           for(Map.Entry<Integer, BigDecimal> entry : priceMap.entrySet()){
               if(null != entry.getKey() && null != entry.getValue()) {
                   organDrugListDAO.updateDrugPrice(organId, entry.getKey(), entry.getValue());
               }
           }
       }
    }

    /************************************************患者类接口 END***************************************************/


    /**
     * 处方列表服务
     *
     * @param doctorId
     *            开方医生
     * @param start
     *            分页开始位置
     * @param limit
     *            每页限制条数
     * @param mark
     *            标志 --0新处方1历史处方
     * @return  List
     */
    private List<HashMap<String, Object>> findRecipesAndPatientsByDoctor(
            final int doctorId, final int start, final int limit, final int mark) {
        if(0 == limit){
            return null;
        }

        List<Recipe> recipes;
        boolean hasUnsignRecipe = false; // 是否含有未签名的数据
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);

        if(0 == mark){
            recipes = new ArrayList<>(0);
            int endIndex = start+limit;
            //先查询未签名处方的数量
            int unsignCount = recipeDAO.getCountByDoctorIdAndStatus(doctorId,
                    Arrays.asList(RecipeStatusConstant.CHECK_NOT_PASS, RecipeStatusConstant.UNSIGN), ConditionOperator.IN, false);
            //查询未签名的处方数据
            if(unsignCount > start){
                hasUnsignRecipe = true;
                List<Recipe> unsignRecipes = recipeDAO.findByDoctorIdAndStatus(doctorId,
                        Arrays.asList(RecipeStatusConstant.CHECK_NOT_PASS, RecipeStatusConstant.UNSIGN), ConditionOperator.IN, false, start, limit, mark);
                if(null != unsignRecipes && !unsignRecipes.isEmpty()) {
                    recipes.addAll(unsignRecipes);
                }

                //当前页的数据未签名的数据无法充满则需要查询未审核的数据
                if(unsignCount < endIndex){
                    List<Recipe> uncheckRecipes = recipeDAO.findByDoctorIdAndStatus(doctorId,
                            Collections.singletonList(RecipeStatusConstant.UNCHECK), ConditionOperator.EQUAL, false, 0, limit - recipes.size(), mark);
                    if(null != uncheckRecipes && !uncheckRecipes.isEmpty()) {
                        recipes.addAll(uncheckRecipes);
                    }
                }
            }else{
                //未签名的数据已经全部显示
                int startIndex = start - unsignCount;
                List<Recipe> uncheckRecipes = recipeDAO.findByDoctorIdAndStatus(doctorId,
                        Collections.singletonList(RecipeStatusConstant.UNCHECK), ConditionOperator.EQUAL, false, startIndex, limit, mark);
                if(null != uncheckRecipes && !uncheckRecipes.isEmpty()) {
                    recipes.addAll(uncheckRecipes);
                }
            }
        } else {
            //历史处方数据
            recipes = recipeDAO.findByDoctorIdAndStatus(doctorId,
                    Collections.singletonList(RecipeStatusConstant.CHECK_PASS), ConditionOperator.GREAT_EQUAL, false, start, limit, mark);
        }

        List<String> patientIds = new ArrayList<>(0);
        Map<Integer,Recipe> recipeMap = new HashMap<>();
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        for (Recipe recipe : recipes) {
            if(StringUtils.isNotEmpty(recipe.getMpiid())) {
                patientIds.add(recipe.getMpiid());
            }
            //设置处方具体药品名称
            recipe.setRecipeDrugName(recipeDetailDAO.getDrugNamesByRecipeId(recipe.getRecipeId()));
            //前台页面展示的时间源不同
            if(0 == mark) {
                if(null != recipe.getLastModify()) {
                    recipe.setRecipeShowTime(recipe.getLastModify());
                }
            }else{
                if(null != recipe.getSignDate()) {
                    recipe.setRecipeShowTime(recipe.getSignDate());
                }
            }
            boolean effective = false;
            //只有审核未通过的情况需要看订单状态
            if(RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()){
                effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(),recipe.getPayMode());
            }
            Map<String, String> tipMap = getTipsByStatus(recipe.getStatus(), recipe, effective);
            recipe.setShowTip(MapValueUtil.getString(tipMap, "listTips"));
            recipeMap.put(recipe.getRecipeId(),this.convertRecipeForRAP(recipe));
        }

        List<Patient> patientList = null;
        if(!patientIds.isEmpty()) {
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            patientList = patientDAO.findByMpiIdIn(patientIds);
        }
        Map<String,Patient> patientMap = new HashMap<>();
        if(null != patientList && !patientList.isEmpty()){
            RelationPatientDAO relationPatientDAO = getDAO(RelationPatientDAO.class);
            RelationLabelDAO labelDAO = getDAO(RelationLabelDAO.class);
            for(Patient patient : patientList){
                //设置患者数据
                setPatientMoreInfo(patient,doctorId,relationPatientDAO,labelDAO);
                patientMap.put(patient.getMpiId(),this.convertPatientForRAP(patient));
            }
        }

        List<HashMap<String, Object>> list = new ArrayList<>(0);
        List<HashMap<String, Object>> unsignMapList = new ArrayList<>(0);
        List<HashMap<String, Object>> uncheckMapList = new ArrayList<>(0);
        for (Recipe recipe : recipes) {
            //对处方数据进行分类
            String mpiid = recipe.getMpiid();
            HashMap<String, Object> _map = new HashMap<>();
            _map.put("recipe", recipeMap.get(recipe.getRecipeId()));
            _map.put("patient", patientMap.get(mpiid));

            //新开处方与历史处方JSON结构不同
            if(0 == mark) {
                if (hasUnsignRecipe) {
                    if (recipe.getStatus() <= RecipeStatusConstant.UNSIGN) {
                        //未签名处方
                        unsignMapList.add(_map);
                    }else if(RecipeStatusConstant.UNCHECK == recipe.getStatus()){
                        //未审核处方
                        uncheckMapList.add(_map);
                    }
                } else {
                    uncheckMapList.add(_map);
                }
            }else{
                list.add(_map);
            }
        }

        if (!unsignMapList.isEmpty()) {
            HashMap<String, Object> _map = new HashMap<>();
            _map.put(UNSIGN, unsignMapList);
            list.add(_map);
        }

        if (!uncheckMapList.isEmpty()) {
            HashMap<String, Object> _map = new HashMap<>();
            _map.put(UNCHECK, uncheckMapList);
            list.add(_map);
        }

        return list;
    }

    public void setPatientMoreInfo(Patient patient, int doctorId, RelationPatientDAO dao, RelationLabelDAO labelDAO){
        RelationDoctor relationDoctor = dao.getByMpiidAndDoctorId(patient.getMpiId(),doctorId);
        Boolean relationFlag = false;  //是否关注
        Boolean signFlag = false; //是否签约
        List<String> labelNames = new ArrayList<>();
        if (relationDoctor != null) {
            relationFlag = true;
            if (relationDoctor.getFamilyDoctorFlag()) {
                signFlag = true;
            }

            if(null != labelDAO) {
                labelNames = labelDAO.findLabelNamesByRPId(relationDoctor.getRelationDoctorId());
            }
        }
        patient.setRelationFlag(relationFlag);
        patient.setSignFlag(signFlag);
        patient.setLabelNames(labelNames);
    }

    public Recipe convertRecipeForRAP(Recipe recipe) {
        Recipe r = new Recipe();
        r.setRecipeId(recipe.getRecipeId());
        r.setCreateDate(recipe.getCreateDate());
        r.setRecipeType(recipe.getRecipeType());
        r.setStatus(recipe.getStatus());
        r.setOrganDiseaseName(recipe.getOrganDiseaseName());
        r.setRecipeDrugName(recipe.getRecipeDrugName());
        r.setRecipeShowTime(recipe.getRecipeShowTime());
        r.setShowTip(recipe.getShowTip());
        return r;
    }

    public Patient convertPatientForRAP(Patient patient) {
        Patient p = new Patient();
        p.setPatientName(patient.getPatientName());
        p.setPatientSex(patient.getPatientSex());
        p.setBirthday(patient.getBirthday());
        p.setPatientType(patient.getPatientType());
        p.setIdcard(patient.getIdcard());
        p.setMobile(patient.getMobile());
        p.setMpiId(patient.getMpiId());
        p.setPhoto(patient.getPhoto());
        p.setSignFlag(patient.getSignFlag());
        p.setRelationFlag(patient.getRelationFlag());
        p.setLabelNames(patient.getLabelNames());
        return p;
    }

    /**
     * 组装从HIS获取处方状态的map，key为organId,value为HIS端处方编号 recipeCode集合
     * @param list
     * @param map
     */
    private void assembleQueryStatusFromHis(List<Recipe> list, Map<Integer,List<String>> map){
        if(CollectionUtils.isNotEmpty(list)){
            for(Recipe recipe : list){
                //到院取药的去查询HIS状态
                if(RecipeStatusConstant.HAVE_PAY == recipe.getStatus() || RecipeStatusConstant.CHECK_PASS == recipe.getStatus()){
                    if(!map.containsKey(recipe.getClinicOrgan())){
                        map.put(recipe.getClinicOrgan(),new ArrayList<String>(0));
                    }

                    if(StringUtils.isNotEmpty(recipe.getRecipeCode())) {
                        map.get(recipe.getClinicOrgan()).add(recipe.getRecipeCode());
                    }
                }
            }
        }
    }

    /**
     * 校验处方数据
     * @param recipeId
     */
    private Recipe checkRecipeCommonInfo(Integer recipeId, RecipeResultBean resultBean){
        if(null == resultBean){
            resultBean = RecipeResultBean.getSuccess();
        }
        if (null == recipeId) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("处方ID参数为空");
            return null;
        }

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe dbRecipe = recipeDAO.getByRecipeId(recipeId);
        if(null == dbRecipe){
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("处方未找到");
            return null;
        }

        return dbRecipe;
    }

    /**
     * 保存处方单的时候，校验处方单数据
     * @param recipe
     */
    private void validateSaveRecipeData(Recipe recipe) {
        if(null == recipe){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipe is required!");
        }

        if (StringUtils.isEmpty(recipe.getMpiid())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required!");
        }

        if (StringUtils.isEmpty(recipe.getOrganDiseaseName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organDiseaseName is required!");
        }

        if (recipe.getClinicOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "clinicOrgan is required!");
        }

        if (recipe.getDepart() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "depart is required!");
        }

        if (recipe.getDoctor() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctor is required!");
        }

        //判断诊断备注是否超过50字
        if(StringUtils.isNotEmpty(recipe.getMemo()) && recipe.getMemo().length()>50){
            throw new DAOException("备注内容字数限制50字");
        }

        if(RecipeConstant.RECIPETYPE_TCM.equals(recipe.getRecipeType())){
            if (recipe.getTcmUsePathways() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "tcmUsePathways is required!");
            }

            if (recipe.getTcmUsingRate() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "tcmUsingRate is required!");
            }
        }

        PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);
        Patient patient=patDao.get(recipe.getMpiid());
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (patient==null || StringUtils.isEmpty(patient.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能开处方");
        }

    }

    /**
     * 保存处方前进行校验前段输入数据
     * @author zhangx
     * @date 2015-12-4 下午4:05:34
     * @param detail 处方明细
     */
    private void validateRecipeDetailData(Recipedetail detail, Recipe recipe) {
        if (detail.getDrugId()==null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "drugId is required!");
        }
        if (detail.getUseDose() == null||detail.getUseDose()<=0d) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "useDose is required!");
        }
        //西药和中成药必填参数
        if(!recipe.containTcmType()) {
            if (detail.getUseDays() == null || detail.getUseDays() <= 0) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "useDays is required!");
            }
            if (detail.getUseTotalDose() == null||detail.getUseTotalDose()<=0d) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "useTotalDose is required!");
            }
            if (StringUtils.isEmpty(detail.getUsingRate())) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "usingRate is required!");
            }
            if (StringUtils.isEmpty(detail.getUsePathways())) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "usePathways is required!");
            }
        }

    }

    /**
     * 设置药品详情数据
     * @param recipe  处方
     * @param recipedetails   处方ID
     */
    private boolean setDetailsInfo(Recipe recipe, List<Recipedetail> recipedetails) {
        boolean success = false;
        int organId = recipe.getClinicOrgan();
        //药品总金额
        BigDecimal totalMoney = new BigDecimal(0d);
        List<Integer> drugIds = new ArrayList<>(0);
        Date nowDate = DateTime.now().toDate();
        for (Recipedetail detail : recipedetails){
            //设置药品详情基础数据
            detail.setStatus(1);
            detail.setRecipeId(recipe.getRecipeId());
            detail.setCreateDt(nowDate);
            detail.setLastModify(nowDate);
            if(null != detail.getDrugId()) {
                drugIds.add(detail.getDrugId());
            }
        }

        if(CollectionUtils.isNotEmpty(drugIds)) {
            OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
            DrugListDAO drugListDAO = DAOFactory.getDAO(DrugListDAO.class);
            List<OrganDrugList> organDrugList = organDrugListDAO.findByOrganIdAndDrugIds(organId, drugIds);
            List<DrugList> drugList = drugListDAO.findByDrugIds(drugIds);
            if(CollectionUtils.isNotEmpty(organDrugList) && CollectionUtils.isNotEmpty(drugList)){
                Map<Integer,OrganDrugList> organDrugListMap = new HashMap<>();
                Map<Integer,DrugList> drugListMap = new HashMap<>();
                for(OrganDrugList obj : organDrugList){
                    organDrugListMap.put(obj.getDrugId(),obj);
                }

                for(DrugList obj : drugList){
                    drugListMap.put(obj.getDrugId(),obj);
                }

                for (Recipedetail detail : recipedetails) {
                    //设置药品基础数据
                    DrugList drug = drugListMap.get(detail.getDrugId());
                    if(null != drug){
                        detail.setDrugName(drug.getDrugName());
                        detail.setDrugSpec(drug.getDrugSpec());
                        detail.setDrugUnit(drug.getUnit());
                        detail.setDefaultUseDose(drug.getUseDose());
                        detail.setUseDoseUnit(drug.getUseDoseUnit());
                        detail.setDosageUnit(drug.getUseDoseUnit());
                        //设置药品包装数量
                        detail.setPack(drug.getPack());
                        //中药基础数据处理
                        if(RecipeConstant.RECIPETYPE_TCM.equals(recipe.getRecipeType())){
                            detail.setUsePathways(recipe.getTcmUsePathways());
                            detail.setUsingRate(recipe.getTcmUsingRate());
                            detail.setUseDays(recipe.getCopyNum());
                            detail.setUseTotalDose(BigDecimal.valueOf(recipe.getCopyNum()).multiply(BigDecimal.valueOf(detail.getUseDose())).doubleValue());
                        }else if(RecipeConstant.RECIPETYPE_HP.equals(recipe.getRecipeType())){
                            detail.setUseDays(recipe.getCopyNum());
                            detail.setUseTotalDose(BigDecimal.valueOf(recipe.getCopyNum()).multiply(BigDecimal.valueOf(detail.getUseDose())).doubleValue());
                        }
                    }

                    //设置药品价格
                    OrganDrugList organDrug = organDrugListMap.get(detail.getDrugId());
                    if(null != organDrug) {
                        detail.setOrganDrugCode(organDrug.getOrganDrugCode());
                        BigDecimal price = organDrug.getSalePrice();
                        if(null == price){
                            logger.error("setDetailsInfo 药品ID："+drug.getDrugId()+" 在医院(ID为"+organId+")的价格为NULL！");
                            throw new DAOException(DAOException.VALUE_NEEDED,"药品数据异常！");
                        }
                        detail.setSalePrice(price);
                        //保留3位小数
                        BigDecimal drugCost = price.multiply(new BigDecimal(detail.getUseTotalDose()))
                                .divide(BigDecimal.ONE, 3, RoundingMode.UP);
                        detail.setDrugCost(drugCost);
                        totalMoney = totalMoney.add(drugCost);
                    }
                }
                success = true;
            }else{
                logger.error("setDetailsInfo organDrugList或者drugList为空. recipeId=[{}], drugIds={}",recipe.getRecipeId(), JSONUtils.toString(drugIds));
            }
        }else{
            logger.error("setDetailsInfo 详情里没有药品ID. recipeId=[{}]",recipe.getRecipeId());
        }

        recipe.setTotalMoney(totalMoney);
        recipe.setActualPrice(totalMoney);
        return success;
    }

    /**
     *获取当前患者所有家庭成员(包括自己)
     * @param mpiId
     * @return
     */
    public List<String> getAllMemberPatientsByCurrentPatient(String mpiId){
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
        //获取所有家庭成员的患者编号
        List<String> allMpiIds = familyMemberDAO.findMemberMpiByMpiid(mpiId);
        if(null == allMpiIds){
            allMpiIds = new ArrayList<>(0);
        }
        //加入患者自己的编号
        allMpiIds.add(mpiId);

        return allMpiIds;
    }

    /**
     *获取处方详情
     * @param recipeId
     * @param isDoctor   true:医生端  false:健康端
     * @return
     */
    private HashMap<String, Object> getRecipeAndDetailByIdImpl(int recipeId, boolean isDoctor) {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        HashMap<String, Object> map = new HashMap<>();
        if (recipe == null) {
            return map;
        }
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        RelationPatientDAO relationPatientDAO = getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = getDAO(RelationLabelDAO.class);

        Patient patient = patientDAO.get(recipe.getMpiid());
        if(patient != null) {
            //添加患者标签和关注这些字段
            setPatientMoreInfo(patient, recipe.getDoctor(), relationPatientDAO, labelDAO);
            patient = this.convertPatientForRAP(patient);
        }
        List<Recipedetail> recipedetails = detailDAO.findByRecipeId(recipeId);


        //中药处方处理
        if(RecipeConstant.RECIPETYPE_TCM.equals(recipe.getRecipeType())){
            if(CollectionUtils.isNotEmpty(recipedetails)){
                Recipedetail recipedetail = recipedetails.get(0);
                recipe.setTcmUsePathways(recipedetail.getUsePathways());
                recipe.setTcmUsingRate(recipedetail.getUsingRate());
            }
        }
        map.put("patient", patient);
        map.put("recipedetails", recipedetails);
        if(isDoctor) {
            // 获取处方单药品总价
            CdrUtil.getRecipeTotalPriceRange(recipe,recipedetails);

            ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);

            boolean effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(),recipe.getPayMode());
            Map<String, String> tipMap = getTipsByStatus(recipe.getStatus(), recipe, effective);
            map.put("tips", MapValueUtil.getString(tipMap, "tips"));
            map.put("cancelReason", MapValueUtil.getString(tipMap, "cancelReason"));
            RecipeCheckService service = AppContextHolder.getBean("recipeCheckService", RecipeCheckService.class);
            //获取审核不通过详情
            List<Map<String, Object>> mapList = service.getCheckNotPassDetail(recipeId);
            map.put("reasonAndDetails", mapList);

            //设置处方撤销标识 true:可以撤销, false:不可撤销
            Boolean cancelFlag = false;
            if(RecipeStatusConstant.REVOKE != recipe.getStatus()){
                if((recipe.getChecker() == null) && !Integer.valueOf(1).equals(recipe.getPayFlag())
                        && recipe.getStatus() != RecipeStatusConstant.UNSIGN
                        && recipe.getStatus() != RecipeStatusConstant.HIS_FAIL
                        && recipe.getStatus() != RecipeStatusConstant.NO_DRUG
                        && recipe.getStatus() != RecipeStatusConstant.NO_PAY
                        && recipe.getStatus() != RecipeStatusConstant.NO_OPERATOR
                        && !Integer.valueOf(1).equals(recipe.getChooseFlag())){
                    cancelFlag = true;
                }
            }
            map.put("cancelFlag",cancelFlag);
            //能否开医保处方
            boolean medicalFlag = false;
            ConsultSet set = consultSetDAO.get(recipe.getDoctor());
            if(null != set && null != set.getMedicarePrescription()){
                medicalFlag = (true == set.getMedicarePrescription())?true:false;
            }
            map.put("medicalFlag", medicalFlag);
            if(null != recipe.getChecker() && recipe.getChecker() > 0){
                String ysTel = doctorDAO.getMobileByDoctorId(recipe.getChecker());
                if(StringUtils.isNotEmpty(ysTel)) {
                    recipe.setCheckerTel(ysTel);
                }
            }

            //审核不通过处方单详情增加二次签名标记
            if(RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus() && (recipe.canMedicalPay() || effective)) {
                Boolean secondsignflag = organConfigDAO.getEnableSecondsignByOrganId(recipe.getClinicOrgan());
                map.put("secondSignFlag", (null!=secondsignflag && true==secondsignflag)?true:false);
            }
        }else{
            RecipeOrder order = orderDAO.getOrderByRecipeId(recipeId);
            map.put("tips", getTipsByStatusForPatient(recipe, order));
            if(null != recipe.getEnterpriseId() && RecipeConstant.GIVEMODE_SEND_TO_HOME.equals(recipe.getGiveMode())
                    && (recipe.getStatus() == RecipeStatusConstant.WAIT_SEND || recipe.getStatus() == RecipeStatusConstant.IN_SEND
                        || recipe.getStatus() == RecipeStatusConstant.FINISH)){
                DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
                map.put("depTel",drugsEnterpriseDAO.getTelById(recipe.getEnterpriseId()));
            }

            recipe.setOrderAmount(recipe.getTotalMoney());
            BigDecimal actualPrice = null;
            if(null != order){
                actualPrice = order.getRecipeFee();
                recipe.setDiscountAmount(order.getCouponName());
            }else{
                // couponId = -1有优惠券  不使用 显示“不使用优惠券”
                actualPrice = recipe.getActualPrice();
                recipe.setDiscountAmount("0.0");

                //如果获取不到有效订单，则不返回订单编号（场景：医保消息发送成功后，处方单关联了一张无效订单，此时处方单点击自费结算，应跳转到订单确认页面）
                recipe.setOrderCode(null);
            }
            if(null == actualPrice){
                actualPrice = recipe.getTotalMoney();
            }
            recipe.setActualPrice(actualPrice);

            //无法配送时间文案提示
            map.put("unSendTitle", getUnSendTitleForPatient(recipe));
            //患者处方取药方式提示
            map.put("recipeGetModeTip", getRecipeGetModeTip(recipe));

            if(null != order && 1 == order.getEffective() && StringUtils.isNotEmpty(recipe.getOrderCode())){
                //如果创建过自费订单，则不显示医保支付
                recipe.setMedicalPayFlag(0);
            }

            //药品价格显示处理
            if(RecipeStatusConstant.FINISH == recipe.getStatus() ||
                    (1 == recipe.getChooseFlag() && !recipe.canncelRecipe() &&
                            (RecipeConstant.PAYMODE_MEDICAL_INSURANCE.equals(recipe.getPayMode())
                                    || RecipeConstant.PAYMODE_ONLINE.equals(recipe.getPayMode())
                                    || RecipeConstant.PAYMODE_TO_HOS.equals(recipe.getPayMode())))){
                //显示价格
            }else{
                recipe.setTotalMoney(null);
            }
        }

        if(StringUtils.isEmpty(recipe.getMemo())){
            recipe.setMemo("无");
        }

        //设置失效时间
        if(RecipeStatusConstant.CHECK_PASS == recipe.getStatus()) {
            recipe.setRecipeSurplusHours(getRecipeSurplusHours(recipe.getSignDate()));
        }

        map.put("recipe", recipe);

        return map;
    }

    /**
     * 在线续方首页，获取当前登录患者待处理处方单
     * @param mpiid 当前登录患者mpiid
     * @return
     */
    @RpcService
    public RecipeResultBean getHomePageTaskForPatient(String mpiid){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        //根据mpiid获取当前患者所有家庭成员(包括自己)
        List<String> allMpiIds = getAllMemberPatientsByCurrentPatient(mpiid);
        //获取患者待处理处方单id
        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS, 0, Integer.MAX_VALUE);
        //获取患者历史处方单，有一个即不为空
        List<PatientRecipeBean> backList = recipeDAO.findOtherRecipesForPatient(allMpiIds, recipeIds, 0, 1);
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();

        if(CollectionUtils.isEmpty(recipeIds)){
            if(CollectionUtils.isEmpty(backList)){
                resultBean.setExtendValue("-1");
                resultBean.setMsg("查看我的处方单");
            } else {
                resultBean.setExtendValue("0");
                resultBean.setMsg("查看我的处方单");
            }
        } else {
            resultBean.setExtendValue("1");
            resultBean.setMsg(String.valueOf(recipeIds.size()));
        }

        return resultBean;
    }

    /**
     * 无法配送时间段文案提示
     * 处方单详情（待处理，待支付,药师未审核，状态为待配送,药师已审核，状态为待配送）
     */
    public String getUnSendTitleForPatient(Recipe recipe){
        String unSendTitle = "";
        switch(recipe.getStatus()){
            case RecipeStatusConstant.CHECK_PASS:
            case RecipeStatusConstant.WAIT_SEND:
            case RecipeStatusConstant.CHECK_PASS_YS:
            case RecipeStatusConstant.READY_CHECK_YS:
                if(!RecipeConstant.PAYMODE_TFDS.equals(recipe.getPayMode())
                        && !RecipeConstant.PAYMODE_COD.equals(recipe.getPayMode())){
                    unSendTitle = ParamUtils.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP);
                }
                //患者选择药店取药但是未点击下一步而返回处方单详情，此时payMode会变成4，增加判断条件
                if(RecipeConstant.PAYMODE_TFDS.equals(recipe.getPayMode()) && 0 == recipe.getChooseFlag()){
                    unSendTitle = ParamUtils.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP);
                }
                break;
            default:
                unSendTitle = "";
        }
        return unSendTitle;
    }

    /**
     * 患者处方取药方式提示
     */
    public String getRecipeGetModeTip(Recipe recipe){
        String recipeGetModeTip = "";
        // 该处方不是只能配送处方，可以显示 到院取药 的文案
        if(1 != recipe.getChooseFlag() && !Integer.valueOf(1).equals(recipe.getDistributionFlag())){
            recipeGetModeTip = ParamUtils.getParam(ParameterConstant.KEY_RECIPE_GETMODE_TIP);
        }
        return recipeGetModeTip;

    }

    /**
     * 处方订单下单时和下单之后对处方单的更新
     * @param saveFlag
     * @param recipeId
     * @param payFlag
     * @param info
     * @return
     */
    public RecipeResultBean updateRecipePayResultImplForOrder(boolean saveFlag, Integer recipeId, Integer payFlag,
                                                              Map<String, Object> info){
        RecipeResultBean result = RecipeResultBean.getSuccess();
        if(null == recipeId){
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方单id为null");
        }

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Map<String, Object> attrMap = new HashMap<>();
        if(null != info){
            attrMap.putAll(info);
        }
        Integer payMode = MapValueUtil.getInteger(attrMap, "payMode");
        Integer giveMode = null;
        if(RecipeConstant.PAYMODE_TFDS.equals(payMode)){
            giveMode = RecipeConstant.GIVEMODE_TFDS;//到店取药
        }else if(RecipeConstant.PAYMODE_COD.equals(payMode) || RecipeConstant.PAYMODE_ONLINE.equals(payMode)
                || RecipeConstant.PAYMODE_MEDICAL_INSURANCE.equals(payMode)){
            giveMode = RecipeConstant.GIVEMODE_SEND_TO_HOME;//配送到家
        }else if(RecipeConstant.PAYMODE_TO_HOS.equals(payMode)){
            giveMode = RecipeConstant.GIVEMODE_TO_HOS;//到院取药
        }else{
            giveMode = null;
        }
        attrMap.put("giveMode", giveMode);
        //默认审核通过
        Integer status = RecipeStatusConstant.CHECK_PASS;
        Recipe _dbRecipe = recipeDAO.getByRecipeId(recipeId);
        if(RecipeResultBean.SUCCESS.equals(result.getCode())){
            //根据传入的方式来处理, 因为供应商列表，钥世圈提供的有可能是多种方式都支持，当时这2个值是保存为null的
            if(saveFlag){
                attrMap.put("chooseFlag", 1);
                String memo = "";
                if(RecipeConstant.GIVEMODE_SEND_TO_HOME.equals(giveMode)){
                    if (RecipeConstant.PAYMODE_ONLINE.equals(payMode)) {
                        //线上支付
                        if (PayConstant.PAY_FLAG_PAY_SUCCESS == payFlag) {
                            //配送到家-线上支付
                            status = RecipeStatusConstant.READY_CHECK_YS;
                            // 如果处方类型是中药或膏方不需要走药师审核流程,默认状态审核通过
                            if (_dbRecipe.containTcmType()) {
                                status = RecipeStatusConstant.CHECK_PASS_YS;
                            }
                            memo = "配送到家-线上支付成功";
                        }else{
                            memo = "配送到家-线上支付失败";
                        }
                    }else if(RecipeConstant.PAYMODE_MEDICAL_INSURANCE.equals(payMode)){
                        if(_dbRecipe.canMedicalPay()){
                            //可医保支付的单子在用户看到之前已进行审核
                            status = RecipeStatusConstant.CHECK_PASS_YS;
                            memo = "医保支付成功，发送药企处方";
                        }
                    }else if(RecipeConstant.PAYMODE_COD.equals(payMode)){
                        //收到userConfirm通知
                        status = RecipeStatusConstant.READY_CHECK_YS;
                        memo = "配送到家-货到付款成功";
                    }
                }else if(RecipeConstant.GIVEMODE_TO_HOS.equals(giveMode)){
                    //医院取药-线上支付，这块其实已经用不到了
                    status = RecipeStatusConstant.HAVE_PAY;
                    memo = "医院取药-线上支付成功";
                }else if(RecipeConstant.GIVEMODE_TFDS.equals(giveMode)){
                    //收到userConfirm通知
                    status = RecipeStatusConstant.READY_CHECK_YS;
                    memo = "药店取药-到店取药成功";
                }

                //记录日志
                RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.CHECK_PASS,status,memo);
            }else{
                attrMap.put("chooseFlag", 0);
            }

            try {
                boolean flag = recipeDAO.updateRecipeInfoByRecipeId(recipeId, status, attrMap);
                if(flag){
                    result.setMsg(SystemConstant.SUCCESS);
                }else {
                    result.setCode(RecipeResultBean.FAIL);
                    result.setError("更新处方失败");
                }
            } catch (Exception e) {
                result.setCode(RecipeResultBean.FAIL);
                result.setError("更新处方失败，"+e.getMessage());
            }
        }

        if (saveFlag && RecipeResultBean.SUCCESS.equals(result.getCode())) {
            if(1 == _dbRecipe.getFromflag()) {
                //HIS消息发送
                RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);
                hisService.recipeDrugTake(recipeId, payFlag, result);
            }
        }

        if(RecipeResultBean.SUCCESS.equals(result.getCode())){
            if(RecipeStatusConstant.READY_CHECK_YS == status) {
                //如果处方 在待药师审核状态 给对应机构的药师进行消息推送
                RecipeMsgService.batchSendMsg(recipeId, status);
            }

            if(RecipeStatusConstant.CHECK_PASS_YS == status){
                //说明是可进行医保支付的单子或者是中药或膏方处方
                RemoteDrugEnterpriseService remoteDrugEnterpriseService = AppContextHolder.getBean("eh.remoteDrugService", RemoteDrugEnterpriseService.class);
                remoteDrugEnterpriseService.pushSingleRecipeInfo(recipeId);
            }
        }

        return result;
    }

    /**
     * 获取处方失效剩余时间
     * @param signDate
     * @return
     */
    public String getRecipeSurplusHours( Date signDate){
        String recipeSurplusHours = "0.1";
        if(null != signDate){
            long startTime = Calendar.getInstance().getTimeInMillis();
            long endTime = DateConversion.getDateAftXDays(signDate,3).getTime();
            if(endTime > startTime){
                DecimalFormat df = new DecimalFormat("0.00");
                recipeSurplusHours = df.format((endTime-startTime)/(float)(1000*60*60));
            }
        }

        return recipeSurplusHours;
    }

    /**
     * 判断是否可以线上支付
     * @param wxAccount 微信帐号
     * @param hisStatus 1:his启用
     * @return
     */
    private boolean isCanSupportPayOnline(String wxAccount, int hisStatus){
        if(StringUtils.isNotEmpty(wxAccount) && 1 == hisStatus){
            return true;
        }

        return false;
    }

    /**
     * 往e签宝进行注册
     * @param eSignId
     * @return
     */
    private String registerEsign(String eSignId, EsignPerson person){
        // 未在E宝进行注册的医生先进行注册
        if (StringUtils.isEmpty(eSignId)) {
            IESignService eSignService = AppContextHolder.getBean("esign.esignService", IESignService.class);
            try {
                eSignId = eSignService.addPerson(person);
            } catch (Exception e) {
                logger.error("registerEsign esign register error! reason:[{}]", e.getMessage());
            }
        }

        return eSignId;
    }

    /**
     * 查询单个处方在HIS中的状态
     * @param recipeId
     * @param modelFlag
     * @return
     */
    public String searchRecipeStatusFromHis(Integer recipeId, int modelFlag){
        logger.info("searchRecipeStatusFromHis "+((1==modelFlag)?"supportTakeMedicine":"supportDistribution")+"  recipeId="+recipeId);
        RecipeHisService hisService = AppContextHolder.getBean("eh.recipeHisService", RecipeHisService.class);
        //HIS发送消息
        return hisService.recipeSingleQuery(recipeId);
    }

    /**
     * 患者mpiId变更后修改处方数据内容
     * @param newPat
     * @param oldMpiId
     */
    public void updatePatientInfoForRecipe(Patient newPat, String oldMpiId){
        if(null != newPat && StringUtils.isNotEmpty(newPat.getMpiId()) && StringUtils.isNotEmpty(oldMpiId)) {
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            Integer count = recipeDAO.updatePatientInfoForRecipe(newPat, oldMpiId);
            logger.info("updatePatientInfoForRecipe newMpiId=[{}], oldMpiId=[{}], count=[{}]", newPat.getMpiId(), oldMpiId, count);
        }
    }

    /**
     * 定时任务向患者推送确认收货微信消息
     */
    @RpcService
    public void pushPatientConfirmReceiptTask()
    {
        // 设置查询时间段
        String endDt =
            DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(3),
                DateConversion.DEFAULT_DATE_TIME);
        String startDt =
            DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(
                RECIPE_EXPIRED_SECTION), DateConversion.DEFAULT_DATE_TIME);

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Recipe> recipes = recipeDAO.findNotConfirmReceiptList(startDt, endDt);

        logger.info("enter RecipeService.pushPatientConfirmReceiptTask() and recipes.size = "
            + recipes.size());
        // 批量信息推送
        RecipeMsgService.batchSendMsgForNew(recipes, RecipeStatusConstant.RECIPR_NOT_CONFIRM_RECEIPT);
    }
}