package eh.mpi.dao;

import com.alibaba.fastjson.JSONObject;
import com.ngari.his.patient.mode.PatientTO;
import com.ngari.his.patient.service.IPatientHisService;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.schema.exception.ValidateException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.ErrorCode;
import eh.base.constant.PagingInfo;
import eh.base.constant.SystemConstant;
import eh.base.dao.RelationLabelDAO;
import eh.base.dao.RelationPatientDAO;
import eh.base.user.UserSevice;
import eh.bus.dao.OperationRecordsDAO;
import eh.entity.base.Employment;
import eh.entity.base.RelationLabel;
import eh.entity.mpi.*;
import eh.mpi.constant.PatientConstant;
import eh.mpi.service.PatientService;
import eh.remote.IHisServiceInterface;
import eh.util.*;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class
PatientDAO extends HibernateSupportDelegateDAO<Patient> {
    private static final Log logger = LogFactory.getLog(PatientDAO.class);

    // 环信患者用户登录密码
    private static final String PATIENT_LOGIN_PSD = "patient222";

    public PatientDAO() {
        super();
        this.setEntityName(Patient.class.getName());
        this.setKeyField("mpiId");
    }

    /**
     * 三条件查询患者（手机号，姓名，身份证）
     *
     * @param mobile
     * @param patientName
     * @param idcard
     * @return
     * @author LF
     */
    @RpcService
    public List<Patient> findByMobileOrNameOrIdCard(final String mobile,
                                                    final String patientName, final String idcard) {
        HibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer buffer = new StringBuffer("From Patient where 1=1");
                if (!StringUtils.isEmpty(mobile)) {
                    buffer.append(" and mobile like :mobile");
                }
                if (!StringUtils.isEmpty(patientName)) {
                    buffer.append(" and patientName like :patientName");
                }
                if (!StringUtils.isEmpty(idcard)) {
                    buffer.append(" and idcard like :idcard");
                }
                Query q = ss.createQuery(buffer.toString());

                if (!StringUtils.isEmpty(mobile)) {
                    q.setParameter("mobile", "%" + mobile + "%");
                }
                if (!StringUtils.isEmpty(patientName)) {
                    q.setParameter("patientName", "%" + patientName + "%");
                }
                if (!StringUtils.isEmpty(idcard)) {
                    q.setParameter("idcard", "%" + idcard + "%");
                }
                List<Patient> patients = q.list();
                setResult(patients);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 三条件查询患者（手机号，姓名，身份证）
     *
     * @param mobile
     * @param patientName
     * @param idcard
     * @return
     * @author LF
     */
    @RpcService
    public QueryResult<Patient> findPatient(final Date startDate, final Date endDate, final String patientName,
                                            final String idcard, final Integer sex, final Integer minAge, final Integer maxAge,final String mobile,final Integer start, final Integer limit,final Boolean hasRegister ){
        HibernateStatelessResultAction<QueryResult<Patient>> action = new AbstractHibernateStatelessResultAction<QueryResult<Patient>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {

                StringBuffer buffer = new StringBuffer(" From Patient where DATE(createDate)>=DATE(:startDate) and DATE(createDate)<=DATE(:endDate) ");
                if (!StringUtils.isEmpty(idcard)) {
                    buffer.append(" and idcard = :idcard");
                }
                if (!StringUtils.isEmpty(mobile)) {
                    buffer.append(" and mobile = :mobile");
                }
                if (!StringUtils.isEmpty(patientName)) {
                    buffer.append(" and patientName = :patientName");
                }
                if(sex != null && sex > 0)
                {
                    buffer.append(" and patientSex = :sex");
                }
                if(maxAge != null && maxAge>=0) {
                    buffer.append(" and DATE(birthday)>DATE(:startBirthDay)");
                }
                if(minAge != null && minAge>=0) {
                    buffer.append(" and DATE(birthday)<=DATE(:endBirthDay)");
                }
                if(hasRegister!=null){
                    if(hasRegister){
                        buffer.append(" and IFNULL(loginId,'')!=''");
                    }else{
                        buffer.append(" and IFNULL(loginId,'')=''");
                    }
                }
                Query countQuery=ss.createQuery("select count(*) "+ buffer.toString());

                buffer.append(" order by createDate DESC");
                Query q = ss.createQuery(buffer.toString());

                if (!StringUtils.isEmpty(patientName)) {
                    countQuery.setParameter("patientName", patientName);
                    q.setParameter("patientName", patientName );
                }
                if (!StringUtils.isEmpty(mobile)) {
                    countQuery.setParameter("mobile", mobile );
                    q.setParameter("mobile", mobile );
                }
                if (!StringUtils.isEmpty(idcard)) {
                    countQuery.setParameter("idcard", idcard );
                    q.setParameter("idcard", idcard );
                }
                if (maxAge != null && maxAge >= 0) {
                    final Date startBirthDay = DateConversion.getBirthdayFromAge(maxAge > 0 ? maxAge + 1 : 0);
                    countQuery.setDate("startBirthDay", startBirthDay);
                    q.setDate("startBirthDay", startBirthDay);
                }
                if (minAge != null && minAge >= 0) {
                    final Date endBirthDay = DateConversion.getBirthdayFromAge(minAge);
                    countQuery.setDate("endBirthDay", endBirthDay);
                    q.setDate("endBirthDay", endBirthDay);
                }
                if(sex != null && sex > 0)
                {
                    countQuery.setParameter("sex",sex.toString());
                    q.setParameter("sex",sex.toString());
                }
                countQuery.setParameter("startDate", startDate );
                countQuery.setParameter("endDate", endDate );
                q.setParameter("startDate", startDate );
                q.setParameter("endDate", endDate );
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<Patient> patients = q.list();

                QueryResult<Patient> qResult = new QueryResult<Patient>(
                        (long)countQuery.uniqueResult(), q.getFirstResult(), q.getMaxResults(), patients);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
    /**
     * 修改病人姓名-手机号-医保卡号
     *
     * @param patient
     * @return
     */
    @RpcService
    public Patient updateBussAndAppointPatientName(final Patient patient) {
        logger.info("updateBussAndAppointPatientName:"
                + JSONUtils.toString(patient));
        final HealthCardDAO cardDao = DAOFactory.getDAO(HealthCardDAO.class);

        final String mpiId = patient.getMpiId();
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required!");
        }

        final String patientName = patient.getPatientName();
        if (StringUtils.isEmpty(patientName)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientName is required!");
        }

        final String mobile = patient.getMobile();
        if (StringUtils.isEmpty(mobile)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mobile is required!");
        }

        final Patient dbPatient = getByMpiId(mpiId);
        if (dbPatient == null) {
            throw new DAOException(609, "患者数据不存在");
        }

        final String dbPatientName = dbPatient.getPatientName();

        AbstractHibernateStatelessResultAction<Patient> action = new AbstractHibernateStatelessResultAction<Patient>() {
            @SuppressWarnings({"unchecked"})
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<HealthCard> cards = new ArrayList<HealthCard>();
                if (patient.getHealthCards() != null) {
                    cards = patient.getHealthCards();
                }

                // 传入的卡信息为新的卡类型，则新增一张卡;传入的卡信息只是卡号修改，则只修改卡号
                //2016-12-8 10:43:45 zhangx 保存卡信息这块业务使用地方较多，抽取到saveOrUpdateHealthCards里面
                cardDao.saveOrUpdateHealthCards(cards,mpiId);

//                for (HealthCard healthCard : cards) {
//                    HealthCard orgCard = cardDao.getByCardOrganAndCardId(
//                            healthCard.getCardOrgan(), healthCard.getCardId().toUpperCase(), healthCard.getCardId());
//                    if (orgCard != null) {
//                        continue;
//                    }
//
//                    int cardOrgan = healthCard.getCardOrgan();
//                    List<HealthCard> cardList = cardDao
//                            .findByCardOrganAndMpiId(cardOrgan, mpiId);
//
//                    if (healthCard.getCardType().equals("1")
//                            || cardList.size() == 0) {
//                        healthCard.setMpiId(mpiId);
//                        healthCard.setInitialCardID(healthCard.getCardId());
//                        healthCard.setCardId(healthCard.getCardId().toUpperCase());
//                        cardDao.save(healthCard);
//                    } else {
//                        HealthCard card = cardList.get(0);
//                        card.setInitialCardID(healthCard.getCardId());
//                        card.setCardId(healthCard.getCardId().toUpperCase());
//                        cardDao.update(card);
//                    }
//                }

                if (!StringUtils.isEmpty(patient.getPatientType())) {
                    dbPatient.setPatientType(patient.getPatientType());
                }

                dbPatient.setPatientName(patient.getPatientName());
                dbPatient.setMobile(patient.getMobile());
                dbPatient.setLastModify(new Date());
                Patient returnP = update(dbPatient);
                returnP.setHealthCards(cardDao.findByMpiId(returnP.getMpiId()));

                OperationRecordsDAO operationRecordsDAO = DAOFactory
                        .getDAO(OperationRecordsDAO.class);

                // 更新病人姓名--历史记录表
                if (!dbPatientName.equals(patientName)) {
                    operationRecordsDAO.updatePatientNameByMpiId(patientName,
                            mpiId);
                }

                setResult(returnP);
            }

        };
        HibernateSessionTemplate.instance().executeTrans(action);

        //刷新服务器缓存
        Patient p = action.getResult();
        new UserSevice().updateUserCache(p.getLoginId(), SystemConstant.ROLES_PATIENT, "patient", p);

        return p;

    }

    /**
     * @param patient
     * @return
     * @author LF
     */
    @RpcService
    public Patient getHisToUpdate(Patient patient) {
        logger.info("getHisToUpdate:" + JSONUtils.toString(patient));
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment employment = (Employment) ur.getProperty("employment");
        String stringH = "h";
        String organString = "1";
        if (employment.getOrganId() != null) {
            organString = employment.getOrganId().toString();
        }
        String patientString = ".patientService";
//        IHisServiceInterface service = AppContextHolder.getBean(stringH
//                + organString + patientString, IHisServiceInterface.class);
        if (patient.getHealthCards() == null
                || patient.getHealthCards().size() <= 0) {
            HealthCard healthCard = new HealthCard();
            healthCard.setCardOrgan(employment.getOrganId());
            List<HealthCard> healthCards = new ArrayList<HealthCard>();
            healthCards.add(healthCard);
            patient.setHealthCards(healthCards);
        } else {
            patient.getHealthCards().get(0)
                    .setCardOrgan(employment.getOrganId());
        }
        Patient p = patient;
        // 判断HIS返回的值是否为空
        try {
            if(DBParamLoaderUtil.getOrganSwich(Integer.valueOf(organString))){
            	IPatientHisService iPatientHisService = AppDomainContext.getBean("his.iPatientHisService", IPatientHisService.class);
            	PatientTO responseTO = new PatientTO();
            	PatientTO reqTO= new PatientTO();
        		BeanUtils.copy(patient,reqTO);
        		responseTO = iPatientHisService.queryPatientInfo(reqTO,Integer.valueOf(organString));
        		BeanUtils.copy(p, responseTO);
        	}else{
        		p = (Patient) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, stringH + organString + patientString, "queryPatientInfo", patient);
        	}
        } catch (DAOException e) {
            if (e.getCode() == DAOException.VALUE_NEEDED) {
                return null;
            }
        }
        
        // p = (Patient) RpcServiceInfoUtil.getClientService(IHisServiceInterface.class, stringH + organString + patientString, "queryPatientInfo", patient);
        Patient patient2 = getOrUpdate(p);
        return patient2;
    }

    /**
     * 2016-12-19 zhangx wx2.7 儿童患者需求：当勾选监护人标记时，
     * 新增时前端录入idcard为监护人身份证，后台保存为idcard-(ABCD.....)
     * 更新时，前端录入idcard为idcard-(ABCD.....)，
     * @param p
     * @return
     */
    @RpcService
    public Patient getOrUpdate(final Patient p) {
        logger.info("getOrUpdate:" + JSONUtils.toString(p));
        //wx2.7 zhangx 2016-12-19 17:06:27 如果是儿童数据，身份证需要截取标记前18位身份证
        final Boolean guardianFlag=p.getGuardianFlag()==null?false:p.getGuardianFlag();

        String idCard=p.getIdcard().toUpperCase();
        if(guardianFlag){
            idCard=p.getIdcard().toUpperCase().split("-")[0];
        }

        final String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idCard);
        } catch (ValidateException e) {
            throw new DAOException(609, "身份证不正确");
        }

        //wx2.7 儿童患者需求： 儿童的出生日期已前端输入为准
        if(!guardianFlag){
            //2016-5-24 luf:前端日期截取错误bug修改
            String bi = idCard18.substring(6, 14);
            String bir = bi.substring(0, 4) + "-" + bi.substring(4, 6) + "-" + bi.substring(6, 8);
            Date birth = DateConversion.getCurrentDate(bir, "yyyy-MM-dd");
            p.setBirthday(birth);
            p.setGuardianName(null);
        }


        AbstractHibernateStatelessResultAction<Patient> action = new AbstractHibernateStatelessResultAction<Patient>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Patient target = null;
                PatientService service= AppContextHolder.getBean("eh.patientService",PatientService.class);

                //根据患者主键mpi查询
                String mpiId = p.getMpiId();
                if (!StringUtils.isEmpty(mpiId)) {
                    target = get(mpiId);
                }

                //根据患者身份证查询
                if (target == null) {
                    //wx2.7 儿童患者需求:患者端添加家庭成员的时候，【姓名，性别，出生年月，监护人身份证】判断，有则更新，无则新加
                    //婴儿患者【姓名，性别，出生年月，监护人身份证】不更新
                    if(guardianFlag){
                        //2017-1-2 15:44:15 zhangx 根据身份证循环查询校验，时间过长，使用like方式查询
//                    target=service.getChildPatient(p,idCard18);
                        target=service.getChildPatientLike(p,idCard18);
                        if(target!=null){
                            p.setIdcard(target.getIdcard());
                            p.setRawIdcard(target.getRawIdcard());
                        }
                    }else{

                        String idNumber = p.getIdcard();
                        if (!StringUtils.isEmpty(idNumber)) {
                            target = getByIdCard(idNumber);
                        }
                    }
                }

                //根据卡号卡信息查询
                List<HealthCard> cards = p.getHealthCards();
                List<HealthCard> newCards = new ArrayList<HealthCard>();
                HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
                if (cards != null) {
                    for (HealthCard card : cards) {
                        HealthCard orgCard = cardDAO.getByCardOrganAndCardId(
                                card.getCardOrgan(), card.getCardId().toUpperCase(), card.getCardId());
                        if (orgCard == null) {
                            newCards.add(card);
                            continue;
                        }
                        if (target == null) {
                            target = get(orgCard.getMpiId());
                        }
                    }
                }




                if (target != null) {
                    mpiId = target.getMpiId();
                    for (HealthCard healthCard : newCards) {
                        int cardOrgan = healthCard.getCardOrgan();
                        List<HealthCard> cardList = cardDAO
                                .findByCardOrganAndMpiId(cardOrgan,
                                        target.getMpiId());

                        // 该卡是健康卡(CardType:1-医院卡证；2-医保卡证)
                        if (healthCard.getCardType().equals("1")) {
                            healthCard.setMpiId(mpiId);
                            cardDAO.save(healthCard);

                            target.setPatientType("1");
                        } else {
                            // 医保卡未被添加过
                            if (cardList.size() == 0) {
                                healthCard.setMpiId(mpiId);
                                healthCard.setInitialCardID(healthCard.getCardId());
                                healthCard.setCardId(healthCard.getCardId().toUpperCase());
                                cardDAO.save(healthCard);
                            }

                            if (cardList.size() > 0) {
                                HealthCard card = cardList.get(0);
                                card.setInitialCardID(healthCard.getCardId());
                                card.setCardId(healthCard.getCardId().toUpperCase());
                                cardDAO.update(card);
                            }

                            target.setPatientType(healthCard.getCardOrgan()
                                    .toString());
                        }
                    }

                    //上海六院就诊人改造，处理了idcard，idcard2，此处将不允许更新身份证
                    //bug:11348 同一个身份证有2个就诊人，用后添加的就诊人来发业务，会报错
                    //bug:11365 【完善信息】用户完善信息不成功,判断数据库中的idcard2不为空，则不更新身份证相关数据
                    if(!StringUtils.isEmpty(target.getIdcard2())){
                        p.setIdcard(null);
                        p.setIdcard2(null);
                        p.setRawIdcard(null);
                    }


                    BeanUtils.map(p, target);
                    target.setLastModify(new Date());

                    if ("".equals(p.getAddress())) {
                        target.setAddress("");
                    }
                    if (p.getPhoto() != null && p.getPhoto() == 0) {
                        target.setPhoto(null);
                    }

                    update(target);

                } else {
                    isValidPatientData(p);
                    Date now = new Date();

                    //wx2.7 新增儿童患者： 儿童的身份证为监护人身份证加后缀
                    if(guardianFlag){
                        String suffix=service.getChildIdCardSuffix(p.getIdcard(),idCard18);
                        p.setRawIdcard(p.getIdcard()+suffix);
                        p.setIdcard(idCard18+suffix);
                    }else{
                        p.setRawIdcard(p.getIdcard());
                        p.setIdcard(idCard18);
                    }

                    p.setCreateDate(now);
                    p.setGuardianFlag(guardianFlag);
                    p.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
                    p.setHealthProfileFlag(p.getHealthProfileFlag()==null?false:p.getHealthProfileFlag());
                    p.setLastModify(now);
                    target = save(p);

                    for (HealthCard card : newCards) {
                        card.setMpiId(target.getMpiId());
                        card.setInitialCardID(card.getCardId());
                        card.setCardId(card.getCardId().toUpperCase());
                        cardDAO.save(card);
                    }
                }

                target.setHealthCards(cardDAO.findByMpiId(target.getMpiId()));
                setResult(target);
            }

        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 添加或更新患者，默认关注患者
     *
     * @param p
     * @param doctorId
     * @return
     */
    @RpcService
    public Patient getOrUpdateAndConcered(Patient p, Integer doctorId) {
        logger.info("添加或更新患者，默认关注患者getOrUpdateAndConcered:" + JSONUtils.toString(p));

        RelationPatientDAO relationPatientDAO = DAOFactory.getDAO(RelationPatientDAO.class);
        if (StringUtils.isNotEmpty(p.getMpiId()) && this.getByIdCard(p.getIdcard()) != null) {
            boolean isRelationPatient = relationPatientDAO.isRelationPatient(p.getMpiId(), doctorId);
            if(isRelationPatient){
                throw new DAOException(ErrorCode.SERVICE_ERROR,"您已关注该患者。");
            }
        }
        Patient patient = this.getOrUpdate(p);
        //默认关注
        RelationDoctor relationPatient = new RelationDoctor();
        relationPatient.setDoctorId(doctorId);
        relationPatient.setMpiId(patient.getMpiId());
        relationPatientDAO.addRelationPatient(relationPatient);

        return patient;
    }

    private void isValidPatientData(Patient p) {
        if (StringUtils.isEmpty(p.getPatientName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "name is required");
        }
        if (StringUtils.isEmpty(p.getPatientSex())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patient sex is required");
        }
        if (p.getBirthday() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "birthday is required");
        }
        if (StringUtils.isEmpty(p.getIdcard())) {
            List<HealthCard> cards = p.getHealthCards();
            if (cards == null || cards.isEmpty()) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "idcard or cards must has one exist.");
            }
            for (HealthCard card : cards) {
                if (StringUtils.isEmpty(card.getCardId())) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "cardId is required.");
                }
                if (card.getCardOrgan() == null || card.getCardOrgan() == 0) {
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "cardOrgan is required.");
                }
                // need more
            }
        }
    }

    /**
     * APP端查询详细信息的时候使用
     */
    @RpcService
    public Patient getByMpiId(String mpiId) {
        Patient p = get(mpiId);
        if (p == null) {
            throw new DAOException(609, "患者数据不存在");
        }
        List<HealthCard> hcs = new ArrayList<HealthCard>();
        List<HealthCard> cards = DAOFactory.getDAO(HealthCardDAO.class)
                .findByMpiId(mpiId);
        String pt = p.getPatientType();
        for (HealthCard hc : cards) {
            String ct = hc.getCardType();
            if (pt.equals("1")) {
                if (ct.equals("1")) {
                    hcs.add(hc);
                }
            } else if (ct.equals("2")
                    && pt.equals(hc.getCardOrgan().toString())) {
                hcs.add(hc);
            }
            if (hcs.size() > 0) {
                break;
            }
        }
        p.setHealthCards(hcs);
        PatientTypeDAO ptDao = DAOFactory.getDAO(PatientTypeDAO.class);
        PatientType ptt = ptDao.get(pt);
        if (ptt != null && ptt.getInputCardNo() != null) {
            p.setInputCardNo(ptt.getInputCardNo());
        } else {
            p.setInputCardNo(false);
        }
        if (StringUtils.isEmpty(p.getFullHomeArea())) {
            p.setFullHomeArea(getFullHomeArea(p.getHomeArea()));
        }
        return p;
    }

    /**
     * 根据区域码获取省市区
     *
     * @param areaCode
     * @return
     */
    public String getFullHomeArea(String areaCode) {
        if (!StringUtils.isEmpty(areaCode)) {
            try {
                String temp = null;
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < areaCode.length() / 2; i++) {
                    temp = areaCode.substring(0, 2 * (i + 1));
                    sb.append(DictionaryController.instance()
                            .get("eh.base.dictionary.AddrArea")
                            .getText(temp) + " ");
                }
                String result = sb.substring(0, sb.length() - 1);
                return result;
            } catch (ControllerException e) {
                logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            }
        }
        return null;
    }

    @RpcService
    public Patient getByCardInfo(int organId, String cardId) {
        String mpiId = DAOFactory.getDAO(HealthCardDAO.class).getMpiIdByCard(
                organId, cardId.toUpperCase(), cardId);
        if (mpiId == null) {
            return null;
        } else {
            return get(mpiId);
        }
    }

    /**
     * 更具主索引查询病人信息
     *
     * @param mpiId 病人主索引
     * @return
     */
    @RpcService
    public Patient getPatientByMpiId(String mpiId) {
        return get(mpiId);
    }

    @RpcService
    @DAOMethod
    public abstract Patient getByIdCard(String idCard);

    @RpcService
    @DAOMethod(sql = "from Patient where rawIdcard=:idcard")
    public abstract Patient getByRawIdCard(@DAOParam("idcard") String idCard);

    /**
     * 查询15位身份证或者18位身份证的患者信息
     *
     * @param idCard15    15位身份证
     * @param UpIdCard18  15位转成18位的大写身份证
     * @param lowIdCard18 15位转成18位的小写身份证
     * @return
     */
    @DAOMethod(sql = "from Patient where idcard=:idCard15 or idcard=:UpIdCard18 or idcard=:lowIdCard18 or rawIdcard=:idCard15")
    public abstract List<Patient> findPatientByIdCard(@DAOParam("idCard15") String idCard15, @DAOParam("UpIdCard18") String UpIdCard18, @DAOParam("lowIdCard18") String lowIdCard18);


    /**
     * idcard有值且createdate时间最早，最先插入的患者信息列表
     * @param idCard15    15位身份证
     * @param UpIdCard18  15位转成18位的大写身份证
     * @param lowIdCard18 15位转成18位的小写身份证
     * @return
     */
    @DAOMethod(sql = "from Patient where idcard>0 and  (idcard=:idCard15 or idcard=:UpIdCard18 or idcard=:lowIdCard18 or rawIdcard=:idCard15) order by createDate")
    public abstract List<Patient> findPatientByIdCardOrderByCreateDate(@DAOParam("idCard15") String idCard15, @DAOParam("UpIdCard18") String UpIdCard18, @DAOParam("lowIdCard18") String lowIdCard18);


    @DAOMethod(sql = "from Patient where patientName=:patientName and patientSex=:patientSex and birthday=:birthday and" +
            " (idcard like :idCard15 or idcard like :UpIdCard18 or idcard like:lowIdCard18 or rawIdcard like:idCard15)")
    public abstract List<Patient> findChildPatientByIdCard(@DAOParam("patientName") String patientName,@DAOParam("patientSex") String patientSex,
                                                      @DAOParam("birthday") Date birthday, @DAOParam("idCard15") String idCard15, @DAOParam("UpIdCard18") String UpIdCard18,
                                                      @DAOParam("lowIdCard18") String lowIdCard18);


    @RpcService
    @DAOMethod
    public abstract Patient getByMobile(String mobile);

    @RpcService
    @DAOMethod
    public abstract Patient getByLoginId(String loginId);

    @RpcService
    public Map<Integer, String> getPatientType() throws ControllerException {
        ctd.dictionary.Dictionary dic = DictionaryController.instance().get(
                "eh.mpi.dictionary.PatientType");
        Map<Integer, String> value = new HashMap<Integer, String>();
        int key = 1;
        while (dic.keyExist(String.valueOf(key))) {
            value.put(key, dic.getText(key + ""));
            key++;
        }
        return value;
    }

    /**
     * 更新照片字段，供updatePhotoByMpiId使用
     *
     * @param photo
     * @param mpiId
     */
    @DAOMethod
    public abstract void updatePhotoByMpiId(int photo, String mpiId);

    @SuppressWarnings("unchecked")
    @RpcService
    public Patient updatePhoto(int photo, String mpiId)
            throws ControllerException {

        updatePhotoByMpiId(photo, mpiId);
        Patient patient = get(mpiId);

        new UserSevice().updateUserCache(patient.getLoginId(), SystemConstant.ROLES_PATIENT, "patient", patient);
        return patient;

    }

    @DAOMethod
    public abstract void updateHealthProfileFlagByMpiId(boolean healthProfileFlag,String mpiId);

    @SuppressWarnings("unchecked")
    @RpcService
    public Patient updateHealthProfileFlag(boolean healthProfileFlag,String mpiId)
            throws ControllerException {

        updateHealthProfileFlagByMpiId(healthProfileFlag, mpiId);
        Patient patient = get(mpiId);

        new UserSevice().updateUserCache(patient.getLoginId(), SystemConstant.ROLES_PATIENT, "patient", patient);
        return patient;

    }
    /**
     * 创建患者用户 备注：用户患者APP注册
     *
     * @param patient
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-4-15 上午10:11:18
     */
    @RpcService
    public Patient createPatientUser(final Patient patient,
                                     final String password) throws DAOException {
        logger.info("createPatientUser:" + JSONUtils.toString(patient)
                + ";pwd:" + password);

        if (StringUtils.isEmpty(password)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "password is required");
        }

        String idCard = patient.getIdcard();
        if (StringUtils.isEmpty(idCard)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "idCard is required");
        }

        // 验证是否已经注册过患者
        final String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idCard.toUpperCase());
        } catch (ValidateException e) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证号码不正确");
        }
        List<Patient> patientList = findPatientByIdCard(idCard, idCard18, idCard18.toLowerCase());
        for (Patient validPatient : patientList) {
            if (validPatient != null) {
                String loginId = validPatient.getLoginId();
                if (!StringUtils.isEmpty(loginId)) {
                    throw new DAOException(609, "该身份证已经注册过患者用户");
                }
            }
        }

        isValidPatientData(patient);

        final UserRoleTokenDAO tokenDao = DAOFactory
                .getDAO(UserRoleTokenDAO.class);

        AbstractHibernateStatelessResultAction<Patient> action = new AbstractHibernateStatelessResultAction<Patient>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {

                UserDAO userDao = DAOFactory.getDAO(UserDAO.class);

                User u = new User();
                u.setId(patient.getMobile());
                u.setEmail(patient.getEmail());
                u.setName(patient.getPatientName());
                u.setPlainPassword(password);
                u.setCreateDt(new Date());
                u.setStatus("1");

                UserRoleTokenEntity urt = new UserRoleTokenEntity();
                urt.setUserId(patient.getMobile());
                urt.setRoleId("patient");
                urt.setTenantId("eh");
                urt.setManageUnit("eh");

                // 增加patient表信息
                Patient returnPatient = new Patient();

                // user表中不存在记录
                if (!userDao.exist(patient.getMobile())) {
                    // 创建角色(user，userrole两张表插入数据)
                    userDao.createUser(u, urt);
                    patient.setLoginId(urt.getUserId());
                    returnPatient = getOrUpdate(patient);

                } else {
                    // user表中存在记录,角色表中不存在记录
                    Object object = tokenDao.getExist(patient.getMobile(),
                            "eh", "patient");
                    if (object == null) {
                        ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
                                .instance().getUpdater();

                        patient.setLoginId(urt.getUserId());
                        returnPatient = getOrUpdate(patient);

                        urt.setProperty("patient", returnPatient);

                        up.createItem(returnPatient.getLoginId(), urt);

                    } else {
                        // user表中存在记录,角色表中存在记录
                        throw new DAOException(602, "该用户已注册过");
                    }
                }

                setResult(returnPatient);
            }

        };
        HibernateSessionTemplate.instance().executeTrans(action);

        // 注册患者环信账户
        UserRoleTokenEntity urt = (UserRoleTokenEntity) tokenDao.getExist(
                patient.getMobile(), "eh", "patient");
        if (urt != null) {
            String userName = Easemob.getPatient(urt.getId());
            Easemob.registUser(userName, SystemConstant.EASEMOB_PATIENT_PWD);
        }

        return action.getResult();
    }

    /**
     * 创建患者用户-纳里健康微信2.0注册接口
     *
     * @param p
     * @return
     * @throws ValidateException
     * @desc 微信端调用
     * @author zhangx
     * @date 2016-1-5 下午3:30:34
     */
    @RpcService
    public Patient createWXPatientUser(Patient p) throws ValidateException {
        Patient returnPatient = null;

        String mobile = p.getMobile();
        String idCard = p.getIdcard();
        String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idCard.toUpperCase());
        } catch (ValidateException e) {
            throw new DAOException(609, "身份证不正确");
        }

        Patient idCardPatient = getByIdCard(idCard);
        if (idCardPatient == null) {
            idCardPatient = getByIdCard(idCard18);
        }


        if (idCardPatient != null
                && !StringUtils.isEmpty(idCardPatient.getLoginId())
                && !idCardPatient.getLoginId().equals(mobile)) {
            // 身份证A存在且关联手机号B-完善信息-提示该用户已存在，手机号未138*****90，姓名为曹**
            String dbLoginId = idCardPatient.getLoginId();
            String dbName = idCardPatient.getPatientName();
            throw new DAOException(609, "该用户已存在，手机号为"
                    + dbLoginId.substring(0, 3)
                    + "******"
                    + dbLoginId.substring(dbLoginId.length() - 2,
                    dbLoginId.length())
                    + "，姓名为"
                    + dbName.substring(0, 1)
                    + dbName.substring(1, dbName.length()).replaceAll("[^\n]",
                    "*"));
        }

        String name = p.getPatientName();
        if (idCardPatient != null
                && !idCardPatient.getPatientName().trim().equals(name.trim())) {
            throw new DAOException(609, "您输入的真实姓名有误");
        }

        if (idCardPatient == null
                || StringUtils.isEmpty(idCardPatient.getLoginId())) {

            Patient loginIdPatient = getByLoginId(mobile);
            if (loginIdPatient != null) {
                loginIdPatient.setLoginId(null);
                update(loginIdPatient);
            }

            p.setLoginId(mobile);

            if (idCardPatient == null) {
                Boolean guardianFlag=p.getGuardianFlag()==null?false:p.getGuardianFlag();
                p.setGuardianFlag(guardianFlag);

                p.setRawIdcard(p.getIdcard());
                p.setIdcard(idCard18);
                p.setBirthday(ChinaIDNumberUtil.getBirthFromIDNumber(idCard18));
                p.setPatientSex(ChinaIDNumberUtil.getSexFromIDNumber(idCard18));
                p.setCreateDate(new Date());
                p.setLastModify(new Date());
                p.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
                p.setHealthProfileFlag(p.getHealthProfileFlag()==null?false:p.getHealthProfileFlag());
                p.setPatientType("1");// 1：自费
                returnPatient = save(p);
            } else {
                BeanUtils.map(p, idCardPatient);
                idCardPatient.setLastModify(new Date());
                returnPatient = update(idCardPatient);
            }
        }

        if (idCardPatient != null
                && !StringUtils.isEmpty(idCardPatient.getLoginId())
                && idCardPatient.getLoginId().equals(mobile)) {
            idCardPatient.setHomeArea(p.getHomeArea());
            idCardPatient.setFullHomeArea(p.getFullHomeArea());
            idCardPatient.setLastModify(new Date());
            returnPatient = update(idCardPatient);
        }

        return returnPatient;
    }


    /**
     * 获取当前患者数量
     *
     * @return
     * @author ZX
     * @date 2015-4-21 下午1:11:02
     */
    @RpcService
    @DAOMethod(sql = "SELECT COUNT(*) FROM Patient")
    public abstract Long getAllPatientNum();

    /**
     * 统计指定时间段内的患者数量
     *
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @return
     * @author ZX
     * @date 2015-5-21 下午3:58:02
     */
    @RpcService
    public  Long getPatientNumFromTo(final Date startDate, final Date endDate){
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            public void execute(StatelessSession ss) throws Exception {
                Date sTime = DateConversion.firstSecondsOfDay(startDate);
                Date eTime = DateConversion.lastSecondsOfDay(endDate);
                StringBuilder hql = new StringBuilder(
                        "SELECT COUNT(*) FROM Patient where createDate between :startDate  and :endDate");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("startDate", sTime);
                query.setParameter("endDate", eTime);
                long num = (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


    @DAOMethod(sql = "SELECT COUNT(*) FROM Patient where idCard=:idCard ")
    public abstract Long getPatientCountByIdCard(@DAOParam("idcard") String idCard);


    public Boolean isPatientExistCheckByIdCard(String idCard)
    {
        return this.getPatientCountByIdCard(idCard) >0;
    }

    /**
     * 获取本月新增患者数
     *
     * @return
     * @author ZX
     * @date 2015-5-21 下午4:00:17
     */
    @RpcService
    public Long getPatientNumByMonth() {
        Date startDate = DateConversion.firstDayOfThisMonth();
        Date endDate = new Date();
        return getPatientNumFromTo(startDate, endDate);
    }

    /**
     * 获取昨日新增患者数
     *
     * @return
     * @author ZX
     * @date 2015-5-21 下午4:00:17
     */
    @RpcService
    public Long getPatientNumByYesterday() {
        Date date = Context.instance().get("date.getYesterday", Date.class);
        return getPatientNumFromTo(date, date);
    }

    /**
     * 获取指定时间内登录过的用户数
     *
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-5-21 下午6:08:54
     */
    @RpcService
    public  Long getActiveNum(final Date startDate, final Date endDate)
    {
        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            public void execute(StatelessSession ss) throws Exception {
                Date sTime = DateConversion.firstSecondsOfDay(startDate);
                Date eTime = DateConversion.lastSecondsOfDay(endDate);
                StringBuilder hql = new StringBuilder(
                        "SELECT COUNT(*) FROM UserRoleTokenEntity where roleId='patient' and lastLoginTime between :startDate and :endDate ");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("startDate", sTime);
                query.setParameter("endDate", eTime);
                long num = (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 获取活跃用户数
     *
     * @return
     * @author ZX
     * @date 2015-5-21 下午6:01:44
     */
    @RpcService
    public Long getActivePatientNum() {
        Date startDate = DateConversion.getDaysAgo(2);
        Date endDate = new Date();
        return getActiveNum(startDate, endDate);
    }

    /**
     * 更新患者用户信息
     *
     * @param patient 要更新的病人信息
     * @return
     * @author ZX
     * @date 2015-6-3 下午6:57:07
     * @date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public Patient updatePatientUserInfo(Patient patient) {
        PatientService patientService = AppContextHolder.getBean("eh.patientService",
                PatientService.class);
        return patientService.updatePatientUserInfo(patient);
    }

    @RpcService
    @DAOMethod
    public abstract List<Patient> findByMpiIdIn(List<String> mpiId);

    @DAOMethod(sql = "select patientName from Patient where mpiId in :mpiIds", limit = 999)
    public abstract List<String> findNameByMpiIdIn(@DAOParam("mpiIds") List<String> mpiIds);

    /**
     * 将属地区域code转化成对应的text值
     *
     * @param AddrAreaCode
     * @return
     * @author hyj
     */
    @RpcService
    public DictionaryItem getAddrAreaTextByCode(String AddrAreaCode) {
        DictionaryItem d = new DictionaryItem();
        try {
            ctd.dictionary.Dictionary dic = DictionaryController.instance()
                    .get("eh.base.dictionary.AddrArea");
            d = dic.getItem(AddrAreaCode);

        } catch (ControllerException e) {
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        return d;
    }

    /**
     * 根据主索引取病人手机号
     *
     * @param mpiId 主索引
     * @return String 手机号
     * @author luf
     */
    @RpcService
    @DAOMethod(sql = "select mobile from Patient where mpiId=:mpiId")
    public abstract String getMobileByMpiId(@DAOParam("mpiId") String mpiId);

    @RpcService
    @DAOMethod(sql = "select loginId from Patient where mpiId=:mpiId")
    public abstract String getLoginIdByMpiId(@DAOParam("mpiId") String mpiId);

    /**
     * Title:根据病人（卡）类型和卡号获取病人
     *
     * @param patientType
     * @param cardNo
     * @return Patient
     * @author zhangjr
     * @date 2015-10-27
     */
    @RpcService
    @DAOMethod(sql = "select p from Patient p,HealthCard h where p.mpiId = h.mpiId and"
            + " h.cardId = :cardNo and p.patientType = :patientType")
    public abstract Patient getByCardNoAndPatientType(
            @DAOParam("cardNo") String cardNo,
            @DAOParam("patientType") String patientType);

    @RpcService
    @DAOMethod(sql = "select p from Patient p,HealthCard h where p.mpiId = h.mpiId and"
            + " h.cardId = :cardNo and h.cardOrgan = :organId")
    public abstract Patient getByCardNoAndOrganId(
            @DAOParam("cardNo") String cardNo,
            @DAOParam("organId") Integer organId);

    /**
     * 过滤病人信息(添加关注相关)
     *
     * @param mpiId
     * @param doctorId
     * @return
     */
    public Patient convertPatientForAppointRecord(String mpiId, Integer doctorId) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.get(mpiId);
        Patient p = new Patient();
        p.setPatientSex(patient.getPatientSex());
        p.setBirthday(patient.getBirthday());
        p.setPhoto(patient.getPhoto());
        p.setPatientName(patient.getPatientName());
        p.setPatientType(patient.getPatientType());
        p.setMobile(patient.getMobile());
        p.setIdcard(patient.getIdcard());
        RelationPatientDAO dao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationDoctor relationDoctor = dao.getByMpiidAndDoctorId(mpiId,
                doctorId);
        Integer relationDoctorId = 0;
        Boolean relationFlag = false;
        Boolean signFlag = false;
        List<String> labelNames = new ArrayList<String>();
        if (relationDoctor != null) {
            relationDoctorId = relationDoctor.getRelationDoctorId();
            relationFlag = true;
            if (relationDoctor.getFamilyDoctorFlag()) {
                signFlag = true;
            }
            RelationLabelDAO labelDAO = DAOFactory
                    .getDAO(RelationLabelDAO.class);
            labelNames = labelDAO.findLabelNamesByRPId(relationDoctorId);
        }
        p.setRelationPatientId(relationDoctorId);
        p.setRelationFlag(relationFlag);
        p.setSignFlag(signFlag);
        p.setLabelNames(labelNames);
        return p;
    }

    /**
     * 按姓名，身份证，手机号三个其中的一个或多个搜索医生关注的患者/医生的常用患者中符合条件的患者
     *
     * @param doctorId
     * @param name
     * @param idCard
     * @param mobile
     * @return
     * @author zhangx
     * @date 2015-11-25 下午5:18:35
     */
    @RpcService
    public List<Patient> searchPatientByDoctorId(Integer doctorId, String name,
                                                 String idCard, String mobile) {
        long start = 0;
        long limit = 20;

        OperationRecordsDAO opDao = DAOFactory
                .getDAO(OperationRecordsDAO.class);
        List<Patient> opList = opDao.findHistoryPatients(doctorId, "%" + name
                + "%", "%" + idCard + "%", "%" + mobile + "%", start, limit);

        RelationDoctorDAO relDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        List<Patient> relList = relDao.findAttentionPatients(doctorId, "%"
                        + name + "%", "%" + idCard + "%", "%" + mobile + "%", start,
                limit);

        opList.addAll(relList);
        // 去除重复项 --AngryKitty
        Set<Patient> set = new HashSet<Patient>(opList);
        Iterator<Patient> it = set.iterator();
        List<Patient> patients = new ArrayList<Patient>();
        while (it.hasNext()) {
            Patient p = getInfo(it.next(), doctorId);

            //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
            //解决方案，idcard字段赋值为空字符串
            if(StringUtils.isEmpty(p.getIdcard())){
                p.setIdcard("");
            }
            patients.add(p);
        }
        return patients;
    }

    private Patient getInfo(Patient op, int doctorId) {
        RelationPatientDAO rpDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationDoctorDAO rdDao = DAOFactory.getDAO(RelationDoctorDAO.class);

        Patient p = new Patient();
        p.setPatientName(op.getPatientName());
        p.setPatientSex(op.getPatientSex());
        p.setPatientType(op.getPatientType());
        p.setBirthday(op.getBirthday());
        p.setPhoto(op.getPhoto());
        p.setIdcard(op.getIdcard());
        p.setMobile(op.getMobile());
        p.setMpiId(op.getMpiId());
        p.setGuardianFlag(op.getGuardianFlag());
        RelationDoctor relDoc = rpDao.getByMpiidAndDoctorId(p.getMpiId(),
                doctorId);

        String remainDates = "";
        if (relDoc == null) {
            p.setSignFlag(false);
            p.setRelationFlag(false);
            p.setRemainDates(remainDates);
            p.setLabels(new ArrayList<RelationLabel>());
            p.setRelationPatientId(null);
        } else {
            // 计算签约剩余时间
            if (relDoc.getFamilyDoctorFlag() && relDoc.getEndDate() != null) {
                remainDates = rdDao.remainingRelationTime(relDoc.getEndDate());
            } else {
                remainDates = "";
            }
            p.setSignFlag(relDoc.getFamilyDoctorFlag());
            p.setRelationFlag(true);
            p.setRemainDates(remainDates);
            p.setRelationPatientId(relDoc.getRelationDoctorId());
        }

        return p;
    }

    /**
     * 获取患者部分信息(当前在转诊findTomorrowTransferByRequestDoctor,findTodayTransferByTargetDoctor使用)
     *
     * @param mpiId    患者ID
     * @param doctorId 医生ID
     * @return 患者部分要显示的信息
     */
    public Patient getPartInfo(String mpiId, int doctorId) {
        Patient patient = get(mpiId);

        Patient pat = new Patient();
        pat.setMpiId(mpiId);
        pat.setPatientSex(patient.getPatientSex());
        pat.setPatientName(patient.getPatientName());
        pat.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));
        pat.setPatientType(patient.getPatientType());
        pat.setPhoto(patient.getPhoto());
        pat.setBirthday(patient.getBirthday());

        RelationPatientDAO reDao = DAOFactory.getDAO(RelationPatientDAO.class);
        RelationLabelDAO labelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
        pat.setAge(patient.getBirthday() == null ? 0 : DateConversion
                .getAge(patient.getBirthday()));
        RelationDoctor rd = reDao.getByMpiidAndDoctorId(mpiId, doctorId);
        if (rd != null) {
            pat.setRelationPatientId(rd.getRelationDoctorId());
            pat.setRelationFlag(true);
            if (rd.getRelationType() == 0) {
                pat.setSignFlag(true);
            } else {
                pat.setSignFlag(false);
            }
            pat.setLabelNames(labelDAO.findLabelNamesByRPId(rd
                    .getRelationDoctorId()));
        } else {
            pat.setRelationFlag(false);
            pat.setSignFlag(false);
            pat.setLabelNames(new ArrayList<String>());
        }
        return pat;
    }


    /**
     * 外包医保卡（自费则取最后一条）
     *
     * @param idCard 身份证
     * @return Patient
     * @author luf
     */
    @RpcService
    public Patient getByIdCardAddHealthCards(String idCard) {
        final String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idCard
                    .toUpperCase());
        } catch (ValidateException e) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证不正确");
        }

        //上海六院改造-2017-7-4 16:29:05 zhangx 相同身份证的患者身份证数据保存在idcard2，rawIdcard，通过find方法查询出来的数据>2条
        //应取idcard有值且createdate时间最早，最先插入的
        //修复bug：app端输入身份证偶尔会查询出idcard2有值的患者数据
        List<Patient> patientList = findPatientByIdCardOrderByCreateDate(idCard, idCard18, idCard18.toLowerCase());
        if (patientList == null || patientList.isEmpty()) {
            return null;
        }
        if (patientList.size() > 2) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证信息有误，请联系客服进行校正");
        }
        Patient patient = patientList.get(0);
        if (StringUtils.isEmpty(patient.getPatientType())) {
            return patient;
        }
        String patientType = patient.getPatientType();
        String mpiId = patient.getMpiId();
        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        String cardId;
        List<HealthCard> cards = new ArrayList<HealthCard>();
        if (patientType.equals("1")) {
            cardId = "1";
            cards = cardDAO.findByMpiIdAndCardType(patient.getMpiId(), cardId);
        } else {
            cardId = "2";
            cards.add(cardDAO.getByThree(mpiId, Integer.valueOf(patientType),
                    cardId));
        }
        if (cards == null) {
            return patient;
        }
        int item = cards.size();
        if (item <= 0) {
            return patient;
        }
        HealthCard card=cards.get(item - 1);
        if(card!=null){
            card.setValidDate(null);
        }
        patient.setHealthCard(card);
        return patient;
    }

    @SuppressWarnings("unused")
    private Patient converIDCard(Patient p, String IDCard) {
        try {
            IDCard = ChinaIDNumberUtil.convert15To18(IDCard.toUpperCase());
        } catch (ValidateException e1) {
           logger.error(e1);
        }
        p.setIdcard(IDCard);
        if (IDCard.length() == 15) {
            int idcardsex = Integer
                    .parseInt(IDCard.substring(IDCard.length() - 1));

            p.setPatientSex(idcardsex % 2 == 0 ? "2" : "1");
            String idcardbirthday = "19" + IDCard.substring(6, 8) + "-"
                    + IDCard.substring(8, 10) + "-" + IDCard.substring(10, 12);

            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date birthday = null;
            try {
                birthday = sdf.parse(idcardbirthday);
                p.setBirthday(birthday);
            } catch (ParseException e) {
                logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            }
        } else {
            int idcardsex = Integer.parseInt(IDCard.substring(
                    IDCard.length() - 2, IDCard.length() - 1));
            p.setPatientSex(idcardsex % 2 == 0 ? "2" : "1");
            String idcardbirthday = IDCard.substring(6, 10) + "-"
                    + IDCard.substring(10, 12) + "-" + IDCard.substring(12, 14);
            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date birthday = null;
            try {
                birthday = sdf.parse(idcardbirthday);
                p.setBirthday(birthday);
            } catch (ParseException e) {
                logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            }
        }

        return p;
    }

    @RpcService
    @DAOMethod(sql = "select patientName from Patient where mpiId=:mpiId")
    public abstract String getNameByMpiId(@DAOParam("mpiId") String mpiId);

    /**
     * 判断患者和目标医生是否为同一个人
     *
     * @param mpiId
     * @param doctorId
     * @return
     * @author zhangx
     * @date 2016-3-7 下午1:42:05
     */
    @RpcService
    public boolean isSamePersion(String mpiId, int doctorId) {
        return SameUserMatching.patientAndDoctor(mpiId, doctorId);
    }

    /**
     * 获取患者部分信息，主要用于getAppointRecordInfoById
     *
     * @param mpIid
     * @return
     * @author zhangx
     * @date 2016-3-11 下午4:57:19
     */
    public Patient getPatientPartInfo(String mpIid) {
        Patient pat = new Patient();

        Patient p = get(mpIid);
        if (p != null) {
            pat.setMpiId(mpIid);
            pat.setPatientName(p.getPatientName());
            pat.setIdcard(p.getIdcard());
            pat.setMobile(p.getMobile());
            pat.setPatientSex(p.getPatientSex());
            pat.setPhoto(p.getPhoto());
            pat.setBirthday(p.getBirthday());
            pat.setPatientType(p.getPatientType());
        }

        return pat;
    }

    /**
     * 获取患者部分信息，主要用于getSignRecordByDoctor
     *
     * @param mpIid
     * @return
     * @author zhangx
     * @date 2016-3-11 下午4:57:19
     */
    public Patient getPatientPartInfoV2(String mpIid) {
        Patient pat = new Patient();

        Patient p = get(mpIid);
        if (p != null) {
            pat.setMpiId(mpIid);
            pat.setPatientName(p.getPatientName());
            pat.setPatientSex(p.getPatientSex());
            pat.setPhoto(p.getPhoto());
            pat.setBirthday(p.getBirthday());
            pat.setAge(DateConversion.getAge(p.getBirthday()));
            pat.setPatientType(p.getPatientType());
        }

        return pat;
    }

    /**
     * 创建患者用户  patient患者Excel导入注册
     *
     * @param patient
     * @return
     * @throws DAOException
     * @author houxr
     * @date 2016-4-4 上午10:11:18
     */
    @RpcService
    public Patient excellRegisterSignPatientUser(final Patient patient) throws DAOException {
        logger.info("importExcellPatientUser:" + JSONUtils.toString(patient));
        String idCard = patient.getIdcard();
        if (StringUtils.isEmpty(idCard)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "idCard is required");
        }
        // 验证是否已经注册过患者
        Patient validPatient = getByIdCard(idCard);
        if (validPatient != null) {
            String loginId = validPatient.getLoginId();
            if (!StringUtils.isEmpty(loginId)) {
                throw new DAOException(609, "该身份证已经注册过患者用户");
            }
        }
        isValidPatientData(patient);

        final UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);

        AbstractHibernateStatelessResultAction<Patient> action = new AbstractHibernateStatelessResultAction<Patient>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {

                UserDAO userDao = DAOFactory.getDAO(UserDAO.class);

                User u = new User();
                u.setId(patient.getMobile());
                u.setEmail(patient.getEmail());
                u.setName(patient.getPatientName());
                String idNummber = patient.getIdcard();
                u.setPlainPassword(idNummber.substring(idNummber.length() - 6));//获取身份证后六位
                u.setCreateDt(new Date());
                u.setStatus("1");

                UserRoleTokenEntity urt = new UserRoleTokenEntity();
                urt.setUserId(patient.getMobile());
                urt.setRoleId("patient");
                urt.setTenantId("eh");
                urt.setManageUnit("eh");

                // 增加patient表信息
                Patient returnPatient = new Patient();
                // user表中不存在记录
                if (!userDao.exist(patient.getMobile())) {
                    // 创建角色(user,userrole两张表插入数据)
                    userDao.createUser(u, urt);
                    patient.setLoginId(urt.getUserId());
                    returnPatient = getOrUpdate(patient);
                } else {
                    // user表中存在记录,角色表中不存在记录
                    Object object = tokenDao.getExist(patient.getMobile(), "eh", "patient");
                    if (object == null) {
                        ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
                                .instance().getUpdater();

                        patient.setLoginId(urt.getUserId());
                        returnPatient = getOrUpdate(patient);

                        urt.setProperty("patient", returnPatient);
                        up.createItem(returnPatient.getLoginId(), urt);
                    } else {
                        // user表中存在记录,角色表中存在记录
                        throw new DAOException(602, "该用户已注册过");
                    }
                }
                setResult(returnPatient);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 添加患者-历史患者(医生未关注患者)
     *
     * @param doctorId
     * @param pagingInfo
     * @return
     */
    public List<Patient> findHistoryPatient(final Integer doctorId, final PagingInfo pagingInfo) {
        AbstractHibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql ="SELECT DISTINCT(a.mpiId),p.PatientName,p.PatientSex,p.Birthday,p.Photo,p.PatientType FROM (SELECT t.MPIID as mpiId FROM bus_operationrecords t " +
                        "WHERE t.RequestDoctor = :doctorId AND t.MPIID IS NOT NULL " +
                        "AND NOT EXISTS (SELECT s.mpiId FROM mpi_relationdoctor s WHERE t.MPIID = s.MPIID AND t.RequestDoctor = s.DoctorID AND (s.RelationType=2 OR (s.RelationType = 0 AND CURRENT_TIMESTAMP () >= s.StartDate AND CURRENT_TIMESTAMP () <= s.EndDate))) " +
                        "ORDER BY t.RequestTime DESC) as a, mpi_patient p where a.mpiId=p.MPIID";
                Query q = ss.createSQLQuery(hql);
                q.setParameter("doctorId", doctorId);
                if (null != pagingInfo) {
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                List<Object[]> olist = q.list();
                List<Patient> patientList = new ArrayList<>(0);
                Patient patient;
                for (int i = 0; i < olist.size(); i++) {
                    Object[] obj = olist.get(i);
                    if (null != obj) {
                        patient = new Patient();
                        if (null != obj[0]) {
                            patient.setMpiId(ObjectUtils.defaultIfNull((String) obj[0], null));
                            patient.setPatientName(ObjectUtils.defaultIfNull((String) obj[1], null));
                            patient.setPatientSex(ObjectUtils.defaultIfNull((String) obj[2], null));
                            patient.setBirthday(ObjectUtils.defaultIfNull((Date) obj[3], null));
                            patient.setPhoto(ObjectUtils.defaultIfNull((Integer) obj[4], null));
                            patient.setPatientType(ObjectUtils.defaultIfNull((String) obj[5], null));
                            patientList.add(patient);
                        }
                    }
                }
                setResult(patientList);
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 添加患者-已关注医生的患者(医生未关注患者)
     *
     * @param doctorId
     * @param pagingInfo
     * @return
     */
    public List<Patient> findConceredDoctorPatient(final Integer doctorId, final PagingInfo pagingInfo) {
        AbstractHibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "SELECT new eh.entity.mpi.Patient(p.mpiId,p.patientName,p.patientSex,p.birthday,p.photo,p.patientType,t.relationType) FROM RelationDoctor t, Patient p " +
                        "WHERE t.doctorId = :doctorId AND t.mpiId=p.mpiId AND p.mpiId IS NOT NULL AND t.relationType = 1" +
                        "AND NOT EXISTS (SELECT s.mpiId FROM RelationDoctor s WHERE t.mpiId = s.mpiId AND t.doctorId = s.doctorId AND (s.relationType=2 OR (s.relationType = 0 AND CURRENT_TIMESTAMP () >= s.startDate AND CURRENT_TIMESTAMP () <= s.endDate))) " +
                        "ORDER BY t.relationDate DESC";

                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                if (null != pagingInfo) {
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * zhongzx
     * 新定义一个patientBean 接收必要信息
     * @param doctorIdList  入参是一个doctorIdList 因为除了查询医生本人的未关注患者 还要查询他作为管理员或者队长的团队的未关注患者
     * @param pagingInfo
     * @return
     */
    public List<PatientConcernBean> findConcernPatientForDoctor(final List<Integer> doctorIdList, final PagingInfo pagingInfo) {
        AbstractHibernateStatelessResultAction<List<PatientConcernBean>> action = new AbstractHibernateStatelessResultAction<List<PatientConcernBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String("SELECT new eh.entity.mpi.PatientConcernBean(p.mpiId,p.patientName,p.patientSex,p.birthday,p.photo,p.patientType,t.relationType,t.obtainType,t.doctorId,t.relationDate) FROM RelationDoctor t, Patient p " +
                        "WHERE t.doctorId in (:doctorIdList) AND t.mpiId=p.mpiId AND p.mpiId IS NOT NULL AND t.relationType = 1" +
                        "AND NOT EXISTS (SELECT s.mpiId FROM RelationDoctor s WHERE t.mpiId = s.mpiId AND s.doctorId in (:doctorIdList) AND (s.relationType=2 OR (s.relationType = 0 AND CURRENT_TIMESTAMP () >= s.startDate AND CURRENT_TIMESTAMP () <= s.endDate))) " +
                        "ORDER BY t.relationDate DESC");

                Query q = ss.createQuery(hql);
                q.setParameterList("doctorIdList", doctorIdList);
                if (null != pagingInfo) {
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 患者管理-未添加标签的患者总数
     *
     * @param doctorId
     * @return
     */
    public Long findNotAddLabelPatientCount(final Integer doctorId) {
        AbstractHibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query q = ss.createQuery(getNotAddLabelPatientHql(null, true, null));
                q.setParameter("doctorId", doctorId);
                setResult((Long) q.uniqueResult());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 患者管理-未添加标签的患者
     *
     * @param doctorId
     * @param pagingInfo
     * @return
     */
    public List<Patient> findNotAddLabelPatient(final Integer doctorId, final PagingInfo pagingInfo, final String searchKey) {
        AbstractHibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query q = ss.createQuery(getNotAddLabelPatientHql(searchKey, false, null));
                q.setParameter("doctorId", doctorId);
                if (StringUtils.isNotEmpty(searchKey)) {
                    q.setParameter("patientName", "%" + searchKey + "%");
                }
                if (null != pagingInfo) {
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询未添加患者的mpiId集合
     *
     * @param doctorId
     * @param pagingInfo
     * @param attr
     * @return
     */
    public List<String> findNotAddLabelPatientMpiId(final Integer doctorId, final PagingInfo pagingInfo, final String attr) {
        AbstractHibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query q = ss.createQuery(getNotAddLabelPatientHql(null, false, attr));
                q.setParameter("doctorId", doctorId);
                if (null != pagingInfo) {
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 未添加标签患者hql
     *
     * @param searchKey
     * @param total
     * @param attr      指定查询某个属性
     * @return
     */
    private String getNotAddLabelPatientHql(String searchKey, boolean total, String attr) {
        StringBuilder hql = new StringBuilder();
        if (total) {
            hql.append("SELECT count(1) ");
        } else {
            if (StringUtils.isNotEmpty(attr)) {
                hql.append("SELECT p." + attr);
            } else {
                hql.append("SELECT new eh.entity.mpi.Patient(p.mpiId,p.patientName,p.patientSex,p.birthday,p.photo,p.patientType,t.relationType,t.endDate) ");
            }
        }
        hql.append(" FROM RelationDoctor t, Patient p " + "WHERE t.doctorId = :doctorId AND t.mpiId=p.mpiId AND p.mpiId IS NOT NULL ");
        if (StringUtils.isNotEmpty(searchKey)) {
            hql.append(" AND p.patientName like :patientName ");
        }
        hql.append("AND (t.relationType=2 OR (t.relationType = 0 AND CURRENT_TIMESTAMP () >= t.startDate AND CURRENT_TIMESTAMP () <= t.endDate)) " +
                "AND NOT EXISTS (SELECT s.relationPatientId FROM RelationLabel s WHERE t.relationDoctorId = s.relationPatientId) ");

        if (!total && StringUtils.isEmpty(attr)) {
            hql.append("ORDER BY t.relationDate DESC");
        }
        return hql.toString();
    }

    /**
     * 近期扫码关注医生的患者列表(同时医生也要对该患者进行关注)
     *
     * @param doctorId
     * @param pagingInfo
     * @return
     */
    public List<Patient> findScanCodePatient(final Integer doctorId, final PagingInfo pagingInfo) {
        AbstractHibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "SELECT new eh.entity.mpi.Patient(p.mpiId,p.patientName,p.patientSex,p.birthday,p.photo,p.patientType,b.relationType,b.relationDoctorId) FROM RelationDoctor a, RelationDoctor b, Patient p " +
                        "WHERE a.doctorId = :doctorId AND a.mpiId=p.mpiId AND p.mpiId IS NOT NULL AND a.mpiId=b.mpiId AND a.doctorId=b.doctorId AND a.relationType=1 AND a.obtainType=2 " +
                        "AND (b.relationType=2 OR (b.relationType = 0 AND CURRENT_TIMESTAMP () >= b.startDate AND CURRENT_TIMESTAMP () <= b.endDate)) " +
                        "AND a.relationDate BETWEEN :relStartDate AND :relEndDate ORDER BY a.relationDate DESC";

                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                q.setParameter("relStartDate", DateConversion.getFormatDate(DateConversion.getDaysAgo(6), DateConversion.YYYY_MM_DD));
                q.setParameter("relEndDate", DateConversion.getFormatDate(DateConversion.getDateAftXDays(new Date(), 1), DateConversion.YYYY_MM_DD));
                if (null != pagingInfo) {
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 全部患者列表下除最近一周扫描二维码关注我的患者之外的患者(同时医生也要对该患者进行关注)
     *
     * @param doctorId
     * @param pagingInfo
     * @return
     */
    public List<Patient> findAllExceptScanCodePatient(final Integer doctorId, final PagingInfo pagingInfo) {
        AbstractHibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "SELECT new eh.entity.mpi.Patient(p.mpiId,p.patientName,p.patientSex,p.birthday,p.photo,p.patientType,a.relationType,a.relationDoctorId) FROM RelationDoctor a, Patient p " +
                        "WHERE a.doctorId = :doctorId AND a.mpiId=p.mpiId AND p.mpiId IS NOT NULL  " +
                        "AND (a.relationType=2 OR (a.relationType = 0 AND CURRENT_TIMESTAMP () >= a.startDate AND CURRENT_TIMESTAMP () <= a.endDate)) " +
                        "AND a.relationDoctorId NOT IN (" +
                        "SELECT t.relationDoctorId from RelationDoctor t, RelationDoctor s WHERE s.doctorId = :doctorId AND t.mpiId=s.mpiId AND t.doctorId=s.doctorId AND t.relationDoctorId <> s.relationDoctorId " +
                        "AND s.relationType = 1 AND s.obtainType = 2 AND s.relationDate BETWEEN :relStartDate AND :relEndDate) " +
                        "ORDER BY a.relationDate DESC";

                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                q.setParameter("relStartDate", DateConversion.getFormatDate(DateConversion.getDaysAgo(6), DateConversion.YYYY_MM_DD));
                q.setParameter("relEndDate", DateConversion.getFormatDate(DateConversion.getDateAftXDays(new Date(), 1), DateConversion.YYYY_MM_DD));
                if (null != pagingInfo) {
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 在自定义标签内搜索患者
     * @param labelName
     * @param doctorId
     * @param patientName
     * @param pagingInfo
     * @return
     */
    public List<Patient> findLabelPatient(final String labelName, final Integer doctorId, final String patientName, final PagingInfo pagingInfo) {
        AbstractHibernateStatelessResultAction<List<Patient>> action = new AbstractHibernateStatelessResultAction<List<Patient>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("SELECT new eh.entity.mpi.Patient(p.mpiId,p.patientName,p.patientSex,p.birthday,p.photo,p.patientType,t.relationType,t.endDate,t.relationDoctorId) ")
                    .append("from RelationDoctor t, RelationLabel lab, Patient p ")
                    .append("WHERE t.relationDoctorId=lab.relationPatientId and t.mpiId=p.mpiId AND p.mpiId IS NOT NULL ")
                    .append("and lab.labelName=:labelName ");

                hql.append("AND (t.relationType=2 OR (t.relationType = 0 AND CURRENT_TIMESTAMP () >= t.startDate AND CURRENT_TIMESTAMP () <= t.endDate)) ");
                if (StringUtils.isNotEmpty(patientName)) {
                    hql.append(" AND p.patientName like :patientName ");
                }
                if (null != doctorId) {
                    hql.append(" AND t.doctorId=:doctorId ");
                }

                Query q = ss.createQuery(hql.toString());
                if (StringUtils.isNotEmpty(labelName)) {
                    q.setParameter("labelName", labelName);
                }
                if (StringUtils.isNotEmpty(patientName)) {
                    q.setParameter("patientName", "%"+patientName+"%");
                }
                if (null != doctorId) {
                    q.setParameter("doctorId", doctorId);
                }

                if (null != pagingInfo) {
                    q.setFirstResult(pagingInfo.getCurrentIndex());
                    q.setMaxResults(pagingInfo.getLimit());
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod
    public abstract void updatePatientTypeByMpiId(String patientType, String mpiId);

    @DAOMethod(sql = "select homeArea from Patient where mpiId=:mpiId")
    public abstract String getHomeAreaByMpiId(@DAOParam("mpiId") String mpiId);

    @DAOMethod(limit = 0, sql = " from Patient where status =1 and ( idcard =:idcard or loginId =:loginId)")
    public abstract List<Patient> findUsefulPatientsByIdCardOrLoginId(@DAOParam("idcard") String idCard,@DAOParam("loginId") String loginId);

    @Override
    public Patient update(Patient p) throws DAOException {
        //为防止前端用户数据更新出错，由后台统一处理
        //11372 【预约挂号-医院列表】机构位置和城市不匹配
        p.setFullHomeArea(getFullHomeArea(p.getHomeArea()));
        p.setLastModify(new Date());
        p=super.update(p);
        if(p.getLoginId()!=null){
            try {
                ControllerUtil.reloadUserByUid(p.getLoginId());
            } catch (ControllerException e) {
                logger.error("患者缓存刷新失败"+e.getMessage());
            }
        }
        return p;
    }

    @Override
    public Patient save(Patient p) throws DAOException {
        if (StringUtils.isEmpty(p.getFullHomeArea())) {
            p.setFullHomeArea(getFullHomeArea(p.getHomeArea()));
        }
        Boolean guardianFlag = p.getGuardianFlag() == null ? false : p.getGuardianFlag();
        p.setGuardianFlag(guardianFlag);
        p.setStatus(p.getStatus()==null?PatientConstant.PATIENT_STATUS_NORMAL:p.getStatus());
        p.setHealthProfileFlag(p.getHealthProfileFlag()==null?false:p.getHealthProfileFlag());
        p.setCreateDate(new Date());
        p.setLastModify(new Date());
        p.setPatientType(p.getPatientType()==null?"1":p.getPatientType());//默认自费
        return super.save(p);
    }

    @DAOMethod(sql = "select patientName from Patient where mpiId=:mpiId")
    public abstract String getPatientNameByMpiId(@DAOParam("mpiId") String mpiId);

    /**
     * zhongzx
     * 根据手机号 和姓名获取患者集合
     * @param mobile
     * @param name
     * @return
     */
    @DAOMethod(sql = "from Patient where mobile=:mobile and patientName=:name and status = 1")
    public abstract List<Patient> findByMobileAndName(@DAOParam("mobile") String mobile, @DAOParam("name") String name);
}
