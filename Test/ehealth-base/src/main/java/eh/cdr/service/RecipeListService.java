package eh.cdr.service;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganDAO;
import eh.base.dao.RelationLabelDAO;
import eh.base.dao.RelationPatientDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.cdr.bean.PatientRecipeBean;
import eh.cdr.bean.RecipeResultBean;
import eh.cdr.constant.OrderStatusConstant;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.dao.RecipeDetailDAO;
import eh.cdr.dao.RecipeOrderDAO;
import eh.entity.base.Doctor;
import eh.entity.cdr.Recipe;
import eh.entity.cdr.RecipeOrder;
import eh.entity.cdr.RecipeRollingInfo;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;
import eh.utils.MapValueUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.*;


/**
 * 处方业务一些列表查询
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2017/2/13.
 */
public class RecipeListService {

    private static final Logger logger = LoggerFactory.getLogger(RecipeListService.class);

    public static final String LIST_TYPE_RECIPE = "1";

    public static final String LIST_TYPE_ORDER = "2";

    /**
     * 医生端处方列表展示
     * @param doctorId 医生ID
     * @param recipeId 上一页最后一条处方ID，首页传0
     * @param limit 每页限制数
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findRecipesForDoctor(Integer doctorId, Integer recipeId, Integer limit) {
        Assert.notNull(doctorId,"findRecipesForDoctor doctor is null.");
        recipeId = (null == recipeId || 0 == recipeId)?Integer.MAX_VALUE:recipeId;

        List<Map<String, Object>> list = new ArrayList<>(0);
        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        RelationPatientDAO relationPatientDAO = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);

        List<Recipe> recipeList = recipeDAO.findRecipesForDoctor(doctorId, recipeId, 0, limit);
        logger.info("findRecipesForDoctor recipeList size={}", recipeList.size());
        if(CollectionUtils.isNotEmpty(recipeList)){
            List<String> patientIds = new ArrayList<>(0);
            Map<Integer,Recipe> recipeMap = new HashMap<>();
            for (Recipe recipe : recipeList) {
                if(StringUtils.isNotEmpty(recipe.getMpiid())) {
                    patientIds.add(recipe.getMpiid());
                }
                //设置处方具体药品名称
                recipe.setRecipeDrugName(recipeDetailDAO.getDrugNamesByRecipeId(recipe.getRecipeId()));
                //前台页面展示的时间源不同
                recipe.setRecipeShowTime(recipe.getCreateDate());
                boolean effective = false;
                //只有审核未通过的情况需要看订单状态
                if(RecipeStatusConstant.CHECK_NOT_PASS_YS == recipe.getStatus()){
                    effective = orderDAO.isEffectiveOrder(recipe.getOrderCode(),recipe.getPayMode());
                }
                Map<String, String> tipMap = recipeService.getTipsByStatus(recipe.getStatus(), recipe, effective);
                recipe.setShowTip(MapValueUtil.getString(tipMap, "listTips"));
                recipeMap.put(recipe.getRecipeId(),recipeService.convertRecipeForRAP(recipe));
            }

            Map<String,Patient> patientMap = new HashMap<>();
            if(CollectionUtils.isNotEmpty(patientIds)) {
                List<Patient> patientList = patientDAO.findByMpiIdIn(patientIds);
                if(CollectionUtils.isNotEmpty(patientList)){
                    for(Patient patient : patientList){
                        //设置患者数据
                        recipeService.setPatientMoreInfo(patient,doctorId,relationPatientDAO,labelDAO);
                        patientMap.put(patient.getMpiId(),recipeService.convertPatientForRAP(patient));
                    }
                }
            }

            for (Recipe recipe : recipeList) {
                String mpiId = recipe.getMpiid();
                HashMap<String, Object> _map = new HashMap<>();
                _map.put("recipe", recipeMap.get(recipe.getRecipeId()));
                _map.put("patient", patientMap.get(mpiId));
                list.add(_map);
            }
        }

        return list;
    }

    /**
     *健康端获取待处理中最新的一单处方单
     * @param mpiId 患者ID
     * @return
     */
    @RpcService
    public Map<String, Object> getLastestPendingRecipe(String mpiId) {
        Assert.hasLength(mpiId,"getLastestPendingRecipe mpiId is null.");

        HashMap<String, Object> map = new HashMap<>();
        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        List<String> allMpiIds = recipeService.getAllMemberPatientsByCurrentPatient(mpiId);
        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS,0,1);
        String title;
        String recipeGetModeTip = "";
        if(CollectionUtils.isNotEmpty(recipeIds)){
            title = "赶快结算您的处方单吧！";
            List<Map> recipesMap = new ArrayList<>(0);
            for(Integer recipeId : recipeIds) {
                Map<String, Object> recipeInfo = recipeService.getPatientRecipeById(recipeId);
                recipeGetModeTip = MapValueUtil.getString(recipeInfo,"recipeGetModeTip");
                recipesMap.add(recipeInfo);
            }

            map.put("recipes",recipesMap);
        }else{
            title = "暂无待处理处方单";
        }

