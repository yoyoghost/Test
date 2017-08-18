package eh.base.dao;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.controller.exception.ControllerException;
import ctd.controller.notifier.NotifierCommands;
import ctd.controller.notifier.NotifierMessage;
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
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import eh.base.constant.ConditionOperator;
import eh.base.constant.DoctorTabConstant;
import eh.base.constant.ErrorCode;
import eh.base.service.BusActionLogService;
import eh.bus.constant.OrganConstant;
import eh.bus.constant.SearchConstant;
import eh.bus.dao.ClientSetDAO;
import eh.bus.dao.SearchContentDAO;
import eh.bus.service.OrganCloudPriceService;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.*;
import eh.entity.bus.SearchContent;
import eh.entity.bus.housekeeper.RecommendOrganBean;
import eh.op.service.OrganOPService;
import eh.pagemodel.DictionaryPageModel;
import eh.utils.DateConversion;
import eh.utils.ValidateUtil;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;

public abstract class OrganDAO extends HibernateSupportDelegateDAO<Organ>
        implements DBDictionaryItemLoader<Organ> {

    public static final Logger log = LoggerFactory.getLogger(OrganDAO.class);

    public OrganDAO() {
        super();
        this.setEntityName(Organ.class.getName());
        this.setKeyField("organId");
    }

    @RpcService
    @DAOMethod
    public abstract Organ getByOrganId(Integer organId);

    @RpcService
    @DAOMethod
    public abstract Organ getByManageUnit(String manageUnit);

    /**
     * 获取机构地址
     * 供app端使用
     *
     * @param organId
     * @return
     */
    @RpcService
    public Organ getOgranAddrArea(Integer organId) {
        Organ o = get(organId);
        Organ returnOrgan = new Organ();
        if (o == null) {
            return returnOrgan;
        }
        returnOrgan.setOrganId(organId);
        String addrArea = o.getAddrArea();
        returnOrgan.setAddrArea(addrArea.substring(0, 2));

        if (!StringUtils.isEmpty(addrArea)) {
            int length = addrArea.length();//地区编码长度
            switch (length / 2) {
                case 1:
                    returnOrgan.setProvince(addrArea.substring(0, 2));
                    returnOrgan.setCity(addrArea.substring(0, 2));
                    returnOrgan.setRegion(addrArea.substring(0, 2));
                    break;
                case 2:
                    returnOrgan.setProvince(addrArea.substring(0, 2));
                    returnOrgan.setCity(addrArea.substring(0, 4));
                    returnOrgan.setRegion(addrArea.substring(0, 4));
                    break;
                case 3:
                    returnOrgan.setProvince(addrArea.substring(0, 2));
                    returnOrgan.setCity(addrArea.substring(0, 4));
                    returnOrgan.setRegion(addrArea);
                    break;
            }
        }
        return returnOrgan;
    }

    /**
     * 注册医院服务
     *
     * @param organ
     * @return
     * @author LF
     */
    @RpcService
    public Organ registOrgan(Organ organ) {
        // 组织代码
        if (StringUtils.isEmpty(organ.getOrganizeCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganizeCode is required!");
        }
        // 机构名称
        if (StringUtils.isEmpty(organ.getName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "Name is required!");
        }
        // 机构简称
        if (StringUtils.isEmpty(organ.getShortName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "ShortName is required!");
        }
        // 机构类型
        if (StringUtils.isEmpty(organ.getType())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "Type is required!");
        }
        // 机构等级
        if (StringUtils.isEmpty(organ.getGrade())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "Grade is required!");
        }
        // 属地区域
        if (StringUtils.isEmpty(organ.getAddrArea())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "AddrArea is required!");
        }
        // 机构层级编码
        if (StringUtils.isEmpty(organ.getManageUnit())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "ManageUnit is required!");
        }
        Organ organ1 = getByManageUnit(organ.getManageUnit());
        if (organ1 != null) {
            throw new DAOException(609, "机构层级编码为【" + organ.getManageUnit()
                    + "】的机构记录已经存在");
        }
        Organ organ2 = new Organ();
        organ.setCreateDt(new Date());
        if (organ.getAccompanyFlag() == null) {
            organ.setAccompanyFlag(false);
        }
        if (organ.getAccompanyPrice() == null) {
            organ.setAccompanyPrice(0d);
        }
        if (organ.getTakeMedicineFlag() == null) {
            organ.setTakeMedicineFlag(0);
        }
        if (StringUtils.isEmpty(organ.getWxAccount())) {
            organ.setWxAccount("");
        }

        organ2 = save(organ);

        //注册机构结束后同时设置机构属性
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        OrganConfig organConfig = new OrganConfig();
        organConfig.setOrganId(organ2.getOrganId());
        organConfig.setAccountFlag(1);//默认奖励标记(0不奖励1奖励)
        organConfig.setPayAhead(-1);//默认机构预约提前支付不限制
        organConfigDAO.save(organConfig);


        //注册机构时候同生成 HisServiceConfig
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig hisServiceConfig = hisServiceConfigDAO.getByOrganId(organ2.getOrganId());
        if (hisServiceConfig == null) {
            hisServiceConfig = new HisServiceConfig();
            hisServiceConfig.setOrganid(organ2.getOrganId());
            hisServiceConfig.setOrganname(organ2.getName());
            hisServiceConfig.setAppointenable(1);
            hisServiceConfig.setTransferenable(1);
            hisServiceConfig.setPatientgetenable(0);
            hisServiceConfig.setMedfilingenable(0);
            hisServiceConfig.setTohis(0);
            hisServiceConfig.setCanAppointToday(0);
            hisServiceConfig.setHisStatus(0);
            hisServiceConfig.setSourcereal(0);
            hisServiceConfig.setCallNum(0);
            hisServiceConfig.setInhosptohis(0);
            hisServiceConfig.setCanFile(0);
            hisServiceConfig.setCanSign(Boolean.FALSE);
            hisServiceConfig.setExistOrgan(0);
            hisServiceConfig.setCheckHis(0);
            hisServiceConfig.setCancelPay(0);
            hisServiceConfig.setExistStatus("0");
            hisServiceConfig.setNeedValidateCode("0");
            hisServiceConfig.setSupportImage(0);
            hisServiceConfig.setPayTime(0);
            hisServiceConfigDAO.save(hisServiceConfig);
        }
        Integer key = organ2.getOrganId();
        DictionaryItem item = getDictionaryItem(key);
        //this.fireEvent(new CreateDAOEvent(key,item));
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_CREATE, "eh.base.dictionary.Organ");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error(e.getMessage());
        }
        return organ2;
    }

    /**
     * 更新医院服务
     *
     * @param organ
     * @return
     * @author LF
     */
    @RpcService
    public Organ updateOrgan(Organ organ) {
        Integer organId = organ.getOrganId();
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganId is required!");
        }
        if (getByOrganId(organId) == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "找不到机构");
        }
        String manageUnit = organ.getManageUnit();
        List<Organ> list = findOrganList(manageUnit, organId);
        if (list != null && list.size() > 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "机构层级编码为【" + manageUnit + "】的机构记录已经存在");
        }
        Organ organ2 = getByOrganId(organId);
        String manageUnitBef = organ2.getManageUnit();
        List<UserRoles> roles = DAOFactory.getDAO(UserRolesDAO.class).findByManageUnit(manageUnitBef);
        if (roles != null && roles.size() > 0) {
            organ.setManageUnit(manageUnitBef);
        }
        if (StringUtils.isEmpty(organ.getWxAccount())) {
            organ2.setWxAccount("");
        }
        StringBuffer sb = new StringBuffer();
        String oldOrganizeCode = organ2.getOrganizeCode();
        String newOrganizeCode = organ.getOrganizeCode();
        if ((oldOrganizeCode == null && newOrganizeCode != null)
                || (oldOrganizeCode != null && !oldOrganizeCode.equals(newOrganizeCode))) {
            sb.append("机构代码:【")
                    .append(oldOrganizeCode)
                    .append("】更新为【").append(newOrganizeCode).append("】,");
        }
        String oldName = organ2.getName();
        String newName = organ.getName();
        if ((oldName == null && newName != null)
                || (oldName != null && !oldName.equals(newName))) {
            sb.append("机构名称：【")
                    .append(oldName).append("】更新为【").append(newName).append("】,");
        }
        String oldShortName = organ2.getShortName();
        String newShortName = organ.getShortName();

        if ((oldShortName == null && newShortName != null)
                || (oldShortName != null && !oldShortName.equals(newShortName))) {
            sb.append("机构简称：【")
                    .append(oldShortName).append("】更新为【").append(newShortName).append("】,");
        }
        String oldType = organ2.getType();
        String newType = organ.getType();
        if ((oldType == null && newType != null)
                || (oldType != null && !oldType.equals(newType))) {
            sb.append("机构类型:【").append(getDicValue("eh.base.dictionary.Type", oldType)).append("】更新为【")
                    .append(getDicValue("eh.base.dictionary.Type", newType)).append("】，");
        }
        String oldGrade = organ2.getGrade();
        String newGrade = organ.getGrade();
        if ((oldGrade == null && newGrade != null)
                || (oldGrade != null && !oldGrade.equals(newGrade))) {
            sb.append("机构等级：【")
                    .append(getDicValue("eh.base.dictionary.Grade", oldGrade)).append("】更新为【")
                    .append(getDicValue("eh.base.dictionary.Grade", newGrade)).append("】,");
        }
        String oldAddrArea = organ2.getAddrArea();
        String newAddrArea = organ.getAddrArea();
        if ((oldAddrArea == null && newAddrArea != null)
                || (oldAddrArea != null && !oldAddrArea.equals(newAddrArea))) {
            sb.append("属地区域：【")
                    .append(getDicValue("eh.base.dictionary.AddrArea", oldAddrArea)).append("】更新为【")
                    .append(getDicValue("eh.base.dictionary.AddrArea", newAddrArea)).append("】,");
        }
        Integer oldOrderNum = organ2.getOrderNum();
        Integer newOrderNum = organ.getOrderNum();
        if ((oldOrderNum == null && newOrderNum != null)
                || (oldOrderNum != null && !oldOrderNum.equals(newOrderNum))) {
            sb.append("权重：【").append(oldOrderNum)
                    .append("】更新为【").append(newOrderNum).append("】,");
        }
        BeanUtils.map(organ, organ2);
        update(organ2);
        Integer key = organ2.getOrganId();
        DictionaryItem item = getDictionaryItem(key);
        //this.fireEvent(new UpdateDAOEvent(key,item));
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_UPDATE, "eh.base.dictionary.Organ");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error(e.getMessage());
        }
        if (sb.length() > 0) {
            BusActionLogService.recordBusinessLog("机构基本信息管理", organId.toString(), "Organ", "更新机构【" + organ.getOrganId() + "-" + organ.getName() + "】基本信息：" + sb.substring(0, sb.length() - 1));
        }
        //同步更新运维统计数据库
        OrganOPService organOPService = AppContextHolder.getBean("eh.organOPService",OrganOPService.class);
        organOPService.changeOrganStatusForOp(organ2.getOrganId(),organ2.getStatus());
        return organ2;
    }

    private String getDicValue(String dic, String name) {
        try {
            return StringUtils.isEmpty(name) ? "" : DictionaryController.instance().get(dic).getText(name);
        } catch (ControllerException e) {
            return "字典加载失败：" + name;
        }
    }

    /**
     * 删除机构
     *
     * @param organId
     * @author LF
     */
    @RpcService
    public void logicallyDeleteOrgan(Integer organId) {
        if (getByOrganId(organId) == null) {
            throw new DAOException(609, "未找到该机构");
        }
        if ((DAOFactory.getDAO(DoctorDAO.class)).findByOrgan(organId).size() > 0) {
            throw new DAOException(609, "该机构已有数据不能删除，请注销");
        }
        if ((DAOFactory.getDAO(DepartmentDAO.class)).findByOrganId(organId)
                .size() > 0) {
            throw new DAOException(609, "该机构已有数据不能删除，请注销");
        }
        if ((DAOFactory.getDAO(EmploymentDAO.class)).findByOrganId(organId)
                .size() > 0) {
            throw new DAOException(609, "该机构已有数据不能删除，请注销");
        }
        if ((DAOFactory.getDAO(UnitOpauthorizeDAO.class))
                .findByOrganIdAndAccreditOrgan(organId, organId).size() > 0) {
            throw new DAOException(609, "该机构已有数据不能删除，请注销");
        }
        deleteOrganByOrganId(organId);
    }

    @RpcService
    @DAOMethod
    public abstract void deleteOrganByOrganId(Integer organId);

    /**
     * 注销机构
     *
     * @param organId
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "update Organ set status=9 where organId=:organId")
    public abstract void updateStatusToCancellation(
            @DAOParam("organId") Integer organId);

    /**
     * 机构查询方法之情况一（按组织代码查）--hyj
     *
     * @param code
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract Organ getByOrganizeCode(String code);

    /**
     * 供前端调取医院列表(剔除无医生)
     *
     * @param addr
     * @return
     * @author LF
     */
    @RpcService
    public List<Organ> findByAddrAreaLike(final String addr) {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select a FROM Organ a WHERE type=:type");
                if (!StringUtils.isEmpty(addr)) {
                    hql.append(" AND (addrArea LIKE :addrArea)");
                }
                // 2016-12-08 zhangsl:患者端预约中按照机构、科室找医生需要添加普通挂号科室医生，添加and d.name like \"%普通%\"
                // 2016-01-19 zhangsl:修复普通挂号科室筛选条件bug
                hql.append(" AND status=:status AND exists (select e.organId from Employment e,Doctor r where e.organId=a.organId" +
                        " and r.doctorId=e.doctorId and r.status=1 and (IFNULL(r.idNumber,:empty)<>:empty or r.teams=1 or (IFNULL(r.idNumber,:empty)=:empty and r.generalDoctor=1))) order by grade");
                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(addr)) {
                    q.setParameter("addrArea", addr + "%");
                }
                q.setParameter("type", "1");
                q.setParameter("status", 1);
                q.setParameter("empty", "");
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供前端调取医院列表(剔除无医生)
     *
     * @param addr
     * @return
     * @author LF
     */
    @RpcService
    public List<Organ> findByAddrAreaAndShortNameLike(final String addr, final String shortName) {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select a FROM Organ a WHERE type=:type");
                if (!StringUtils.isEmpty(addr)) {
                    hql.append(" AND (addrArea LIKE :addrArea)");
                }
                if (!StringUtils.isEmpty(shortName)) {
                    hql.append(" AND a.shortName LIKE :shortName");
                }
                // 2016-12-08 zhangsl:患者端预约中按照机构、科室找医生需要添加普通挂号科室医生，添加and d.name like \"%普通%\"
                // 2016-01-19 zhangsl:修复普通挂号科室筛选条件bug
                hql.append(" AND status=:status AND exists (select e.organId from Employment e,Doctor r where e.organId=a.organId" +
                        " and r.doctorId=e.doctorId and r.status=1 and (IFNULL(r.idNumber,:empty)<>:empty or r.teams=1 or (IFNULL(r.idNumber,:empty)=:empty and r.generalDoctor=1))) order by grade");
                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(addr)) {
                    q.setParameter("addrArea", addr + "%");
                }
                if (!StringUtils.isEmpty(shortName)) {
                    q.setParameter("shortName", "%" + shortName + "%");
                }
                q.setParameter("type", "1");
                q.setParameter("status", 1);
                q.setParameter("empty", "");
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供前端调取医院列表----分页
     *
     * @param searchType 查询类型：0非个性化（剔除无医生并向上查找） 1非个性化（剔除无医生） 2按地区查询个性化 3查询个性化
     * @param addr
     * @param start
     * @param limit
     * @return
     * @author zhangsl 2016-12-20 18:24:58
     */
    @RpcService
    public List<Organ> findByAddrAreaLikeInPage(final Integer searchType, final String addr, final int start, final int limit) {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Integer> organIds = new ArrayList<>();
                StringBuilder hql = new StringBuilder("select a FROM Organ a WHERE type=:type AND status=:status ");
                if (searchType != 3) {
                    hql.append(" AND (addrArea LIKE :addrArea)");
                }
                if (searchType == 0 || searchType == 1) {
                    // 2016-12-08 zhangsl:患者端预约中按照机构、科室找医生需要添加普通挂号科室医生，添加and d.name like \"%普通%\"
                    // 2016-01-19 zhangsl:修复普通挂号科室筛选条件bug
                    hql.append(" AND exists (select e.organId from Employment e,Doctor r where e.organId=a.organId" +
                            " and r.doctorId=e.doctorId and r.status=1 and (IFNULL(r.idNumber,:empty)<>:empty or r.teams=1 or (IFNULL(r.idNumber,:empty)=:empty and r.generalDoctor=1))) ");
                } else {
                    organIds = findOrgansByUnitForHealth();
                    if (organIds != null && organIds.size() > 0) {
                        hql.append(" and (");
                        for (Integer i : organIds) {
                            hql.append("organId=").append(i).append(" or ");
                        }
                        hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                    }
                }
                hql.append(" order by grade");
                Query q = ss.createQuery(hql.toString());
                if (searchType != 3) {
                    q.setParameter("addrArea", addr + "%");
                }
                if (searchType == 0 || searchType == 1) {
                    q.setParameter("empty", "");
                }
                q.setParameter("type", "1");
                q.setParameter("status", 1);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据机构分级编码查询机构列表的服务
     * <p>
     * 给后台运营管理系统调用
     *
     * @param organ
     * @param start
     * @return
     * @author LF
     */
    @RpcService
    public List<Organ> findOrgansByManageUnit(final Organ organ,
                                              final Integer start) {
        // manageUnit和type必传
        if (StringUtils.isEmpty(organ.getManageUnit())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "manageUnit is required!");
        }
        if (StringUtils.isEmpty(organ.getType())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "type is required!");
        }
        final String manageUnit = organ.getManageUnit();
        final String type = organ.getType();
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer();
                String addrAreaString = new String();
                if (!StringUtils.isEmpty(organ.getAddrArea())) {
                    addrAreaString = " and addrArea like :addrArea";
                }
                hql.append("from Organ where manageUnit like :manageUnit");
                Query q;
                if (type.equals("0")) {
                    // 查询所有机构
                    hql.append(addrAreaString);
                    q = ss.createQuery(hql.toString());
                } else {
                    // 查询某一类型机构
                    hql.append(" and type=:type" + addrAreaString);
                    q = ss.createQuery(hql.toString());
                    q.setParameter("type", type);
                }

                q.setParameter("manageUnit", manageUnit + "%");
                if (!StringUtils.isEmpty(organ.getAddrArea())) {
                    // AddrArea不为空，则添加该字段条件判断
                    q.setParameter("addrArea", organ.getAddrArea() + "%");
                }
                // 分页
                q.setFirstResult(start);
                q.setMaxResults(10);
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 机构筛选
     *
     * @param organ
     * @param start
     * @return
     * @author LF
     */
    @RpcService
    public List<Organ> findOrgansByDefParam(final Organ organ,
                                            final Integer start) {
        // manageUnit必传
        if (StringUtils.isEmpty(organ.getManageUnit())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "manageUnit is required!");
        }
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String manageUnit = organ.getManageUnit();
                StringBuffer hql = new StringBuffer(
                        "from Organ where manageUnit like :manageUnit");
                String addrAreaString = new String();
                String nameString = new String();
                String gradeString = new String();
                String typeString = new String();
                if (!StringUtils.isEmpty(organ.getAddrArea())) {
                    addrAreaString = " and addrArea like :addrArea";
                }
                if (!StringUtils.isEmpty(organ.getName())) {
                    nameString = " and name like :name";
                }
                if (!StringUtils.isEmpty(organ.getGrade())) {
                    gradeString = " and grade=:grade";
                }
                if (!StringUtils.isEmpty(organ.getType())) {
                    typeString = " and type=:type";
                }
                hql.append(addrAreaString + nameString + gradeString
                        + typeString);
                hql.append(" order by  createDt desc");
                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(organ.getAddrArea())) {
                    q.setParameter("addrArea", organ.getAddrArea() + "%");
                }
                if (!StringUtils.isEmpty(organ.getName())) {
                    q.setParameter("name", "%" + organ.getName() + "%");
                }
                if (!StringUtils.isEmpty(organ.getGrade())) {
                    q.setParameter("grade", organ.getGrade());
                }
                if (!StringUtils.isEmpty(organ.getType())) {
                    q.setParameter("type", organ.getType());
                }
                q.setParameter("manageUnit", manageUnit + "%");
                // 分页
                q.setFirstResult(start);
                q.setMaxResults(10);
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 机构查询方法之情况二（按属地区域查）--hyj
     *
     * @param addr
     * @return
     */
    @DAOMethod
    public abstract List<Organ> findByAddrAreaLikeOld(String addr);

    @RpcService
    @DAOMethod(sql = "select name from Organ where organId = :id")
    public abstract String getNameById(@DAOParam("id") int organid);

    @RpcService
    @DAOMethod(sql = "select shortName from Organ where organId = :id")
    public abstract String getShortNameById(@DAOParam("id") int organid);

    @Override
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(organId,shortName) from Organ order by organId")
    public abstract List<DictionaryItem> findAllDictionaryItem(
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(organId,shortName) from Organ where organId=:id")
    public abstract DictionaryItem getDictionaryItem(@DAOParam("id") Object key);

    @DAOMethod(sql = "from Organ where manageUnit = :manageUnit and organId <> :organId")
    public abstract List<Organ> findOrganList(
            @DAOParam("manageUnit") String manageUnit,
            @DAOParam("organId") Integer organId);

    @RpcService
    @DAOMethod(sql = "from Organ where status = 1")
    public abstract List<Organ> findOrgans();

    /**
     * 服务名:联盟机构查询服务
     *
     * @param organId
     * @param buesType
     * @return List<Organ>
     * @throws DAOException 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     *                      修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Organ> queryRelaOrgan(final int organId, final String buesType)
            throws DAOException {
        List<Organ> oList = new ArrayList<Organ>();
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        // "select a from Organ a,UnitOpauthorize b where (b.accreditOrgan = :organId and b.accreditBuess = :buesType and a.organId=b.organId) or (b.organId = :organId and b.accreditBuess = :buesType and b.accreditOrgan=a.organId)");
                        "select a from Organ a,UnitOpauthorize b where (b.accreditOrgan = :organId and b.accreditBuess = :buesType and a.organId=b.organId)");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("organId", organId);
                query.setParameter("buesType", buesType);
                List<Organ> list = query.list();

                //app3.4需求：当没有授权机构时，显示所有机构
                if (null == list || list.isEmpty()) {
                    //2016-8-4 luf：剔除 其他 机构，添加 and manageUnit<>:manageUnit
                    String h = "from Organ where type=1 and status=1 and manageUnit<>:manageUnit";
                    Query q = ss.createQuery(h);
                    q.setParameter("manageUnit", OrganConstant.OtherOrgan_ManageUnit);
                    list = q.list();
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        oList = (List) action.getResult();
        return oList;
    }

    /**
     * 服务名:联盟机构查询服务
     *
     * @param organId
     * @param buesType
     * @param addr
     * @return List<Organ>
     * @throws DAOException 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     *                      修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     *                      增加按orderNum排序</br>
     * @author Qichengjian
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Organ> queryRelaOrganNew(final int organId,
                                         final String buesType, final String addr) throws DAOException {
        List<Organ> oList = new ArrayList<Organ>();
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        // "select a from Organ a,UnitOpauthorize b where b.organId = :organId and b.accreditBuess = :buesType and a.organId = b.accreditOrgan and a.type=:type AND a.addrArea LIKE :addrArea AND a.status=:status order by a.grade");
                        // "select distinct a from Organ a,UnitOpauthorize b where ((b.organId = :organId  and a.organId = b.accreditOrgan) or (b.accreditOrgan = :organId and a.organId = b.organId) or (a.organId = :organId)) and b.accreditBuess = :buesType and a.type=:type AND a.addrArea LIKE :addrArea AND a.status=:status order by a.grade");
                        // 2016-8-4 luf:若机构没有授权机构则显示所有机构，剔除其他机构。将( (b.accreditOrgan = :organId and a.organId = b.organId) or (a.organId = :organId) )修改为(b.accreditOrgan = :organId and a.organId = b.organId)
                        "select distinct a from Organ a,UnitOpauthorize b where (b.accreditOrgan = :organId and a.organId = b.organId) and b.accreditBuess = :buesType and a.type=:type  AND a.status=:status ");

                if (!StringUtils.isEmpty(addr)) {
                    hql.append("AND a.addrArea LIKE :addrArea");
                }

                hql.append(" order by b.orderNum,a.grade");

                Query query = ss.createQuery(hql.toString());
                query.setParameter("organId", organId);
                query.setParameter("buesType", buesType);
                query.setParameter("type", "1");
                query.setParameter("status", 1);

                if (!StringUtils.isEmpty(addr)) {
                    query.setParameter("addrArea", addr + "%");
                }

                List<Organ> list = query.list();

                //app3.4需求：当没有授权机构时，显示所有机构
                if (null == list || list.isEmpty()) {
                    //2016-8-4 luf：剔除 其他 机构，添加 and manageUnit<>:manageUnit
                    StringBuilder h = new StringBuilder("from Organ where type=1 and status=1 and manageUnit<>:manageUnit");
                    if (!StringUtils.isEmpty(addr)) {
                        h.append(" and addrArea like :addrArea");
                    }
                    h.append(" order by grade");
                    Query q = ss.createQuery(h.toString());

                    if (!StringUtils.isEmpty(addr)) {
                        q.setParameter("addrArea", addr + "%");
                    }
                    q.setParameter("manageUnit", OrganConstant.OtherOrgan_ManageUnit);
                    list = q.list();
                }
                // 2016-8-23 luf:授权机构添加自己
                boolean myself = false;
                for (Organ o : list) {
                    if (o.getOrganId() == organId) {
                        myself = true;
                        break;
                    }
                }
                if (!myself) {
                    List<Organ> organList = findByAddrAreaLike(addr);
                    Organ mine = DAOFactory.getDAO(OrganDAO.class).get(organId);
                    for (Organ organ : organList) {
                        if (organ.getOrganId().equals(organId)) {
                            list.add(mine);
                            break;
                        }
                    }
                }
                list = convertOrderByGrade(list);
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        oList = (List) action.getResult();
        return oList;
    }

    /**
     * 根据grade正序
     *
     * @param organs
     * @return
     */
    public List<Organ> convertOrderByGrade(List<Organ> organs) {
        Collections.sort(organs, new Comparator<Organ>() {
            public int compare(Organ arg0, Organ arg1) {
                return arg0.getGrade().compareTo(arg1.getGrade());
            }
        });
        return organs;
    }

    /**
     * 联盟机构查询服务(添加按姓名)
     *
     * @param organId 机构代码
     * @param bus     授权业务 --1转诊、预约 2会诊
     * @param addr    属地区域
     * @param name    机构名称
     * @return List<Organ>
     * @throws DAOException 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     *                      修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     *                      增加按orderNum排序</br>
     * @author luf
     * @date 2017-07-24 app,pc,xiaoyu均未调用
     */
    @RpcService
    public List<Organ> queryRelaOrganAddName(final int organId,
                                             final String bus, final String addr, final String name)
            throws DAOException {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        // "select distinct a from Organ a,UnitOpauthorize b where ((b.organId=:organId and a.organId=b.accreditOrgan) or (b.accreditOrgan=:organId and a.organId=b.organId) or (a.organId=:organId)) and b.accreditBuess=:bus and a.type=1 AND a.addrArea LIKE :addrArea AND a.status=1 and a.name like :name order by a.grade");
                        // 2016-8-4 luf:若机构没有授权机构则显示所有机构，剔除其他机构。将( (b.accreditOrgan=:organId and a.organId=b.organId) or (a.organId=:organId))修改为(b.accreditOrgan=:organId and a.organId=b.organId)
                        "select distinct a from Organ a,UnitOpauthorize b where (b.accreditOrgan=:organId and a.organId=b.organId) and b.accreditBuess=:bus and a.type=1  AND a.status=1 and a.name like :name ");

                if (!StringUtils.isEmpty(addr)) {
                    hql.append("AND a.addrArea LIKE :addrArea");
                }

                hql.append(" order by b.orderNum,a.grade");

                Query query = ss.createQuery(hql.toString());
                query.setParameter("organId", organId);
                query.setParameter("bus", bus);
                query.setParameter("name", "%" + name + "%");

                if (!StringUtils.isEmpty(addr)) {
                    query.setParameter("addrArea", addr + "%");
                }

                List<Organ> list = query.list();

                //app3.4需求：当没有授权机构时，显示所有机构
                if (null == list || list.isEmpty()) {
                    //2016-8-4 luf：剔除 其他 机构，添加 and manageUnit<>:manageUnit
                    StringBuilder h = new StringBuilder("from Organ where type=1 and status=1 and name like :name and manageUnit<>:manageUnit ");
                    if (!StringUtils.isEmpty(addr)) {
                        h.append(" and addrArea like :addrArea");
                    }
                    h.append(" order by grade");

                    Query q = ss.createQuery(h.toString());
                    q.setParameter("name", "%" + name + "%");
                    if (!StringUtils.isEmpty(addr)) {
                        q.setParameter("addrArea", addr + "%");
                    }
                    q.setParameter("manageUnit", OrganConstant.OtherOrgan_ManageUnit);
                    list = q.list();
                }
                // 2016-8-23 luf:授权机构添加自己
                boolean myself = false;
                for (Organ o : list) {
                    if (o.getOrganId() == organId) {
                        myself = true;
                        break;
                    }
                }
                if (!myself) {
                    List<Organ> organList = findByAddrAreaLike(addr);
                    Organ mine = DAOFactory.getDAO(OrganDAO.class).get(organId);
                    if (organList.contains(mine)) {
                        list.add(mine);
                    }
                }
                list = convertOrderByGrade(list);
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "select grade from Organ where organId in (:organIds) ")
    public abstract List<String> findGradeByOrganIds(@DAOParam("organIds") List<Integer> organIds);

    /**
     * 获取所有区域信息
     *
     * @return
     * @throws ControllerException
     * @author Eric
     */
    @RpcService
    public Map<Integer, Object> getAddrArea() throws ControllerException {
        ctd.dictionary.Dictionary dic = DictionaryController.instance().get(
                "eh.base.dictionary.AddrArea");
        List<DictionaryItem> items = dic.getSlice(null, 0, "");
        Map<Integer, Object> value = new HashMap<Integer, Object>();
        if (items != null && items.size() > 0) {
            for (int i = 0; i < items.size(); i++) {
                DictionaryItem item = items.get(i);
                DictionaryPageModel pg = new DictionaryPageModel();
                pg.setKey(item.getKey());
                pg.setValue(item.getText());
                value.put(i + 1, pg);
            }
        }
        return value;
    }

    @RpcService
    @DAOMethod
    public abstract void updatePhotoByOrganId(int photo, int organId);

    @RpcService
    @DAOMethod
    public abstract void updateCertImageByOrganId(int certImage, int organId);

    /**
     * 平台机构编号转换成his机构编号
     *
     * @param organId
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select organizeCode from Organ where organId=:organId")
    public abstract String getOrganizeCodeByOrganId(
            @DAOParam("organId") int organId);

    /**
     * his机构编号转换成平台机构编号
     *
     * @param organizeCode
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select organId from Organ where organizeCode=:organizeCode")
    public abstract int getOrganIdByOrganizeCode(
            @DAOParam("organizeCode") String organizeCode);

    /**
     * his机构编号转换成平台机构编号
     *
     * @param organizeCode
     * @return
     */
    @RpcService
    @DAOMethod(sql = "from Organ where organizeCode=:organizeCode")
    public abstract Organ getOrganByOrganizeCode(
            @DAOParam("organizeCode") String organizeCode);

    /**
     * 统计指定时间段内的新增机构数量
     *
     * @param manageUnit
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-5-22 下午4:25:56
     */
    @RpcService
    @DAOMethod(sql = "SELECT COUNT(*) FROM Organ  where manageUnit like :manageUnit and date(createDt) >=date(:startDate) and date(createDt) <=date(:endDate) ")
    public abstract Long getOrganNumFromTo(
            @DAOParam("manageUnit") String manageUnit,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 获取本月新增
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-22 下午4:41:29
     */
    @RpcService
    public Long getOrganNumByMonth(String manageUnit) {
        Date startDate = DateConversion.firstDayOfThisMonth();
        Date endDate = new Date();
        return getOrganNumFromTo(manageUnit + "%", startDate, endDate);
    }

    /**
     * 获取昨日新增机构数
     *
     * @return
     * @author ZX
     * @date 2015-5-21 下午4:00:17
     */
    @RpcService
    public Long getOrganNumByYesterday(String manageUnit) {
        Date date = Context.instance().get("date.getYesterday", Date.class);
        return getOrganNumFromTo(manageUnit + "%", date, date);
    }

    /**
     * 统计当前机构
     */
    @DAOMethod(sql = "SELECT COUNT(*) FROM Organ o where manageUnit like :manageUnit")
    public abstract Long getAllOrganNum(
            @DAOParam("manageUnit") String manageUnit);

    /**
     * 获取当前管理机构数量
     *
     * @param manageUnit
     * @return
     * @author ZX
     * @date 2015-5-22 下午5:44:49
     */
    @RpcService
    public Long getAllOrganNumWithManager(String manageUnit) {
        return getAllOrganNum(manageUnit + "%");
    }

    /**
     * 根据医生编码查询第一执业机构
     *
     * @param doctorId 医生编码
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select o from Organ o,Employment e where  o.organId=e.organId and e.primaryOrgan=1 and e.doctorId=:doctorId")
    public abstract Organ getByDoctorId(@DAOParam("doctorId") Integer doctorId);

    /**
     * 根据名称查询所有有效医院
     *
     * @param name 医院名称
     * @return List<Organ>
     * @author luf
     */
    @RpcService
    public List<Organ> findHospitalByNameLike(final String name) {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM Organ WHERE type=:type AND (name LIKE :name) AND status=:status order by grade";
                Query q = ss.createQuery(hql);
                q.setParameter("name", "%" + name.trim() + "%");
                q.setParameter("type", "1");
                q.setParameter("status", 1);
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();

    }

    /**
     * 根据名称查找该地区所有有效医院
     *
     * @param name 医院名称
     * @param addr 地址名称
     * @return List<Organ>
     * @author zhangz
     */
    @RpcService
    public List<Organ> findHospitalByNameAndAddrLike(final String name, final String addr) {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM Organ WHERE type=:type AND (name LIKE :name) AND (addrArea LIKE :addrArea) AND status=:status order by grade";
                Query q = ss.createQuery(hql);
                q.setParameter("name", "%" + name.trim() + "%");
                q.setParameter("addrArea", "%" + addr.trim() + "%");
                q.setParameter("type", "1");
                q.setParameter("status", 1);
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();

    }

    @RpcService
    @DAOMethod(sql = "select phoneNumber from Organ where organId = :id")
    public abstract String getPhoneNumberById(@DAOParam("id") int organId);

    /**
     * 判断能否修改管理单元格
     * zhongzx
     *
     * @param manageUnit
     * @return
     */
    @RpcService
    public Boolean canManageUnitUpdated(String manageUnit) {
        UserRolesDAO uDao = DAOFactory.getDAO(UserRolesDAO.class);
        List<UserRoles> userRoleList = uDao.findByManageUnit(manageUnit);
        if (userRoleList != null && userRoleList.size() != 0) {
            throw new DAOException(609, "该机构已录入医生，管理单元格不可修改");
        }
        return true;
    }


    /**
     * 根据医联体获取有效医院（供健康端个性化使用）
     *
     * @param manageUnit
     * @return
     */
    public List<Integer> findEffOrgansUnitLike(final String manageUnit) {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select organId From Organ where type=1 and status=1 and manageUnit like :manageUnit";
                Query q = ss.createQuery(hql);
                q.setParameter("manageUnit", manageUnit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取所有有效医院列表（供健康端个性化使用）
     *
     * @return
     */
    public List<Integer> findAllEffOrgan() {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select organId From Organ where type=1 and status=1";
                Query q = ss.createQuery(hql);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取所有有效医院列表(剔除 其他 机构)
     * <p>
     * 供UnitOpauthorizeDAO.findByBussId调用
     *
     * @return
     * @author luf
     * @date 2016-8-4
     */
    public List<Integer> findEffOrganWithoutOthor() {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                //2016-8-4 luf：剔除 其他 机构，添加 and manageUnit<>:manageUnit
                String hql = "select organId From Organ where type=1 and status=1 and manageUnit<>:manageUnit";
                Query q = ss.createQuery(hql);
                q.setParameter("manageUnit", OrganConstant.OtherOrgan_ManageUnit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供前端调取医院列表(健康端个性化)
     *
     * @param addr
     * @return
     * @author LF
     */
    @RpcService
    public List<Organ> findByAddrAreaLikeForHealth(final String addr) {
        //log.info("findByAddrAreaLikeForHealth.addr="+ JSONUtils.toString(addr));
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Integer> organIds = findOrgansByUnitForHealth();
                //log.info("findByAddrAreaLikeForHealth.organIds="+ JSONUtils.toString(organIds));
                StringBuilder hql = new StringBuilder("FROM Organ WHERE type=:type AND (addrArea LIKE :addrArea) AND status=:status");
                if (organIds != null && organIds.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organIds) {
                        hql.append("organId=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                hql.append(" order by grade");
                Query q = ss.createQuery(hql.toString());
                //log.info("findByAddrAreaLikeForHealth.hql="+ JSONUtils.toString(hql.toString()));
                q.setParameter("addrArea", addr + "%");
                q.setParameter("type", "1");
                q.setParameter("status", 1);
                List<Organ> organs = q.list();
                //log.info("findByAddrAreaLikeForHealth.getResult="+ JSONUtils.toString(organs));
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据名称查询所有有效医院(健康端个性化)
     *
     * @param name 医院名称
     * @return List<Organ>
     * @author luf
     */
    @RpcService
    public List<Organ> findHospitalByNameLikeForHealth(final String name) {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Integer> organIds = findOrgansByUnitForHealth();

                StringBuilder hql = new StringBuilder("FROM Organ WHERE type=:type AND (name LIKE :name) AND status=:status");
                if (organIds != null && organIds.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organIds) {
                        hql.append("organId=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                hql.append(" order by grade");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("name", "%" + name.trim() + "%");
                q.setParameter("type", "1");
                q.setParameter("status", 1);
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();

    }

    /**
     * 获取机构内码（供微信端个性化使用）
     *
     * @return
     */
    @RpcService
    public List<Integer> findOrgansByUnitForHealth() {
        String type = "1";//0 全国 1 当前管理单元，包括当前管理单元以下的，搜索按%	2 只限当前管理单元 搜索按=
        String manageUnitId = "eh";
        List<Integer> organs = new ArrayList<Integer>();
        Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        if (wxAppProperties != null && ValidateUtil.notBlankString(wxAppProperties.get("manageUnitId"))) {
            type = !StringUtils.isEmpty(wxAppProperties.get("type")) ? (String) wxAppProperties.get("type") : "0";
            manageUnitId = wxAppProperties.get("manageUnitId");
        }else {
            log.info("findOrgansByUnitForHealth wxAppProperties null！ currentClient[{}]", JSONObject.toJSONString(CurrentUserInfo.getCurrentClient()));
            return organs;
        }

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        if (type.equals("1")) {
            organs = organDAO.findEffOrgansUnitLike(manageUnitId + "%");
        } else if (type.equals("2")) {
            organs = organDAO.findEffOrgansUnitLike(manageUnitId);
        } else {
            organs = null;
        }
        log.info("findOrgansByUnitForHealth type[{}], manageUnit[{}], organs[{}]", type, manageUnitId, organs);
        return organs;
    }


    /**
     * 查询微信号对应的机构列表， 0 全国 1区域 2特定机构
     * 如果是区域，又开启了展示
     * 查询对应机构的config信息和机构detail信息
     * @return
     */
    @RpcService
    public HashMap<String, Object> getWxOrgansDisplay() {
        String type = "1";//0 全国 1 当前管理单元，包括当前管理单元以下的，搜索按%	2 只限当前管理单元 搜索按=
        String manageUnitId = "eh";
        Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        log.info("wxAppProperties[{}]", wxAppProperties);
        if (wxAppProperties != null && ValidateUtil.notBlankString(wxAppProperties.get("manageUnitId"))) {
            type = !StringUtils.isEmpty(wxAppProperties.get("type")) ? (String) wxAppProperties.get("type") : "0";
            manageUnitId = wxAppProperties.get("manageUnitId");
        }

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Integer> organs = new ArrayList<Integer>();
        if (type.equals("1")) {
            organs = organDAO.findEffOrgansUnitLike(manageUnitId + "%");
        } else if (type.equals("2")) {
            organs = organDAO.findEffOrgansUnitLike(manageUnitId);
        } else {
            //organs = null;
        }

        HashMap<String, Object> displayMap = Maps.newHashMap();

        if (wxAppProperties != null) {
            //把其他配置全部放入到displayMap中
            for (Map.Entry<String, String> wxAppPropsEs : wxAppProperties.entrySet()) {
                displayMap.put(wxAppPropsEs.getKey(), wxAppPropsEs.getValue());
            }

            //当设置为空或者为0时候，说明需要展示机构选择页
            if (wxAppProperties.get("areaOrganHidden") == null || wxAppProperties.get("areaOrganHidden").equalsIgnoreCase("0")) {
                if (type.equalsIgnoreCase("2")) {
                    displayMap.put("displayOrganSelector", false);
                } else {
                    displayMap.put("displayOrganSelector", true);
                }
                displayMap.put("organs", organs);
                displayMap.put("type", type);

                List<Organ> organDetailsList = Lists.newArrayList();
                List<OrganConfig> organConfigList = Lists.newArrayList();
                OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
                for (Integer organId : organs) {
                    organDetailsList.add(organDAO.getByOrganId(organId));
                    organConfigList.add(organConfigDAO.getByOrganId(organId));
                }

                displayMap.put("organDetails", organDetailsList);
                displayMap.put("organConfigDetails", organConfigList);


            } else if (wxAppProperties.get("areaOrganHidden").equalsIgnoreCase("1")) {  //需要隐藏（个性化展示）,展示当前查询出机构中任一机构号即可。
                Integer organId = null;
                if (!organs.isEmpty()) {
                    organId = organs.get(0);
                }
                displayMap.put("displayOrganSelector", false);
                displayMap.put("type", type);
                displayMap.put("organs", Lists.newArrayList(organId));

                List<Organ> organDetailsList = Lists.newArrayList();
                List<OrganConfig> organConfigList = Lists.newArrayList();
                OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
                organDetailsList.add(organDAO.getByOrganId(organId));
                organConfigList.add(organConfigDAO.getByOrganId(organId));

                displayMap.put("organDetails", organDetailsList);
                displayMap.put("organConfigDetails", organConfigList);
            }
        }
        log.info("current wx config[{}]", JSONUtils.toString(displayMap));
        return displayMap;
    }

    /**
     * 根据机构名称查找机构Like
     *
     * @param name 机构名称
     * @return List<Organ>
     * @author houxr
     * @date 2016-04-26 16:29:30
     */
    @RpcService
    public List<Organ> queryOrganByName(final String name) {
        if (StringUtils.isEmpty(name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "name is needed");
        }

        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM Organ WHERE name LIKE :name";
                Query q = ss.createQuery(hql);
                q.setParameter("name", "%" + name + "%");
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据机构名称查找机构
     *
     * @param name 机构名称
     * @return List<Organ>
     * @author Andywang
     * @date 2017-02-23 09:00:00
     */
    @RpcService
    public List<Organ> findOrganByName(final String name) {
        if (StringUtils.isEmpty(name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "name is needed");
        }
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM Organ WHERE name = :name";
                Query q = ss.createQuery(hql);
                q.setParameter("name", name);
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据机构简称查找机构
     *
     * @param shortName 机构简称
     * @return List<Organ>
     * @author houxr
     * @date 2016-04-26 16:29:30
     */
    @RpcService
    public List<Organ> findOrganByShortName(final String shortName) {
        if (StringUtils.isEmpty(shortName)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "shortName is needed");
        }

        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM Organ WHERE shortName = :shortName";
                Query q = ss.createQuery(hql);
                q.setParameter("shortName", shortName);
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据机构简称查找机构
     *
     * @param shortName 机构简称
     * @return List<Organ>
     * @author houxr
     * @date 2016-04-26 16:29:30
     */
    @RpcService
    public List<Organ> queryOrganByShortName(final String shortName) {
        if (StringUtils.isEmpty(shortName)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "shortName is needed");
        }

        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM Organ WHERE shortName LIKE :shortName";
                Query q = ss.createQuery(hql);
                q.setParameter("shortName", "%" + shortName + "%");
                List<Organ> organs = q.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 机构名称 进行重复性校验的服务
     *
     * @param organName
     * @return boolean
     * @author houxr
     * @date 2016-04-26 17:17:20
     */
    @RpcService
    public Boolean checkOrganIsExistByName(String organName) {
        organName = organName.trim();
        if (StringUtils.isEmpty(organName)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organName is needed");
        }

        List<Organ> organLists = this.findOrganByName(organName);
        if (organLists != null && organLists.size() != 0) {
            //throw new DAOException(609, "机构名称是: " + organName + " 对应的机构已存在");
            return true;
        }
        return false;
    }


    /**
     * 机构简称 进行重复性校验的服务
     *
     * @param shortName
     * @return boolean
     * @author houxr
     * @date 2016-04-26 17:17:20
     */
    @RpcService
    public Boolean checkOrganIsExistByShortName(String shortName) {
        if (StringUtils.isEmpty(shortName)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "shortName is needed");
        }
        List<Organ> organLists = this.findOrganByShortName(shortName);
        if (organLists != null && organLists.size() != 0) {
            //throw new DAOException(609, "机构简称是: " + shortName + " 对应的机构已存在");
            return true;
        }
        return false;
    }

    /**
     * 修改机构管理单元格
     * zhongzx
     *
     * @param organId    机构代码
     * @param manageUnit 新的编码
     * @return
     */
    @RpcService
    public Boolean updateManageUnit(int organId, String manageUnit) {
        if (StringUtils.isEmpty(manageUnit)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manageUnit is needed");
        }
        Organ organ = getByManageUnit(manageUnit);
        Organ oldOrgan = getByOrganId(organId);
        if (organ != null) {
            throw new DAOException(609, "机构层级编码为" + manageUnit + "的机构记录已经存在");
        }
        oldOrgan.setManageUnit(manageUnit);
        update(oldOrgan);
        return true;
    }

    /**
     * 管理单元 进行重复性校验
     *
     * @param manageUnit
     * @return boolean
     * @author houxr
     * @date 2016-04-27 17:17:20
     */
    @RpcService
    public Boolean checkOrganIsExistByManageUnit(String manageUnit) {
        if (StringUtils.isEmpty(manageUnit)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manageUnit is needed");
        }
        Organ organ = this.getByManageUnit(manageUnit);
        if (organ != null) {
            //throw new DAOException(609, "管理单元是: " + manageUnit + " 对应的机构已存在");
            return true;
        }
        return false;
    }

    /**
     * 获取机构能否取单 和 取单的条件
     * 1 就诊卡 2 就诊卡、医保卡 0 不需绑定（身份证查询） -1不能取单
     *
     * @param organid
     * @return
     */
    @RpcService
    public String getConsultByOrgan(int organid) {
        //1 就诊卡 2 就诊卡、医保卡  0 不需绑定（身份证查询）
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig config = hisServiceConfigDAO.getByOrganId(organid);
        if (config == null) {
            return "-1";
        } else {
            String canReport = config.getCanReport();
            if (StringUtils.isEmpty(canReport)) {
                return "-1";
            } else {
                return canReport;
            }
        }
    }

    /**
     * 获取机构能否支付 和 支付的条件
     * 1 就诊卡 2 就诊卡、医保卡 0 不需绑定（身份证查询） -1不能
     *
     * @param organId
     * @return
     */
    public String getPayResByOrgan(Integer organId) {
        //1 就诊卡 2 就诊卡、医保卡  0 不需绑定（身份证查询）
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig config = hisServiceConfigDAO.getByOrganId(organId);
        if (config == null) {
            return "-1";
        } else {
            String canPay = config.getCanPay();
            if (StringUtils.isEmpty(canPay)) {
                return "-1";
            } else {
                return canPay;
            }
        }
    }

    /**
     * 供前端调取医院列表(过滤未有取单功能的医院)-全国取单搜索
     *
     * @param addr
     * @return
     * @author zhangz
     */
    @RpcService
    public List<Organ> findByAddrAreaAndRepostLike(final String addr) {
        List<Organ> oList = new ArrayList<Organ>();
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select a FROM Organ a WHERE type=:type AND (addrArea LIKE :addrArea) AND status=:status" +
                                " and organid in(select organid from HisServiceConfig where canReport !='')" +
                                "  order by grade");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("addrArea", addr + "%");
                query.setParameter("type", "1");
                query.setParameter("status", 1);
                List<Organ> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        oList = (List) action.getResult();
        return oList;
    }

    /**
     * 过滤未有支付功能的医院-全国支付搜索
     * zhongzx
     *
     * @param addr
     * @return
     */
    @RpcService
    public List<Organ> findByAddrAreaAndPayLike(final String addr) {
        List<Organ> oList = new ArrayList<Organ>();
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select a FROM Organ a WHERE type=:type AND (addrArea LIKE :addrArea) AND status=:status" +
                                " and organid in(select organid from HisServiceConfig where canPay is not null or paymentInHosp =1)" +
                                "  order by grade");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("addrArea", addr + "%");
                query.setParameter("type", "1");
                query.setParameter("status", 1);
                List<Organ> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        oList = (List) action.getResult();
        return oList;
    }

    /**
     * 供前端调取医院列表(过滤不能查询缴费信息的医院)
     *
     * @param addr
     * @return
     */
    public List<Organ> findByAddrAreaAndPaymentLike(final String addr) {
        List<Organ> oList;
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select a FROM Organ a WHERE type=:type AND (addrArea LIKE :addrArea) AND status=:status" +
                                " and organid in(select organid from HisServiceConfig where paymentInHosp = 1)" +
                                "  order by grade");

                Query query = ss.createQuery(hql.toString());
                query.setParameter("addrArea", addr + "%");
                query.setParameter("type", "1");
                query.setParameter("status", 1);
                List<Organ> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        oList = (List) action.getResult();
        return oList;

    }

    /**
     * zhongzx
     * 根据名字进行查询有效医院列表
     *
     * @param name
     * @param flag 0-原来的模式 1-取单 2-支付
     * @return
     */
    @RpcService
    public List<Organ> findByFlagAndNameLike(final Integer flag, final String name, final String addr) {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                if (flag != 0) {
                    hql = new StringBuilder(
                            "FROM Organ WHERE type=:type AND status=:status and name LIKE :name and addrArea like :addr ");
                    if (1 == flag) {
                        hql.append("and organId in (select organid from HisServiceConfig where canReport is not null) ");
                    } else if (2 == flag) {
                        hql.append("and organId in (select organid from HisServiceConfig where canPay is not null or paymentInHosp =1) ");
                    }
                } else {
                    //剔除无医生的机构
                    hql = new StringBuilder("Select distinct o from Organ o,Doctor d,Employment e where o.type=:type " +
                            "AND o.status=:status and o.name LIKE :name and o.addrArea like :addr and e.organId=o.organId " +
                            "and e.doctorId=d.doctorId and d.status=1 and ((d.idNumber is not null and d.idNumber<>'') or d.teams=1)) ");
                }
                hql.append("order by grade");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("addr", addr + "%");
                query.setParameter("name", "%" + name + "%");
                query.setParameter("type", "1");
                query.setParameter("status", 1);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List) action.getResult();
    }

    /**
     * 微信个性化显示当前管理单元的机构列表
     * zhongzx
     *
     * @param flag 0-按照原来模式不变 1-取单搜索 2-支付搜素
     * @return
     */
    @RpcService
    public List<Organ> findByFlagForHealth(final Integer flag) {
        Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        if (null == wxAppProperties) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "wxApp is null or is not wxApp");
        }
        final String manageUnit = wxAppProperties.get("manageUnitId");
        final String type = wxAppProperties.get("type");
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "FROM Organ WHERE type=:type and manageUnit like :manageUnit AND status=:status ");
                if (1 == flag) {
                    hql.append("and organId in (select organid from HisServiceConfig where canReport !='') ");
                } else if (2 == flag) {
                    hql.append("and organId in (select organid from HisServiceConfig where canPay !='') ");
                }
                hql.append("order by grade");
                Query query = ss.createQuery(hql.toString());
                if ("1".equals(type)) {
                    query.setParameter("manageUnit", manageUnit + "%");
                } else {
                    query.setParameter("manageUnit", manageUnit);
                }
                query.setParameter("type", "1");
                query.setParameter("status", 1);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Organ> list = (List) action.getResult();
        return list;
    }

    /**
     * zhongzx
     * 根据名字搜索机构管理单元下的机构 （微信个性化）
     *
     * @param flag 0-按照原来模式不变 1-取单搜索 2-支付搜索
     * @param name
     * @return
     */
    @RpcService
    public List<Organ> findByNameLikeForHealth(final Integer flag, final String name) {
        Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        if (null == wxAppProperties) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "wxApp is null or is not wxApp");
        }
        final String manageUnit = wxAppProperties.get("manageUnitId");
        final String type = wxAppProperties.get("type");
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "FROM Organ WHERE type=:type and manageUnit like :manageUnit AND status=:status and name LIKE :name ");
                if (1 == flag) {
                    hql.append("and organId in (select organid from HisServiceConfig where canReport !='') ");
                } else if (2 == flag) {
                    hql.append("and organId in (select organid from HisServiceConfig where canPay !='') ");
                }
                hql.append("order by grade");
                Query query = ss.createQuery(hql.toString());
                if ("1".equals(type)) {
                    query.setParameter("manageUnit", manageUnit + "%");
                } else {
                    query.setParameter("manageUnit", manageUnit);
                }
                query.setParameter("name", "%" + name + "%");
                query.setParameter("type", "1");
                query.setParameter("status", 1);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List) action.getResult();
    }

    @DAOMethod(sql = "select takeMedicineFlag from Organ where organId = :id")
    public abstract Integer getTakeMedicineFlagById(@DAOParam("id") int organId);

    @DAOMethod(sql = "select wxAccount from Organ where organId = :id")
    public abstract String getWxAccountById(@DAOParam("id") int organId);

    /**
     * 运营平台 QueryResult 类型的机构列表
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Organ> queryOrgansByStartAndLimit(final Organ organ, final Date startDate, final Date endDate, final Integer start, final Integer limit) {
        if (StringUtils.isEmpty(organ.getManageUnit())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manageUnit is required!");
        }
        final Map<String, Object> preparedHqlAndParams = this.generateHQLAndParamsforStatistics(organ, startDate, endDate);
        HibernateStatelessResultAction<QueryResult<Organ>> action = new AbstractHibernateStatelessResultAction<QueryResult<Organ>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                //String manageUnit = organ.getManageUnit();
                StringBuilder hql = (StringBuilder) preparedHqlAndParams.remove("HQL");
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                countQuery.setProperties(preparedHqlAndParams);
                Long total = (Long) countQuery.uniqueResult();

                hql.append(" order by createDt desc");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(preparedHqlAndParams);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<Organ> organs = query.list();
                setResult(new QueryResult<Organ>(total, query.getFirstResult(), query.getMaxResults(), organs));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    private Map<String, Object> generateHQLAndParamsforStatistics(Organ organ, Date startDate, Date endDate) {
        StringBuilder hql = new StringBuilder("from Organ where manageUnit like :manageUnit");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("manageUnit", organ.getManageUnit() + "%");
        if (!StringUtils.isEmpty(organ.getAddrArea())) {
            hql.append(" and addrArea like :addrArea");
            params.put("addrArea", organ.getAddrArea() + "%");
        }
        if (!StringUtils.isEmpty(organ.getName())) {
            hql.append(" and name like :name");
            params.put("name", organ.getName() + "%");
        }
        if (!StringUtils.isEmpty(organ.getGrade())) {
            hql.append(" and grade like :grade");
            params.put("grade", organ.getGrade() + "%");
        }
        if (!StringUtils.isEmpty(organ.getType())) {
            hql.append(" and type=:type");
            params.put("type", organ.getType());
        }
        if (organ.getStatus() != null) {
            hql.append(" and status=:status");
            params.put("status", organ.getStatus());
        }
        if (startDate != null) {
            hql.append(" and DATE(createDt)>=DATE(:startDate)");
            params.put("startDate", startDate);
        }
        if (endDate != null) {
            hql.append(" and DATE(createDt)<=DATE(:endDate)");
            params.put("endDate", endDate);
        }
        params.put("HQL", hql);
        return params;
    }

    public HashMap<String, Integer> getStatisticsByGrade(
            final Organ organ, final Date startDate, final Date endDate) {
        if (StringUtils.isEmpty(organ.getManageUnit())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manageUnit is required!");
        }
        final Map<String, Object> preparedHqlAndParams = this.generateHQLAndParamsforStatistics(organ, startDate, endDate);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = (StringBuilder) preparedHqlAndParams.remove("HQL");
                hql.insert(0, "select grade, count(organId) as count ").append(" group by grade ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(preparedHqlAndParams);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            String grade = hps[0].toString();
                            String gradeName = DictionaryController.instance()
                                    .get("eh.base.dictionary.Grade").getText(grade);
                            mapStatistics.put(gradeName, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public HashMap<String, Integer> getStatisticsByStatus(
            final Organ organ, final Date startDate, final Date endDate) {
        if (StringUtils.isEmpty(organ.getManageUnit())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manageUnit is required!");
        }
        final Map<String, Object> preparedHqlAndParams = this.generateHQLAndParamsforStatistics(organ, startDate, endDate);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = (StringBuilder) preparedHqlAndParams.remove("HQL");
                hql.insert(0, "select status, count(organId) as count ").append(" group by status ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(preparedHqlAndParams);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            String status = hps[0].toString();
                            String gradeName = DictionaryController.instance()
                                    .get("eh.base.dictionary.OrganStatus").getText(status);
                            mapStatistics.put(gradeName, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    public HashMap<String, Integer> getStatisticsByType(
            final Organ organ, final Date startDate, final Date endDate) {
        if (StringUtils.isEmpty(organ.getManageUnit())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manageUnit is required!");
        }
        final Map<String, Object> preparedHqlAndParams = this.generateHQLAndParamsforStatistics(organ, startDate, endDate);
        HibernateStatelessResultAction<HashMap<String, Integer>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = (StringBuilder) preparedHqlAndParams.remove("HQL");
                hql.insert(0, "select type, count(organId) as count ").append(" group by type ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(preparedHqlAndParams);
                List<Object[]> tfList = query.list();
                HashMap<String, Integer> mapStatistics = new HashMap<String, Integer>();
                if (tfList.size() > 0) {
                    for (Object[] hps : tfList) {
                        if (hps[0] != null && !StringUtils.isEmpty(hps[0].toString())) {
                            String type = hps[0].toString();
                            String gradeName = DictionaryController.instance()
                                    .get("eh.base.dictionary.Type").getText(type);
                            mapStatistics.put(gradeName, Integer.parseInt(hps[1].toString()));
                        }
                    }
                }
                setResult(mapStatistics);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "select organId from Organ where grade=:grade")
    public abstract List<Integer> findOrganIdsByGrade(@DAOParam("grade") String grade);

    @RpcService
    public List<Integer> findOrganIdsByAddrArea(final String addrArea) {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select DISTINCT organId From Organ where type=1 and status=1 ");
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append("and addrArea like :addrArea ");
                }
                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    // TODO
    public abstract List<Integer> findOrgansWithNumSystem();

    /**
     * 获取机构区域编码
     *
     * @param organId
     * @return
     */
    @DAOMethod(sql = "select addrArea from Organ where organId=:organId")
    public abstract String getAddrAreaByOrganId(@DAOParam("organId") int organId);

    /**
     * 获取推荐机构 (仅展示有开通签约业务医生的机构)
     *
     * @param organList
     * @param addrArea
     * @param removeAddr
     * @param addrSymbol
     * @param limit
     * @return
     */
    public List<RecommendOrganBean> findRecommendOrgan(final List<Integer> organList, final String addrArea,
                                                       final String removeAddr, final String addrSymbol, final Integer limit) {
        HibernateStatelessResultAction<List<RecommendOrganBean>> action = new AbstractHibernateStatelessResultAction<List<RecommendOrganBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("select new eh.entity.bus.housekeeper.RecommendOrganBean(doc.organ,org.name,org.address,org.photo) from Doctor doc,Organ org,ConsultSet s " +
                        "where doc.doctorId = s.doctorId and doc.organ = org.organId ")
                        .append("and s.canSign=1 and org.status=1 and doc.teams=0 and org.addrArea " + addrSymbol + " :addrArea ");
                if (null != organList && !organList.isEmpty()) {
                    hql.append("and org.organId in :organList ");
                }
                if (org.apache.commons.lang3.StringUtils.isNotEmpty(removeAddr)) {
                    hql.append("and org.addrArea " + ConditionOperator.NOT_EQUAL + " :removeAddr ");
                }
                hql.append("GROUP BY doc.organ");

                Query q = ss.createQuery(hql.toString());
                if (addrSymbol.equals(ConditionOperator.LIKE)) {
                    q.setParameter("addrArea", addrArea + "%");
                } else {
                    q.setParameter("addrArea", addrArea);
                }
                if (null != organList && !organList.isEmpty()) {
                    q.setParameterList("organList", organList);
                }
                if (org.apache.commons.lang3.StringUtils.isNotEmpty(removeAddr)) {
                    q.setParameter("removeAddr", removeAddr);
                }
                if (null != limit) {
                    q.setFirstResult(0);
                    q.setMaxResults(limit);
                }

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 微信个性化显示当前管理单元的机构列表(剔除无医生)
     * zsl
     *
     * @param addr
     * @return
     */
    @RpcService
    public List<Organ> findValidOrganForHealth(final String addr) {
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<Integer> organs = findOrgansByUnitForHealth();
                StringBuilder hql = new StringBuilder(
                        "FROM Organ a WHERE type=:type and addrArea like :addr AND status=:status " +
                                "AND exists (select r.organ from Doctor r where r.organ=a.organId " +
                                "and r.status=1 and ((r.idNumber is not null and r.idNumber<>'') or r.teams=1))");
                if (organs != null && organs.size() > 0) {
                    hql.append("and (");
                    for (Integer organId : organs) {
                        hql.append("organId=").append(organId).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                hql.append(" order by grade");
                log.info("findValidOrganForHealth hql[{}]", hql.toString());
                Query query = ss.createQuery(hql.toString());
                query.setParameter("addr", addr + "%");
                query.setParameter("type", "1");
                query.setParameter("status", 1);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Organ> list = (List) action.getResult();
        return list;
    }

   /* @DAOMethod
    public abstract Organ getByName(String Name);*/

    @DAOMethod(sql = " from Organ where status = 1 and name=:name")
    public abstract Organ getValidOrganByName(@DAOParam("name") String Name);

    @DAOMethod(sql = "select a from Organ a where organId in (:organIds) ")
    public abstract List<Organ> findOrgansByOrganIds(@DAOParam("organIds") List<Integer> organIds);

    public HashMap<Integer, Organ> convertToHashMapKeyWithOrganId(List<Organ> organs) {
        HashMap<Integer, Organ> map = new HashMap<Integer, Organ>();
        Iterator it1 = organs.iterator();
        while (it1.hasNext()) {
            Organ o = (Organ) it1.next();
            Integer organId = o.getOrganId();
            map.put(organId, o);
        }
        return map;
    }

    /**
     * 服务名:联盟机构查询服务
     *
     * @param doctorId
     * @param organId
     * @param buesType
     * @param shortName
     * @param addr
     * @return List<Organ>
     * @throws DAOException 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     *                      修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     *                      增加按orderNum排序</br>
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Organ> queryRelaOrganForCloud(final int doctorId, final int organId,
                                              final String buesType, final String shortName, final String addr) throws DAOException {
        if (!StringUtils.isEmpty(shortName)) {
            //添加历史搜索记录
            SearchContentDAO contentDAO = DAOFactory.getDAO(SearchContentDAO.class);
            SearchContent content = new SearchContent();
            content.setDoctorId(doctorId);
            content.setBussType(2);
            content.setContent(shortName);
            contentDAO.addSearchContent(content, 1);
        }
        List<Organ> oList = new ArrayList<Organ>();
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        // "select a from Organ a,UnitOpauthorize b where b.organId = :organId and b.accreditBuess = :buesType and a.organId = b.accreditOrgan and a.type=:type AND a.addrArea LIKE :addrArea AND a.status=:status order by a.grade");
                        // "select distinct a from Organ a,UnitOpauthorize b where ((b.organId = :organId  and a.organId = b.accreditOrgan) or (b.accreditOrgan = :organId and a.organId = b.organId) or (a.organId = :organId)) and b.accreditBuess = :buesType and a.type=:type AND a.addrArea LIKE :addrArea AND a.status=:status order by a.grade");
                        // 2016-8-4 luf:若机构没有授权机构则显示所有机构，剔除其他机构。将( (b.accreditOrgan = :organId and a.organId = b.organId) or (a.organId = :organId) )修改为(b.accreditOrgan = :organId and a.organId = b.organId)
                        "select distinct a from Organ a,UnitOpauthorize b where (b.accreditOrgan = :organId and a.organId = b.organId) and b.accreditBuess = :buesType and a.type=:type  AND a.status=:status ");

                if (!StringUtils.isEmpty(addr)) {
                    hql.append(" AND a.addrArea LIKE :addrArea");
                }
                if (!StringUtils.isEmpty(shortName)) {
                    hql.append(" AND a.shortName LIKE :shortName");
                }

                hql.append(" order by b.orderNum,a.grade");

                Query query = ss.createQuery(hql.toString());
                query.setParameter("organId", organId);
                query.setParameter("buesType", buesType);
                query.setParameter("type", "1");
                query.setParameter("status", 1);

                if (!StringUtils.isEmpty(addr)) {
                    query.setParameter("addrArea", addr + "%");
                }
                if (!StringUtils.isEmpty(shortName)) {
                    query.setParameter("shortName", "%" + shortName + "%");
                }

                List<Organ> list = query.list();

                //app3.4需求：当没有授权机构时，显示所有机构
                if (null == list || list.isEmpty()) {
                    //2016-8-4 luf：剔除 其他 机构，添加 and manageUnit<>:manageUnit
                    StringBuilder h = new StringBuilder("from Organ where type=1 and status=1 and manageUnit<>:manageUnit");
                    if (!StringUtils.isEmpty(addr)) {
                        h.append(" and addrArea like :addrArea");
                    }
                    if (!StringUtils.isEmpty(shortName)) {
                        h.append(" AND shortName LIKE :shortName");
                    }
                    h.append(" order by grade");
                    Query q = ss.createQuery(h.toString());

                    if (!StringUtils.isEmpty(addr)) {
                        q.setParameter("addrArea", addr + "%");
                    }
                    if (!StringUtils.isEmpty(shortName)) {
                        q.setParameter("shortName", "%" + shortName + "%");
                    }
                    q.setParameter("manageUnit", OrganConstant.OtherOrgan_ManageUnit);
                    list = q.list();
                }
                // 2016-8-23 luf:授权机构添加自己
                boolean myself = false;
                for (Organ o : list) {
                    if (o.getOrganId() == organId) {
                        myself = true;
                        break;
                    }
                }
                if (!myself) {
                    List<Organ> organList = findByAddrAreaAndShortNameLike(addr, shortName);
                    for (Organ organ : organList) {
                        if (organ.getOrganId().equals(organId)) {
                            Organ mine = DAOFactory.getDAO(OrganDAO.class).get(organId);
                            list.add(mine);
                            break;
                        }
                    }
                }
                list = convertOrderByGrade(list);
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        oList = (List) action.getResult();
        return oList;
    }

    /**
     * 给在线续方获取 所有有效机构
     *
     * @return
     */
    private List<Integer> findEffectiveOrgansForRecipe() {
        List<Integer> organIds = findOrgansByUnitForHealth();
        if (null == organIds) {
            organIds = findAllEffOrgan();
        }
        return organIds;
    }

    /**
     * 查询 某个 区域 或者 能开某个药品的开方机构
     *
     * @param addr   区域编码
     * @param drugId 药品平台编号
     * @return
     * @author zhongzx
     */
    public List<Organ> queryOrganCanRecipe(final String addr, final Integer drugId) {
        final List<Integer> organIds = findEffectiveOrgansForRecipe();
        if (null != organIds && organIds.size() > 0) {
            HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql = new StringBuilder("select DISTINCT g from Organ g, DrugList d, OrganDrugList o where " +
                            "g.organId = o.organId and g.type =1 and g.status = 1 and o.drugId = d.drugId and d.status = 1 " +
                            "and o.status = 1 and g.organId in (:organIds) ");
                    if (!StringUtils.isEmpty(addr)) {
                        hql.append("and g.addrArea like :addr ");
                    }
                    if (null != drugId && drugId > 0) {
                        hql.append("and d.drugId = :drugId");
                    }
                    Query q = ss.createQuery(hql.toString());
                    q.setParameterList("organIds", organIds);
                    if (!StringUtils.isEmpty(addr)) {
                        q.setParameter("addr", addr + "%");
                    }
                    if (null != drugId && drugId > 0) {
                        q.setParameter("drugId", drugId);
                    }
                    setResult(q.list());
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action);
            return action.getResult();
        }
        return null;
    }

    /**
     * 查询能开处方机构的 机构平台编号集合
     *
     * @param addr
     * @param drugId
     * @return
     * @author zhongzx
     */
    public List<Integer> queryOragnIdsCanRecipe(final String addr, final Integer drugId) {
        List<Organ> organList = queryOrganCanRecipe(addr, drugId);
        if (null != organList && organList.size() > 0) {
            List<Integer> organIds = new ArrayList<>();
            for (Organ organ : organList) {
                organIds.add(organ.getOrganId());
            }
            return organIds;
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 根据机构获取云门诊价格
     *
     * @param doctorId
     * @return
     */
    public double getCloudClinicPriceByOrgan(int doctorId) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        if (doctor == null || doctor.getProTitle() == null || doctor.getOrgan() == null || StringUtils.isEmpty(doctor.getProTitle()) || doctor.getOrgan() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctor or organ or proTitle is required!");
        }

        OrganCloudPriceService priceService = AppDomainContext.getBean("eh.organCloudPriceService", OrganCloudPriceService.class);
        return priceService.getPriceForCloudOnlineOnly(doctor.getOrgan(), doctor.getProTitle());
    }

    /**
     * 查询支持用就诊卡的机构
     *
     * @param addr 选择的区域
     * @return
     */
    @RpcService
    public List<Organ> getOrgans(final String addr, final String organName) {
        AbstractHibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                /*String inAddrs = "";
                if (addrs.length > 0) {
                    inAddrs = Joiner.on(",").join(addrs);
                }*/
                StringBuilder hql = new StringBuilder("from Organ o ,OrganConfig oc where o.organId = oc.organId and oc.organHealthCardSupport = true and o.status=1  and o.addrArea like :addrArea");
                if (organName != null && !organName.trim().equalsIgnoreCase("")) {
                    hql.append(" and (o.name like :organName or o.shortName like :organName)");
                }

                Query query = ss.createQuery(hql.toString());
                query.setParameter("addrArea", addr + "%");

                if (organName != null && !organName.trim().equalsIgnoreCase("")) {
                    query.setParameter("organName", "%" + organName + "%");
                }

                List<Organ> organs = query.list();
                setResult(organs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询支持用就诊卡的机构数量
     *
     * @return
     */
    @RpcService
    @DAOMethod(sql = "SELECT count(*) FROM Organ o ,OrganConfig oc where o.organId = oc.organId and oc.organHealthCardSupport = true and o.status=1")
    public abstract long getCountsSupportHealthCard();

    /**
     * 查新his接口是否支持选定机构查询就诊卡
     *
     * @param organId 传入的机构Id
     * @since 2017-4-19
     */
    @RpcService
    @Deprecated
    public Boolean canQueryHealthCardFromHis(int organId, String serviceName) {
        boolean canQueryHealthCardFromHis = false;

        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        HisServiceConfig hisOrganConfig = hisServiceConfigDAO.getByOrganId(organId);

        if (hisOrganConfig != null) {
            String serviceAddr = hisOrganConfig.getAppDomainId() + serviceName;

            ClientSetDAO clientSetDAO = DAOFactory.getDAO(ClientSetDAO.class);
            if (clientSetDAO.getByOrganIdAndServiceName(serviceAddr) != null) {
                canQueryHealthCardFromHis = true;
            }
        }
        log.info("organId :[{}],serviceName:[{}],canQueryHealthCardFromHis result:[{}] ", organId, serviceName, canQueryHealthCardFromHis);
        return canQueryHealthCardFromHis;
    }

    /**
     * 根据省份汇总医院数量
     *
     * @return
     */
    @RpcService
    public List<Map<String, Object>> countByProvince() {
        HibernateStatelessResultAction<List<Map<String, Object>>> action = new AbstractHibernateStatelessResultAction<List<Map<String, Object>>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select substring(addrArea,1,2),count(*) from Organ where addrArea is not null group by substring(addrArea,1,2)";
                Query query = ss.createQuery(hql);
                List<Object[]> list = query.list();
                List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
                if (list != null && !list.isEmpty()) {
                    Iterator<Object[]> iterator = list.iterator();
                    for (Object[] obj : list) {
                        Map<String, Object> map = new HashMap<String, Object>();
                        map.put("province", obj[0]);
                        map.put("provinceName", DictionaryController.instance().get("eh.base.dictionary.AddrArea").getText(obj[0]));
                        map.put("count", obj[1]);
                        maps.add(map);
                    }
                }
                setResult(maps);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    @DAOMethod(sql = " select count(*) from Organ where manageUnit like:manageUnit and organId in:organIds")
    public abstract Long getAuthoritiedOrgan(@DAOParam("manageUnit") String manageUnit,@DAOParam("organIds") List<Integer> organIds);

    @DAOMethod(sql = "select organId from Organ where manageUnit like:manageUnit and status=1",limit = 0)
    public abstract List<Integer> findEffOrganIdsByManageUnit(@DAOParam("manageUnit") String manageUnit);

    @DAOMethod(sql = "select organId from Organ where manageUnit like :manageUnit",limit = 0)
    public abstract List<Integer> findOrganIdsByManageUnit(@DAOParam("manageUnit") String manageUnit);

    /**
     * 按条件查询预约云门诊机构列表（剔除无号源）-sql
     *
     * @param shortName
     * @param addr
     * @param organs
     * @param doctorId
     * @param todayOrgans
     * @return
     */
    public List<Organ> queryOrganForCloud(final String shortName, final String addr, final List<Integer> organs, final int doctorId, final List<Integer> todayOrgans) {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("select distinct o from Organ o,Employment e " +
                        "where o.organId in(:organs) and o.type=1 and o.status=1 and e.organId=o.organId and e.doctorId<>:doctorId ");
                if (!org.apache.commons.lang3.StringUtils.isEmpty(addr)) {
                    hql.append(" AND o.addrArea LIKE :addrArea ");
                }
                if (!org.apache.commons.lang3.StringUtils.isBlank(shortName)) {
                    hql.append(" AND o.shortName LIKE :shortName ");
                }
                hql.append("and exists(FROM AppointSource s WHERE s.cloudClinic=1 AND s.cloudClinicType=1 AND s.stopFlag=0 AND s.doctorId=e.doctorId ");
                hql.append("AND s.organId=o.organId ");//要有当前机构下的号源
                if (todayOrgans != null && !todayOrgans.isEmpty()) {//有当天号源可约机构
                    hql.append("AND ((s.workDate>NOW() AND (s.sourceNum-s.usedNum)>0) OR (s.workDate=DATE(NOW()) AND s.organId in(:todayOrgans) and sourceNum>=usedNum ))) ");
                } else {
                    hql.append("AND s.workDate>NOW() AND (s.sourceNum-s.usedNum)>0) ");
                }
                hql.append(" order by o.grade");
                Query q = ss.createQuery(hql.toString());
                if (!org.apache.commons.lang3.StringUtils.isEmpty(addr)) {
                    q.setParameter("addrArea", addr + "%");
                }
                if (!org.apache.commons.lang3.StringUtils.isBlank(shortName)) {
                    q.setParameter("shortName", "%" + shortName + "%");
                    SearchContentDAO searchContentDAO = DAOFactory.getDAO(SearchContentDAO.class);
                    SearchContent searchContent = new SearchContent();
                    searchContent.setDoctorId(doctorId);
                    searchContent.setContent(shortName);
                    searchContent.setBussType(SearchConstant.SEARCHTYPE_YCYY);
                    searchContentDAO.addSearchContent(searchContent, 1);
                }
                if (todayOrgans != null && !todayOrgans.isEmpty()) {//有当天号源可约机构
                    q.setParameterList("todayOrgans", todayOrgans);
                }
                q.setParameterList("organs", organs);
                q.setParameter("doctorId", doctorId);
                List<Organ> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 按条件查询会诊中心机构列表（剔除无会诊中心）-sql
     *
     * @param shortName
     * @param addr
     * @param organs
     * @param doctorId
     * @return
     */
    public List<Organ> queryOrganForMeetCenter(final String shortName, final String addr, final List<Integer> organs, final int doctorId) {
        HibernateStatelessResultAction<List<Organ>> action = new AbstractHibernateStatelessResultAction<List<Organ>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("select distinct o from Organ o where o.organId in(:organs) and o.type=1 and o.status=1 " +
                        "and exists(from Doctor d,DoctorTab dt,Employment e,ConsultSet c " +
                        "where dt.paramType=:paramType and dt.paramValue=:paramValue and d.doctorId=dt.doctorId and d.doctorId<>:doctorId and d.status=1 " +
                        "and e.doctorId=d.doctorId and d.doctorId=c.doctorId and c.meetClinicStatus=1 and o.organId=e.organId) " +
                        "");
                if (!org.apache.commons.lang3.StringUtils.isEmpty(addr)) {
                    hql.append(" AND o.addrArea LIKE :addrArea ");
                }
                if (!org.apache.commons.lang3.StringUtils.isBlank(shortName)) {
                    hql.append(" AND o.shortName LIKE :shortName ");
                }
                hql.append("order by o.grade");
                Query q = ss.createQuery(hql.toString());
                if (!org.apache.commons.lang3.StringUtils.isEmpty(addr)) {
                    q.setParameter("addrArea", addr + "%");
                }
                if (!org.apache.commons.lang3.StringUtils.isBlank(shortName)) {
                    q.setParameter("shortName", "%" + shortName + "%");
                    SearchContentDAO searchContentDAO = DAOFactory.getDAO(SearchContentDAO.class);
                    SearchContent searchContent = new SearchContent();
                    searchContent.setDoctorId(doctorId);
                    searchContent.setContent(shortName);
                    searchContent.setBussType(SearchConstant.SEARCHTYPE_HZZXYY); //会诊中心医生名称
                    searchContentDAO.addSearchContent(searchContent, 1);
                }
                q.setParameterList("organs", organs);
                q.setParameter("doctorId", doctorId);
                q.setParameter("paramType", DoctorTabConstant.ParamType_MEETCENTER);
                q.setParameter("paramValue", DoctorTabConstant.ParamValue_TRUE);
                List<Organ> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

}
