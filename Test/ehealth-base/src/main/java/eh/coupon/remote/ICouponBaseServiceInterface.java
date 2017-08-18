package eh.coupon.remote;

import ctd.util.annotation.RpcService;
import eh.entity.coupon.Coupon;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠劵平台对应接口
 */
public interface ICouponBaseServiceInterface {

    @RpcService
    public Coupon getCouponById(Integer id);

    /**
     * 获得用户优惠券列表，只显示未使用和已过期优惠券。未使用在前，已过期在后，按发放日期倒序排列。
     *
     * @param urt
     * @return
     */
    @RpcService
    public List<Coupon> findCouponsByUrt(Integer urt, int start, int limit);

    /**
     * 在一次业务时获得用户的可用优惠券列表，根据传入的订单金额会自动计算折扣金额和实付金额。按到期日期正序排列。
     *
     * @param urt         用户角色ID
     * @param orderAmount 订单金额
     * @param type        业务类型
     * @param subType     子业务类型
     * @param doctorId    使用优惠券的医生，可以为空
     * @param organId     使用优惠券的机构，可以为空
     * @param manageUnit  使用优惠券的管理单元，可以为空
     * @return
     */
    @RpcService
    public List<Coupon> findAvailableCoupons(Integer urt, BigDecimal orderAmount, Integer type, Integer subType,
                                             Integer doctorId, Integer organId, String manageUnit, int start, int limit);

    /**
     * 使用优惠劵
     * @param id
     * @return
     */
    @RpcService
    public Coupon useCouponById(Integer id);

    /**
     * 取消使用一张优惠券。由于业务取消等原因需要将已使用的优惠券返还给用户。
     *
     * @param id
     * @return
     */
    @RpcService
    public Coupon unuseCouponById(Integer id);

    /**
     * 锁定一张优惠券。同时根据订单金额计算实际支付金额。
     *
     * @param id
     * @param orderAmount 使用优惠券的订单金额
     * @return
     */
    @RpcService
    public Coupon lockCouponById(Integer id, BigDecimal orderAmount);
    /**
     * 解锁一张优惠券。
     *
     * @param id
     * @return
     */
    @RpcService
    public Coupon unlockCouponById(Integer id);

    /**
     * 判断一张优惠券是否有效。
     *
     * @param id          优惠券Id
     * @param orderAmount 订单金额
     * @param type        主业务类型
     * @param subType     子业务类型
     * @param doctorId    使用优惠券的医生，可以为空
     * @param organId     使用优惠券的机构，可以为空
     * @param manageUnit  使用优惠券的管理单元，可以为空
     * @return
     */
    @RpcService
    public boolean isCouponAvailable(int id, BigDecimal orderAmount, Integer type, Integer subType,
                                     Integer doctorId, Integer organId, String manageUnit);

    /**
     * 根据用户和优惠券状态获取优惠券数量。
     *
     * @param urt
     * @param status -1,失效;0,未使用;1,已使用;2,已锁定
     * @return
     */
    @RpcService
    public Long countCouponsByUrtAndStatus(final Integer urt, Integer status);

    /**
     * 在一次业务时获得用户的可用优惠券总数。
     *
     * @param urt         用户角色ID
     * @param orderAmount 订单金额
     * @param type        业务类型
     * @param subType     子业务类型
     * @param doctorId    使用优惠券的医生，可以为空
     * @param organId     使用优惠券的机构，可以为空
     * @param manageUnit  使用优惠券的管理单元，可以为空
     */
    @RpcService
    public Long countAvailableCoupons(Integer urt, BigDecimal orderAmount, Integer type,
                                      Integer subType, Integer doctorId, Integer organId, String manageUnit);

}
