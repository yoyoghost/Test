package eh.bus.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.CheckItemDAO;
import eh.base.dao.OrganCheckItemDAO;
import eh.base.dao.OrganDAO;
import eh.base.dao.UnitOpauthorizeDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.CheckItem;
import eh.entity.base.Organ;
import eh.entity.base.OrganCheckItem;
import eh.entity.base.UnitOpauthorize;
import eh.entity.bus.*;
import eh.utils.DateConversion;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.joda.time.LocalDate;

import java.text.SimpleDateFormat;
import java.util.*;

public abstract class CheckSourceDAO extends
        HibernateSupportDelegateDAO<CheckSource> {

    private static final Log logger = LogFactory.getLog(CheckSourceDAO.class);

    private static long saveTime = 2 * 60 * 1000;

    public CheckSourceDAO() {
        super();
        this.setEntityName(CheckSource.class.getName());
        this.setKeyField("chkSourceId");
    }

    @RpcService
    @DAOMethod
    public abstract CheckSource getByChkSourceId(Integer chkSourceId);

    @RpcService
    public void findChecksourceList() {

        @SuppressWarnings("rawtypes")
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("");
                Query query = null;

                hql = new StringBuilder("from BusChecksource where ");//
                query = ss.createQuery(hql.toString());

                query.setInteger("doctorId", 1);
                // query.setTimestamp("workDate", workDate);

                @SuppressWarnings("unused")
                List<AppointSource> temp = query.list();

            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

    }

    @DAOMethod(orderBy = "workDate,workType", sql = "select c,sum(sourceNum-usedNum) From CheckSource c where checkAppointId=:checkAppointId and startTime>=now() and stopFlag=0 and lockDoctor is null group by workDate,workType")
    public abstract List<Object[]> findGroupByCheckAppointId(
            @DAOParam("checkAppointId") Integer checkAppointId);

    /**
     * 号源服务 后面将废弃
     *
     * @param checkItemId 检查项目序号
     * @return List<Object>
     */
    @RpcService
    public List<Object> findSourcesByCheckItemId(Integer checkItemId,
                                                 String addrArea) {
        OrganCheckItemDAO organItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        CheckItemDAO itemDAO = DAOFactory.getDAO(CheckItemDAO.class);
        CheckItem baseItem = itemDAO.get(checkItemId);
        String bodyTxt = null;
        String examinationTypeName = null;
        if (baseItem != null) {
            String bodyDic = baseItem.getCheckBody();
            String checkType = baseItem.getCheckClass();
            try {
                bodyTxt = DictionaryController.instance().get("eh.base.dictionary.Body").getText(bodyDic);
                examinationTypeName = DictionaryController.instance().get("eh.base.dictionary.CheckClass").getText(checkType);
            } catch (ControllerException e) {
                logger.error(e);
            }
        } else {
            throw new DAOException(609, "检查项目不存在！");
        }
        CheckAppointItemDAO appointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Object> results = new ArrayList<Object>();
        List<OrganCheckItem> items = organItemDAO.findByTwoConnect(checkItemId, addrArea);
        for (OrganCheckItem item : items) {
            item.setBody(baseItem.getCheckBody());
            item.setBodyText(bodyTxt);
            item.setCheckType(baseItem.getCheckClass());
            item.setExaminationTypeName(examinationTypeName);
            HashMap<String, Object> map = new HashMap<String, Object>();
            Integer checkAppointId = item.getCheckAppointId();
            Integer organId = item.getOrganId();
            if (checkAppointId == null) {
                continue;
            }
            if (organId == null) {
                continue;
            }
            Organ organ = organDAO.get(organId);
            CheckAppointItem appointItem = appointItemDAO.get(checkAppointId);
            Integer status = appointItem.getStatus();
            if (status == null || status.intValue() == 0) {
                continue;
            }
            List<Object[]> sourcesAndCount = this.findGroupByCheckAppointId(checkAppointId);
            if (sourcesAndCount == null || sourcesAndCount.size() == 0) {
                continue;
            }
            map.put("organCheckItem", item);
            map.put("organ", organ);
            map.put("checkAppointItem", appointItem);
            map.put("checkSourcesAndCount", sourcesAndCount);
            results.add(map);
        }
        return results;
    }

    /**
     * 号源服务 3.1版本开始使用
     *
     * @param checkItemId 检查项目序号
     * @return List<Object>
     */
    @RpcService
    public List<Object> findSourcesByCheckItemIdfor31(Integer checkItemId, String addrArea, Integer organid) {
        OrganCheckItemDAO organItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        CheckItemDAO itemDAO = DAOFactory.getDAO(CheckItemDAO.class);
        UnitOpauthorizeDAO unitOpauthorizeDAO = DAOFactory.getDAO(UnitOpauthorizeDAO.class);

        CheckItem baseItem = itemDAO.get(checkItemId);
        String bodyTxt = null;
        String examinationTypeName = null;
        if (baseItem != null) {
            String bodyDic = baseItem.getCheckBody();
            String checkType = baseItem.getCheckClass();
            try {
                bodyTxt = DictionaryController.instance().get("eh.base.dictionary.Body").getText(bodyDic);
                examinationTypeName = DictionaryController.instance().get("eh.base.dictionary.CheckClass").getText(checkType);
            } catch (ControllerException e) {
               logger.error(e);
            }
        } else {
            throw new DAOException(609, "检查项目不存在！");
        }
        CheckAppointItemDAO appointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Object> results = new ArrayList<Object>();
        List<OrganCheckItem> items = organItemDAO.findByTwoConnect(checkItemId, addrArea);
        for (OrganCheckItem item : items) {
            //获取该机构授权的医院
            List<UnitOpauthorize> organList = unitOpauthorizeDAO.findListByAccreditOrgan(organid);
            logger.info("findSourcesByCheckItemIdfor31 传入机构授权医院:" + JSONUtils.toString(organList));
            if (item.getOrganId().intValue() != organid.intValue() && !isContain(organList, item.getOrganId())) {
                continue;
            }
            item.setBody(baseItem.getCheckBody());
            item.setBodyText(bodyTxt);
            item.setCheckType(baseItem.getCheckClass());
            item.setExaminationTypeName(examinationTypeName);
            HashMap<String, Object> map = new HashMap<String, Object>();
            Integer checkAppointId = item.getCheckAppointId();
            Integer organId = item.getOrganId();
            String extra = item.getExtra();
            if (checkAppointId == null) {
                continue;
            }
            if (organId == null) {
                continue;
            }
            Organ organ = organDAO.get(organId);
            CheckAppointItem appointItem = appointItemDAO.get(checkAppointId);
            Integer status = appointItem.getStatus();
            if (status == null || status.intValue() == 0) {
                continue;
            }
            List<Object[]> sourcesAndCount = this.findGroupByCheckAppointId(checkAppointId);
            List res = new ArrayList<>();
            //通过预约检查提前时间返回医技检查数据
            if (!StringUtils.isEmpty(extra)) {
                for (Object[] os : sourcesAndCount) {
                    CheckSource cs = (CheckSource) os[0];
                    Date workDate = cs.getWorkDate();
                    Date toDay = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
                    SimpleDateFormat sdf = new SimpleDateFormat("E");
                    Date dt = DateConversion.getDateAftXDays(toDay, Integer.valueOf(extra));
                    String toweek = sdf.format(dt);
                    if (!"星期六".equals(toweek) && !"星期日".equals(toweek)) {
                        if (dt.compareTo(workDate) == 1 || Integer.valueOf(extra) < 0) {
                        }else
                            res.add(os);
                    }
                }
            }else{
                res = sourcesAndCount;
            }
            if(res==null||res.size()==0){
                continue;
            }
            map.put("organCheckItem", item);
            map.put("organ", organ);
            map.put("checkAppointItem", appointItem);
            map.put("checkSourcesAndCount", res);
            results.add(map);
        }
        return results;
    }

    /**
     * 检查项目是否有机构可约 (只进行授权关系检查)
     *
     * @param checkItemId
     * @param organid
     * @return true:不为空
     */
    public boolean checkSourcesByCheckItemIdIsNotEmpty(Integer checkItemId, Integer organid) {
        OrganCheckItemDAO organItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        UnitOpauthorizeDAO unitOpauthorizeDAO = DAOFactory.getDAO(UnitOpauthorizeDAO.class);

        List<OrganCheckItem> items = organItemDAO.findByCheckItemId(checkItemId);
        boolean isNotEmpty = false;
        for (OrganCheckItem item : items) {
            //获取该机构授权的医院
            List<Integer> organList = unitOpauthorizeDAO.findByAccreditOrgan(organid);
            logger.info("checkSourcesByCheckItemIdIsNotEmpty 项目[" + item.getCheckItemId() + "]:" + item.getCheckItemName() + "，传入机构授权医院:" + JSONUtils.toString(organList));
            if (null == organList) {
                organList = new ArrayList<>(0);
            }
            if (item.getOrganId().intValue() != organid.intValue() && !organList.contains(item.getOrganId())) {
                continue;
            }

            isNotEmpty = true;
            if (isNotEmpty) {
                break;
            }
        }

        return isNotEmpty;
    }

    /**
     * 号源服务--原生 android
     *
     * @param checkItemId 检查项目序号
     * @return List<Object>
     */
    @RpcService
    public List<Object> findSourcesByCheckItemId2(Integer checkItemId,
                                                  String addrArea) {
        OrganCheckItemDAO itemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        CheckAppointItemDAO appointItemDAO = DAOFactory
                .getDAO(CheckAppointItemDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Object> results = new ArrayList<Object>();
        List<OrganCheckItem> items = itemDAO.findByTwoConnect(checkItemId,
                addrArea);
        for (OrganCheckItem item : items) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            Integer checkAppointId = item.getCheckAppointId();
            Integer organId = item.getOrganId();
            String extra = item.getExtra();
            if (checkAppointId == null) {
                continue;
            }
            if (organId == null) {
                continue;
            }
            Organ organ = organDAO.get(organId);
            CheckAppointItem appointItem = appointItemDAO.get(checkAppointId);
            Integer status = appointItem.getStatus();
            if (status == null || status.intValue() == 0) {
                continue;
            }
            List<Object[]> sourcesAndCount = this
                    .findGroupByCheckAppointId(checkAppointId);
            List<CheckSource> css = new ArrayList<CheckSource>();
            for (Object[] os : sourcesAndCount) {
                CheckSource cs = (CheckSource) os[0];
                //通过预约检查提前时间返回医技检查数据
                if (!StringUtils.isEmpty(extra)) {
                    Date workDate = cs.getWorkDate();
                    Date toDay = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
                    SimpleDateFormat sdf = new SimpleDateFormat("E");
                    Date dt = DateConversion.getDateAftXDays(toDay, Integer.valueOf(extra));
                    String toweek = sdf.format(dt);
                    if (!"星期六".equals(toweek) && !"星期日".equals(toweek)) {
                        if (dt.compareTo(workDate) == 1 || Integer.valueOf(extra) < 0) {
                            continue;
                        }
                        cs.setSum((Long) os[1]);
                        css.add(cs);
                    }
                }
            }
            map.put("organCheckItem", item);
            map.put("organ", organ);
            map.put("checkAppointItem", appointItem);
            map.put("checkSources", css);
            results.add(map);
        }
        return results;
    }

    /**
     * 号源服务--原生  android
     *
     * @param checkItemId 检查项目序号
     * @return List<Object>
     */
    @RpcService
    public List<Object> findSourcesByCheckItemId2for31(Integer checkItemId, String addrArea, Integer organid) {
        OrganCheckItemDAO itemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        CheckAppointItemDAO appointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        UnitOpauthorizeDAO unitOpauthorizeDAO = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<Object> results = new ArrayList<Object>();
        List<OrganCheckItem> items = itemDAO.findByTwoConnect(checkItemId, addrArea);
        for (OrganCheckItem item : items) {
            //获取该机构授权的医院
            List<UnitOpauthorize> organList = unitOpauthorizeDAO.findListByAccreditOrgan(organid);
            if (!isContain(organList, item.getOrganId()) && item.getOrganId().intValue() != organid.intValue()) {
                continue;
            }
            HashMap<String, Object> map = new HashMap<String, Object>();
            Integer checkAppointId = item.getCheckAppointId();
            Integer organId = item.getOrganId();
            String extra = item.getExtra();
            if (checkAppointId == null) {
                continue;
            }
            if (organId == null) {
                continue;
            }
            Organ organ = organDAO.get(organId);
            CheckAppointItem appointItem = appointItemDAO.get(checkAppointId);
            Integer status = appointItem.getStatus();
            if (status == null || status.intValue() == 0) {
                continue;
            }
            List<Object[]> sourcesAndCount = this
                    .findGroupByCheckAppointId(checkAppointId);
            List<CheckSource> css = new ArrayList<CheckSource>();
            for (Object[] os : sourcesAndCount) {
                CheckSource cs = (CheckSource) os[0];
                cs.setSum((Long) os[1]);
                //通过预约检查提前时间返回医技检查数据
                if (!StringUtils.isEmpty(extra)) {
                    Date workDate = cs.getWorkDate();
                    Date toDay = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
                    SimpleDateFormat sdf = new SimpleDateFormat("E");
                    Date dt = DateConversion.getDateAftXDays(toDay, Integer.valueOf(extra));
                    String toweek = sdf.format(dt);
                    if (!"星期六".equals(toweek) && !"星期日".equals(toweek)) {
                        if (dt.compareTo(workDate) == 1 || Integer.valueOf(extra) < 0) {
                            continue;
                        }
                        css.add(cs);
                    }
                }
            }
            map.put("organCheckItem", item);
            map.put("organ", organ);
            map.put("checkAppointItem", appointItem);
            map.put("checkSources", css);
            results.add(map);
        }
        return results;
    }

    private boolean isContain(List<UnitOpauthorize> organList, Integer organId) {
        for (UnitOpauthorize u : organList) {
            if (u.getOrganId().intValue() == organId.intValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 详细号源列表
     *
     * @param workType       值班类别
     * @param workDate       工作日期
     * @param checkAppointId 预约项目序号
     * @return List<CheckSource>
     */
    @RpcService
    @DAOMethod(orderBy = "startTime", sql = "from CheckSource where workType=:workType and workDate=:workDate and checkAppointId=:checkAppointId and stopFlag=0 and (sourceNum-usedNum)>0 and startTime > NOW()")
    public abstract List<CheckSource> findByThree(
            @DAOParam("workType") Integer workType,
            @DAOParam("workDate") Date workDate,
            @DAOParam("checkAppointId") Integer checkAppointId);

    /**
     * 更新号源已用数量服务--zxq
     *
     * @param usedNum
     */
    @RpcService
    @DAOMethod(sql = "update CheckSource set usedNum=:usedNum,orderNum=:orderNum where chkSourceId=:chkSourceId")
    public abstract void updateUsedNum(@DAOParam("usedNum") int usedNum,
                                       @DAOParam("orderNum") int orderNum,
                                       @DAOParam("chkSourceId") int chkSourceId);

    /**
     * 添加号源锁
     *
     * @param chkSourceId 检查号源序号
     * @param doctor      当前医生内码
     * @return Boolean
     */
    @RpcService
    public Boolean lockCheckSource(int chkSourceId, int doctor) {
        CheckSource cs = this.get(chkSourceId);
        if (cs == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CheckSource is required!");
        }
        Integer lockDoc = cs.getLockDoctor();
        if (lockDoc != null && cs.getLockTime() != null) {
            long now = (new Date()).getTime();
            long lock = cs.getLockTime().getTime();
            if ((now - lock) <= saveTime && doctor != lockDoc) {
                throw new DAOException(609, "啊哦！这个时段刚刚被约走了...");
            }
        }
        cs.setLockDoctor(doctor);
        cs.setLockTime(new Date());
        this.update(cs);
        return true;
    }

    /**
     * 解锁号源
     *
     * @param chkSourceId 检查号源序号
     * @param doctor      当前医生内码
     * @return Boolean
     */
    @RpcService
    public Boolean unlockCheckSource(int chkSourceId, Integer doctor) {
        CheckSource cs = this.get(chkSourceId);
        if (cs == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CheckSource is required!");
        }
        if (cs.getLockDoctor() != null && cs.getLockDoctor().intValue() == doctor.intValue()) {
            cs.setLockDoctor(null);
            cs.setLockTime(null);
            this.update(cs);
            return true;
        }
        return false;
    }

    /**
     * 定时解锁超时号源
     *
     * @return
     * @throws Exception
     */
    @RpcService
    public Integer unlockOverTime() throws Exception {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "update CheckSource set lockDoctor=null,lockTime=null where "
                        + "TIMESTAMPDIFF(MINUTE,lockTime,now())>=2 and (sourceNum-usedNum)>0";
                Query q = ss.createQuery(hql);
                setResult(q.executeUpdate());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


    /**
     * 根据机构检查项目定时增加号源
     *
     * @return
     * @throws Exception
     */
    @RpcService
    public void autoCreateChecksourceByOrganCheckItem() {
        Date today = DateConversion.getDateAftXDays(new Date(), 7);
        Calendar cal = Calendar.getInstance();
        cal.setTime(today);
        int w = cal.get(Calendar.DAY_OF_WEEK);
        if (w == 7 || w == 1) {//邵逸夫周末检查项目没有号源
            return;
        }
        Date workdate = DateConversion.getFormatDate(today, "yyyy-MM-dd");
        String workdate_str = DateConversion.getDateFormatter(today, "yyyy-MM-dd");

        Integer organid = 1;
        OrganCheckItemDAO organItemDAO = DAOFactory.getDAO(OrganCheckItemDAO.class);
        CheckSourceDAO checkSourceDAO = DAOFactory.getDAO(CheckSourceDAO.class);
        List<OrganCheckItem> organitemList = organItemDAO.findByOrganId(organid);
        for (OrganCheckItem organCheckItem : organitemList) {
            Integer appointid = organCheckItem.getCheckAppointId();
            String organsourceID = "A001|" + organCheckItem.getOrganItemCode();

            CheckSource source = new CheckSource();
            source.setCheckAppointId(appointid);
            source.setCreateDate(new Date());

            source.setOrganSourceId(organsourceID);
            source.setSourceNum(1);
            source.setStopFlag(0);
            source.setUsedNum(0);
            source.setWorkDate(workdate);
            if (appointid.intValue() == 1 || appointid.intValue() == 12) {
                Date startTime = DateConversion.getCurrentDate(workdate_str + " 10:00:00", "yyyy-MM-dd hh:mm:ss");
                Date endTime = DateConversion.getCurrentDate(workdate_str + " 10:30:00", "yyyy-MM-dd hh:mm:ss");
                source.setEndTime(endTime);
                source.setStartTime(startTime);
                source.setWorkType(1);
                source.setOrderNum(1);
                checkSourceDAO.save(source);

                Date startTime2 = DateConversion.getCurrentDate(workdate_str + " 14:00:00", "yyyy-MM-dd hh:mm:ss");
                Date endTime2 = DateConversion.getCurrentDate(workdate_str + " 14:30:00", "yyyy-MM-dd hh:mm:ss");
                source.setEndTime(endTime2);
                source.setStartTime(startTime2);
                source.setWorkType(2);
                source.setOrderNum(2);
                checkSourceDAO.save(source);
            }

            if (appointid.intValue() == 2 || appointid.intValue() == 3 || appointid.intValue() == 4 || appointid.intValue() == 5 || appointid.intValue() == 6 || appointid.intValue() == 7) {
                Date startTime = DateConversion.getCurrentDate(workdate_str + " 13:30:00", "yyyy-MM-dd hh:mm:ss");
                Date endTime = DateConversion.getCurrentDate(workdate_str + " 13:40:00", "yyyy-MM-dd hh:mm:ss");
                source.setEndTime(endTime);
                source.setStartTime(startTime);
                source.setWorkType(2);
                source.setOrderNum(1);
                checkSourceDAO.save(source);

                Date startTime2 = DateConversion.getCurrentDate(workdate_str + " 13:40:00", "yyyy-MM-dd hh:mm:ss");
                Date endTime2 = DateConversion.getCurrentDate(workdate_str + " 13:50:00", "yyyy-MM-dd hh:mm:ss");
                source.setEndTime(endTime2);
                source.setStartTime(startTime2);
                source.setWorkType(2);
                source.setOrderNum(2);
                checkSourceDAO.save(source);

                Date startTime3 = DateConversion.getCurrentDate(workdate_str + " 13:50:00", "yyyy-MM-dd hh:mm:ss");
                Date endTime3 = DateConversion.getCurrentDate(workdate_str + " 14:00:00", "yyyy-MM-dd hh:mm:ss");
                source.setEndTime(endTime3);
                source.setStartTime(startTime3);
                source.setWorkType(2);
                source.setOrderNum(3);
                checkSourceDAO.save(source);
            }
        }
    }

    /**
     * 号源数据验证
     *
     * @param checkSource
     */
    public void validateCheckSource(final CheckSource checkSource) {
        if (null == checkSource) {
            throw new DAOException(DAOException.VALUE_NEEDED, "Object is null");
        }
        if (null == checkSource.getCheckAppointId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "checkAppointId is null");
        }
        if (null == checkSource.getSourceNum()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "SourceNum is null");
        }
        if (null == checkSource.getWorkType()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "WorkType is null");
        }
        if (checkSource.getEndTime() == null || checkSource.getStartTime() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "EndTime or StartTime is null");
        }
        if (null == checkSource.getCheckAppointId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "CheckAppointId is null");
        }
        if (checkSource.getSourceNum() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "SourceNum must bigger than zero");
        }
        if (checkSource.getEndTime().before(checkSource.getStartTime())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "EndTime must later than StartTime");
        }
    }

    /**
     * 新增检查项 目号源
     *
     * @param checkSource 新增的号源信息组成对象
     * @return List<Integer> 新增成功返回号源序号列表
     * @throws DAOException
     * @author houxr
     */
    @RpcService
    public List<Integer> addOneCheckSource(CheckSource checkSource) throws DAOException {
        logger.info("新增号源addOneCheckSource,CheckSource:" + JSONUtils.toString(checkSource));
        validateCheckSource(checkSource);
        if (checkSource.getStopFlag() == null) {
            checkSource.setStopFlag(0);
        }
        int num=checkSource.getSourceNum();
       // System.out.println("num:"+num);
        CheckAppointItemDAO checkAppointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        CheckAppointItem checkAppointItem = checkAppointItemDAO.get(checkSource.getCheckAppointId());
        List<Integer> ids =addFromCheckScheduleToCheckSource(checkSource);
        //保存业务日志
        BusActionLogService.recordBusinessLog("检查号源", JSONUtils.toString(ids), "CheckSource",
                "给[" + this.getLogMessageByCheckSource(checkSource) + " " + checkAppointItem.getCheckAppointName() + "]添加" + num + "个号源");
        return ids;
    }

    public String getLogMessageByCheckSource(CheckSource a)
    {
        String logMessage = null;
        if (a != null)
        {
            Organ organ = null;
            OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
            organ = organDao.getByOrganId(a.getOrganId());
            if (organ != null)
            {
                logMessage = organ.getShortName() ;
            }
            CheckAppointItem item = null;
            if (a.getCheckAppointId() != null)
            {
                CheckAppointItemDAO itemDao = DAOFactory.getDAO(CheckAppointItemDAO.class);
                item = itemDao.get(a.getCheckAppointId());
            }
            if (item != null )
            {
                logMessage += " " + item.getCheckAppointName();
            }
        }
        return logMessage;
    }
    /**
     * 定时器调用生成医技检查号源
     *
     * @param checkSource
     * @return
     * @throws DAOException
     */
    public List<Integer> addFromCheckScheduleToCheckSource(CheckSource checkSource) throws DAOException {
        validateCheckSource(checkSource);
        if (checkSource.getStopFlag() == null) {
            checkSource.setStopFlag(0);
        }
        CheckAppointItemDAO checkAppointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        CheckAppointItem checkAppointItem = checkAppointItemDAO.get(checkSource.getCheckAppointId());
        checkSource.setOrganId(checkAppointItem.getOrganId());
        checkSource.setFromFlag(1);
        checkSource.setUsedNum(0);
        checkSource.setCreateDate(new Date());
        List<Integer> ids = new ArrayList<Integer>();
        int avg = checkSource.getSourceNum();
        List<Object[]> os = DateConversion.getAverageTime(checkSource.getStartTime(), checkSource.getEndTime(), avg);
        Integer orderNum = this.getMaxOrderNum(checkSource.getCheckAppointId(), checkSource.getWorkDate());
        if (checkSource.getStartNum() != null) {
            orderNum = checkSource.getStartNum();//设置 起始号源
        }
        if (orderNum == null) {
            orderNum = 0;
        }
        checkSource.setSourceNum(1);
        for (Object[] o : os) {
            CheckSource cs = new CheckSource();
            cs = checkSource;
            cs.setLockDoctor(null);
            cs.setStartTime((Date) o[0]);
            cs.setEndTime((Date) o[1]);
            cs.setOrderNum(++orderNum);
            ids.add(save(cs).getChkSourceId());
        }
        if (ids.size() <= 0) {
            return null;
        }
        return ids;
    }

    /**
     * 查询最大OrderNum值
     *
     * @param checkAppointId 检查队列内码
     * @param workDate       工作日期
     * @return int 最大OrderNum值
     * @author houxr
     */
    @DAOMethod(sql = "select max(orderNum) from CheckSource where checkAppointId=:checkAppointId and workDate=:workDate")
    public abstract Integer getMaxOrderNum(@DAOParam("checkAppointId") int checkAppointId,
                                           @DAOParam("workDate") Date workDate);

    /**
     * 查询医技检查号源 列表(添加范围)
     *
     * @param checkAppointId   科室代码
     * @param checkAppointName 医生姓名
     * @param range            范围- 0只查无排班，1只查有排班，-1查询全部
     * @param start            分页起始位置
     * @param limit            条数
     * @return
     * @author houxr 2016-07-19
     */
    public QueryResult<CheckAppointItem> findCheckItemSourceWithRange(final Integer organId, final Integer checkAppointId,
                                                                      final String checkAppointName, final int range,
                                                                      final Date startDate, final Date endDate,
                                                                      final int start, final int limit) {
        logger.info("查询医技检查队列号源findCheckAppointWithRange:[checkAppointId=" + checkAppointId + ";checkAppointName="
                + checkAppointName + ";range=" + range + ";start=" + start);
        HibernateStatelessResultAction<QueryResult<CheckAppointItem>> action = new AbstractHibernateStatelessResultAction<QueryResult<CheckAppointItem>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder("FROM CheckAppointItem c WHERE c.status=1 and c.organId=:organId ");
                if (checkAppointId != null) {
                    hql.append(" AND c.checkAppointId=:checkAppointId");
                }
                if (!StringUtils.isEmpty(checkAppointName)) {
                    hql.append(" AND c.checkAppointName like :checkAppointName");
                }
                switch (range) {
                    case 0:
                        hql.append(" and (select count(*) from CheckSource s where s.checkAppointId=c.checkAppointId "
                                + " and s.workDate>=:startDate and s.workDate<=:endDate and s.fromFlag=1)<=0");
                        break;
                    case 1:
                        hql.append(" and (select count(*) from CheckSource s where s.checkAppointId=c.checkAppointId "
                                + " and s.workDate>=:startDate and s.workDate<=:endDate and s.fromFlag=1)>0");
                }

                Query query = ss.createQuery("SELECT count(*) " + hql.toString());
                query.setParameter("organId", organId);
                if (checkAppointId != null) {
                    query.setParameter("checkAppointId", checkAppointId);
                }
                if (!StringUtils.isEmpty(checkAppointName)) {
                    query.setParameter("checkAppointName", "%" + checkAppointName + "%");
                }
                if (range == 0 || range == 1) {
                    query.setParameter("startDate", startDate);
                    query.setParameter("endDate", endDate);
                }
                Long total = (Long) query.uniqueResult();

                query = ss.createQuery("SELECT c " + hql.toString());
                query.setParameter("organId", organId);
                if (checkAppointId != null) {
                    query.setParameter("checkAppointId", checkAppointId);
                }
                if (!StringUtils.isEmpty(checkAppointName)) {
                    query.setParameter("checkAppointName", "%" + checkAppointName + "%");
                }
                if (range == 0 || range == 1) {
                    query.setParameter("startDate", startDate);
                    query.setParameter("endDate", endDate);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<CheckAppointItem>(total, query.getFirstResult(), query.getMaxResults(), query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 查询检查队列号源列表(添加范围)
     *
     * @param checkAppointId   检查队列编号
     * @param checkAppointName 检查队列名称
     * @param range            范围- 0只查无号源，1只查有号源，-1查询全部
     * @param startDate        号源分页开始时间
     * @param start            分页起始位置
     * @return
     * @author houxr
     */
    @RpcService
    public QueryResult<CheckAppointItem> findCheckSourcesWithRange(Integer organId, Integer checkAppointId,
                                                                   String checkAppointName,
                                                                   int range, Date startDate,
                                                                   int start, int limit) {
        logger.info("findAppointItemAndCheckSourcesWithRange==>checkAppointId=" + checkAppointId + ";start=" + start + ";limit=" + limit + "]");
        startDate = DateConversion.getFormatDate(startDate, "yyyy-MM-dd");
        Date endDate = DateConversion.getDateOfWeekNow(startDate);
        QueryResult<CheckAppointItem> checkAppointItems = this.findCheckItemSourceWithRange(organId, checkAppointId, checkAppointName, range, startDate, endDate, start, limit);
        for (CheckAppointItem cai : checkAppointItems.getItems()) {
            List<CheckSource> checkSourceList = findCheckSourcesByWorkDateAndCheckAppointId(cai.getCheckAppointId(), startDate, endDate);
            cai.setCheckSource(checkSourceList);
        }
        return checkAppointItems;
    }

    /**
     * 获取检查队列项目该天的号源信息列表
     *
     * @param checkAppointId 检查队列编号
     * @param dateNow        该天日期
     * @return
     * @author houxr
     */
    @RpcService
    public Hashtable<String, Object> findOneCheckItemSourcesByOneDate(int checkAppointId, Date dateNow) {
        List<CheckSource> checkSourceList = this.findCheckSourcesByWorkDateAndCheckAppointId(checkAppointId, dateNow, dateNow);
        int stop = 0, hadAppoint = 0, all = 0;
        Hashtable<String, Object> table = new Hashtable<String, Object>();
        Hashtable<String, Integer> counts = new Hashtable<String, Integer>();
        for (CheckSource checkSource : checkSourceList) {
            all++;
            if (checkSource.getStopFlag() == 1) {
                stop++;
            }
            if (checkSource.getSourceNum().equals(checkSource.getUsedNum())) {
                hadAppoint++;
            }
        }
        counts.put("all", all);
        counts.put("stop", stop);
        counts.put("hadAppoint", hadAppoint);
        table.put("appointSources", checkSourceList);
        table.put("counts", counts);
        return table;
    }

    /**
     * 查询医技检查号源服务
     *
     * @param checkAppointId 预约项目序号
     * @param startDate      分页开始时间
     * @param endDate        分页结束时间
     * @return
     * @author houxr
     */
    public List<CheckSource> findCheckSourcesByWorkDateAndCheckAppointId(final int checkAppointId,
                                                                         final Date startDate, final Date endDate) {
        HibernateStatelessResultAction<List<CheckSource>> action = new AbstractHibernateStatelessResultAction<List<CheckSource>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "From CheckSource where checkAppointId=:checkAppointId "
                        + " and workDate>=:startDate and workDate<=:endDate and fromFlag=1 order by startTime";
                Query q = ss.createQuery(hql);
                q.setParameter("checkAppointId", checkAppointId);
                q.setParameter("startDate", startDate);
                q.setParameter("endDate", endDate);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 定时器:根据排班定时生成号源
     *
     * @author xrhou
     */
    @RpcService
    public void fromCheckScheduleToCheckSource() {
        CheckScheduleDAO checkScheduleDAO = DAOFactory.getDAO(CheckScheduleDAO.class);
        AppointScheduleDAO appointScheduleDAO = DAOFactory.getDAO(AppointScheduleDAO.class);
        List<CheckSchedule> checkSchedules = checkScheduleDAO.findAllEffectiveCheckSchedule(0);
        for (CheckSchedule cs : checkSchedules) {
            logger.info("医技检查排班生成号源fromCheckScheduleToCheckSource==>CheckSchedule:" + JSONUtils.toString(cs));
            int week = cs.getWeek();
            LocalDate dt = new LocalDate();
            Date now = dt.toDate();
            int max = cs.getMaxRegDays();
            Date dTime = dt.plusDays(max).toDate();
            long maxDateTime = dTime.getTime();
            if (cs.getLastGenDate() != null) {
                now = cs.getLastGenDate();
            }
            while (now.getTime() <= maxDateTime) {
                List<Date> dates = appointScheduleDAO.findTimeSlotByThree(now, max, week);
                Date lastDate = DateConversion.getFormatDate(dates.get(1), "yyyy-MM-dd");
                long lastDateTime = lastDate.getTime();
                if (lastDateTime <= maxDateTime) {
                    String startTimeString = DateConversion.getDateFormatter(cs.getStartTime(), "HH:mm:ss");
                    String endTimeString = DateConversion.getDateFormatter(cs.getEndTime(), "HH:mm:ss");
                    Date startTime = DateConversion.getDateByTimePoint(lastDate, startTimeString);
                    Date endTime = DateConversion.getDateByTimePoint(lastDate, endTimeString);
                    int checkAppointId = cs.getCheckAppointId();
                    CheckSource source = new CheckSource();
                    source.setCheckAppointId(checkAppointId);
                    source.setOrganSourceId(cs.getOrganId().toString());
                    source.setSourceNum(cs.getSourceNum());
                    source.setWorkType(cs.getWorkType());
                    source.setStartTime(startTime);
                    source.setEndTime(endTime);
                    source.setWorkDate(lastDate);
                    source.setOrganSchedulingId(cs.getCheckScheduleId().toString());
                    source.setStartNum(cs.getStartNum());
                    this.addFromCheckScheduleToCheckSource(source);
                    cs.setLastGenDate(lastDate);
                }
                now = lastDate;
            }
            checkScheduleDAO.update(cs);
        }
    }

    @RpcService
    @DAOMethod(sql = "from CheckSource where organId=:organId and organSchedulingId=:organSchedulingId")
    public abstract List<CheckSource> findCheckSourceByOrganIdAndCheckScheduleId(@DAOParam("organId") Integer organId,
                                                                                 @DAOParam("organSchedulingId") String organSchedulingId);

    /**
     * 删除单条/多条医技检查号源
     *
     * @param ids 需删除的号源序号列表
     * @return int 成功删除的条数
     * @author houxr
     */
    @RpcService
    public Integer deleteOneOrMoreCheckSource(List<Integer> ids) {
        logger.info("删除单条/deleteOneOrMoreCheckSource=>ids:" + JSONUtils.toString(ids));
        //保存业务日志

        int count = 0;
        String organName = null;
        CheckRequestDAO checkRequestDAO = DAOFactory.getDAO(CheckRequestDAO.class);
        CheckAppointItemDAO checkAppointItemDAO = DAOFactory.getDAO(CheckAppointItemDAO.class);
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            CheckSource cs = get(id);
            if (cs == null) {
                continue;
            }
            if (organName == null)
            {
                organName  = this.getLogMessageByCheckSource(cs);
            }
            CheckAppointItem cai = checkAppointItemDAO.get(cs.getCheckAppointId());
            List<CheckRequest> checkRequestList = checkRequestDAO.findCheckRequestByCheckAppointIdAndChkSourceId(cai.getOrganId(), cs.getCheckAppointId(), cs.getChkSourceId());
            if (null == checkRequestList || checkRequestList.size() == 0) {
                remove(id);
                count++;
            } else {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "号源已被预约,不能删除");
            }
        }
        BusActionLogService.recordBusinessLog("检查号源", JSONUtils.toString(ids), "CheckSource",
                organName + " "  + JSONUtils.toString(ids) + "检查号源被删掉");
        return count;
    }
}
