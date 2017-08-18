package eh.base.dao;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import ctd.controller.exception.ControllerException;
import ctd.controller.notifier.NotifierCommands;
import ctd.controller.notifier.NotifierMessage;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.service.BusActionLogService;
import eh.base.service.ScratchableService;
import eh.bus.dao.AppointDepartDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Department;
import eh.entity.base.Scratchable;
import eh.entity.bus.AppointDepart;
import eh.entity.tx.TxDepart;
import eh.utils.MapValueUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public abstract class DepartmentDAO extends
        HibernateSupportDelegateDAO<Department> implements
        DBDictionaryItemLoader<Department> {

    public static final Logger log = Logger.getLogger(DepartmentDAO.class);

    public LoadingCache<String, List<Object[]>> professionOrder = CacheBuilder.newBuilder().expireAfterWrite(7, TimeUnit.DAYS).build(new CacheLoader<String, List<Object[]>>() {
        @Override
        public List<Object[]> load(String s) throws Exception {
            return setCatchProfession(s);
        }
    });

    public DepartmentDAO() {
        super();
        this.setEntityName(Department.class.getName());
        this.setKeyField("deptId");

    }

    public List<Object[]> setCatchProfession(final String s) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                List<Object[]> list = new ArrayList<Object[]>();
                if ("order".equals(s)) {
                    StringBuilder hql = new StringBuilder("SELECT SUBSTRING(a.professionCode,1,2),count(*) FROM Department a," +
                            "Employment b where a.deptId=b.department and a.professionCode<>'98' group by SUBSTRING(a.professionCode,1,2) " +
                            " having count(*) > 0 order by count(*) desc");
                    Query q = statelessSession.createQuery(hql.toString()).setFirstResult(0);
                    list = (List<Object[]>) q.list();
                } else {
                    StringBuilder hql = new StringBuilder("SELECT  SUBSTRING(a.professionCode,1,2),count(*) " +
                            "FROM Department a,Consult b where a.deptId=b.consultDepart  and a.professionCode<>'98' " +
                            "group by SUBSTRING(a.professionCode,1,2) having count(*) > 0 order by count(*) desc");
                    Query q1 = statelessSession.createQuery(hql.toString()).setFirstResult(0);
                    list = (List<Object[]>) q1.list();
                }
                if (list == null || list.isEmpty()) {
                    list = new ArrayList<Object[]>();
                }
                if (list.size() < 8) {
                    String[] strHps = new String[list.size() + 1];
                    int j = 0;
                    for (Object[] hps : list) {
                        strHps[j] = hps[0].toString();
                        j++;
                    }
                    strHps[j] = "98";//剔除"其它"专科
                    StringBuilder hql = new StringBuilder("SELECT distinct SUBSTRING(professionCode,1,2),0 FROM Department a " +
                            "where SUBSTRING(a.professionCode,1,2) not in(:strHps)");
                    Query q2 = statelessSession.createQuery(hql.toString()).setFirstResult(0).setMaxResults(8 - list.size());
                    q2.setParameterList("strHps", strHps);
                    list.addAll((List<Object[]>) q2.list());
                }
                if ("order".equals(s)) {
                    professionOrder.put("order", list);
                } else {
                    professionOrder.put("other", list);
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    // insert data
    @RpcService
    @DAOMethod(sql = "select deptId from Department where name = :name and organId=1")
    public abstract int getIdByName(@DAOParam("name") String name);

    // insert data
    @RpcService
    @DAOMethod(sql = "select professionCode from Department where name = :name and organId=1")
    public abstract String getProfessionCodeByName(@DAOParam("name") String name);

    // insert data
    @RpcService
    @DAOMethod(sql = "select professionCode from Department where code = :code and organId=:organ")
    public abstract String getProfessionCodeById(@DAOParam("code") String code,
                                                 @DAOParam("organ") int organ);

    // insert data
    @RpcService
    @DAOMethod(sql = "select deptId from Department where code = :code and organId=:organ")
    public abstract int getIdByCode(@DAOParam("code") String code,
                                    @DAOParam("organ") int organ);

    @RpcService
    @DAOMethod
    public abstract Department getByCode(String code);

    @RpcService
    @DAOMethod(sql = "select name from Department where deptId = :id")
    public abstract String getNameById(@DAOParam("id") int deptId);

    @Override
    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(deptId,name) from Department order by deptId")
    public abstract List<DictionaryItem> findAllDictionaryItem(
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(deptId,name) from Department where deptId=:id")
    public abstract DictionaryItem getDictionaryItem(@DAOParam("id") Object key);

    @RpcService
    @DAOMethod
    public abstract List<Department> findByOrganId(Integer organId);

    @RpcService
    @DAOMethod(limit = 0,orderBy = " orderNum desc")
    public abstract List<Department> findAllByOrganId(Integer organId);

    @RpcService
    @DAOMethod(sql = "from Department where code = :code and organId=:organ and status=0")
    public abstract Department getByCodeAndOrgan(@DAOParam("code") String code,
                                                 @DAOParam("organ") int organ);

    @RpcService
    @DAOMethod(sql = "from Department where code = :code and organId=:organ")
    public abstract Department getByCodeAndOrganAndProfessionCode(@DAOParam("code") String code,
                                                 @DAOParam("organ") int organ);

    /**
     * 根据机构代码和专科编码获取Department
     *
     * @param organId
     * @param professionCode
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract List<Department> findByOrganIdAndProfessionCode(
            int organId, String professionCode);

    /**
     * 医院科室列表查询服务
     *
     * @param organId
     * @param professionCode
     * @return
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<Department> findDepartment(final Integer organId,
                                           final String professionCode) {
        HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "FROM Department WHERE professionCode like :professionCode AND organId=:organId and status=0 ORDER BY orderNum desc");
                Query q = ss.createQuery(hql);
                q.setParameter("organId", organId);
                q.setParameter("professionCode", professionCode + "%");
                List<Department> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Department>) action.getResult();
    }

    /**
     * 按机构查询一级专科目录-app
     *
     * @param organId
     * @return
     * @author hyj
     */
    @DAOMethod(sql = "select distinct SUBSTRING(professionCode,1,2) from Department where organId=:organId" )
    public abstract List<String> findProfessionCodeByOrganId(
            @DAOParam("organId") Integer organId);


    /**
     * 按住院机构查询一级专科目录-app
     *
     * @param organId
     * @return
     * @author hjh
     */
    @DAOMethod(sql = "select distinct SUBSTRING(professionCode,1,2) from Department where organId=:organId and inpatientEnable='1'" )
    public abstract List<String> findInProfessionCodeByOrganId(
            @DAOParam("organId") Integer organId);
    /**
     * 按机构查询一级专科目录(剔除无医生)-微信健康端
     *
     * @param organId
     * @return
     * @author hyj
     *
     * @Date 2016-12-08 10:45:11
     * @author zhangsl
     * 不剔除普通虚拟医生
     * 2016-01-19 zhangsl:修复普通挂号科室筛选条件bug
     */
    @DAOMethod(sql = "select distinct SUBSTRING(a.professionCode,1,2) from Department a,Employment e,Doctor r,Profession p where a.organId=:organId and p.key =SUBSTRING(a.professionCode,1,2) " +
            " AND a.deptId=e.department and e.doctorId=r.doctorId and r.status=1 and (IFNULL(r.idNumber,'')<>'' or r.teams=1 or (IFNULL(r.idNumber,'')='' and r.generalDoctor=1)) and a.status=0 order by p.orderNum DESC")
    public abstract List<String> findValidProfessionCodeByOrganId(
            @DAOParam("organId") Integer organId);


    /**
     * 按地区机构查询专科目录(剔除无医生)
     *
     * @param addrStr 地区
     * @param organIdStr 机构代码
     * @param professionCode 大专科代码（查询小专科时传入）
     * @return
     * @author zsl
     */
    @RpcService
    public List<Object> findValidDepartmentByProfessionCode(final String addrStr,final String organIdStr,final String professionCode) {
        final Integer organId;
        final String addr;
        try {
            organId =StringUtils.isNotBlank(organIdStr)?Integer.parseInt(organIdStr):null;
            addr=StringUtils.isBlank(addrStr)?"":addrStr;
        }catch (Exception e){
            throw new DAOException("organId is not valid!");
        }
        OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
        final List<Integer> organs=organDAO.findOrgansByUnitForHealth();
        if(StringUtils.isNotBlank(professionCode)) {
            HibernateStatelessResultAction<List<Object>> action = new AbstractHibernateStatelessResultAction<List<Object>>() {

                List<String> list = new ArrayList<>();
                List<Object> result = new ArrayList<Object>();
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql;
                    hql = new StringBuilder("select distinct r.profession from Doctor r,Organ o " +
                            "where r.profession like :professionCode " +
                            "and r.status=1 and ((r.idNumber is not null and r.idNumber<>'') or r.teams=1) " +
                            "and o.organId=r.organ and o.type=1 and o.status=1 and o.addrArea like :addr ");
                    if(organId!=null){
                        hql.append("and r.organ= :organId ");
                    }else if(organs!=null&&organs.size()>0){
                        hql.append("and (");
                        for(Integer organId:organs){
                            hql.append("r.organ=").append(organId).append(" or ");
                        }
                        hql=new StringBuilder(hql.substring(0,hql.length()-3)).append(") ");
                    }
                    hql.append("order by r.profession");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("professionCode", professionCode + "%");
                    q.setParameter("addr", addr + "%");
                    if(organId!=null){
                        q.setParameter("organId",organId);
                    }
                    list = (List<String>) q.list();
                    for (int i = 0; i < list.size(); i++) {
                        Map<String, String> map = new HashMap<String, String>();
                        try {
                            String code = list.get(i);
                            String professionName = DictionaryController.instance()
                                    .get("eh.base.dictionary.Profession")
                                    .getText(code);
                            //剔除一级专科
                            if (code.equals(professionCode) || code.equals(professionCode + "A")) {
                                continue;
                            }
                            map.put("professionCode", code);
                            map.put("professionName", professionName);
                            result.add(map);
                        } catch (ControllerException e) {
                            log.error("findValidDepartmentByProfessionCode() error:"+e);
                        }
                    }
                    setResult(result);
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action);
            return action.getResult();
        }else{
            HibernateStatelessResultAction<List<Object>> action = new AbstractHibernateStatelessResultAction<List<Object>>() {

                List<String> list = new ArrayList<>();
                List<Object> result = new ArrayList<Object>();
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql;
                    hql = new StringBuilder("select distinct SUBSTRING(r.profession,1,2) from Doctor r,Organ o " +
                            "where r.status=1 and ((r.idNumber is not null and r.idNumber<>'') or r.teams=1) " +
                            "and o.organId=r.organ and o.type=1 and o.status=1 and o.addrArea like :addr ");
                    if(organId!=null){
                        hql.append("and r.organ= :organId ");
                    }else if(organs!=null&&organs.size()>0){
                        hql.append("and (");
                        for(Integer organId:organs){
                            hql.append("r.organ=").append(organId).append(" or ");
                        }
                        hql=new StringBuilder(hql.substring(0,hql.length()-3)).append(") ");
                    }
                    hql.append("order by SUBSTRING(r.profession,1,2)");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("addr", addr + "%");
                    if(organId!=null){
                        q.setParameter("organId",organId);
                    }
                    list = (List<String>) q.list();
                    hql=new StringBuilder("select distinct r.profession from Doctor r,Organ o "+
                            "where r.profession='03A' AND r.status=1 and " +
                            "((r.idNumber is not null and r.idNumber<>'') or r.teams=1) " +
                            "and o.organId=r.organ and o.type=1 and o.status=1 and o.addrArea like :addr ");
                    if(organId!=null){
                        hql.append("and r.organ= :organId ");
                    }else if(organs!=null&&organs.size()>0){
                        hql.append("and (");
                        for(Integer organId:organs){
                            hql.append("r.organ=").append(organId).append(" or ");
                        }
                        hql=new StringBuilder(hql.substring(0,hql.length()-3)).append(") ");
                    }
                    Query q1=ss.createQuery(hql.toString());
                    q1.setParameter("addr", addr + "%");
                    if(organId!=null){
                        q1.setParameter("organId",organId);
                    }
                    if(q1.list().size()>0){
                        list.add(list.size()-1<0?0:list.size()-1,q1.list().get(0).toString());
                    }
                    for (int i = 0; i < list.size(); i++) {
                        Map<String, String> map = new HashMap<String, String>();
                        try {
                            String code = list.get(i);
                            String professionName = DictionaryController.instance()
                                    .get("eh.base.dictionary.Profession")
                                    .getText(code);
                            map.put("professionCode", code);
                            map.put("professionName", professionName);
                            result.add(map);
                        } catch (ControllerException e) {
                            log.error("findValidDepartmentByProfessionCode() error:"+e);
                        }
                    }
                    setResult(result);
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action);
            return action.getResult();
        }
    }

    /**
     * 查询
     * @param addrStr
     * @param organIdStr
     * @param professionCode
     * @param queryParam 扩展参数 mark == 2 在线续方 查询逻辑不同
     * @return
     */
    public List<Object> findValidDepartmentByProfessionCodeExt(String addrStr, String organIdStr,
                                                               final String professionCode, Map<String, Object> queryParam){
        Integer mark = MapValueUtil.getInteger(queryParam, "mark");
        Integer drugId = MapValueUtil.getInteger(queryParam, "drugId");
        //mark == 2 是在线续方的入口
        if(mark != null && 2 == mark){
            return findValidDepartmentByProfessionCodeForRecipe(addrStr, organIdStr, professionCode, drugId);
        }else{
            return findValidDepartmentByProfessionCode(addrStr, organIdStr, professionCode);
        }
    }

    /**
     * 在线续方 查找有效科室
     * @param addrStr
     * @param organIdStr
     * @param professionCode
     * @param drugId
     * @return
     */
    private List<Object> findValidDepartmentByProfessionCodeForRecipe(String addrStr, String organIdStr, final String professionCode, Integer drugId){
        final Integer organId;
        final String addr;
        try {
            organId =StringUtils.isNotBlank(organIdStr)?Integer.parseInt(organIdStr):null;
            addr=StringUtils.isBlank(addrStr)?"":addrStr;
        }catch (Exception e){
            throw new DAOException("organId is not valid!");
        }
        OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
        final List<Integer> organIds =organDAO.queryOragnIdsCanRecipe(addr, drugId);
        if(organIds == null || organIds.size() == 0){
            log.error("该公众号下没有可开处方的机构");
            return new ArrayList<>();
        }
        if(StringUtils.isNotBlank(professionCode)) {
            HibernateStatelessResultAction<List<Object>> action = new AbstractHibernateStatelessResultAction<List<Object>>() {

                List<String> list = new ArrayList<>();
                List<Object> result = new ArrayList<Object>();
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql;
                    hql = new StringBuilder("select distinct d.profession from Doctor d, ConsultSet c " +
                            "where d.profession like :professionCode and d.status=1 and d.teams = 0 and " +
                            "c.recipeConsultStatus = 1 and d.doctorId = c.doctorId and " +
                            "d.doctorId in (select DISTINCT e.doctorId " +
                            "from Employment e where e.organId in (:organIds) ");
                    if(organId !=null && organId > 0){
                        hql.append("and e.organId= :organId) ");
                    }else{
                        hql.append(") ");
                    }
                    hql.append("order by d.profession");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameterList("organIds", organIds);
                    if(organId !=null && organId > 0){
                        q.setParameter("organId",organId);
                    }
                    q.setParameter("professionCode", professionCode + "%");
                    list = (List<String>) q.list();
                    for (int i = 0; i < list.size(); i++) {
                        Map<String, String> map = new HashMap<String, String>();
                        try {
                            String code = list.get(i);
                            String professionName = DictionaryController.instance()
                                    .get("eh.base.dictionary.Profession")
                                    .getText(code);
                            //剔除一级专科
                            if (code.equals(professionCode) || code.equals(professionCode + "A")) {
                                continue;
                            }
                            map.put("professionCode", code);
                            map.put("professionName", professionName);
                            result.add(map);
                        } catch (ControllerException e) {
                            log.error("findValidDepartmentByProfessionCodeForRecipe() error:"+e);
                        }
                    }
                    setResult(result);
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action);
            return action.getResult();
        }else{
            HibernateStatelessResultAction<List<Object>> action = new AbstractHibernateStatelessResultAction<List<Object>>() {

                List<String> list = new ArrayList<>();
                List<Object> result = new ArrayList<Object>();
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql;
                    hql = new StringBuilder("select distinct SUBSTRING(d.profession,1,2) from Doctor d, ConsultSet c " +
                            "where d.status=1 and d.teams = 0 and d.doctorId = c.doctorId and c.recipeConsultStatus = 1 and " +
                            "d.doctorId in (select DISTINCT e.doctorId from Employment e where e.organId in (:organIds) ");
                    if(organId != null && organId > 0){
                        hql.append("and e.organId= :organId) ");
                    }else{
                        hql.append(") ");
                    }
                    hql.append("order by SUBSTRING(d.profession,1,2)");
                    Query q = ss.createQuery(hql.toString());
                    q.setParameterList("organIds", organIds);
                    if(organId != null && organId > 0){
                        q.setParameter("organId",organId);
                    }
                    list = (List<String>) q.list();
                    hql = new StringBuilder("select distinct d.profession from Doctor d, ConsultSet c " +
                            "where d.status=1 and d.teams = 0 and d.profession='03A' and d.doctorId = c.doctorId and " +
                            "c.recipeConsultStatus = 1 and d.doctorId in " +
                            "(select DISTINCT e.doctorId from Employment e where e.organId in (:organIds) ");
                    if(organId != null && organId > 0){
                        hql.append("and e.organId= :organId)");
                    }else{
                        hql.append(")");
                    }
                    Query q1 = ss.createQuery(hql.toString());
                    q1.setParameterList("organIds", organIds);
                    if(organId != null && organId > 0){
                        q1.setParameter("organId",organId);
                    }
                    if(q1.list().size()>0){
                        list.add(list.size()-1<0?0:list.size()-1,q1.list().get(0).toString());
                    }
                    for (int i = 0; i < list.size(); i++) {
                        Map<String, String> map = new HashMap<String, String>();
                        try {
                            String code = list.get(i);
                            String professionName = DictionaryController.instance()
                                    .get("eh.base.dictionary.Profession")
                                    .getText(code);
                            map.put("professionCode", code);
                            map.put("professionName", professionName);
                            result.add(map);
                        } catch (ControllerException e) {
                            log.error("findValidDepartmentByProfessionCodeForRecipe() error:"+e);
                        }
                    }
                    setResult(result);
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action);
            return action.getResult();
        }
    }

    /**
     * 前八门热门专科类别查询服务-健康端个性化
     *
     * 个性化orderType (0:按医生数量，1按咨询量)
     * @return
     * @author zsl
     *
     * @Date 2016-12-08 15:30:37
     * @author zhangsl
     * 热门专科剔除“其它”专科
     */
    @RpcService
    public List<Object> findHotProfession(){
        //final String orderType="1";
        Map<String, String> wxAppProperties = CurrentUserInfo.getCurrentWxProperties();
        ScratchableService scratchableService = AppContextHolder.getBean("eh.scratchableService", ScratchableService.class);
        if (null == wxAppProperties) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "wxApp is null or is not wxApp");
        }
        List<Scratchable> scratchableList = scratchableService.findHotDepartment();
        List<Object> result = new ArrayList<>();
        if (!ObjectUtils.isEmpty(scratchableList)) {
            for (Scratchable scratchable : scratchableList) {
                Map<String, String> map = new HashMap<String, String>();
                try {
                    String professionName = DictionaryController.instance()
                            .get("eh.base.dictionary.Profession")
                            .getText(scratchable.getBoxLink());

                    map.put("professionCode", scratchable.getBoxLink());
                    map.put("professionName", professionName);
                    result.add(map);
                } catch (ControllerException e) {
                    log.error("findHotProfession() error:" + e);
                }
            }
            return result;
        }
        final String orderType = wxAppProperties.get("orderType")==null?"1":wxAppProperties.get("orderType");
        List<Object[]> list = new ArrayList<Object[]>();

        // 2017-04-24 luf:将数据库读取改为缓存
        try {
            if ("0".equals(orderType)) {
                list = professionOrder.get("order");
            } else {
                list = professionOrder.get("other");
            }
        } catch (ExecutionException e) {
            log.error("professionOrder Error ====>orderType=" + orderType + "===>errormessage：" + e);
        }
        //log.info("当前热门"+list.size()+"专科统计："+ JSON.toJSONString(list));
        for (int i = 0; i < list.size(); i++) {
            Map<String, String> map = new HashMap<String, String>();
            try {
                String professionName = DictionaryController.instance()
                        .get("eh.base.dictionary.Profession")
                        .getText(list.get(i)[0].toString());

                map.put("professionCode", list.get(i)[0].toString());
                map.put("professionName", professionName);
                result.add(map);
            } catch (ControllerException e) {
                log.error("findHotProfession() error:" + e);
            }
        }
//                setResult(result);
//            }
//        };
//        HibernateSessionTemplate.instance().executeReadOnly(action);
//        return action.getResult();
        return result;
    }

    /**
     * 医院专科类别查询服务(剔除无医生)
     *
     * @param organId
     * @param deptType (1临床科室,2挂号科室)
     * @return
     * @author hyj
     */
    @RpcService
    public List<Object> findValidProfession(Integer organId, int deptType) {
        List<String> list = new ArrayList<String>();
        List<Object> result = new ArrayList<Object>();
        switch (deptType) {
            case 1:
                list = findValidProfessionCodeByOrganId(organId);
                break;
            case 2:
                AppointDepartDAO appointdepartdao = DAOFactory
                        .getDAO(AppointDepartDAO.class);
                list = appointdepartdao.findProfessionCodeByOrganId(organId);
                break;
        }
        for (int i = 0; i < list.size(); i++) {
            Map<String, String> map = new HashMap<String, String>();
            try {
                String professionName = DictionaryController.instance()
                        .get("eh.base.dictionary.Profession")
                        .getText(list.get(i));

                map.put("professionCode", list.get(i));
                map.put("professionName", professionName);
                result.add(map);
            } catch (ControllerException e) {
                log.error("findValidProfession() error:"+e);
            }
        }
        return result;
    }

    /**
     * 医院专科类别查询服务
     *
     * @param organId
     * @param deptType (1临床科室,2挂号科室)
     *
     * @return
     * @author hyj
     */
    @RpcService
    public List<Object> findValidProfessionForPC(Integer organId, int deptType) {
        List<String> list = new ArrayList<String>();
        List<Object> result = new ArrayList<Object>();
        switch (deptType) {
            case 1:
                list = findProfessionCodeByOrganId(organId);
                break;
            case 2:
                AppointDepartDAO appointdepartdao = DAOFactory
                        .getDAO(AppointDepartDAO.class);
                list = appointdepartdao.findProfessionCodeByOrganId(organId);
                break;
        }
        for (int i = 0; i < list.size(); i++) {
            Map<String, String> map = new HashMap<String, String>();
            try {
                String professionName = DictionaryController.instance()
                        .get("eh.base.dictionary.Profession")
                        .getText(list.get(i));

                map.put("professionCode", list.get(i));
                map.put("professionName", professionName);
                result.add(map);
            } catch (ControllerException e) {
                log.error("findValidProfessionForPC() error:"+e);
            }
        }
        return result;
    }

    /**
     * 医院住院专科类别查询服务
     *
     * @param organId
     * @param deptType (1临床科室,2挂号科室)
     *
     * @return
     * @author hjh
     */
    @RpcService
    public List<Object> findValidInHpProfessionForPC(Integer organId, int deptType) {
        List<String> list = new ArrayList<String>();
        List<Object> result = new ArrayList<Object>();
        list = findInProfessionCodeByOrganId(organId);
        for (int i = 0; i < list.size(); i++) {
            Map<String, String> map = new HashMap<String, String>();
            try {
                String professionName = DictionaryController.instance()
                        .get("eh.base.dictionary.Profession")
                        .getText(list.get(i));

                map.put("professionCode", list.get(i));
                map.put("professionName", professionName);
                result.add(map);
            } catch (ControllerException e) {
                log.error("findValidProfessionForPC() error:"+e);
            }
        }
        return result;
    }

    /**
     * 医院有效科室查询服务(剔除无医生)
     *
     * @param organId
     * @param professionCode
     * @param bussType       (0全部，1转诊，2会诊)
     * @return
     * @author hyj
     */
    @RpcService
    public List<Department> findValidDepartment(final Integer organId,
                                                final String professionCode, final int bussType) {
        HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {
            List<Department> list = new ArrayList<Department>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                switch (bussType) {
                    case 0:
                        // 2016-12-08 zhangsl:患者端预约中按照机构、科室找医生需要添加普通挂号科室医生，添加and d.name like \"%普通%\"
                        // 2016-01-19 zhangsl:修复普通挂号科室筛选条件bug
                        hql = new StringBuilder("select distinct d from Department d,Employment e,Doctor r where d.organId=e.organId" +
                                " and d.deptId=e.department and d.professionCode like :professionCode and d.organId=:organId" +
                                " and d.status=0 and r.doctorId=e.doctorId and r.status=1 and (IFNULL(r.idNumber,:empty)<>:empty or r.teams=1 or (IFNULL(r.idNumber,:empty)=:empty and r.generalDoctor=1))");
                        hql.append(" order by d.orderNum desc ");//2017-03-08 houxr:行政科室 维护排序
                        Query q = ss.createQuery(hql.toString());
                        q.setParameter("professionCode", professionCode + "%");
                        q.setParameter("organId", organId);
                        q.setParameter("empty","");
                        list = (List<Department>) q.list();
                        break;
                    case 1:
                        hql = new StringBuilder(
                                "select distinct d from Department d,Employment e,ConsultSet c,Doctor r where d.organId=e.organId" +
                                        " and d.deptId=e.department and d.professionCode like :professionCode and d.organId=:organId" +
                                        " and e.doctorId=c.doctorId and c.transferStatus=1 and d.status=0 and r.doctorId=e.doctorId and r.status=1");
                        hql.append(" order by d.orderNum desc ");//2017-03-08 houxr:行政科室设置排序
                        Query q1 = ss.createQuery(hql.toString());
                        q1.setParameter("professionCode", professionCode + "%");
                        q1.setParameter("organId", organId);
                        list = (List<Department>) q1.list();
                        break;
                    case 2:
                        hql = new StringBuilder(
                                "select distinct d from Department d,Employment e,ConsultSet c,Doctor r where d.organId=e.organId" +
                                        " and d.deptId=e.department and d.professionCode like :professionCode and d.organId=:organId" +
                                        " and e.doctorId=c.doctorId and c.meetClinicStatus=1 and d.status=0 and r.doctorId=e.doctorId and r.status=1");
                        hql.append(" order by d.orderNum desc ");//2017-03-08 houxr:行政科室设置排序
                        Query q2 = ss.createQuery(hql.toString());
                        q2.setParameter("professionCode", professionCode + "%");
                        q2.setParameter("organId", organId);
                        list = (List<Department>) q2.list();
                        break;
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Department>) action.getResult();

    }

    @RpcService
    @DAOMethod
    public abstract Department getById(int deptId);

    /**
     * 科室新增服务
     *
     * @param d
     * @author hyj
     */
    @RpcService
    public Department addDepartment(Department d) {
        if (d.getOrganId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required");
        }
        if (StringUtils.isEmpty(d.getCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "code is required");
        }
        if (StringUtils.isEmpty(d.getProfessionCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "professionCode is required");
        }
        Department Department = this.getByCodeAndOrgan(d.getCode(), d.getOrganId());
        if (Department != null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该科室已存在，请勿重复添加");
        }
        Department target = save(d);
        Integer key = target.getDeptId();
        DictionaryItem item = getDictionaryItem(key);
        //this.fireEvent(new CreateDAOEvent(key, item));
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_CREATE, "eh.base.dictionary.Depart");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error("addDepartment() error:"+e);
        }
        BusActionLogService.recordBusinessLog("科室管理",key+"","Department","新增科室【"+key+"--"+d.getName()+"】");
        return target;
    }

    /**
     * 更新科室服务
     *
     * @param department
     * @return
     * @author yaozh
     */
    @RpcService
    public Department updateDepartment(Department department) {
        Integer deptId = department.getDeptId();
        if (deptId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "deptId is required!");
        }
        Department oldDept = getById(deptId);
        if (oldDept == null) {
            throw new DAOException(609, "找不到科室");
        }
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        if(department.getStatus().intValue()==1&&employmentDAO.getCountByDepartment(deptId)>0){
            throw new DAOException(609, "该科室下已经存在医生，不能被注销");
        }

        //判断科室编码不能重复
        List<Department> listDept = findDeptByCodeAndOrganId(department.getCode(), department.getOrganId());
        if (listDept.size() > 1) {
            throw new DAOException(609, "科室编码不能重复");
        } else {
            BeanUtils.map(department, oldDept);
            update(oldDept);
            Integer key = oldDept.getDeptId();
            DictionaryItem item = getDictionaryItem(key);
            //this.fireEvent(new UpdateDAOEvent(key, item));
            NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_UPDATE, "eh.base.dictionary.Depart");
            msg.setLastModify(System.currentTimeMillis());
            msg.addUpdatedItems(item);
            try {
                DictionaryController.instance().getUpdater().notifyMessage(msg);
            } catch (ControllerException e) {
                log.error("updateDepartment() error:"+e);
            }
        }
        BusActionLogService.recordBusinessLog("科室管理",deptId+"","Department","修改科室【"+deptId+"--"+department.getName()+"】");
        return oldDept;
    }

    /**
     * 医院科室列表分页查询服务
     *
     * @param organId
     * @param professionCode
     * @return
     * @author hyj
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<Department> findDepartmentWithPage(final Integer organId,
                                                   final String professionCode, final int startPage) {
        HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "FROM Department WHERE professionCode like :professionCode AND organId=:organId ORDER BY orderNum asc");
                Query q = ss.createQuery(hql);
                q.setParameter("organId", organId);
                q.setParameter("professionCode", professionCode + "%");
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                List<Department> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Department>) action.getResult();
    }

    /**
     * Title: 根据机构id获取其所有科室，该接口为桐乡医院号源导入 时调用
     *
     * @param organId 桐乡医院机构id
     * @return List<TxDepart>
     * @author zhangjr
     * @date 2015-10-10
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<TxDepart> findTxDepartByOrganId(final Integer organId) {
        if (organId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        HibernateStatelessResultAction<List<TxDepart>> action = new AbstractHibernateStatelessResultAction<List<TxDepart>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.tx.TxDepart(d.deptId,d.code,d.name) from Department d where d.organId=:organId ";
                Query query = ss.createQuery(hql);
                query.setParameter("organId", organId);
                List<TxDepart> objArrList = query.list();
                setResult(objArrList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @RpcService
    @DAOMethod(sql = " from Department where name = :name and organId=:organId")
    public abstract Department getByNameAndOrgan(@DAOParam("name") String name,
                                                 @DAOParam("organId") int organId);

    /**
     * @param name
     * @param organId
     * @return
     * @date 2016-08-01 luf 添加 and status=0,解决get时报错
     */
    @DAOMethod(sql = " from Department where name = :name and organId=:organId and status=0")
    public abstract Department getEffByNameAndOrgan(@DAOParam("name") String name,
                                                    @DAOParam("organId") int organId);

    /**
     * @param @return
     * @return Department
     * @throws
     * @Title: getDeptByProfessionIdAndOrgan
     * @Description: TODO根据专科编码和机构编码获得科室编码，若数据库无则插入并返回
     * @author AngryKitty
     * @Date 2015-11-17下午3:42:32
     */
    @RpcService
    public Department getDeptByProfessionIdAndOrgan(String profession, int organ) {
        String proText = ""; // 专科名称
        try {
            proText = DictionaryController.instance()
                    .get("eh.base.dictionary.Profession").getText(profession);
        } catch (ControllerException e) {
            log.error("getDeptByProfessionIdAndOrgan() error:"+e);
        }
        Department dept = this.getEffByNameAndOrgan(proText, organ);
        if (dept == null) {
            dept = new Department();
            dept.setOrganId(organ);
            dept.setCode("ZC" + profession);
            dept.setName(proText);
            dept.setProfessionCode(profession);
            dept.setOrderNum(1);
            dept.setClinicEnable(1);
            dept.setInpatientEnable(0);
            dept.setStatus(0);
            dept = this.addDepartment(dept);
        }
        return dept;
    }

    /**
     * @param @param  profession
     * @param @param  professionText
     * @param @param  organ
     * @param @return
     * @return Department
     * @throws
     * @Title: saveDeptByProfessionAndOrgan
     * @Description: TODO根据专科信息保存科室
     * @author AngryKitty
     * @Date 2015-11-17下午4:20:38
     */
    public Department saveDeptByProfessionAndOrgan(String profession,
                                                   String professionText, int organ) {
        Department dept = new Department();
        dept.setOrganId(organ);
        dept.setCode("ZC" + profession);
        dept.setName(professionText);
        dept.setProfessionCode(profession);
        dept.setOrderNum(1);
        dept.setClinicEnable(1);
        dept.setInpatientEnable(0);
        dept.setStatus(0);
        return this.addDepartment(dept);
    }

    /**
     * @param @param  organId 机构编码
     * @param @return
     * @return List<Department>
     * @throws
     * @Class eh.base.dao.DepartmentDAO.java
     * @Title: getDepts
     * @Description: TODO
     * @author AngryKitty
     * @Date 2016-1-6上午10:35:41
     */
    @SuppressWarnings("unchecked")
    @RpcService
    public List<Department> findDepts(final Integer organId) {

        if (organId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {

            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select new eh.entity.base.Department(d.deptId,d.name) from Department d where d.organId=:organId ";
                Query query = ss.createQuery(hql);
                query.setParameter("organId", organId);
                List<Department> depts = query.list();
                setResult(depts);
            }

        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 医院有效科室查询服务(原findValidDepartment)-原生端
     * <p>
     * 转诊入口添加开通转诊或者有号限制
     * <p>
     * eh.base.dao
     *
     * @param organId
     * @param professionCode
     * @param bussType       (0全部，1转诊，2会诊)
     * @return List<Department>
     * @author luf 2016-3-10
     */
    @RpcService
    public List<Department> findValidDepartmentTransferWithApp(
            final Integer organId, final String professionCode,
            final int bussType) {
        HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {
            List<Department> list = new ArrayList<Department>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                switch (bussType) {
                    case 0:
                        hql = new StringBuilder(
                                "select distinct d from Department d,Employment e where d.organId=e.organId and d.deptId=e.department and d.professionCode like :professionCode and d.organId=:organId and d.status=0");
                        Query q = ss.createQuery(hql.toString());
                        q.setParameter("professionCode", professionCode + "%");
                        q.setParameter("organId", organId);
                        list = (List<Department>) q.list();
                        break;
                    case 1:
                            hql = new StringBuilder(
                                    "select distinct d from Department d,Employment e,ConsultSet c,Doctor do where "
                                            + "d.organId=e.organId and d.deptId=e.department and d.professionCode like :professionCode "
                                            + "and d.organId=:organId and e.doctorId=c.doctorId and do.doctorId=e.doctorId and "
                                            + "(c.transferStatus=1 or do.haveAppoint=1) and d.status=0 ");
                        Query q1 = ss.createQuery(hql.toString());
                        q1.setParameter("professionCode", professionCode + "%");
                        q1.setParameter("organId", organId);
                        list = (List<Department>) q1.list();
                        break;
                    case 2:
                        hql = new StringBuilder(
                                "select distinct d from Department d,Employment e,ConsultSet c where d.organId=e.organId and d.deptId=e.department and d.professionCode like :professionCode and d.organId=:organId and e.doctorId=c.doctorId and c.meetClinicStatus=1 and d.status=0");
                        Query q2 = ss.createQuery(hql.toString());
                        q2.setParameter("professionCode", professionCode + "%");
                        q2.setParameter("organId", organId);
                        list = (List<Department>) q2.list();
                        break;
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Department>) action.getResult();
    }

    /**
     * 医院住院有效科室查询服务
     * eh.base.dao
     *
     * @param organId
     * @param professionCode
     * @param
     * @return List<Department>
     * @author hjh
     */
    @RpcService
    public List<Department> findValidInHpDepartmentTransferWithApp(
            final Integer organId, final String professionCode,
            final int bussType) {
        HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {
            List<Department> list = new ArrayList<Department>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql;
                switch (bussType) {
                    case 0:
                        hql = new StringBuilder(
                                "select distinct d from Department d,Employment e where d.organId=e.organId and d.deptId=e.department and d.professionCode like :professionCode and d.organId=:organId and d.status=0");
                        Query q = ss.createQuery(hql.toString());
                        q.setParameter("professionCode", professionCode + "%");
                        q.setParameter("organId", organId);
                        list = (List<Department>) q.list();
                        break;
                    case 1:

                            hql = new StringBuilder(
                                    "select distinct d from Department d,Employment e,ConsultSet c,Doctor do where "
                                            + "d.organId=e.organId and d.deptId=e.department and d.professionCode like :professionCode "
                                            + "and d.organId=:organId and e.doctorId=c.doctorId and do.doctorId=e.doctorId and "
                                            + "(c.transferStatus=1 or do.haveAppoint=1) and d.status=0 and d.inpatientEnable=1");
                        Query q1 = ss.createQuery(hql.toString());
                        q1.setParameter("professionCode", professionCode + "%");
                        q1.setParameter("organId", organId);
                        list = (List<Department>) q1.list();
                        break;
                    case 2:
                        hql = new StringBuilder(
                                "select distinct d from Department d,Employment e,ConsultSet c where d.organId=e.organId and d.deptId=e.department and d.professionCode like :professionCode and d.organId=:organId and e.doctorId=c.doctorId and c.meetClinicStatus=1 and d.status=0");
                        Query q2 = ss.createQuery(hql.toString());
                        q2.setParameter("professionCode", professionCode + "%");
                        q2.setParameter("organId", organId);
                        list = (List<Department>) q2.list();
                        break;
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Department>) action.getResult();
    }


    /**
     * 查找organId对应的所有department code
     *
     * @param organId
     * @return
     */
    @RpcService
    public List<Department> findDeptByCodeAndOrganId(final String code, final Integer organId) {
        HibernateStatelessResultAction<List<Department>> action = new AbstractHibernateStatelessResultAction<List<Department>>() {
            List<Department> list = new ArrayList<Department>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "from Department d where d.code=:code and d.organId=:organId and d.status=0");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("code", code);
                q.setParameter("organId", organId);
                list = (List<Department>) q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Department>) action.getResult();
    }

    @RpcService
    public String registerDepart(Department d) {
        try {
            d = this.addDepartment(d);
            if (d == null) {
                return "error";
            }
            AppointDepartDAO appointDepartDao = DAOFactory.getDAO(AppointDepartDAO.class);
            AppointDepart appointDepart = new AppointDepart();
            appointDepart.setOrganId(d.getOrganId());
            appointDepart.setAppointDepartCode(d.getCode());
            appointDepart.setAppointDepartName(d.getName());
            appointDepart.setProfessionCode("98");
//			appointDepart.setProfessionCode(dept.getCode());
            appointDepart.setCreateTime(new Date());
            appointDepart.setAppointDepartId(d.getDeptId());
//			appointDepart.setRemarks();//科室介绍
            appointDepartDao.regAppointDepartment(appointDepart);
            return "";
        } catch (Exception e) {
            log.error("registerDepart() error:"+e);
            return e.getMessage();
        }
    }
}