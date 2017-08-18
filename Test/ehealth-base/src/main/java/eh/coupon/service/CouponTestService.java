package eh.coupon.service;

import ctd.persistence.DAOFactory;
import ctd.util.context.Context;
import eh.base.constant.SystemConstant;
import eh.bus.constant.RequestModeConstant;
import eh.bus.dao.ConsultDAO;
import eh.coupon.constant.CouponConstant;
import eh.entity.bus.Consult;
import eh.entity.coupon.Coupon;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 优惠劵相关服务
 */
public class CouponTestService {
    private static final Log logger = LogFactory.getLog(CouponTestService.class);
    private int couponId=1;

    /**
     * 获取所有优惠劵列表
     * @param mpi 患者主键
     * @return
     */
    public List<Coupon> findAllCouponsList(String mpi){

        List<Coupon> list=new ArrayList<Coupon>();
        list.addAll(findInlineCunsultCoupons(mpi));
        list.addAll(findAppointCunsultCoupons(mpi));
        list.addAll(findRecipeOnlineCoupons(mpi));
        return list;
    }

    public List<Coupon> findBusCouponsList(String mpi,Integer busType){
        List<Coupon> list=new ArrayList<Coupon>();
        if(busType==null){
            return new ArrayList<Coupon>();
        }
        if(CouponConstant.COUPON_SUBTYPE_CONSULT_ONLINE==busType){
            list.addAll(findInlineCunsultCoupons(mpi));
        } if(CouponConstant.COUPON_SUBTYPE_CONSULT_APPOINT==busType){
            list.addAll(findAppointCunsultCoupons(mpi));
        } if(CouponConstant.COUPON_SUBTYPE_RECIPE_HOME_PAYONLINE==busType){
            list.addAll(findRecipeOnlineCoupons(mpi));
        }
        return list;
    }

    /**
     * 获取优惠劵总数
     * @param mpi 患者主键
     * @param busType 0是全部,>0具体业务，见eh.coupon.dictionary.BusType
     * @return
     */
    public int getCouponsNum(String mpi,Integer busType){
        int num=0;
        if(busType==null){
            return 0;
        }

        if(CouponConstant.COUPON_BUSTYPE_ALL==busType){
            num=num+getCunsultCouponsNums(mpi,RequestModeConstant.ONLINE_REQUEST_MODE);
            num=num+getCunsultCouponsNums(mpi,RequestModeConstant.APPOINT_REQUEST_MODE);
            num=num+5;
        }else{
            if(CouponConstant.COUPON_SUBTYPE_CONSULT_ONLINE==busType){
                num=num+getCunsultCouponsNums(mpi,RequestModeConstant.ONLINE_REQUEST_MODE);
            } if(CouponConstant.COUPON_SUBTYPE_CONSULT_APPOINT==busType){
                num=num+getCunsultCouponsNums(mpi,RequestModeConstant.APPOINT_REQUEST_MODE);
            }if(CouponConstant.COUPON_SUBTYPE_RECIPE_HOME_PAYONLINE==busType){
                num=5;
            }
        }
        return num;
    }


    /**
     * 获取图文咨询优惠劵
     */
    private List<Coupon> findInlineCunsultCoupons(String mpi){
        int requestMode=RequestModeConstant.ONLINE_REQUEST_MODE;
        int num=getCunsultCouponsNums(mpi,requestMode);

        List<Coupon> list=new ArrayList<Coupon>();
        for(int i=0;i<num;i++){
            try{
                Coupon map=getCoupon(CouponConstant.COUPON_SUBTYPE_CONSULT_ONLINE,CouponConstant.COUPON_BUSTYPE_CONSULT);
                list.add(map);
            }catch(Exception e){
                logger.error(e.getMessage());
            }
        }

        return list;
    }

