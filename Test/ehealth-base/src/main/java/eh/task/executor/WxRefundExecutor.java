package eh.task.executor;

import com.alibaba.druid.support.json.JSONUtils;

import ctd.net.rpc.transport.exception.TransportException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.OutpatientDAO;
import eh.bus.dao.PayBusinessDAO;
import eh.bus.push.MessagePushExecutorConstant;
import eh.cdr.service.RecipeService;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Consult;
import eh.entity.bus.Outpatient;
import eh.entity.mindgift.MindGift;
import eh.entity.msg.SmsInfo;
import eh.mindgift.constant.MindGiftConstant;
import eh.mindgift.dao.MindGiftDAO;
import eh.push.SmsPushService;
import eh.task.ActionExecutor;
import eh.task.ExecutorRegister;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.service.NgariRefundService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 退款失败重试，最终失败会发送通知短信
 * 
 * @author zhangjr
 * 
 */
public class WxRefundExecutor implements ActionExecutor {
	private static final Log logger = LogFactory.getLog(WxRefundExecutor.class);
	/** 线程池 */
	private static ExecutorService executors = ExecutorRegister.register(Executors
			.newSingleThreadExecutor());

	/** 业务参数 */

	private Integer busId;
	private String busType;
	private int remark;//1主动退款
	public WxRefundExecutor(Integer busId, String busType) {
		this.busId = busId;
		this.busType = busType;
	}

	public WxRefundExecutor(Integer busId, String busType,int remark) {
		this.busId = busId;
		this.busType = busType;
		this.remark=remark;
	}


	private int tryCount = 0;
	private int maxCount = 5;

	@Override
	public void execute() throws DAOException {
		executors.execute(new Runnable() {
			@Override
			public void run() {
				sendToRefund();
			}
		});
	}

