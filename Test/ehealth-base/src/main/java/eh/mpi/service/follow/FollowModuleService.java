package eh.mpi.service.follow;

import com.alibaba.fastjson.JSONObject;
import ctd.account.UserRoleToken;
import ctd.persistence.DAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.service.BusActionLogService;
import eh.entity.base.BusActionLog;
import eh.entity.mpi.FollowModule;
import eh.entity.mpi.FollowModuleLimit;
import eh.entity.mpi.FollowModulePlan;
import eh.mpi.dao.FollowModuleDAO;
import eh.mpi.dao.FollowModulePlanDAO;
import eh.mpi.dao.FollwModuleLimitDAO;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author renzh
 * @date 2016/10/9 0009 下午 15:08
 */
public class FollowModuleService {
    private static final Logger logger = LoggerFactory.getLogger(FollowModuleService.class);

    private FollowModuleDAO followModuleDAO;
    private FollowModulePlanDAO followModulePlanDAO;

    public FollowModuleService() {
        this.followModuleDAO = DAOFactory.getDAO(FollowModuleDAO.class);
        this.followModulePlanDAO = DAOFactory.getDAO(FollowModulePlanDAO.class);
    }

    /**
     * 获取所有随访模板列表
     *
     * @return
     */
    @RpcService
    public List getAllModules() {
        List list = new ArrayList();
        try {
            List<FollowModule> followModuleList = followModuleDAO.findAll();
            if (followModuleList != null) {
                for (FollowModule followModule : followModuleList) {
                    List<FollowModulePlan> followModulePlanList = followModulePlanDAO.findByMid(followModule.getMid());
                    Map map = new HashMap();
                    map.put("followModule", followModule);
                    map.put("followModulePlanList", followModulePlanList);
                    list.add(map);
                }
            }
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        return list;
    }

    /**
     * 根据模板Id查询模板详情
     *
     * @param mid
     * @return
     */
    @RpcService
    public Map getModuleBymMid(Integer mid) {
        Map m = new HashMap();
        try {
            FollowModule followModule = followModuleDAO.get(mid);
            if (followModule != null) {
                List<FollowModulePlan> followModulePlanList = followModulePlanDAO.findByMid(followModule.getMid());
                m.put("followModule", followModule);
                m.put("followModulePlanList", followModulePlanList);
                m.put("limits",DAOFactory.getDAO(FollwModuleLimitDAO.class).findByMid(mid));
            }
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        return m;
    }

    /**
     * 获取全部模板（集合）
     *
     * @param start
     * @return
     */
    @RpcService
    public List<Map> getModuls(Integer start, Integer limit) {
        List<Map> maps = new ArrayList<Map>();
        try {
            List<FollowModule> followModuleList = followModuleDAO.findByLimitAndStart(start, limit);
            if (followModuleList != null) {
                for (FollowModule followModule : followModuleList) {
                    List<FollowModulePlan> followModulePlanList = followModulePlanDAO.findByMid(followModule.getMid());
                    Map map = new HashMap();
                    map.put("module", followModule);
                    map.put("plans", followModulePlanList);
                    maps.add(map);
                }
            }
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        return maps;
    }

    /**
     * 获取全部模板（QueryResult）
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Map> getAllModuleInfo(Integer start, Integer limit) {
        List<Map> maps = this.getModuls(start, limit);
        if (ValidateUtil.blankList(maps)) {
            maps = new ArrayList<>();
        }
        Long total = followModuleDAO.getCount();
        return new QueryResult<Map>(total, start, maps.size(), maps);
    }


    /**
     * 新增一个随访模板
     *
     * @param module 模板
     * @param plans  计划集合
     * @return
     * @author jianghc
     */
    @RpcService
    public Map createOneModule(FollowModule module, List<FollowModulePlan> plans, List<FollowModuleLimit> limits) {
        if (module == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "followModule is required!");
        }
        if (plans == null || plans.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "followModulePlan is required!");
        }
        /*if (limits == null || limits.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "FollowModuleLimit is required!");
        }*/
        if (module.getUniversally() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "通用选项不能为空");
        }

        //给模板赋默认值
        UserRoleToken urt = UserRoleToken.getCurrent();
        module.setCreator(urt.getId());
        module.setCreateName(urt.getUserName());
        module.setUpdater(urt.getId());
        module.setUpdateName(urt.getUserName());
        module.setCreateTime(new Date());
        module.setModifyTime(new Date());
        module.setStatus(1);
        Integer display = module.getDisplay();
        if (null == display) {
            //默认显示在医生端
            module.setDisplay(1);
        }
        // module.setPlanNum(plans.size());
        FollowModule saveModule = followModuleDAO.save(module);
        List<FollowModulePlan> savePlans = new ArrayList<FollowModulePlan>();
        Integer followNum = 0;//模板随访次数和
        for (FollowModulePlan plan : plans) {
            plan.setMid(saveModule.getMid());
            plan.setCreator(urt.getId());
            plan.setCreateName(urt.getUserName());
            plan.setUpdater(urt.getId());
            plan.setUpdateName(urt.getUserName());
            plan.setCreateTime(new Date());
            plan.setModifyTime(new Date());
            FollowModulePlan savePlan = followModulePlanDAO.save(plan);
            savePlans.add(savePlan);
            //获取单个计划的随访次数
            followNum += getFollowNum(plan.getIntervalNum(), plan.getIntervalUnit(), plan.getIntervalDay(), plan.getIntervalDayUnit());
        }
        FollwModuleLimitDAO follwModuleLimitDAO = DAOFactory.getDAO(FollwModuleLimitDAO.class);
        List<FollowModuleLimit> saveLimits = new ArrayList<FollowModuleLimit>();
        for (FollowModuleLimit l:limits){
            l.setMid(saveModule.getMid());
            l.setActiveFlag(true);
            l.setCreateTime(new Date());
            l.setUpdateTime(new Date());
            saveLimits.add(follwModuleLimitDAO.save(l));
        }



        //更新模板的随访次数和
        module.setPlanNum(followNum);
        saveModule = followModuleDAO.update(module);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("module", saveModule);
        map.put("plans", savePlans);
        map.put("limits",saveLimits);
        BusActionLogService.recordBusinessLog("随访模板管理",module.getMid()+"","FollowModule","创建随访模板【"+module.getTitle()+"】");
        return map;
    }