    /**
     * 获取电话咨询优惠劵
     */
    private List<Coupon> findAppointCunsultCoupons(String mpi){
        int requestMode=RequestModeConstant.APPOINT_REQUEST_MODE;
        int num=getCunsultCouponsNums(mpi,requestMode);

        List<Coupon> list=new ArrayList<Coupon>();
        for(int i=0;i<num;i++){
            try{
                Coupon map=getCoupon(CouponConstant.COUPON_SUBTYPE_CONSULT_APPOINT,CouponConstant.COUPON_BUSTYPE_CONSULT);
                list.add(map);
            }catch(Exception e){
                logger.error(e.getMessage());
            }
        }

        return list;
    }

    /**
     * 获取图文咨询优惠劵
     */
    private int getCunsultCouponsNums(String mpi,Integer requestMode){
        ConsultDAO consutDao=DAOFactory.getDAO(ConsultDAO.class);

        Integer disCountNum=0;

        if(RequestModeConstant.APPOINT_REQUEST_MODE==requestMode) {
            disCountNum=SystemConstant.APPOINT_CONSULT_DISCOUNT_NUM;
        }
        if(RequestModeConstant.ONLINE_REQUEST_MODE==requestMode) {
            disCountNum=SystemConstant.ONLINE_CONSULT_DISCOUNT_NUM;
        }

        List<Consult> appointConsultList = consutDao
                .findEffectiveByRequestMpiAndRequestMode(mpi, requestMode);

        int num=disCountNum-appointConsultList.size()<=0?0:disCountNum-appointConsultList.size();
        return num;
    }

    /**
     * 处方
     * @param mpi
     * @return
     */
    private List<Coupon> findRecipeOnlineCoupons(String mpi){
        int num=5;

        List<Coupon> list=new ArrayList<Coupon>();
        for(int i=0;i<num;i++){
            try{
                Coupon map=getCoupon(CouponConstant.COUPON_SUBTYPE_RECIPE_HOME_PAYONLINE,CouponConstant.COUPON_BUSTYPE_RECIPE);
                list.add(map);
            }catch(Exception e){
                logger.error(e.getMessage());
            }
        }

        return list;
    }


    /**
     * 组装优惠劵数据
     * @param busType
     * @param couponType
     * @return
     * @throws Exception
     */
    private Coupon getCoupon(int busType, int couponType){
        Coupon coupon=new Coupon();
        coupon.setCouponId(couponId);
        coupon.setCouponType(couponType);
        coupon.setSubCouponType(busType);
        if(CouponConstant.COUPON_SUBTYPE_CONSULT_ONLINE==busType){
            coupon.setDiscountType(1);
            coupon.setDiscount(new BigDecimal(5));
            coupon.setDiscountShowNum(new BigDecimal(5));
            coupon.setCouponDesc("满0.00元使用");
        }
        if(CouponConstant.COUPON_SUBTYPE_CONSULT_APPOINT==busType){
            coupon.setDiscountType(2);
            coupon.setDiscount(new BigDecimal(0.75));
            coupon.setDiscountShowNum(new BigDecimal(7.5));
            coupon.setCouponDesc("满0.00元使用");
        }
        if(CouponConstant.COUPON_SUBTYPE_RECIPE_HOME_PAYONLINE==busType){
            coupon.setDiscountType(1);
            coupon.setDiscount(new BigDecimal(10));
            coupon.setDiscountShowNum(new BigDecimal(10));
            coupon.setMinAmount(new BigDecimal(39));
            coupon.setCouponDesc("满39.00元使用");
        }

        coupon.setDiscountShowUnit(getDiscountUnit(coupon.getDiscountType()));
        coupon.setStartDate(Context.instance().get("date.datetime",Date.class));
        coupon.setEndDate(Context.instance().get("date.dateOfNextMonth",Date.class));
        coupon.setCouponStatus(0);
        couponId++;
        return coupon;
    }


    private String getDiscountUnit(Integer discountType){
        if(1==discountType){
            return "元";
        }

        if(2==discountType){
            return "折";
        }

        return "";
    }

    public BigDecimal getDiscountAmount(BigDecimal busPrice,Integer discountType){
        if(1==discountType){
            return new BigDecimal(5);
        }

        if(2==discountType){
            return busPrice.multiply(new BigDecimal(1).subtract(new BigDecimal(0.75)));
        }

        return new BigDecimal(5);
    }
}
