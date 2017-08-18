package eh.bus.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.Organ;
import eh.entity.bus.CheckAppointItem;
import eh.entity.bus.CheckSchedule;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * Created by houxr on 2016/7/19.
 */
public abstract class CheckScheduleDAO extends HibernateSupportDelegateDAO<CheckSchedule> {

    private static final Logger logger = Logger.getLogger(CheckScheduleDAO.class);

    public CheckScheduleDAO() {
        super();
        this.setEntityName(CheckSchedule.class.getName());
        this.setKeyField("checkScheduleId");
    }

    @RpcService
    @DAOMethod
    public abstract CheckSchedule getByCheckScheduleId(Integer checkScheduleId);

    @RpcService
    @DAOMethod
    public abstract List<CheckSchedule> findByCheckAppointId(Integer checkAppointId);

    /**
     * 排班数据 验证
     *
     * @param chkSchedule
     */
    public void validateCheckSchedule(final CheckSchedule chkSchedule) {
        if (null == chkSchedule) {
            throw new DAOException(DAOException.VALUE_NEEDED, "Object is null");
        }
        if (null == chkSchedule.getOrganId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "OrganId is null");
        }
        if (null == chkSchedule.getSourceNum()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "SourceNum is null");
        }
        if (null == chkSchedule.getWeek()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "Week is null");
        }
        if (null == chkSchedule.getWorkType()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "WorkType is null");
        }
        if (chkSchedule.getEndTime() == null || chkSchedule.getStartTime() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "EndTime or StartTime is null");
        }
        if (null == chkSchedule.getUseFlag()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "UseFlag is null");
        }
        if (null == chkSchedule.getCheckAppointId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "CheckAppointId is null");
        }
        if (chkSchedule.getSourceNum() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "SourceNum must bigger than zero");
        }
        if (chkSchedule.getMaxRegDays() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "MaxRegDays must bigger than zero");
        }
        if (chkSchedule.getEndTime().before(chkSchedule.getStartTime())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "EndTime must later than StartTime");
        }
    }

    /**
     * 新增医技排班信息
     *
     * @param chkSchedule
     * @return
     */
    @RpcService
    public CheckSchedule addOneChkSchedule(final CheckSchedule chkSchedule) {
        logger.info("新增医技排班信息[addOneSchedule]:CheckSchedule:" + JSONUtils.toString(chkSchedule));
        validateCheckSchedule(chkSchedule);
        Date ymd = DateConversion.getCurrentDate("1990-01-01", "yyyy-MM-dd");
        String startTime = DateConversion.getDateFormatter(chkSchedule.getStartTime(), "HH:mm:ss");
        String endTime = DateConversion.getDateFormatter(chkSchedule.getEndTime(), "HH:mm:ss");
        Date s = DateConversion.getDateByTimePoint(ymd, startTime);
        Date e = DateConversion.getDateByTimePoint(ymd, endTime);
        chkSchedule.setStartTime(s);
        chkSchedule.setEndTime(e);
        CheckSchedule newCheckSchedule=save(chkSchedule);
        BusActionLogService.recordBusinessLog("检查排班",String.valueOf(newCheckSchedule.getCheckScheduleId()),"CheckSchedule",
               this.getLogMessageByCheckSchedule(chkSchedule) + " 新增检查排班["+chkSchedule.getCheckScheduleId()+"]");
        return newCheckSchedule;
    }

    public String getLogMessageByCheckSchedule(CheckSchedule a)
    {
        String logMessage = null;
        if (a != null)
        {
            Organ organ = null;
            OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
            if (a.getOrganId() != null)
            {
                organ = organDao.getByOrganId(a.getOrganId());
            }
            if (organ != null )
            {
                logMessage = organ.getShortName();
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
     * 更新检查排班
     *
     * @param chkSchedule 检查号源
     * @return
     */
    @RpcService
    public CheckSchedule updateCheckSchedule(final CheckSchedule chkSchedule) {

        if (null == chkSchedule) {
            throw new DAOException(DAOException.VALUE_NEEDED, "CheckSchedule is null");
        }
        if (null == chkSchedule.getCheckScheduleId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "CheckScheduleId is null");
        }
        CheckSchedule target = get(chkSchedule.getCheckScheduleId());
        if (null == target) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "CheckSchedule is not exist");
        }
        //不允许修改排班状态
        chkSchedule.setUseFlag(null);
        BeanUtils.map(chkSchedule, target);
        validateCheckSchedule(target);
        BusActionLogService.recordBusinessLog("检查排班",String.valueOf(chkSchedule.getCheckScheduleId()),"CheckSchedule",
                this.getLogMessageByCheckSchedule(chkSchedule) + "更新检查排班["+chkSchedule.getCheckScheduleId()+"]");
        return update(target);
    }

    /**
     * 由预约项目序号和机构内码查询医技检查排班列表服务
     *
     * @param checkAppointId 预约项目序号
     * @param organId        机构编码
     * @return List<CheckSchedule> 医技检查 排班列表[时间正序]
     * @author houxr
     * @date 2016-07-18 11:10:32
     */
    @RpcService
    @DAOMethod(sql = "From CheckSchedule where checkAppointId=:checkAppointId and organId=:organId order by week,startTime")
    public abstract List<CheckSchedule> findChkScheduleByChkAppointIdAndOrganId(
            @DAOParam("checkAppointId") int checkAppointId, @DAOParam("organId") int organId);

    /**
     * 获取所有有效/无效医技检查排班列表
     *
     * @param useFlag 启用标志:0正常，1停班
     * @return List<AppointSchedule> 排班列表
     * @author houxr
     */
    @RpcService
    public List<CheckSchedule> findAllEffectiveCheckSchedule(final int useFlag) {
        HibernateStatelessResultAction<List<CheckSchedule>> action =
                new AbstractHibernateStatelessResultAction<List<CheckSchedule>>() {
                    @SuppressWarnings("unchecked")
                    public void execute(StatelessSession ss) throws DAOException {
                        String hql = "From CheckSchedule where useFlag=:useFlag";
                        Query q = ss.createQuery(hql);
                        q.setParameter("useFlag", useFlag);
                        List<CheckSchedule> as = q.list();
                        setResult(as);
                    }
                };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询排班检查查列表接口(添加范围)
     *
     * @param organId        机构内码
     * @param chkAppointId   检查队列编号
     * @param chkAppointName 检查队列名称
     * @param range          范围- 0只查无排班，1只查有排班，-1查询全部
     * @param start          分页起始位置
     * @param limit          条数
     * @return
     * @author houxr 2016-07-19
     */
    @RpcService
    public QueryResult<CheckAppointItem> findAppointItemAndScheduleWithRange(int organId, Integer chkAppointId, String chkAppointName,
                                                                             int range, int start, int limit) {
        logger.info("findAppointItemAndScheduleWithRange==" + "[organId=" + organId
                + ";chkAppointId=" + chkAppointId + ";start=" + start + "]");
        QueryResult<CheckAppointItem> cas = this.findCheckAppointItemWithRange(organId, chkAppointId, chkAppointName, range, start, limit);
        for (CheckAppointItem c : cas.getItems()) {
            List<CheckSchedule> checkSchedules = this.findChkScheduleByChkAppointIdAndOrganId(c.getCheckAppointId(), organId);
            c.setCheckSchedules(checkSchedules);
        }
        return cas;
    }

    /**
     * 供 查询排班 列表接口(添加范围) 调用
     *
     * @param organId          机构内码
     * @param checkAppointId   科室代码
     * @param checkAppointName 医生姓名
     * @param range            范围- 0只查无排班，1只查有排班，-1查询全部
     * @param start            分页起始位置
     * @return
     * @author houxr 2016-07-19
     */
    public QueryResult<CheckAppointItem> findCheckAppointItemWithRange(final int organId, final Integer checkAppointId,
                                                                       final String checkAppointName, final int range,
                                                                       final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<CheckAppointItem>> action = new AbstractHibernateStatelessResultAction<QueryResult<CheckAppointItem>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "FROM CheckAppointItem c WHERE c.status=1 and c.organId=:organId ");
                if (null != checkAppointId) {
                    hql.append(" AND c.checkAppointId=:checkAppointId");
                }
                if (!StringUtils.isEmpty(checkAppointName)) {
                    hql.append(" and c.checkAppointName like :checkAppointName");
                }
                switch (range) {
                    case 0:
                        hql.append(" and (select count(*) from CheckSchedule s where s.checkAppointId=c.checkAppointId)<=0");
                        break;
                    case 1:
                        hql.append(" and (select count(*) from CheckSchedule s where s.checkAppointId=c.checkAppointId)>0");
                }
                Query query = ss.createQuery("SELECT count(*) " + hql.toString());
                query.setParameter("organId", organId);
                if (checkAppointId != null) {
                    query.setParameter("checkAppointId", checkAppointId);
                }
                if (!StringUtils.isEmpty(checkAppointName)) {
                    query.setParameter("checkAppointName", "%" + checkAppointName + "%");
                }
                Long total = (Long) query.uniqueResult();

                query = ss.createQuery("SELECT c " + hql);
                query.setParameter("organId", organId);
                if (checkAppointId != null) {
                    query.setParameter("checkAppointId", checkAppointId);
                }
                if (!StringUtils.isEmpty(checkAppointName)) {
                    query.setParameter("checkAppointName", "%" + checkAppointName + "%");
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
     * 删除单条/多条医技检查排班
     *
     * @param ids 需删除的排班序号列表
     * @return int 成功删除的条数
     * @author houxr
     */
    @RpcService
    public Integer deleteOneOrMoreCheckSchedule(List<Integer> ids) {
        logger.info("删除单条/多条排班deleteOneOrMoreCheckSchedule=>ids:" + JSONUtils.toString(ids));

        int count = 0;
        String deptMessage = null;
        for (Integer id : ids) {
            if (id == null) {
                continue;
            }
            CheckSchedule cs = get(id);
            if (cs == null) {
                continue;
            }
            if (deptMessage == null)
            {
                deptMessage = this.getLogMessageByCheckSchedule(cs);
            }
            remove(id);
            count++;
        }
        BusActionLogService.recordBusinessLog("检查排班",JSONUtils.toString(ids),"CheckSchedule",
                deptMessage + " 医技检查排班"+JSONUtils.toString(ids)+"被删除");
        return count;
    }

}
