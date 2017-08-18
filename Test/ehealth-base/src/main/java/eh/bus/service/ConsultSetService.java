package eh.bus.service;

import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.account.constant.AccountConstant;
import eh.base.constant.SubBussTypeConstant;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.service.DoctorRevenueService;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Doctor;
import eh.entity.base.OrganConfig;
import eh.entity.bus.Consult;
import eh.entity.bus.ConsultSet;
import eh.utils.LocalStringUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.log4j.Logger;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ConsultSetService {
    private static final Logger log = Logger.getLogger(ConsultSetService.class);

    /**
     * 获取一个医生的业务设置-简版
     *
     * @param doctorId
     * @return 健康3.0 医生认证前需查询是否设置过业务开关,-医生在咨询介绍页可查询业务设置
     */
    @RpcService
    public ConsultSet getSimpleSetByDoctorId(int doctorId) {
        return getByIdForApp(doctorId);
    }


    /**
     * 删除【专家解读】白名单时，需要将个人专家解读开关关闭
     *
     * @param
     * @return
     */
    @RpcService
    public void closeProfessorStatus(Integer doctorId) {

        ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet set = dao.get(doctorId);
        if (set != null) {
            set.setProfessorConsultStatus(0);
            dao.update(set);
            log.info("专家解读白名单删除[" + doctorId + "]");
        }
    }

    /**
     * 新增【专家解读】白名单时，需要将个人专家解读开关打开
     *
     * @param
     * @return
     */
    public void openProfessorConsultInfo(Integer doctorId) {
        ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);

        Double professorConsultPrice = new Double(ParamUtils.getParam(ParameterConstant.KEY_PROFESSOR_CONSULT_PRICE, "10"));
        Integer professorConsultStatus = 1;
        dao.updateProfessorConsultInfoByDoctorId(professorConsultStatus, professorConsultPrice, doctorId);
        log.info("专家解读白名单增加[" + doctorId + "]");
    }


    /**
     * wx2.8-zhangx 当新增职业点/新开医生账户的时候，发现医生所在的职业点中有处方权限，则默认开通【在线续方】功能
     * 2017-3-9 15:52:25 zhangx 褚炜要求新建账户的时候不进行默认打开，让医生自己打开
     *
     * @param doctorId
     */
    public void openRecipeConsultInfo(Integer doctorId) {

//		RecipeService recipeService = AppContextHolder.getBean("eh.recipeService", RecipeService.class);
//		Map<String, Object> canRecipe=recipeService.openRecipeOrNot(doctorId);
//		Boolean canRecipeFlag=(Boolean)canRecipe.get("result");
//		if(canRecipeFlag){
//			ConsultSetDAO dao=DAOFactory.getDAO(ConsultSetDAO.class);
//			Double recipeConsultPrice=new Double(ParamUtils.getParam(ParameterConstant.KEY_RECIPE_CONSULT_PRICE,"10"));
//			Integer recipeConsultStatus=1;
//
//			dao.updateRecipeConsultInfoByDoctorId(recipeConsultStatus,recipeConsultPrice,doctorId);
//			log.info("在线续方-开通功能["+doctorId+"]");
//		}
    }


    /**
     * 1. 该医生是否有咨询类的业务开通  consultionOpenFlag
     * 2. 该医生是否有过咨询的业务   consultionHaveFlag
     * 3. 该医生的咨询量，获取的数据是每天凌晨 3:00更新得到的
     *
     * @param doctorId 该医生的主键
     * @return
     * @author cuill
     */
    @RpcService
    public Map<String, Object> findDoctorConsultionStatus(Integer doctorId) {
        boolean consultionOpenFlag = false;
        boolean consultionHaveFlag = false;
        long consultAmount = 0;
        HashMap<String, Object> consultionInfoMap = new HashMap<String, Object>();
        //如果该医生开通了咨询业务看看该医生是否有订单.
        if (judgeConsultionOpen(doctorId)) {
            consultionOpenFlag = true;
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        if (!StringUtils.isEmpty(doctor.getConsultAmount())) {
            consultAmount = doctor.getConsultAmount();
        }
        if (consultAmount > 0) {
            consultionHaveFlag = true;
        }
        consultionInfoMap.put("consultionOpenFlag", consultionOpenFlag);
        consultionInfoMap.put("consultionHaveFlag", consultionHaveFlag);
        consultionInfoMap.put("consultAmount", consultAmount);
        return consultionInfoMap;
    }

    /**
     * 判断医生是否开通了咨询业务设置
     *
     * @param doctorId 医生主键
     * @return
     * @author cuill
     */
    private boolean judgeConsultionOpen(Integer doctorId) {
        ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet consultSet = dao.get(doctorId);
        if (StringUtils.isEmpty(consultSet)) {
            return false;
        }
        //后期可能会加上在线续方,那么就会多加上一条判断在线续方是否开通
        if (!StringUtils.isEmpty(consultSet.getOnLineStatus())
                && consultSet.getOnLineStatus().equals(ConsultConstant.DOCTOR_SERVICE_SWITCH_ON)) {
            return true;
        } else if (!StringUtils.isEmpty(consultSet.getAppointStatus())
                && consultSet.getAppointStatus().equals(ConsultConstant.DOCTOR_SERVICE_SWITCH_ON)) {
            return true;
        } else if (!StringUtils.isEmpty(consultSet.getProfessorConsultStatus())
                && consultSet.getProfessorConsultStatus().equals(ConsultConstant.DOCTOR_SERVICE_SWITCH_ON)) {
            return true;
        } else if (!StringUtils.isEmpty(consultSet.getRecipeConsultStatus())
                && consultSet.getRecipeConsultStatus().equals(ConsultConstant.DOCTOR_SERVICE_SWITCH_ON)) {
            return true;
        }
        return false;
    }


    /**
     * 获取医生所在机构设置的业务价格
     * wx3.1 运营平台添加（图文咨询、电话咨询）价格控制，运营平台上设置了价格，
     * 则医生App端无法修改价格，页面显示价格为医院设置的价格
     * 需要运营平台提供一个医生所在机构设置的业务价格
     * @return
     */
    public Double getOrganConsultPrice(Integer organId,Integer requestMode){
        Double price=null;

        OrganConfigDAO organConfigDAO=DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig organConfig=organConfigDAO.get(organId);
        if(organConfig==null){
            return Double.valueOf(-1);
        }

        //图文咨询
        if(ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(requestMode)){
            price= organConfig.getOnLineConsultPrice()==null?Double.valueOf(0):organConfig.getOnLineConsultPrice();
        }

        //电话咨询
        if(ConsultConstant.CONSULT_TYPE_POHONE.equals(requestMode)){
            price= organConfig.getAppointConsultPrice()==null?Double.valueOf(0):organConfig.getAppointConsultPrice();
        }

        //在线续方
        if(ConsultConstant.CONSULT_TYPE_RECIPE.equals(requestMode)){
            price= organConfig.getRecipeConsultPrice()==null?Double.valueOf(0):organConfig.getRecipeConsultPrice();
        }

        //专家解读
        if(ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(requestMode)){
            price= organConfig.getProfessorConsultPrice()==null?Double.valueOf(0):organConfig.getProfessorConsultPrice();
        }
        return price;
    }


    /**
     * 医生端是否可以修改价格
     * @param price 运营平台设置的价格
     * @return true可以；false不可以
     */
    public Boolean getCanModifyPriceFlag(Double organPrice){
          return Double.valueOf(-1).equals(organPrice);
    }


    /**
     * 包装咨询设置,仅仅返回图文咨询状态,目前给团队医生使用
     * @param consultSet  数据库读取的医生业务设置
     * @return
     * @date 2017/7/11
     * @author cuill
     */
    public ConsultSet wrapConsultSetForOnLineStatus(ConsultSet consultSet){
        ConsultSet returnConsultSet = new ConsultSet();
        if (ObjectUtils.isEmpty(consultSet) || ObjectUtils.isEmpty(consultSet.getOnLineStatus())) {
            returnConsultSet.setOnLineStatus(0);
        } else {
            returnConsultSet.setOnLineStatus(consultSet.getOnLineStatus());
        }
        return returnConsultSet;
    }


    /**
     * 根据医生主键 获取业务设置
     * @param id
     * @return
     */
    @RpcService
    public ConsultSet getByIdForApp(int doctorId){
        ConsultSetDAO setDao=DAOFactory.getDAO(ConsultSetDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        ConsultSet set=setDao.getById(doctorId);

        Doctor doctor=doctorDAO.get(set.getDoctorId());
        if(doctor==null){
            doctor=new Doctor();
        }
        int organId =doctor.getOrgan()==null?0:doctor.getOrgan();

        set.setOnLineConsultDesc( getConsultSetDesc(set,ConsultConstant.CONSULT_TYPE_GRAPHIC,organId) );
        set.setAppointConsultDesc( getConsultSetDesc(set,ConsultConstant.CONSULT_TYPE_POHONE,organId)) ;
        set.setProfessorConsultDesc( getConsultSetDesc(set,ConsultConstant.CONSULT_TYPE_PROFESSOR,organId) );
        set.setRecipeConsultDesc( getConsultSetDesc(set,ConsultConstant.CONSULT_TYPE_RECIPE,organId) );

        return set;
    }

    public String getConsultSetDesc(ConsultSet set,Integer requestMode,Integer organId){

        //是否可修改，true可修改，医院未设置价格
        Boolean canModify=true;
        int subBusType=0;
        String setDesc="建议价格100元以内-其中-${organRate}%为医院费用-${ngariRate}%将作为平台费";

        //图文咨询
        if(ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(requestMode)){
            canModify=set.getCanModifyOnLineConsultPrice();
            subBusType= SubBussTypeConstant.ONLINE_CONSULT;
            setDesc=ParamUtils.getParam(ParameterConstant.KEY_CONSULTSET_DESC_ONLINE,setDesc);
        }

        //电话咨询
        if(ConsultConstant.CONSULT_TYPE_POHONE.equals(requestMode)){
            canModify=set.getCanModifyAppointConsultPrice();
            subBusType= SubBussTypeConstant.APPOINT_CONSULT;
            setDesc=ParamUtils.getParam(ParameterConstant.KEY_CONSULTSET_DESC_APPOINT,setDesc);
        }

        //在线续方
        if(ConsultConstant.CONSULT_TYPE_RECIPE.equals(requestMode)){
            canModify=set.getCanModifyRecipeConsultPrice();
            subBusType= SubBussTypeConstant.RECIPE_CONSULT;
            setDesc=ParamUtils.getParam(ParameterConstant.KEY_CONSULTSET_DESC_RECIPE,setDesc);
        }

        //专家解读
        if(ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(requestMode)){
            canModify=set.getCanModifyProfessorConsultPrice();
            subBusType= SubBussTypeConstant.PROFESSOR_CONSULT;
            setDesc=ParamUtils.getParam(ParameterConstant.KEY_CONSULTSET_DESC_PROFESSOR,setDesc);
        }

        String[] descs=setDesc.split("-");

        Map<String,BigDecimal> rateMap=new DoctorRevenueService()
                .getOrganProportion(String.valueOf(subBusType),organId);

        BigDecimal ngariRate=rateMap.get(AccountConstant.KEY_RATE_NGARI);
        BigDecimal organRate=rateMap.get(AccountConstant.KEY_RATE_ORGAN);

        Map<String,Double> paramMap= Maps.newHashMap();
        paramMap.put("ngariRate",BigDecimal.valueOf(100).multiply(ngariRate).setScale(0,BigDecimal.ROUND_HALF_UP).doubleValue());
        paramMap.put("organRate",BigDecimal.valueOf(100).multiply(organRate).setScale(0,BigDecimal.ROUND_HALF_UP).doubleValue());



        StringBuilder desc=new StringBuilder();

        if(canModify){
            desc.append(descs[0]).append(",");
        }
        desc.append(descs[1]);
        if(BigDecimal.ZERO.compareTo(organRate) != 0){
            desc.append(descs[2]).append(",");
        }
        desc.append(descs[3]);

        return LocalStringUtil.processTemplate(desc.toString(),paramMap);
    }


}
