package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.AliPayConfigDAO;
import eh.base.dao.DefaultPayTargetDAO;
import eh.base.dao.PayConfigDAO;
import eh.entity.base.AliPayConfig;
import eh.entity.base.DefaultPayTarget;
import eh.entity.base.PayConfig;
import eh.entity.wx.WXConfig;
import eh.op.dao.WXConfigsDAO;
import org.apache.axis.utils.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务机构支付方式
 *
 * @author jianghc
 * @create 2016-10-13 9:49
 **/
public class PayConfigService {
    private static final Log logger = LogFactory.getLog(PayConfigService.class);

    private PayConfigDAO payConfigDAO;

    private DefaultPayTargetDAO defaultPayTargetDAO;

    private AliPayConfigDAO aliPayConfigDAO;

    public PayConfigService() {

        this.payConfigDAO = DAOFactory.getDAO(PayConfigDAO.class);

        this.defaultPayTargetDAO = DAOFactory.getDAO(DefaultPayTargetDAO.class);

        this.aliPayConfigDAO = DAOFactory.getDAO(AliPayConfigDAO.class);
    }

    /**
     * 获取业务机构支付方式
     *
     * @param isDefault
     * @param start
     * @return
     */
    @RpcService
    public List<Map> findByDefault(Integer isDefault, String payType, Integer start, Integer limit) {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        if (isDefault == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "isDefault is required!");
        }
        List<PayConfig> list = payConfigDAO.findByDefault(isDefault, payType, start, limit);
        if (list == null || list.size() <= 0) {
            return null;
        }
        List<Map> maps = new ArrayList<Map>();
        for (PayConfig pc : list) {
            Map map = new HashMap();
            map.put("payConfig", pc);
            if ("weChat".equals(payType.trim())) {//微信
                WXConfig targetWx = wxConfigsDAO.getById(pc.getTargetPayConfigID());
                map.put("targetConfig", targetWx);
                if (isDefault.intValue() == 2) {//获取例外的支付方式
                    WXConfig originWx = wxConfigsDAO.getById(pc.getOriginPayConfigID());
                    map.put("originConfig", originWx);
                }
            } else if ("aliPay".equals(payType.trim())) {//支付宝
                AliPayConfig targetAliPay = aliPayConfigDAO.getById(pc.getTargetPayConfigID());
                map.put("targetConfig", targetAliPay);
                if (isDefault.intValue() == 2) {//获取例外的支付方式
                    AliPayConfig originAliPay = aliPayConfigDAO.getById(pc.getOriginPayConfigID());
                    map.put("originConfig", originAliPay);
                }
            }
            maps.add(map);
        }
        return maps;
    }


    /**
     * 获取业务机构支付方式(QueryResult)
     *
     * @param isDefault
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Map> findQueryResultByDefault(Integer isDefault, Integer start, Integer limit, String payType) {
        List<Map> maps = this.findByDefault(isDefault, payType, start, limit);
        if (maps == null) {
            maps = new ArrayList<Map>();
        }
        Long total = payConfigDAO.getCountByDefault(isDefault, payType);
        return new QueryResult<Map>(total, start, maps.size(), maps);
    }


    /**
     * 创建默认或例外支付方式
     *
     * @param payConfig
     * @return
     */
    @RpcService
    public PayConfig createPayConfig(PayConfig payConfig) {
        if (payConfig == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "PayConfig is required!");
        }
        if (payConfig.getPayType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "payType is required!");
        }
        if (payConfig.getIsDefault() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "isDefault is required!");
        }
        if (payConfig.getBaseOrganID() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "baseOrganID is required!");
        }
        if (payConfig.getBaseOrganName() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "baseOrganName is required!");
        }
        if (payConfig.getTargetPayConfigID() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "targetPayConfigID is required!");
        }
        if (payConfig.getIsDefault() == 1) {
            if (payConfigDAO.getByBaseOrganID(payConfig.getBaseOrganID(),payConfig.getPayType()) != null) {
                throw new DAOException(DAOException.VALUE_NEEDED, " this payConfig is exist!");
            }
        } else if (payConfig.getIsDefault().intValue() == 2) {
            if (payConfig.getOriginPayConfigID() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "originPayConfigID is required!");
            }
            if (payConfig.getBusinessType() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "businessType is required!");
            }
            if (payConfigDAO.getByOriginPayConfigIDAndBaseOrganIDAndbusinessTypeIDAndPayType(payConfig.getOriginPayConfigID(), payConfig.getBaseOrganID(), payConfig.getBusinessTypeID(),payConfig.getPayType()) != null) {
                throw new DAOException(DAOException.VALUE_NEEDED, " this payConfig is exist!");

            }
        }
        if ("weChat".equals(payConfig.getPayType().trim())) {
            payConfig.setPayTypeName("微信支付");
        } else if ("aliPay".equals(payConfig.getPayType().trim())) {
            payConfig.setPayTypeName("支付宝支付");
        }
        return payConfigDAO.save(payConfig);
    }

    /**
     * 创建默认或例外支付方式(多个)
     *
     * @param payConfigs
     * @return
     */
    @RpcService
    public List<PayConfig> createPayConfigs(Integer originPayConfigID, Integer baseOrganID, String baseOrganName, String payType, List<PayConfig> payConfigs) {
        if (originPayConfigID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "originPayConfigID is required!");
        }
        if (baseOrganID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "baseOrganID is required!");
        }
        if (baseOrganName == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "baseOrganName is required!");
        }
        if (payType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "payType is required!");
        }
        List<PayConfig> old = payConfigDAO.findByOriginPayConfigIDAndBaseOrganID(originPayConfigID, baseOrganID, payType);
        if (old != null && old.size() > 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "this config is exist!");
        }
        for (PayConfig payConfig : payConfigs) {
            if (payConfig == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "PayConfig is required!");
            }
            if (payConfig.getIsDefault() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "isDefault is required!");
            }
            if (payConfig.getTargetPayConfigID() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "targetPayConfigID is required!");
            }
        }
        List<PayConfig> pcs = new ArrayList<PayConfig>();
        for (PayConfig payConfig : payConfigs) {
            payConfig.setOriginPayConfigID(originPayConfigID);
            payConfig.setBaseOrganID(baseOrganID);
            payConfig.setBaseOrganName(baseOrganName);
            payConfig.setPayType(payType);
            if ("weChat".equals(payType.trim())) {
                payConfig.setPayTypeName("微信支付");
            } else if ("aliPay".equals(payType.trim())) {
                payConfig.setPayTypeName("支付宝支付");
            }
            pcs.add(payConfigDAO.save(payConfig));
        }
        return pcs;
    }


    /**
     * 更新默认或例外支付方式
     *
     * @param payConfig
     * @return
     */
    @RpcService
    public PayConfig updateConfig(PayConfig payConfig) {
        if (payConfig == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "PayConfig is required!");
        }
        if (payConfig.getIsDefault() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "isDefault is required!");
        }
        if (payConfig.getBaseOrganID() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "baseOrganID is required!");
        }
        if (payConfig.getBaseOrganName() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "baseOrganName is required!");
        }
        if (payConfig.getTargetPayConfigID() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "targetPayConfigID is required!");
        }
        if (payConfig.getIsDefault().intValue() == 1) {
            PayConfig pc = payConfigDAO.getByBaseOrganID(payConfig.getBaseOrganID(),payConfig.getPayType());
            if (pc != null && pc.getId().intValue() != payConfig.getId().intValue()) {
                throw new DAOException(DAOException.VALUE_NEEDED, " this payConfig is exist!");
            }
        } else if (payConfig.getIsDefault().intValue() == 2) {
            if (payConfig.getOriginPayConfigID() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "originPayConfigID is required!");
            }
            if (payConfig.getBusinessType() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "businessType is required!");
            }
            PayConfig pc = payConfigDAO.getByOriginPayConfigIDAndBaseOrganIDAndbusinessTypeIDAndPayType(payConfig.getOriginPayConfigID(), payConfig.getBaseOrganID(), payConfig.getBusinessTypeID(),payConfig.getPayType());
            if (pc != null && pc.getId().intValue() != payConfig.getId().intValue()) {
                throw new DAOException(DAOException.VALUE_NEEDED, " this payConfig is exist!");

            }
        }
        return payConfigDAO.update(payConfig);
    }

    /**
     * 根据Id删除记录
     *
     * @param id
     */
    @RpcService
    public void deletePayConfigById(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is required!");
        }
        payConfigDAO.deleteById(id);
    }

    /**
     * 根据支付环境的id、业务类型Id，机构id
     *
     * @param originPayConfigID
     * @param busId 为
     * @param organId
     * @return
     */
    @RpcService
    public WXConfig getConfigByidAndBusIdAndOrganId(Integer originPayConfigID, Integer busId, Integer organId,String payType) {
        if (originPayConfigID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "originPayConfigID is required!");
        }
        if (busId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busId is required!");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        if (StringUtils.isEmpty(payType)){
            throw new DAOException(DAOException.VALUE_NEEDED, "payType is required!");
        }
        //根据支付环境的id、业务类型Id，机构id 尝试获取例外设置
        PayConfig payConfig = payConfigDAO.getByOriginPayConfigIDAndBaseOrganIDAndbusinessTypeIDAndPayType(originPayConfigID, organId, busId,payType);
        if (payConfig == null || payConfig.getTargetPayConfigID().intValue() == 0) {//例外设置中未找到合适数据，去默认设置中查找默认设置
            DefaultPayTarget defaultPayTarget = defaultPayTargetDAO.getById(busId);
            if (defaultPayTarget == null) {
                throw new DAOException(DAOException.DAO_NOT_FOUND, "this businessTypeID is not exist!");
            }
            if (defaultPayTarget.getPayTarget().intValue() == 0) {//支付给机构
                payConfig = payConfigDAO.getByBaseOrganID(organId,payType);
                if (payConfig == null || payConfig.getTargetPayConfigID().intValue() <= 0) {
                    throw new DAOException(DAOException.DAO_NOT_FOUND, "this organ's config is not exist!");
                }
            } else {//支付给纳里
                payConfig = new PayConfig();
                payConfig.setTargetPayConfigID(defaultPayTarget.getPayTarget());
            }
        }else if(payConfig.getTargetPayConfigID().intValue()<0){//该机构业务被禁用
            throw new DAOException( "该机构业务被禁用!");
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        return wxConfigsDAO.getById(payConfig.getTargetPayConfigID());
    }

    /**
     * 根据支付Appid，
     *
     * @param appId
     * @param busKey
     * @param organId
     * @return
     */
    public WXConfig getConfigByAppId(String appId, String busKey, Integer organId) {
        if (appId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appId is required!");
        }
        if (busKey == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busKey is required!");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        DefaultPayTarget defaultPayTarget = defaultPayTargetDAO.getByBusinessTypeKey(busKey);
        if (defaultPayTarget == null || defaultPayTarget.getPayTarget() == null) {
            throw new DAOException(DAOException.DAO_NOT_FOUND, " businessTypeKey is not exist!");
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wx = wxConfigsDAO.getByAppID(appId);
        if (wx == null ) {
            if(defaultPayTarget.getPayTarget().intValue() <= 0){
                throw new DAOException(DAOException.DAO_NOT_FOUND, " appId is not exist!");
            }else{
                return wxConfigsDAO.getById(defaultPayTarget.getPayTarget());
            }
        }
        return this.getConfigByidAndBusIdAndOrganId(wx.getId(), defaultPayTarget.getId(), organId,"weChat");

    }


    public AliPayConfig getAliPayConfigByidAndBusIdAndOrganId(Integer originPayConfigID, Integer busId, Integer organId,String payType) {
        if (originPayConfigID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "originPayConfigID is required!");
        }
        if (busId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busId is required!");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        if (StringUtils.isEmpty(payType)){
            throw new DAOException(DAOException.VALUE_NEEDED, "payType is required!");
        }
        //根据支付环境的id、业务类型Id，机构id 尝试获取例外设置
        PayConfig payConfig = payConfigDAO.getByOriginPayConfigIDAndBaseOrganIDAndbusinessTypeIDAndPayType(originPayConfigID, organId, busId,payType);
        if (payConfig == null || payConfig.getTargetPayConfigID().intValue() == 0) {//例外设置中未找到合适数据，去默认设置中查找默认设置
            DefaultPayTarget defaultPayTarget = defaultPayTargetDAO.getById(busId);
            if (defaultPayTarget == null) {
                throw new DAOException(DAOException.DAO_NOT_FOUND, "this businessTypeID is not exist!");
            }
            if (defaultPayTarget.getAlipayTarget().intValue() == 0) {//支付给机构
                payConfig = payConfigDAO.getByBaseOrganID(organId,payType);
                if (payConfig == null || payConfig.getTargetPayConfigID().intValue() <= 0) {
                    throw new DAOException(DAOException.DAO_NOT_FOUND, "this organ's config is not exist!");
                }
            } else {//支付给纳里
                payConfig = new PayConfig();
                payConfig.setTargetPayConfigID(defaultPayTarget.getAlipayTarget());
            }
        }else if(payConfig.getTargetPayConfigID().intValue()<0){
            throw new DAOException( "该机构业务被禁用!");
        }
        //WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        return aliPayConfigDAO.getById(payConfig.getTargetPayConfigID());//wxConfigsDAO.getById(payConfig.getTargetPayConfigID());
    }

    public AliPayConfig getAliPayConfigByAppId(String appId, String busKey, Integer organId) {
        if (appId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appId is required!");
        }
        if (busKey == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "busKey is required!");
        }
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        DefaultPayTarget defaultPayTarget = defaultPayTargetDAO.getByBusinessTypeKey(busKey);
        if (defaultPayTarget == null||defaultPayTarget.getAlipayTarget()==null) {
            throw new DAOException(DAOException.DAO_NOT_FOUND, " businessTypeKey is not exist!");
        }
        AliPayConfig aliPayConfig = aliPayConfigDAO.getByAppID(appId);
        if (aliPayConfig == null) {
            if(defaultPayTarget.getAlipayTarget().intValue() <= 0){
                throw new DAOException(DAOException.DAO_NOT_FOUND, " appId is not exist!");
            }else {
                return aliPayConfigDAO.getById(defaultPayTarget.getAlipayTarget());
            }
        }
        return this.getAliPayConfigByidAndBusIdAndOrganId(aliPayConfig.getId(), defaultPayTarget.getId(), organId,"aliPay");

    }

    /**
     * 获取支付账号（aliPay，weChat）
     * @param appId
     * @param busKey
     * @param organId
     * @param payWay（0表示微信，1表示支付宝）
     * @return
     */
    public Object getConfigByInfo(String appId, String busKey, Integer organId, Integer payWay) {
        logger.info("获取支付账号：Appid：【"+appId+"】，busKey：【"+busKey+"】，organId：【"+organId+"】，payWay:【"+payWay+"】");
        if (payWay == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "payWay is required!");
        }
        Object obj = null;
        switch (payWay) {
            case 2://微信
                obj = getConfigByAppId(appId, busKey, organId);
                break;
            case 1://支付宝
                obj = getAliPayConfigByAppId(appId, busKey, organId);
                break;
            default:
                throw new DAOException(DAOException.VALUE_NEEDED, "payWay not in(1,2)!");
        }
        logger.info("获取支付账号："+ JSONUtils.toString(obj));
        return obj;

    }

    /**
     * 根据Id获取单个支付方式
     *
     * @param id
     * @return
     */
    @RpcService
    public Map getPayConfigById(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is required!");
        }
        PayConfig payConfig = payConfigDAO.getById(id);
        if (payConfig == null) {
            return null;
        }
        Map map = new HashMap();
        map.put("payConfig", payConfig);
        String payType = payConfig.getPayType()+"";
        if("weChat".equals(payType.trim())){
            WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
            WXConfig targetWx = wxConfigsDAO.getById(payConfig.getTargetPayConfigID());
            map.put("targetConfig", targetWx);
            if (payConfig.getIsDefault().intValue() == 2) {//获取例外的支付方式
                WXConfig originWx = wxConfigsDAO.getById(payConfig.getOriginPayConfigID());
                map.put("originConfig", originWx);
            }
        }else if("aliPay".equals(payType.trim())){
            AliPayConfig targetAliPay = aliPayConfigDAO.getById(payConfig.getTargetPayConfigID());
            map.put("targetConfig", targetAliPay);
            if (payConfig.getIsDefault().intValue() == 2) {//获取例外的支付方式
                AliPayConfig originAliPay = aliPayConfigDAO.getById(payConfig.getOriginPayConfigID());
                map.put("originConfig", originAliPay);
            }
        }
        return map;
    }

    /**
     * 获取支付方式（分组）
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Map> findByGroup(String payType, Integer start, Integer limit) {
        if (StringUtils.isEmpty(payType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "payType is required!");
        }
        List<PayConfig> totals = payConfigDAO.findByGroup(2, payType, 0, 999999);
        Integer total = 0;
        if (totals != null) {
            total = totals.size();
        }
        if (total == null || total <= 0) {
            return null;
        }
        List<PayConfig> pcs = payConfigDAO.findByGroup(2, payType, start, limit);
        if (pcs == null) {
            return null;
        }
        List<Map> maps = new ArrayList<Map>();
        if("weChat".equals(payType.trim())){
            WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
            for (PayConfig pc : pcs) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("payConfig", pc);
                WXConfig originWx = wxConfigsDAO.getById(pc.getOriginPayConfigID());
                map.put("originConfig", originWx);
                List<PayConfig> payConfigs = payConfigDAO.findByOriginPayConfigIDAndBaseOrganID(pc.getOriginPayConfigID(), pc.getBaseOrganID(), pc.getPayType());
                if (payConfigs != null) {
                    List<Map> payMaps = new ArrayList<Map>();
                    for (PayConfig config : payConfigs) {
                        Map<String, Object> payMap = new HashMap<String, Object>();
                        BeanUtils.map(config, payMap);
                        Integer targetPayConfigID = config.getTargetPayConfigID();
                        if (targetPayConfigID != null && targetPayConfigID > 0) {
                            payMap.put("targetPayConfig", wxConfigsDAO.getById(targetPayConfigID));
                        }
                        payMaps.add(payMap);
                    }
                    map.put("payConfigs", payMaps);
                }
                maps.add(map);
            }
        }else if("aliPay".equals(payType.trim())){
            for (PayConfig pc : pcs) {
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("payConfig", pc);
                AliPayConfig originAlipay= aliPayConfigDAO.getById(pc.getOriginPayConfigID());
                map.put("originConfig", originAlipay);
                List<PayConfig> payConfigs = payConfigDAO.findByOriginPayConfigIDAndBaseOrganID(pc.getOriginPayConfigID(), pc.getBaseOrganID(), pc.getPayType());
                if (payConfigs != null) {
                    List<Map> payMaps = new ArrayList<Map>();
                    for (PayConfig config : payConfigs) {
                        Map<String, Object> payMap = new HashMap<String, Object>();
                        BeanUtils.map(config, payMap);
                        Integer targetPayConfigID = config.getTargetPayConfigID();
                        if (targetPayConfigID != null && targetPayConfigID > 0) {
                            payMap.put("targetPayConfig", aliPayConfigDAO.getById(targetPayConfigID));
                        }
                        payMaps.add(payMap);
                    }
                    map.put("payConfigs", payMaps);
                }
                maps.add(map);


            }
        }

        return new QueryResult<Map>(total, start, pcs.size(), maps);
    }

    @RpcService
    public List<PayConfig> getPayConfigByGroup(Integer originPayConfigId, Integer baseOrganId, String payType) {
        return payConfigDAO.findByOriginPayConfigIDAndBaseOrganID(originPayConfigId, baseOrganId, payType);
    }

    @RpcService
    public List<PayConfig> updateConfigs(Integer originPayConfigID, Integer baseOrganID, String baseOrganName, List<PayConfig> pcs) {
        if (originPayConfigID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "originPayConfigID is required!");
        }
        if (baseOrganID == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "baseOrganID is required!");
        }
        if (baseOrganName == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "baseOrganName is required!");
        }
        if (pcs == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "pcs is required!");
        }
        List<PayConfig> returns = new ArrayList<PayConfig>();
        for (PayConfig payConfig : pcs) {
            if (payConfig.getIsDefault().intValue() == 1) {
                PayConfig pc = payConfigDAO.getByBaseOrganID(baseOrganID,payConfig.getPayType());
                if (pc != null && pc.getId().intValue() != payConfig.getId().intValue()) {
                    throw new DAOException(DAOException.VALUE_NEEDED, " this payConfig is exist!");
                }
            } else if (payConfig.getIsDefault().intValue() == 2) {
                if (payConfig.getBusinessType() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "businessType is required!");
                }
                PayConfig pc = payConfigDAO.getByOriginPayConfigIDAndBaseOrganIDAndbusinessTypeIDAndPayType(originPayConfigID, baseOrganID, payConfig.getBusinessTypeID(),payConfig.getPayType());
                if (pc != null && pc.getId().intValue() != payConfig.getId().intValue()) {
                    throw new DAOException(DAOException.VALUE_NEEDED, " this payConfig is exist!" + payConfig.getId());

                }
            }
        }
        for (PayConfig payConfig : pcs) {
            payConfig.setOriginPayConfigID(originPayConfigID);
            payConfig.setBaseOrganID(baseOrganID);
            payConfig.setBaseOrganName(baseOrganName);
            returns.add(payConfigDAO.update(payConfig));
        }
        return returns;
    }

    @RpcService
    public void deleteConfigsByOriginPayConfigIDAndBaseOrganIDAndIsDefault(Integer originPayConfigID, Integer baseOrganID, Integer isDefault,String payType) {
        payConfigDAO.deleteConfigsByOriginPayConfigIDAndBaseOrganIDAndIsDefault(originPayConfigID, baseOrganID, isDefault,payType);
    }


    /**
     * 查询所有默认设置
     * @return
     */
    @RpcService
    public List<Map> findAllDefaultPayTarget(String payType) {
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        List<DefaultPayTarget> defaultPayTargets = defaultPayTargetDAO.findAll();
        if (defaultPayTargets == null) {
            return null;
        }
        List<Map> maps = new ArrayList<Map>();
        for (DefaultPayTarget defaultPayTarget : defaultPayTargets) {
            Map<String, Object> map = new HashMap<String, Object>();
            BeanUtils.map(defaultPayTarget, map);
            Integer payTarget = null;
            if("weChat".equals(payType.trim())){
                payTarget=defaultPayTarget.getPayTarget();
                if (payTarget != null && payTarget > 0) {
                    map.put("payTargetWx", wxConfigsDAO.getById(payTarget));
                }
            }else if("aliPay".equals(payType.trim())){
                payTarget = defaultPayTarget.getAlipayTarget();
                if (payTarget != null && payTarget > 0) {
                    map.put("payTargetAliPay", aliPayConfigDAO.getById(payTarget));
                }
            }
            maps.add(map);
        }
        return maps;
    }

}
