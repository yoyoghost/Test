package eh.cdr.service; 

import ctd.persistence.exception.DAOException;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeDAO;
import eh.entity.cdr.Recipe;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Before; 
import org.junit.After;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;


import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;



/** 
* RecipeService Tester. 
* 
* @author <Authors name> 
* @since <pre>Jul 7, 2017</pre> 
* @version 1.0 
*/ 

  
@RunWith(SpringJUnit4ClassRunner.class) 
@ContextConfiguration(locations={"classpath:spring-test.xml"})
public class RecipeServiceTest extends AbstractTransactionalJUnit4SpringContextTests{

@Before
public void before() throws Exception {
} 

@After
public void after() throws Exception { 
}

    @Resource
    private RecipeDAO recipeDAO;
    @Resource
    private RecipeService recipeService;


/** 
* 
* Method: openRecipeOrNot(Integer doctorId) 
* 医生是否可以开处方，判断条件：对于该医生任一执业机构如果配置有药品，则可以开处方
* 如果标记为可开医保处方，则医生可以开医保处方。
 * case1：医生执业机构没有配置药品，标记为可开医保处方
 * case2：医生执业机构没有配置药品，标记为不可开医保处方
 * case3：医生多执业机构有药品，标记为可开医保
 * case4：医生多执业机构有药品，标记为不可开医保
*/ 
@Test

public void testOpenRecipeOrNotCase1() throws Exception {
//TODO: Test goes here...
    Map<String, Object> reultMap = recipeService.openRecipeOrNot(8237);
    Map<String, Object> assertMap = new HashMap<String, Object>();
    assertMap.put("result",false);
    assertMap.put("medicalFlag",false);
    assertMap.put("tips","抱歉，您所在医院暂不支持开处方业务。");
    Assert.assertEquals(reultMap,assertMap);
}

    @Test
    public void testOpenRecipeOrNotCase2() throws Exception {
    //TODO: Test goes here...
        Map<String, Object> reultMap = recipeService.openRecipeOrNot(3837);
        Map<String, Object> assertMap = new HashMap<String, Object>();
        assertMap.put("result",false);
        assertMap.put("medicalFlag",false);
        assertMap.put("tips","抱歉，您所在医院暂不支持开处方业务。");
        Assert.assertEquals(reultMap,assertMap);
    }

    @Test
    public void testOpenRecipeOrNotCase3() throws Exception {
    //TODO: Test goes here...
        Map<String, Object> reultMap = recipeService.openRecipeOrNot(1177);
        Map<String, Object> assertMap = new HashMap<String, Object>();
        assertMap.put("result",true);
        assertMap.put("medicalFlag",false);
        assertMap.put("tips","");
        Assert.assertEquals(reultMap,assertMap);
    }

    @Test
    public void testOpenRecipeOrNotCase4() throws Exception {
    //TODO: Test goes here...
        Map<String, Object> reultMap = recipeService.openRecipeOrNot(110513);
        Map<String, Object> assertMap = new HashMap<String, Object>();
        assertMap.put("result",true);
        assertMap.put("medicalFlag",true);
        assertMap.put("tips","");
        Assert.assertEquals(reultMap,assertMap);
    }

    /**
* 
* Method: findNewRecipeAndPatient(int doctorId, int start, int limit) 
* 
*/ 
@Test
public void testFindNewRecipeAndPatient() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: findOldRecipeAndPatient(int doctorId, int start, int limit) 
* 
*/ 
@Test
public void testFindOldRecipeAndPatient() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: delRecipeForce(int recipeId) 
* 
*/ 
@Test
public void testDelRecipeForce() throws Exception{
//TODO: Test goes here...
    recipeService.delRecipeForce(10);
    Recipe recipe = recipeDAO.getByRecipeId(10);
    Assert.assertNull(recipe);
} 

/** 
* 
* Method: delRecipe(int recipeId) 
* Case1：正常删除处方单
 * Case2：不存在处方
 * Case3：不允许删除的处方
*/
@Rule
public ExpectedException thrown = ExpectedException.none();

@Test
public void testDelRecipeCase1() throws Exception {
//TODO: Test goes here...

    Recipe recipeOrigin = recipeDAO.getByRecipeId(25);
    recipeService.delRecipe(25);
    Recipe recipe = recipeDAO.getByRecipeId(25);
    Assert.assertEquals((Integer) RecipeStatusConstant.DELETE,recipe.getStatus());
//    recipeService.updateRecipeAndDetail(recipeOrigin,recipeDET)

}


