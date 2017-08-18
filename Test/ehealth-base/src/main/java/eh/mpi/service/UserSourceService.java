package eh.mpi.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import eh.coupon.service.CouponService;
import eh.entity.mpi.UserSource;
import eh.mpi.constant.UserSourceConstant;
import eh.mpi.dao.UserSourceDAO;
import eh.push.SmsPushService;
import eh.utils.DateConversion;
import eh.wxpay.dao.WxSubscribeDAO;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class UserSourceService {

    private static final Log logger = LogFactory.getLog(UserSourceService.class);
    private UserSourceDAO sourceDAO= DAOFactory.getDAO(UserSourceDAO.class);

    /**
     * sms使用
     * @param id
     * @return
     */
    @RpcService
    public UserSource getById(Integer id){
        return sourceDAO.get(id);
    }

    /**
     * 注册后24小时推送客服消息：
     您的医生给您发了一个新提醒。
     您对就诊过程有疑问吗？
     有任何问题可随时咨询医生哦。
     您还有6张优惠券未使用，立即去咨询，让医生解答您的疑惑。
     马上咨询医生
     若是通过关注用户进行注册的，点击跳转至关注医生主页（第一个扫码关注的医生）；没有关注跳转到咨询首页；
     x张优惠券未使用，x取得是未使用的张数；

     2017-4-22 15:21:36 zhangx 由于一次性访问数据量比较大，且程序无法一次执行完成，进行优化，优化方案如下：
     每次读取100条，100条执行完成后，再次读取下100条，一直到读不出数据为止，跳出循环,定时器每天晚上7点开始执行一次
     */
    @RpcService
    public void sendWxMsgForCoupon(){
        WxSubscribeDAO subsriDAO=DAOFactory.getDAO(WxSubscribeDAO.class);
        CouponService couponService= AppContextHolder.getBean("eh.couponService",CouponService.class);

        Date yestoday= Context.instance().get("date.datetimeOfLastDay",Date.class);
        logger.info("注册24小时以后推送优惠劵客服消息-"+ DateConversion.getDateFormatter(yestoday, "yyyy-MM-dd HH:mm"));

        int start =0;
        int limit=2;

        while(1==1){
            List<Integer> list=sourceDAO.findUnRemindPatient(yestoday,start,limit);
            if(list.size()==0){
                break;
            }

            for (Integer sourceId:list) {
                //更新发送标记
                sourceDAO.updateRemindStatusById(UserSourceConstant.REMIND_STATUS_REMINDED,sourceId);

                //发送消息通知
                Integer clientId = null;
                SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                smsPushService.pushMsgData2Ons(sourceId, 0, "CouponsNums", "CouponsNums", clientId);
            }

            try{
                // 将线程睡眠1秒，否则短信发送不成功
                TimeUnit.SECONDS.sleep(1);
            }catch (Exception e){
                logger.error("注册后24小时推送客服消息-线程睡眠异常"+e.getMessage());
                continue;
            }

        }

    }

}
