package eh.mpi.service;

import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.schema.exception.ValidateException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.OrganBussTypeConstant;
import eh.base.constant.OrganConfigConstant;
import eh.base.constant.SystemConstant;
import eh.base.dao.OrganConfigDAO;
import eh.base.dao.OrganBusAdvertisementDAO;
import eh.base.dao.OrganConfigDAO;
import eh.base.user.UserSevice;
import eh.base.user.WXUserService;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.OrganConfig;
import eh.entity.base.OrganBusAdvertisement;
import eh.entity.base.OrganConfig;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.mpi.FamilyMember;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.constant.FamilyMemberConstant;
import eh.mpi.constant.PatientConstant;
import eh.mpi.dao.FamilyMemberDAO;
import eh.mpi.dao.HealthCardDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.service.healthcard.QueryHealthCardService;
import eh.util.ChinaIDNumberUtil;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.*;

import static ctd.persistence.exception.DAOException.VALIDATE_FALIED;

/**
 * Created by luf on 2016/6/3.
 */

public class FamilyMemberService {

    private Logger log = Logger.getLogger(FamilyMemberService.class);

    /**
     * 更新家庭成员服务（同时更新病人表信息）
     *
     * @param mpiId    主索引
     * @param patient  家庭成员详细信息
     * @param relation 家庭成员的关系
     * @author luf
     * @date 2016-6-3
     */
    @RpcService
    public void updateFamilyMemberAndPatient(String mpiId, Patient patient, String relation) {

        HealthCardDAO cardDAO = DAOFactory.getDAO(HealthCardDAO.class);
        List<HealthCard> cards = patient.getHealthCards();
        for (HealthCard card : cards) {
                if(!StringUtils.isEmpty(card.getCardId()) && !StringUtils.isEmpty(card.getInitialCardID())){
                    HealthCard orgCard = cardDAO.getByCardOrganAndCardId(
                        card.getCardOrgan(), card.getCardId().toUpperCase(), card.getCardId());

                if (orgCard != null && !StringUtils.isEmpty(orgCard.getMpiId())) {
                    if (StringUtils.isEmpty(patient.getMpiId()) || !orgCard.getMpiId().equals(patient.getMpiId())) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "该医保卡号已被使用。如有问题，请联系纳里客服：\n" + ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL,SystemConstant.CUSTOMER_TEL));
                    }
                }
            }
        }

        addFamilyMemberAndPatient(mpiId, patient, relation);
    }

    /**
     * 判断是否有家庭成员关系
     *
     * @param mpiId     本人mpi
     * @param memberMpi 家庭成员mpi
     * @return
     */
    @RpcService
    public Boolean isFamily(String mpiId, String memberMpi) {
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
        Boolean flag = true;
        FamilyMember member = familyMemberDAO.getByMpiIdAndMemberMpi(mpiId, memberMpi);
        if (member == null) {
            flag = false;
        }

        return flag;
    }

    /**
     * 家庭成员增加服务（同时保存病人表信息）,然后判断判断是否注册过，如果没有注册则注册该家庭成员.
     * 同样儿童监护人判断是否注册,如果没有注册则注册该监护人
     *
     * @param mpiId    病人id
     * @param patient  家庭成员详细信息
     * @param relation 家庭成员的关系
     * @author cuill
     * @date   2017/3/22  addFamilyMemberAndPatient
     */
    @RpcService
    public Patient addFamilyMemberAndPatient(final String mpiId,
                                             final Patient patient, final String relation) {

        log.info("就诊人添加：mpiId=" + mpiId + ";patient="
                + JSONUtils.toString(patient) + "; relation=" + relation);
        if (StringUtils.isEmpty(mpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }
        validatePatient(patient);

        if (!StringUtils.isEmpty(patient.getLoginId())) {
            patient.setLoginId(null);
        }
        //2016-12-23 15:46:26 zhangx wx2.7 儿童患者需求：儿童患者时，前端录入的身份证为监护人的身份证
        //判断时需要根据时间情况进行判断,guardianFlag为true表示儿童,false为成人
        Boolean guardianFlag = patient.getGuardianFlag() == null ? Boolean.valueOf(false) : patient.getGuardianFlag();
        final PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        final PatientService patientService = AppContextHolder.getBean("eh.patientService", PatientService.class);

        //如果不是监护人的情况下
        if (!guardianFlag) {
            Patient familyPatient = patientDAO.getByMpiId(mpiId);
            String familyPatientIdCard = familyPatient.getIdcard();
            //在不是监护人的情况下,不能添加自己作为家庭成员
            if (familyPatientIdCard.equals(patient.getIdcard())) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "不能添加自己作为家庭成员");
            }
        }
        // 保存病人表信息
        HibernateStatelessResultAction<List<HashMap<String, Object>>> action = new AbstractHibernateStatelessResultAction<List<HashMap<String, Object>>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                //存放的map中 patient:患者信息 role:分为familyMember(就诊人)和guardian(监护人) registerFlag:是否注册该患者信息
                List<HashMap<String, Object>> patientMapList = new ArrayList();
                //对监护人信息进行增加或者注册
                if (!ObjectUtils.isEmpty(patient.getGuardianFlag()) && patient.getGuardianFlag()) {
                    HashMap<String, Object> resultGuardianMap = patientService.saveForGuardianPatient(patient);
                    if (!ObjectUtils.isEmpty(resultGuardianMap)) {
                        resultGuardianMap.put("role", "guardian");
                        patientMapList.add(resultGuardianMap);
                    }
                }
                //对儿童或者就诊人信息进行增加或者注册
                HashMap<String, Object> resultMap = patientService.saveOrUpdateFamilyMember(patient);
                resultMap.put("role", "familyMember");
                Patient resultPatient = (Patient) resultMap.get("patient");
                patientMapList.add(resultMap);
                // 保存家庭成员表信息,在mpi_familyMember插入数据
                addFamilyMember(mpiId, resultPatient.getMpiId(), relation);
                setResult(patientMapList);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        //就诊人为成人或者儿童的监护人在mpi_patient表里面增加完信息以后注册该用户
        Patient returnPatient = createUserAndReturnPatient(action.getResult());
        UserSevice.updateUserCache(returnPatient.getLoginId(), SystemConstant.ROLES_PATIENT, "patient", returnPatient);
        return createUserAndReturnPatient(action.getResult());
    }


    /**
     * 病人校验
     * 身份证日期大于今天
     * @param patient
     */
    private static void validatePatient(Patient patient){

        if (patient != null && StringUtils.isNotEmpty(patient.getIdcard())) {
            String patientBirthDay = patient.getIdcard().substring(6, 14);
            if(DateConversion.parseDate(patientBirthDay,"yyyyMMdd").after(new Date())){
                throw new DAOException(VALIDATE_FALIED,"身份证无效");
            }
        }
    }

    /**
     *  wx2.9版本, 就诊人为成人或者儿童的监护人在mpi_patient表里面增加完信息以后注册该用户
     * @param patientMapList 获取的患者信息集合
     * @return patient 患者信息
     * @author cuill
     * @date 2017/3/23
     */
    private Patient createUserAndReturnPatient(List<HashMap<String, Object>> patientMapList) {
        Patient returnPatient = null;
        for (int i = 0; i < patientMapList.size(); i++) {
            HashMap<String, Object> patientMap = patientMapList.get(i);
            Patient patient = (Patient) patientMap.get("patient");
            Boolean registerFlag = (Boolean) patientMap.get("registerFlag");
            String role = (String) patientMap.get("role");
            //刷新服务器缓存
            if (StringUtils.isEmpty(patient.getLoginId())){
                 UserSevice.updateUserCache(patient.getLoginId(), SystemConstant.ROLES_PATIENT, "patient", patient);
            }
            if (!patient.getGuardianFlag() && registerFlag) {
                WXUserService wxUserService = AppContextHolder.getBean("eh.wxUserService", WXUserService.class);
                wxUserService.createWXUserForFamilyMember(patient);
            }
            //返回到是就诊人对象,不返回儿童监护人对象
            if (role.equals("familyMember")) {
                returnPatient = patient;
            }
        }
        return returnPatient;
    }

    /**
     * 增加家庭成员,在mpi_familyMember表中增加两个患者关系
     * @param mpiId 患者的主键
     * @param memberMpiId  就诊人的主键
     * @param relation 关系主键
     * @author cuill
     * @date   2017/3/22
     */
    public void addFamilyMember(String mpiId, String memberMpiId, String relation) {

        FamilyMember familyMember = new FamilyMember();
        familyMember.setMpiid(mpiId);
        // 使用保存后的patient1对象的主键
        familyMember.setMemberMpi(memberMpiId);
        familyMember.setRelation(relation);
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
        if (StringUtils.isEmpty(familyMember.getMpiid())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }
        if (StringUtils.isEmpty(familyMember.getMemberMpi())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "memberMpi is required");
        }
        FamilyMember familyMember2 = familyMemberDAO.getByMpiIdAndMemberMpi(
                familyMember.getMpiid(), familyMember.getMemberMpi());
        if (familyMember2 == null) {
            familyMember.setCreateDt(new Date());
            familyMember.setLastModify(new Date());
            familyMember.setMemeberStatus(FamilyMemberConstant.MEMBER_STATUS_NO_DEL);
            familyMemberDAO.save(familyMember);
        } else {
            familyMember2.setMemeberStatus(FamilyMemberConstant.MEMBER_STATUS_NO_DEL);
            familyMember2.setRelation(familyMember.getRelation());
            familyMember2.setLastModify(new Date());
            familyMemberDAO.update(familyMember2);
        }
    }

    /**
     * 获取家庭成员列表包含自己
     * 只含有mpiid，patientName
     * [微信端]-完善健康信息页面使用
     * @param mpiId
     * @return
     */
    @RpcService
    public List<Patient> findMemberListWithSelf(String mpiId) {
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);

        List<FamilyMember> members = getFamilyMembers(mpiId);

        List<Patient> patList = new ArrayList<>();
        for (FamilyMember familyMember : members) {
            String memberMpiId=familyMember.getMemberMpi();
            Patient pat=patientDao.get(memberMpiId);
            if(pat!=null){
                Patient returnPat=new Patient();
                returnPat.setMpiId(pat.getMpiId());
                returnPat.setPatientName(pat.getPatientName());
                patList.add(returnPat);
            }
        }

        return patList;
    }

    /**
     * 家庭成员列表(包括自己)
     *
     * @author luf
     * @param mpiId
     *            当前患者主索引
     * @return List<Patient>
     */
    @RpcService
    public List<HashMap<String, Object>> familyMemberList(String mpiId) {
        PatientDAO dao = DAOFactory.getDAO(PatientDAO.class);
        List<HashMap<String, Object>> list = new ArrayList();

        List<FamilyMember> members = getFamilyMembers(mpiId);
        for (FamilyMember member : members) {
            HashMap<String, Object> map = new HashMap<>();
            Patient p = dao.getByMpiId(member.getMemberMpi());
            if(StringUtils.isEmpty(p.getFullHomeArea())){
                p.setFullHomeArea(dao.getFullHomeArea(p.getHomeArea()));
            }
            int patientUserType=p.getPatientUserType()==null?
                    PatientConstant.PATIENT_USERTYPE_ADULT:p.getPatientUserType().intValue();
            p.setPatientUserType(patientUserType);

            map.put("patient", p);
            FamilyMember m = new FamilyMember();
            m.setRelation(member.getRelation());
            map.put("relation", m);
            list.add(map);
        }
        return list;
    }

    /**
     * 根据机构设置，组装数据，前端在就诊人列表是否置灰显示，以下情况需要置灰显示
     *
     * @param mpiId 患者用户的mpiId
     * @param organId 机构ID 预约业务则入参为预约的目标机构，个人中心的入参为公众号对应的organId
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> familyMemberListWithOrgan(String mpiId,Integer organId){
        OrganConfigDAO organConfigDAO=DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig organConfig=organConfigDAO.get(organId);
        if(organConfig==null){
            organConfig=new OrganConfig();
        }
        int childWithoutIdSupport=organConfig.getChildWithoutIdSupport()==null?
                OrganConfigConstant.CHILD_WITHOUT_ID_SUPPORT_NO:organConfig.getChildWithoutIdSupport().intValue();
        int childWithIdSupport=organConfig.getChildWithIdSupport()==null?
                OrganConfigConstant.CHILD_WITH_ID_SUPPORT_YES:organConfig.getChildWithIdSupport().intValue();
        int childAge=organConfig.getChildrenAge()==null?
                OrganConfigConstant.CHILD_AGE_DEFAULT:organConfig.getChildrenAge().intValue();

        //获取提示文案
        Map<String,String>  childAgePromptMap=getChildAgePrompt(organId,childAge);
        //类型为成人,实际年龄小于配置年龄：类型不符,请用儿童类型添加
        String childAgePromptAdult= childAgePromptMap.get("childAgePromptAdult");
        //类型为儿童有身份证+无身份证,实际年龄大于配置年龄：类型不符,请用成人类型添加
        String childAgePromptChild= childAgePromptMap.get("childAgePromptChild");
        //就诊人类型不支持
        String childAgePrompt= childAgePromptMap.get("childAgePrompt");

        String key_prompt="prompt";

        List<HashMap<String, Object>> memberList=familyMemberList(mpiId);
        for (HashMap<String, Object> map:memberList ) {
            Patient pat=(Patient)map.get("patient");

            //默认不置灰
            pat.setAshFlag(false);

            //就诊人类型
            int patientUserType=pat.getPatientUserType()==null?
                    PatientConstant.PATIENT_USERTYPE_ADULT:pat.getPatientUserType().intValue();
            pat.setPatientUserType(patientUserType);

            //机构设置不支持无身份证儿童就诊，但是就诊人类型为无身份证儿童,前端置灰显示,点击提示文案：暂不支持该类就诊人
            if(childWithoutIdSupport == OrganConfigConstant.CHILD_WITHOUT_ID_SUPPORT_NO
                    && patientUserType==PatientConstant.PATIENT_USERTYPE_CHILD_NO_IDCARD){
                pat.setAshFlag(true);
                map.put(key_prompt,childAgePrompt);
                continue;
            }

            //机构设置不支持有身份证儿童就诊，但是就诊人类型为有身份证儿童,前端置灰显示,点击无提示文案：暂不支持该类就诊人
            if(childWithIdSupport == OrganConfigConstant.CHILD_WITH_ID_SUPPORT_NO
                    && patientUserType==PatientConstant.PATIENT_USERTYPE_CHILD_HAS_IDCARD){
                pat.setAshFlag(true);
                map.put(key_prompt,childAgePrompt);
                continue;
            }

            if(childAge==0){
                pat.setAshFlag(false);
                continue;
            }

            //计算实际周岁年龄(误差一天)
            Date birthDay=pat.getBirthday()==null?new Date():pat.getBirthday();
            int actualAge=DateConversion.getAge(birthDay);


            //已设置年龄判断，实际年龄大于限定年龄，但类别是儿童
            if(childAge>0 && actualAge>=childAge &&
                    (PatientConstant.PATIENT_USERTYPE_CHILD_HAS_IDCARD==patientUserType
                            ||PatientConstant.PATIENT_USERTYPE_CHILD_NO_IDCARD==patientUserType) ){
                pat.setAshFlag(true);
                map.put(key_prompt,childAgePromptChild);
                continue;
            }

            //已设置年龄判断，实际年龄小于限定年龄，但类别是成人
            if(childAge>0 && actualAge<childAge && PatientConstant.PATIENT_USERTYPE_ADULT==patientUserType){
                pat.setAshFlag(true);
                map.put(key_prompt,childAgePromptAdult);
            }
        }

        return memberList;
    }


    /**
     * 添加就诊人改造v1.0：
     *添加就诊人关系：，
     A:如果关系里已存在（姓名和身份证相同）且状态为未删除，则提示不能再次添加
     B:如果关系里已存在（姓名和身份证相同）且状态为已删除，则还原关系，同时更新就诊人基础信息，若添加的是自己，则另外要追加刷新缓存
     C:如果关系里存在身份证相同，姓名不相同，状态为未删除，则新增关系，新增就诊人
     D:如果关系里存在身份证不同，姓名相同，状态为未删除，则新增关系，新增就诊人
     E:如果关系里存在身份证不同，姓名不相同（关系不存在，基础信息可能存在，可能不存在），状态为未删除，则新增关系，新增就诊人

     新增就诊人：
     若身份证不存在，则按原来的逻辑进行新增，
     若身份证存在，则将A的身份证保留在idcard2，rawIdcard，添加就诊人关系信息，此时不注册用户

     2017-7-8 16:46:08 zhangx
     添加就诊人改造v2.0
     在1.0基础上增加配置是否隔离，若配置不隔离，则逻辑按v1.0进行
     如果配置隔离，则逻辑如下：

     添加就诊人改造v2.0-有身份的就诊人=有身份证的成人+有身份证的儿童，无身份证的儿童按原来的逻辑：
     添加就诊人关系-不隔离关系：
     A:如果关系里已存在（姓名和身份证相同）且状态为未删除，则提示不能再次添加
     B:如果关系里已存在（姓名和身份证相同）且状态为已删除，则还原关系，同时更新就诊人基础信息，若添加的是自己，则另外要追加刷新缓存
     C:如果关系里存在身份证相同，姓名不相同，状态为未删除，则新增关系，新增就诊人
     D:如果关系里存在身份证不同，姓名相同，状态为未删除，则新增关系，新增就诊人
     E:如果关系里存在身份证不同，姓名不相同（关系不存在，基础信息可能存在，可能不存在），状态为未删除，则新增关系，新增就诊人

     新增就诊人：
     若身份证不存在，则按原来的逻辑进行新增，
     若身份证存在，则将A的身份证保留在idcard2，rawIdcard，添加就诊人关系信息，此时不注册用户
     * @param userMpiId 患者用户mpiid
     * @param patient 就诊人基础信息
     * @return
     */
    @RpcService
    public Patient addFamilyMemberInfo(String userMpiId,
                                              Patient patient) {
        log.info("就诊人添加addFamilyMemberInfo：mpiId=" + userMpiId + ";patient="
                + JSONUtils.toString(patient));
        PatientService patientService = AppContextHolder.getBean("eh.patientService",
                PatientService.class);
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

        if (StringUtils.isEmpty(userMpiId)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mpiId is required");
        }

        if(patientDAO.get(userMpiId)==null){
            throw new DAOException(ErrorCode.SERVICE_ERROR,
                    "当前用户不存在，无法添加就诊人");
        }



        validAddFamilyMemberInfoData(patient);

        String patientName=patient.getPatientName();
        String idCard=patient.getIdcard();

        final String idCard18;
        try {
            idCard18 = ChinaIDNumberUtil.convert15To18(idCard);
        } catch (ValidateException e) {
            throw new DAOException(609, "身份证不正确");
        }


        int patientUserType=patient.getPatientUserType().intValue();


        //2017-6-30 18:46:49 上海六院就诊人第一次优化，无身份儿童按原来数据进行录入
        if(PatientConstant.PATIENT_USERTYPE_CHILD_NO_IDCARD==patientUserType){
            return addFamilyMemberAndPatient(userMpiId,patient,FamilyMemberConstant.MEMBER_RELATION_NO);
        }

        Map<String,Object> familyConfig=getFamilyConfig();
        Integer organId=(Integer)familyConfig.get("organId");
        Boolean isolationFlag=(Boolean)familyConfig.get("isolationFlag");

        List<FamilyMember> memberList=new ArrayList<>();
        //要隔离，按身份证，姓名，机构id
        if(isolationFlag){
            memberList= familyMemberDAO.findOrganMemberByMpiId(userMpiId,organId);
        }else{
            //不隔离,按姓名和身份证对比
            memberList=familyMemberDAO.findAllMemberByMpiId(userMpiId);
        }

        //for循环校验各种情况
        for(FamilyMember member:memberList){
            int memberStatus=member.getMemeberStatus()==null?FamilyMemberConstant.MEMBER_STATUS_NO_DEL:
                    member.getMemeberStatus().intValue();

            //家庭成员基础信息
            Patient pat=patientDAO.get(member.getMemberMpi());
            String dbPatName=pat.getPatientName();
            String dbPatIdCard=pat.getIdcard();

            log.info("患者["+userMpiId+"]添加就诊人时判断数据:dbPatName="+dbPatName+"; patientName="+patientName+"; " +
                    "dbPatIdCard="+dbPatIdCard+"; idCard18="+idCard18+"; memberStatus="+memberStatus);
            //如果关系里已存在（姓名和身份证相同）且状态为未删除，则提示不能再次添加
            if(patientName.equals(dbPatName) && idCard18.equals(dbPatIdCard)
                    && memberStatus==FamilyMemberConstant.MEMBER_STATUS_NO_DEL){
                throw new DAOException(ErrorCode.SERVICE_ERROR,"该就诊人信息已存在，请不要重复添加");
            }

            //如果关系里已存在（姓名和身份证相同）且状态为已删除，
            // 则还原关系，同时更新就诊人基础信息，若添加的是自己，则另外要追加刷新缓存
            if(patientName.equals(dbPatName) && idCard18.equals(dbPatIdCard)
                    && memberStatus==FamilyMemberConstant.MEMBER_STATUS_HAS_DEL){
                patientService.setUnUpdatePatient(patient);
                BeanUtils.map(patient,pat);
                return updateMemberInfo(member,pat);
            }
        }

        //除A，B情况外，其他情况都新增就诊人关系，新增就诊人信息
        return saveMemberInfo(userMpiId,patient,idCard18);
    }

    /**
     * 根据医院判断是否符合年龄标准
     * @param birthDay
     * @param patientUserType
     * @param organ
     * @return
     */
    @RpcService
    public Boolean validMemberAgeByOrgan(Date birthDay,Integer patientUserType,Integer organ){
        log.info("validMemberAgeByOrgan: birthDay"+JSONUtils.toString(birthDay)+"; patientUserType"+patientUserType+"; organ="+organ);
        if(organ==null){
            return true;
        }

        OrganConfigDAO configDAO=DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig config=configDAO.getByOrganId(organ);
        if(config==null){
            config=new OrganConfig();
        }

        int childAge=config.getChildrenAge()==null?OrganConfigConstant.CHILD_AGE_DEFAULT:config.getChildrenAge().intValue();

        //获取提示文案
        Map<String,String>  childAgePromptMap=getChildAgePrompt(organ,childAge);


        return validMemberAge(birthDay,patientUserType,childAge,childAgePromptMap);
    }

    /**
     * 校验年龄是否符合规定
     * 根据每个医院的标准设置年龄，只要等于或者大于这个年龄，就算是成人，反之就是儿童。
     * @param birth
     */
    private Boolean validMemberAge(Date birthDay,int patientUserType,Integer childAge,
                                   Map<String,String>  childAgePromptMap){
        int actualAge=DateConversion.getAge(birthDay);
        String childAgePromptAdult= childAgePromptMap.get("childAgePromptAdult");
        String childAgePromptChild= childAgePromptMap.get("childAgePromptChild");

        //未设置年龄判断
        if(childAge==0){
            return true;
        }

        //已设置年龄判断，实际年龄大于限定年龄，但类别是儿童
        if(childAge>0 && actualAge>=childAge &&
                (PatientConstant.PATIENT_USERTYPE_CHILD_HAS_IDCARD==patientUserType
                        ||PatientConstant.PATIENT_USERTYPE_CHILD_NO_IDCARD==patientUserType) ){
           throw new DAOException(ErrorCode.SERVICE_ERROR,childAgePromptChild);
        }

        //已设置年龄判断，实际年龄小于限定年龄，但类别是成人
        if(childAge>0 && actualAge<childAge && PatientConstant.PATIENT_USERTYPE_ADULT==patientUserType){
            throw new DAOException(ErrorCode.SERVICE_ERROR,childAgePromptAdult);
        }
        return false;
    }

    /**
     * 获取年龄不符合医院设定标准，提示文案
     * @param organId 机构id
     * @return
     */
    private Map<String,String> getChildAgePrompt(Integer organId,Integer childAge){
        Map<String,String> promptMap=Maps.newHashMap();

        Map<String,Integer> paramMap=Maps.newHashMap();
        paramMap.put("childAge",childAge);

        //获取儿童年龄，成人就诊人类型文案
        OrganBusAdvertisementDAO dao = DAOFactory.getDAO(OrganBusAdvertisementDAO.class);
        OrganBusAdvertisement obaAdult = dao.getByOrganIdAndBusType(organId, OrganBussTypeConstant.ORGANBUSSTYPE_CHILDAGE_ADULT);
        if(obaAdult==null){
            obaAdult=new OrganBusAdvertisement();
        }
        String advertisementAdult=obaAdult.getAdvertisement();
        if(StringUtils.isEmpty(advertisementAdult)){
            String childAgePromptAdult=ParamUtils.getParam(ParameterConstant.KEY_CHILDAGE_PROMPT_ADULT,"类型不符，请用儿童类型添加");
            promptMap.put("childAgePromptAdult", LocalStringUtil.processTemplate(childAgePromptAdult,paramMap));
        }else {
            promptMap.put("childAgePromptAdult", advertisementAdult);
        }

        //获取成人年龄，儿童就诊人类型文案
        OrganBusAdvertisement obaChild = dao.getByOrganIdAndBusType(organId, OrganBussTypeConstant.ORGANBUSSTYPE_CHILDAGE_CHILD);
        if(obaChild==null){
            obaChild=new OrganBusAdvertisement();
        }

        String advertisementChild=obaChild.getAdvertisement();
        if(StringUtils.isEmpty(advertisementChild)){
            String childAgePromptChild=ParamUtils.getParam(ParameterConstant.KEY_CHILDAGE_PROMPT_CHILD,"类型不符，请用成人类型添加");
            promptMap.put("childAgePromptChild", LocalStringUtil.processTemplate(childAgePromptChild,paramMap));
        }else {
            promptMap.put("childAgePromptChild", advertisementChild);
        }

        String childAgePrompt=ParamUtils.getParam(ParameterConstant.KEY_CHILDAGE_PROMPT,"暂不支持该类就诊人");
        promptMap.put("childAgePrompt", childAgePrompt);

        return promptMap;
    }

    /**
     * 校验数据
     * @param mind
     */
    private void validAddFamilyMemberInfoData(Patient patient){
        if(patient==null){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patient is required");
        }

        if(StringUtils.isEmpty(patient.getIdcard())){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "Idcard is required");
        }

        if(StringUtils.isEmpty(patient.getPatientName())){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientName is required");
        }

        if(StringUtils.isEmpty(patient.getMobile())){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mobile is required");
        }

        if(patient.getPatientUserType()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientUserType is required");
        }

        //校验卡号是否已被使用
        QueryHealthCardService queryHealthCardService = AppContextHolder.getBean("eh.queryHealthCardService",
                QueryHealthCardService.class);
        List<HealthCard> cards = patient.getHealthCards();
        if(ValidateUtil.notBlankList(cards)) {
            for (HealthCard card : cards) {
                if (!StringUtils.isEmpty(card.getCardId()) && !StringUtils.isEmpty(card.getInitialCardID())) {
                    queryHealthCardService.isUsedCardByNewPatient(card.getCardOrgan(), card.getCardId());
                }
            }
        }
    }


    /**
     * 如果关系里已存在（姓名和身份证相同）且状态为已删除，
     * 则还原关系，同时更新就诊人基础信息，若添加的是自己，则另外要追加刷新缓存
     * @param memberId 就诊人关系id
     * @param updatePat 待更新的患者信息
     * @return
     */
    private Patient updateMemberInfo(FamilyMember member,Patient updatePat){
        FamilyMemberDAO memberDAO=DAOFactory.getDAO(FamilyMemberDAO.class);
        PatientDAO patientDAO=DAOFactory.getDAO(PatientDAO.class);
        HealthCardDAO cardDAO=DAOFactory.getDAO(HealthCardDAO.class);

        //还原就诊人关系
        member.setMemeberStatus(FamilyMemberConstant.MEMBER_STATUS_NO_DEL);
        memberDAO.update(member);

        //更新就诊人基础信息，有账户则更新数据
        Patient returnPat=patientDAO.update(updatePat);
        UserSevice.updateUserCache(returnPat.getLoginId(), SystemConstant.ROLES_PATIENT,"patient",returnPat);

        //更新,新增卡信息
        cardDAO.saveOrUpdateHealthCards(updatePat.getHealthCards(),returnPat.getMpiId());

        return returnPat;
    }

    /**
     * 情况C,D,E新增关系，新增就诊人信息
     * @return
     */
    private Patient saveMemberInfo(String userMpiId,Patient updatePat,String idCard18){
        FamilyMemberDAO memberDAO=DAOFactory.getDAO(FamilyMemberDAO.class);
        HealthCardDAO cardDAO=DAOFactory.getDAO(HealthCardDAO.class);
        PatientDAO patientDAO=DAOFactory.getDAO(PatientDAO.class);
        PatientService patientService = AppContextHolder.getBean("eh.patientService",
                PatientService.class);

        //新增就诊人信息
        Patient returnPat =patientService.saveVisitingPersonInfo(updatePat,idCard18);

        //添加就诊人时，手机号未注册，则进行注册用户
        String mobile=returnPat.getMobile();
        if(patientDAO.getByLoginId(mobile)==null && StringUtils.isEmpty(returnPat.getIdcard2())){
            WXUserService wxUserService = AppContextHolder.getBean("eh.wxUserService", WXUserService.class);
            wxUserService.createWXUserForFamilyMember(returnPat);

            returnPat.setLoginId(mobile);
            returnPat=patientDAO.update(returnPat);

            //添加自己为就诊人
            saveSelf(userMpiId);
        }

        //更新,新增卡信息
        cardDAO.saveOrUpdateHealthCards(updatePat.getHealthCards(),returnPat.getMpiId());

        //新增关系
        FamilyMember member=new FamilyMember();
        member.setMpiid(userMpiId);
        member.setMemberMpi(returnPat.getMpiId());
        if(StringUtils.equals(userMpiId,returnPat.getMpiId())){
            member.setRelation(FamilyMemberConstant.MEMBER_RELATION_SELF);
        }
        memberDAO.save(member);


        return returnPat;
    }



    /**
     * 更新患者信息
     * @param patient
     * @return
     */
    @RpcService
    public Patient updateFamilyMemberInfo(Patient patient){
        PatientService patientService = AppContextHolder.getBean("eh.patientService",
                PatientService.class);
        return patientService.updatePatientInfo(patient);
    }


    /**
     * 添加自己为就诊人
     * 创建患者用户时使用
     * 如果在新增就诊人页面，则使用接口addFamilyMemberInfo
     * @param userMpiId
     * @return
     */
    @RpcService
    public FamilyMember saveSelf(String userMpiId){
        log.info("添加自己为就诊人:"+userMpiId);
        FamilyMemberDAO memberDAO=DAOFactory.getDAO(FamilyMemberDAO.class);

        FamilyMember familyMember = memberDAO.getByMpiIdAndMemberMpi(
                userMpiId, userMpiId);
        FamilyMember returnMember=null;
        if (familyMember == null) {
            //新增关系
            FamilyMember member=new FamilyMember();
            member.setMpiid(userMpiId);
            member.setMemberMpi(userMpiId);
            member.setRelation(FamilyMemberConstant.MEMBER_RELATION_SELF);
            returnMember= memberDAO.save(member);
        }

        return returnMember;
    }

    /**
     * 判断公众号是否可删除自己
     * 公众号个性化配置中canDelSelf属性，配置：1可删除，0不可删除
     * @return
     */
    public Boolean canDelSelf(){
        Boolean candelFalg=false;
        Map<String,String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        String canDelSelf="";
        if(wxAppProperties != null){
            canDelSelf=StringUtils.isEmpty(wxAppProperties.get("canDelSelf"))?"":wxAppProperties.get("canDelSelf");
        }

        if(!StringUtils.isEmpty(canDelSelf) && FamilyMemberConstant.CAN_DEL_SELF.equals(canDelSelf.trim()) ){
            candelFalg=true;
        }

        return candelFalg;
    }

    /**
     * 获取是否隔离，以及配置的所属机构
     * @return
     */
    public Map<String,Object> getFamilyConfig(){
        Map<String,Object> returnMap=  Maps.newHashMap();

        Boolean isolationFlag=false;//是否隔离

        Map<String,String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        String organIdStr=String.valueOf(FamilyMemberConstant.ORGAN_NGARI);
        if(wxAppProperties != null){
            organIdStr=StringUtils.isEmpty(wxAppProperties.get("organId"))?
                    String.valueOf(FamilyMemberConstant.ORGAN_NGARI):wxAppProperties.get("organId");
        }

        OrganConfigDAO configDAO=DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig config=configDAO.getByOrganId(Integer.valueOf(organIdStr));
        if(config==null){
            config=new OrganConfig();
        }
        Integer isolation=config.getIsolationFlag()==null?FamilyMemberConstant.ISOLATION_NO:config.getIsolationFlag();

        if(FamilyMemberConstant.ISOLATION_YES == isolation.intValue() ){
            isolationFlag=true;
        }
        returnMap.put("isolationFlag",isolationFlag);
        returnMap.put("organId",Integer.valueOf(organIdStr));
        return returnMap;
    }

    /**
     * 根据是否隔离，获取就诊人信息列表
     * @param userMpiId
     * @return
     */
    private  List<FamilyMember> getFamilyMembers(String userMpiId) {
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);

        Map<String, Object> familyConfig = getFamilyConfig();
        Integer organId = (Integer) familyConfig.get("organId");
        Boolean isolationFlag = (Boolean) familyConfig.get("isolationFlag");

        List<FamilyMember> memberList = new ArrayList<>();
        //要隔离，按身份证，姓名，机构id
        if (isolationFlag) {
            memberList = familyMemberDAO.findOrganFamilyMemberStartWithSelf(userMpiId, organId);
        } else {
            //不隔离,按姓名和身份证对比
            memberList = familyMemberDAO.findFamilyMemberStartWithSelf(userMpiId);
        }

        return memberList;
    }
}
