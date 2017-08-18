package eh.coupon.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.coupon.CouponInfo;

import java.util.Date;
import java.util.List;

public abstract class CouponInfoDAO extends HibernateSupportDelegateDAO<CouponInfo>{
	public CouponInfoDAO(){
		super();
		this.setEntityName(CouponInfo.class.getName());
		this.setKeyField("couponInfoId");
	}

	public CouponInfo saveCoupon(CouponInfo info){
		info.setDate(new Date());
		return save(info);
	}

	@DAOMethod(sql="from CouponInfo where urt=:urt and serviceType=2 and doctorId=:doctorId")
	public abstract List<CouponInfo> findRelations(@DAOParam("urt")Integer urt,@DAOParam("doctorId")Integer doctorId);

	@DAOMethod(sql = "from CouponInfo where urt=:urt and serviceType=:serviceType")
	public abstract List<CouponInfo> findCouponInfoByUrtAndServiceType(@DAOParam("urt")Integer urt,
																	   @DAOParam("serviceType")Integer serviceType);

}