	public void sendToRefund() {
		NgariRefundService refundService = AppContextHolder.getBean("ngariRefundService", NgariRefundService.class);
		Map<String, Object> resultMap = refundService.refund(busId, busType);
		String code = (String) resultMap.get("code");
		logger.info("传入参数:busId=" + busId + ",busType=" + busType);
		try {
			if (tryCount < maxCount) {
				tryCount++;
				logger.info("第" + tryCount + "次重启退款操作！返回数据：" + JSONUtils.toJSONString(resultMap));
				if("SUCCESS".equals(code)){//退款成功
					if(busType.equals("transfer")){//转诊
						SmsPushService smsPushService = AppContextHolder.getBean("smsPushService", SmsPushService.class);
						smsPushService.pushMsgData2Ons(busId, 0, MessagePushExecutorConstant.REFUND_SUCCESS_TRANSFER, MessagePushExecutorConstant.REFUND_SUCCESS_TRANSFER, 0);
					}else if(busType.equals("consult")){//咨询
						ConsultDAO dao = DAOFactory.getDAO(ConsultDAO.class);
						Consult consult = dao.getById(busId);
						if(consult.getConsultStatus()==3){
							//健康2.1.1版本后:拒绝成功(不考虑是否退款成功)，给申请人发送微信推送消息、推送消息、短信(图文咨询)
						}else if(consult.getConsultStatus()==9){
							//咨询取消
						}

					}else if(busType.equals(RecipeService.WX_RECIPE_BUSTYPE)){

					}else if(busType.equals("appoint")){
						AppointRecordDAO appointRecorddao = DAOFactory.getDAO(AppointRecordDAO.class);
						AppointRecord a = appointRecorddao.getByAppointRecordId(busId);
						//当天结算失败要退款 并且不能继续支付
						if(a.getRecordType().equals(1)){
							a.setPayFlag(3);
							a.setAppointStatus(6);
						}else{
							if(1==remark){
								//主动发起取消申请
								a.setPayFlag(3);
								a.setAppointStatus(6);
							}else{
								//预约结算失败要退款 并且能继续支付
								a.setPayFlag(0);
								a.setAppointStatus(4);
							}
						}
						appointRecorddao.update(a);
					}else if(busType.equals("outpatient")){
						OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
						Outpatient o = outpatientDAO.getById(busId);
						o.setPayflag(3);
						o.setTradeStatus(34);
						outpatientDAO.update(o);
					}else if(busType.equals("mindgift")){//心意
						MindGiftDAO mindDao = DAOFactory.getDAO(MindGiftDAO.class);
						MindGift mind = mindDao.get(busId);

						//退款成功
						if(mind.getMindGiftStatus().intValue()== MindGiftConstant.MINDGIFT_STATUS_CANCEL){
							mindDao.updateMindGiftForRefundById(PayConstant.PAY_FLAG_REFUND_SUCCESS,new Date(),mind.getMindGiftId());
						}else{
							logger.info("心意单["+mind.getMindGiftId()+"]不是取消状态，不能更新退款状态");
						}

					}else if(busType.equals("prepay")){
						PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
		                payDAO.updatePayFlagByID(3,busId);
					}else if(busType.equals("appointcloud")){
						AppointRecordDAO appointRecorddao = DAOFactory.getDAO(AppointRecordDAO.class);
						AppointRecord a = appointRecorddao.getByAppointRecordId(busId);
						appointRecorddao.updateAppointStatusForcloudClinic(null,a,3,6);
					}
				}else{
					if(code.equals("REFUND-FAIL")){//微信退款失败
						Thread.sleep((tryCount*1000)+1000);
						sendToRefund();
					}else{//业务数据不足导致的无法发起退款
						logger.error("busId=" + busId + ",busType=" + busType + " 因为业务数据原因不再发起退款重试！");
					}
				}
			} else {
//				if("REFUND-FAIL".equals(code)){
				if(busType.equals("appoint")){
					//挂号预约支付退款失败短信
					AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
					AppointRecord a = appointRecordDAO.getByAppointRecordId(busId);


					a.setPayFlag(4);
					a.setAppointStatus(7);
					a.setCancelResean("退款失败");
					appointRecordDAO.update(a);
					appointRecordDAO.sendSMS_PayRefundFail(busId);
				}else if(busType.equals("mindgift")) {//心意退款失败
					MindGiftDAO mindDao = DAOFactory.getDAO(MindGiftDAO.class);
					MindGift mind = mindDao.get(busId);

					//退款失败
					if(mind.getMindGiftStatus().intValue()== MindGiftConstant.MINDGIFT_STATUS_CANCEL){
						mindDao.updateMindGiftForRefundById(PayConstant.PAY_FLAG_REFUND_SUCCESS,new Date(),mind.getMindGiftId());
					}else{
						logger.info("心意单["+mind.getMindGiftId()+"]不是取消状态，不能更新退款状态");
					}
				}else if(busType.equals("prepay")){
					PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
	                payDAO.updatePayFlagByID(4,busId);
				}else if(busType.equals("appointcloud")){
					AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
					AppointRecord a = appointRecordDAO.getByAppointRecordId(busId);
					appointRecordDAO.updateAppointStatusForcloudClinic(null,a,4,7);
					appointRecordDAO.sendSMS_PayRefundFail(busId);
				}else {
					SmsInfo info = new SmsInfo();
					info.setBusId(busId);
					info.setBusType(busType);
					info.setSmsType("refundFailMsg");//微信退款失败通知
					info.setStatus(0);
					info.setOrganId(0);// 短信服务对应的机构， 0代表通用机构
					AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
					exe.execute();
					logger.error("调用退款服务超过重试次数！发送失败短信通知");
				}
//				}
			}
		} catch (TransportException e) {
			logger.error("TransportException预约：发起重试" + e.getMessage());
			try {
				Thread.sleep((tryCount*1000)+1000);
			} catch (InterruptedException e1) {
				logger.error(e1);
			}
			sendToRefund();
		} catch (Exception e) {
			logger.error("Exception错误：发起重试" + e.getMessage());
			try {
				Thread.sleep((tryCount*1000)+1000);
			} catch (InterruptedException e1) {
				logger.error(e1);
			}
			sendToRefund();
		}
	}

}
