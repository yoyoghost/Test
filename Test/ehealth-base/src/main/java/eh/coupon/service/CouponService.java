package eh.coupon.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.OrganDAO;
import eh.base.user.UserSevice;
import eh.bus.dao.*;
import eh.cdr.dao.RecipeDAO;
import eh.cdr.dao.RecipeOrderDAO;
import eh.cdr.service.RecipeOrderService;
import eh.coupon.constant.CouponBusTypeEnum;
import eh.coupon.constant.CouponConstant;
import eh.coupon.remote.ICouponBaseServiceInterface;
import eh.entity.base.Organ;
import eh.entity.bus.*;
import eh.entity.cdr.Recipe;
import eh.entity.cdr.RecipeOrder;
import eh.entity.coupon.Coupon;
import eh.entity.coupon.CouponParam;
import eh.entity.mpi.Patient;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.SignRecordDAO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 优惠劵相关服务
 */
public class CouponService {
    private static final Log logger = LogFactory.getLog(CouponService.class);

    // 调用运营平台测试桩接口
    private CouponTestService opService=new CouponTestService();
    private ICouponBaseServiceInterface couponBaseService = AppContextHolder. getBean("eh.couponBaseService", ICouponBaseServiceInterface. class);



    @RpcService
    public HashMap<String,Object> findAllCoupons(Integer start,Integer limit){
        List<Coupon> list=new ArrayList<Coupon>();
        HashMap<String,Object> returnMap=new HashMap<String,Object>();

        UserRoleToken urt = UserRoleToken.getCurrent();
        if(urt==null){
            returnMap.put("num",0);
            returnMap.put("coupons",list);
            return returnMap;
        }

        int urtId=urt.getId();
        Long num=getCouponsNum(CouponConstant.COUPON_BUSTYPE_ALL,null);

        limit=limit==null?num.intValue():limit;
        list.addAll(couponBaseService.findCouponsByUrt(urtId,start,limit));

        returnMap.put("num",num);
        returnMap.put("coupons",list);
        return returnMap;
    }

    @RpcService
    public HashMap<String,Object> findBusCoupons(Integer couponBusType,Integer busId,Integer start,Integer limit){
        List<Coupon> list=new ArrayList<Coupon>();
        HashMap<String,Object> returnMap=new HashMap<String,Object>();

        CouponBusTypeEnum typeEnum=CouponBusTypeEnum.getById(couponBusType);

        UserRoleToken urt = UserRoleToken.getCurrent();
        if(typeEnum==null){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"未找到相对应的业务单信息，无法获取业务单原始价格");
        }
        CouponParam param=getCouponParam(typeEnum,busId);
        BigDecimal busPrice=param.getOrderAmount();
        if(urt==null){
            returnMap.put("num",0);
            list.add(getNotUseCouponInfo(busPrice));
            returnMap.put("coupons",list);
            return returnMap;
        }

        int urtId=urt.getId();
        Long num=getCouponsNum(couponBusType,param);

        limit=limit==null?num.intValue():limit;
        List<Coupon> availableList=findAvailableCoupons(urtId,param,start,limit);
        list.addAll(availableList);

        //当查询出来当前页是最后一页时，则添加
        Integer length=availableList.size();

        //第一次请求指定业务优惠劵且无优惠劵
        if(length==0 && start==0){
            list.add(getNotUseCouponInfo(busPrice));
        }

        //最后一页请求数据
        if(length>0 && new Long(start+length).longValue() == num.longValue()){
            list.add(getNotUseCouponInfo(busPrice));
        }

//        dealCoupons(list,busId,typeEnum);

