package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.his.service.RecipeStatusUpdateService;
import eh.entity.cdr.Recipe;
import eh.entity.his.RecipeStatusUpdateReq;
import eh.entity.mpi.Patient;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by zhongzx on 2016/7/13 0013.
 * 用于测试处方流程
 */
public class RecipeTestService {

    /**
     * 把指定的处方单在医院的状态置成已完成
     * @param recipeId
     * @return
     */
    @RpcService
    public boolean setHisEnd(Integer recipeId){
        RecipeDAO rdao = DAOFactory.getDAO(RecipeDAO.class);
        PatientDAO pdao = DAOFactory.getDAO(PatientDAO.class);
        Recipe recipe = rdao.get(recipeId);
        if(null == recipe){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipe is null");
        }
        Patient patient = pdao.get(recipe.getMpiid());
        if(null == patient){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "patient is null");
        }
        RecipeStatusUpdateService service = new RecipeStatusUpdateService();
        RecipeStatusUpdateReq req = new RecipeStatusUpdateReq(recipe, null, patient, null);
        //状态置为完成
        req.setRecipeStatus("1");
        return service.recipeStatusUpdate(req);
    }

    @RpcService
    public int checkPassFail(Integer recipeId, Integer errorCode, String msg){
        HisCallBackService.checkPassFail(recipeId,errorCode,msg);
        return 0;
    }

    /**
     * 测试用-将处方单改成已完成状态
     */
    @RpcService
    public int changeRecipeToFinish(String recipeCode,int organId){
        HisCallBackService.finishRecipesFromHis(Arrays.asList(recipeCode),organId);
        return 0;
    }

    @RpcService
    public int changeRecipeToPay(String recipeCode,int organId){
        HisCallBackService.havePayRecipesFromHis(Arrays.asList(recipeCode),organId);
        return 0;
    }

    @RpcService
    public int changeRecipeToHisFail(Integer recipeId){
        HisCallBackService.havePayFail(recipeId);
        return 0;
    }

    @RpcService
    public void testSendMsg(String bussType,Integer bussId,Integer organId){
        SmsInfo info = new SmsInfo();
        info.setBusId(bussId);// 业务表主键
        info.setBusType(bussType);// 业务类型
        info.setSmsType(bussType);
        info.setStatus(0);
        info.setOrganId(organId);// 短信服务对应的机构， 0代表通用机构
        info.setExtendValue("康复药店");
        info.setExtendWithoutPersist(JSONUtils.toString(Arrays.asList("2c9081814d720593014d758dd0880020")));
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    @RpcService
    public void testSendMsgForRecipe(Integer recipeId, int afterStatus){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        RecipeMsgService.batchSendMsg(recipe, afterStatus);
    }

    @RpcService(timeout = 1000)
    public Map<String, Object> analysisDrugList(List<Integer> drugIdList, int organId, boolean useFile){
        DrugsEnterpriseTestService testService = new DrugsEnterpriseTestService();
        try {
            return testService.analysisDrugList(drugIdList,organId,useFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
