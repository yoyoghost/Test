package eh.wxpay.service;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.xml.XMLHelper;
import eh.bus.dao.*;
import eh.cdr.dao.RecipeOrderDAO;
import eh.cdr.service.RecipeService;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.cdr.RecipeOrder;
import eh.entity.mindgift.MindGift;
import eh.entity.mpi.Patient;
import eh.entity.mpi.SignRecord;
import eh.entity.msg.SmsInfo;
import eh.mindgift.dao.MindGiftDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.dao.SignRecordDAO;
import eh.task.executor.AliSmsSendExecutor;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.constant.WxConstant;
import eh.wxpay.util.PayUtil;
import eh.wxpay.util.UrlPost;
import eh.wxpay.util.XMLParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WxRefundService extends RefundService {
    private static final Log logger = LogFactory.getLog(WxRefundService.class);

    /**
     * @param busId   业务id
     * @param busType 业务类型:转诊transfer,咨询consult，处方recipe；预约挂号appoint，门诊缴费outpatient，住院预交prepay
     * @return String
     * @function 退款申请服务
     * @author zhangjr
     * @date 2015-12-17
     */

    @RpcService
    public Map<String, Object> refund(Integer busId, String busType) {
        Map<String, Object> resultMap = null;// 返回对象
        Document req_data_doc = getRefundReqMap(busId, busType);
        logger.info("【微信】业务请求数据:" + req_data_doc.getRootElement().asXML() + ",业务类型=" + busType + ",业务id=" + busId);
        String code = req_data_doc.getRootElement().elementText(
                "code");
        try {
            if (code.equals("DATA-FAIL")) {
                resultMap = XMLParser.getMapFromXML(req_data_doc
                        .getRootElement().asXML());
                return resultMap;
            }
            String applyno = req_data_doc.getRootElement().elementText("applyno");

            Map<String, String> paramMap = new HashMap<String, String>();
            String req_data_xml = req_data_doc.getRootElement().asXML();
            paramMap.put("req_data", req_data_xml);
            paramMap.put("charset", "utf-8");
            paramMap.put("service", WxConstant.Service.WX_REFUND);
            paramMap.put("format", "xml");// 未知
            paramMap.put("version", "v3.0");
            logger.info("【微信】原始请求参数:" + JSONUtils.toString(paramMap)
                    + ",业务类型" + busType + ",业务id" + busId);

            String responseXml = UrlPost.getHttpPostResult(
                    PayUtil.getWxRefundUrl(), paramMap, "【微信退款申请】");
            resultMap = XMLParser.getMapFromXML(responseXml);
            logger.info("【微信】支付平台应答数据:" + resultMap + ",业务类型" + busType
                    + ",业务id" + busId);

            String return_code = (String) resultMap.get("return_code");
            if ("SUCCESS".equals(return_code)) {
                String resultcode = (String) resultMap.get("result_code");
                if (resultcode != null && resultcode.equals("SUCCESS")) {//SUCCESS退款申请接收成功,默认为退款中
                    //退款中
                    updatePayflag(applyno, 3);
                    resultMap.put("code", "SUCCESS");
                } else {
                    //退款失败
                    updatePayflag(applyno, 4);
                    resultMap.put("code", "REFUND-FAIL");
                }
            } else {
                //处方收到支付平台出错，用户其实已收到退款，但需将标记为置为 退款失败，方便查询数据
                    updatePayflag(applyno, 4);
            }
            logger.info("【微信】云平台退款服务返回数据:" + JSONUtils.toString(resultMap));
            return resultMap;
        } catch (ParserConfigurationException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage());
        } catch (SAXException e) {
            logger.error(e.getMessage());
        }
        logger.info("【微信】云平台退款服务返回数据:" + JSONUtils.toString(resultMap));
        return resultMap;
    }

    /**
     * @param busId   业务id
     * @param busType 业务类型:转诊transfer,咨询consult
     * @return String
     * @function 退款查询服务
     * @author zhangjr
     * @date 2015-12-17
     */
    @RpcService
    public Map<String, Object> refundQuery(Integer busId, String busType) {
        Map<String, Object> resultMap = null;// 返回对象
        Document req_data_doc = getRefundQueryMap(busId, busType);
        logger.info("【微信】业务请求数据:" + req_data_doc.getRootElement().asXML() + ",业务类型" + busType + ",业务id" + busId);
        String code = req_data_doc.getRootElement().elementText(
                "code");
        try {
            if (code.equals("FAIL")) {
                resultMap = XMLParser.getMapFromXML(req_data_doc
                        .getRootElement().asXML());
                return resultMap;
            }
            String applyno = req_data_doc.getRootElement().elementText("applyno");

            Map<String, String> paramMap = new HashMap<String, String>();
            String req_data_xml = req_data_doc.getRootElement().asXML();
            paramMap.put("req_data", req_data_xml);
            paramMap.put("charset", "utf-8");
            paramMap.put("service", WxConstant.Service.WX_REFUND_QUERY);
            paramMap.put("format", "xml");// 未知
            paramMap.put("version", "v3.0");
            logger.info("【微信】原始请求参数:" + JSONUtils.toString(paramMap));
            String responseXml = UrlPost.getHttpPostResult(
                    PayUtil.getWxRefundQueryUrl(), paramMap, "【微信退款查询】");
            resultMap = XMLParser.getMapFromXML(responseXml);
            logger.info("【微信】支付平台应答数据:" + resultMap);
            String refund_status = (String) resultMap.get("refund_status_0");
            if (StringUtils.isEmpty(refund_status) == false) {
                if (refund_status.equals("SUCCESS")) {
                    // 更新业务支付状态
                    updatePayflag(applyno, 3);//退款成功
                } else if (refund_status.equals("PROCESSING")) {
                    updatePayflag(applyno, 2);//退款中
                } else {
                    updatePayflag(applyno, 4);//支付失败
                }
                resultMap.put("code", "SUCCESS");
            } else {
                resultMap.put("code", "FAIL");
            }

        } catch (ParserConfigurationException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage());
        } catch (SAXException e) {
            logger.error(e.getMessage());
        }
        logger.info("【微信】微信服务返回数据:" + JSONUtils.toString(resultMap));
        return resultMap;
    }

    /**
     * @param busId
     * @param busType
     * @return String
     * @function 退款查询服务
     * @author zhangjr
     * @date 2015-12-17
     */
    private Document getRefundQueryMap(Integer busId, String busType) {
        logger.info("【微信】开始请求前业务数据查询...");
        Document document = DocumentHelper.createDocument();
        Element root = document.addElement("xml");
        if (busType.equals(BusTypeEnum.SIGN.getCode())) {// 签约
            SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
            SignRecord signRecord = signRecordDAO.get(busId);
            if (signRecord == null) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该签约业务记录不存在");
                return document;
            }
            //商户订单号不能为空
            String applyNo = signRecord.getOutTradeNo();
            if (StringUtils.isEmpty(applyNo)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("商户订单号不存在");
                return document;
            }
            //支付状态为支付或未支付状态不能查询退款详情
            Integer payflag = signRecord.getPayFlag();
            if (payflag != null && (payflag == 0 || payflag == 1)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该签约单没有退款信息");
                return document;
            } else {
                root.addElement("code").setText("SUCCESS");
            }
            String tradeno = signRecord.getTradeNo();// 微信订单号
            String payway = signRecord.getPayWay();// 支付方式
            root.addElement("applyno").setText(applyNo);
            root.addElement("tradeno").setText(tradeno == null ? "" : tradeno);
            root.addElement("payway").setText(payway);
            root.addElement("trade_type").setText(
                    payway.equals("40") ? "JSAPI" : "APP");
            root.addElement("device_info").setText(
                    payway.equals("40") ? "WEB" : "APP");
            root.addElement("organid").setText(signRecord.getPayOrganId());

        } else if (busType.equals(BusTypeEnum.TRANSFER.getCode())) {// 转诊
            TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
            Transfer transfer = transferDAO.getById(busId);
            if (transfer == null) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该转诊业务记录不存在");
                return document;
            }
            //商户订单号不能为空
            String applyNo = transfer.getOutTradeNo();
            if (StringUtils.isEmpty(applyNo)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("商户订单号不存在");
                return document;
            }
            //支付状态为支付或未支付状态不能查询退款详情
            Integer payflag = transfer.getPayflag();
            if (payflag != null && (payflag == 0 || payflag == 1)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该转诊单没有退款信息");
                return document;
            } else {
                root.addElement("code").setText("SUCCESS");
            }
            String tradeno = transfer.getTradeNo();// 微信订单号
            String payway = transfer.getPayWay();// 支付方式
            root.addElement("applyno").setText(applyNo);
            root.addElement("tradeno").setText(tradeno == null ? "" : tradeno);
            root.addElement("payway").setText(payway);
            root.addElement("trade_type").setText(
                    payway.equals("40") ? "JSAPI" : "APP");
            root.addElement("device_info").setText(
                    payway.equals("40") ? "WEB" : "APP");
            root.addElement("organid").setText(transfer.getPayOrganId());

        } else if (busType.equals(BusTypeEnum.RECIPE.getCode())) {
            //处方
            RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
//            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
//            Recipe recipe = recipeDAO.getByRecipeId(busId);
            RecipeOrder order = orderDAO.get(busId);
            if (order == null) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该处方订单记录不存在");
                return document;
            }
            //商户订单号不能为空
            String applyNo = order.getOutTradeNo();
            if (StringUtils.isEmpty(applyNo)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("商户订单号不存在");
                return document;
            }
            //支付状态为支付或未支付状态不能查询退款详情
            Integer payflag = order.getPayFlag();
            if (payflag != null && (payflag == 0 || payflag == 1)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该处方单没有退款信息");
                return document;
            } else {
                root.addElement("code").setText("SUCCESS");
            }
            String tradeno = order.getTradeNo();// 微信订单号
            String payway = order.getWxPayWay();// 支付方式
            root.addElement("applyno").setText(applyNo);
            root.addElement("tradeno").setText(tradeno == null ? "" : tradeno);
            root.addElement("payway").setText(payway);
            root.addElement("trade_type").setText(
                    payway.equals("40") ? "JSAPI" : "APP");
            root.addElement("device_info").setText(
                    payway.equals("40") ? "WEB" : "APP");
            root.addElement("organid").setText(order.getPayOrganId());

        } else if (BusTypeEnum.CONSULT.getCode().equals(busType)) {// 咨询
            ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
            Consult consult = consultDAO.getById(busId);
            if (consult == null) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该咨询业务记录不存在");
                return document;
            }
            //商户订单号不能为空
            String applyNo = consult.getOutTradeNo();
            if (StringUtils.isEmpty(applyNo)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("商户订单号不存在");
                return document;
            }
            Integer payflag = consult.getPayflag();
            if (payflag != null && (payflag == 0 || payflag == 1)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该咨询单没有退款信息");
                return document;
            } else {
                root.addElement("code").setText("SUCCESS");
            }
            String tradeno = consult.getTradeNo();
            String payway = consult.getPayWay();// 支付方式
            root.addElement("applyno").setText(applyNo);
            root.addElement("tradeno").setText(tradeno == null ? "" : tradeno);
            root.addElement("payway").setText(payway);
            root.addElement("trade_type").setText(
                    payway.equals("40") ? "JSAPI" : "APP");
            root.addElement("device_info").setText(
                    payway.equals("40") ? "WEB" : "APP");
            root.addElement("organid").setText(consult.getPayOrganId());

        } else if (busType.equals(BusTypeEnum.APPOINT.getCode()) || busType.equals(BusTypeEnum.APPOINTPAY.getCode())) {// 挂号
            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
            if (appointRecord == null) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该挂号业务记录不存在");
                return document;
            }
            //商户订单号不能为空
            String applyNo = appointRecord.getOutTradeNo();
            if (StringUtils.isEmpty(applyNo)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("商户订单号不存在");
                return document;
            }
            //支付状态为支付或未支付状态不能查询退款详情
            Integer payflag = appointRecord.getPayFlag();
            if (payflag != null && (payflag == 0 || payflag == 1)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该挂号单没有退款信息");
                return document;
            } else {
                root.addElement("code").setText("SUCCESS");
            }
            String tradeno = appointRecord.getTradeNo();// 微信订单号
            String payway = appointRecord.getPayWay();// 支付方式
            root.addElement("applyno").setText(applyNo);
            root.addElement("tradeno").setText(tradeno == null ? "" : tradeno);
            root.addElement("payway").setText(payway);
            root.addElement("trade_type").setText(
                    payway.equals("40") ? "JSAPI" : "APP");
            root.addElement("device_info").setText(
                    payway.equals("40") ? "WEB" : "APP");
            root.addElement("organid").setText(appointRecord.getPayOrganId());
        } else if(busType.equals(BusTypeEnum.PREPAY.getCode())) {
            PayBusinessDAO payBusinessDAO = DAOFactory.getDAO(PayBusinessDAO.class);
            PayBusiness payBusiness = payBusinessDAO.getById(busId);
            if (payBusiness == null) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该支付业务记录不存在");
                return document;
            }
            //商户订单号不能为空
            String applyNo = payBusiness.getOutTradeNo();
            if (StringUtils.isEmpty(applyNo)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("商户订单号不存在");
                return document;
            }
            //支付状态为支付或未支付状态不能查询退款详情
            Integer payflag = payBusiness.getPayflag();
            if (payflag != null && (payflag == 0 || payflag == 1)) {
                root.addElement("code").setText("FAIL");
                root.addElement("msg").setText("该支付单没有退款信息");
                return document;
            } else {
                root.addElement("code").setText("SUCCESS");
            }
            String tradeno = payBusiness.getTradeNo();// 微信订单号
            String payway = payBusiness.getPayWay();// 支付方式
            root.addElement("applyno").setText(applyNo);
            root.addElement("tradeno").setText(tradeno == null ? "" : tradeno);
            root.addElement("payway").setText(payway);
            root.addElement("trade_type").setText(
                    payway.equals("40") ? "JSAPI" : "APP");
            root.addElement("device_info").setText(
                    payway.equals("40") ? "WEB" : "APP");
            root.addElement("organid").setText(payBusiness.getPayOrganId());
        } else if(busType.equals(BusTypeEnum.OUTPATIENT.getCode())){
            {
                OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
                Outpatient outpatient = outpatientDAO.getById(busId);
                if (outpatient == null) {
                    root.addElement("code").setText("FAIL");
                    root.addElement("msg").setText("该支付业务记录不存在");
                    return document;
                }
                //商户订单号不能为空
                String applyNo = outpatient.getOutTradeNo();
                if (StringUtils.isEmpty(applyNo)) {
                    root.addElement("code").setText("FAIL");
                    root.addElement("msg").setText("商户订单号不存在");
                    return document;
                }
                //支付状态为支付或未支付状态不能查询退款详情
                Integer payflag = outpatient.getPayflag();
                if (payflag != null && (payflag == 0 || payflag == 1)) {
                    root.addElement("code").setText("FAIL");
                    root.addElement("msg").setText("该支付单没有退款信息");
                    return document;
                } else {
                    root.addElement("code").setText("SUCCESS");
                }
                String tradeno = outpatient.getTradeNo();// 微信订单号
                String payway = outpatient.getPayWay();// 支付方式
                root.addElement("applyno").setText(applyNo);
                root.addElement("tradeno").setText(tradeno == null ? "" : tradeno);
                root.addElement("payway").setText(payway);
                root.addElement("trade_type").setText(
                        payway.equals("40") ? "JSAPI" : "APP");
                root.addElement("device_info").setText(
                        payway.equals("40") ? "WEB" : "APP");
                root.addElement("organid").setText(outpatient.getPayOrganId());
            }
        }
        return document;
    }

    /**
     * @param busId
     * @param busType
     * @return String
     * @function 组装退款查询所需业务数据
     * @author zhangjr
     * @date 2015-12-22
     */
    private Document getRefundReqMap(Integer busId, String busType) {
        logger.info("【微信】开始请求前业务数据查询...");
        Document document = null;
        if (busType.equals(BusTypeEnum.TRANSFER.getCode())) {
            document = getTransferRefundDoc(busId);
        } else if (busType.equals(BusTypeEnum.RECIPE.getCode())) {
            document = getRecipeRefundDoc(busId);
        } else if (busType.equals(BusTypeEnum.CONSULT.getCode())) {
            document = getConsultRefundDoc(busId);
        } else if (busType.equals(BusTypeEnum.APPOINT.getCode()) || busType.equals(BusTypeEnum.APPOINTPAY.getCode())) {
            document = getAppointRefundDoc(busId);
        } else if (busType.equals(BusTypeEnum.SIGN.getCode())) {
            document = getSignRefundDoc(busId);
        } else if(busType.equals(BusTypeEnum.PREPAY.getCode())){
            document = getPrepayRefundDoc(busId);
        }else if(busType.equals("mindgift")){
            document=getMindGiftRefundDoc(busId);
        }else if(busType.equals(BusTypeEnum.OUTPATIENT.getCode())) {
            document = getOutpatientRefundDoc(busId);
        }
        return document;
    }

    // 获取转诊业务数据
    private Document getTransferRefundDoc(Integer busId) {
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Transfer transfer = transferDAO.getById(busId);
        if (transfer == null) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该转诊业务记录不存在");
            return doc;
        }
        //如果商户订单号不存在
        String applyNo = transfer.getOutTradeNo();
        if (StringUtils.isEmpty(applyNo)) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payflag = transfer.getPayflag();
        Double transferCost = transfer.getTransferCost();
        if (payflag == null || payflag == 0 || payflag == 2) {
            String msg = "";
            if (payflag == null) {
                payflag = 0;
            }
            switch (payflag) {
                case 0:
                    msg = "尚未付费，不能发起支付";
                    break;
                case 2:
                    msg = "已有一笔金额在退款中,暂不能发起退款";
                    break;
                default:
                    break;
            }
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该转诊单" + msg);
            return doc;
        } else {
            if (transferCost > 0.00) {
                root.addElement("code").setText("SUCCESS");
            } else {
                root.addElement("code").setText("DATA-FAIL");
                root.addElement("msg").setText("待退款金额必须大于0.00");
                return doc;
            }
        }

        Patient patient = patientDAO.getByMpiId(transfer.getMpiId());
        String payway = transfer.getPayWay();
        String tradeNo = transfer.getTradeNo();
        String pName = patient.getPatientName();
        String refundNo = BusTypeEnum.TRANSFER.getRefundNo();
        root.addElement("payname").setText("");
        root.addElement("pname").setText(pName);
        root.addElement("applyno").setText(applyNo);
        root.addElement("total_fee").setText(String.valueOf(transferCost));
        root.addElement("batchno").setText(refundNo);// 退款商户号
        root.addElement("tradeno").setText(tradeNo == null ? "" : tradeNo);
        root.addElement("refund_fee").setText(String.valueOf(transferCost));
        root.addElement("payway").setText(payway);
        root.addElement("device_info").setText(
                payway.equals("40") ? "WEB" : "APP");
        root.addElement("trade_type").setText(
                payway.equals("40") ? "JSAPI" : "APP");
        root.addElement("subject").setText("转诊费用退款");
        logger.info("微信咨询退款organId值无效，支付平台根据applyno取值");
        root.addElement("organid").setText(transfer.getPayOrganId());
        root.addElement("comments").setText("转诊超时/拒绝自动退款");
        return doc;
    }

    // 获取咨询业务数据
    private Document getConsultRefundDoc(Integer busId) {
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Consult consult = consultDAO.getById(busId);
        if (consult == null) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该咨询业务记录不存在");
            return doc;
        }
        //如果商户订单号不存在
        String applyNo = consult.getOutTradeNo();
        if (StringUtils.isEmpty(applyNo)) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payflag = consult.getPayflag();
        Double consultCost = consult.getActualPrice()==null?consult.getConsultCost():consult.getActualPrice();
        if (payflag == null || payflag == 0 || payflag == 2) {
            String msg = "";
            if (payflag == null) {
                payflag = 0;
            }
            switch (payflag) {
                case 0:
                    msg = "尚未付费，不能发起支付";
                    break;
                case 2:
                    msg = "已有一笔金额在退款中,暂不能发起退款";
                    break;
                default:
                    break;
            }
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该咨询单" + msg);
            return doc;
        } else {
            if (consultCost > 0.00) {
                root.addElement("code").setText("SUCCESS");
            } else {
                root.addElement("code").setText("DATA-FAIL");
                root.addElement("msg").setText("待退款金额必须大于0.00");
                return doc;
            }
        }

        Patient patient = patientDAO.getByMpiId(consult.getMpiid());
        String payway = consult.getPayWay();
        String tradeNo = consult.getTradeNo();
        String pName = patient.getPatientName();
        String refundNo = BusTypeEnum.CONSULT.getRefundNo();
        root.addElement("payname").setText("");
        root.addElement("pname").setText(pName);
        root.addElement("applyno").setText(applyNo);
        root.addElement("total_fee").setText(String.valueOf(consultCost));
        root.addElement("batchno").setText(refundNo);// 退款商户号
        root.addElement("tradeno").setText(tradeNo == null ? "" : tradeNo);
        root.addElement("refund_fee").setText(String.valueOf(consultCost));
        root.addElement("payway").setText(payway);
        root.addElement("device_info").setText(
                payway.equals("40") ? "WEB" : "APP");
        root.addElement("trade_type").setText(
                payway.equals("40") ? "JSAPI" : "APP");
        root.addElement("subject").setText("咨询费退款");
        logger.info("微信咨询退款organId值无效，支付平台根据applyno取值");
        root.addElement("organid").setText(consult.getPayOrganId());
        root.addElement("comments").setText("咨询超时/拒绝自动退款");
        return doc;
    }

    // 获取心意业务数据
    private Document getMindGiftRefundDoc(Integer busId) {
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        MindGiftDAO mindGiftDAO = DAOFactory.getDAO(MindGiftDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        MindGift mind = mindGiftDAO.get(busId);
        if (mind == null) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该心意业务记录不存在");
            return doc;
        }
        //如果商户订单号不存在
        String applyNo = mind.getOutTradeNo();
        if (StringUtils.isEmpty(applyNo)) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payflag = mind.getPayFlag();
        Double cost = mind.getActualPrice()==null?mind.getAmount():mind.getActualPrice();
        if (payflag == null || payflag == 0 ) {
            String msg = "";
            if (payflag == null) {
                payflag = 0;
            }

            //发起退款前已将支付状态payflag改为退款中2，此处只判断是否支付
            switch (payflag) {
                case 0:
                    msg = "尚未付费，不能发起支付";
                    break;
                default:
                    break;
            }
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该心意单" + msg);
            return doc;
        } else {
            if (cost > 0.00) {
                root.addElement("code").setText("SUCCESS");
            } else {
                root.addElement("code").setText("DATA-FAIL");
                root.addElement("msg").setText("待退款金额必须大于0.00");
                return doc;
            }
        }

        Patient patient = patientDAO.getByMpiId(mind.getMpiId());
        String payway = mind.getPayWay();
        String tradeNo = mind.getTradeNo();
        String pName = patient.getPatientName();
        String refundNo = BusTypeEnum.MINDGIFT.getRefundNo();
        root.addElement("payname").setText("");
        root.addElement("pname").setText(pName);
        root.addElement("applyno").setText(applyNo);
        root.addElement("total_fee").setText(String.valueOf(cost));
        root.addElement("batchno").setText(refundNo);// 退款商户号
        root.addElement("tradeno").setText(tradeNo == null ? "" : tradeNo);
        root.addElement("refund_fee").setText(String.valueOf(cost));
        root.addElement("payway").setText(payway);
        root.addElement("device_info").setText(
                payway.equals("40") ? "WEB" : "APP");
        root.addElement("trade_type").setText(
                payway.equals("40") ? "JSAPI" : "APP");
        root.addElement("subject").setText("心意费用退款");
        logger.info("微信心意费用退款organId值无效，支付平台根据applyno取值");
        root.addElement("organid").setText(mind.getPayOrganId());
        root.addElement("comments").setText("心意费用退款");
        return doc;
    }


    /**
     * 处方微信退款
     *
     * @param busId
     * @return
     */
    private Document getRecipeRefundDoc(Integer busId) {
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
//        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

        RecipeOrder order = orderDAO.get(busId);
        if (order == null) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该处方订单记录不存在");
            return doc;
        }
        //如果商户订单号不存在
        String applyNo = order.getOutTradeNo();
        if (StringUtils.isEmpty(applyNo)) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payflag = order.getPayFlag();
        Double cost = order.getActualPrice();
        if (payflag == null || payflag == 0 || payflag == 2) {
            String msg = "";
            if (payflag == null) {
                payflag = 0;
            }
            switch (payflag) {
                case 0:
                    msg = "尚未付费，不能发起退款";
                    break;
                case 2:
                    msg = "已有一笔金额在退款中,暂不能发起退款";
                    break;
                default:
                    break;
            }
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("处方单" + msg);
            return doc;
        } else {
            if (cost > 0d) {
                root.addElement("code").setText("SUCCESS");
            } else {
                root.addElement("code").setText("DATA-FAIL");
                root.addElement("msg").setText("待退款金额必须大于0.00");
                return doc;
            }
        }

        Patient patient = patientDAO.getByMpiId(order.getMpiId());
        String payway = order.getWxPayWay();
        String tradeNo = order.getTradeNo();
        String pName = patient.getPatientName();
        String refundNo = BusTypeEnum.RECIPE.getRefundNo();
        root.addElement("payname").setText("");
        root.addElement("pname").setText(pName);
        root.addElement("applyno").setText(applyNo);
        root.addElement("total_fee").setText(String.valueOf(cost.doubleValue()));
        root.addElement("batchno").setText(refundNo);// 退款商户号
        root.addElement("tradeno").setText(tradeNo == null ? "" : tradeNo);
        root.addElement("refund_fee").setText(String.valueOf(cost.doubleValue()));
        root.addElement("payway").setText(payway);
        root.addElement("device_info").setText(
                payway.equals("40") ? "WEB" : "APP");
        root.addElement("trade_type").setText(
                payway.equals("40") ? "JSAPI" : "APP");
        root.addElement("subject").setText("处方单费用");

        root.addElement("organid").setText(order.getPayOrganId());

        root.addElement("comments").setText("处方单自动退款");
        return doc;
    }

    // 获取挂号业务数据
    private Document getAppointRefundDoc(Integer busId) {
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
        if (appointRecord == null) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该转诊业务记录不存在");
            return doc;
        }
        //如果商户订单号不存在
        String applyNo = appointRecord.getOutTradeNo();
        if (StringUtils.isEmpty(applyNo)) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payflag = appointRecord.getPayFlag();
        Double transferCost = appointRecord.getActualPrice()!=null?appointRecord.getActualPrice():appointRecord.getClinicPrice();
        if (payflag == null || payflag == 0 || payflag == 2) {
            String msg = "";
            if (payflag == null) {
                payflag = 0;
            }
            switch (payflag) {
                case 0:
                    msg = "尚未付费，不能发起支付";
                    break;
                case 2:
                    msg = "已有一笔金额在退款中,暂不能发起退款";
                    break;
                default:
                    break;
            }
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该转诊单" + msg);
            return doc;
        } else {
            if (transferCost > 0.00) {
                root.addElement("code").setText("SUCCESS");
            } else {
                root.addElement("code").setText("DATA-FAIL");
                root.addElement("msg").setText("待退款金额必须大于0.00");
                return doc;
            }
        }

        Patient patient = patientDAO.getByMpiId(appointRecord.getMpiid());
        String payway = appointRecord.getPayWay();
        String tradeNo = appointRecord.getTradeNo();
        String pName = patient.getPatientName();
        String refundNo = BusTypeEnum.APPOINT.getRefundNo();
        root.addElement("payname").setText("");
        root.addElement("pname").setText(pName);
        root.addElement("applyno").setText(applyNo);
        root.addElement("total_fee").setText(String.valueOf(transferCost));
        root.addElement("batchno").setText(refundNo);// 退款商户号
        root.addElement("tradeno").setText(tradeNo == null ? "" : tradeNo);
        root.addElement("refund_fee").setText(String.valueOf(transferCost));
        root.addElement("payway").setText(payway);
        root.addElement("device_info").setText(
                payway.equals("40") ? "WEB" : "APP");
        root.addElement("trade_type").setText(
                payway.equals("40") ? "JSAPI" : "APP");
        root.addElement("subject").setText("转诊费用退款");
        logger.info("微信咨询退款organId值无效，支付平台根据applyno取值");
        root.addElement("organid").setText(appointRecord.getPayOrganId());
        root.addElement("comments").setText("转诊超时/拒绝自动退款");
        return doc;
    }

    // 获取门诊缴费业务数据
    private Document getOutpatientRefundDoc(Integer busId) {
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Outpatient outpatient = outpatientDAO.getById(busId);
        if (outpatient == null) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该转诊业务记录不存在");
            return doc;
        }
        //如果商户订单号不存在
        String applyNo = outpatient.getOutTradeNo();
        if (StringUtils.isEmpty(applyNo)) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payflag = outpatient.getPayflag();
        Double transferCost = outpatient.getTotalFee();
        if (payflag == null || payflag == 0 || payflag == 2) {
            String msg = "";
            if (payflag == null) {
                payflag = 0;
            }
            switch (payflag) {
                case 0:
                    msg = "尚未付费，不能发起支付";
                    break;
                case 2:
                    msg = "已有一笔金额在退款中,暂不能发起退款";
                    break;
                default:
                    break;
            }
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该转诊单" + msg);
            return doc;
        } else {
            if (transferCost > 0.00) {
                root.addElement("code").setText("SUCCESS");
            } else {
                root.addElement("code").setText("DATA-FAIL");
                root.addElement("msg").setText("待退款金额必须大于0.00");
                return doc;
            }
        }

        Patient patient = patientDAO.getByMpiId(outpatient.getMpiId());
        String payway = outpatient.getPayWay();
        String tradeNo = outpatient.getTradeNo();
        String pName = patient.getPatientName();
        String refundNo = BusTypeEnum.OUTPATIENT.getRefundNo();
        root.addElement("payname").setText("");
        root.addElement("pname").setText(pName);
        root.addElement("applyno").setText(applyNo);
        root.addElement("total_fee").setText(String.valueOf(transferCost));
        root.addElement("batchno").setText(refundNo);// 退款商户号
        root.addElement("tradeno").setText(tradeNo == null ? "" : tradeNo);
        root.addElement("refund_fee").setText(String.valueOf(transferCost));
        root.addElement("payway").setText(payway);
        root.addElement("device_info").setText(
                payway.equals("40") ? "WEB" : "APP");
        root.addElement("trade_type").setText(
                payway.equals("40") ? "JSAPI" : "APP");
        root.addElement("subject").setText("转诊费用退款");
        logger.info("微信咨询退款organId值无效，支付平台根据applyno取值");
        root.addElement("organid").setText(outpatient.getPayOrganId());
        root.addElement("comments").setText("转诊超时/拒绝自动退款");
        return doc;
    }

    /**
     * 获取签约业务数据
     */
    private Document getSignRefundDoc(Integer busId) {
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
        SignRecord signRecord = signRecordDAO.get(busId);
        if (signRecord == null) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该签约业务记录不存在");
            return doc;
        }
        //如果商户订单号不存在
        String applyNo = signRecord.getOutTradeNo();
        if (StringUtils.isEmpty(applyNo)) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payFlag = signRecord.getPayFlag() == null ? Integer.valueOf(0) : signRecord.getPayFlag();
        Double signCost = signRecord.getActualPrice()!=null?signRecord.getActualPrice():signRecord.getSignCost();
        if (payFlag == 0 || payFlag == 2) {
            String msg = Integer.valueOf(0).equals(payFlag) ? "尚未付费，不能发起支付" : "已有一笔金额在退款中,暂不能发起退款";
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该签约单" + msg);
            return doc;
        } else {
            if (signCost != null && signCost > 0.00) {
                root.addElement("code").setText("SUCCESS");
            } else {
                root.addElement("code").setText("DATA-FAIL");
                root.addElement("msg").setText("待退款金额必须大于0.00");
                return doc;
            }
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        //患者信息
        Patient patient = patientDAO.getByMpiId(signRecord.getRequestMpiId());
        String pName = patient == null ? "" : patient.getPatientName();
        String payway = signRecord.getPayWay();
        String tradeNo = StringUtils.isBlank(signRecord.getTradeNo()) ? "" : signRecord.getTradeNo();
        String refundNo = BusTypeEnum.SIGN.getRefundNo();
        root.addElement("payname").setText("");
        root.addElement("pname").setText(pName);
        root.addElement("applyno").setText(applyNo);
        root.addElement("total_fee").setText(String.valueOf(signCost));
        root.addElement("batchno").setText(refundNo);// 退款商户号
        root.addElement("tradeno").setText(tradeNo);
        root.addElement("refund_fee").setText(String.valueOf(signCost));
        root.addElement("payway").setText(payway);
        root.addElement("device_info").setText("40".equals(payway) ? "WEB" : "APP");
        root.addElement("trade_type").setText("40".equals(payway) ? "JSAPI" : "APP");
        root.addElement("subject").setText("签约费用退款");
        root.addElement("organid").setText(signRecord.getPayOrganId());
        root.addElement("comments").setText("签约费用退款");
        return doc;
    }

    /**
     * 住院缴费业务数据
     * @param busId
     * @return
     */
    private Document getPrepayRefundDoc(Integer busId){
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
        PayBusiness payBuss = payDAO.get(busId);
        if (payBuss == null){
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该签约业务记录不存在");
            return doc;
        }
        String applyNo = payBuss.getOutTradeNo();
        if (StringUtils.isEmpty(applyNo)){
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payFlag = payBuss.getPayflag() == null ? Integer.valueOf(0) : payBuss.getPayflag();
        Double totalFee = payBuss.getTotalFee();
        if (payFlag == 0 || payFlag == 2){
            String msg = Integer.valueOf(0).equals(payFlag) ? "尚未付费，不能发起支付" : "已有一笔金额在退款中,暂不能发起退款";
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该住院缴费单" + msg);
            return doc;
        }else {
            if (totalFee != null && totalFee > 0.00){
                root.addElement("code").setText("SUCCESS");
            }else{
                root.addElement("code").setText("DATA-FAIL");
                root.addElement("msg").setText("待退款金额必须大于0.00");
                return doc;
            }
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        //患者信息
        Patient patient = patientDAO.getByMpiId(payBuss.getMPIID());
        String pName = patient == null ? "" : patient.getPatientName();
        String payway = payBuss.getPayWay();
        String tradeNo = StringUtils.isBlank(payBuss.getTradeNo()) ? "" : payBuss.getTradeNo();
        String refundNo = BusTypeEnum.PREPAY.getRefundNo();
        root.addElement("payname").setText("");
        root.addElement("pname").setText(pName);
        root.addElement("applyno").setText(applyNo);
        root.addElement("total_fee").setText(String.valueOf(totalFee));
        root.addElement("batchno").setText(refundNo);// 退款商户号
        root.addElement("tradeno").setText(tradeNo);
        root.addElement("refund_fee").setText(String.valueOf(totalFee));
        root.addElement("payway").setText(payway);
        root.addElement("device_info").setText("40".equals(payway) ? "WEB" : "APP");
        root.addElement("trade_type").setText("40".equals(payway) ? "JSAPI" : "APP");
        root.addElement("subject").setText("签约费用退款");
        root.addElement("organid").setText(payBuss.getPayOrganId());
        root.addElement("comments").setText("签约费用退款");
        return doc;

    }

    // 定时查询退款状态服务
    @RpcService
    public void refundQueryOrUpdate() {
        logger.info("云平台进入退款状态定时查询服务");
        // 转诊业务
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        List<Transfer> trList = transferDAO.findByPayflag(2);// 退款中
        logger.info("转诊退款中状态数量:" + trList.size());
        for (Transfer transfer : trList) {
            Map<String, Object> map = refundQuery(transfer.getTransferId(), "transfer");
            sendMessage(map, transfer.getTransferId(), "transfer");
        }
        // 咨询业务
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        List<Consult> coList = consultDAO.findByPayflag(2);// 退款中
        logger.info("咨询退款中状态数量:" + coList.size());
        for (Consult consult : coList) {
            Map<String, Object> map = refundQuery(consult.getConsultId(), "consult");
            sendMessage(map, consult.getConsultId(), "consult");
        }
        // 处方业务
//        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
//        List<Recipe> recipeList = recipeDAO.findByPayFlag(2);// 退款中
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        List<RecipeOrder> orderList = orderDAO.findByPayFlag(PayConstant.PAY_FLAG_REFUNDING);
        logger.info("处方订单退款中状态数量:" + orderList.size());
        for (RecipeOrder order : orderList) {
            Map<String, Object> map = null;
            try {
                NgariRefundService refundService = AppContextHolder.getBean("ngariRefundService", NgariRefundService.class);
                map = refundService.refundQuery(order.getOrderId(), RecipeService.WX_RECIPE_BUSTYPE);
            } catch (Exception e) {
                logger.error("refundQueryOrUpdate 处方订单退款查询 error. recipeId:"+order.getOrderId()+",map:"+JSONUtils.toString(map)+",error:"+e.getMessage());
            }
        }

        logger.info("云平台结束退款状态定时查询服务");
    }

    /**
     * @param map
     * @param busId
     * @param busType
     * @return void
     * @function 根据查询返回结果判断是否需要发送失败通知短信
     * @author zhangjr
     * @date 2016-4-5
     */
    private void sendMessage(Map<String, Object> map, Integer busId, String busType) {
        String return_code = (String) map.get("return_code");
        if ("SUCCESS".equals(return_code)) {
            String result_code = (String) map.get("result_code");
            if ("SUCCESS".equals(result_code)) {
                String refund_status = (String) map.get("refund_status_0");
                if (!"SUCCESS".equals(refund_status) && !"PROCESSING".equals(refund_status)) {
                    SmsInfo info = new SmsInfo();
                    info.setBusId(busId);
                    info.setBusType(busType);
                    info.setSmsType("refundFailMsg");//微信退款失败通知
                    info.setStatus(0);
                    info.setOrganId(0);// 短信服务对应的机构， 0代表通用机构
                    AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
                    exe.execute();
                    logger.error("退款查询返回退款结果失败！发送失败短信通知");
                }
            }
        }
    }

    /**
     *  根据配置的测试支付机构号，对该机构号下的交易进行批量退款（正式服务不开放使用）
     * 2017-3-7 12:58:42 zhangx 以防将正式环境的相关业务进行退款，特意注释掉该接口
     */
    @RpcService
    private void wxRefundScheduleTask(){
        //需要退款的机构号配置，多个机构用“|”间隔
//        String organid = "10009|10010|10011|10013|10014|10015|10016|10017";
//        Map<String, String> paramMap = new HashMap<String, String>();
//        String req_data_xml = "<req_data><organid>"+organid+"</organid></req_data>";
//        paramMap.put("req_data", req_data_xml);
//        paramMap.put("charset", "utf-8");
//        paramMap.put("service", WxConstant.Service.WX_REFUND_TESTRECORD);
//        paramMap.put("format", "xml");
//        paramMap.put("version", "v3.0");
//        logger.info("【云平台测试交易批量退款】原始请求参数:" + JSONUtils.toString(paramMap));
//        String responseXml = UrlPost.getHttpPostResult(
//                PayUtil.getWxRefundUrl(), paramMap, "【微信测试交易批量退款申请】");
    }

}
