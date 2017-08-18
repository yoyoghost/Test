package eh.cdr.service;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDrugListDAO;
import eh.cdr.bean.RecipeCheckPassResult;
import eh.cdr.bean.RecipeResultBean;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.dao.RecipeDetailDAO;
import eh.cdr.his.service.*;
import eh.entity.base.OrganDrugList;
import eh.entity.cdr.Recipe;
import eh.entity.cdr.Recipedetail;
import eh.entity.his.*;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by liuya on 2017-4-13.
 * his接口服务
 */
public class RecipeHisService {

    private static final Logger logger = LoggerFactory.getLogger(RecipeHisService.class);

    /**
     * 发送处方
     * @param recipeId
     */
    @RpcService
    public boolean recipeSendHis(Integer recipeId, Integer otherOrganId){
        boolean result = true;
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(null == recipe){
            //中药处方由于不需要跟HIS交互，故读写分离后有可能查询不到数据
            return false;
        }
        if(skipHis(recipe)){
            RecipeCheckPassResult recipeCheckPassResult = new RecipeCheckPassResult();
            recipeCheckPassResult.setRecipeId(recipeId);
            recipeCheckPassResult.setRecipeCode(RandomStringUtils.randomAlphanumeric(10));
            HisCallBackService.checkPassSuccess(recipeCheckPassResult,true);
            return result;
        }

        Integer sendOrganId = (null==otherOrganId)?recipe.getClinicOrgan():otherOrganId;
        if(hisServiceConfigDAO.isHisEnable(sendOrganId)){
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
            OrganDrugListDAO drugDao = DAOFactory.getDAO(OrganDrugListDAO.class);
            EmploymentDAO eDao = DAOFactory.getDAO(EmploymentDAO.class);
            RecipeSendHisService service = AppContextHolder.getBean("eh.recipeSendHisService", RecipeSendHisService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
            HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");
            //创建请求体
            RecipeSendRequest request = new RecipeSendRequest(recipe, details, patient, card);
            //设置医生工号
            request.setDoctorID(eDao.getJobNumberByDoctorIdAndOrganIdAndDepartment(recipe.getDoctor(), sendOrganId, recipe.getDepart()));
            //查询生产厂家
            List<OrderItem> orderItemList = request.getOrderList();
            if(CollectionUtils.isNotEmpty(orderItemList)){
                List<Integer> drugIdList = FluentIterable.from(orderItemList).transform(new Function<OrderItem, Integer>() {
                    @Override
                    public Integer apply(OrderItem input) {
                        return input.getDrugId();
                    }
                }).toList();

                List<OrganDrugList> organDrugList = drugDao.findByOrganIdAndDrugIds(sendOrganId,drugIdList);
                Map<Integer, OrganDrugList> drugIdAndProduce = Maps.uniqueIndex(organDrugList, new Function<OrganDrugList, Integer>() {
                    @Override
                    public Integer apply(OrganDrugList input) {
                        return input.getDrugId();
                    }
                });

                OrganDrugList organDrug;
                for (OrderItem item : orderItemList) {
                    organDrug = drugIdAndProduce.get(item.getDrugId());
                    if(null != organDrug) {
                        item.setManfcode(organDrug.getProducerCode());
                    }
                }

            }
            request.setOrganID(sendOrganId.toString());

            service.recipeSendHis(request);
        }else{
            result = false;
            logger.error("recipeSendHis 医院HIS未启用[organId:"+sendOrganId+",recipeId:"+recipeId+"]");
        }
        return result;
    }

    /**
     * 更新处方状态推送his服务
     * @param recipeId
     */
    @RpcService
    public boolean recipeStatusUpdate(Integer recipeId){
        return recipeStatusUpdateWithOrganId(recipeId,null,null);
    }

    /**
     * 发送指定HIS修改处方状态
     * @param recipeId
     * @param otherOrganId
     * @return
     */
    @RpcService
    public boolean recipeStatusUpdateWithOrganId(Integer recipeId, Integer otherOrganId, String hisRecipeStatus){
        boolean flag = true;
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(null == recipe){
            return false;
        }
        if(skipHis(recipe)){
            return flag;
        }

        Integer sendOrganId = (null==otherOrganId)?recipe.getClinicOrgan():otherOrganId;
        if(hisServiceConfigDAO.isHisEnable(sendOrganId)){
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
            RecipeStatusUpdateService service = AppContextHolder.getBean("recipeStatusUpdateService", RecipeStatusUpdateService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
            HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");
            RecipeStatusUpdateReq request = new RecipeStatusUpdateReq(recipe,details,patient,card);
            request.setOrganID(sendOrganId.toString());
            if(StringUtils.isNotEmpty(hisRecipeStatus)){
                request.setRecipeStatus(hisRecipeStatus);
            }

            flag = service.recipeStatusUpdate(request);
        }else{
            flag = false;
            logger.error("recipeStatusUpdate 医院HIS未启用[organId:"+sendOrganId+",recipeId:"+recipeId+"]");
        }

        return flag;
    }

    /**
     * 处方退款推送his服务
     * @param recipeId
     */
    @RpcService
    public String recipeRefund(Integer recipeId){
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(null == recipe){
            return "处方不存在";
        }
        String backInfo = "成功";
        if(skipHis(recipe)){
            return backInfo;
        }
        if(hisServiceConfigDAO.isHisEnable(recipe.getClinicOrgan())) {
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
            RecipeRefundService service = AppContextHolder.getBean("recipeRefundService", RecipeRefundService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
            HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");
            RecipeRefundReq request = new RecipeRefundReq(recipe, details, patient, card);

            RecipeRefundRes response = service.refund(request);
            if(null == response || null == response.getMsgCode()){
                backInfo = "dao is null";
            }else{
                if(0 != response.getMsgCode()){
                    backInfo = response.getMsg();
                }
            }
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "同步HIS退款返回："+backInfo);
        }else {
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(),"recipeRefund[RecipeRefundService] HIS未启用");
            logger.error("recipeRefund 医院HIS未启用[organId:"+recipe.getClinicOrgan()+",recipeId:"+recipe.getRecipeId()+"]");
        }

        return backInfo;
    }

    /**
     * 处方购药方式及支付状态修改
     * @param recipeId
     * @param payFlag
     * @param result
     */
    @RpcService
    public RecipeResultBean recipeDrugTake(Integer recipeId, Integer payFlag, RecipeResultBean result){
        if(null == result){
            result = RecipeResultBean.getSuccess();
        }
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(null == recipe){
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方不存在");
            return result;
        }
        if(skipHis(recipe)){
            return result;
        }

        Integer status = recipe.getStatus();
        if(hisServiceConfigDAO.isHisEnable(recipe.getClinicOrgan())){
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
            DrugTakeUpdateService drugTakeUpdateService = AppContextHolder.getBean("drugTakeUpdateService", DrugTakeUpdateService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
            HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");
            DrugTakeChangeReq request = new DrugTakeChangeReq(recipe,details,patient,card);

            Boolean success = drugTakeUpdateService.drugTakeUpdate(request);
            if(success){
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(),status,status,"HIS更新购药方式返回：写入his成功");
            }else{
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(),status,status,"HIS更新购药方式返回：写入his失败");
                if(!RecipeConstant.GIVEMODE_TO_HOS.equals(recipe.getGiveMode())){
                    logger.error("HIS drugTake synchronize error. recipeId="+recipeId);
                    //配送到家同步失败则返回异常,医院取药不需要管，医院处方默认是医院取药
//                        HisCallBackService.havePayFail(_dbRecipe.getRecipeId());
                    result.setCode(RecipeResultBean.FAIL);
                    result.setError("由于医院接口异常，购药方式修改失败。");
                }
            }

            //线上支付完成需要发送消息
            if (RecipeResultBean.SUCCESS.equals(result.getCode()) && RecipeConstant.PAYMODE_ONLINE.equals(recipe.getPayMode()) && 1 == payFlag) {
                PayNotifyHisService payNotifyHisService = AppContextHolder.getBean("payNotifyHisService", PayNotifyHisService.class);

                PayNotifyReq payNotifyReq = new PayNotifyReq(recipe,patient,card);
                Recipedetail recipedetail = payNotifyHisService.payNotifyHis(payNotifyReq);
                if(null != recipedetail){
                    HisCallBackService.havePaySuccess(recipe.getRecipeId(),recipedetail);
                }else{
                    HisCallBackService.havePayFail(recipe.getRecipeId());
                    result.setCode(RecipeResultBean.FAIL);
                    result.setError("由于医院接口异常，支付失败，建议您稍后重新支付。");
                }
            }
        }else{
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(),status,status,"recipeDrugTake[DrugTakeUpdateService] HIS未启用");
            logger.error("recipeDrugTake 医院HIS未启用[organId:"+recipe.getClinicOrgan()+",recipeId:"+recipe.getRecipeId()+"]");
            result.setCode(RecipeResultBean.FAIL);
            result.setError("医院HIS未启用。");
        }