        returnMap.put("num",num);
        returnMap.put("coupons",list);
        return returnMap;
    }

    /**
     * 当业务单中绑定优惠劵，返回绑定的优惠劵
     * @param couponBusType
     * @param busId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public HashMap<String,Object> findBusCouponsWithBus(Integer couponBusType,Integer busId,Integer start,Integer limit){
        List<Coupon> list=new ArrayList<Coupon>();
        HashMap<String,Object> returnMap=new HashMap<String,Object>();
        Boolean bindFlag=false;

        CouponBusTypeEnum typeEnum=CouponBusTypeEnum.getById(couponBusType);
        if(typeEnum==null){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"未找到相对应的业务单信息，无法获取业务单原始价格");
        }
        CouponParam param=getCouponParam(typeEnum,busId);
        BigDecimal busPrice=param.getOrderAmount();
        Integer couponId=param.getCouponId();
        if(couponId!=null && couponId.intValue()==-1){
            bindFlag = true;
            returnMap.put("num", 0);
            list.add(getNotUseCouponInfo(busPrice));
            returnMap.put("coupons",list);
            returnMap.put("bindFlag",bindFlag);
            return returnMap;
        }
        //业务单中已绑定优惠劵，且与前端的业务类型相同，则返回已绑定的优惠劵列表数据
        if(couponId!=null && couponId.intValue()>0){
            Coupon coupon=couponBaseService.getCouponById(couponId);
            if(coupon!=null){
                int busType=coupon.getCouponType()==null?0:coupon.getCouponType();
                int subBusType=coupon.getSubCouponType()==null?0:coupon.getSubCouponType();
                int EnumSubBusType=typeEnum.getSubCouponBusType()==null?0:typeEnum.getSubCouponBusType();
                if(typeEnum.getCouponBusType()==busType && subBusType==EnumSubBusType){
                    bindFlag=true;
                    returnMap.put("num",0);
                    list.add(getBindCouponInfo(param));
                    list.add(getNotUseCouponInfo(busPrice));
                    returnMap.put("coupons",list);
                    returnMap.put("bindFlag",bindFlag);
                    return returnMap;
                }
            }
        }

        UserRoleToken urt = UserRoleToken.getCurrent();
        if(urt==null){
            returnMap.put("bindFlag",bindFlag);
            returnMap.put("num",0);
            list.add(getNotUseCouponInfo(busPrice));
            returnMap.put("coupons",list);
            return returnMap;
        }

        int urtId=urt.getId();
        Long num=getCouponsNum(couponBusType,param);

        limit=limit==null?num.intValue():limit;
        List<Coupon> availableList=findAvailableCoupons(urtId,param,start,limit);
        list.addAll(availableList);

        //当查询出来当前页是最后一页时，则添加
        Integer length=availableList.size();

        //第一次请求指定业务优惠劵且无优惠劵
        if(length==0 && start==0){
            list.add(getNotUseCouponInfo(busPrice));
        }

        //最后一页请求数据
        if(length>0 && new Long(start+length).longValue() == num.longValue()){
            list.add(getNotUseCouponInfo(busPrice));
        }

//        dealCoupons(list,busId,typeEnum);

        returnMap.put("bindFlag",bindFlag);
        returnMap.put("num",num);
        returnMap.put("coupons",list);
        return returnMap;
    }

    /**
     * 对优惠券列表进行二次加工
     * @param list
     * @param busId
     * @param typeEnum
     */
    private void dealCoupons(List<Coupon> list, Integer busId, CouponBusTypeEnum typeEnum){
        Integer busType=typeEnum.getCouponBusType();
        Integer subBusType=typeEnum.getSubCouponBusType();

        switch (busType){
            case CouponConstant.COUPON_BUSTYPE_RECIPE:
                //处方进行实际支付金额显示处理做处理，优惠券里的业务金额指的是处方金额
                RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
                RecipeOrderService orderService = AppContextHolder.getBean("eh.recipeOrderService", RecipeOrderService.class);

                RecipeOrder order = orderDAO.get(busId);
                BigDecimal totalFee = orderService.countOrderTotalFee(order);
                for(Coupon coupon : list){
                    coupon.setOrderAmount(totalFee);
                    if(null != coupon.getDiscountAmount()) {
                        coupon.setActualPrice(totalFee.subtract(coupon.getDiscountAmount()));
                    }else{
                        coupon.setActualPrice(totalFee);
                    }
                }

                break;
        }

    }


    /**
     * 获取所有可用优惠劵总数  wx前端使用,sms发送消息使用
     * @return
     */
    @RpcService
    public Long getAllCouponsNum(){
        return getCouponsNum(CouponConstant.COUPON_BUSTYPE_ALL,null);
    }

    @RpcService
    public Long getCouponsNumForSms(String mpiId){
        PatientDAO patientDAO=DAOFactory.getDAO(PatientDAO.class);
        Patient pat=patientDAO.get(mpiId);
        if(pat==null){
            return 0l;
        }
        UserSevice userService = new UserSevice();
        Integer uId=userService.getUrtIdByUserId(pat.getLoginId(),SystemConstant.ROLES_PATIENT);
        if(uId<0){
            return 0l;
        }

        Long num=couponBaseService.countCouponsByUrtAndStatus(uId,0);
        logger.info("用户"+uId+"所有可用优惠劵数量为:"+num);
        return num;
    }

    /**
     * 获取优惠劵总数接口
     * wx前端要用的，后台也要用
     * @param couponBusType
     * @param param
     * @return
     */
    @RpcService
    public Long getCouponsNum(Integer couponBusType, CouponParam param){
        Long num=0l;
        couponBusType=couponBusType==null?0:couponBusType;
        UserRoleToken urt = UserRoleToken.getCurrent();
        if(urt==null){
            return num;
        }
        int urtId=urt.getId();

        if(couponBusType.intValue()==0){
            num=couponBaseService.countCouponsByUrtAndStatus(urtId,0);
            logger.info("用户"+urtId+"所有可用优惠劵数量为:"+num);
            return num;
        }

        CouponBusTypeEnum typeEnum=CouponBusTypeEnum.getById(couponBusType);
        if(typeEnum==null){
            logger.info("未找到相对应的业务业务类型");
            return num;
        }

        num=couponBaseService.countAvailableCoupons(urtId,param.getOrderAmount(),param.getBusType(),param.getSubType()==null?0:param.getSubType(),
                param.getDoctorId(),param.getOrganId(),param.getManageUnit());
        logger.info("用户"+urtId+"的业务["+typeEnum.getId()+"]可用优惠劵数量为:"+num);

        return num;
    }

    /**
     * 在一次业务时获得用户的可用优惠券列表，根据传入的订单金额会自动计算折扣金额和实付金额。按到期日期正序排列。
     */
    public List<Coupon> findAvailableCoupons(Integer urtId,CouponParam param,Integer start,Integer limit){
        logger.info("获取指定业务优惠劵入参：param="+JSONUtils.toString(param)+",urtId="+urtId+",start="+start+",limit="+limit);
        List<Coupon> coupons= couponBaseService.findAvailableCoupons(urtId,param.getOrderAmount(),param.getBusType(),param.getSubType()==null?0:param.getSubType(),
                param.getDoctorId(),param.getOrganId(),param.getManageUnit(),start,limit);
        logger.info("获取指定业务优惠劵列表："+JSONUtils.toString(coupons));
        return coupons;
    }

    /**
     * 使用优惠劵(支付成功以后调用)
     * @param id
     * @return
     */
    @RpcService
    public Coupon useCouponById(Integer id){
        logger.info("使用优惠劵:"+id);
        return couponBaseService.useCouponById(id);
    }


    /**
     * 取消使用一张优惠券。由于业务取消等原因需要将已使用的优惠券返还给用户。
     * @param id
     * @return
     */
    @RpcService
    public Coupon unuseCouponById(Integer id){
        logger.info("返还优惠劵:"+id);
        try{
            return couponBaseService.unuseCouponById(id);
        }catch (Exception e){
            logger.error("unuseCouponById:["+id+"]"+e.getMessage());
        }

        return null;
    }

    /**
     * 锁定一张优惠券(向第三方支付机构下单时使用)
     * @param id
     * @return
     */
    @RpcService
    public Coupon lockCouponById(CouponParam param){
        logger.info("锁定一张优惠券:"+JSONUtils.toString(param));
        return couponBaseService.lockCouponById(param.getCouponId(),param.getOrderAmount());
    }

    /**
     * 解锁一张优惠券(订单失效时使用)
     * @param id
     * @returna
     */
    @RpcService
    public Coupon unlockCouponById(Integer id){
        logger.info("解锁一张优惠券:"+id);
        try{
            return couponBaseService.unlockCouponById(id);
        }catch (Exception e){
            logger.error("unlockCouponById:["+id+"]"+e.getMessage());
        }

        return null;
    }

    /**
     * 判断一张优惠券是否有效。
     *
     * @param id 优惠券Id
     * @param orderAmount 订单金额
     * @param type 主业务类型
     * @param subType 子业务类型
     * @return
     */
    @RpcService
    public boolean isCouponAvailable(CouponParam param){
        logger.info("判断一张优惠券是否有效:"+JSONUtils.toString(param));

        if(param==null ){
            throw new DAOException(DAOException.VALUE_NEEDED,"param is null");
        }

        String managerUnit=null;
        Integer organId=param.getOrganId();
        if(organId!=null){
            OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
            Organ organ=organDAO.get(organId);
            if(organ!=null){
                managerUnit=organ.getManageUnit();
            }
        }

        boolean b=couponBaseService.isCouponAvailable(param.getCouponId(),param.getOrderAmount(),
                param.getBusType(),param.getSubType()==null?0:param.getSubType(),param.getDoctorId(),organId,managerUnit);
        logger.info("优惠劵ID:"+param.getCouponId()+"，业务类型:"+ param.getBusType()+",业务ID："+param.getBusId()+";是否有效结果："+b);
        return b;
    }

    private CouponParam getCouponParam(CouponBusTypeEnum typeEnum,Integer busId){
        CouponParam param=new CouponParam();
        param.setBusId(busId);

        int busType=typeEnum.getCouponBusType();
        Integer subBusType=typeEnum.getSubCouponBusType();

        param.setBusType(busType);
        param.setSubType(subBusType);
        switch (busType){
            //咨询 wx2.7版本实现
            case CouponConstant.COUPON_BUSTYPE_CONSULT:
                ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
                Consult consult = consultDAO.getById(busId);
                Double consultPrice=consult.getConsultPrice()==null?0d:consult.getConsultPrice();
                BigDecimal actualPrice=consult.getActualPrice()==null?null:new BigDecimal(consult.getActualPrice());
                param.setOrderAmount(new BigDecimal(consultPrice));
                param.setCouponId(consult.getCouponId());
                param.setActualPrice(actualPrice);
                param.setDoctorId(consult.getConsultDoctor());
                param.setOrganId(consult.getConsultOrgan());
                break;
            //预约 未实现
            case CouponConstant.COUPON_BUSTYPE_APPOINT:
                //特需预约
                if(CouponConstant.COUPON_SUBTYPE_APPOINT_PATIENTTRANSFER==subBusType){
                    TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                    Transfer transfer = transferDAO.getById(busId);
                    Double transferPrice=transfer.getTransferPrice()==null?0d:transfer.getTransferPrice();
                    param.setOrderAmount(new BigDecimal(transferPrice));
                    param.setDoctorId(transfer.getTargetDoctor());
                    param.setOrganId(transfer.getTargetOrgan());
                }else{
                    AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                    AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
                    Double appointPrice=appointRecord.getClinicPrice();
                    BigDecimal appointActualPrice=appointRecord.getActualPrice()==null?null:new BigDecimal(appointRecord.getActualPrice());
                    param.setOrderAmount(new BigDecimal(appointPrice));
                    param.setCouponId(appointRecord.getCouponId());
                    param.setActualPrice(appointActualPrice);
                    param.setDoctorId(appointRecord.getDoctorId());
                    param.setOrganId(appointRecord.getOrganId());
                }
                break;
            //处方 wx2.7版本实现
            case CouponConstant.COUPON_BUSTYPE_RECIPE:
                RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
                Recipe recipe = recipeDAO.getByRecipeId(busId);
                param.setCouponId(null);
                //优惠券只对处方金额进行优惠
                param.setOrderAmount(recipe.getTotalMoney());
                param.setActualPrice(recipe.getTotalMoney());
                param.setDoctorId(recipe.getDoctor());
                param.setOrganId(recipe.getClinicOrgan());
                break;
            //门诊缴费 未实现
            case CouponConstant.COUPON_BUSTYPE_PAYMENT:
                OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
                Outpatient outpatient = outpatientDAO.get(busId);
                Double outpatientprice = outpatient.getTotalFee()==null?0d:outpatient.getTotalFee();
                param.setOrderAmount(new BigDecimal(outpatientprice));
                break;
            //住院预交 未实现
            case CouponConstant.COUPON_BUSTYPE_INHOSPITALPREPAID:
                PayBusinessDAO payBusinessDAO = DAOFactory.getDAO(PayBusinessDAO.class);
                PayBusiness payBusiness = payBusinessDAO.get(busId);
                Double price = payBusiness.getTotalFee()==null?0d:payBusiness.getTotalFee();
                param.setOrderAmount(new BigDecimal(price));
                break;
            //签约 未实现
            case CouponConstant.COUPON_BUSTYPE_SIGN:
                SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
                SignRecord signRecord = signRecordDAO.get(busId);
                Double signRecordprice=signRecord.getSignCost()==null?0d:signRecord.getSignCost();
                BigDecimal signActualPrice=signRecord.getActualPrice()==null?null:new BigDecimal(signRecord.getActualPrice());
                param.setOrderAmount(new BigDecimal(signRecordprice));
                param.setCouponId(signRecord.getCouponId());
                param.setActualPrice(signActualPrice);
                param.setDoctorId(signRecord.getOrgan());
                param.setOrganId(signRecord.getDoctor());
                break;

        }
        //计算优惠价格
        BigDecimal actualPrice=param.getActualPrice()==null?BigDecimal.ZERO:param.getActualPrice();
        BigDecimal discountAmount=param.getOrderAmount().subtract(actualPrice);
        param.setDiscountAmount(discountAmount.compareTo(BigDecimal.ZERO)<0?BigDecimal.ZERO:discountAmount);

        String managerUnit=null;
        Integer organId=param.getOrganId();
        if(organId!=null){
            OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
            Organ organ=organDAO.get(organId);
            if(organ!=null){
                managerUnit=organ.getManageUnit();
            }
        }
        param.setManageUnit(managerUnit);

        return param;
    }

    /**
     * 构造不使用的咨询单ID
     * @return
     */
    private Coupon getNotUseCouponInfo(BigDecimal busPrice){
        Coupon coupon=new Coupon();
        coupon.setCouponId(CouponConstant.COUPON_NOTUSE_ID);
        coupon.setCouponDesc(CouponConstant.COUPON_NOTUSE_DESC);
        coupon.setOrderAmount(busPrice.setScale(2, BigDecimal.ROUND_HALF_UP));
        coupon.setActualPrice(busPrice.setScale(2, BigDecimal.ROUND_HALF_UP));
        coupon.setDiscountAmount(BigDecimal.ZERO);
        return coupon;
    }

    /**
     * 构造不使用的咨询单ID
     * @return
     */
    private Coupon getBindCouponInfo(CouponParam param){
        Coupon coupon=new Coupon();
        coupon.setCouponId(param.getCouponId());
        coupon.setActualPrice(param.getActualPrice());
        coupon.setOrderAmount(param.getOrderAmount());
        coupon.setDiscountAmount(param.getDiscountAmount());
        return coupon;
    }




}
