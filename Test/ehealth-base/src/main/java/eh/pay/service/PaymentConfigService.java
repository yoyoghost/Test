package eh.pay.service;

import com.alibaba.druid.util.StringUtils;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.ClientConfigDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.ClientConfig;
import eh.entity.base.Organ;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.pay.PaymentAccount;
import eh.entity.pay.PaymentBusiness;
import eh.entity.pay.PaymentClient;
import eh.entity.pay.PaymentConfig;
import eh.pay.dao.PaymentAccountDAO;
import eh.pay.dao.PaymentBusinessDAO;
import eh.pay.dao.PaymentClientDAO;
import eh.pay.dao.PaymentConfigDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付方式服务
 *
 * @author jianghc
 * @create 2017-07-19 14:22
 **/
public class PaymentConfigService {

    private final static Logger logger = LoggerFactory.getLogger(PaymentConfigService.class);

    /**
     * 获取客户端的支持的支付方式列表
     */
    @RpcService
    public List<PaymentConfig> findPaymentTypes(Integer clientId, String busKey, Integer organId) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clientId is require");
        }
        if (StringUtils.isEmpty(busKey)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busKey is require");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        List<Integer> paymentTypes = DAOFactory.getDAO(PaymentClientDAO.class).findPaymentTypesByClientId(clientId);
        if (paymentTypes == null || paymentTypes.isEmpty()) {
            throw new DAOException("该客户端未配置支付方式");
        }
        PaymentConfigDAO paymentConfigDAO = DAOFactory.getDAO(PaymentConfigDAO.class);
        //获取个性化支付方式
        List<PaymentConfig> configs = paymentConfigDAO.findIndividualizationPayments(clientId, organId, busKey, paymentTypes);
        if (configs != null && !configs.isEmpty()) {
            if (configs.size() == 1 && configs.get(0).paymentIsForbid()) {
                throw new DAOException("该业务不支持支付");
            }
            return configs;
        }
        //获取默认支付方式
        PaymentBusiness paymentBusiness = DAOFactory.getDAO(PaymentBusinessDAO.class).getByBusinessTypeKey(busKey);
        if (paymentBusiness == null) {
            throw new DAOException("该业务不存在");
        }
        return paymentConfigDAO.findDefaultPayments(paymentBusiness.targetIsOrgan() ? organId : 0, busKey, paymentTypes);
    }

    /**
     * 获取客户端的支持的支付方式
     *
     * @return
     */
    @RpcService
    public PaymentConfig getPaymentConfig(Integer clientId, String busKey, Integer organId, Integer paymentType) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clientId is require");
        }
        if (StringUtils.isEmpty(busKey)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busKey is require");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        if (paymentType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "paymentType is require");
        }
        PaymentClient PaymentClient = DAOFactory.getDAO(PaymentClientDAO.class).getByClientIdAndPaymentType(clientId, paymentType);
        if (PaymentClient == null) {
            throw new DAOException("该客户端未配置支付方式");
        }
        PaymentConfigDAO paymentConfigDAO = DAOFactory.getDAO(PaymentConfigDAO.class);
        //获取个性化支付方式
        PaymentConfig paymentConfig = paymentConfigDAO.getIndividualizationPayment(clientId, organId, busKey, paymentType);
        if (paymentConfig != null) {
            if (paymentConfig.paymentIsForbid()) {
                throw new DAOException("该业务不支持支付");
            } else if (!paymentConfig.paymentIsDefault()) {
                return paymentConfig;
            }
        }
        //获取默认支付方式
        PaymentBusiness paymentBusiness = DAOFactory.getDAO(PaymentBusinessDAO.class).getByBusinessTypeKey(busKey);
        if (paymentBusiness == null) {
            throw new DAOException("该业务不存在");
        }
        return paymentConfigDAO.getDefaultPayment(paymentBusiness.targetIsOrgan() ? organId : 0, paymentType);
    }

    @RpcService
    public PaymentConfig getById(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is require");
        }
        return DAOFactory.getDAO(PaymentConfigDAO.class).get(id);
    }

    @RpcService
    public QueryResult<PaymentConfig> queryPaymentConfigs(Integer clientId, Integer baseOrganID, Integer isDefault, int start, int limit) {
        return DAOFactory.getDAO(PaymentConfigDAO.class).queryPaymentConfigs(clientId, baseOrganID, isDefault, start, limit);
    }

    @RpcService
    public PaymentConfig saveOrUpdatePaymentConfig(PaymentConfig paymentConfig) {
        if (paymentConfig == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "PaymentConfig is require");
        }
        Integer defaultFlag = paymentConfig.getIsDefault();
        if (defaultFlag == null) {
            throw new DAOException("默认标志不合法");
        }
        switch (defaultFlag) {
            case 0:
                paymentConfig = this.saveOrUpdateUnDefaultPaymentConfig(paymentConfig);
                break;
            case 1:
                paymentConfig = this.saveOrUpdateDefaultPaymentConfig(paymentConfig);
                break;
            default:
                throw new DAOException("默认标志不合法");
        }
        return paymentConfig;
    }


    private PaymentConfig saveOrUpdateUnDefaultPaymentConfig(PaymentConfig paymentConfig) {
        Integer clientId = paymentConfig.getClientId();
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clientId is require");
        }
        Integer organID = paymentConfig.getBaseOrganID();
        if (organID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        Organ organ = null;
        if (organID.intValue() == 0) {
            organ = new Organ();
            organ.setName("纳里健康");
        } else {
            organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organID);
            if (organ == null) {
                throw new DAOException("organId is not exist");
            }
        }
        String busKey = paymentConfig.getBusinessKey();
        if (StringUtils.isEmpty(busKey)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busKey is require");
        }
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(busKey);
        if (busTypeEnum == null) {
            throw new DAOException("busKey is not exist");
        }
        Integer paymentId = paymentConfig.getPaymentId();
        if (paymentId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "paymentId is require");
        }
        if (paymentId.intValue() != 0 && paymentId.intValue() == -1) {
            PaymentAccount account = DAOFactory.getDAO(PaymentAccountDAO.class).get(paymentId);
            if (account == null) {
                throw new DAOException("paymentId is not exist");
            }
        }
        Integer paymentType = paymentConfig.getPaymentType();
        if (paymentType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "paymentType is require");
        }
        PaymentConfigDAO paymentConfigDAO = DAOFactory.getDAO(PaymentConfigDAO.class);
        PaymentConfig oldConfig = paymentConfigDAO.getIndividualizationPayment(clientId, organID, busKey, paymentType);
        if (oldConfig == null) {//create
            paymentConfig.setId(null);
            Integer orderNum = paymentConfig.getOrderNum();
            paymentConfig.setOrderNum(orderNum == null ? 0 : orderNum);
            paymentConfig = paymentConfigDAO.save(paymentConfig);

            StringBuffer sbLog = new StringBuffer("新增例外支付：");
            sbLog.append("在客户端【").append(paymentConfig.getClientName()).append("】上对机构【")
                    .append(organ.getName()).append("】进行【").append(busTypeEnum.getName()).append("】业务,支付信息：")
                    .append(paymentId > 0 ? paymentId : paymentId == 0 ? "机构默认" : "业务不支持支付");
            BusActionLogService.recordBusinessLog("支付管理", paymentConfig.getId() + "", "PaymentConfig", sbLog.toString());

        } else {//update
            paymentConfig.setId(oldConfig.getId());
            Integer oldPaymentId = oldConfig.getPaymentId();
            if (oldPaymentId.equals(paymentId)) {
                return paymentConfig;
            }
            paymentConfig = paymentConfigDAO.update(paymentConfig);
            StringBuffer sbLog = new StringBuffer("更新例外支付：");
            sbLog.append("在客户端【").append(paymentConfig.getClientName()).append("】上对机构【")
                    .append(organ.getName()).append("】进行【").append(busTypeEnum.getName()).append("】业务,支付信息：")
                    .append(oldPaymentId > 0 ? oldPaymentId : oldPaymentId == 0 ? "机构默认" : "业务不支持支付").append("更新为").append(paymentId > 0 ? paymentId : paymentId == 0 ? "机构默认" : "业务不支持支付");
            BusActionLogService.recordBusinessLog("支付管理", paymentConfig.getId() + "", "PaymentConfig", sbLog.toString());
        }
        return paymentConfig;
    }


    private PaymentConfig saveOrUpdateDefaultPaymentConfig(PaymentConfig paymentConfig) {
        Integer organID = paymentConfig.getBaseOrganID();
        if (organID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        Organ organ = null;
        if (organID.intValue() == 0) {
            organ = new Organ();
            organ.setName("纳里健康");
        } else {
            organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organID);
            if (organ == null) {
                throw new DAOException("organId is not exist");
            }
        }
        Integer paymentId = paymentConfig.getPaymentId();
        if (paymentId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "paymentId is require");
        }
        if (paymentId.intValue() != 0 && paymentId.intValue() == -1) {
            PaymentAccount account = DAOFactory.getDAO(PaymentAccountDAO.class).get(paymentId);
            if (account == null) {
                throw new DAOException("paymentId is not exist");
            }
        }
        Integer paymentType = paymentConfig.getPaymentType();
        if (paymentType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "paymentType is require");
        }
        PaymentConfigDAO paymentConfigDAO = DAOFactory.getDAO(PaymentConfigDAO.class);
        PaymentConfig oldConfig = paymentConfigDAO.getDefaultPayment(organID, paymentType);
        if (oldConfig == null) {//create
            paymentConfig.setId(null);
            Integer orderNum = paymentConfig.getOrderNum();
            paymentConfig.setOrderNum(orderNum == null ? 0 : orderNum);
            paymentConfig = paymentConfigDAO.save(paymentConfig);
            StringBuffer sbLog = new StringBuffer("新增默认支付：");
            sbLog.append("机构【")
                    .append(organ.getName()).append("】支付信息：")
                    .append(paymentId > 0 ? paymentId : paymentId == 0 ? "机构默认" : "业务不支持支付");
            BusActionLogService.recordBusinessLog("支付管理", paymentConfig.getId() + "", "PaymentConfig", sbLog.toString());

        } else {//update
            paymentConfig.setId(oldConfig.getId());
            Integer oldPaymentId = oldConfig.getPaymentId();
            if (oldPaymentId.equals(paymentId)) {
                return paymentConfig;
            }
            paymentConfig = paymentConfigDAO.update(paymentConfig);
            StringBuffer sbLog = new StringBuffer("更新默认支付：");
            sbLog.append("机构【")
                    .append(organ.getName()).append("】业务,支付信息：")
                    .append(oldPaymentId > 0 ? oldPaymentId : oldPaymentId == 0 ? "机构默认" : "业务不支持支付").append("更新为").append(paymentId > 0 ? paymentId : paymentId == 0 ? "机构默认" : "业务不支持支付");
            BusActionLogService.recordBusinessLog("支付管理", paymentConfig.getId() + "", "PaymentConfig", sbLog.toString());
        }
        return paymentConfig;
    }

    @RpcService
    public void deletePaymentConfig(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is require");
        }
        PaymentConfigDAO paymentConfigDAO = DAOFactory.getDAO(PaymentConfigDAO.class);
        PaymentConfig config = paymentConfigDAO.get(id);
        if (config == null) {
            throw new DAOException("id is not exist");
        }
        Integer organId = config.getBaseOrganID();
        Organ organ = null;
        if (organId.intValue() > 0) {
            organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
        } else {
            organ = new Organ();
            organ.setName("纳里健康");
        }
        BusTypeEnum busTypeEnum = BusTypeEnum.fromCode(config.getBusinessKey());
        BusActionLogService.recordBusinessLog("支付管理", config.getId() + "", "PaymentConfig", "删除机构【" + organ.getName() + "】，" +
                "业务为【" + busTypeEnum.getName() + "】的，" + (config.getIsDefault() == 1 ? "默认" : "个性化") + "支付配置");
        paymentConfigDAO.remove(id);
    }

    @RpcService
    public List<PaymentConfig> findByClientIdAndBaseOrganID(Integer clientId, Integer baseOrganID) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clientId is require");
        }
        if (baseOrganID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        return DAOFactory.getDAO(PaymentConfigDAO.class).findByClientIdAndBaseOrganID(clientId, baseOrganID);
    }

    @RpcService
    public List<PaymentConfig> saveOrUpdateDefaultPaymentConfigs(Integer organId,
                                                                 List<PaymentConfig> list) {
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        Organ organ = new Organ();
        organ.setName("纳里健康");
        if (organId.intValue() != 0) {
            organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
            if (organ == null) {
                throw new DAOException("organId is not exist");
            }
        }
        Dictionary dic = null;
        try {
            dic = DictionaryController.instance().get("eh.pay.dictionary.PaymentType");
        } catch (ControllerException e) {
            throw new DAOException("字典加载失败");
        }

        PaymentConfigDAO paymentConfigDAO = DAOFactory.getDAO(PaymentConfigDAO.class);
        List<PaymentConfig> paymentConfigList = paymentConfigDAO.findDefaultByBaseOrganID(organId);
        List<PaymentConfig> returnList = new ArrayList<PaymentConfig>();
        StringBuffer sb = new StringBuffer();
        if (paymentConfigList == null || paymentConfigList.isEmpty()) {//create
            sb.append("新增机构【").append(organ.getName()).append("】默认支付方式：");
            for (PaymentConfig pc : list) {
                Integer paymentId = pc.getPaymentId();
                if (paymentId != null) {
                    pc.setIsDefault(1);
                    pc.setClientId(null);
                    pc.setClientName(null);
                    Integer orderNum = pc.getOrderNum();
                    pc.setOrderNum(orderNum == null ? 0 : orderNum);
                    sb.append("【").append(dic.getText(pc.getPaymentType()))
                            .append("-").append(paymentId == -1 ? "业务不可用" : paymentId).append("】,");
                    returnList.add(paymentConfigDAO.save(pc));
                }
            }
        } else {//update
            Map<Integer, PaymentConfig> map = new HashMap<Integer, PaymentConfig>();
            for (PaymentConfig p : paymentConfigList) {
                map.put(p.getPaymentType(), p);
            }
            sb.append("更新机构【").append(organ.getName()).append("】默认支付方式：");
            for (PaymentConfig pc : list) {
                PaymentConfig old = map.get(pc.getPaymentType());
                if (old == null) {
                    if (pc.getPaymentId() != null) {
                        pc.setId(null);
                        pc.setIsDefault(1);
                        pc.setClientId(null);
                        pc.setClientName(null);
                        Integer orderNum = pc.getOrderNum();
                        pc.setOrderNum(orderNum == null ? 0 : orderNum);
                        Integer paymentId = pc.getPaymentId();
                        sb.append("新增【").append(dic.getText(pc.getPaymentType()))
                                .append("-").append(paymentId == -1 ? "业务不可用" : paymentId).append("】,");
                        returnList.add(paymentConfigDAO.save(pc));
                    }
                } else {
                    Integer paymentId = pc.getPaymentId();
                    Integer oldPaymentId = old.getPaymentId();
                    if (paymentId == null) {
                        sb.append("删除【").append(dic.getText(pc.getPaymentType()))
                                .append("-").append(oldPaymentId== -1 ? "业务不可用" : oldPaymentId).append("】,");
                        paymentConfigDAO.remove(old.getId());
                    } else {
                        if (!oldPaymentId.equals(paymentId)) {
                            sb.append("更新【").append(dic.getText(pc.getPaymentType()))
                                    .append("-").append(oldPaymentId == -1 ? "业务不可用" : oldPaymentId).append("更新为")
                                    .append(paymentId == -1 ? "业务不可用" : paymentId).append("】,");
                            BeanUtils.map(pc, old);
                            returnList.add(paymentConfigDAO.update(old));
                        }
                    }

                }
            }

        }
        BusActionLogService.recordBusinessLog("支付管理", organId + "", "PaymentConfig", sb.substring(0, sb.length() - 1));
        return returnList;
    }


    @RpcService
    public List<PaymentConfig> saveOrUpdateIndividualizationPaymentConfigs(Integer clientId, Integer organId, String busKey,
                                                                           List<PaymentConfig> list) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clientId is require");
        }
        ClientConfig clientConfig = DAOFactory.getDAO(ClientConfigDAO.class).get(clientId);
        if (clientConfig == null) {
            throw new DAOException("clientId is not exist");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
        if (organ == null) {
            throw new DAOException("organId is not exist");
        }
        if (StringUtils.isEmpty(busKey)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busKey is require");
        }
        PaymentBusiness pb = DAOFactory.getDAO(PaymentBusinessDAO.class).getByBusinessTypeKey(busKey);
        if (pb == null) {
            throw new DAOException("busKey is not exist");
        }
        Dictionary dic = null;
        try {
            dic = DictionaryController.instance().get("eh.pay.dictionary.PaymentType");
        } catch (ControllerException e) {
            throw new DAOException("字典加载失败");
        }
        PaymentConfigDAO paymentConfigDAO = DAOFactory.getDAO(PaymentConfigDAO.class);
        List<PaymentConfig> paymentConfigList = paymentConfigDAO.findIndividualizationByClientIdAndBaseOrganIDAndBusinessKey(clientId, organId, busKey);
        List<PaymentConfig> returnList = new ArrayList<PaymentConfig>();
        StringBuffer sb = new StringBuffer();
        if (paymentConfigList == null) {
            sb.append("新增【" + clientConfig.getClientName() + "】端,机构【").append(organ.getName()).append("】，【" + pb.getBusinessType() + "】业务支付方式：");
            for (PaymentConfig pc : list) {
                pc.setId(null);
                pc.setIsDefault(0);
                Integer orderNum = pc.getOrderNum();
                pc.setOrderNum(orderNum == null ? 0 : orderNum);
                Integer paymentId = pc.getPaymentId();
                sb.append("【").append(dic.getText(pc.getPaymentType()))
                        .append("-").append(paymentId == -1 ? "业务不可用" : paymentId).append("】,");
                returnList.add(paymentConfigDAO.save(pc));
            }
        } else {
            sb.append("更新机构【").append(organ.getName()).append("】，【" + pb.getBusinessType() + "】业务个性化支付方式：");
            Map<Integer, PaymentConfig> map = new HashMap<Integer, PaymentConfig>();
            for (PaymentConfig p : paymentConfigList) {
                map.put(p.getPaymentType(), p);
            }
            for (PaymentConfig pc : list) {
                PaymentConfig old = map.get(pc.getPaymentType());
                if (old == null) {
                    if (pc.getPaymentId() != null) {
                        pc.setId(null);
                        pc.setIsDefault(0);
                        Integer orderNum = pc.getOrderNum();
                        pc.setOrderNum(orderNum == null ? 0 : orderNum);
                        Integer paymentId = pc.getPaymentId();
                        sb.append("新增【").append(dic.getText(pc.getPaymentType()))
                                .append("-").append(paymentId == -1 ? "业务不可用" : paymentId).append("】,");
                        returnList.add(paymentConfigDAO.save(pc));
                    }
                } else {
                    Integer paymentId = pc.getPaymentId();
                    if (paymentId == null) {
                        sb.append("删除【").append(dic.getText(pc.getPaymentType()))
                                .append("-").append(paymentId == -1 ? "业务不可用" : paymentId).append("】,");
                        paymentConfigDAO.remove(old.getId());
                    } else {
                        Integer oldPaymentId = old.getPaymentId();
                        if (!oldPaymentId.equals(paymentId)) {
                            sb.append("更新【").append(dic.getText(pc.getPaymentType()))
                                    .append("-").append(oldPaymentId == -1 ? "业务不可用" : oldPaymentId).append("更新为")
                                    .append(paymentId == -1 ? "业务不可用" : paymentId).append("】,");
                            BeanUtils.map(pc, old);
                            returnList.add(paymentConfigDAO.update(old));
                        }
                    }

                }
            }


        }
        BusActionLogService.recordBusinessLog("支付管理", organId + "", "PaymentConfig", sb.substring(0, sb.length() - 1));
        return returnList;
    }

    @RpcService
    public void resettingIndividualizationPaymentConfigs(Integer clientId, Integer organId, String busKey) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clientId is require");
        }
        ClientConfig clientConfig = DAOFactory.getDAO(ClientConfigDAO.class).get(clientId);
        if (clientConfig == null) {
            throw new DAOException("clientId is not exist");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
        if (organ == null) {
            throw new DAOException("organId is not exist");
        }
        if (StringUtils.isEmpty(busKey)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busKey is require");
        }
        PaymentBusiness pb = DAOFactory.getDAO(PaymentBusinessDAO.class).getByBusinessTypeKey(busKey);
        if (pb == null) {
            throw new DAOException("busKey is not exist");
        }
        StringBuffer sb = new StringBuffer();
        sb.append("重置【" + clientConfig.getClientName() + "】端,机构【").append(organ.getName()).append("】，【" + pb.getBusinessType() + "】业务支付方式。");
        BusActionLogService.recordBusinessLog("支付管理", organId + "", "PaymentConfig", sb.toString());
        DAOFactory.getDAO(PaymentConfigDAO.class).deleteIndividualizationByClientIdAndBaseOrganIDAndBusinessKey(clientId, organId, busKey);
    }

    @RpcService
    public void forbiddenIndividualizationPaymentConfigs(Integer clientId, Integer organId, String busKey) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clientId is require");
        }
        ClientConfig clientConfig = DAOFactory.getDAO(ClientConfigDAO.class).get(clientId);
        if (clientConfig == null) {
            throw new DAOException("clientId is not exist");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(organId);
        if (organ == null) {
            throw new DAOException("organId is not exist");
        }
        if (StringUtils.isEmpty(busKey)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busKey is require");
        }
        PaymentBusiness pb = DAOFactory.getDAO(PaymentBusinessDAO.class).getByBusinessTypeKey(busKey);
        if (pb == null) {
            throw new DAOException("busKey is not exist");
        }
        StringBuffer sb = new StringBuffer();
        sb.append("禁止【" + clientConfig.getClientName() + "】端,机构【").append(organ.getName()).append("】，【" + pb.getBusinessType() + "】业务支付。");
        BusActionLogService.recordBusinessLog("支付管理", organId + "", "PaymentConfig", sb.toString());
        PaymentConfigDAO paymentConfigDAO = DAOFactory.getDAO(PaymentConfigDAO.class);
        paymentConfigDAO.deleteIndividualizationByClientIdAndBaseOrganIDAndBusinessKey(clientId, organId, busKey);
        PaymentConfig pc = new PaymentConfig();
        pc.setClientId(clientId);
        pc.setClientName(clientConfig.getClientName());
        pc.setBaseOrganID(organId);
        pc.setBaseOrganName(organ.getName());
        pc.setBusinessKey(busKey);
        pc.setBusinessName(pb.getBusinessType());
        pc.setPaymentId(-1);
        pc.setIsDefault(0);
        paymentConfigDAO.save(pc);

    }


    @RpcService
    public List<PaymentConfig> findDefaultByBaseOrganID(Integer organId) {
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        return DAOFactory.getDAO(PaymentConfigDAO.class).findDefaultByBaseOrganID(organId);
    }

    @RpcService
    public List<PaymentConfig> findIndividualizationByClientIdAndBaseOrganID(Integer clientId,
                                                                             Integer organId) {
        if (clientId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "clientId is require");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        return DAOFactory.getDAO(PaymentConfigDAO.class).findIndividualizationByClientIdAndBaseOrganID(clientId, organId);
    }
}
