package eh.mpi.dao;

import com.google.common.collect.Lists;
import com.ngari.his.patient.mode.PatientQueryRequestTO;
import com.ngari.his.patient.service.IPatientHisService;
import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.spring.AppDomainContext;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.CardSource;
import eh.base.constant.CardTypeEnum;
import eh.base.constant.ErrorCode;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganDAO;
import eh.base.dao.ValidateCodeDAO;
import eh.entity.base.HisServiceConfig;
import eh.entity.base.OrganConfig;
import eh.entity.his.PatientQueryRequest;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.remote.IPatientService;
import eh.util.DBParamLoaderUtil;
import eh.util.RpcServiceInfoUtil;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;


public abstract class HealthCardDAO extends
        HibernateSupportDelegateDAO<HealthCard> {

    public static final org.slf4j.Logger log = LoggerFactory.getLogger(HealthCardDAO.class);

    public HealthCardDAO() {
        super();
        this.setEntityName(HealthCard.class.getName());
        this.setKeyField("healthCardId");
    }

    @RpcService
    @DAOMethod(sql = "from HealthCard where cardOrgan=:organId and (cardId=:cardId or initialCardID = :initialCardID)")
    public abstract HealthCard getByCardOrganAndCardId(@DAOParam("organId")int organId,@DAOParam("cardId")String cardId,@DAOParam("initialCardID")String initialCardID);

    @RpcService
    @DAOMethod(sql = "from HealthCard where cardOrgan=:organId and mpiId=:mpiId and (cardId=:cardId or initialCardID = :initialCardID)")
    public abstract HealthCard getByCardOrganAndMpiIdAndCardId(@DAOParam("organId")int organId,@DAOParam("mpiId")String mpiId,@DAOParam("cardId")String cardId,@DAOParam("initialCardID")String initialCardID);


    @RpcService
    @DAOMethod(sql = "select mpiId from HealthCard where cardOrgan=:organId and (cardId=:cardId or initialCardID = :initialCardID)")
    public abstract String getMpiIdByCard(@DAOParam("organId")int organId,@DAOParam("cardId")String cardId,@DAOParam("initialCardID")String initialCardID);

    @RpcService
    @DAOMethod
    public abstract List<HealthCard> findByMpiId(String mpiId);

    @RpcService
    @DAOMethod
    public abstract List<HealthCard> findByCardOrganAndMpiId(int organId,
                                                             String mpiId);

    /**
     * 根据mpiid和发卡机构获取医保卡号
     *
     * @param mpiId
     * @param cardOrgan
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select cardId from HealthCard where mpiId=:mpiId and cardOrgan=:cardOrgan and cardType='2'")
    public abstract String getMedicareCardId(@DAOParam("mpiId") String mpiId,
                                             @DAOParam("cardOrgan") Integer cardOrgan);

    @DAOMethod
    public abstract List<HealthCard> findByMpiIdAndCardType(String mpiId,
                                                            String cardType);

    /**
     * 两天取患者最近使用的医保卡
     * @param mpiId
     * @param cardOrgan
     * @return
     */
    @DAOMethod(sql = "from HealthCard where mpiId=:mpiId and cardOrgan=:cardOrgan")
    public abstract HealthCard getByTwo(@DAOParam("mpiId") String mpiId,
                                          @DAOParam("cardOrgan") Integer cardOrgan);

    /**
     * 三条件获取患者最近使用的医保卡
     * <p>
     * eh.mpi.dao
     *
     * @param mpiId     主索引
     * @param cardOrgan 发卡机构
     * @param cardType  证卡类型
     * @return HealthCard
     * @author luf 2016-2-25
     */
    @DAOMethod(sql = "from HealthCard where mpiId=:mpiId and cardOrgan=:cardOrgan and cardType=:cardType")
    public abstract HealthCard getByThree(@DAOParam("mpiId") String mpiId,
                                          @DAOParam("cardOrgan") Integer cardOrgan,
                                          @DAOParam("cardType") String cardType);

    @DAOMethod(sql = "from HealthCard where mpiId=:mpiId and cardOrgan=:cardOrgan and cardType=:cardType")
    public abstract List<HealthCard> findByMpiIdAndCardTypeAndCardOrgan(@DAOParam("mpiId") String mpiId, @DAOParam("cardType") String cardType, @DAOParam("cardOrgan") Integer cardOrgan);

    /**
     * 根据患者主索引，卡号及卡类型查询卡信息
     *
     * @param mpiId    主索引
     * @param cardId   卡号
     * @param cardType 卡类型
     * @return
     */
    @DAOMethod(sql = "from HealthCard where mpiId=:mpiId and cardType=:cardType and (cardId=:cardId or initialCardID = :initialCardID)")
    public abstract List<HealthCard> findByMpiIdAndCardIdAndCardType(
            @DAOParam("mpiId")String mpiId,@DAOParam("cardId")String cardId,@DAOParam("cardType")String cardType,@DAOParam("initialCardID")String initialCardID);

    @DAOMethod(sql = "from HealthCard where mpiId=:mpiId and cardOrgan=:cardOrgan and (cardId=:cardId or initialCardID = :initialCardID)")
    public abstract List<HealthCard> findByMpiIdAndCardIdAndCardOrgan(
            @DAOParam("mpiId")String mpiId,@DAOParam("cardOrgan")Integer cardOrgan,@DAOParam("cardId")String cardId,@DAOParam("initialCardID")String initialCardID);

    @DAOMethod(sql = "from HealthCard where mpiId=:mpiId and cardOrgan=:cardOrgan ")
    public abstract List<HealthCard> findByMpiIdAndCardOrgan(
            @DAOParam("mpiId")String mpiId,@DAOParam("cardOrgan")Integer cardOrgan);

    /**
     * 绑卡/新建档案
     *
     * @param mpiId     患者主索引-必传
     * @param cardType  卡类型-绑卡时必传，建档时非必传
     * @param cardId    卡号-绑卡时必传，建档时非必传
     * @param cardOrgan 预约申请机构-必传
     * @param flag      标志-0绑卡；1建档
     * @return
     */
    @RpcService
    public HashMap<String, Object> bindCard(String mpiId, String cardType, String cardId, Integer cardOrgan, int flag) {
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is required!");
        }
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient p = patientDAO.get(mpiId);
        if (p == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patient is required!");
        }
        if (StringUtils.isEmpty(cardType) && flag == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardType is required!");
        }
        if (StringUtils.isEmpty(cardId) && flag == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardId is required!");
        }
        if (cardOrgan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardOrgan is required!");
        }
        PatientQueryRequest pat = new PatientQueryRequest();
        pat.setMpi(mpiId);
        pat.setOrgan(cardOrgan);
        pat.setCertID(p.getIdcard());
        pat.setPatientName(p.getPatientName());
        pat.setMobile(p.getMobile());
        pat.setCardType(cardType);
        pat.setCardID(cardId.toUpperCase());
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(cardOrgan);
        String hisServiceId = cfg.getAppDomainId() + ".patientService";//调用服务id
        PatientQueryRequest result = null;
        IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
        if (flag==0) {
            //result = (PatientQueryRequest) RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "bandCard", pat);
            //cardId = result.getCardID();
        	if(DBParamLoaderUtil.getOrganSwich(pat.getOrgan())){
        		PatientQueryRequestTO responseTO = new PatientQueryRequestTO();
        		PatientQueryRequestTO reqTO= new PatientQueryRequestTO();
        		BeanUtils.copy(pat,reqTO);
        		responseTO = iPatientHisService.bandCard(reqTO);
                result=new PatientQueryRequest();
                BeanUtils.copy(responseTO, result);
        	}else{
        		result = (PatientQueryRequest) RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "bandCard", pat);
        	}
            if (result != null && result.isSuccess()) {

            }else{
                String errMsg = "绑卡失败";

                if (result!=null && result.getErrMsg()!=null) {
                    errMsg += result.getErrMsg();
                }

                throw new DAOException(errMsg);
            }
        }else {
            //result = (PatientQueryRequest) RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "createPatient", pat);
            //cardId = result.getCardID();
        	if(DBParamLoaderUtil.getOrganSwich(pat.getOrgan())){
        		PatientQueryRequestTO responseTO = new PatientQueryRequestTO();
        		PatientQueryRequestTO reqTO= new PatientQueryRequestTO();
        		BeanUtils.copy(pat,reqTO);
        		responseTO = iPatientHisService.createPatient(reqTO);
                result=new PatientQueryRequest();
                BeanUtils.copy(responseTO, result);
        	}else{
        		result = (PatientQueryRequest) RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "createPatient", pat);
        	}
        	//cardId = result == null ? "" : result.getCardID();
            log.info("createPatient result: [{}]",JSONUtils.toString(result));
            if (result != null && result.isSuccess()) {

            }else{
                String errMsg = "建档失败";
                if (result!=null && result.getErrMsg()!=null) {
                    errMsg += result.getErrMsg();
                }
                throw new DAOException(errMsg);
            }
        }

        log.info("bindCard的结果 flag：[{}],result: [{}]",flag,JSONUtils.toString(result));
        String needVcode = cfg.getNeedValidateCode();
        boolean isNeedVcode = "1".equals(needVcode);
        if(isNeedVcode&& !ValidateUtil.blankString(result.getMobile())){
            String vCode = DAOFactory.getDAO(ValidateCodeDAO.class).sendVcodeNoUser(result.getMobile(),"patient");
            result.setvCode(vCode);
        }
        HealthCard card = new HealthCard();
        card.setInitialCardID(cardId);
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("patientName", p.getPatientName());
        map.put("mpiid", p.getMpiId());
        map.put("organId", cardOrgan);
        if (result == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "系统错误");
        }
        if (!StringUtils.isEmpty(result.getErrCode()) && !result.getErrCode().equals("0")) {
            map.put("patientQueryRequest", result);
            map.put("healthCard", card);
            return map;
        }
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        PatientTypeDAO typeDAO = DAOFactory.getDAO(PatientTypeDAO.class);
        String addr = organDAO.getAddrAreaByOrganId(cardOrgan);
        String key = typeDAO.getKeyByAddrArea(addr);
        if (flag == 0) {//绑卡
            List<HealthCard> h = this.findByMpiIdAndCardOrgan(mpiId,cardOrgan);
            if (h == null || h.isEmpty()) {
                card.setMpiId(mpiId);
                if (!StringUtils.isEmpty(key)) {
                    card.setCardOrgan(Integer.valueOf(key));
                    patientDAO.updatePatientTypeByMpiId(key, mpiId);
                }else {
                    card.setCardOrgan(cardOrgan);
                }
                if("2".equals(cardType)){//主要分医保卡 和 非医保医院卡证
                    card.setCardType("2");
                }else {
                    card.setCardType("1");
                }
                card.setCardId(cardId.toUpperCase());
                card.setInitialCardID(cardId);
                this.save(card);
            }
            map.put("patientQueryRequest", result);
            map.put("healthCard", card);
            return map;
        }
        //建档
        if (result.getHisCardList()==null || result.getHisCardList().size()==0) {
            map.put("patientQueryRequest", result);
            map.put("healthCard", card);
            return map;
        }
        card.setMpiId(mpiId);
        if (!StringUtils.isEmpty(key)) {
            card.setCardOrgan(Integer.valueOf(key));
            patientDAO.updatePatientTypeByMpiId(key, mpiId);
        }else {
            card.setCardOrgan(cardOrgan);
        }
        card.setCardType(result.getCardType());
        card.setCardId(cardId);
        log.info("卡号："+cardId);
        HealthCard  healthCardDB = getByCardOrganAndMpiIdAndCardId(cardOrgan,mpiId,cardId.toUpperCase(),cardId);
        if(healthCardDB == null&&!StringUtils.isEmpty(cardId)){
            this.save(card);
        }
        map.put("patientQueryRequest", result);
        map.put("healthCard", card);
