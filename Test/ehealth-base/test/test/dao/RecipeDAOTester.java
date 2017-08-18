package test.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.mvc.upload.exception.FileRegistryException;
import ctd.mvc.upload.exception.FileRepositoryException;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.dao.RecipeDetailDAO;
import eh.cdr.his.service.*;
import eh.cdr.service.RecipeCheckService;
import eh.cdr.service.RecipeService;
import eh.entity.base.Doctor;
import eh.entity.cdr.Recipe;
import eh.entity.cdr.Recipedetail;
import eh.entity.his.*;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecipeDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	static {
		appContext = new ClassPathXmlApplicationContext("spring.xml");
	}
	private static RecipeService dao = appContext.getBean("recipeService",
            RecipeService.class);

	public void testFindNewRecipeAndPatient() {
		int doctor = 1292;
		int start = 0;
		System.out.println(JSONUtils.toString(dao.findNewRecipeAndPatient(
				doctor, start,6)));
	}
	
	public void testFindOldRecipeAndPatient() {
		int doctor = 1292;
		int start = 0;
		System.out.println(JSONUtils.toString(dao.findOldRecipeAndPatient(
				doctor, start,6)));
	}
	
	public void testFindRecipeAndDetailById() {
		int recipeId = 393;
		HashMap<String, Object> map = dao.findRecipeAndDetailById(recipeId);
		System.out.println(JSONUtils.toString(map));
	}

    public void testCancelRecipeTask(){
        dao.cancelRecipeTask();
    }
	
	public void testUpdateRecipeAndDetail() {
		Recipe recipe = new Recipe();
		Recipedetail recipedetail = new Recipedetail();
		List<Recipedetail> recipedetails = new ArrayList<Recipedetail>();
		recipe.setRecipeId(3);
		recipe.setAddress1("ashjdlkag");
		recipe.setAddress2("ajhgk");

		recipedetail.setRecipeId(3);
		recipedetail.setDrugGroup("0");
		recipedetail.setDrugId(0);
//		recipedetail.setOrganDrugId("110393670");
		recipedetail.setDrugName("聚维酮碘溶液5%100ml*1");
		recipedetail.setDrugSpec("5%100ml");
		recipedetail.setDrugUnit("BOT");
		recipedetail.setUseDose((double) 100);
		recipedetail.setUseDoseUnit("ml");
		recipedetail.setUsingRate("QD");
		recipedetail.setUsePathways("AP");
		recipedetail.setUseTotalDose((double) 0);
		recipedetail.setSendNumber((double) 0);
		recipedetail.setUseDays(0);
//		recipedetail.setDrugCost(6.73);
		recipedetails.add(recipedetail);
		dao.updateRecipeAndDetail(recipe, recipedetails);
	}
	
	/**
	 * 新增处方
	 * @author zhangx
	 * @date 2015-12-5 下午4:36:44
	 */
	public void testSaveRecipe() {
		Recipe p = new Recipe();
		p.setMpiid("40288937594da14a01594e0ec7500000");
		p.setOrganDiseaseName("疾病名称");
        p.setOrganDiseaseId("111");
        p.setRecipeType(1);
		p.setClinicOrgan(1);
		p.setDepart(70);
		p.setDoctor(1292);
		p.setRecMobile("13738049559");
		p.setReceiver("123切克闹");
		p.setAddress1("33");
		p.setAddress2("08");
		p.setAddress3("01");
		p.setAddress4("sss");

		RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
		List<Recipedetail> details = detailDAO.findByRecipeId(127);

		
		dao.saveRecipeData(p, details);
	}
	
	/**
	 * 删除处方及处方明细
	 * @author zhangx
	 * @date 2015-12-5 下午4:36:21
	 */
	public void testDelRecipe(){
		System.out.println(dao.delRecipe(99));
	}
	

	/**
	 * 撤销处方单
	 * @author zhangx
	 * @date 2015-12-9 下午3:02:11
	 */
	public void testUndoRecipe(){
		dao.undoRecipe(99);
	}
	
	/**
	 * 审核处方单
	 * @author zhangx
	 * @date 2015-12-9 下午3:02:39
	 */
	/*public void testReviewRecipe(){
		dao.reviewRecipe(99,2,1,1,"xxxxxx");
	}*/
	/**
	 * @throws FileRegistryException 
	 * @throws FileRepositoryException 
	 * @throws FileNotFoundException 
	 * @throws ControllerException 
	 * 
	*
	* @Class test.dao.RecipeDAOTester.java
	*
	* @Title: testSign
	
	* @Description: TODO测试签名
	
	* @param     
	
	* @author AngryKitty
	
	* @Date 2015-12-14上午10:20:48 
	
	* @return void   
	
	* @throws
	 */