    /**
     * 更新一个模板（包括模板里面的计划）
     *
     * @param module 模板
     * @param plans  计划集合
     * @param dels   需要删除的计划ID集合
     * @return
     * @author jianghc
     */
    @RpcService
    public Map updateOneModule(FollowModule module, List<FollowModulePlan> plans, List<Integer> dels, List<FollowModuleLimit> limits, List<Integer> delLimit) {

        if (module == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "followModule is required!");
        }
        if (plans == null || plans.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "followModulePlan is required!");
        }
       /* if (limits == null || limits.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "FollowModuleLimit is required!");
        }*/
        FollowModule oldModule = followModuleDAO.getByMid(module.getMid());
        if (oldModule == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "this MID is not exist!");
        }
        //获取当前操作人员信息
        UserRoleToken urt = UserRoleToken.getCurrent();
        module.setUpdater(urt.getId());
        module.setUpdateName(urt.getUserName());
        module.setModifyTime(new Date());
        module.setPlanNum(plans.size());
        Integer mid = module.getMid();
        //执行删除计划操作
        if (dels != null) {
            for (Integer del : dels) {
                if (del != null) {
                    followModulePlanDAO.deleteByMpid(del);
                }
            }
        }
        FollwModuleLimitDAO follwModuleLimitDAO = DAOFactory.getDAO(FollwModuleLimitDAO.class);
        if (delLimit != null) {
            for (Integer d : delLimit) {
                if (d != null) {
                    follwModuleLimitDAO.remove(d);
                }
            }
        }
        for (FollowModuleLimit l:limits){
            l.setMid(module.getMid());
            l.setActiveFlag(true);
            l.setCreateTime(new Date());
            l.setUpdateTime(new Date());
            follwModuleLimitDAO.save(l);
        }
        //更新或新增计划操作
        List<FollowModulePlan> savePlans = new ArrayList<FollowModulePlan>();
        Integer followNum = 0;//模板随访次数和
        for (FollowModulePlan plan : plans) {
            plan.setUpdater(urt.getId());
            plan.setUpdateName(urt.getUserName());
            plan.setModifyTime(new Date());
            FollowModulePlan savePlan = null;
            if (plan.getMpid() != null) {//执行更新操作
                FollowModulePlan oldPlan = followModulePlanDAO.getByMpid(plan.getMpid());
                if (oldPlan == null) {
                    plan.setMpid(null);
                    plan.setMid(module.getMid());
                    plan.setCreateTime(new Date());
                    plan.setCreator(urt.getId());
                    plan.setCreateName(urt.getUserName());//创建人姓名
                    savePlan = followModulePlanDAO.save(plan);
                } else {
                    BeanUtils.map(plan, oldPlan);
                    savePlan = followModulePlanDAO.update(oldPlan);
                }
            } else {//执行新增操作
                plan.setMid(module.getMid());
                plan.setCreateTime(new Date());
                plan.setCreator(urt.getId());
                plan.setCreateName(urt.getUserName());//创建人姓名
                savePlan = followModulePlanDAO.save(plan);
            }
            savePlans.add(savePlan);
            //获取每个计划的随访次数
            followNum += getFollowNum(plan.getIntervalNum(), plan.getIntervalUnit(), plan.getIntervalDay(), plan.getIntervalDayUnit());
        }
        //更新模板的随访次数
        module.setPlanNum(followNum);
        BeanUtils.map(module, oldModule);
        FollowModule saveModule = followModuleDAO.update(oldModule);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("module", saveModule);
        map.put("plans", savePlans);
        BusActionLogService.recordBusinessLog("随访模板管理",mid+"","FollowModule","更新随访模板【"+module.getTitle()+"】");
        return map;
    }

    /**
     * 删除一个模板并删除与之对应的计划
     *
     * @param mid 模板ID
     */
    @RpcService
    public void deleteOneModule(Integer mid) {
        if (mid == null || mid <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mid is required!");
        }
        FollowModule module = followModuleDAO.getByMid(mid);
        if (module==null) {
            throw new DAOException("mid is not exist!");
        }
        followModuleDAO.deleteByMid(mid);
        followModulePlanDAO.deleteByMid(mid);
        DAOFactory.getDAO(FollwModuleLimitDAO.class).deleteByMid(mid);
        BusActionLogService.recordBusinessLog("随访模板管理",mid+"","FollowModule","删除随访模板【"+module.getTitle()+"】");
    }


    /**
     * 根据计划周期及长度计算随访次数
     *
     * @param intervalNum
     * @param intervalUnit
     * @param intervalDay
     * @param intervalDayUnit
     * @return
     */
    private Integer getFollowNum(Integer intervalNum, Integer intervalUnit, Integer intervalDay, Integer intervalDayUnit) {
        int u1 = 1, u2 = 1;//u1为周期倍数（天为1倍，周为7倍，月为30倍，年为365倍），u2为长度倍数（天为1倍，周为7倍，月为30倍，年为365倍）
        switch (intervalUnit) {
            case 1:
                u1 = 1;
                break;
            case 2:
                u1 = 7;
                break;
            case 3:
                u1 = 30;
                break;
            case 4:
                u1 = 365;
                break;
            default:
                break;
        }
        switch (intervalDayUnit) {
            case 1:
                u2 = 1;
                break;
            case 2:
                u2 = 7;
                break;
            case 3:
                u2 = 30;
                break;
            case 4:
                u2 = 365;
                break;
            default:
                break;
        }
        Integer sum1 = intervalNum * u1;
        Integer sum2 = intervalDay * u2;
        if (sum1.equals(Integer.valueOf(0))) {
            return 0;
        }
        return sum2 / sum1;
    }

    /**
     * 根据Id获得一个模板，包括计划
     *
     * @param mid
     * @return
     */
    @RpcService
    public Map getOneModule(Integer mid) {
        if (mid == null || mid <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mid is required!");
        }
        FollowModule module = followModuleDAO.getByMid(mid);
        if (module == null) {
            return null;
        }
        List<FollowModulePlan> plans = followModulePlanDAO.findByMid(mid);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("module", module);
        map.put("plans", plans);
        map.put("limits",DAOFactory.getDAO(FollwModuleLimitDAO.class).findByMid(mid));
        return map;
    }

}