        return result;
    }

    /**
     * 处方批量查询
     * @param recipeCodes
     * @param organId
     */
    @RpcService
    public void recipeListQuery(List<String> recipeCodes, Integer organId){
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        if(hisServiceConfigDAO.isHisEnable(organId)) {
            RecipeListQueryService service = AppContextHolder.getBean("recipeListQueryService", RecipeListQueryService.class);
            RecipeListQueryReq request = new RecipeListQueryReq(recipeCodes,organId);
            service.recipeListQuery(request);
        }else{
            logger.error("recipeListQuery 医院HIS未启用[organId:"+organId+",recipeIds:"+ JSONUtils.toString(recipeCodes)+"]");
        }
    }

    /**
     * 处方完成
     * @param recipeId
     */
    @RpcService
    public boolean recipeFinish(Integer recipeId){
        boolean result = true;
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(null == recipe){
            return false;
        }
        if(skipHis(recipe)){
            return result;
        }

        if(hisServiceConfigDAO.isHisEnable(recipe.getClinicOrgan())){
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);
            RecipeStatusUpdateService service = AppContextHolder.getBean("recipeStatusUpdateService", RecipeStatusUpdateService.class);

            List<Recipedetail> details = recipeDetailDAO.findByRecipeId(recipeId);
            Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
            HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");
            RecipeStatusUpdateReq request = new RecipeStatusUpdateReq(recipe,details,patient,card);

            String memo = "";
            if(RecipeConstant.PAYMODE_COD.equals(recipe.getPayMode())){
                memo = "HIS配送到家完成返回";
            }else if(RecipeConstant.PAYMODE_TFDS.equals(recipe.getPayMode())){
                memo = "HIS到店取药完成返回";
            }
            boolean sendToHisFlag = service.recipeStatusUpdate(request);
            if(sendToHisFlag){
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.FINISH, RecipeStatusConstant.FINISH,memo+"：写入his成功");
            }else{
                result = false;
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.FINISH, RecipeStatusConstant.FINISH,memo+"：写入his失败");
            }
        }else{
            result = false;
            RecipeLogService.saveRecipeLog(recipeId, RecipeStatusConstant.FINISH, RecipeStatusConstant.FINISH,"recipeFinish[RecipeStatusUpdateService] HIS未启用");
            logger.error("recipeFinish 医院HIS未启用[organId:"+recipe.getClinicOrgan()+",recipeId:"+recipeId+"]");
        }

        return result;
    }

    /**
     * 单个处方查询
     * @param recipeId
     * @return
     */
    @RpcService
    public String recipeSingleQuery(Integer recipeId){
        String backInfo = "";
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(null == recipe){
            return "处方不存在";
        }
        if(skipHis(recipe)){
           return backInfo;
        }

        if (hisServiceConfigDAO.isHisEnable(recipe.getClinicOrgan())) {
            RecipeListQueryService service = AppContextHolder.getBean("recipeListQueryService", RecipeListQueryService.class);
            RecipeListQueryReq request = new RecipeListQueryReq(recipe.getRecipeCode(), recipe.getClinicOrgan());
            Integer status = service.recipeSingleQuery(request);
            //审核通过的处方才能点击
            if (Integer.valueOf(RecipeStatusConstant.CHECK_PASS).equals(status)) {

            }else{
                logger.error("recipeSingleQuery recipeId="+recipeId+" not check pass status!");
                if(null == status){
                    backInfo = "医院接口异常，请稍后再试！";
                }else{
                    backInfo = "处方单已处理！";
                }
            }
        } else {
            logger.error("recipeSingleQuery 医院HIS未启用[organId:" + recipe.getClinicOrgan() + ",recipeId:" + recipeId + "]");
            backInfo = "医院系统维护中！";
        }

        return backInfo;
    }

    /**
     * 从医院HIS获取药品信息
     * @param organId
     * @param searchAll true:查询该医院所有有效药品信息， false:查询限定范围内无效药品信息
     * @return
     */
    @RpcService
    public List<DrugInfo> getDrugInfoFromHis(int organId, boolean searchAll, int start){
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);

        if (hisServiceConfigDAO.isHisEnable(organId)) {
            DrugInfoSynService service = AppContextHolder.getBean("drugInfoSynService", DrugInfoSynService.class);
            OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

            List<DrugInfo> requestList = null;
            List<DrugInfo> backList = null;
            if(searchAll){
                backList = service.queryDrugInfo(requestList,organId);
            }else{
                requestList  = organDrugListDAO.findDrugInfoByOrganId(organId, start, 100);
                if(CollectionUtils.isNotEmpty(requestList)){
                    backList = service.queryDrugInfo(requestList,organId);
                }
            }

            return backList;
        } else {
            logger.error("getDrugInfoFromHis 医院HIS未启用[organId:" + organId +"]");
        }

        return null;
    }

    @RpcService
    public RecipeResultBean scanDrugStockByRecipeId(Integer recipeId)
    {
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        RecipeDetailDAO recipedetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);

        Recipe recipe = recipeDAO.getByRecipeId(recipeId);

        List<Recipedetail> detailList = recipedetailDAO.findByRecipeId(recipeId);

        return this.scanDrugStock(recipe,detailList);
    }


    /**
     * 检查医院库存
     * @return
     */
    @RpcService
    public RecipeResultBean scanDrugStock(Recipe recipe,List<Recipedetail> detailList){
        RecipeResultBean result = RecipeResultBean.getSuccess();
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

        if(null == recipe){
            result.setCode(RecipeResultBean.FAIL);
            result.setError("没有该处方");
            return result;
        }

        if(skipHis(recipe)){
            return result;
        }

        if(CollectionUtils.isEmpty(detailList)){
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方没有详情");
            return result;
        }

        if (hisServiceConfigDAO.isHisEnable(recipe.getClinicOrgan()))
        {
            ScanDrugService service = AppContextHolder.getBean("scanDrugService", ScanDrugService.class);
            List<Integer> emptyOrganCode = new ArrayList<>();
            for(Recipedetail detail : detailList)
            {
                if(StringUtils.isEmpty(detail.getOrganDrugCode()))
                {
                    emptyOrganCode.add(detail.getDrugId());
                }
            }
            if(CollectionUtils.isNotEmpty(emptyOrganCode))
            {
                logger.error("scanDrugStock 医院配置药品存在编号为空的数据. drugIdList={}", JSONUtils.toString(emptyOrganCode));
                result.setCode(RecipeResultBean.FAIL);
                result.setError("医院配置药品存在编号为空的数据");
                return result;
            }

            DrugInfoResponse response = service.scanDrugStock(detailList,recipe.getClinicOrgan());
            if(null == response)
            {
                //his未配置该服务则还是可以通过
//                result.setCode(RecipeResultBean.FAIL);
                result.setError("HIS返回为NULL");
            }
            else
            {
                if (Integer.valueOf(0).equals(response.getMsgCode()))
                {
                    //校验通过
                }
                else
                {
                    String organCodeStr = response.getMsg();
                    List<String> nameList = new ArrayList<>();
                    if (StringUtils.isNotEmpty(organCodeStr))
                    {
                        List<String> organCodes = Arrays.asList(organCodeStr.split(","));
                        nameList = organDrugListDAO.findNameByOrganIdAndDrugCodes(recipe.getClinicOrgan(), organCodes);
                    }
                    String showMsg = "由于" + Joiner.on(",").join(nameList) + "门诊药房库存不足，该处方仅支持配送，无法到院取药，是否继续？";
                    result.setCode(RecipeResultBean.FAIL);
                    result.setError(showMsg.toString());
                    result.setExtendValue("1");
                    logger.error("scanDrugStock 存在无库存药品. dao={} ", JSONUtils.toString(response));
                }
            }
        }
        else
        {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("医院HIS未启用。");
            logger.error("scanDrugStock 医院HIS未启用[organId:"+recipe.getClinicOrgan()+",recipeId:"+recipe.getRecipeId()+"]");
        }

        return result;
    }

    /**
     * 判断是否需要对接HIS
     * @param recipe
     * @return
     */
    private boolean skipHis(Recipe recipe){
        if(recipe.containTcmType()){
            //TODO 中药，膏方处方目前不需要对接HIS
            return true;
        }

        return false;
    }
}