//        String needVcode = cfg.getNeedValidateCode();
//        boolean isNeedVcode = "1".equals(needVcode);
        if(result==null){
            map.put("mobile","");
            map.put("msg","您在医院的记录中没有手机号，无法接收验证短信，请前往医院登记手机号");
        }else{
            map.put("mobile",result.getMobile());
        }
        map.put("isNeedVcode",isNeedVcode);
        return map;
    }


    /**
     * 传入的卡信息为新的卡类型，则新增一张卡;传入的卡信息只是卡号修改，则只修改卡号
     */
    public void saveOrUpdateHealthCards(List<HealthCard> cards,String mpiId){
        if(CollectionUtils.isEmpty(cards) || StringUtils.isEmpty(mpiId)){
            return;
        }

        for (HealthCard healthCard : cards) {
            HealthCard orgCard = getByCardOrganAndCardId(
                    healthCard.getCardOrgan(), healthCard.getCardId().toUpperCase(), healthCard.getCardId());
            if (orgCard != null) {
                continue;
            }

            int cardOrgan = healthCard.getCardOrgan();
            List<HealthCard> cardList = findByCardOrganAndMpiId(cardOrgan, mpiId);

            if (healthCard.getCardType().equals("1")
                    || cardList.size() == 0) {
                healthCard.setMpiId(mpiId);
                healthCard.setInitialCardID(healthCard.getCardId());
                healthCard.setCardId(healthCard.getCardId().toUpperCase());
                save(healthCard);
            } else {
                HealthCard card = cardList.get(0);
                card.setInitialCardID(healthCard.getCardId());
                card.setCardId(healthCard.getCardId().toUpperCase());
                update(card);
            }
        }
    }

    /**
     * 添加患者医院卡证
     * */
    @RpcService
    public boolean addOrganCard(int organ ,String cardId, String mpiid){
        List<HealthCard> card = this.findByMpiIdAndCardOrgan(mpiid,organ);
        if(!card.isEmpty()){
            log.info("该病人已有医院卡证信息 mpiid："+mpiid+"  organ:"+organ);
            return true;
        }
        return saveOrganCard(organ,cardId,mpiid,"1");

    }

    /**
     * 保存患者医院卡证
     * */
    public boolean saveOrganCard(int organ ,String cardId, String mpiid,String cardType){
        // 更新患者的医院卡证
        if(!StringUtils.isEmpty(cardId)){
            HealthCard healthCard = new HealthCard();
            healthCard.setMpiId(mpiid);
            healthCard.setCardType(cardType);// 医院卡证，医保卡
            healthCard.setCardOrgan(organ);
            healthCard.setValidDate(new Date());
            healthCard.setCardId(cardId);
            healthCard.setInitialCardID(cardId.toUpperCase());
            healthCard.setCardSource(CardSource.Remote.getDesc());
            save(healthCard);
            return true;
        }
        return false;
    }


    /**
     * 预约挂号发起业务时候，当本地有远程卡时候： 不再执行远程拉取就诊卡的操作
     * 当本地库没有远程就诊卡的时候，，一律远程拉取。
     * @param organId
     * @param mpiid
     * @return
     * @throws Exception
     */
    @RpcService
    public List<HealthCard>  appointQueryHealthCard(final Integer organId, final String mpiid) throws Exception {
        List<HealthCard> cards = findByMpiIdAndCardTypeAndCardOrgan(mpiid, CardTypeEnum.HOSPITALCARD.getValue(), organId);

        int remoteCardsNum=0;
        for (HealthCard card : cards) {
            if (card.getCardSource()!=null && card.getCardSource().equalsIgnoreCase(CardSource.Remote.getDesc())){
                remoteCardsNum++;
            }
        }

        boolean remotePull = false;
        if (remoteCardsNum==0){
            remotePull = true;
        }
        return queryHealthCardFromHisAndMerge(organId, mpiid, remotePull);
    }


    /**
     * 查询患者在某机构的就诊卡，并合并至本地库
     * @param organId 机构Id
     * @param mpiid 患者id
     * @param remotePull 开关，true的话从远程拉取合并，false，从本地数据库查询
     * @return 当前机构支持远程拉取，就拉取合并再返回合并结果；如果不是支持远程拉取，返回本地卡信息
     */
    @RpcService
    public List<HealthCard> queryHealthCardFromHisAndMerge(final Integer organId, final String mpiid, final boolean remotePull) throws Exception {
        if (remotePull) {

            boolean isXianYang = isXianYangOrgan(organId);
            String hisServiceName = "";
            if (isXianYang){
                hisServiceName = ".patientUserService";
            }else{
                hisServiceName = ".patientService";
            }

            OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
            /*Boolean canQueryHealthCardFromHis = organDAO.canQueryHealthCardFromHis(organId,hisServiceName);

            if (canQueryHealthCardFromHis != null && canQueryHealthCardFromHis) {*/
                HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
                HisServiceConfig hisServiceConfig = hisServiceConfigDAO.getByOrganId(organId);
                if (hisServiceConfig != null && hisServiceConfig.getAppDomainId() != null) {
                    String hisServiceId = hisServiceConfig.getAppDomainId() + hisServiceName;//调用服务id

                    PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                    Patient patient = patientDAO.getByMpiId(mpiid);

                    String pName = patient.getPatientName();
                    String certId = patient.getIdcard();
                    if (patient.getGuardianFlag() != null && patient.getGuardianFlag().booleanValue()) {
                        if (certId.contains("-")) {
                            int index = certId.indexOf("-");
                            certId = certId.substring(0, index);
                        }
                    }

                    String patientType = patient.getGuardianFlag() == null ? "1" : patient.getGuardianFlag() ? "2" : "1";
                    String mobile = patient.getMobile();



                    if (!isXianYang) {

                        HisResponse<PatientQueryRequest> queryPatient = hisServiceConfigDAO.getPatientFromHis(organId,patient);
                        //log.info("hisServiceConfigDAO.getPatientFromHis:organId:[{}]patient:[{}] dao[{}]",organId,JSONUtils.toString(patient), JSONUtils.toString(queryPatient));
                        PatientQueryRequest queryRequest = queryPatient.getData();
                        if (queryPatient != null && queryPatient.isSuccess() && queryRequest != null) {
                            if (queryRequest.getCardType()!=null && queryRequest.getCardType().equalsIgnoreCase(CardTypeEnum.HOSPITALCARD.getValue())) {
                                queryRequest.setOrgan(organId);
                                queryRequest.setMpi(mpiid);
                                merge(queryRequest);
                            }
                        }


                     /*   PatientQueryRequest patientQueryRequest = new PatientQueryRequest();
                        patientQueryRequest.setMpi(mpiid);
                        patientQueryRequest.setOrgan(organId);


                        if (patient != null) {
                            patientQueryRequest.setCertID(patient.getIdcard());
                            HisResponse<PatientQueryRequest> queryPatient = null;
                            if(DBParamLoaderUtil.getOrganSwich(patientQueryRequest.getOrgan())){
                        		IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
                        		HisResponseTO<PatientQueryRequestTO> responseTO = new HisResponseTO();
                        		PatientQueryRequestTO reqTO= new PatientQueryRequestTO();
                        		BeanUtils.copy(patientQueryRequest,reqTO);
                        		responseTO = iPatientHisService.queryPatient(reqTO);
                        		BeanUtils.copy(responseTO, queryPatient);
                        	}else{
                        		queryPatient = (HisResponse<PatientQueryRequest>) RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "queryPatient", patientQueryRequest);
                        	}


                            if (queryPatient != null && queryPatient.isSuccess() && queryPatient.getData() != null) {
                                if (queryPatient.getData().getCardType().equalsIgnoreCase(CardTypeEnum.HOSPITALCARD.getValue())) {
                                    merge(queryPatient.getData());
                                }
                            }
                        }*/
                    } else {
                    	Object cardInfo = null;
                    	if(DBParamLoaderUtil.getOrganSwich(hisServiceConfig.getOrganid())){
                    		IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
                    		cardInfo = iPatientHisService.getPatientCode(hisServiceConfig.getOrganid(),pName, certId, mobile, patientType);
                		}else{
                			cardInfo = RpcServiceInfoUtil.getClientService(
                                    IPatientService.class, hisServiceId, "getPatientCode", pName, certId, mobile, patientType);
                		}
                        log.info("[{}]咸阳获取的卡信息[{}]", certId, cardInfo);
                        if (cardInfo!=null ){
                            PatientQueryRequest result = new PatientQueryRequest();
                            result.setOrgan(organId);
                            result.setCardType(CardTypeEnum.HOSPITALCARD.getValue());
//                            result.setCardID(cardInfo.toString());
                            ArrayList<String[]> cards = Lists.newArrayList();
                            String[] card = {String.valueOf(cardInfo), CardTypeEnum.HOSPITALCARD.getValue()};
                            cards.add(card);
                            result.setHisCardList(cards);

                            result.setMpi(mpiid);

                            merge(result);
                        }

                    }

                }
           /* }*/
        }

        //查询当前用户当前机构的卡的数目
        List<HealthCard> hospitalCards = this.findHospitalCards(mpiid, organId);
        //替换cardId中的中文
        for (HealthCard hospitalCard : hospitalCards) {
            String cardId = hospitalCard.getCardId();
            hospitalCard.setCardId(LocalStringUtil.replaceChinese(cardId));
        }
        return hospitalCards;
    }

    private boolean isXianYangOrgan(Integer organId) {
        boolean isXianYangOrgan = false;
        if (organId!=null && (organId>=1000774 && organId<=1000781)){
            isXianYangOrgan = true;
        }
        return isXianYangOrgan;
    }

    @RpcService
    @DAOMethod(sql = "from HealthCard where mpiId=:mpiId and cardOrgan=:cardOrgan and cardType='1'")
    public abstract List<HealthCard> findHospitalCards(@DAOParam("mpiId") String mpiId,
                                                       @DAOParam("cardOrgan") Integer cardOrgan);

    private void merge(PatientQueryRequest patientQueryRequest) {
        log.info("health card merge patientQueryRequest[{}]:"+patientQueryRequest.getHisCardList());
        if(patientQueryRequest.isNewPatient()){//新病人没有卡号 但是可以预约
            return;
        }
        int organ = patientQueryRequest.getOrgan();
        List<String[]> hisCardList = patientQueryRequest.getHisCardList();
        log.info("health card merge hisCardList:"+hisCardList);
        if(hisCardList!=null&& !hisCardList.isEmpty()){
            String mpiId = patientQueryRequest.getMpi();
            for(String[]card : hisCardList){
                String cardID = card[0];
                String cardType = StringUtils.isEmpty(card[1])?"1":card[1];
                //当前查询的卡信息在数据库是否已经存在，不存在需要添加
                List<HealthCard> cards = this.findCards(organ, cardID);
                if (CollectionUtils.isEmpty(cards) && !StringUtils.isEmpty(cardID)) {
                    HealthCard healthCard = new HealthCard();
                    healthCard.setCardId(cardID.toUpperCase());
                    healthCard.setCardOrgan(organ);
                    healthCard.setCardType(cardType);
                    healthCard.setInitialCardID(cardID);
                    healthCard.setMpiId(mpiId);
                    healthCard.setCardSource(CardSource.Remote.getDesc());//就诊卡来源是远程拉取
                    save(healthCard);
                }
            }
        }
    }

    /**
     * 机构绑卡
     * 输入的卡信息通过医院的效验，增加至本地的数据库标识为本地卡，可删除;未通过医院的效验，给予提示
     * */
    @RpcService
    public HealthCard bindOrganCard( String cardId, Integer cardOrgan,String cardType, String mpiId) {
        validateparam(cardType, cardId, cardOrgan);

        HisResponse response = new HisResponse();
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient p = patientDAO.get(mpiId);
        if (p == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patient is required!");
        }

        OrganConfig organConfig = DAOFactory.getDAO(OrganConfigDAO.class).getByOrganId(cardOrgan);

        if (organConfig != null && organConfig.getManualAddHeathCard() != null && organConfig.getManualAddHeathCard()) {

            //查询输入的卡片信息是否已经存在
            HealthCard card = getByCardOrganAndMpiIdAndCardId(cardOrgan, mpiId, cardId, cardId);
            if (card!=null) {
                throw new DAOException("当前就诊卡号已经存在");
            }

            /*List<HealthCard> cardsInOrgan = this.findCards(cardOrgan, cardId);
            if (CollectionUtils.isNotEmpty(cardsInOrgan)) {
                throw new DAOException("当前就诊卡号已经存在");
            }*/

            PatientQueryRequest pat = new PatientQueryRequest();
            pat.setMpi(mpiId);
            pat.setOrgan(cardOrgan);
            pat.setCertID(p.getIdcard());
            pat.setPatientName(p.getPatientName());
            pat.setMobile(p.getMobile());
            pat.setCardType(cardType);
            pat.setCardID(cardId.toUpperCase());
            pat.setGuardianFlag(p.getGuardianFlag());
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            HisServiceConfig cfg = hisServiceConfigDao.getByOrganId(cardOrgan);
            String hisServiceId = cfg.getAppDomainId() + ".patientService";//调用服务id
            Object result = null;
            if (DBParamLoaderUtil.getOrganSwich(pat.getOrgan())) {
                IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
                PatientQueryRequestTO responseTO = new PatientQueryRequestTO();
                PatientQueryRequestTO reqTO = new PatientQueryRequestTO();
                BeanUtils.copy(pat, reqTO);
                responseTO = iPatientHisService.bandCard(reqTO);
                result=new PatientQueryRequest();
                BeanUtils.copy(responseTO, result);
            } else {
                result = RpcServiceInfoUtil.getClientService(IPatientService.class, hisServiceId, "bandCard", pat);
            }
            if (result != null) {
                PatientQueryRequest dd = (PatientQueryRequest) result;
                if (dd.isSuccess()) {
                    HealthCard patientCard = this.getByCardOrganAndMpiIdAndCardId(cardOrgan, mpiId, cardId, cardId);
                    if (patientCard == null) {
                        HealthCard healthCard = new HealthCard();
                        healthCard.setCardId(cardId.toUpperCase());
                        healthCard.setCardOrgan(cardOrgan);
                        healthCard.setCardType(cardType);
                        healthCard.setInitialCardID(cardId);
                        healthCard.setMpiId(mpiId);
                        healthCard.setCardSource(CardSource.Local.getDesc());//就诊卡来源是远程拉取
                        return save(healthCard);
                    }

                } else {
                    //输入的卡号在his不存在
                    throw new DAOException(dd.getErrMsg());
                }
            } else {
                //输入的卡号在his不存在
                throw new DAOException("未查到您的信息");
            }
        }else {
            throw new DAOException("当前机构不支持新增就诊卡信息");
        }

        return null;
    }
    public boolean validateparam(String cardType, String cardId, Integer cardOrgan){
        if (StringUtils.isEmpty(cardType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardType is required!");
        }
        if (StringUtils.isEmpty(cardId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardId is required!");
        }
        if (cardOrgan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "cardOrgan is required!");
        }
        String pattern = "[\u4e00-\u9fa5]";
        if (StringUtils.isNotEmpty(cardId) && Pattern.compile(pattern).matcher(cardId).find()) {
            throw new DAOException("card id contains unsupport Strings");
        }
        return true;
    }
    @DAOMethod(sql = "from HealthCard where cardOrgan=:organ and (cardId=:cardID or initialCardID=:cardID)")
    protected abstract List<HealthCard> findCards(@DAOParam("organ") int organ, @DAOParam("cardID") String cardID);


    /**
     * 患者手动增加就诊卡
     *
     * @param cardId 输入的就诊卡片号码
     * @param organ  选择的机构号码
     * @param mpiId 患者id
     */
    @RpcService
    @Deprecated
    // bindOrganCard 代替
    public HealthCard addLocalHealthCard(String cardId, Integer organ,String mpiId) {

        String pattern = "[\u4e00-\u9fa5]";
        if (StringUtils.isNotEmpty(cardId) && Pattern.compile(pattern).matcher(cardId).find()) {
            throw new DAOException("card id contains unsupport Strings");
        }


        OrganConfig organConfig = DAOFactory.getDAO(OrganConfigDAO.class).getByOrganId(organ);

        if (organConfig != null && organConfig.getManualAddHeathCard() != null && organConfig.getManualAddHeathCard()) {
            List<HealthCard> cards = this.findCards(organ, cardId);

            if (CollectionUtils.isNotEmpty(cards)) {
                throw new DAOException("当前就诊卡号已经存在");
            }

            /*UserRoleToken urt = UserRoleToken.getCurrent();
            Patient patient = (Patient) urt.getProperty("patient");*/

            HealthCard healthCard = new HealthCard(mpiId, cardId.toUpperCase(), CardTypeEnum.HOSPITALCARD.getValue(), organ, cardId);
            healthCard.setCardSource(CardSource.Local.getDesc());

            return save(healthCard);
        } else {
            throw new DAOException("当前机构不支持新增就诊卡信息");
        }


    }


    /**
     * 患者删除卡片
     *
     * @param healthCardId healthcard的主键id
     */
    @RpcService
    public void removeHealthCard(Integer healthCardId) {

        HealthCard healthCard = get(healthCardId);
        if (healthCard!=null) {

        //判断卡片是否是当前执行删除用户的就诊人的
        Boolean canDeleteCard = isCardBelongMyFamilyMember(healthCard);

        if (!canDeleteCard) {
            throw new DAOException("this is not your card");
        }
        //判断卡片是否能被删除
        if (healthCard != null && !healthCard.getCardSource().equalsIgnoreCase(CardSource.Local.getDesc())) {
            throw new DAOException("you can't delete the card from the remote service");
        }

        remove(healthCard.getHealthCardId());
        }
    }

    /**
     * 判断选择的卡片是否是我的就诊人的卡片
     * @param healthCard
     * @return
     */
    private boolean isCardBelongMyFamilyMember(HealthCard healthCard) {
        boolean canDeleteCard = false;
        String healthCardMpiId = healthCard.getMpiId();
        UserRoleToken urt = UserRoleToken.getCurrent();
        Patient patient = (Patient) urt.getProperty("patient");

        //如果是自己的卡，能删除
        if (Objects.equals(healthCardMpiId,patient.getMpiId())) {
            canDeleteCard=true;
            return canDeleteCard;
        }


        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
        List<HashMap<String, Object>> familyMemberList = familyMemberDAO.familyMemberList(patient.getMpiId());
        log.info("family member list {}",JSONUtils.toString(familyMemberList) );

        for (HashMap<String, Object> familyMember : familyMemberList) {
            Object patientObject = familyMember.get("patient");

            if (patientObject!=null){
                Patient familyMember_patient = (Patient) patientObject;
                if (familyMember_patient.getMpiId()!=null ){
                    //
                    if (!healthCardMpiId.equalsIgnoreCase(familyMember_patient.getMpiId())){
                        continue;
                    }else{
                    //当前的就诊卡是我的就诊人的卡
                        canDeleteCard = true;
                        break;
                    }
                }
            }
        }
        return canDeleteCard;
    }
    @Override
    @RpcService
    public HealthCard save(HealthCard o) throws DAOException {
        if(o.getCreateDate()==null){
            o.setCreateDate(new Date());
        }
        return super.save(o);
    }

    @DAOMethod(sql = "from HealthCard where cardOrgan=:organ and createDate>=:createDate")
    public abstract List<HealthCard> findByOrganAndDate(Integer organID,Date createDate);

}