/*	public void testSign() throws ControllerException, FileNotFoundException, FileRepositoryException, FileRegistryException {
		dao.doSignRecipe(48);
		try {
			TimeUnit.SECONDS.sleep(10);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}*/
	/**
	 * 调用国药接口
	 * @author xiebz
	 * @throws IOException 
	 * @Date 2015-12-30下午8:25:00
	 */
	public void testSetData() throws IOException{
		RecipeDAO dao = DAOFactory.getDAO(RecipeDAO.class);
		Recipe recipe = dao.getByRecipeId(46);
//		System.out.println(dao.setData(recipe));
	}
		
	/**
	 * @throws IOException 
	 * 获取access_token
	 * @author xiebz
	 * @Date 2016-1-20 14:20:00
	 */
	public void testGetAccessToken() throws IOException{
		RecipeDAO dao = DAOFactory.getDAO(RecipeDAO.class);
//		System.out.println(dao.getAccessToken("nljk", "nljk"));
	}
	
	/**
	 * @throws ParseException 
	 * startSend接口
	 * @author xiebz
	 * @Date 2016-1-20 15:26:00
	 */
	public void testStartSend() throws ParseException{
		RecipeDAO dao = DAOFactory.getDAO(RecipeDAO.class);
		HashMap<String, Object> hashMap = new HashMap<>();
		hashMap.put("Recipeid", "78");
		hashMap.put("Recipecode", "");
		hashMap.put("SendDate", "");
		hashMap.put("Sender", "Jim");
		List<Map<String, Object>> l = new ArrayList<>();
		Map<String, Object> m = new HashMap<>();
		m.put("dtlid", "74");
		m.put("goodid", "1");
		m.put("DrugBatch", "");
		m.put("ValidDate", "");
		m.put("qty", 10.00);
		m.put("price", 20.00);
		m.put("Rate", 0.1);
		m.put("RatePrice", 18.0);
		l.add(m);
		hashMap.put("dtl", l);
//		dao.startSend(hashMap);
	}
	
	/**
	 * @throws ParseException 
	 * updateSendStatus接口
	 * @author xiebz
	 * @Date 2016-1-20 15:38:00
	 */
	public void testUpdateSendStatus() throws ParseException{
		RecipeDAO dao = DAOFactory.getDAO(RecipeDAO.class);
		HashMap<String, Object> hashMap = new HashMap<>();
		hashMap.put("Recipeid", "74");
		hashMap.put("Recipecode", "");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		hashMap.put("SendDate", sdf.format(new Date()));
		hashMap.put("SendStatus", "2");
//		dao.updateSendStatus(hashMap);
	}

	//向his发送处方
	public void testSendNewRecipe() throws InterruptedException {
		/*dao.sendNewRecipeToHIS(964);
		TimeUnit.SECONDS.sleep(1000);*/
	}

	//his 支付通知
	public void testPayNotify(){

	}

	//his 更改取药方式
	public void testDrugTake(){
		//获取必要参数
		RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
		RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
		PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
		HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);

		Recipe recipe = recipeDAO.getByRecipeId(127);
		List<Recipedetail> details = detailDAO.findByRecipeId(127);
		Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
		HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");

		DrugTakeUpdateService service = new DrugTakeUpdateService();
		System.out.println(service.drugTakeUpdate(new DrugTakeChangeReq(recipe, details, patient, card)));

	}

	//his 处方状态更新
	public void testRecipeUpdate(){
		//获取必要参数
		RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
		RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
		PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
		HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);

		Recipe recipe = recipeDAO.getByRecipeId(127);
		List<Recipedetail> details = detailDAO.findByRecipeId(127);
		Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
		HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");

		RecipeStatusUpdateService service = new RecipeStatusUpdateService();
		System.out.println(service.recipeStatusUpdate(new RecipeStatusUpdateReq(recipe, details, patient, card)));

	}

	public void testRecipeStatusUpdate(){
		dao.cancelRecipeTask();
	}

	//his 处方列表查询
	public void testRecipeList(){
		//获取必要参数

		List<String> list = new ArrayList<>();
		list.add("121");
		list.add("122");
		RecipeListQueryService service = new RecipeListQueryService();
		service.recipeListQuery(new RecipeListQueryReq(list, 1));

	}

	public void testNull(){
		System.out.println("123"+10000);
	}

	public void testSign() throws FileRepositoryException, FileNotFoundException, FileRegistryException, ControllerException {
		RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
		RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
		Recipe recipe = recipeDAO.getByRecipeId(127);
		List<Recipedetail> details = detailDAO.findByRecipeId(127);
		RecipeService service = new RecipeService();
		service.doSignRecipe(recipe, details);
	}

	public void testRecipeQuery(){
		RecipeQueryHisSerivce serivce = new RecipeQueryHisSerivce();
		RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
		PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
		HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);

		Recipe recipe = recipeDAO.getByRecipeId(127);
		Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
		HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");
		serivce.recipeQueryHis(new RecipeQueryReq(recipe, patient, card));
	}

	public void testDetailQuery(){
		RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
		PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
		HealthCardDAO healthCardDAO = DAOFactory.getDAO(HealthCardDAO.class);

		Recipe recipe = recipeDAO.getByRecipeId(127);
		Patient patient = patientDAO.getPatientByMpiId(recipe.getMpiid());
		HealthCard card = healthCardDAO.getByThree(recipe.getMpiid(),recipe.getClinicOrgan(),"2");
		DetailQueryHisService service = new DetailQueryHisService();
		service.detailQueryHis(new DetailQueryReq(recipe, patient, card));
	}

	public void testException(){
		try{
			Doctor d = null;
			System.out.println(d.getName());
		}
		catch (Exception e){
			System.out.println("-================="+e.getMessage());
		}
		System.out.println("----------------");
	}

	public void testcheck(){
		RecipeCheckService service = new RecipeCheckService();
		System.out.println(JSONUtils.toString(service.findRecipeAndDetailsAndCheckById(1015)));
	}

	public void testPage(){
		RecipeCheckService service = new RecipeCheckService();
		System.out.println(JSONUtils.toString(service.findRecipeListWithPage(1,0,9,5)));
	}

	public void testDic(){
		try {
			String appointName = DictionaryController.instance()
					.get("eh.base.dictionary.Chemist")
					.getText(2);
			System.out.println(appointName);
		}
		catch (Exception e){

		}
	}

	public void test(){
		RecipeCheckService service = appContext.getBean("recipeCheckService", RecipeCheckService.class);
		System.out.println(JSONUtils.toString(service.findRecipeListWithPage(1,0,0,3)));
	}

	public void testCancelRecipe(){
//		测试处方撤销接口
//		dao.cancelRecipe(1250);
	}
}
