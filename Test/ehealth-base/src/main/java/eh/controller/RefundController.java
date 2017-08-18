package eh.controller;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.PayBusinessDAO;
import eh.bus.dao.TransferDAO;
import eh.entity.bus.Consult;
import eh.entity.bus.PayBusiness;
import eh.entity.bus.Transfer;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import eh.wxpay.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

@Controller("refundController")
public class RefundController {

	private static final Log logger = LogFactory.getLog(RefundController.class);

	/**
	 * 用于接收支付平台退款结果异步通知
	 * 
	 * @param httpServletRequest
	 * @param res
	 */
	@RequestMapping(value = "wxpay/refundNotify")
	public void refundNotify(HttpServletRequest httpServletRequest,
			HttpServletResponse res) {

		// 处理咨询业务逻辑，更新支付标志
		Map<String, String> requestMap = Util.buildRequest(httpServletRequest);
		logger.info("收到支付平台退款异步通知,请求参数：" + JSONUtils.toString(requestMap));
		PrintWriter writer;
		try {
			writer = res.getWriter();
			if (StringUtils.isEmpty(requestMap.get("appid")) == false) {// 说明查询成功
				String outTradeNo = requestMap.get("out_trade_no");// 商户订单号
				// String tradeNo = requestMap.get("transaction_id");
				if (outTradeNo.startsWith(BusTypeEnum.CONSULT.refundPrefix())) {// 咨询
					ConsultDAO dao = DAOFactory.getDAO(ConsultDAO.class);
					Consult c = dao.getByOutTradeNo(outTradeNo);
					if (c == null) {
						writer.println("failed");
						return;
					}
					if (c.getPayflag() == 1) {// 已处理
						writer.println("success");
						return;
					}
					c.setPayflag(3);// 退款成功
					dao.update(c);
				} else if (outTradeNo.startsWith(BusTypeEnum.TRANSFER.refundPrefix())) {// 转诊
					TransferDAO dao = DAOFactory.getDAO(TransferDAO.class);
					Transfer t = dao.getByOutTradeNo(outTradeNo);
					if (t == null) {
						writer.println("failed");
						return;
					}
					if (t.getPayflag() == 1) {
						writer.println("success");
						return;
					}
					t.setPayflag(3);
					dao.update(t);
				}else if (outTradeNo.startsWith(BusTypeEnum.SIGN.refundPrefix())) {//签约
					// 签约退款
					SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
					SignRecord signRecord = signRecordDAO.getByOutTradeNo(outTradeNo);
					if (signRecord == null || signRecord.getPayFlag() == null ) {
						writer.println("failed");
						return;
					}else if (signRecord.getPayFlag() == 1) {
						writer.println("success");
						return;
					}
					signRecord.setPayFlag(3);
					signRecordDAO.update(signRecord);
				}else if (outTradeNo.startsWith(BusTypeEnum.PREPAY.refundPrefix())){//住院缴费
					PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
					PayBusiness payBus = payDAO.getByOutTradeNo(outTradeNo);
					if (payBus == null || payBus.getPayflag() == null){
						writer.println("failed");
						return;
					}else if(payBus.getPayflag() == 1){
						writer.println("success");
						return;
					}
					payBus.setPayflag(3);
					payDAO.update(payBus);

				}

				writer.println("success");
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}
}