    @Test
    public void testDelRecipeCase2() throws DAOException {
//TODO: Test goes here...
        thrown.expect(DAOException.class);
        thrown.expectMessage("该处方单不存在或者已删除");
        recipeService.delRecipe(1);
    }

    @Test
    public void testDelRecipeCase3() throws Exception {
//TODO: Test goes here...
        thrown.expect(DAOException.class);
        thrown.expectMessage("该处方单不是新处方或者审核失败的处方，不能删除");
        recipeService.delRecipe(6);
    }
    /**
* 
* Method: undoRecipe(int recipeId) 
* 
*/ 
@Test
public void testUndoRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: saveRecipeData(Recipe recipe, List<Recipedetail> details) 
* 
*/ 
@Test
public void testSaveRecipeData() throws Exception { 
//TODO: Test goes here...
} 

/** 
* 
* Method: saveRecipeDataForHos(Recipe recipe, List<Recipedetail> details) 
* 
*/ 
@Test
public void testSaveRecipeDataForHos() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: updateRecipeAndDetail(Recipe recipe, List<Recipedetail> recipedetails) 
* 
*/ 
@Test
public void testUpdateRecipeAndDetail() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: saveRecipeDocIndex(Recipe recipe) 
* 
*/ 
@Test
public void testSaveRecipeDocIndex() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getCompleteAddress(Integer recipeId) 
* 
*/ 
@Test
public void testGetCompleteAddress() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: reviewRecipe(Map<String, Object> paramMap) 
* 
*/ 
@Test
public void testReviewRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: reCreatedRecipe(Integer recipeId) 
* 
*/ 
@Test
public void testReCreatedRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: validateDrugs(Integer recipeId) 
* 
*/ 
@Test
public void testValidateDrugs() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: generateRecipePdfAndSign(Integer recipeId) 
* 
*/ 
@Test
public void testGenerateRecipePdfAndSign() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: sendNewRecipeToHIS(Integer recipeId) 
* 
*/ 
@Test
public void testSendNewRecipeToHIS() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: sendDistributionRecipe(Recipe recipe, List<Recipedetail> details) 
* 
*/ 
@Test
public void testSendDistributionRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: doSignRecipe(Recipe recipe, List<Recipedetail> details) 
* 
*/ 
@Test
public void testDoSignRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: doSignRecipeExt(Recipe recipe, List<Recipedetail> details) 
* 
*/ 
@Test
public void testDoSignRecipeExt() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: doSecondSignRecipe(Recipe recipe) 
* 
*/ 
@Test
public void testDoSecondSignRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: afterCheckPassYs(Recipe recipe) 
* 
*/ 
@Test
public void testAfterCheckPassYs() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: afterCheckNotPassYs(Recipe recipe) 
* 
*/ 
@Test
public void testAfterCheckNotPassYs() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: sendRecipeTagToPatient(Recipe recipe, List<Recipedetail> details, Map<String, Object> rMap) 
* 
*/ 
@Test
public void testSendRecipeTagToPatient() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: recipeAutoCheck(Integer recipeId) 
* 
*/ 
@Test
public void testRecipeAutoCheck() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getTipsByStatus(int status, Recipe recipe, boolean effective) 
* 
*/ 
@Test
public void testGetTipsByStatus() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getTipsByStatusForPatient(Recipe recipe, RecipeOrder order) 
* 
*/ 
@Test
public void testGetTipsByStatusForPatient() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: findRecipeAndDetailById(int recipeId) 
* 
*/ 
@Test
public void testFindRecipeAndDetailById() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: cancelRecipe(Integer recipeId) 
* 
*/ 
@Test
public void testCancelRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: cancelRecipeForOperator(Integer recipeId, String name, String message) 
* 
*/ 
@Test
public void testCancelRecipeForOperator() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: cancelRecipeImpl(Integer recipeId, Integer flag, String name, String message) 
* 
*/ 
@Test
public void testCancelRecipeImpl() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: drugInfoSynTask(Integer organId) 
* 
*/ 
@Test
public void testDrugInfoSynTask() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: cancelRecipeTask() 
* 
*/ 
@Test
public void testCancelRecipeTask() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: remindRecipeTask() 
* 
*/ 
@Test
public void testRemindRecipeTask() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: afterCheckNotPassYsTask() 
* 
*/ 
@Test
public void testAfterCheckNotPassYsTask() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getRecipeStatusFromHis() 
* 
*/ 
@Test
public void testGetRecipeStatusFromHis() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: updateDrugsEnterpriseToken() 
* 
*/ 
@Test
public void testUpdateDrugsEnterpriseToken() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getPatientRecipeById(int recipeId) 
* 
*/ 
@Test
public void testGetPatientRecipeById() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getRecipePayMode(int recipeId, int flag) 
* 
*/ 
@Test
public void testGetRecipePayMode() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: supportTakeMedicine(Integer recipeId, Integer clinicOrgan) 
* 
*/ 
@Test
public void testSupportTakeMedicine() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: supportDistributionExt(Integer recipeId, Integer clinicOrgan, Map<Integer, Set<Integer>> map, Integer payMode) 
* 
*/ 
@Test
public void testSupportDistributionExt() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: applyMedicalInsurancePayForRecipe(Integer recipeId) 
* 
*/ 
@Test
public void testApplyMedicalInsurancePayForRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getDrugsEpsIdByOrganId(Integer recipeId, Integer payMode) 
* 
*/ 
@Test
public void testGetDrugsEpsIdByOrganId() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: manualRefundForRecipe(int recipeId, String operName, String reason) 
* 
*/ 
@Test
public void testManualRefundForRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: wxPayRefundForRecipe(int flag, int recipeId, String log) 
* 
*/ 
@Test
public void testWxPayRefundForRecipe() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: updateDrugPrice(Integer organId, Map<Integer,BigDecimal> priceMap) 
* 
*/ 
@Test
public void testUpdateDrugPrice() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: setPatientMoreInfo(Patient patient, int doctorId, RelationPatientDAO dao, RelationLabelDAO labelDAO) 
* 
*/ 
@Test
public void testSetPatientMoreInfo() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: convertRecipeForRAP(Recipe recipe) 
* 
*/ 
@Test
public void testConvertRecipeForRAP() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: convertPatientForRAP(Patient patient) 
* 
*/ 
@Test
public void testConvertPatientForRAP() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getAllMemberPatientsByCurrentPatient(String mpiId) 
* 
*/ 
@Test
public void testGetAllMemberPatientsByCurrentPatient() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getHomePageTaskForPatient(String mpiid) 
* 
*/ 
@Test
public void testGetHomePageTaskForPatient() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getUnSendTitleForPatient(Recipe recipe) 
* 
*/ 
@Test
public void testGetUnSendTitleForPatient() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getRecipeGetModeTip(Recipe recipe) 
* 
*/ 
@Test
public void testGetRecipeGetModeTip() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: updateRecipePayResultImplForOrder(boolean saveFlag, Integer recipeId, Integer payFlag, Map<String, Object> info) 
* 
*/ 
@Test
public void testUpdateRecipePayResultImplForOrder() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: getRecipeSurplusHours(Date signDate) 
* 
*/ 
@Test
public void testGetRecipeSurplusHours() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: searchRecipeStatusFromHis(Integer recipeId, int modelFlag) 
* 
*/ 
@Test
public void testSearchRecipeStatusFromHis() throws Exception { 
//TODO: Test goes here... 
} 

/** 
* 
* Method: updatePatientInfoForRecipe(Patient newPat, String oldMpiId) 
* 
*/ 
@Test
public void testUpdatePatientInfoForRecipe() throws Exception { 
//TODO: Test goes here... 
} 


/** 
* 
* Method: saveRecipeDataImpl(Recipe recipe, List<Recipedetail> details, Integer flag) 
* 
*/ 
@Test
public void testSaveRecipeDataImpl() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("saveRecipeDataImpl", Recipe.class, List<Recipedetail>.class, Integer.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: validateDrugsImpl(Recipe recipe) 
* 
*/ 
@Test
public void testValidateDrugsImpl() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("validateDrugsImpl", Recipe.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: createParamMap(Recipe recipe, List<Recipedetail> details, String fileName) 
* 
*/ 
@Test
public void testCreateParamMap() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("createParamMap", Recipe.class, List<Recipedetail>.class, String.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: createParamMapForChineseMedicine(Recipe recipe, List<Recipedetail> details, String fileName) 
* 
*/ 
@Test
public void testCreateParamMapForChineseMedicine() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("createParamMapForChineseMedicine", Recipe.class, List<Recipedetail>.class, String.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: getWxAppIdForRecipeFromOps(Integer recipeId, Integer busOrgan) 
* 
*/ 
@Test
public void testGetWxAppIdForRecipeFromOps() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("getWxAppIdForRecipeFromOps", Integer.class, Integer.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: getOptimalDrugsEpId(Integer recipeId, Integer organId, Integer payMode) 
* 
*/ 
@Test
public void testGetOptimalDrugsEpId() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("getOptimalDrugsEpId", Integer.class, Integer.class, Integer.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: getDepSupportMode(Integer payMode) 
* 
*/ 
@Test
public void testGetDepSupportMode() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("getDepSupportMode", Integer.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: findRecipesAndPatientsByDoctor(final int doctorId, final int start, final int limit, final int mark) 
* 
*/ 
@Test
public void testFindRecipesAndPatientsByDoctor() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("findRecipesAndPatientsByDoctor", final.class, final.class, final.class, final.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: assembleQueryStatusFromHis(List<Recipe> list, Map<Integer,List<String>> map) 
* 
*/ 
@Test
public void testAssembleQueryStatusFromHis() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("assembleQueryStatusFromHis", List<Recipe>.class, Map<Integer,List<String>>.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: checkRecipeCommonInfo(Integer recipeId, RecipeResultBean resultBean) 
* 
*/ 
@Test
public void testCheckRecipeCommonInfo() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("checkRecipeCommonInfo", Integer.class, RecipeResultBean.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: validateSaveRecipeData(Recipe recipe) 
* 
*/ 
@Test
public void testValidateSaveRecipeData() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("validateSaveRecipeData", Recipe.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: validateRecipeDetailData(Recipedetail detail, Recipe recipe) 
* 
*/ 
@Test
public void testValidateRecipeDetailData() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("validateRecipeDetailData", Recipedetail.class, Recipe.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: setDetailsInfo(Recipe recipe, List<Recipedetail> recipedetails) 
* 
*/ 
@Test
public void testSetDetailsInfo() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("setDetailsInfo", Recipe.class, List<Recipedetail>.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: getRecipeAndDetailByIdImpl(int recipeId, boolean isDoctor) 
* 
*/ 
@Test
public void testGetRecipeAndDetailByIdImpl() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("getRecipeAndDetailByIdImpl", int.class, boolean.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: isCanSupportPayOnline(String wxAccount, int hisStatus) 
* 
*/ 
@Test
public void testIsCanSupportPayOnline() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("isCanSupportPayOnline", String.class, int.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

/** 
* 
* Method: registerEsign(String eSignId, EsignPerson person) 
* 
*/ 
@Test
public void testRegisterEsign() throws Exception { 
//TODO: Test goes here... 
/* 
try { 
   Method method = RecipeService.getClass().getMethod("registerEsign", String.class, EsignPerson.class); 
   method.setAccessible(true); 
   method.invoke(<Object>, <Parameters>); 
} catch(NoSuchMethodException e) { 
} catch(IllegalAccessException e) { 
} catch(InvocationTargetException e) { 
} 
*/ 
} 

} 