        List<PatientRecipeBean> otherRecipes = this.findOtherRecipesForPatient(mpiId,0,1);
        if(CollectionUtils.isNotEmpty(otherRecipes)){
            map.put("haveFinished",true);
        }else{
            map.put("haveFinished",false);
        }

        map.put("title",title);
        map.put("unSendTitle", ParamUtils.getParam(ParameterConstant.KEY_RECIPE_UNSEND_TIP));
        map.put("recipeGetModeTip", recipeGetModeTip);

        return map;
    }

    @RpcService
    public List<PatientRecipeBean> findOtherRecipesForPatient(String mpiId, Integer index, Integer limit) {
        Assert.hasLength(mpiId,"findOtherRecipesForPatient mpiId is null.");
        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        List<String> allMpiIds = recipeService.getAllMemberPatientsByCurrentPatient(mpiId);
        //获取待处理那边最新的一单
        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS,0,1);
        List<PatientRecipeBean> backList = recipeDAO.findOtherRecipesForPatient(allMpiIds,recipeIds,index,limit);
        processListDate(backList, allMpiIds);
        return backList;
    }

    /**
     * 获取所有处方单信息
     * @param mpiId
     * @param index
     * @param limit
     * @return
     */
    @RpcService
    public List<PatientRecipeBean> findAllRecipesForPatient(String mpiId, Integer index, Integer limit) {
        Assert.hasLength(mpiId,"findAllRecipesForPatient mpiId is null.");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

//        List<String> allMpiIds = recipeService.getAllMemberPatientsByCurrentPatient(mpiId);
        List<String> allMpiIds = Arrays.asList(mpiId);
        //获取待处理那边最新的一单
//        List<Integer> recipeIds = recipeDAO.findPendingRecipes(allMpiIds, RecipeStatusConstant.CHECK_PASS,0,1);
        List<PatientRecipeBean> backList = recipeDAO.findOtherRecipesForPatient(allMpiIds,null,index,limit);
        processListDate(backList, allMpiIds);
        return backList;
    }

    /**
     * 处理列表数据
     * @param backList
     * @param allMpiIds
     */
    private void processListDate(List<PatientRecipeBean> backList, List<String> allMpiIds){
        RecipeService recipeService = AppContextHolder.getBean("recipeService", RecipeService.class);

        if(CollectionUtils.isNotEmpty(backList)) {
            //处理订单类型数据
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            List<Patient> patientList = patientDAO.findByMpiIdIn(allMpiIds);
            Map<String, Patient> patientMap = new HashMap<>();
            if (null != patientList && !patientList.isEmpty()) {
                for (Patient p : patientList) {
                    if (StringUtils.isNotEmpty(p.getMpiId())) {
                        patientMap.put(p.getMpiId(), p);
                    }
                }
            }

            Patient p;
            for (PatientRecipeBean record : backList) {
                p = patientMap.get(record.getMpiId());
                if (null != p) {
                    record.setPatientName(p.getPatientName());
                    record.setPhoto(p.getPhoto());
                    record.setPatientSex(p.getPatientSex());
                }
                if (LIST_TYPE_RECIPE.equals(record.getRecordType())) {
                    record.setStatusText(getRecipeStatusText(record.getStatusCode()));
                    //设置失效时间
                    if (RecipeStatusConstant.CHECK_PASS == record.getStatusCode()) {
                        record.setRecipeSurplusHours(recipeService.getRecipeSurplusHours(record.getSignDate()));
                    }
                    //药品详情
                    record.setRecipeDetail(detailDAO.findByRecipeId(record.getRecordId()));
                } else if (LIST_TYPE_ORDER.equals(record.getRecordType())) {
                    RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);
                    record.setStatusText(getOrderStatusText(record.getStatusCode()));
                    RecipeResultBean resultBean = orderService.getOrderDetailById(record.getRecordId());
                    if (RecipeResultBean.SUCCESS.equals(resultBean.getCode())) {
                        if (null != resultBean.getObject() && resultBean.getObject() instanceof RecipeOrder) {
                            RecipeOrder order = (RecipeOrder) resultBean.getObject();
                            List<PatientRecipeBean> recipeList = (List<PatientRecipeBean>) order.getList();
                            if (CollectionUtils.isNotEmpty(recipeList)) {
                                //TODO 前端要求，先去掉数组形式，否则前端不好处理
//                                List<PatientRecipeBean> subList = new ArrayList<>(5);
//                                PatientRecipeBean _bean;
                                for (PatientRecipeBean recipe : recipeList) {
//                                    _bean = new PatientRecipeBean();
//                                    _bean.setRecordType(LIST_TYPE_RECIPE);
                                    //TODO 当前订单只有一个处方，处方内的患者信息使用订单的信息就可以
//                                    _bean.setPatientName(record.getPatientName());
//                                    _bean.setPhoto(record.getPhoto());
//                                    _bean.setPatientSex(record.getPatientSex());

                                    record.setRecipeId(recipe.getRecipeId());
                                    record.setRecipeType(recipe.getRecipeType());
                                    record.setOrganDiseaseName(recipe.getOrganDiseaseName());
                                    // 订单支付方式
                                    record.setPayMode(recipe.getPayMode());
                                    //药品详情
                                    record.setRecipeDetail(recipe.getRecipeDetail());
//                                    _bean.setSignDate(recipe.getSignDate());
                                    if (RecipeStatusConstant.CHECK_PASS == recipe.getStatusCode()
                                            && OrderStatusConstant.READY_PAY.equals(record.getStatusCode())) {
                                        record.setRecipeSurplusHours(recipe.getRecipeSurplusHours());
                                    }
//                                    subList.add(_bean);
                                }

//                                record.setRecipeList(subList);
                            }
                        }
                    }
                }
            }
        }

        if(null == backList){
            backList = new ArrayList<>(0);
        }
    }

    /**
     * 获取最新开具的处方单前limit条，用于跑马灯显示
     * @param limit
     * @return
     */
    @RpcService
    public List<RecipeRollingInfo> findLastesRecipeList(int limit){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);


        OrganDAO organdao = DAOFactory.getDAO(OrganDAO.class);

        List<Integer> organs = organdao.findOrgansByUnitForHealth();
        String endDt = DateTime.now().toString(DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateTime.now().minusMonths(3).toString(DateConversion.DEFAULT_DATE_TIME);
        List<RecipeRollingInfo> list = recipeDAO.findLastesRecipeList(startDt, endDt,organs, 0, limit);

        // 个性化微信号医院没有开方医生不展示
        if(list.isEmpty())
        {
            return list;
        }
        List<String> mpiIdList = new ArrayList<>();
        for(RecipeRollingInfo info : list){
            mpiIdList.add(info.getMpiId());
        }

        List<Patient> patientList = patientDAO.findByMpiIdIn(mpiIdList);
        Map<String, Patient> patientMap = Maps.uniqueIndex(patientList, new Function<Patient, String>() {
            @Override
            public String apply(Patient input) {
                return input.getMpiId();
            }
        });

        Patient patient;
        for(RecipeRollingInfo info : list){
            patient = patientMap.get(info.getMpiId());
            if(null != patient) {
                info.setPatientName(patient.getPatientName());
            }
        }

        return list;
    }

    /**
     * 处方患者端主页展示推荐医生 (样本采集数量在3个月内)
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<Doctor> findDoctorByCount(int start, int limit){

        OrganDAO organdao = DAOFactory.getDAO(OrganDAO.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        // 个性化微信号医院没有开方医生不展示
        List<Integer> organs = organdao.findOrgansByUnitForHealth();
        String endDt = DateTime.now().toString(DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateTime.now().minusMonths(3).toString(DateConversion.DEFAULT_DATE_TIME);
        return recipeDAO.findDoctorByCount(startDt, endDt,organs, start, limit);
    }

    private String getOrderStatusText(Integer status){
        String msg = "未知";
        if(OrderStatusConstant.FINISH.equals(status)){
            msg = "已完成";
        }else if(OrderStatusConstant.READY_PAY.equals(status)){
            msg = "待支付";
        }else if(OrderStatusConstant.READY_GET_DRUG.equals(status)){
            msg = "待取药";
        }else if(OrderStatusConstant.READY_CHECK.equals(status)){
            msg = "待审核";
        }else if(OrderStatusConstant.READY_SEND.equals(status)){
            msg = "待配送";
        }else if(OrderStatusConstant.SENDING.equals(status)){
            msg = "配送中";
        }else if(OrderStatusConstant.CANCEL_NOT_PASS.equals(status)){
            msg = "已取消，审核未通过";
        }else if(OrderStatusConstant.CANCEL_AUTO.equals(status)
                || OrderStatusConstant.CANCEL_MANUAL.equals(status)){
            msg = "已取消";
        }

        return msg;
    }

    private String getRecipeStatusText(int status){
        String msg;
        switch(status){
            case RecipeStatusConstant.FINISH:
                msg = "已完成";
                break;
            case RecipeStatusConstant.HAVE_PAY:
                msg = "已支付，待取药";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                msg = "待处理";
                break;
            case RecipeStatusConstant.NO_PAY:
            case RecipeStatusConstant.NO_OPERATOR:
            case RecipeStatusConstant.REVOKE:
            case RecipeStatusConstant.NO_DRUG:
            case RecipeStatusConstant.CHECK_NOT_PASS_YS:
            case RecipeStatusConstant.DELETE:
            case RecipeStatusConstant.HIS_FAIL:
                msg = "已取消";
                break;
            case RecipeStatusConstant.IN_SEND:
                msg = "配送中";
                break;
            case RecipeStatusConstant.WAIT_SEND:
            case RecipeStatusConstant.READY_CHECK_YS:
            case RecipeStatusConstant.CHECK_PASS_YS:
                msg = "待配送";
                break;
            default:
                msg="未知状态";
        }

        return msg;
    }

}
