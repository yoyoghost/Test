package eh.alipay.service;

import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.xml.XMLHelper;
import eh.alipay.constant.AliPayConstant;
import eh.bus.dao.*;
import eh.cdr.dao.RecipeOrderDAO;
import eh.cdr.service.RecipeService;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.bus.pay.RefundBasicParam;
import eh.entity.cdr.RecipeOrder;
import eh.entity.mpi.SignRecord;
import eh.mpi.dao.SignRecordDAO;
import eh.utils.LocalStringUtil;
import eh.wxpay.constant.PayServiceConstant;
import eh.wxpay.service.RefundService;
import eh.wxpay.util.UrlPost;
import eh.wxpay.util.XMLParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AliRefundService extends RefundService {
    private static final Log logger = LogFactory.getLog(AliRefundService.class);

    /**
     * 退款请求处理
     *
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
        logger.info("【支付宝】业务请求数据:" + req_data_doc.getRootElement().asXML() + ",业务类型=" + busType + ",业务id=" + busId);
        String code = req_data_doc.getRootElement().elementText(
                "code");
        try {
            if (code.equals("DATA-FAIL")) {
                resultMap = XMLParser.getMapFromXML(req_data_doc
                        .getRootElement().asXML());
                return resultMap;
            }
            String applyno = req_data_doc.getRootElement().element("detail").elementText("applyno");

            Map<String, String> paramMap = new HashMap<String, String>();
            String req_data_xml = req_data_doc.getRootElement().asXML();
            paramMap.put("req_data", req_data_xml);
            paramMap.put("charset", "utf-8");
            paramMap.put("service", PayServiceConstant.ALIPAY.getRefundApplyServiceName());
            paramMap.put("format", "xml");// 未知
            paramMap.put("version", "3.0");
            logger.info("【支付宝】原始请求参数:" + JSONUtils.toString(paramMap)
                    + ",业务类型" + busType + ",业务id" + busId);

            String responseXml = UrlPost.getHttpPostResult(PayServiceConstant.ALIPAY.getRefundApplyUrl(), paramMap, "【支付宝】");
            resultMap = XMLParser.getMapFromXMLForVideo(responseXml);
            logger.info("【支付宝】支付平台应答数据:" + resultMap + ",业务类型" + busType
                    + ",业务id" + busId);

            Map<String, Object> reponse = (Map<String, Object>) resultMap.get("dao");
            String res_code = (String) reponse.get("res_code");
            Map<String, Object> result = Maps.newHashMap();
            if (AliPayConstant.ResStatus.SUCCESS.equals(res_code)) {
                result = (Map<String, Object>) reponse.get("result");
                String fund_change = (String) result.get("fund_change");

                if (AliPayConstant.FundChange.Y.equals(fund_change)) {//SUCCESS退款申请接收成功,默认为退款中
                    //退款成功
                    updatePayflag(applyno, 3);
                    result.put("code", "SUCCESS");
                } else {
                    //退款失败
                    updatePayflag(applyno, 4);
                    result.put("code", "REFUND-FAIL");
                }
                return result;
            } else {
                //有疑问
                //处方收到支付平台出错，用户其实已收到退款，但需将标记为置为 退款失败，方便查询数据
                    updatePayflag(applyno, 4);
            }
            logger.info("【支付宝】云平台退款服务返回数据:" + JSONUtils.toString(result));
            return resultMap;
        } catch (ParserConfigurationException e) {
            logger.error(e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage());
        } catch (SAXException e) {
            logger.error(e.getMessage());
        }
        logger.info("【支付宝】云平台退款服务返回数据:" + JSONUtils.toString(resultMap));
        return resultMap;
    }

    @Override
    public Map<String, Object> refundQuery(Integer busId, String busType) {
        return null;
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
        logger.info("【支付宝】开始请求前业务数据查询...");
        Document document = null;
        RefundBasicParam param = new RefundBasicParam();

        if (BusTypeEnum.TRANSFER.getCode().equals(busType)) {
            TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
            Transfer transfer = transferDAO.getById(busId);
            if (transfer != null) {
                param.setApplyNo(transfer.getOutTradeNo());
                param.setPayFlag(transfer.getPayflag());
                param.setTotalFee(transfer.getTransferCost());
                param.setOrganId(transfer.getPayOrganId());
                param.setComments("转诊超时/拒绝自动退款");
            }
        } else if (busType.equals(RecipeService.WX_RECIPE_BUSTYPE)) {
//            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
//            Recipe recipe = recipeDAO.getByRecipeId(busId);
            RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
            RecipeOrder order = orderDAO.get(busId);
            if (order != null) {
                param.setApplyNo(order.getOutTradeNo());
                param.setPayFlag(order.getPayFlag());
                param.setTotalFee(order.getActualPrice().doubleValue());
                param.setOrganId(order.getPayOrganId());
                param.setComments("处方订单自动退款");
            }
        } else if (BusTypeEnum.CONSULT.getCode().equals(busType)) {
            ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
            Consult consult = consultDAO.getById(busId);
            if (consult != null) {
                param.setApplyNo(consult.getOutTradeNo());
                param.setPayFlag(consult.getPayflag());
                param.setTotalFee(consult.getActualPrice()==null?consult.getConsultCost():consult.getActualPrice());
                param.setOrganId(consult.getPayOrganId());
                param.setComments("咨询超时/拒绝自动退款");
            }
        } else if (BusTypeEnum.APPOINT.getCode().equals(busType) || BusTypeEnum.APPOINTPAY.getCode().equals(busType)) {
            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
            if (appointRecord != null) {
                param.setApplyNo(appointRecord.getOutTradeNo());
                param.setPayFlag(appointRecord.getPayFlag());
                param.setTotalFee(appointRecord.getActualPrice()==null?appointRecord.getClinicPrice():appointRecord.getActualPrice());
                param.setOrganId(appointRecord.getPayOrganId());
                param.setComments("挂号失败自动退款");
            }

        } else if (BusTypeEnum.SIGN.getCode().equals(busType)) {
            SignRecordDAO signRecordDAO = DAOFactory.getDAO(SignRecordDAO.class);
            SignRecord signRecord = signRecordDAO.get(busId);
            if (signRecord != null) {
                param.setApplyNo(signRecord.getOutTradeNo());
                param.setPayFlag(signRecord.getPayFlag() == null ? 0 : signRecord.getPayFlag());
                param.setTotalFee(signRecord.getActualPrice()==null?signRecord.getSignCost():signRecord.getActualPrice());
                param.setOrganId(signRecord.getPayOrganId());
                param.setComments("签约费用退款");
            }
        } else if (BusTypeEnum.PREPAY.getCode().equals(busType)) {
            PayBusinessDAO payDAO = DAOFactory.getDAO(PayBusinessDAO.class);
            PayBusiness payBuss = payDAO.get(busId);
            if (payBuss != null) {
                param.setApplyNo(payBuss.getOutTradeNo());
                param.setPayFlag(payBuss.getPayflag() == null ? 0 : payBuss.getPayflag());
                param.setTotalFee(payBuss.getTotalFee());
                param.setOrganId(payBuss.getPayOrganId());
                param.setComments("住院预缴费退款");
            }
        } else if (BusTypeEnum.OUTPATIENT.getCode().equals(busType)) {
            OutpatientDAO outpatientDAO = DAOFactory.getDAO(OutpatientDAO.class);
            Outpatient outpatient = outpatientDAO.getById(busId);
            if (outpatient != null) {
                param.setApplyNo(outpatient.getOutTradeNo());
                param.setPayFlag(outpatient.getPayflag() == null ? 0 : outpatient.getPayflag());
                param.setTotalFee(outpatient.getTotalFee());
                param.setOrganId(outpatient.getPayOrganId());
                param.setComments("门诊缴费退款");
            }

        }

        document = getRefundDoc(param, busType);
        return document;
    }

    /**
     * 组装退款xml参数
     *
     * @param param
     * @param busType
     * @return
     */
    private Document getRefundDoc(RefundBasicParam param, String busType) {
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busType);
        Document doc = XMLHelper.createDocument();
        Element root = doc.addElement("xml");
        Element detail = root.addElement("detail");

        if (param == null) {
            String errorMsg = LocalStringUtil.format("该{}业务记录不存在", busTypeEnum.getName());
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText(errorMsg);
            return doc;
        }
        //如果商户订单号不存在
        String applyNo = param.getApplyNo();
        if (StringUtils.isEmpty(applyNo)) {
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("商户订单号为空");
            return doc;
        }
        // 判断支付状态
        Integer payflag = param.getPayFlag();
        Double cost = param.getTotalFee();
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
            }
            root.addElement("code").setText("DATA-FAIL");
            root.addElement("msg").setText("该挂号单" + msg);
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

        detail.addElement("applyno").setText(applyNo);
        detail.addElement("total_fee").setText(String.valueOf(cost));
        logger.info(LocalStringUtil.format("该{}业务支付宝退款organId值无效，支付平台根据applyno取值", busTypeEnum.getName()));
        detail.addElement("organid").setText(param.getOrganId());
        detail.addElement("comments").setText(param.getComments());
        return doc;
    }


}
